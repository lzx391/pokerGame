package com.example.mgdemoplus.npc.tabletalk;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.websocket.DpGameRoomPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NPC 行动成功后的桌边话术：规则 bot 走台词池，LLM bot 走模型 {@code table_talk} 字段。
 * 本手进入 {@code settled} 后，规则 bot 另走 {@code SETTLE_WIN} / {@code SETTLE_LOSE} 池（独立概率、不节流）。
 */
@Service
public class DpNpcTableTalkService {

    private static final Logger log = LoggerFactory.getLogger(DpNpcTableTalkService.class);
    private static final Logger logLlmTableTalk =
            LoggerFactory.getLogger("com.example.mgdemoplus.BotLlmTableTalk");

    private final DpNpcTableTalkProperties properties;
    private final DpNpcLinePoolLoader linePoolLoader;
    private final DpGameRoomPushService gameRoomPushService;

    /** roomId|botNickname → last push epoch ms（仅行动中话术节流） */
    private final Map<String, Long> lastPushMsByRoomBot = new ConcurrentHashMap<>();
    /** roomId|botNickname → 桌边话独立随机盐 */
    private final Map<String, Long> talkRollSaltByRoomBot = new ConcurrentHashMap<>();

    public DpNpcTableTalkService(
            DpNpcTableTalkProperties properties,
            DpNpcLinePoolLoader linePoolLoader,
            DpGameRoomPushService gameRoomPushService) {
        this.properties = properties;
        this.linePoolLoader = linePoolLoader;
        this.gameRoomPushService = gameRoomPushService;
    }

    public void afterNpcActionSucceeded(DpRoomBO room, DpPlayer bot, DpNpcEngine.BotAction action) {
        if (room == null || bot == null || action == null) {
            return;
        }
        if (!properties.isEnabled()) {
            log.debug("table-talk skip: global disabled room={} bot={}", room.getRoomId(), bot.getNickname());
            return;
        }
        if (!DpNpcEngine.isBotPlayer(bot)) {
            return;
        }
        String roomId = room.getRoomId();
        String nick = bot.getNickname();
        if (DpNpcEngine.isLlmBotNickname(nick)) {
            publishLlmTableTalk(roomId, nick, action);
        } else {
            publishRuleTableTalk(room, bot, action);
        }
    }

    /**
     * 分池与筹码落定、阶段已进入 {@code settled} 之后调用。
     * {@code showdownWinnerNicknames} 与 {@code computeStreakWinnersByBestHand} 一致。
     */
    public void afterHandSettled(DpRoomBO room, Set<String> showdownWinnerNicknames) {
        if (room == null || showdownWinnerNicknames == null || showdownWinnerNicknames.isEmpty()) {
            return;
        }
        if (!properties.isEnabled() || room.getPlayers() == null) {
            return;
        }
        for (DpPlayer bot : room.getPlayers()) {
            if (bot == null || bot.isLeftThisHand()) {
                continue;
            }
            if (!isRuleNpcForSettleTalk(bot)) {
                continue;
            }
            boolean win = showdownWinnerNicknames.contains(bot.getNickname());
            String poolKey = win ? "SETTLE_WIN" : "SETTLE_LOSE";
            publishSettleTableTalk(room, bot, poolKey);
        }
    }

    private static boolean isRuleNpcForSettleTalk(DpPlayer bot) {
        return DpNpcEngine.isBotPlayer(bot) && !DpNpcEngine.isLlmBotNickname(bot.getNickname());
    }

    private void publishLlmTableTalk(String roomId, String botNickname, DpNpcEngine.BotAction action) {
        if (action.isRuleTableTalk()) {
            logLlmTableTalk.info(
                    "table_talk WS跳过: 解析为 RULE(缺 table_talk 或 JSON 未带上) room={} bot={}",
                    roomId,
                    botNickname);
            return;
        }
        if (action.getLlmTableTalkMode() == DpNpcEngine.LlmTableTalkMode.SILENT) {
            logLlmTableTalk.info("table_talk WS跳过: false/缺失/空串 room={} bot={}", roomId, botNickname);
            return;
        }
        String text = action.getLlmTableTalkText();
        if (text == null || text.isBlank()) {
            logLlmTableTalk.info("table_talk WS跳过: SAY 但文本空 room={} bot={}", roomId, botNickname);
            return;
        }
        logLlmTableTalk.info("table_talk WS尝试推送 room={} bot={} text={}", roomId, botNickname, text);
        tryPublish(roomId, botNickname, text, "llm", true);
    }

