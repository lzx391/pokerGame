package com.example.mgdemoplus.service.serviceImpl;

import com.example.mgdemoplus.bo.DpObservedHandRecordBO;
import com.example.mgdemoplus.bo.DpRoomBO;
import com.example.mgdemoplus.quickmatch.DpQuickMatchRoomSemantics;
import com.example.mgdemoplus.quickmatch.JoinableQuickMatchRoomIndex;
import com.example.mgdemoplus.quickmatch.pairing.DpQuickMatchPairingCoordinator;
import com.example.mgdemoplus.quickmatch.pairing.DpQuickMatchPairingHost;
import com.example.mgdemoplus.quickmatch.pairing.DpQuickMatchWaitEntry;
import com.example.mgdemoplus.entity.DpPlayer;
import com.example.mgdemoplus.entity.DpPot;
import com.example.mgdemoplus.entity.DpRoom;
import com.example.mgdemoplus.entity.DpPlayerStats;
import com.example.mgdemoplus.entity.DpUser;
import com.example.mgdemoplus.mapper.DpUserMapper;
import com.example.mgdemoplus.service.DpFriendPresenceService;
import com.example.mgdemoplus.service.DpHandHistoryObservedService;
import com.example.mgdemoplus.service.DpHandHistoryPersistService;
import com.example.mgdemoplus.service.DpRoomHallService;
import com.example.mgdemoplus.utils.ResultUtil;
import com.example.mgdemoplus.utils.DpUtilHandEvaluator;
import com.example.mgdemoplus.websocket.DpGameRoomPushService;
import com.example.mgdemoplus.websocket.DpQuickMatchPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
public class DpRoomServiceImpl {
    private static final Logger log = LoggerFactory.getLogger(DpRoomServiceImpl.class);

    private final Map<String, DpRoomBO> roomMap = new ConcurrentHashMap<>();
    private final DpHandHistoryPersistService observedHandPersistService;
    private final DpLlmNpcDecisionService llmNpcDecisionService;
    private final DpGameRoomPushService gameRoomPushService;
    private final DpUserMapper dpUserMapper;
    private final DpHandHistoryObservedService observedHandService;
    private final DpRoomHallService dpRoomHallService;
    private final ObjectMapper objectMapper;
    private final DpQuickMatchPushService quickMatchPush;
    private final DpFriendPresenceService friendPresence;

    /**
     * 无密码公开房快匹「缺人数」索引；在 {@link #syncLobbyForRoomId}（及退房删除摘要）链路之前由本类维护。
     * 锁序见 {@link JoinableQuickMatchRoomIndex} 与
     * {@code docs/dp-quick-match-concurrency.md}：禁止「持单房 intrinsic 监视器时再抢索引锁」。
     */
    private final JoinableQuickMatchRoomIndex joinableQuickMatchRoomIndex = new JoinableQuickMatchRoomIndex();

    /**
     * 公开房瞬时快匹均无空位时的默认等待队列（单机内存）。
     * 参数与 {@link #createRoom} 默认快匹建房一致：小盲 5、大盲 10、50 倍初始、公开、最多 9
     * 人；FIFO，满二人自动建新桌并进房、准备。
     */
    private static final int DEFAULT_QM_SB = 5;
    private static final int DEFAULT_QM_BB = 10;
    private static final int DEFAULT_QM_STARTING_BB = 50;
    private static final int DEFAULT_QM_MAX_SEATS = DpRoomBO.MAX_SEAT_COUNT;
    /** 排队等候上限，超时移出队列（仅内存，不写库）。 */
    private static final long DEFAULT_QM_WAIT_MS = 3L * 60L * 1000L;
    /** 与 {@link #quickMatchJoinAndReady} 中「无候选公开房」文案保持一致，便于判断仅此时入队。 */
    static final String MSG_NO_PUBLIC_ROOM = "暂无可匹配的公开房间";

    private final Object defaultQmLock = new Object();
    private final ArrayDeque<DpQuickMatchWaitEntry> defaultQmWaiters = new ArrayDeque<>();

    /**
     * 串行化快匹写入 roomMap：{@link #quickMatchJoinAndReady} 与
     * {@link DpQuickMatchPairingCoordinator#attemptPairing()}（经 {@link #attemptQuickMatchPairing}）共用，
     * 避免「已从队列摘下但尚未落座」与另一条快匹/扫房请求交错导致重复占位。
     * <p>
     * {@link #attemptQuickMatchPairing} 使用 {@link ReentrantLock#tryLock()}：一轮配对已会冲刷队列，并发触发时跳过即可；
     * {@link #quickMatchJoinAndReady} 仍 {@link ReentrantLock#lock()}，避免在用户侧误报无房。
     */
    private final ReentrantLock dpQuickMatchAssignmentLock = new ReentrantLock();

    private final DpQuickMatchPairingCoordinator qmPairingCoordinator = new DpQuickMatchPairingCoordinator(
            new QuickMatchPairingHost());
/**
 * 尝试从队列配对建房逻辑
 */
    private void attemptQuickMatchPairing() {
        if (!dpQuickMatchAssignmentLock.tryLock()) {
            return;
        }
        try {
            qmPairingCoordinator.attemptPairing();
        } finally {
            dpQuickMatchAssignmentLock.unlock();
        }
    }

    // 统一从 NPC 引擎中获取机器人昵称，避免散落魔法字符串
    public boolean addDemoBotToNextHand(String roomId) {
        DpRoomBO r = roomMap.get(roomId);
        if (r == null) {
            return false;
        }
        int seq = r.allocateBotNicknameSeqBatch(1);
        return readyNextHand(roomId, DpNpcEngine.ruleBotNickname(DpNpcEngine.BotType.FISH, seq), null);
    }

    /**
     * 疯子 NPC：{@code BOT_MANIAC_<房间序号>}。
     */
    public boolean addManiacBotToNextHand(String roomId) {
        DpRoomBO r = roomMap.get(roomId);
        if (r == null) {
            return false;
        }
        int seq = r.allocateBotNicknameSeqBatch(1);
        return readyNextHand(roomId, DpNpcEngine.ruleBotNickname(DpNpcEngine.BotType.MANIAC, seq), null);
    }

    /**
     * 兼容旧接口/前端：下一局加入昵称 {@link DpNpcEngine#LEGACY_BOT_SHARK_NICKNAME}，
     * 行为与 {@link #addTagBotToNextHand(String)} 相同（紧凶规则 NPC）。
     */
    public boolean addSharkBotToNextHand(String roomId) {
        return readyNextHand(roomId, DpNpcEngine.LEGACY_BOT_SHARK_NICKNAME, null);
    }

    /**
     * 紧凶型 NPC：{@code BOT_TAG_<房间序号>}。
     */
    public boolean addTagBotToNextHand(String roomId) {
        DpRoomBO r = roomMap.get(roomId);
        if (r == null) {
            return false;
        }
        int seq = r.allocateBotNicknameSeqBatch(1);
        return readyNextHand(roomId, DpNpcEngine.ruleBotNickname(DpNpcEngine.BotType.TAG, seq), null);
    }

    /** 松凶 LAG */
    public boolean addLagBotToNextHand(String roomId) {
        DpRoomBO r = roomMap.get(roomId);
        if (r == null) {
            return false;
        }
        int seq = r.allocateBotNicknameSeqBatch(1);
        return readyNextHand(roomId, DpNpcEngine.ruleBotNickname(DpNpcEngine.BotType.LAG, seq), null);
    }

    /** 紧弱 Nit */
    public boolean addNitBotToNextHand(String roomId) {
        DpRoomBO r = roomMap.get(roomId);
        if (r == null) {
            return false;
        }
        int seq = r.allocateBotNicknameSeqBatch(1);
        return readyNextHand(roomId, DpNpcEngine.ruleBotNickname(DpNpcEngine.BotType.NIT, seq), null);
    }

    /** 跟注站 */
    public boolean addCallStationBotToNextHand(String roomId) {
        DpRoomBO r = roomMap.get(roomId);
        if (r == null) {
            return false;
        }
        int seq = r.allocateBotNicknameSeqBatch(1);
        return readyNextHand(roomId, DpNpcEngine.ruleBotNickname(DpNpcEngine.BotType.CALL, seq), null);
    }

    /**
     * 按档位连续添加 {@code count} 个规则 NPC（共用房间内递增序号）；{@code archetypeKey} 参见
     * {@link DpNpcEngine#ruleBotNicknameForKey(String, int)}。
     */
    public boolean addRuleNpcBatchToNextHand(String roomId, String archetypeKey, int count) {
        if (count <= 0) {
            return true;
        }
        DpRoomBO r = roomMap.get(roomId);
        if (r == null) {
            return false;
        }
        List<String> waiters = r.getWaitNextHand();
        if (waiters == null) {
            waiters = new ArrayList<>();
            r.setWaitNextHand(waiters);
        }
        int cap = r.getMaxSeatCount() - seatedCountForSeatCap(r) - waiters.size();
        if (cap <= 0) {
            return false;
        }
        int n = Math.min(count, cap);
        try {
            int firstSeq = r.allocateBotNicknameSeqBatch(n);
            for (int i = 0; i < n; i++) {
                String nick = DpNpcEngine.ruleBotNicknameForKey(archetypeKey, firstSeq + i);
                if (!waiters.contains(nick)) {
                    waiters.add(nick);
                }
            }
        } catch (IllegalArgumentException ex) {
            return false;
        }
        refreshJoinableQuickMatchIndexRoom(roomId, System.currentTimeMillis());
        return true;
    }

    /**
     * 将大模型 NPC 加入下一局；{@code BOT_LLM_<房间序号>}，决策仅走 {@link DpLlmNpcDecisionService}。
     */
    public boolean addLlmBotToNextHand(String roomId) {
        DpRoomBO r = roomMap.get(roomId);
        if (r == null) {
            return false;
        }
        int seq = r.allocateBotNicknameSeqBatch(1);
        return readyNextHand(roomId, DpNpcEngine.llmBotNickname(seq), null);
    }

    /**
     * 将「全局叙事」大模型 NPC 加入下一局；{@code BOT_LLM_GLOBAL_<房间序号>}，决策与同文件内 BOT_LLM 一样走 {@link DpLlmNpcDecisionService}。
     */
    public boolean addGlobalLlmBotToNextHand(String roomId) {
        DpRoomBO r = roomMap.get(roomId);
        if (r == null) {
            return false;
        }
        int seq = r.allocateBotNicknameSeqBatch(1);
        return readyNextHand(roomId, DpNpcEngine.llmGlobalBotNickname(seq), null);
    }

    // ========== 构造与全局 1s 节拍 ==========

    // 轮询入口
    public DpRoomServiceImpl(
            // 注入服务
            DpHandHistoryPersistService observedHandPersistService,
            DpLlmNpcDecisionService llmNpcDecisionService,
            DpGameRoomPushService gameRoomPushService,
            DpUserMapper dpUserMapper,
            DpHandHistoryObservedService observedHandService,
            DpRoomHallService dpRoomHallService,
            ObjectMapper objectMapper,
            DpQuickMatchPushService quickMatchPush,
            DpFriendPresenceService friendPresence) {
        this.observedHandPersistService = observedHandPersistService;
        this.llmNpcDecisionService = llmNpcDecisionService;
        this.gameRoomPushService = gameRoomPushService;
        this.dpUserMapper = dpUserMapper;
        this.observedHandService = observedHandService;
        this.dpRoomHallService = dpRoomHallService;
        this.objectMapper = objectMapper;
        this.quickMatchPush = quickMatchPush;
        this.friendPresence = friendPresence;
        // 心跳清理 + 超时行动（单测见 {@link #suppressGlobalRoomTimerForTests}）
        if (!suppressGlobalRoomTimerForTests) {
            new Timer().scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    for (DpRoomBO room : roomMap.values()) {
                        runGlobalSecondTickForSingleRoom(room);
                    }
                }
            }, 0, 1000);
        }
    }

    /**
     * 仅单测：为 true 时不启动构造内 1s Timer，避免后台线程与计时竞态。生产须保持 false。
     */
    static volatile boolean suppressGlobalRoomTimerForTests = false;

    // ========== A. 全局 1s 节拍（构造内 Timer：编排薄、子步骤提取）==========

    /**
     * Timer 每秒对单房的编排：顺序、{@code continue}/早退与原匿名 {@link TimerTask#run()} 一致。
     */
    private void runGlobalSecondTickForSingleRoom(DpRoomBO room) {
        //踢超时桌上人
        boolean lobbyDirty = tickEvictStaleSeatedPlayersOnHeartbeat(room);
        //踢超时观众
        if (tickEvictStaleSpectatorsOnHeartbeat(room)) {
            lobbyDirty = true;
        }
        //检查可解散房间
        if (removeDesertedRoomInGlobalTickIfNoLiveHumans(room)) {
            return;
        }
        tickNpcTurnOrHumanActionTimeout(room);
        tickSettledBotsAutoReady(room);
        //剩一人没准备就不踢
        if (skipBroadcastForLoneSettledPlayerWaitingNothing(room)) {
            return;
        }
        //超时准备的人
        maybeInvokeReadyTimeoutWhenDeadlinePassed(room);
        broadcastRoomAndMaybeRefreshLobbyAfterHeartbeatTick(room, lobbyDirty);
    }

    /** 验真人，心跳超时移除房间，没断ws。 */
    private boolean tickEvictStaleSeatedPlayersOnHeartbeat(DpRoomBO room) {
        boolean lobbyDirty = false;
        Iterator<DpPlayer> it = room.getPlayers().iterator();
        while (it.hasNext()) {
            DpPlayer p = it.next();
            // 演示用机器人不依赖前端心跳，直接视为始终在线
            if (DpNpcEngine.isBotPlayer(p)) {
                p.setLastHeartBeat(System.currentTimeMillis());
                continue;
            }
            // 本手已离线的“占位”玩家不因心跳踢出，留到结算时再移除，以保持行动顺序
            if (p.isLeftThisHand())
                continue;
            if (System.currentTimeMillis() - p.getLastHeartBeat() > DpRoomBO.getHeartTimeout()) {
                if (p.getNickname().equals(room.getOwner())) {
                    giveOwner(room.getRoomId(), p.getNickname());
                }
                System.out.println("未收到" + p.getNickname() + "的心跳,已移除房间");
                String hbNick = p.getNickname();
                Integer hbUid = p.getDpUserId();
                it.remove();
                // 已从房内移除：立即关断该用户对本房的 WS，否则仍收推送且前端以为还在桌
                // gameRoomPushService.shutdownSubscriptionsForNicknameInRoom(room.getRoomId(), hbNick);
                presenceTryMarkIdleFullyLeft(hbNick, hbUid, room, "heartbeat_evict_player");
                lobbyDirty = true;
            }
        }
        return lobbyDirty;
    }

    /** 验观众，移除房间中观众 */
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
                if (exitRoom(room.getRoomId(), specNick)) {
                    lobbyDirty = true;
                }
            }
        }
        return lobbyDirty;
    }

    /** 无活人且无观众则摘房；原逻辑在拆房后 {@code continue} 跳过本秒余下步骤。 */
    private boolean removeDesertedRoomInGlobalTickIfNoLiveHumans(DpRoomBO room) {
        int size = liveHumanTableCount(room);// 检测活人逻辑（与 exitRoom / removeRoom 拆房口径一致）
        // System.out.println("定时器检测：人数："+size);
        // System.out.println("正在检测房间:"+room.getRoomId());
        if (size == 0 && room.getSpectators().isEmpty()) {
            System.out.println("定时器检测：房间：" + room.getRoomId() + "没活人了");
            System.out.println("房间Id"+room.getRoomId());
            removeRoom(room.getRoomId());// 房间空了就清人
            return true;
        }
        return false;
    }

    /** 当前行动位：NPC 决策或真人超时弃牌。 */
    private void tickNpcTurnOrHumanActionTimeout(DpRoomBO room) {
        // 30秒超时弃牌；若当前行动位是已离线占位，直接跳过不等待
        if (room.isPlaying()
                && room.getCurrentActorIndex() >= 0
                && room.getCurrentActorIndex() < room.getPlayers().size()) {
            DpPlayer p = room.getPlayers().get(room.getCurrentActorIndex());
            // 如果当前行动者是 NPC，则直接由后端自动决策行动，不等待前端操作
            if (DpNpcEngine.isBotPlayer(p)) {// 决策入口：BOT_LLM 与普通 NPC 分离
                DpNpcEngine.BotAction action;
                // 如果是BOT_LLM则走大模型逻辑
                if (DpNpcEngine.isLlmBotNickname(p.getNickname())) {
                    action = llmNpcDecisionService.decideActionIfReady(room, p);
                    NpcAction(room, p, action);
                } else {
                    action = DpNpcEngine.decideActionIfReady(room, p);
                    NpcAction(room, p, action);
                }

            } else {
                if (p.isLeftThisHand()) {
                    moveToNextValidActor(room);
                    autoAdvanceIfRoundFinished(room);
                } else if (System.currentTimeMillis() - room.getLastActionTime() > DpRoomBO
                        .getActionTimeout()) {
                    p.setFold(true);
                    moveToNextValidActor(room);
                    autoAdvanceIfRoundFinished(room);
                }
            }
        }
    }

    /** 结算后准备阶段：机器人自动补码并自动准备。 */
    private void tickSettledBotsAutoReady(DpRoomBO room) {
        if (room.isPlaying() && "settled".equals(room.getCurrentStage())) {
            boolean botTouched = false;
            // 只有场上的NPC才能自动准备，踢走的不准备，遍历所有机器人
            for (DpPlayer p : room.getPlayers()) {
                if (!DpNpcEngine.isBotPlayer(p))
                    continue;
                // 若筹码不足大盲，自动补码（内部已有阶段与筹码校验）
                if (p.getChips() < room.getBigBlindChips()) {
                    rebuy(room.getRoomId(), p.getNickname());
                }
                // 筹码充足时自动准备
                if (p.getChips() >= room.getBigBlindChips() && !p.isReady()
                        && !(room.getPlayers().size() == 1 && room.getWaitNextHand().isEmpty())) {// 防止机器人空转，一直发牌准备循环，白白浪费资源
                    // 这里插入一个功能，当机器人行动之前，先看下场上玩家数是否为1且准备下一把是否为空，防止机器人空转，一直发牌准备循环，白白浪费资源
                    p.setReady(true);
                    botTouched = true;// 有机器人准备状态被更新

                }
                // System.out.println("准备状态是"+botTouched);
            }
            // 只要有机器人准备状态被更新，就检查是否可以直接开下一局
            if (botTouched) {
                if (checkAndStartNextHandAfterSettleReturning(room)) {
                    refreshJoinableQmIndexThenSyncLobby(room.getRoomId());
                }
            }
        }
    }

    /**
     * settled 且准备窗口内仅一人且无 waiter 时，原实现 {@code continue} 跳过广播与大厅刷新。
     *
     * @return true 时调用方须跳过后续 {@link #broadcastRoomAndMaybeRefreshLobbyAfterHeartbeatTick}。
     */
    private boolean skipBroadcastForLoneSettledPlayerWaitingNothing(DpRoomBO room) {
        return room.isPlaying()
                && "settled".equals(room.getCurrentStage())
                && room.getReadyDeadline() > 0
                && System.currentTimeMillis() > room.getReadyDeadline()
                && room.getPlayers().size() == 1
                && room.getWaitNextHand().isEmpty();
    }

    /** 准备倒计时到期：调用 {@link #handleReadyTimeout}（ lone 情形已由 {@link #skipBroadcastForLoneSettledPlayerWaitingNothing} 早退）。 */
    private void maybeInvokeReadyTimeoutWhenDeadlinePassed(DpRoomBO room) {
        if (room.isPlaying()
                && "settled".equals(room.getCurrentStage())
                && room.getReadyDeadline() > 0
                && System.currentTimeMillis() > room.getReadyDeadline()) {// 当前时间戳大于准备倒计时时间戳，则进入准备超时逻辑
            handleReadyTimeout(room);
        }
    }
