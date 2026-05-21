package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.llm.DpLlmNpcDecisionService;
import com.example.mgdemoplus.websocket.DpGameRoomPushService;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Global 1s room tick: heartbeat eviction, deserted-room cleanup, NPC/human action timeouts, settled auto-ready.
 */
public final class DpRoomHeartbeatScheduler {

    private final DpRoomRegistry registry;
    private final DpLlmNpcDecisionService llmNpcDecisionService;
    private final DpGameRoomPushService gameRoomPushService;
    private final DpRoomLobbySync lobbySync;
    private final DpRoomServiceCallbacks callbacks;

    public DpRoomHeartbeatScheduler(
            DpRoomRegistry registry,
            DpLlmNpcDecisionService llmNpcDecisionService,
            DpGameRoomPushService gameRoomPushService,
            DpRoomLobbySync lobbySync,
            DpRoomServiceCallbacks callbacks) {
        this.registry = registry;
        this.llmNpcDecisionService = llmNpcDecisionService;
        this.gameRoomPushService = gameRoomPushService;
        this.lobbySync = lobbySync;
        this.callbacks = callbacks;
    }

    public void startGlobalTimerUnlessSuppressed(boolean suppressForTests) {
        if (!suppressForTests) {
            new Timer().scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    for (DpRoomBO room : registry.values()) {
                        runGlobalSecondTickForSingleRoom(room);
                    }
                }
            }, 0, 1000);
        }
    }

    public void runGlobalSecondTickForSingleRoom(DpRoomBO room) {
        boolean lobbyDirty = tickEvictStaleSeatedPlayersOnHeartbeat(room);
        if (tickEvictStaleSpectatorsOnHeartbeat(room)) {
            lobbyDirty = true;
        }
        if (removeDesertedRoomInGlobalTickIfNoLiveHumans(room)) {
            return;
        }
        tickNpcTurnOrHumanActionTimeout(room);
        tickSettledBotsAutoReady(room);
        tickSettledCheckAndStartIfReady(room);
        if (skipBroadcastForLoneSettledPlayerWaitingNothing(room)) {
            return;
        }
        maybeInvokeReadyTimeoutWhenDeadlinePassed(room);
        broadcastRoomAndMaybeRefreshLobbyAfterHeartbeatTick(room, lobbyDirty);
    }

    public boolean removeDesertedRoomInGlobalTickIfNoLiveHumans(DpRoomBO room) {
        int size = DpRoomHumanCounts.liveHumanTableCount(room);
        if (size == 0 && room.getSpectators().isEmpty()) {
            System.out.println("定时器检测：房间：" + room.getRoomId() + "没活人了");
            System.out.println("房间Id" + room.getRoomId());
            callbacks.removeRoom(room.getRoomId());
            return true;
        }
        return false;
    }