    private void publishRuleTableTalk(DpRoomBO room, DpPlayer bot, DpNpcEngine.BotAction action) {
        Optional<NpcLinePool> poolOpt = resolveLinePool(bot);
        if (poolOpt.isEmpty() || !poolOpt.get().isEnabled()) {
            return;
        }
        NpcLinePool pool = poolOpt.get();
        double probability = properties.getSpeakProbability() != null
                ? properties.getSpeakProbability()
                : pool.getSpeakProbability();
        Random random = DpNpcEngine.buildHandRandomForTableTalk(
                room, bot, nextTalkRollSalt(room.getRoomId(), bot.getNickname()));
        if (random.nextDouble() >= probability) {
            return;
        }
        String poolKey = actionTypeToPoolKey(action.getType());
        var lines = pool.linesForActionKey(poolKey);
        if (lines.isEmpty()) {
            return;
        }
        tryPublish(room.getRoomId(), bot.getNickname(), lines.get(random.nextInt(lines.size())), "rule", true);
    }

    private void publishSettleTableTalk(DpRoomBO room, DpPlayer bot, String poolKey) {
        Optional<NpcLinePool> poolOpt = resolveLinePool(bot);
        if (poolOpt.isEmpty() || !poolOpt.get().isEnabled()) {
            return;
        }
        NpcLinePool pool = poolOpt.get();
        double probability = properties.getSettleSpeakProbability() != null
                ? properties.getSettleSpeakProbability()
                : pool.getSettleSpeakProbability();
        Random random = DpNpcEngine.buildHandRandomForTableTalk(
                room, bot, nextTalkRollSalt(room.getRoomId(), bot.getNickname()));
        if (random.nextDouble() >= probability) {
            log.debug("settle-talk skip: probability bot={} p={} key={}", bot.getNickname(), probability, poolKey);
            return;
        }
        var lines = pool.linesForPoolKey(poolKey);
        if (lines.isEmpty()) {
            return;
        }
        tryPublish(room.getRoomId(), bot.getNickname(), lines.get(random.nextInt(lines.size())), "settle", false);
    }

    private Optional<NpcLinePool> resolveLinePool(DpPlayer bot) {
        String resourceKey;
        if (DpNpcEngine.isCustomBotNickname(bot.getNickname())) {
            resourceKey = "custom";
        } else {
            DpNpcEngine.NpcStyle style = DpNpcEngine.resolveNpcStyleForTableTalk(bot);
            if (style == null) {
                return Optional.empty();
            }
            resourceKey = DpNpcLinePoolLoader.resourceKeyForStyle(style);
        }
        return linePoolLoader.loadByResourceKey(resourceKey);
    }

    private long nextTalkRollSalt(String roomId, String botNickname) {
        return talkRollSaltByRoomBot.merge(roomId + "|" + botNickname, 1L, Long::sum);
    }

    private void tryPublish(String roomId, String botNickname, String text, String source, boolean applyThrottle) {
        if (applyThrottle && !throttleAllows(roomId, botNickname)) {
            if ("llm".equals(source)) {
                logLlmTableTalk.info(
                        "table_talk WS跳过: 节流 minIntervalMs={} room={} bot={}",
                        properties.getMinIntervalMs(),
                        roomId,
                        botNickname);
            }
            return;
        }
        boolean sent = gameRoomPushService.publishNpcTableTalk(roomId, botNickname, text);
        if (sent && applyThrottle) {
            lastPushMsByRoomBot.put(roomId + "|" + botNickname, System.currentTimeMillis());
            if ("llm".equals(source)) {
                logLlmTableTalk.info("table_talk WS已推送 room={} bot={}", roomId, botNickname);
            }
        }
    }

    private boolean throttleAllows(String roomId, String botNickname) {
        long minMs = Math.max(0L, properties.getMinIntervalMs());
        if (minMs <= 0L) {
            return true;
        }
        String key = roomId + "|" + botNickname;
        Long last = lastPushMsByRoomBot.get(key);
        return last == null || System.currentTimeMillis() - last >= minMs;
    }

    private static String actionTypeToPoolKey(DpNpcEngine.BotActionType type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case FOLD -> "FOLD";
            case CALL_OR_CHECK -> "CALL_OR_CHECK";
            case RAISE -> "RAISE";
            case ALL_IN -> "ALL_IN";
        };
    }
}