/**
 * 断开不在房间的ws，推送去重json，更新可用房和大厅索引
 */
    private void broadcastRoomAndMaybeRefreshLobbyAfterHeartbeatTick(DpRoomBO room, boolean lobbyDirty) {
        // 已学习，调用websocket的gameRoomPushService.broadcastIfSubscribed(room.getRoomId())广播房间数据给所有订阅者
        gameRoomPushService.broadcastIfSubscribed(room.getRoomId());
        if (lobbyDirty) {
            refreshJoinableQmIndexThenSyncLobby(room.getRoomId());
        }
    }

    public JoinableQuickMatchRoomIndex getJoinableQuickMatchRoomIndex() {
        return joinableQuickMatchRoomIndex;
    }

    // ========== B. 快匹可进索引 + 大厅摘要 ==========

    /**
     * 全量重建快匹可进索引（遍历 {@link #roomMap}）。怀疑索引与房态不一致时调用。
     */
    public void rebuildJoinableQuickMatchIndex(long nowMs) {
        joinableQuickMatchRoomIndex.rebuildAll(roomMap, nowMs);
    }

    /** 进程启动后从当前 {@link #roomMap} 回填快匹索引，避免重启后索引空窗。 */
    @PostConstruct
    void rebuildJoinableQuickMatchIndexAfterBeanReady() {
        rebuildJoinableQuickMatchIndex(System.currentTimeMillis());
    }

    /**
     * 按当前 BO 更新或摘除快匹可进条目。禁止在持有 {@code synchronized(room)} 时调用，以免锁序变为「监视器先于索引」（死锁）。
     */
    private void refreshJoinableQuickMatchIndexRoom(String roomId, long nowMs) {
        if (roomId == null || roomId.isEmpty()) {
            return;
        }
        DpRoomBO live = roomMap.get(roomId);
        if (live == null) {
            joinableQuickMatchRoomIndex.remove(roomId);
        } else {
            joinableQuickMatchRoomIndex.addOrRefresh(roomId, live, nowMs);
        }
    }

    /**
     * 刷新（更新）快匹可进房间索引，并同步大厅信息。
     * 作用：首先根据最新房间状态刷新快匹索引（让大厅、快匹页显示正确），
     * 然后同步该房间相关的大厅信息（如房间人数、当前状态等）。
     *
     * @param roomId 房间ID，不能为空
     */
    private void refreshJoinableQmIndexThenSyncLobby(String roomId) {
        // 先刷新快匹索引，让大厅中的匹配房间列表及时更新
        refreshJoinableQuickMatchIndexRoom(roomId, System.currentTimeMillis());
        // 再同步该房间的大厅数据（如：房间摘要、状态推送等）
        syncLobbyForRoomId(roomId);
    }

    /**
     * 房间删除后处理
     */
    private void finalizeHallAfterRoomRemoved(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return;
        }
        joinableQuickMatchRoomIndex.remove(roomId);
        dpRoomHallService.deleteRoomSummary(roomId);
        gameRoomPushService.shutdownSubscriptionsForRoom(roomId);
    }

    /**
     * 空房且无观众时摘除 {@link #roomMap} 条目。必须在已持有 {@code synchronized(r)} 下调用；
     * 成功返回 true 时，调用方在释放 intrinsic 监视器之后须
     * {@link #finalizeHallAfterRoomRemoved(String)}。
     */
    private boolean tryUnregisterEmptyRoomAssumeLocked(DpRoomBO r, String roomId) {
        if (r == null || roomId == null || roomId.isEmpty()) {
            return false;
        }
        if (roomMap.get(roomId) != r) {
            return false;
        }
        if (liveHumanTableCount(r) > 0
                || (r.getSpectators() != null && !r.getSpectators().isEmpty())) {
            return false;
        }
        roomMap.remove(roomId);
        return true;
    }

    /** 组装快匹候选：先按索引桶序（缺人越少越先，同桶 roomId 序），再以历史全表扫描兜底。 */
    private List<DpRoomBO> orderedQuickMatchJoinCandidates(long sortTimeMs) {
        LinkedHashMap<String, DpRoomBO> ordered = new LinkedHashMap<>();
        for (int shortage = 1; shortage <= DpRoomBO.MAX_SEAT_COUNT; shortage++) {
            for (String rid : joinableQuickMatchRoomIndex.roomIdsInBucket(shortage)) {
                DpRoomBO x = rid == null ? null : roomMap.get(rid);
                if (x != null && quickMatchEligiblePublicSlice(x, sortTimeMs)) {
                    ordered.putIfAbsent(rid, x);
                }
            }
        }
        List<DpRoomBO> scanned = roomMap.values().stream()
                .filter(rr -> rr != null && quickMatchEligiblePublicSlice(rr, sortTimeMs))
                .sorted(quickMatchCandComparator(sortTimeMs))
                .toList();
        int fallbackAdds = 0;
        for (DpRoomBO r : scanned) {
            if (!ordered.containsKey(r.getRoomId())) {
                ordered.put(r.getRoomId(), r);
                fallbackAdds++;
            }
        }
        if (fallbackAdds > 0) {
            log.debug(
                    "quickMatch fallback scan added {} candidates not present in JoinableQuickMatchRoomIndex (drift repair)",
                    fallbackAdds);
        }
        return new ArrayList<>(ordered.values());
    }

    private static boolean quickMatchEligiblePublicSlice(DpRoomBO r, long nowMs) {
        return !r.isPasswordProtected() && DpQuickMatchRoomSemantics.rawVacancyForQuickMatch(r, nowMs) > 0;
    }

    private static Comparator<DpRoomBO> quickMatchCandComparator(long sortTimeMs) {
        return (a, b) -> {
            int fa = DpQuickMatchRoomSemantics.quickMatchFillScore(a, sortTimeMs);
            int fb = DpQuickMatchRoomSemantics.quickMatchFillScore(b, sortTimeMs);
            if (fb != fa) {
                return Integer.compare(fb, fa);
            }
            return a.getRoomId().compareTo(b.getRoomId());
        };
    }

    // ========== 工具方法 ==========
    private void NpcAction(DpRoomBO room, DpPlayer p, DpNpcEngine.BotAction action) {
        if (action != null) {
            String roomId = room.getRoomId();
            switch (action.getType()) {
                case FOLD:
                    fold(roomId, p.getNickname());
                    break;
                case CALL_OR_CHECK: {
                    int callAmount = Math.max(0, room.getCurrentBetToCall() - p.getBet());
                    bet(roomId, p.getNickname(), callAmount);
                    break;
                }
                case RAISE:
                case ALL_IN: {
                    int amount = Math.max(0, action.getAmount());
                    if (action.getType() == DpNpcEngine.BotActionType.RAISE) {
                        amount = clampRaiseAmountForLegal(room, p, amount);
                    }
                    bet(roomId, p.getNickname(), amount);
                    break;
                }
                default:
                    break;
            }
        }
    }

    private List<String> newDeck() {
        List<String> deck = new ArrayList<>();
        String[] suits = { "hearts", "diamonds", "clubs", "spades" };
        String[] ranks = { "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A" };
        for (String s : suits) {
            for (String r : ranks) {
                deck.add(s + "_" + r);
            }
        }
        Collections.shuffle(deck);
        return deck;
    }

    private void syncLobbyForRoomId(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return;
        }
        DpRoomBO room = roomMap.get(roomId);
        if (room == null) {
            return;
        }
        dpRoomHallService.upsertRoomSummary(room);
    }

    /**
     * 拿锁清理房间并刷新缓存
     */
    private void removeRoom(String roomId) {
        System.out.println("清理房间触发");
        if (roomId == null || roomId.isEmpty()) {
            return;
        }
        DpRoomBO r = roomMap.get(roomId);
        if (r == null) {
            return;
        }
        boolean dropped;
        synchronized (r) {
            dropped = tryUnregisterEmptyRoomAssumeLocked(r, roomId);
            System.out.println("dropped: " + dropped);
        }
        if (dropped) {
            finalizeHallAfterRoomRemovedWithPresenceSnapshot(roomId, r);
        }
    }

    /**
     * 房主主动移交房主给房间内另一位玩家
     */
    public boolean transferOwner(String roomId, String fromNickname, String toNickname) {
        DpRoomBO r = roomMap.get(roomId);
        if (r == null)
            return false;
        synchronized (r) {
            // 只有当前房主可以发起移交
            if (!fromNickname.equals(r.getOwner()))
                return false;
            if (fromNickname.equals(toNickname))
                return false;
            if (DpNpcEngine.isBotNickname(toNickname))
                return false;

            // 目标：在座且本手未退的真人，或观众席中的真人（与 giveOwner 一致）
            boolean found = false;
            for (DpPlayer p : r.getPlayers()) {
                if (p.getNickname().equals(toNickname) && !p.isLeftThisHand() && !DpNpcEngine.isBotPlayer(p)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                List<String> spectators = r.getSpectators();
                if (spectators != null && spectators.contains(toNickname)) {
                    found = true;
                }
            }
            if (!found)
                return false;

            r.setOwner(toNickname);
            System.out.println("房间 " + r.getRoomId() + " 房主由 " + fromNickname + " 移交给: " + toNickname);
        }
        refreshJoinableQmIndexThenSyncLobby(roomId);
        return true;
    }

    private static final class GiveOwnerMutationOutcome {
        final boolean droppedFromRoomMap;
        final boolean ownerFieldChanged;

        GiveOwnerMutationOutcome(boolean droppedFromRoomMap, boolean ownerFieldChanged) {
            this.droppedFromRoomMap = droppedFromRoomMap;
            this.ownerFieldChanged = ownerFieldChanged;
        }

        GiveOwnerMutationOutcome mergedWith(GiveOwnerMutationOutcome other) {
            return new GiveOwnerMutationOutcome(
                    this.droppedFromRoomMap || other.droppedFromRoomMap,
                    this.ownerFieldChanged || other.ownerFieldChanged);
        }
/**
 * droppedFromRoomMap：房间是否被移除。
ownerFieldChanged：房主字段是否发生变化。
 * @return
 */
        static GiveOwnerMutationOutcome none() {
            return new GiveOwnerMutationOutcome(false, false);
        }
    }

    /** {@link #exitRoom(String, String)} 在 {@code synchronized(r)} 内求值的结果快照（便于编排外层大厅/presence）。 */
    private static final class ExitRoomSynchronizedOutcome {
        final boolean result;
        final boolean droppedEmpty;
        final boolean lobbyTouch;
        final GiveOwnerMutationOutcome giveAgg;
        final Integer presenceHintUid;

        ExitRoomSynchronizedOutcome(boolean result, boolean droppedEmpty, boolean lobbyTouch,
                GiveOwnerMutationOutcome giveAgg, Integer presenceHintUid) {
            this.result = result;
            this.droppedEmpty = droppedEmpty;
            this.lobbyTouch = lobbyTouch;
            this.giveAgg = giveAgg;
            this.presenceHintUid = presenceHintUid;
        }
    }

    /**
     * 前提：已由调用方持有 {@code synchronized(room)}。处理房主离开后易位或无活人摘房；
     * 若摘房成功后须由调用方在释放监视器之后 {@link #finalizeHallAfterRoomRemoved(String)}（或令
     * {@link #removeRoom} / 外层 exit 统一收尾）。
     */
    private GiveOwnerMutationOutcome applyGiveOwnerWhileRoomLocked(DpRoomBO room, String outgoingOwnerNickname) {
        if (room == null) {
            return GiveOwnerMutationOutcome.none();
        }
        int tableHumans = 0;
        for (DpPlayer getActivePlayer : room.getPlayers()) {
            if (!getActivePlayer.isLeftThisHand() && !DpNpcEngine.isBotPlayer(getActivePlayer)) {
                tableHumans++;
                System.out.println("退出按钮检测：" + getActivePlayer.getNickname() + "是活人");
            }
        }
        int spectatorHumans = 0;
        List<String> spectators = getNewSpectators(room);
        for (String nick : spectators) {
            if (!DpNpcEngine.isBotNickname(nick)) {
                spectatorHumans++;
            }
        }
        if (tableHumans + spectatorHumans == 0) {
            System.out.println("giveOwner：没有活人了");
            boolean dropped = tryUnregisterEmptyRoomAssumeLocked(room, room.getRoomId());
            return new GiveOwnerMutationOutcome(dropped, false);
        }
        String roomId = room.getRoomId();
        for (DpPlayer candidate : room.getPlayers()) {
            if (!candidate.getNickname().equals(outgoingOwnerNickname) && !candidate.isLeftThisHand()
                    && !DpNpcEngine.isBotPlayer(candidate)) {
                room.setOwner(candidate.getNickname());
                System.out.println("giveOwner：房间 " + roomId + " 房主易位给: " + candidate.getNickname());
                return new GiveOwnerMutationOutcome(false, true);
            }
        }
        for (String specNick : spectators) {
            if (!specNick.equals(outgoingOwnerNickname) && !DpNpcEngine.isBotNickname(specNick)) {
                room.setOwner(specNick);
                System.out.println("giveOwner：房间 " + roomId + " 房主易位给观众: " + specNick);
                return new GiveOwnerMutationOutcome(false, true);
            }
        }
        return GiveOwnerMutationOutcome.none();
    }

    // ========== 顺位移交房主操作 =========

    /** 定时器与其它未持房监视器的入口：内部单房串行并完成索引→大厅链路。 */
    public void giveOwner(String roomId, String ownerNickname) {
        DpRoomBO room = roomMap.get(roomId);
        if (room == null) {
            return;
        }
        GiveOwnerMutationOutcome outcome;
        synchronized (room) {
            outcome = applyGiveOwnerWhileRoomLocked(room, ownerNickname);
        }
        if (outcome.droppedFromRoomMap) {
            finalizeHallAfterRoomRemovedWithPresenceSnapshot(roomId, room);
        } else if (outcome.ownerFieldChanged) {
            refreshJoinableQmIndexThenSyncLobby(roomId);
        }
    }

    /**
     * 房主踢人至观众席（单人单请求）
     */
    public boolean kickPlayer(String roomId, String nickname) {
        boolean ok = kickOnePlayerWithoutLobbySync(roomId, nickname);
        if (ok) {
            refreshJoinableQmIndexThenSyncLobby(roomId);
        }
        return ok;
    }

    /**
     * 房主批量踢人：昵称用英文逗号分隔，去重后顺序执行，成功至少一人时统一同步大厅一次。
     */
    public KickPlayersBatchResult kickPlayersBatch(String roomId, String nicknamesCsv) {
        KickPlayersBatchResult out = new KickPlayersBatchResult();
        if (roomId == null || roomId.isBlank()) {
            return out;
        }
        if (nicknamesCsv == null || nicknamesCsv.isBlank()) {
            return out;
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String part : nicknamesCsv.split(",")) {
            if (part == null) {
                continue;
            }
            String t = part.trim();
            if (!t.isEmpty()) {
                unique.add(t);
            }
        }
        if (unique.isEmpty()) {
            return out;
        }
        out.attempted = unique.size();
        boolean anyOk = false;
        for (String nickname : unique) {
            if (kickOnePlayerWithoutLobbySync(roomId, nickname)) {
                out.successCount++;
                anyOk = true;
            } else {
                out.failCount++;
                out.failedNicknames.add(nickname);
            }
        }
        if (anyOk) {
            refreshJoinableQmIndexThenSyncLobby(roomId);
        }
        return out;
    }

    /**
     * 单次踢人核心逻辑，不触发大厅同步（供 {@link #kickPlayer} 与 {@link #kickPlayersBatch} 复用）。
     */
    private boolean kickOnePlayerWithoutLobbySync(String roomId, String nickname) {
        DpRoomBO r = roomMap.get(roomId);
        if (r == null) {
            return false;
        }
        int idx = -1;
        List<DpPlayer> ps = r.getPlayers();
        for (int i = 0; i < ps.size(); i++) {
            DpPlayer p = ps.get(i);
            if (p.getNickname().equals(nickname)) {
                idx = i;
                break;
            }
        }
        if (idx == -1) {
            return false;
        }
        // fold 可能触发结算 → removeLeftThisHandZombiesAfterHand 对同一 List removeIf，
        // 批量踢人时已离桌位会被摘掉，idx 会失效；后续必须对已解析的 DpPlayer 引用操作。
        DpPlayer kicked = ps.get(idx);
        if (r.getCurrentActorIndex() == idx) {
            fold(roomId, nickname);
        } else {
            if (!kicked.isFold()) {
                kicked.setFold(true);
            }
        }
        kicked.setReady(false);
        kicked.setLeftThisHand(true);
        if (!DpNpcEngine.isBotPlayer(kicked)) {
            List<String> spectators = getNewSpectators(r);
            if (!spectators.contains(nickname)) {
                spectators.add(nickname);
            }
            if (!DpNpcEngine.isBotNickname(nickname)) {
                r.touchSpectatorPresence(nickname, System.currentTimeMillis());
            }
        }
        return true;
    }

    /** 批量踢人结果 */
    public static final class KickPlayersBatchResult {
        private int attempted;
        private int successCount;
        private int failCount;
        private final List<String> failedNicknames = new ArrayList<>();

        public int getAttempted() {
            return attempted;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getFailCount() {
            return failCount;
        }

        public List<String> getFailedNicknames() {
            return failedNicknames;
        }
    }

    // ======== 获取观众席防null版本 =========
    public List<String> getNewSpectators(DpRoomBO r) {
        List<String> spectators = r.getSpectators();
        if (spectators == null) {
            spectators = new ArrayList<>();
            r.setSpectators(spectators);
        }
        return spectators;
    }

    /**
     * 统一的行动者轮转方法
     * 跳过：已弃牌、已全下、已行动且下注已跟齐 的玩家
     */
    private void moveToNextValidActor(DpRoomBO r) {
        int size = r.getPlayers().size();// 玩家数量
        int startIdx = r.getCurrentActorIndex();// 当前行动者下标
        // 这里是检测本轮剩两个玩家时，当前玩家弃牌之后，应该直接推进，而不是等另一个玩家点一下过牌
        // 方法一：
        if (countPlayersStillInHand(r) == 1) {
            // System.out.println("走弃牌直接结算逻辑");
            r.setCurrentActorIndex(-1);
            return;
        }
        // 方法二：
        // int outGameCount = 0;//算僵尸牌和弃牌者数量
        // for(DpPlayer p : r.getPlayers()){
        // if(p.isLeftThisHand() || p.isFold()){
        // outGameCount++;
        // }
        // }

        // if(outGameCount+1==r.getPlayers().size()){//如果差一个，就走直接弃牌逻辑，这里先算不合法的数量，因为算合法的数量会造成正常对局最后一个玩家不能行动
        // System.out.println("走弃牌直接结算逻辑");
        // System.out.println("僵尸和弃牌者数量为:"+outGameCount);
        // r.setCurrentActorIndex(-1);
        // return;
        // }
        // 方法三：
        // if(isOnlyOnePlayerStillInHandAndNotActedAndFold(r)){
        // System.out.println("走弃牌直接结算逻辑");
        // r.setCurrentActorIndex(-1);
        // return;
        // }
        for (int i = 1; i <= size; i++) {// 循环玩家数量
            int nextIdx = (startIdx + i) % size;
            DpPlayer nextP = r.getPlayers().get(nextIdx);// 下一个玩家

            // 跳过已离线位；没弃牌、没全下、且（还没行动 或 下注不够）
            if (nextP.isLeftThisHand())
                continue;
            if (!nextP.isFold() && !nextP.isAllIn()// 没弃牌、没全下
                    && (!nextP.isActed() || nextP.getBet() < r.getCurrentBetToCall())) {// 没行动或下注不够
                r.setCurrentActorIndex(nextIdx);// 设置下一个玩家为当前行动者
                r.setLastActionTime(System.currentTimeMillis());// 设置上次行动时间
                return;
            }
        }

        r.setCurrentActorIndex(-1); // 本轮所有人都行动完毕
    }

    /**
     * 计算主池和边池
     * 在进入 showdown 时调用
     */
    private void calculatePots(DpRoomBO r) {
        List<Integer> levels = r.getPlayers().stream()
                .map(DpPlayer::getTotalBet)
                .filter(b -> b > 0)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        List<DpPot> pots = new ArrayList<>();
        int prevLevel = 0;

        for (int level : levels) {
            int potAmount = 0;
            List<String> eligible = new ArrayList<>();

            for (DpPlayer p : r.getPlayers()) {
                int contribution = Math.min(p.getTotalBet(), level) - Math.min(p.getTotalBet(), prevLevel);
                potAmount += contribution;
                // 没弃牌 且 totalBet >= 这一层，才有资格赢这个池
                if (!p.isFold() && p.getTotalBet() >= level) {
                    eligible.add(p.getNickname());
                }
            }

            if (potAmount > 0) {
                DpPot pot = new DpPot();
                pot.setAmount(potAmount);
                pot.setEligiblePlayers(eligible);
                pots.add(pot);
            }
            prevLevel = level;
        }

        r.setPots(pots);
    }

    /**
     * 占「人数上限」的桌上席位数：本手已离座（{@code leftThisHand}）仅占位维持本手流程，下一局会释放，
     * 预约「下一局上桌」时应视为空出席位。
     */
    private static int seatedCountForSeatCap(DpRoomBO r) {
        if (r == null || r.getPlayers() == null) {
            return 0;
        }
        int n = 0;
        for (DpPlayer p : r.getPlayers()) {
            if (p != null && !p.isLeftThisHand()) {
                n++;
            }
        }
        return n;
    }

    // ========== 开局前：房间与准备 ==========

    public DpRoomBO createRoom(String ownerNickname, Integer ownerUserId,
            int smallBlindChips, int bigBlindChips, int startingStackBb, String roomPassword,
            int maxSeatCount) {
        cancelDefaultQuickMatchWait(ownerNickname);
        String id = UUID.randomUUID().toString().substring(0, 8);// 随机生成的id?
        DpRoomBO r = new DpRoomBO();
        r.setRoomId(id);
        r.setOwner(ownerNickname);
        int sb = Math.max(1, smallBlindChips);
        int bb = Math.max(sb * 2, bigBlindChips);
        if (sb > 1_000_000) {
            sb = 1_000_000;
        }
        if (bb > 2_000_000) {
            bb = 2_000_000;
        }
        if (bb < sb * 2) {
            bb = sb * 2;
        }
        r.setSmallBlindChips(sb);
        r.setBigBlindChips(bb);
        int stackBb = Math.max(5, Math.min(100_000, startingStackBb));
        r.setStartingStackBb(stackBb);
        long startingLong = (long) bb * (long) stackBb;
        int starting = startingLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) startingLong;
        r.setStartingChips(starting);
        r.setRoomPassword(roomPassword);
        r.setMaxSeatCount(maxSeatCount);

        DpPlayer p = new DpPlayer();
        p.setNickname(ownerNickname);
        p.setReady(true);
        p.setChips(starting);
        Integer uid = resolveAndValidateUserId(ownerUserId, ownerNickname);
        if (uid != null) {
            p.setDpUserId(uid);
            r.putRegisteredDpUserId(ownerNickname, uid);
        }
        r.getPlayers().add(p);
        roomMap.put(id, r);
        refreshJoinableQuickMatchIndexRoom(id, System.currentTimeMillis());
        syncLobbyForRoomId(id);
        if (roomPassword == null) {
            attemptQuickMatchPairing();
        }
        if (uid != null) {
            friendPresence.markInGame(uid, "create_room");
        }
        return r;
    }

    /**
     * 检查昵称id合法性
     * 前端传入的 userId 必须与昵称在 dp_user 中一致，否则忽略（防伪造）。
     * 校验依赖数据库；若 Mapper/JDBC 异常则记录日志并返回 null，避免因「已写完内存状态却因校验抛错」导致 500
     * （典型：游戏中进房的观众已写入 spectators，再查库失败）。
     */
    private Integer resolveAndValidateUserId(Integer requestedUserId, String nickname) {
        if (requestedUserId == null || nickname == null) {
            return null;
        }
        try {
            DpUser u = dpUserMapper.selectById(requestedUserId);
            if (u == null || !nickname.equals(u.getNickname())) {
                return null;
            }
            return requestedUserId;
        } catch (Exception e) {
            log.warn("resolveAndValidateUserId db error userId={}, nickname={}: {}",
                    requestedUserId, nickname, e.toString());
            return null;
        }
    }

    /**
     * Presence 解析：优先校验通过的请求 userId；否则房内登记；再兜底 dp_user（避免仅有昵称链路无法落键）。
     */
    private Integer resolvePresenceUserIdPreferHint(String nickname, Integer hintedUserId, DpRoomBO room) {
        if (nickname == null || DpNpcEngine.isBotNickname(nickname)) {
            return null;
        }
        Integer validated = resolveAndValidateUserId(hintedUserId, nickname);
        if (validated != null) {
            return validated;
        }
        if (room != null && room.getRegisteredDpUserId(nickname) != null) {
            return room.getRegisteredDpUserId(nickname);
        }
        try {
            DpUser row = dpUserMapper.selectByNickname(nickname);
            return row != null ? row.getId() : null;
        } catch (Exception e) {
            log.warn("resolvePresenceUserIdPreferHint db error nickname={}: {}", nickname, e.toString());
            return null;
        }
    }

    private void presenceMarkInGameHuman(DpRoomBO r, String nickname, Integer requestedUserId, String trigger) {
        Integer uid = resolvePresenceUserIdPreferHint(nickname, requestedUserId, r);
        if (uid != null) {
            friendPresence.markInGame(uid, trigger);
        }
    }

    /**
     * 当昵称已不在任何房间的 players/spectators 中时置 IDLE（离房幂等；多路径重复调用无害）。
     */
    private void presenceTryMarkIdleFullyLeft(String nickname, Integer hintedUserId, DpRoomBO roomHint, String trigger) {
        if (nickname == null || DpNpcEngine.isBotNickname(nickname)) {//昵称为空返回
            return;
        }
        if (findRoomContainingNickname(nickname) != null) {//发现房间包含自己名字，直接返回
            return;
        }
        Integer uid = resolvePresenceUserIdPreferHint(nickname, hintedUserId, roomHint);
        if (uid != null) {
            //昵称合法就设置空闲状态
            friendPresence.markIdle(uid, trigger);
        }
    }
/**
 * 
 这个方法会遍历房间内的所有真实用户（玩家和观众），把他们的在线状态标记为“空闲”（IDLE）。这样做的目的是：在房间快照被处理时，确保所有非机器人用户被正确置为离开房间或空闲状态，便于后续的状态管理。
 * @param r
 * @param trigger
 */
    private void presenceMarkIdleAllHumansOnRoomSnapshot(DpRoomBO r, String trigger) {
        if (r == null) {
            return;
        }
        LinkedHashSet<Integer> seenUserIds = new LinkedHashSet<>();
        if (r.getPlayers() != null) {
            for (DpPlayer p : r.getPlayers()) {
                if (p == null || DpNpcEngine.isBotPlayer(p)) {
                    continue;
                }
                Integer uid = p.getDpUserId() != null ? p.getDpUserId()
                        : resolvePresenceUserIdPreferHint(p.getNickname(), null, r);
                if (uid != null && seenUserIds.add(uid)) {
                    friendPresence.markIdle(uid, trigger);
                }
            }
        }
        List<String> specs = r.getSpectators();
        if (specs != null) {
            for (String nick : specs) {
                if (nick == null || DpNpcEngine.isBotNickname(nick)) {
                    continue;
                }
                Integer uid = resolvePresenceUserIdPreferHint(nick, null, r);
                if (uid != null && seenUserIds.add(uid)) {
                    friendPresence.markIdle(uid, trigger);
                }
            }
        }
    }

    /**
     * 退房前调用查用户Id的。
     */
    private Integer resolvePresenceHintBeforeExitMutationLocked(DpRoomBO r, String nickname) {
        if (r == null || nickname == null) {
            return null;
        }
        Integer id = r.getRegisteredDpUserId(nickname);
        if (id != null) {
            return id;
        }
        List<DpPlayer> ps = r.getPlayers();
        if (ps != null) {
            for (DpPlayer p : ps) {
                if (p != null && nickname.equals(p.getNickname())) {
                    return p.getDpUserId();
                }
            }
        }
        return null;
    }
/**
 * 一整套清理空房间方法，包括设置真人空闲状态，移除房间关闭ws连接，更新大厅索引，更新房间表
 * @param roomId
 * @param corpse
 */
    private void finalizeHallAfterRoomRemovedWithPresenceSnapshot(String roomId, DpRoomBO corpse) {
        presenceMarkIdleAllHumansOnRoomSnapshot(corpse, "room_map_remove");
        finalizeHallAfterRoomRemoved(roomId);
    }

    /**
     * 房间列表摘要：用于大厅展示所有房间（房间号 / 房主 / 在线人数）
     */
    public List<DpRoom> getAllRooms2() {
        return roomMap.values().stream()
                .map(room -> {
                    DpRoom dto = new DpRoom();
                    dto.setRoomId(room.getRoomId());
                    dto.setOwner(room.getOwner());
                    // 房间展示人数只统计“非僵尸位”的真实玩家
                    int aliveSize = (int) room.getPlayers().stream()
                            .filter(p -> !p.isLeftThisHand())
                            .count();
                    dto.setPlayerSize(aliveSize);
                    dto.setSmallBlindChips(room.getSmallBlindChips());
                    dto.setBigBlindChips(room.getBigBlindChips());
                    dto.setStartingStackBb(room.getStartingStackBb());
                    dto.setPasswordProtected(room.isPasswordProtected());
                    dto.setMaxSeatCount(room.getMaxSeatCount());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /** 当前进程内存中的房间 ID（与 dp_room_lobby 对齐任务使用） */
    public Set<String> getRoomIdsInMemory() {
        return Set.copyOf(roomMap.keySet());
    }

    public DpRoomBO getAllRooms(String roomId) {
        DpRoomBO r = roomMap.get(roomId);
        if (r == null)
            return null;
        // 当公共牌不少于 3 张时，为每位有手牌的玩家计算并填充「最大牌型的 5 张牌」，供前端展示
        List<String> community = r.getCommunityCards();
        if (community != null && community.size() >= 3) {
            for (DpPlayer p : r.getPlayers()) {
                if (p.getHoleCards() != null && p.getHoleCards().size() >= 2) {
                    List<String> all = new ArrayList<>(p.getHoleCards());
                    all.addAll(community);
                    p.setBestHandCards(DpUtilHandEvaluator.getBestHandCards(all));
                    DpUtilHandEvaluator.HandStrength hs = DpUtilHandEvaluator.evaluateBestHand(all);
                    p.setHandRankName(DpUtilHandEvaluator.rankCategoryNameZh(hs.rankCategory));
                    p.setHandRankDetail(DpUtilHandEvaluator.buildHandRankDetailZh(hs));
                } else {
                    p.setBestHandCards(Collections.emptyList());
                    p.setHandRankName("");
                    p.setHandRankDetail("");
                }
            }
        } else {
            for (DpPlayer p : r.getPlayers()) {
                p.setBestHandCards(Collections.emptyList());
                p.setHandRankName("");
                p.setHandRankDetail("");
            }
        }
        return r;
    }

    /**
     * 检测玩家在不在房间，全面检测  
     * 是否仍为该房成员：桌上玩家、观众席、或下一局候补。用于快照/推送门禁，避免已被心跳剔除的用户仍看到房间 JSON。
     */
    public boolean isNicknameInRoom(DpRoomBO room, String nickname) {
        if (room == null || nickname == null) {
            return false;
        }
        String n = nickname.trim();
        if (n.isEmpty()) {
            return false;
        }
        List<DpPlayer> players = room.getPlayers();
        if (players != null) {
            for (DpPlayer p : players) {
                if (p != null && n.equals(p.getNickname())) {
                    return true;
                }
            }
        }
        List<String> spectators = room.getSpectators();
        if (spectators != null && spectators.contains(n)) {
            return true;
        }
        List<String> wait = room.getWaitNextHand();
        return wait != null && wait.contains(n);
    }

    /**
     * 对外接口与 WebSocket 推送使用：在完整房间状态上按观看者身份隐藏他人底牌及相关推导字段，不修改内存中的房间实体。
     *
     * @param viewerNickname 当前连接者的昵称；null 或空则视为未认领身份，不展示任何玩家的真实底牌。
     */
    public DpRoomBO getRoomSnapshotForViewer(String roomId, String viewerNickname) {
        DpRoomBO live = getAllRooms(roomId);
        if (live == null) {
            return null;
        }
        if (viewerNickname != null && !viewerNickname.trim().isEmpty()
                && !isNicknameInRoom(live, viewerNickname)) {
            return null;
        }
        return snapshotForViewerFromLive(live, viewerNickname);
    }

    /**
     * 在已调用 {@link #getAllRooms(String)} 得到的实体上生成对外快照，避免同一推送周期内重复计算 bestHand。
     */
    public DpRoomBO snapshotForViewerFromLive(DpRoomBO live, String viewerNickname) {
        if (live == null) {
            return null;
        }
        DpRoomBO copy = deepCopyRoomForSnapshot(live);
        sanitizeHoleCardsForViewer(copy, viewerNickname);
        return copy;
    }

    /**
     * 深拷贝房间对象，避免修改原始对象
     */
    private DpRoomBO deepCopyRoomForSnapshot(DpRoomBO live) {
        try {
            return objectMapper.readValue(objectMapper.writeValueAsBytes(live), DpRoomBO.class);
        } catch (Exception e) {
            throw new IllegalStateException("room snapshot copy failed", e);
        }
    }

    /**
     * 仅保留「本人」或「已摊牌公开」的他人底牌；并清空由他人底牌推导的 bestHand / 牌型说明，避免间接泄露。
     */
    private static void sanitizeHoleCardsForViewer(DpRoomBO room, String viewerNickname) {
        if (room == null || room.getPlayers() == null) {
            return;
        }
        String v = viewerNickname == null ? "" : viewerNickname.trim();
        String stage = room.getCurrentStage();
        // int nonFoldIn = 0;
        // for (DpPlayer x : room.getPlayers()) {
        // if (x != null && !x.isLeftThisHand() && !x.isFold()) {
        // nonFoldIn++;
        // }
        // }
        // boolean multiWayShowdown = nonFoldIn >= 2;
        boolean multiWayShowdown = true;
        // 结算摊牌阶段可以摊牌公开底牌
        boolean revealOthers = ("showdown".equals(stage) && multiWayShowdown)
                || ("settled".equals(stage) && room.isLastHandHoleCardsPublic() && multiWayShowdown);

        for (DpPlayer p : room.getPlayers()) {
            if (p == null) {
                continue;
            }
            // 略过本人
            if (!v.isEmpty() && v.equals(p.getNickname())) {
                continue;
            }
            boolean showCards = revealOthers && !p.isFold();
            if (!showCards && !v.equals(room.getOwner())) {
                p.setHoleCards(Collections.emptyList());
                p.setBestHandCards(Collections.emptyList());
                p.setHandRankName("");
                p.setHandRankDetail("");
            }
        }
    }

    /**
     * 进房写操作本体；调用方必须已持有 {@code synchronized(room)}。不在持锁状态下更新快匹索引/大厅，
     * 由调用方在释放 {@link DpRoomBO} 监视器后调用
     * {@link #refreshQmIndexAfterJoinOutcomeOutsideRoomLock}。
     */
    private String joinRoomMutateAssumeLocked(String roomId, String nickname, Integer userId, String roomPassword,
            DpRoomBO r) {
        if (!r.matchesRoomPassword(roomPassword)) {
            return "密码错误";
        }
        if (r.isPlaying()) {
            Integer uid = resolveAndValidateUserId(userId, nickname);
            List<String> waiters = r.getWaitNextHand();
            if (waiters != null) {
                waiters.remove(nickname);
            }
            List<String> spectators = r.getSpectators();
            if (spectators == null) {
                spectators = new ArrayList<>();
                r.setSpectators(spectators);
            }
            if (!spectators.contains(nickname)) {
                spectators.add(nickname);
            }
            if (!DpNpcEngine.isBotNickname(nickname)) {
                r.touchSpectatorPresence(nickname, System.currentTimeMillis());
            }
            if (uid != null) {
                r.putRegisteredDpUserId(nickname, uid);
            }
            presenceMarkInGameHuman(r, nickname, userId, "join_room_mutate");
            return "游戏已开始";
        }
        if (r.getPlayers().size() >= r.getMaxSeatCount()) {
            return "人数已满";
        }
        DpPlayer p = new DpPlayer();
        p.setNickname(nickname);
        p.setChips(r.getStartingChips());
        Integer uid = resolveAndValidateUserId(userId, nickname);
        if (uid != null) {
            p.setDpUserId(uid);
            r.putRegisteredDpUserId(nickname, uid);
        }
        r.getPlayers().add(p);
        presenceMarkInGameHuman(r, nickname, userId, "join_room_mutate");
        return "ok";
    }

    /**
     * 不得在持有 {@code synchronized(room)} 时调用。 {@code outcome} 为
     * {@link #joinRoomMutateAssumeLocked} 返回值。
     */
    private void refreshQmIndexAfterJoinOutcomeOutsideRoomLock(String roomId, String outcome) {
        if (!"ok".equals(outcome) && !"游戏已开始".equals(outcome)) {
            return;
        }
        long now = System.currentTimeMillis();
        refreshJoinableQuickMatchIndexRoom(roomId, now);
        if ("ok".equals(outcome)) {
            syncLobbyForRoomId(roomId);
        }
    }

    public String joinRoom(String roomId, String nickname, Integer userId, String roomPassword) {
        cancelDefaultQuickMatchWait(nickname);
        DpRoomBO r = roomMap.get(roomId);
        if (r == null)
            return "房间不存在";
        String outcome;
        synchronized (r) {
            if (roomMap.get(roomId) != r) {
                return "房间不存在";
            }
            outcome = joinRoomMutateAssumeLocked(roomId, nickname, userId, roomPassword, r);
        }
        //观众进去又不占位置，房间大厅又不显示观众人数，刷新个蛋
        // refreshQmIndexAfterJoinOutcomeOutsideRoomLock(roomId, outcome);
        return outcome;
    }

    public boolean dpRoomExistsInMemory(String roomId) {
        return roomId != null && roomMap.containsKey(roomId);
    }

    /** 指定昵称是否为该房房主。 */
    public boolean isRoomOwnerNickname(String roomId, String nickname) {
        if (roomId == null || nickname == null) {
            return false;
        }
        DpRoomBO r = roomMap.get(roomId);
        return r != null && nickname.equals(r.getOwner());
    }

    /**
     * 是否可由当前用户向好友发起进房邀请：须在目标房内，且为观众席真人，或桌上真人且本手未离座；机器人不可发起。
     */
    public boolean canActiveMemberInviteFriends(String roomId, String nickname) {
        if (roomId == null || nickname == null || nickname.isBlank()) {
            return false;
        }
        if (DpNpcEngine.isBotNickname(nickname)) {
            return false;
        }
        DpRoomBO r = roomMap.get(roomId);
        if (r == null) {
            return false;
        }
        synchronized (r) {
            List<String> spectators = r.getSpectators();
            if (spectators != null && spectators.contains(nickname)) {
                return true;
            }
            if (r.getPlayers() != null) {
                for (DpPlayer p : r.getPlayers()) {
                    if (p != null && nickname.equals(p.getNickname())) {
                        return !p.isLeftThisHand() && !DpNpcEngine.isBotPlayer(p);
                    }
                }
            }
        }
        return false;
    }

    /**
     * 邮箱进房邀请通过后：跳过密码；始终以观众身份存在于房间（未开局只进观众席；已开局进观众席）。
     * 坐位是否已满不拦截观众（与本轮「超员不拦截」约定一致）。
     */
    public String joinRoomInviteAsSpectator(String roomId, String nickname, Integer userId) {
        cancelDefaultQuickMatchWait(nickname);
        DpRoomBO r = roomMap.get(roomId);
        if (r == null) {
            return "房间不存在";
        }
        final String outcome;
        synchronized (r) {
            if (roomMap.get(roomId) != r) {
                return "房间不存在";
            }
            Integer uid = resolveAndValidateUserId(userId, nickname);
            List<String> waiters = r.getWaitNextHand();
            if (waiters != null) {
                waiters.remove(nickname);
            }
            if (r.isPlaying()) {
                List<String> spectators = r.getSpectators();
                if (spectators == null) {
                    spectators = new ArrayList<>();
                    r.setSpectators(spectators);
                }
                if (!spectators.contains(nickname)) {
                    spectators.add(nickname);
                }
                if (!DpNpcEngine.isBotNickname(nickname)) {
                    r.touchSpectatorPresence(nickname, System.currentTimeMillis());
                }
                if (uid != null) {
                    r.putRegisteredDpUserId(nickname, uid);
                }
                outcome = "游戏已开始";
            } else {
                boolean alreadySeatedOk = false;
                if (r.getPlayers() != null) {
                    for (DpPlayer p : r.getPlayers()) {
                        if (p != null && nickname.equals(p.getNickname()) && !p.isLeftThisHand()) {
                            if (uid != null) {
                                r.putRegisteredDpUserId(nickname, uid);
                            }
                            alreadySeatedOk = true;
                            break;
                        }
                    }
                }
                if (alreadySeatedOk) {
                    outcome = "ok";
                } else {
                    List<String> spectators = r.getSpectators();
                    if (spectators == null) {
                        spectators = new ArrayList<>();
                        r.setSpectators(spectators);
                    }
                    if (!spectators.contains(nickname)) {
                        spectators.add(nickname);
                    }
                    if (!DpNpcEngine.isBotNickname(nickname)) {
                        r.touchSpectatorPresence(nickname, System.currentTimeMillis());
                    }
                    if (uid != null) {
                        r.putRegisteredDpUserId(nickname, uid);
                    }
                    outcome = "ok";
                }
            }
        }
        if ("ok".equals(outcome) || "游戏已开始".equals(outcome)) {
            presenceMarkInGameHuman(r, nickname, userId, "join_room_invite_spectator");
        }
        refreshJoinableQmIndexThenSyncLobby(roomId);
        return outcome;
    }

    /**
     * 准备下一局前会检查人是否真实在房间
     * 候补下一局：调用方<b>不得</b>在未持 {@code synchronized(r)} 下调用；
     * 若由 {@link #readyNextHand(String, String, Integer)} 发起，该方法负责在释放监视器后再
     * {@link #refreshJoinableQmIndexThenSyncLobby(String)}。
     */
    private boolean applyReadyNextHandWhileLocked(DpRoomBO r, String nickname, Integer userId) {
        // 真人必须先仍在「桌上或观众席」；否则未进房者仅凭 HTTP 也能占候补名额，出现「大厅里已被踢出、房内仍显示已报下一局」。
        // 规则 NPC 由服务端生成昵称，进桌链路不保证已写入 spectators/players，故除外。
        if (!DpNpcEngine.isBotNickname(nickname) && !nicknamePresentInRoom(r, nickname)) {
            return false;
        }
        Integer uid = resolveAndValidateUserId(userId, nickname);
        if (uid != null) {
            r.putRegisteredDpUserId(nickname, uid);
        }

        List<String> waiters = r.getWaitNextHand();
        if (waiters == null) {
            waiters = new ArrayList<>();
            r.setWaitNextHand(waiters);
        }
        if (seatedCountForSeatCap(r) + waiters.size() >= r.getMaxSeatCount()) {
            return false;
        }
        if (!waiters.contains(nickname)) {
            waiters.add(nickname);
        }
        if (r.isPlaying() && "settled".equals(r.getCurrentStage())) {
            checkAndStartNextHandAfterSettleReturning(r);
        }
        return true;
    }

    public boolean readyNextHand(String roomId, String nickname, Integer userId) {
        DpRoomBO r = roomMap.get(roomId);
        if (r == null)
            return false;
        boolean ok;
        synchronized (r) {
            ok = applyReadyNextHandWhileLocked(r, nickname, userId);
        }
        if (ok) {
            //仅更新房间索引，不更新大厅索引，因为大厅索引显示的是正在游戏的人数，不是算上等待者一起的人数
            refreshJoinableQuickMatchIndexRoom(roomId, System.currentTimeMillis());
        }
        return ok;
    }

    /**
     * 取消“下一局加入”：从 {@link DpRoomBO#getWaitNextHand()} 移除昵称。
     */
    public boolean cancelReadyNextHand(String roomId, String nickname, Integer userId) {
        DpRoomBO r = roomMap.get(roomId);
        if (r == null)
            return false;
        boolean changed;
        synchronized (r) {
            Integer uid = resolveAndValidateUserId(userId, nickname);
            if (uid != null) {
                r.putRegisteredDpUserId(nickname, uid);
            }
            List<String> waiters = r.getWaitNextHand();
            if (waiters == null || waiters.isEmpty()) {
                changed = false;
            } else {
                changed = waiters.remove(nickname);
            }
        }
        if (changed) {
            refreshJoinableQuickMatchIndexRoom(roomId, System.currentTimeMillis());
        }
        return true;
    }

    /**
     * 大厅快速匹配：公开房、按「越接近满员越优先」选房；对单房间加锁后完成进房 + 准备，避免两人抢最后一席的竞态。
     * <p>
     * 空位口径与 {@link DpQuickMatchRoomSemantics}
     * 一致：{@code 人数上限 − 桌上非离线座次 − waitNextHand.size() > 0}。
     * 「非离线」：本手未离座且（机器人 或 心跳未超时）。若与 {@link #readyNextHand} 的硬上限校验仍不一致，本方法在加锁后会以
     * {@code readyNextHand} 为准，失败则回滚（新加入但尚未在房内的用户会 {@link #exitRoom}）。
     */
    public ResultUtil quickMatchJoinAndReady(String nickname, Integer userId) {
        if (nickname == null || nickname.isBlank()) {
            return ResultUtil.error().data("message", "昵称无效");
        }
        dpQuickMatchAssignmentLock.lock();
        try {
            DpRoomBO existing = findRoomContainingNickname(nickname);
            if (existing != null) {
                String err;
                synchronized (existing) {
                    err = finishQuickMatchForExistingPresence(existing, nickname, userId);
                }
                if (err != null) {
                    return ResultUtil.error().data("message", err);
                }
                presenceMarkInGameHuman(existing, nickname, userId, "quick_match_already_in_room");
                refreshJoinableQmIndexThenSyncLobby(existing.getRoomId());
                return ResultUtil.ok()
                        .data("roomId", existing.getRoomId())
                        .data("message", "你已在该房间，已更新准备状态");
            }

            long sortTime = System.currentTimeMillis();
            List<DpRoomBO> candidates = orderedQuickMatchJoinCandidates(sortTime);

            candLoop: for (DpRoomBO r : candidates) {
                boolean progressedInRoom = false;
                String join = "";
                boolean wasPresent = false;
                boolean readyOk = true;
                synchronized (r) {
                    if (roomMap.get(r.getRoomId()) != r) {
                        continue candLoop;
                    }
                    long now = System.currentTimeMillis();
                    if (r.isPasswordProtected()
                            || DpQuickMatchRoomSemantics.rawVacancyForQuickMatch(r, now) <= 0) {
                        continue candLoop;
                    }
                    wasPresent = nicknamePresentInRoom(r, nickname);
                    join = joinRoomMutateAssumeLocked(r.getRoomId(), nickname, userId, null, r);
                    if (!"ok".equals(join) && !"游戏已开始".equals(join)) {
                        continue candLoop;
                    }
                    if (r.isPlaying()) {
                        if (!applyReadyNextHandWhileLocked(r, nickname, userId)) {
                            readyOk = false;
                        }
                    } else {
                        if (!ensureLobbyReady(r, nickname)) {
                            readyOk = false;
                        }
                    }
                    progressedInRoom = true;
                }
                if (!progressedInRoom) {
                    continue candLoop;
                }
                refreshQmIndexAfterJoinOutcomeOutsideRoomLock(r.getRoomId(), join);
                if (!readyOk) {
                    if (!wasPresent) {
                        exitRoom(r.getRoomId(), nickname);
                    }
                    continue candLoop;
                }
                refreshJoinableQmIndexThenSyncLobby(r.getRoomId());
                return ResultUtil.ok().data("roomId", r.getRoomId()).data("message", "ok");
            }
            return ResultUtil.error().data("message", MSG_NO_PUBLIC_ROOM);
        } finally {
            dpQuickMatchAssignmentLock.unlock();
        }
    }

    private static String quickMatchResultDetailMessage(ResultUtil r) {
        if (r == null) {
            return "";
        }
        Map<String, Object> d = r.getData();
        if (d != null && d.get("message") != null) {
            return String.valueOf(d.get("message"));
        }
        String m = r.getMessage();
        return m != null ? m : "";
    }

    /**
     * 批量通知超时者，用ws推送
     */
    private void notifyQuickMatchTimedOut(List<String> nicknames) {
        if (nicknames == null || nicknames.isEmpty()) {
            return;
        }
        for (String n : nicknames) {
            quickMatchPush.notifyIdle(n, "匹配等待超时");
        }
    }

    /**
     * 快匹 WebSocket 建连后补发当前状态（避免先 POST 再连接触发推送时客户端尚未注册会话）。
     */
    public void pushQuickMatchLobbySnapshot(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return;
        }
        List<String> qmTimedOut;
        int queuePos = 0;
        synchronized (defaultQmLock) {
            qmTimedOut = pruneDefaultQuickMatchQueueLockedWithoutReentry();
            int i = 1;
            for (DpQuickMatchWaitEntry e : defaultQmWaiters) {
                if (nickname.equals(e.nickname())) {
                    queuePos = i;
                    break;
                }
                i++;
            }
        }
        notifyQuickMatchTimedOut(qmTimedOut);
        if (queuePos > 0) {
            quickMatchPush.notifyWaiting(nickname, queuePos);
            return;
        }
        DpRoomBO inRoom = findRoomContainingNickname(nickname);
        if (inRoom != null) {
            quickMatchPush.notifyMatched(nickname, inRoom.getRoomId());
        }
    }

    /**
     * 大厅快匹：先尝试进入已有公开房；若无房可进则加入等待队列，尝试与队列里的玩家配对建房。
     */
    public ResultUtil quickMatchJoinQueueOrImmediate(String nickname, Integer userId) {
        // 直接匹配可用房逻辑，现在发现这个方法效率太低，直接不走了，直接入队，然后触发尝试匹配即可
        // ResultUtil immediate = quickMatchJoinAndReady(nickname, userId);
        // if (Boolean.TRUE.equals(immediate.getSuccess())) {
        //     return immediate;
        // }
        // if (!MSG_NO_PUBLIC_ROOM.equals(quickMatchResultDetailMessage(immediate))) {
        //     return immediate;
        // }
        // if (findRoomContainingNickname(nickname) != null) {
        //     return quickMatchJoinAndReady(nickname, userId);
        // }
        long now = System.currentTimeMillis();
        // 加入等待队列逻辑
        List<String> qmTimedOut;
        synchronized (defaultQmLock) {
            qmTimedOut = pruneDefaultQuickMatchQueueLockedWithoutReentry();
            boolean already = false;
            for (DpQuickMatchWaitEntry e : defaultQmWaiters) {
                if (nickname.equals(e.nickname())) {
                    already = true;
                    break;
                }
            }
            if (!already) {
                defaultQmWaiters.addLast(new DpQuickMatchWaitEntry(nickname, userId, now));
            }
        }

        notifyQuickMatchTimedOut(qmTimedOut);
        //尝试配对
        attemptQuickMatchPairing();
        //没有配对成功，告知玩家匹配所处位置
        int queuePos = 0;
        synchronized (defaultQmLock) {
            int i = 1;
            for (DpQuickMatchWaitEntry e : defaultQmWaiters) {
                if (nickname.equals(e.nickname())) {
                    queuePos = i;
                    break;
                }
                i++;
            }
        }
        if (queuePos > 0) {
            quickMatchPush.notifyWaiting(nickname, queuePos);
        }
        return ResultUtil.ok()
                .data("queued", true)
                .data("state", "WAITING")
                .data("message", "已加入匹配队列，凑齐另一名玩家后将自动建房");
    }

    /** 离开默认快匹等待队列（例如关闭大厅）。 */
    public boolean cancelDefaultQuickMatchWait(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return false;
        }
        boolean removed;
        synchronized (defaultQmLock) {
            removed = defaultQmWaiters.removeIf(e -> nickname.equals(e.nickname()));
        }
        if (removed) {
            quickMatchPush.notifyIdle(nickname, "已取消匹配");
        }
        return removed;
    }

    @Scheduled(fixedDelayString = "${mgdemoplus.dp-quick-match-prune-ms:30000}")
    public void scheduledPruneDefaultQuickMatchQueue() {
        List<String> timedOut;
        synchronized (defaultQmLock) {
            timedOut = pruneDefaultQuickMatchQueueLockedWithoutReentry();
        }
        notifyQuickMatchTimedOut(timedOut);
    }

    /**
     * 在已持有或可安全使用 {@link #defaultQmLock} 的上下文中调用：将它们出队并获取超时或已在房内者名单；
     *
     * @return 仅因<strong>等待超时</strong>被移出队列的昵称（用于 {@link #notifyQuickMatchTimedOut}）
     */
    private List<String> pruneDefaultQuickMatchQueueLockedWithoutReentry() {
        long now = System.currentTimeMillis();
        List<String> timedOut = new ArrayList<>();
        defaultQmWaiters.removeIf(e -> {
            boolean tooLong = now - e.enqueuedMs() > DEFAULT_QM_WAIT_MS;
            boolean inRoom = findRoomContainingNickname(e.nickname()) != null;
            if (tooLong || inRoom) {
                if (tooLong) {
                    timedOut.add(e.nickname());
                }
                return true;
            }
            return false;
        });
        return timedOut;
    }

    private static String quickMatchResultRoomId(ResultUtil res) {
        if (res == null) {
            return null;
        }
        Map<String, Object> d = res.getData();
        if (d == null || d.get("roomId") == null) {
            return null;
        }
        return String.valueOf(d.get("roomId"));
    }

    /** 席上且本手未标记离座视为「在场上」。 */
    private static boolean isSeatedAlive(DpRoomBO r, String nickname) {
        if (r == null || r.getPlayers() == null || nickname == null) {
            return false;
        }
        for (DpPlayer p : r.getPlayers()) {
            if (p != null && nickname.equals(p.getNickname()) && !p.isLeftThisHand()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 好友「跟随进房」等：按好友昵称查其当前所在内存房间（在座或观众席）。
     *
     * @return 房间号；未在任一房内则 null
     */
    public String findRoomIdContainingNickname(String nickname) {
        DpRoomBO r = findRoomContainingNickname(nickname);
        return r != null ? r.getRoomId() : null;
    }
/**
 * 发现房间包含自己名字就返回房间
 * @param nickname
 * @return
 */
    private DpRoomBO findRoomContainingNickname(String nickname) {
        if (nickname == null) {
            return null;
        }
        for (DpRoomBO r : roomMap.values()) {
            List<String> specs = r.getSpectators();
            if (specs != null && specs.contains(nickname)) {
                return r;
            }
            if (r.getPlayers() != null) {
                for (DpPlayer p : r.getPlayers()) {
                    if (p != null && nickname.equals(p.getNickname())) {
                        return r;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 昵称在座或未离座观众列表中时返回房间号；否则 null。单机内存口径，供好友跟随解析目标房间。
     */
    public String findActiveRoomIdForNickname(String nickname) {
        DpRoomBO r = findRoomContainingNickname(nickname);
        return r != null ? r.getRoomId() : null;
    }
/**
 * 看名字在不在房间里，不检查等待区
 */
    private static boolean nicknamePresentInRoom(DpRoomBO r, String nickname) {
        if (r == null || nickname == null) {
            return false;
        }
        List<String> specs = r.getSpectators();
        if (specs != null && specs.contains(nickname)) {
            return true;
        }
        if (r.getPlayers() != null) {
            for (DpPlayer p : r.getPlayers()) {
                if (p != null && nickname.equals(p.getNickname())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 桌上非僵尸、非机器人真人数量（与心跳无关；与定时空房清理 / {@link #removeRoom} 口径一致）。 */
    private static int liveHumanTableCount(DpRoomBO room) {
        if (room == null || room.getPlayers() == null) {
            return 0;
        }
        int n = 0;
        for (DpPlayer p : room.getPlayers()) {
            if (p == null || p.isLeftThisHand() || DpNpcEngine.isBotPlayer(p)) {
                continue;
            }
            n++;
        }
        return n;
    }

    /** settled 阶段：桌上可参与下一手的真人（非僵尸、非机器人、筹码 ≥ 大盲）。 */
    private static int settleCapableLiveHumansCount(DpRoomBO r) {
        if (r == null || r.getPlayers() == null) {
            return 0;
        }
        int n = 0;
        for (DpPlayer p : r.getPlayers()) {
            if (p == null || p.isLeftThisHand() || DpNpcEngine.isBotPlayer(p)) {
                continue;
            }
            if (p.getChips() >= r.getBigBlindChips()) {
                n++;
            }
        }
        return n;
    }

    private static int settleReadyLiveHumansCount(DpRoomBO r) {
        if (r == null || r.getPlayers() == null) {
            return 0;
        }
        int n = 0;
        for (DpPlayer p : r.getPlayers()) {
            if (p == null || p.isLeftThisHand() || DpNpcEngine.isBotPlayer(p)) {
                continue;
            }
            if (p.getChips() < r.getBigBlindChips()) {
                continue;
            }
            if (p.isReady()) {
                n++;
            }
        }
        return n;
    }

    /** 未离座且筹码够的 Bot 是否全部已准备（用于「桌上无真人」时仅 NPC 桌的自动开牌）。 */
    private static boolean allSeatedCapableBotsReady(DpRoomBO r) {
        if (r == null || r.getPlayers() == null) {
            return false;
        }
        int need = 0;
        int ok = 0;
        for (DpPlayer p : r.getPlayers()) {
            if (p == null || p.isLeftThisHand() || !DpNpcEngine.isBotPlayer(p)) {
                continue;
            }
            if (p.getChips() < r.getBigBlindChips()) {
                continue;
            }
            need++;
            if (p.isReady()) {
                ok++;
            }
        }
        return need > 0 && need == ok;
    }

    /** 未开局：将玩家标记为已准备（开局前准备），找不到座位则 false。 */
    private static boolean ensureLobbyReady(DpRoomBO r, String nickname) {
        if (r == null || r.isPlaying() || nickname == null) {
            return false;
        }
        for (DpPlayer p : r.getPlayers()) {
            if (p != null && nickname.equals(p.getNickname())) {
                if (!p.isReady()) {
                    p.setReady(true);
                }
                return true;
            }
        }
        return false;
    }

    private String finishQuickMatchForExistingPresence(DpRoomBO r, String nickname, Integer userId) {
        if (r.isPlaying()) {
            if (isSeatedAlive(r, nickname)) {
                return null;
            }
            return applyReadyNextHandWhileLocked(r, nickname, userId) ? null : "候补已满，请稍后再试";
        }
        return ensureLobbyReady(r, nickname) ? null : "准备失败";
    }

    public boolean toggleReady(String roomId, String nickname) {
        DpRoomBO r = roomMap.get(roomId);
        if (r == null)
            return false;
        AtomicBoolean startedNewHand = new AtomicBoolean(false);
        boolean ok;
        synchronized (r) {
            ok = toggleReadyAssumeLocked(r, nickname, startedNewHand);
        }
        if (ok && startedNewHand.get()) {
            refreshJoinableQmIndexThenSyncLobby(roomId);
        }
        return ok;
    }

    /** 前提：已持有 {@code synchronized(r)} */
    private boolean toggleReadyAssumeLocked(DpRoomBO r, String nickname, AtomicBoolean outStartedNewHand) {
        // 首次开局前的准备：房间未开始
        if (!r.isPlaying()) {
            for (DpPlayer p : r.getPlayers()) {
                if (p.getNickname().equals(nickname)) {
                    p.setReady(!p.isReady());
                    return true;
                }
            }
            return false;
        }
        // 结算后的下一局准备
        if (r.isPlaying() && "settled".equals(r.getCurrentStage())) {
            for (DpPlayer p : r.getPlayers()) {
                if (p.getNickname().equals(nickname)) {
                    if (p.getChips() < r.getBigBlindChips()) {
                        return false;
                    }
                    p.setReady(!p.isReady());
                    outStartedNewHand.set(checkAndStartNextHandAfterSettleReturning(r));
                    return true;
                }
            }
            return false;
        }
        // 其它阶段不允许切换准备
        for (DpPlayer p : r.getPlayers()) {
            if (p.getNickname().equals(nickname)) {
                return false;
            }
        }
        return false;
    }

    /**
     * settled：看「该准备的真人是否齐了 / 是否仅靠候补或机器上桌」——能开就 {@link #newHand}。
     * <p>
     * 为何 {@link #autoSettle} 进入 settled 后也要调用：候补可能在<strong>上一局</strong>就已进
     * {@code waitNextHand}，
     * 结算这一刻<strong>不会</strong>再走 HTTP {@link #readyNextHand}；若只在「准备下一把」接口里检测，
     * 就容易出现只能靠<strong>取消再加入</strong>（再调一次接口）才能开局的情况。
     * 桌上有人且靠「点准备」齐人的场景仍由 {@link #toggleReady} 等路径覆盖。
     * <p>
     * 触发：{@link #autoSettle} 写入 settled、候补 {@link #readyNextHand}、真人
     * {@link #toggleReady}、
     * 构造器里机器人自动准备、准备超时 {@link #handleReadyTimeout}。
     */
    /**
     * settled：齐人则可 {@link #newHandWithoutLobbyUpsert}。
     *
     * @return 是否真的开始了新一手（调用方随后在<b>释放</b>单房 intrinsic 监视器后执行
     *         {@link #refreshJoinableQmIndexThenSyncLobby(String)}）。
     */
    private boolean checkAndStartNextHandAfterSettleReturning(DpRoomBO r) {
        if (r == null || !r.isPlaying()) {
            return false;
        }
        if (!"settled".equals(r.getCurrentStage())) {
            return false;
        }

        int humanCap = settleCapableLiveHumansCount(r);
        int humanReady = settleReadyLiveHumansCount(r);
        List<String> waiters = r.getWaitNextHand();
        int w = waiters == null ? 0 : waiters.size();

        if (humanCap > 0) {
            if (humanReady == humanCap) {
                return newHandWithoutLobbyUpsert(r.getRoomId());
            }
            return false;
        }
        if (w >= 1) {
            return newHandWithoutLobbyUpsert(r.getRoomId());
        }
        if (allSeatedCapableBotsReady(r)) {
            return newHandWithoutLobbyUpsert(r.getRoomId());
        }
        return false;
    }

    /**
     * 准备超时前先踢人：筹码 &lt; 大盲的上桌→观众席；未点准备的上桌→观众席；并清准备倒计时。
     *
     * @return 筹码筛完后上桌已空则为 false（并已收局）；否则为 true，可继续
     *         {@link #checkAndStartNextHandAfterSettleReturning}。
     */
    private boolean pruneSettledTableForReadyTimeout(DpRoomBO r) {
        List<DpPlayer> players = r.getPlayers();
        if (players == null || players.isEmpty()) {
            r.setPlaying(false);
            r.setReadyDeadline(0L);
            return false;
        }

        List<String> spectators = r.getSpectators();
        if (spectators == null) {
            spectators = new ArrayList<>();
            r.setSpectators(spectators);
        }
        List<DpPlayer> afterChips = new ArrayList<>();
        for (DpPlayer p : players) {
            if (p.getChips() < r.getBigBlindChips()) {
                if (!spectators.contains(p.getNickname())) {
                    spectators.add(p.getNickname());
                    if (!DpNpcEngine.isBotPlayer(p)) {
                        r.touchSpectatorPresence(p.getNickname(), System.currentTimeMillis());
                    }
                }
            } else {
                afterChips.add(p);
            }
        }
        r.setPlayers(afterChips);
        players = afterChips;
        if (players.isEmpty()) {
            r.setPlaying(false);
            r.setReadyDeadline(0L);
            return false;
        }

        List<DpPlayer> remain = new ArrayList<>();
        List<String> kicked = new ArrayList<>();
        for (DpPlayer p : players) {
            if (p.isReady()) {
                remain.add(p);
            } else {
                kicked.add(p.getNickname());
            }
        }

        spectators = r.getSpectators();
        if (spectators == null) {
            spectators = new ArrayList<>();
            r.setSpectators(spectators);
        }
        for (String name : kicked) {
            if (!spectators.contains(name)) {
                spectators.add(name);
                if (!DpNpcEngine.isBotNickname(name)) {
                    r.touchSpectatorPresence(name, System.currentTimeMillis());
                }
            }
        }

        r.setPlayers(remain);
        r.setReadyDeadline(0L);
        return true;
    }

    /**
     * 上一手房主已不在本手上桌名单时：若房主仍在观众席（本局未上桌但仍在房内观战），保留 {@code owner}；
     * 否则把房主移交给桌上第一位真人，再没有则交给第一个座位。
     * <p>
     * 注意：玩家彻底退房时由 {@link #exitRoom}/{@link #giveOwner} 先从观众席移除再移交，不会与本条冲突。
     */
    private void ensureOwnerAlignedWithSeatedPlayers(DpRoomBO r) {
        if (r == null) {
            return;
        }
        List<DpPlayer> ps = r.getPlayers();
        if (ps == null || ps.isEmpty()) {
            return;
        }
        String ow = r.getOwner();
        if (ow != null) {
            for (DpPlayer p : ps) {
                if (ow.equals(p.getNickname())) {
                    return;
                }
            }
            // 主动离座等：真人房主在 spectators 中但不在本局上桌列表，不视为丧失房主资格
            List<String> spectators = r.getSpectators();
            if (spectators != null && !ow.isEmpty() && spectators.contains(ow)
                    && !DpNpcEngine.isBotNickname(ow)) {
                return;
            }
        }
        for (DpPlayer p : ps) {
            if (!DpNpcEngine.isBotPlayer(p)) {
                r.setOwner(p.getNickname());
                return;
            }
        }
        r.setOwner(ps.get(0).getNickname());
    }

    public boolean exitRoom(String roomId, String nickname) {
        DpRoomBO r = roomMap.get(roomId);
        if (r == null)
            return false;
        boolean wasPublic = !r.isPasswordProtected();
        ExitRoomSynchronizedOutcome snap = exitRoomApplyMutationWhileHoldingRoomLock(roomId, nickname, r);
        System.out.println("snap.droppedEmpty: " + snap.droppedEmpty);
        if (snap.droppedEmpty) {
            finalizeHallAfterRoomRemovedWithPresenceSnapshot(roomId, r);
        } else {
            //如果不影响房间解散，意味有可能腾出空位，刷新快匹房间索引，再异步更新房间数据库
            refreshJoinableQuickMatchIndexRoom(roomId, System.currentTimeMillis());
            if (snap.lobbyTouch || snap.giveAgg.ownerFieldChanged) {
                syncLobbyForRoomId(roomId);
            }
        }
        if (!snap.droppedEmpty) {
            //给人设置成空闲状态
            presenceTryMarkIdleFullyLeft(nickname, snap.presenceHintUid, r, "exit_room");
        }
        if (wasPublic && snap.result) {
            attemptQuickMatchPairing();
        }
        return snap.result;
    }

    /**
     * 变异观众席、在位玩家或离线占位：须在 {@code synchronized(r)} 内完成，与原 {@link #exitRoom} 内联块一致。
     */
    private ExitRoomSynchronizedOutcome exitRoomApplyMutationWhileHoldingRoomLock(
            String roomId, String nickname, DpRoomBO r) {
        GiveOwnerMutationOutcome giveAgg = GiveOwnerMutationOutcome.none();
        boolean lobbyTouch = false;
        boolean droppedEmpty = false;
        boolean result;
        Integer presenceHintUid;
        synchronized (r) {
            presenceHintUid = resolvePresenceHintBeforeExitMutationLocked(r, nickname);
            List<String> spectators = r.getSpectators();
            List<String> waiters = r.getWaitNextHand();
            if (spectators != null) {
                System.out.println("spectators: " + spectators+"已经被移除");
                spectators.remove(nickname);
            }
            r.removeSpectatorPresence(nickname);
            if (waiters != null) {
                waiters.remove(nickname);
            }

            if (!r.isPlaying()) {
                if (Objects.equals(r.getOwner(), nickname)) {
                    giveAgg = giveAgg.mergedWith(applyGiveOwnerWhileRoomLocked(r, nickname));
                }
                if (roomMap.get(roomId) != r) {
                    droppedEmpty = true;
                    result = true;
                } else {
                    boolean removed = r.getPlayers().removeIf(p -> p.getNickname().equals(nickname));
                    if (tryUnregisterEmptyRoomAssumeLocked(r, roomId)) {
                        droppedEmpty = true;
                        result = true;
                    } else {
                        lobbyTouch = true;
                        result = removed;
                    }
                }
            } else {
                // 进行中：离开者可能只在观众席，也可能仍在 players 里（含 leftThisHand 占位「僵尸位」仅按昵称匹配）。
                // 摘房口径与定时器 {@link #removeDesertedRoomInGlobalTickIfNoLiveHumans} 一致，须在处置完本方法内的变更后统一尝试，
                // 不能只在 target==null 时摘房（否则桌上只剩僵尸位时 liveHumanTableCount 已为 0，但 exit 仍进 target 分支则不摘）。
                if (Objects.equals(r.getOwner(), nickname)) {
                    giveAgg = giveAgg.mergedWith(applyGiveOwnerWhileRoomLocked(r, nickname));
                }
                DpPlayer target = null;
                int idx = -1;
                List<DpPlayer> ps = r.getPlayers();
                for (int i = 0; i < ps.size(); i++) {
                    DpPlayer p = ps.get(i);
                    if (p.getNickname().equals(nickname)) {
                        target = p;
                        idx = i;
                        break;
                    }
                }
                if (target == null) {
                    result = true;
                } else {
                    target.setLeftThisHand(true);
                    target.setHoleCards(new ArrayList<>());
                    if (r.getCurrentActorIndex() == idx) {
                        fold(roomId, nickname);
                    } else {
                        if (!target.isFold()) {
                            target.setFold(true);
                        }
                    }
                    result = true;
                }
                if (roomMap.get(roomId) != r) {
                    droppedEmpty = true;
                } else if (tryUnregisterEmptyRoomAssumeLocked(r, roomId)) {
                    droppedEmpty = true;
                } else {
                    lobbyTouch = true;
                }
            }
        }
        return new ExitRoomSynchronizedOutcome(result, droppedEmpty, lobbyTouch, giveAgg, presenceHintUid);
    }

    // ========== 游戏开始与流程 ==========

    public boolean startGame(String roomId, String ownerNickname) {
        DpRoomBO r = roomMap.get(roomId);
        if (r == null || !r.getOwner().equals(ownerNickname))
            return false;
        boolean nhOk;
        synchronized (r) {
            if (!r.getOwner().equals(ownerNickname)) {
                return false;
            }
            r.setPlaying(true);
            r.setCurrentStage("preflop");
            r.setCommunityCards(new ArrayList<>());
            r.setPot(0);
            r.setPots(new ArrayList<>());
            r.setCurrentBetToCall(0);
            r.setReadyDeadline(0L);
            // for (DpPlayer p : r.getPlayers()) {
            // p.setChips(r.getStartingChips());
            // p.setFold(false);
            // p.setBet(0);
            // p.setTotalBet(0);
            // p.setAllIn(false);
            // p.setActed(false);
            // p.setHoleCards(new ArrayList<>());
            // p.setDealer(false);
            // p.setBlind(0);
            // }
            r.getPlayers().get(0).setDealer(true);
            nhOk = newHandWithoutLobbyUpsert(roomId);
        }
        refreshJoinableQmIndexThenSyncLobby(roomId);
        return nhOk;
    }

    /**
     * 开始新一手：不 upsert 大厅；调用方在合适的锁序下再
     * {@link #refreshJoinableQmIndexThenSyncLobby(String)}。
     */
    private boolean newHandWithoutLobbyUpsert(String roomId) {
        DpRoomBO r = roomMap.get(roomId);
        if (r == null || !r.isPlaying())
            return false;
        r.setLastHandHoleCardsPublic(false);

        // 把上一局观战并标记“下一局加入”的玩家，加入到本局的玩家列表中，并更新观众列表
        // 检测是否有积分不足的玩家，清理到观众厅，都在getAllCanPlayer方法里
        r.setPlayers(getAllCanPlayer(r));
        ensureOwnerAlignedWithSeatedPlayers(r);

        List<DpPlayer> ps = r.getPlayers();
        if (ps == null || ps.isEmpty()) {
            r.setPlaying(false);
            r.setCurrentStage("preflop");
            r.setCommunityCards(new ArrayList<>());
            r.setDeck(new ArrayList<>());
            r.setPot(0);
            r.setCurrentBetToCall(0);
            r.setPots(new ArrayList<>());
            r.setCurrentActorIndex(-1);
            r.setReadyDeadline(0L);
            return false;
        }

        // 为本手牌生成一个稳定的随机种子
        long seedBase = System.currentTimeMillis();
        if (roomId != null) {
            seedBase ^= roomId.hashCode();
        }
        r.setCurrentHandSeed(seedBase);

        // 逐街动作日志（全员）：与新一手种子绑定，结算后清空
        DpNpcStreetActionLog.beginHand(r);
        // 开始一手牌谱的记录
        observedHandService.beginHand(r);

        r.setDeck(newDeck());
        r.setCommunityCards(new ArrayList<>());
        r.setCurrentStage("preflop");
        r.setPot(0);
        r.setPots(new ArrayList<>());
        r.setCurrentBetToCall(0);
        r.setRaiseLevel(0);
        r.setLastRaiseIncrement(r.getBigBlindChips());
        r.setReadyDeadline(0L);
        int did = 0;// 庄家索引
        for (DpPlayer p : ps) {// 这里需要及时重置状态
            p.setFold(false);
            p.setBet(0);
            p.setBlind(0);
            p.setActed(false);
            p.setTotalBet(0);
            p.setAllIn(false);
            p.setLeftThisHand(false);
            // 每一手开局时清理 NPC 的整手牌 HandPlan 状态，避免跨手残留
            p.setNpcHandPlanType(null);
            p.setNpcHandPlanMaxBarrels(0);
            p.setNpcHandPlanAggression(0.0);
            p.setNpcHandPlanTargetVillain(null);
            p.setHoleCards(Arrays.asList(r.getDeck().remove(0), r.getDeck().remove(0)));
        }
        // 留着上一把遗留下来的按钮以便确认下一把的按钮
        // 找当前玩家里是庄家的，如果没有默认从0号位开始
        // 庄家轮动
        if (r.getLastDealerIndex() != 0) {// 如果上局不是0，那就把did更新，如果是0，那按上把是0算
            did = r.getLastDealerIndex();
        }
        // System.out.println("上局庄位索引" + did);
        // System.out.println("玩家是:" + r.getPlayers().get(did).getNickname());
        for (DpPlayer p : ps)
            p.setDealer(false);// 更新一轮
        did = (did + 1) % ps.size();

        ps.get(did).setDealer(true);
        r.setLastDealerIndex(did);// 把这局的庄家索引记录下来
        System.out.println("本局庄位" + did);
        System.out.println("玩家是:" + r.getPlayers().get(did).getNickname());
        // 大小盲，如果人数大于2才有大小盲位
        if (ps.size() >= 2) {
            int sb = (did + 1) % ps.size();
            int bb = (did + 2) % ps.size();

            ps.get(sb).setBlind(1);
            int potBeforeSb = r.getPot();
            ps.get(sb).setChips(ps.get(sb).getChips() - r.getSmallBlindChips());
            ps.get(sb).setBet(r.getSmallBlindChips());
            ps.get(sb).setTotalBet(r.getSmallBlindChips()); // 记入累计下注
            DpNpcStreetActionLog.recordBlind(r, ps.get(sb).getNickname(), true, r.getSmallBlindChips(), potBeforeSb);
            observedHandService.recordBlind(r, ps.get(sb).getNickname(), true, r.getSmallBlindChips(), potBeforeSb);

            ps.get(bb).setBlind(2);
            int potBeforeBb = r.getPot() + r.getSmallBlindChips();
            ps.get(bb).setChips(ps.get(bb).getChips() - r.getBigBlindChips());
            ps.get(bb).setBet(r.getBigBlindChips());
            ps.get(bb).setTotalBet(r.getBigBlindChips()); // 记入累计下注
            DpNpcStreetActionLog.recordBlind(r, ps.get(bb).getNickname(), false, r.getBigBlindChips(), potBeforeBb);
            observedHandService.recordBlind(r, ps.get(bb).getNickname(), false, r.getBigBlindChips(), potBeforeBb);

            r.setCurrentBetToCall(r.getBigBlindChips());
            r.setPot(r.getBigBlindChips() + r.getSmallBlindChips()); // 大小盲计入底池
            r.setLastRaiseIncrement(r.getBigBlindChips());
        } else {
            r.setLastRaiseIncrement(r.getBigBlindChips());
        }

        r.setCurrentActorIndex((did + 3) % ps.size());// 翻前从大盲的下一个开始行动
        r.setLastActionTime(System.currentTimeMillis());// 方便计时间用
        // 固定本手座位/盲注后筹码，并记 preflop 公共牌空快照
        // 标记手准备
        observedHandService.markHandReadyAfterBlinds(r);
        return true;
    }

    /** 未持单房监视器嵌套时安全：内部 {@link #newHandWithoutLobbyUpsert} 后刷新索引并 upsert 大厅。 */
    public boolean newHand(String roomId) {
        boolean b = newHandWithoutLobbyUpsert(roomId);
        refreshJoinableQmIndexThenSyncLobby(roomId);
        return b;
    }

    public List<DpPlayer> getAllCanPlayer(DpRoomBO r) {
        // 防null
        List<String> spectators = getNewSpectators(r);
        r.setSpectators(spectators);
        // 防筹码不足
        List<DpPlayer> canPlay = new ArrayList<>();
        for (DpPlayer p : r.getPlayers()) {
            if (p.isLeftThisHand()) {
                // 与 kick 离座一致：本手已离桌的真人进入观众席，避免 newHand 时仅因未入列表而“消失”
                if (!DpNpcEngine.isBotPlayer(p) && !spectators.contains(p.getNickname())) {
                    spectators.add(p.getNickname());
                    r.touchSpectatorPresence(p.getNickname(), System.currentTimeMillis());
                }
                continue;
            }
            if (p.getChips() < r.getBigBlindChips()) {
                if (!spectators.contains(p.getNickname())) {
                    spectators.add(p.getNickname());
                    if (!DpNpcEngine.isBotPlayer(p)) {
                        r.touchSpectatorPresence(p.getNickname(), System.currentTimeMillis());
                    }
                }
            } else {
                canPlay.add(p);
            }
        }
        // 拉取准备下一把的（受人数上限约束，未排上的仍留在 waitNextHand）
        List<String> waiters = r.getWaitNextHand();
        if (waiters != null && !waiters.isEmpty()) {
            int seatCap = r.getMaxSeatCount();
            Iterator<String> it = waiters.iterator();
            while (it.hasNext()) {
                if (canPlay.size() >= seatCap) {
                    break;
                }
                String name = it.next();

                DpPlayer np = new DpPlayer();
                np.setNickname(name);
                Integer regUid = r.getRegisteredDpUserId(name);
                if (regUid != null) {
                    np.setDpUserId(regUid);
                }
                // 新加入的玩家带着默认筹码参与新一局
                np.setChips(r.getStartingChips());
                np.setReady(true);
                canPlay.add(np);// 准备下一把的新人加入

                // 这些人已经回到牌桌，不再属于观众席
                if (spectators != null) {// 从观众席里拉下来了
                    spectators.remove(name);
                    r.removeSpectatorPresence(name);
                }
                it.remove();
            }
        }
        return canPlay;

    }
    // ========== 游戏进行中：下注 / 弃牌 ==========

    /**
     * 找到翻后每圈的第一个行动者：
     * 从庄家位 D 左边第一个玩家开始，跳过已弃牌 / 已 all-in 的玩家。
     * 若没有庄家标记或没人能行动，则返回 -1。
     */
    private int findFirstActorAfterDealer(DpRoomBO r) {
        List<DpPlayer> ps = r.getPlayers();
        if (ps == null || ps.isEmpty()) {
            return -1;
        }

        // 找到当前庄家下标
        int dealerIdx = -1;
        for (int i = 0; i < ps.size(); i++) {
            if (ps.get(i).isDealer()) {
                dealerIdx = i;
                break;
            }
        }
        if (dealerIdx < 0) {
            // 理论上不该发生：没有 D，就让调用方自己处理
            return -1;
        }

        // 从 D 的下家开始循环一圈，寻找第一个可行动玩家（跳过已离线位）
        for (int step = 1; step <= ps.size(); step++) {
            int idx = (dealerIdx + step) % ps.size();
            DpPlayer p = ps.get(idx);
            if (p.isLeftThisHand())
                continue;
            if (!p.isFold() && !p.isAllIn()) {
                return idx;
            }
        }

        // 全部人都弃牌或 all-in 时，没有人需要行动
        return -1;
    }

    /**
     * NPC 加注额兜底：抬到合法最小再加注（若后手不足则全下）。
     */
    private static int clampRaiseAmountForLegal(DpRoomBO r, DpPlayer p, int amount) {
        int chips = p.getChips();
        if (amount > chips) {
            amount = chips;
        }
        int totalBet = p.getBet() + amount;
        int ctc = r.getCurrentBetToCall();
        if (totalBet <= ctc) {
            return amount;
        }
        int minAdd = r.minChipsToLegalRaise(p);
        if (amount >= minAdd) {
            return amount;
        }
        if (chips <= minAdd) {
            return chips;
        }
        return minAdd;
    }

    public boolean bet(String roomId, String nickname, int amount) {
        DpRoomBO r = roomMap.get(roomId);
        if (r == null || !r.isPlaying())
            return false;

        DpPlayer p = r.getPlayers().stream()
                .filter(pl -> pl.getNickname().equals(nickname))
                .findFirst().orElse(null);
        if (p == null || p.isFold() || p.isAllIn())
            return false;

        // 记录下注前快照（用于逐街动作日志）
        int betToCallBefore = r.getCurrentBetToCall();
        int actorBetBefore = p.getBet();
        int potBefore = r.getPot();
        boolean becameAllIn = false;
        int chipsBefore = p.getChips();

        // 如果下注金额 >= 玩家剩余筹码，视为 all-in
        if (amount >= p.getChips()) {
            amount = p.getChips();
            p.setAllIn(true);
            becameAllIn = true;
        }

        int totalBet = p.getBet() + amount;
        int prevBetToCall = r.getCurrentBetToCall();

        if (totalBet < prevBetToCall) {
            if (amount < chipsBefore) {
                return false;
            }
        } else if (totalBet > prevBetToCall) {
            int inc = Math.max(r.getLastRaiseIncrement(), r.getBigBlindChips());
            int minTotal = prevBetToCall + inc;
            if (totalBet < minTotal && amount < chipsBefore) {
                return false;
            }
        }

        if (totalBet > prevBetToCall) {
            // 出现了更高的一档下注：如果之前没有有效下注，则视为 open，否则视为一次 re-raise
            int currentLevel = r.getRaiseLevel();
            if (prevBetToCall <= 0) {
                currentLevel = 1;
            } else {
                currentLevel = Math.max(1, currentLevel + 1);
            }
            r.setRaiseLevel(currentLevel);
            r.setCurrentBetToCall(totalBet);
            int increment = totalBet - prevBetToCall;
            int minTotalLegacy = prevBetToCall + Math.max(r.getLastRaiseIncrement(), r.getBigBlindChips());
            // 短全下（按原 NL 规则够不着最小总注）仍不刷新增量；其余抬升一律按实际增量写入，避免关闭校验后增量与局面脱节
            boolean shortAllInBelowLegacyMin = becameAllIn && totalBet < minTotalLegacy;
            if (!shortAllInBelowLegacyMin) {
                r.setLastRaiseIncrement(increment);// 不是就设置新的增量，是的话按旧的来
            } // 所谓短全下就是比如最小加注是40，但是用户all
              // in了也够不到这个完整的mini-raise,比如它的增量只是20，那么后面的人继续按最小加注是40来算，忽略这个all in者的增量
        }

        boolean isRaise = totalBet > betToCallBefore;

        p.setChips(p.getChips() - amount);
        p.setBet(totalBet);
        p.setTotalBet(p.getTotalBet() + amount);
        p.setActed(true);
        r.setPot(r.getPot() + amount);

        DpNpcStreetActionLog.recordBetLikeAction(r, p, amount, betToCallBefore, actorBetBefore, potBefore, becameAllIn,
                isRaise);
        observedHandService.recordBetLikeAction(r, p, amount, betToCallBefore, actorBetBefore, potBefore, becameAllIn,
                isRaise);

        moveToNextValidActor(r);
        autoAdvanceIfRoundFinished(r);
        return true;
    }

    public boolean fold(String roomId, String nickname) {
        DpRoomBO r = roomMap.get(roomId);
        if (r == null || !r.isPlaying() || r.getCurrentActorIndex() < 0)
            return false;

        DpPlayer p = r.getPlayers().get(r.getCurrentActorIndex());
        if (!p.getNickname().equals(nickname) || p.isFold())
            return false;

        p.setFold(true);// 弃牌的人没设置行动状态，只有bet的人才设置
        DpNpcStreetActionLog.recordFold(r, p, r.getPot());
        observedHandService.recordFold(r, p, r.getPot());
        moveToNextValidActor(r); // 统一用新方法，不再用旧的 nextActor
        autoAdvanceIfRoundFinished(r);
        return true;
    }

    // ========== 游戏进行中：阶段推进 ==========

    /**
     * 自动推进阶段：当本轮所有可行动玩家都行动完毕 (currentActorIndex == -1) 时，
     * 自动从 preflop/flop/turn/river 往后走，直到出现新的行动者或进入 showdown。
     */
    private void autoAdvanceIfRoundFinished(DpRoomBO r) {
        if (r == null || !r.isPlaying())
            return;
        // 只有当当前轮已经没有行动者时才考虑推进
        if (r.getCurrentActorIndex() >= 0)
            return;

        // 连续推进，处理“所有人都 all-in”的情况
        while (true) {
            if (!advanceStage(r))
                break;
            // 自动结算逻辑在 advanceStage 中处理，结算后阶段会切到 settled
            if ("settled".equals(r.getCurrentStage()))
                break;
            // 如果出现了新的行动者（还有人可以下注），也停止，等待玩家操作
            if (r.getCurrentActorIndex() >= 0)
                break;
        }
    }

    /**
     * 内部通用：在一轮下注结束后推进阶段（翻牌/转牌/河牌/摊牌）
     */
    private boolean advanceStage(DpRoomBO r) {
        if (r == null || !r.isPlaying())
            return false;

        // 清理本轮投注数据（不清 totalBet 和 allIn）
        for (DpPlayer p : r.getPlayers()) {
            p.setBet(0);
            p.setActed(false);
        }
        r.setCurrentBetToCall(0);
        r.setRaiseLevel(0);
        r.setLastRaiseIncrement(r.getBigBlindChips());

        List<String> deck = r.getDeck();
        switch (r.getCurrentStage()) {
            case "preflop":
                r.getCommunityCards().add(deck.remove(0));
                r.getCommunityCards().add(deck.remove(0));
                r.getCommunityCards().add(deck.remove(0));
                r.setCurrentStage("flop");
                // System.out.println("设置flop阶段");
                observedHandService.recordBoardState(r);
                break;
            case "flop":
                r.getCommunityCards().add(deck.remove(0));
                r.setCurrentStage("turn");
                observedHandService.recordBoardState(r);
                // System.out.println("设置turn阶段");
                break;
            case "turn":
                r.getCommunityCards().add(deck.remove(0));
                r.setCurrentStage("river");
                observedHandService.recordBoardState(r);
                // System.out.println("设置river阶段");
                break;
            case "river":
                r.setCurrentStage("showdown");
                // System.out.println("设置showdown阶段");
                calculatePots(r); // 进入摊牌时计算主池/边池
                r.setCurrentActorIndex(-1);
                // 摊牌后立即自动结算
                autoSettle(r);
                return true;
            default:
                return false;
        }

        // 非 showdown 阶段，优先判断后续是否还可能有新的下注，检测全员或剩一人没有All in
        if (noFurtherBettingPossible(r)) {
            // 已经不可能再出现新的下注：不设置行动者，让外层循环自动连推到摊牌
            r.setCurrentActorIndex(-1);
            return true;
        }

        // 否则按正常逻辑找下一位行动者
        int newIndex = findFirstActorAfterDealer(r);
        // 原来剩最后一个人手动连推
        int stillInCount = countPlayersStillInHand(r);
        // 弃牌剩一个人时，直接收池
        if (newIndex >= 0 && stillInCount <= 1) {
            // 只剩一人未弃牌，等价于直接收池
            r.setCurrentActorIndex(-1);
            return true;
        }
        // 正常推进
        if (newIndex >= 0) {
            r.setCurrentActorIndex(newIndex);
            r.setLastActionTime(System.currentTimeMillis());
            return true;
        }

        // 所有人都 all-in 或弃牌了，没人能行动，直接标记本轮结束
        r.setCurrentActorIndex(-1);
        return true;
    }

    /**
     * 统计本手牌中仍未弃牌且在局的玩家数量（未离开本手）。
     * 用于判断“只剩一人”时是否连推到摊牌。
     */
    private int countPlayersStillInHand(DpRoomBO r) {
        if (r == null || r.getPlayers() == null)
            return 0;
        int count = 0;
        for (DpPlayer p : r.getPlayers()) {
            if (!p.isLeftThisHand() && !p.isFold())
                count++;
        }
        return count;
    }

    // 这个是倒数第二个人弃牌之后直接触发连推的逻辑
    private int countPlayersStillInHandAndNotActed(DpRoomBO r) {
        // 这里是规避两个人玩的时候出现逻辑错误
        if (r == null || r.getPlayers() == null || r.getPlayers().size() <= 2)
            return 0;
        int count = 0;
        for (DpPlayer p : r.getPlayers()) {
            if (!p.isLeftThisHand() && !p.isFold() && !p.isActed())
                count++;
        }
        return count;
    }

    private boolean isOnlyOnePlayerStillInHandAndNotActedAndFold(DpRoomBO r) {
        return countPlayersStillInHandAndNotActed(r) == 1;
    }

    /**
     * 判断后续街是否已经不可能再出现新的下注：
     * - 仍在本手牌中的玩家中，非 all-in 的人数 <= 1。
     * （要么全部 all-in，要么只有一个人还有筹码，但其它人都 all-in 了）
     * 这种情况下，无论有无边池，系统都可以直接连推到摊牌。
     */
    private boolean noFurtherBettingPossible(DpRoomBO r) {
        if (r == null || r.getPlayers() == null)
            return false;
        int notAllIn = 0;
        for (DpPlayer p : r.getPlayers()) {
            if (p.isLeftThisHand() || p.isFold())
                continue;
            if (!p.isAllIn()) {
                notAllIn++;
                if (notAllIn > 1) {
                    return false;
                }
            }
        }
        // 所有人 all-in（notAllIn == 0）或仅剩一个人没 all-in，且其余都 all-in
        return notAllIn <= 1;
    }

    /**
     * 本手结束后用于「连胜」的赢家：在所有未弃牌、本手未离桌的玩家中，取摊牌牌力最强者（含并列第一）。
     * <p>
     * 说明：按池分配时，边池可能只有筹码更深的一方有资格，会不比较牌力就独吞边池；
     * 若用“分到池子的人”算连胜，会出现“输主池但赢边池仍算胜”的错误。连胜只跟全局牌力挂钩。
     * </p>
     */
    private Set<String> computeStreakWinnersByBestHand(DpRoomBO r,
            Map<String, DpUtilHandEvaluator.HandStrength> strengthMap) {
        List<DpPlayer> stillIn = r.getPlayers().stream()
                .filter(p -> p != null && !p.isLeftThisHand() && !p.isFold())
                .collect(Collectors.toList());
        if (stillIn.isEmpty()) {
            return Collections.emptySet();
        }
        // 仅剩一人（典型：其余人弃牌），直接计胜，无需比牌
        if (stillIn.size() == 1) {
            return Collections.singleton(stillIn.get(0).getNickname());
        }
        DpUtilHandEvaluator.HandStrength best = null;
        for (DpPlayer p : stillIn) {
            DpUtilHandEvaluator.HandStrength hs = strengthMap.get(p.getNickname());
            if (hs == null) {
                continue;
            }
            if (best == null || hs.compareTo(best) > 0) {
                best = hs;
            }
        }
        if (best == null) {
            return Collections.emptySet();
        }
        Set<String> out = new HashSet<>();
        for (DpPlayer p : stillIn) {
            DpUtilHandEvaluator.HandStrength hs = strengthMap.get(p.getNickname());
            if (hs != null && hs.compareTo(best) == 0) {
                out.add(p.getNickname());
            }
        }
        return out;
    }

    /**
     * 本手牌池底分配完成后更新连胜：牌力全局第一或并列第一的玩家 +1，其余未离线座位清零。
     * 若没有任何此类赢家（异常局），不修改避免误清空。
     */
    private void applyWinStreakAfterHand(DpRoomBO r, Set<String> winnerNicknames) {
        if (r == null || r.getPlayers() == null)
            return;
        if (winnerNicknames == null || winnerNicknames.isEmpty())
            return;
        for (DpPlayer p : r.getPlayers()) {
            if (p == null || p.isLeftThisHand())
                continue;
            if (winnerNicknames.contains(p.getNickname())) {
                p.setWinStreak(p.getWinStreak() + 1);
            } else {
                p.setWinStreak(0);
            }
        }
    }

    // ========== 游戏尾声：结算与下一局准备 ==========

    /**
     * 按当前 {@link DpPlayer#getChips()} 计算场上（未本手离席）筹码最高者昵称列表，写入房间供前端座位光效。
     * 仅在 autoSettle 末尾调用，第一局开局前列表为空，避免「全员同分并列第一」误亮。
     */
    private void refreshChipLeaderNicknames(DpRoomBO r) {
        if (r == null) {
            return;
        }
        List<String> leaders = new ArrayList<>();
        List<DpPlayer> players = r.getPlayers();
        if (players == null || players.isEmpty()) {
            r.setChipLeaderNicknames(leaders);
            return;
        }
        int max = Integer.MIN_VALUE;
        for (DpPlayer p : players) {
            if (p == null || p.isLeftThisHand()) {
                continue;
            }
            int c = p.getChips();
            if (c > max) {
                max = c;
            }
        }
        if (max == Integer.MIN_VALUE) {
            r.setChipLeaderNicknames(leaders);
            return;
        }
        for (DpPlayer p : players) {
            if (p == null || p.isLeftThisHand()) {
                continue;
            }
            if (p.getChips() == max) {
                leaders.add(p.getNickname());
            }
        }
        r.setChipLeaderNicknames(leaders);
    }

    /** 本手结算收尾：从 players 移除已离线占位，与 {@link #autoSettle} 主路径及零底池捷径一致。 */
    private void removeLeftThisHandZombiesAfterHand(DpRoomBO r) {
        if (r == null) {
            return;
        }
        List<DpPlayer> ps = r.getPlayers();
        if (ps == null || ps.isEmpty()) {
            return;
        }
        List<DpPlayer> humanZombies = new ArrayList<>();
        for (DpPlayer p : ps) {
            if (p != null && p.isLeftThisHand() && !DpNpcEngine.isBotPlayer(p)) {
                humanZombies.add(p);
            }
        }
        ps.removeIf(DpPlayer::isLeftThisHand);
        for (DpPlayer p : humanZombies) {
            presenceTryMarkIdleFullyLeft(
                    p.getNickname(), p.getDpUserId(), r, "hand_settle_zombie_removed");
        }
    }

    // ========== J. 一手结算与进入 settled ==========

    /**
     * 自动结算当前房间：为每个池按牌力选出赢家并分配筹码，然后进入结算完成/准备下一局阶段。
     */
    private void autoSettle(DpRoomBO r) {
        if (r == null || !r.isPlaying())
            return;

        // 至少 1 人未弃牌收池时也应公开其底牌；原先 >=2 会在「对手弃牌、独赢」时置 false，
        // 与 sanitizeHoleCardsForViewer 在 settled 阶段依赖本标志矛盾，导致非房主看不到赢家手牌。
        final boolean lastHandPublic = countPlayersStillInHand(r) >= 1;

        // 没有任何下注，直接标记为结算完成
        if (r.getPot() <= 0 && (r.getPots() == null || r.getPots().isEmpty())) {
            autoSettleZeroPotAndEnterSettledShortcut(r);
            return;
        }

        autoSettleNormalPotShowdownPath(r, lastHandPublic);
    }

    /** autoSettle 零底池短路：与原 if 分支内联实现逐行等价。 */
    private void autoSettleZeroPotAndEnterSettledShortcut(DpRoomBO r) {
        r.setLastHandHoleCardsPublic(false);
        r.setCurrentStage("settled");
        r.setReadyDeadline(System.currentTimeMillis() + 30_000L);
        for (DpPlayer p : r.getPlayers()) {
            // settle阶段会自动把大家的准备状态设置为false
            p.setReady(false);
        }
        // 每局结算的时候将牌谱归档，并存入数据库
        DpObservedHandRecordBO archivedEarly = observedHandService.finalizeHand(r);
        if (archivedEarly != null) {
            observedHandPersistService.save(archivedEarly, r);
        }
        DpNpcStreetActionLog.clearHand(r);
        observedHandService.clearHand(r);
        removeLeftThisHandZombiesAfterHand(r);
        refreshChipLeaderNicknames(r);
        if (checkAndStartNextHandAfterSettleReturning(r)) {
            refreshJoinableQmIndexThenSyncLobby(r.getRoomId());
        }
    }

    /**
     * autoSettle 主路径：边池分配、统计与归档后进入 settled（参数 {@code lastHandPublic} 与原局部变量一致）。
     */
    private void autoSettleNormalPotShowdownPath(DpRoomBO r, boolean lastHandPublic) {

        // 先确保已经有主池/边池数据
        if (r.getPots() == null || r.getPots().isEmpty()) {
            // 兼容：如果还没算过 pots，就根据总 pot 和没弃牌玩家算一个主池
            DpPot pot = new DpPot();
            pot.setAmount(r.getPot());
            List<String> eligible = r.getPlayers().stream()
                    .filter(p -> !p.isFold())
                    .map(DpPlayer::getNickname)
                    .collect(Collectors.toList());
            pot.setEligiblePlayers(eligible);
            List<DpPot> list = new ArrayList<>();
            list.add(pot);
            r.setPots(list);
        }

        // 预先解析每个玩家的 7 张牌，评估牌力（供结算与统计复用）
        Map<String, DpUtilHandEvaluator.HandStrength> strengthMap = new HashMap<>();
        for (DpPlayer p : r.getPlayers()) {
            if (p.isFold())
                continue;
            List<String> allCards = new ArrayList<>();
            if (p.getHoleCards() != null) {
                allCards.addAll(p.getHoleCards());
            }
            if (r.getCommunityCards() != null) {
                allCards.addAll(r.getCommunityCards());
            }
            if (allCards.size() < 5)
                continue;
            DpUtilHandEvaluator.HandStrength hs = DpUtilHandEvaluator.evaluateBestHand(allCards);
            strengthMap.put(p.getNickname(), hs);
        }

        // 按池分配：每个池在 eligiblePlayers 中找到牌力最高的一批人，平分该池
        Set<String> streakWinnerNicknames = computeStreakWinnersByBestHand(r, strengthMap);
        for (DpPot pot : r.getPots()) {
            List<String> eligible = pot.getEligiblePlayers();
            if (eligible == null || eligible.isEmpty())
                continue;

            DpUtilHandEvaluator.HandStrength best = null;
            List<DpPlayer> winners = new ArrayList<>();

            for (String name : eligible) {
                DpUtilHandEvaluator.HandStrength hs = strengthMap.get(name);
                if (hs == null)
                    continue;
                if (best == null || hs.compareTo(best) > 0) {
                    best = hs;
                    winners.clear();
                    DpPlayer winnerPlayer = r.getPlayers().stream()
                            .filter(p -> p.getNickname().equals(name))
                            .findFirst().orElse(null);
                    if (winnerPlayer != null) {
                        winners.add(winnerPlayer);
                    }
                } else if (hs.compareTo(best) == 0) {
                    DpPlayer winnerPlayer = r.getPlayers().stream()
                            .filter(p -> p.getNickname().equals(name))
                            .findFirst().orElse(null);
                    if (winnerPlayer != null) {
                        winners.add(winnerPlayer);
                    }
                }
            }

            if (winners.isEmpty())
                continue;

            int share = pot.getAmount() / winners.size();
            int remainder = pot.getAmount() % winners.size();
            for (DpPlayer w : winners) {
                w.setChips(w.getChips() + share);
                if (remainder > 0) {
                    w.setChips(w.getChips() + 1);
                    remainder -= 1;
                }
            }
        }

        applyWinStreakAfterHand(r, streakWinnerNicknames);

        // 结算分配前快照底池结构（随后会清空 pots）
        observedHandService.capturePotsBeforeClear(r);

        r.setPot(0);
        r.setPots(new ArrayList<>());

        // ==== 更新玩家行为统计（全桌昵称维度；供 NPC / 回放分析等使用） ====
        // 逐街动作快照：填充 SingleHandStats 各街激进/放弃等字段
        List<DpNpcStreetActionLog.ActionEvent> streetEvents = DpNpcStreetActionLog.snapshot(r);

        if (r.getPlayerStatsMap() != null) {
            Map<String, DpPlayerStats> statsMap = r.getPlayerStatsMap();
            int bb = r.getBigBlindChips();
            for (DpPlayer p : r.getPlayers()) {
                String name = p.getNickname();
                DpPlayerStats stats = statsMap.get(name);
                if (stats == null) {
                    stats = new DpPlayerStats();
                    statsMap.put(name, stats);
                }
                DpPlayerStats.SingleHandStats hand = new DpPlayerStats.SingleHandStats();
                int totalBet = p.getTotalBet();
                boolean participated = totalBet > 0 && !p.isFold();
                hand.setParticipated(participated);
                // 加注：简单视为本手中有过超过大盲的下注（不区分具体街道）
                hand.setRaised(bb > 0 && totalBet > bb);
                // 摊牌：到 showdown 阶段且未弃牌
                boolean wentToShowdown = "showdown".equals(r.getCurrentStage()) && !p.isFold();
                hand.setWentToShowdown(wentToShowdown);
                // 记录摊牌时的最终牌型大类（仅在真正摊牌且有评估结果时有效）
                if (wentToShowdown) {
                    DpUtilHandEvaluator.HandStrength hs = strengthMap.get(name);
                    hand.setFinalRankCategory(hs != null ? hs.rankCategory : 0);
                    hand.setFinalStrengthValue(DpUtilHandEvaluator.encodeStrengthValue(hs));
                    // 同时记录“公共牌主导”的基准：仅用公共牌能形成的最佳牌型大类
                    // 用于后续在统计中剔除“大家都在打公共牌”的摊牌样本。
                    int boardCat = 0;
                    List<String> board = r.getCommunityCards();
                    if (board != null && board.size() >= 5) {
                        DpUtilHandEvaluator.HandStrength bhs = DpUtilHandEvaluator.evaluateBestHand(board);
                        boardCat = (bhs != null ? bhs.rankCategory : 0);
                    }
                    hand.setBoardRankCategory(boardCat);

                    // 更精确的公共牌主导判定：最终最佳 5 张牌是否完全不使用手牌
                    boolean boardDominant = false;
                    List<String> hole = p.getHoleCards();
                    if (board != null && board.size() >= 5 && hole != null && hole.size() >= 2) {
                        List<String> all = new java.util.ArrayList<>();
                        all.addAll(board);
                        all.addAll(hole);
                        List<String> best5 = DpUtilHandEvaluator.getBestHandCards(all);
                        if (best5 != null && best5.size() == 5) {
                            String h1 = hole.get(0);
                            String h2 = hole.get(1);
                            boolean usedHole = (h1 != null && best5.contains(h1)) || (h2 != null && best5.contains(h2));
                            boardDominant = !usedHole;
                        }
                    }
                    hand.setBoardDominantShowdown(boardDominant);
                }

                // === 基于本手最终下注规模和公共牌张数的粗略行为标签推断 ===
                // 这里只做非常简化的近似：项目当前未记录逐街下注轨迹，因此只能依赖 totalBet / 公共牌数量等信息。
                // 这些标签主要用于长期统计上的趋势判断，而非精确还原单手动作。

                // 粗略判断翻前 limp / open-raise：
                boolean limpedPreflop = false;
                boolean openRaisedPreflop = false;
                if (participated && bb > 0) {
                    int blindChips = 0;
                    if (p.getBlind() == 1) {
                        blindChips = r.getSmallBlindChips();
                    } else if (p.getBlind() == 2) {
                        blindChips = r.getBigBlindChips();
                    }
                    // 视为“自愿入池”的最小投入：超过自身盲注部分的筹码
                    int voluntary = Math.max(0, totalBet - blindChips);
                    // 典型 limp：仅投入少量筹码（<= 2BB）且整体未超过常见开局加注尺寸
                    if (p.getBlind() == 0 && voluntary > 0 && totalBet <= 2 * bb && !hand.isRaised()) {
                        limpedPreflop = true;
                    }
                    // 典型 open-raise：非盲位且总投入已经达到 3BB 以上
                    if (p.getBlind() == 0 && totalBet >= 3 * bb) {
                        openRaisedPreflop = true;
                    }
                }
                hand.setLimpedPreflop(limpedPreflop);
                hand.setOpenRaisedPreflop(openRaisedPreflop);

                // === 逐街激进行为：优先用逐街动作日志填充（否则回退为 false） ===
                boolean flopAgg = false;
                boolean turnAgg = false;
                boolean riverAgg = false;
                boolean foldedOnTurn = false;
                boolean foldedOnRiver = false;
                boolean hadAggBeforeTurn = false;
                boolean hadAggBeforeRiver = false;

                if (streetEvents != null && !streetEvents.isEmpty()) {
                    for (DpNpcStreetActionLog.ActionEvent e : streetEvents) {
                        if (e == null)
                            continue;
                        if (!name.equals(e.actor))
                            continue;
                        boolean isAgg = e.type == DpNpcStreetActionLog.ActionType.BET
                                || e.type == DpNpcStreetActionLog.ActionType.RAISE
                                || e.type == DpNpcStreetActionLog.ActionType.ALL_IN;
                        if ("flop".equals(e.stage)) {
                            if (isAgg) {
                                flopAgg = true;
                                hadAggBeforeTurn = true;
                                hadAggBeforeRiver = true;
                            }
                        } else if ("turn".equals(e.stage)) {
                            if (isAgg) {
                                turnAgg = true;
                                hadAggBeforeRiver = true;
                            }
                            if (e.type == DpNpcStreetActionLog.ActionType.FOLD) {
                                foldedOnTurn = true;
                            }
                        } else if ("river".equals(e.stage)) {
                            if (isAgg) {
                                riverAgg = true;
                            }
                            if (e.type == DpNpcStreetActionLog.ActionType.FOLD) {
                                foldedOnRiver = true;
                            }
                        }
                    }
                }

                hand.setPreflopOpenRaise(openRaisedPreflop);
                hand.setFlopAggressive(flopAgg);
                hand.setTurnAggressive(turnAgg);
                hand.setRiverAggressive(riverAgg);

                // 判断“加注后在 turn/river 放弃”的粗略模式：
                boolean gaveUpTurnAfterRaise = false;
                boolean gaveUpRiverAfterRaise = false;
                if (hand.isRaised() && !wentToShowdown) {
                    // 优先用动作日志判断放弃街道（更准确）；没有日志时才回退用公共牌张数猜
                    if (streetEvents != null && !streetEvents.isEmpty()) {
                        if (foldedOnTurn && (hadAggBeforeTurn || openRaisedPreflop)) {
                            gaveUpTurnAfterRaise = true;
                        }
                        if (foldedOnRiver && (hadAggBeforeRiver || openRaisedPreflop)) {
                            gaveUpRiverAfterRaise = true;
                        }
                    } else {
                        int communitySize = r.getCommunityCards() != null ? r.getCommunityCards().size() : 0;
                        // community=4 代表至少看到 turn 后弃牌，视为 turn give-up；=5 代表 river give-up
                        if (communitySize >= 4 && communitySize < 5) {
                            gaveUpTurnAfterRaise = true;
                        } else if (communitySize >= 5) {
                            gaveUpRiverAfterRaise = true;
                        }
                    }
                }
                hand.setGaveUpTurnAfterRaise(gaveUpTurnAfterRaise);
                hand.setGaveUpRiverAfterRaise(gaveUpRiverAfterRaise);

                stats.addHand(hand, 10);

                // 个体化长期弃牌调整：
                // - 若该玩家经常以较弱牌力摊牌且本手去了摊牌，适当降低 foldAdjustment；
                // - 若摊牌大多是强牌，则略微提高 foldAdjustment。
                double adj = stats.getFoldAdjustmentAgainstHero();
                if (wentToShowdown) {
                    int cat = hand.getFinalRankCategory();
                    if (cat > 0 && cat <= 4) {
                        adj -= 0.01;
                    } else if (cat >= 7) {
                        adj += 0.01;
                    }
                    if (adj > 0.3)
                        adj = 0.3;
                    if (adj < -0.3)
                        adj = -0.3;
                    stats.setFoldAdjustmentAgainstHero(adj);
                }
            }
        }

        // 根据赢/输调整机器人情绪（可通过 DpNpcEngine.NPC_MOOD_ENABLED 关闭）
        if (DpNpcEngine.NPC_MOOD_ENABLED) {
            for (DpPlayer p : r.getPlayers()) {
                if (!DpNpcEngine.isBotPlayer(p)) {
                    continue;
                }
                int initialChips = r.getStartingChips();
                int diff = p.getChips() - initialChips;
                double moodDelta;
                if (diff > 0) {
                    moodDelta = 0.2;
                } else if (diff < 0) {
                    moodDelta = -0.2;
                } else {
                    continue;
                }
                double newMood = p.getMood() + moodDelta;
                if (newMood > 1.0)
                    newMood = 1.0;
                if (newMood < -1.0)
                    newMood = -1.0;
                p.setMood(newMood);
            }
        }

        // 完整牌谱归档 → 可选对手画像入库；最后再清理当手观测/逐街缓冲
        DpObservedHandRecordBO archived = observedHandService.finalizeHand(r);
        if (archived != null) {
            observedHandPersistService.save(archived, r);
        }

        // 逐街动作日志：本手结束后清理，避免内存增长
        DpNpcStreetActionLog.clearHand(r);
        observedHandService.clearHand(r);

        removeLeftThisHandZombiesAfterHand(r);

        refreshChipLeaderNicknames(r);

        // 进入“结算完成，等待准备下一局”阶段，并开始 30 秒准备倒计时
        r.setLastHandHoleCardsPublic(lastHandPublic);
        r.setCurrentStage("settled");
        r.setReadyDeadline(System.currentTimeMillis() + 30_000L);
        for (DpPlayer p : r.getPlayers()) {
            p.setReady(false);
        }
        // checkAndStartNextHandAfterSettle(r);
    }

    /**
     * 结算准备倒计时到期：先 {@link #pruneSettledTableForReadyTimeout}，再
     * {@link #checkAndStartNextHandAfterSettleReturning}
     * （外层在释放临界区后在 {@link #handleReadyTimeout} 中 upsert）。
     */
    private void handleReadyTimeout(DpRoomBO r) {
        System.out.println("结算准备倒计时到期");
        if (r == null || !r.isPlaying()) {
            return;
        }
        if (!"settled".equals(r.getCurrentStage())) {
            return;
        }
        if (!pruneSettledTableForReadyTimeout(r)) {
            return;
        }
        if (checkAndStartNextHandAfterSettleReturning(r)) {
            refreshJoinableQmIndexThenSyncLobby(r.getRoomId());
        }
    }

    /**
     * 结算后筹码不足大盲（10）的玩家补码到初始筹码。
     */
    public boolean rebuy(String roomId, String nickname) {
        DpRoomBO r = roomMap.get(roomId);
        if (r == null || !r.isPlaying())
            return false;
        if (!"settled".equals(r.getCurrentStage()))
            return false;
        for (DpPlayer p : r.getPlayers()) {
            if (p.getNickname().equals(nickname)) {
                // 筹码充足（>=10）则不允许补码
                if (p.getChips() >= r.getBigBlindChips()) {
                    return false;
                }
                p.setChips(r.getStartingChips());
                return true;
            }
        }
        return false;
    }

    // ========== 心跳 ==========

    public void heartbeat(String roomId, String nickname) {
        DpRoomBO r = roomMap.get(roomId);
        if (r == null)
            return;
        synchronized (r) {
            long now = System.currentTimeMillis();
            for (DpPlayer p : r.getPlayers()) {
                if (p.getNickname().equals(nickname)) {
                    p.setLastHeartBeat(now);
                }
            }
            List<String> specs = r.getSpectators();
            if (specs != null && specs.contains(nickname)) {
                r.touchSpectatorPresence(nickname, now);
            }
        }
    }

    private final class QuickMatchPairingHost implements DpQuickMatchPairingHost {

        @Override
        public Object defaultQmLock() {
            return DpRoomServiceImpl.this.defaultQmLock;
        }

        @Override
        public Map<String, DpRoomBO> roomMap() {
            return DpRoomServiceImpl.this.roomMap;
        }

        @Override
        public JoinableQuickMatchRoomIndex joinableQuickMatchRoomIndex() {
            return DpRoomServiceImpl.this.joinableQuickMatchRoomIndex;
        }

        @Override
        public long nowMillis() {
            return System.currentTimeMillis();
        }

        @Override
        public List<String> pruneQueueWhileLocked() {
            List<String> timed = pruneDefaultQuickMatchQueueLockedWithoutReentry();
            notifyQuickMatchTimedOut(timed);
            return timed;
        }

        @Override
        public int defaultQueueSizeWhileLocked() {
            return defaultQmWaiters.size();
        }

        @Override
        public List<DpQuickMatchWaitEntry> pollHeadWhileLocked(int maxTake) {
            if (maxTake <= 0 || defaultQmWaiters.isEmpty()) {
                return List.of();
            }
            int n = Math.min(maxTake, defaultQmWaiters.size());
            List<DpQuickMatchWaitEntry> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                DpQuickMatchWaitEntry e = defaultQmWaiters.pollFirst();
                if (e != null) {
                    out.add(e);
                }
            }
            return out;
        }

        @Override
        public void unshiftHeadWhileLocked(List<DpQuickMatchWaitEntry> entries) {
            if (entries == null || entries.isEmpty()) {
                return;
            }
            for (int i = entries.size() - 1; i >= 0; i--) {
                defaultQmWaiters.addFirst(entries.get(i));
            }
        }

        @Override
        public void addTailWhileLocked(List<DpQuickMatchWaitEntry> entries) {
            if (entries == null || entries.isEmpty()) {
                return;
            }
            for (DpQuickMatchWaitEntry e : entries) {
                defaultQmWaiters.addLast(e);
            }
        }

        @Override
        public boolean nicknameAlreadyInSomeRoom(String nickname) {
            return findRoomContainingNickname(nickname) != null;
        }

        @Override
        public void joinAndReadyWhileRoomLocked(DpRoomBO room, DpQuickMatchWaitEntry entry) {
            String joinMsg = joinRoomMutateAssumeLocked(
                    room.getRoomId(),
                    entry.nickname(),
                    entry.userId(),
                    null,
                    room);
            if (!"ok".equals(joinMsg) && !"游戏已开始".equals(joinMsg)) {
                throw new IllegalStateException("quick match join: " + joinMsg);
            }
            if (room.isPlaying()) {
                if (!applyReadyNextHandWhileLocked(room, entry.nickname(), entry.userId())) {
                    throw new IllegalStateException("quick match readyNextHand failed");
                }
            } else {
                if (!ensureLobbyReady(room, entry.nickname())) {
                    throw new IllegalStateException("quick match ensureLobbyReady failed");
                }
            }
        }

        @Override
        public DpRoomBO createDefaultQuickMatchRoomForPairing(DpQuickMatchWaitEntry owner) {
            return createRoom(
                    owner.nickname(),
                    owner.userId(),
                    DEFAULT_QM_SB,
                    DEFAULT_QM_BB,
                    DEFAULT_QM_STARTING_BB,
                    null,
                    DEFAULT_QM_MAX_SEATS);
        }

        @Override
        public void startGameForQuickMatchRoom(String roomId, String ownerNickname) {
            if (!DpRoomServiceImpl.this.startGame(roomId, ownerNickname)) {
                log.warn(
                        "auto quick-match: startGame failed roomId={} owner={}",
                        roomId,
                        ownerNickname);
            }
        }

        @Override
        public void notifyQuickMatchMatched(String nickname, String roomId) {
            quickMatchPush.notifyMatched(nickname, roomId);
        }

        @Override
        public void afterRoomMutationRefreshQuickMatchIndex(String roomId, String joinDetailMessageOrNull) {
            refreshQmIndexAfterJoinOutcomeOutsideRoomLock(roomId, joinDetailMessageOrNull);
        }

        @Override
        public void flushQueuedWaitersIntoJoinablePublicRooms() {
            while (true) {
                List<String> qmTimedOut;
                List<DpQuickMatchWaitEntry> snapshot;
                synchronized (defaultQmLock) {
                    qmTimedOut = pruneDefaultQuickMatchQueueLockedWithoutReentry();
                    snapshot = new ArrayList<>(defaultQmWaiters);
                }
                notifyQuickMatchTimedOut(qmTimedOut);
                if (snapshot.isEmpty()) {
                    return;
                }
                int roundSuccesses = 0;
                for (DpQuickMatchWaitEntry e : snapshot) {
                    boolean stillInQueue;
                    synchronized (defaultQmLock) {
                        stillInQueue = false;
                        for (DpQuickMatchWaitEntry x : defaultQmWaiters) {
                            if (e.nickname().equals(x.nickname())) {
                                stillInQueue = true;
                                break;
                            }
                        }
                    }
                    if (!stillInQueue) {
                        continue;
                    }
                    ResultUtil res = quickMatchJoinAndReady(e.nickname(), e.userId());
                    if (!Boolean.TRUE.equals(res.getSuccess())) {
                        continue;
                    }
                    synchronized (defaultQmLock) {
                        defaultQmWaiters.removeIf(x -> e.nickname().equals(x.nickname()));
                    }
                    String rid = quickMatchResultRoomId(res);
                    if (rid != null) {
                        quickMatchPush.notifyMatched(e.nickname(), rid);
                    }
                    roundSuccesses++;
                }
                if (roundSuccesses == 0) {
                    return;
                }
            }
        }
    }
}