/**
 * 清理掉没有心跳的真人玩家，则移出房间
 */
    private boolean tickEvictStaleSeatedPlayersOnHeartbeat(DpRoomBO room) {
        boolean lobbyDirty = false;
        Iterator<DpPlayer> it = room.getPlayers().iterator();
        while (it.hasNext()) {
            DpPlayer p = it.next();
            if (DpNpcEngine.isBotPlayer(p)) {
                p.setLastHeartBeat(System.currentTimeMillis());
                continue;
            }
            if (p.isLeftThisHand()) {
                continue;
            }
            if (System.currentTimeMillis() - p.getLastHeartBeat() > DpRoomBO.getHeartTimeout()) {
                if (p.getNickname().equals(room.getOwner())) {
                    callbacks.giveOwner(room.getRoomId(), p.getNickname());
                }
                System.out.println("未收到" + p.getNickname() + "的心跳,已移出房间");
                String hbNick = p.getNickname();
                Integer hbUid = p.getDpUserId();
                it.remove();
                callbacks.presenceTryMarkIdleFullyLeft(hbNick, hbUid, room, "heartbeat_evict_player");
                lobbyDirty = true;
            }
        }
        return lobbyDirty;
    }

    private boolean tickEvictStaleSpectatorsOnHeartbeat(DpRoomBO room) {
        List<String> specList = room.getSpectators();
        if (specList == null || specList.isEmpty()) {
            return false;
        }
        boolean lobbyDirty = false;
        long tick = System.currentTimeMillis();
        for (String specNick : new ArrayList<>(specList)) {
            if (DpNpcEngine.isBotNickname(specNick)) {
                continue;
            }
            Long spLast = room.getSpectatorLastPresenceMs(specNick);
            if (spLast == null) {
                room.touchSpectatorPresence(specNick, tick);
                continue;
            }
            if (tick - spLast > DpRoomBO.getHeartTimeout()) {
                System.out.println("未收到观众 " + specNick + " 的心跳/WS 保活,已移出房间");
                if (callbacks.exitRoom(room.getRoomId(), specNick)) {
                    lobbyDirty = true;
                }
            }
        }
        return lobbyDirty;
    }

    private void tickNpcTurnOrHumanActionTimeout(DpRoomBO room) {
        if (room.isPlaying()
                && room.getCurrentActorIndex() >= 0
                && room.getCurrentActorIndex() < room.getPlayers().size()) {
            DpPlayer p = room.getPlayers().get(room.getCurrentActorIndex());
            if (DpNpcEngine.isBotPlayer(p)) {
                DpNpcEngine.BotAction action;
                if (DpNpcEngine.isLlmBotNickname(p.getNickname())) {
                    action = llmNpcDecisionService.decideActionIfReady(room, p);
                    callbacks.npcAction(room, p, action);
                } else {
                    action = DpNpcEngine.decideActionIfReady(room, p);
                    callbacks.npcAction(room, p, action);
                }
            } else {
                if (p.isLeftThisHand()) {
                    callbacks.moveToNextValidActor(room);
                    callbacks.autoAdvanceIfRoundFinished(room);
                } else if (System.currentTimeMillis() - room.getLastActionTime() > DpRoomBO.getActionTimeout()) {
                    p.setFold(true);
                    callbacks.moveToNextValidActor(room);
                    callbacks.autoAdvanceIfRoundFinished(room);
                }
            }
        }
    }

    private void tickSettledBotsAutoReady(DpRoomBO room) {
        if (room.isPlaying() && "settled".equals(room.getCurrentStage())) {
            for (DpPlayer p : room.getPlayers()) {
                if (!DpNpcEngine.isBotPlayer(p)) {
                    continue;
                }
                if (p.getChips() < room.getBigBlindChips()) {
                    callbacks.rebuy(room.getRoomId(), p.getNickname());
                }
                if (p.getChips() >= room.getBigBlindChips() && !p.isReady()
                        && !(room.getPlayers().size() == 1 && room.getWaitNextHand().isEmpty())) {
                    p.setReady(true);
                }
            }
        }
    }

    /**
     * settled 阶段每秒重判：补码窗口（0~10s）→筹码筛选（10s~30s）的时间边界由心跳驱动，
     * 避免 10s 时刻判定标准切换后无人触发 {@code checkAndStartNextHandAfterSettleReturning}。
     */
    private void tickSettledCheckAndStartIfReady(DpRoomBO room) {
        if (room.isPlaying() && "settled".equals(room.getCurrentStage())) {
            if (callbacks.checkAndStartNextHandAfterSettleReturning(room)) {
                lobbySync.refreshJoinableQmIndexThenSyncLobby(room.getRoomId());
            }
        }
    }

    private boolean skipBroadcastForLoneSettledPlayerWaitingNothing(DpRoomBO room) {
        return room.isPlaying()
                && "settled".equals(room.getCurrentStage())
                && room.getReadyDeadline() > 0
                && System.currentTimeMillis() > room.getReadyDeadline()
                && room.getPlayers().size() == 1
                && room.getWaitNextHand().isEmpty();
    }

    private void maybeInvokeReadyTimeoutWhenDeadlinePassed(DpRoomBO room) {
        if (room.isPlaying()
                && "settled".equals(room.getCurrentStage())
                && room.getReadyDeadline() > 0
                && System.currentTimeMillis() > room.getReadyDeadline()) {
            callbacks.handleReadyTimeout(room);
        }
    }

    private void broadcastRoomAndMaybeRefreshLobbyAfterHeartbeatTick(DpRoomBO room, boolean lobbyDirty) {
        gameRoomPushService.broadcastIfSubscribed(room.getRoomId());
        if (lobbyDirty) {
            lobbySync.refreshJoinableQmIndexThenSyncLobby(room.getRoomId());
        }
    }
}
