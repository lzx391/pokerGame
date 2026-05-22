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
import java.util.concurrent.ConcurrentHashMap;

/**
 * NPC 行动成功后的桌边话术：规则 bot 走台词池，LLM bot 走模型 {@code table_talk} 字段。
 */
@Service
public class DpNpcTableTalkService {

    private static final Logger log = LoggerFactory.getLogger(DpNpcTableTalkService.class);

    private final DpNpcTableTalkProperties properties;
    private final DpNpcLinePoolLoader linePoolLoader;
    private final DpGameRoomPushService gameRoomPushService;

    /** roomId|botNickname → last push epoch ms */
    private final Map<String, Long> lastPushMsByRoomBot = new ConcurrentHashMap<>();
    /** roomId|botNickname → 桌边话独立随机盐（避免与决策 RNG 同种子同首抽） */
    private final Map<String, Long> talkRollSaltByRoomBot = new ConcurrentHashMap<>();

    public DpNpcTableTalkService(
            DpNpcTableTalkProperties properties,
            DpNpcLinePoolLoader linePoolLoader,
            DpGameRoomPushService gameRoomPushService) {
        this.properties = properties;
        this.linePoolLoader = linePoolLoader;
        this.gameRoomPushService = gameRoomPushService;
    }

    /**
     * 在 {@link com.example.mgdemoplus.room.impl.DpRoomServiceImpl#npcAction} 执行成功后调用（单入口，避免重复推送）。
     */
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

    private void publishLlmTableTalk(String roomId, String botNickname, DpNpcEngine.BotAction action) {
        if (action.isRuleTableTalk()) {
            log.debug("table-talk skip: LLM bot missing table_talk room={} bot={}", roomId, botNickname);
            return;
        }
        if (action.getLlmTableTalkMode() == DpNpcEngine.LlmTableTalkMode.SILENT) {
            log.debug("table-talk skip: LLM false room={} bot={}", roomId, botNickname);
            return;
        }
        String text = action.getLlmTableTalkText();
        if (text == null || text.isBlank()) {
            log.debug("table-talk skip: LLM empty text room={} bot={}", roomId, botNickname);
            return;
        }
        tryPublish(roomId, botNickname, text, "llm");
    }

    private void publishRuleTableTalk(DpRoomBO room, DpPlayer bot, DpNpcEngine.BotAction action) {
        String resourceKey;
        if (DpNpcEngine.isCustomBotNickname(bot.getNickname())) {
            resourceKey = "custom";
        } else {
            DpNpcEngine.NpcStyle style = DpNpcEngine.resolveNpcStyleForTableTalk(bot);
            if (style == null) {
                log.debug("table-talk skip: no style room={} bot={}", room.getRoomId(), bot.getNickname());
                return;
            }
            resourceKey = DpNpcLinePoolLoader.resourceKeyForStyle(style);
        }
        Optional<NpcLinePool> poolOpt = linePoolLoader.loadByResourceKey(resourceKey);
        if (poolOpt.isEmpty()) {
            log.debug("table-talk skip: no yaml pool key={} bot={}", resourceKey, bot.getNickname());
            return;
        }
        NpcLinePool pool = poolOpt.get();
        if (!pool.isEnabled()) {
            log.debug("table-talk skip: yaml disabled key={} bot={}", resourceKey, bot.getNickname());
            return;
        }
        double probability = properties.getSpeakProbability() != null
                ? properties.getSpeakProbability()
                : pool.getSpeakProbability();
        long rollSalt = nextTalkRollSalt(room.getRoomId(), bot.getNickname());
        Random random = DpNpcEngine.buildHandRandomForTableTalk(room, bot, rollSalt);
        if (random.nextDouble() >= probability) {
            log.debug("table-talk skip: probability key={} bot={} p={}", resourceKey, bot.getNickname(), probability);
            return;
        }
        String poolKey = actionTypeToPoolKey(action.getType());
        var lines = pool.linesForActionKey(poolKey);
        if (lines.isEmpty()) {
            log.debug("table-talk skip: empty pool key={} res={} bot={}", poolKey, resourceKey, bot.getNickname());
            return;
        }
        String line = lines.get(random.nextInt(lines.size()));
        tryPublish(room.getRoomId(), bot.getNickname(), line, "rule");
    }

    private long nextTalkRollSalt(String roomId, String botNickname) {
        String key = roomId + "|" + botNickname;
        return talkRollSaltByRoomBot.merge(key, 1L, (a, b) -> a + b);
    }

    private void tryPublish(String roomId, String botNickname, String text, String source) {
        if (!throttleAllows(roomId, botNickname)) {
            log.debug("table-talk skip: throttle room={} bot={} source={}", roomId, botNickname, source);
            return;
        }
        boolean sent = gameRoomPushService.publishNpcTableTalk(roomId, botNickname, text);
        if (sent) {
            lastPushMsByRoomBot.put(roomId + "|" + botNickname, System.currentTimeMillis());
        }
    }

    private boolean throttleAllows(String roomId, String botNickname) {
        long minMs = Math.max(0L, properties.getMinIntervalMs());
        if (minMs <= 0L) {
            return true;
        }
        String key = roomId + "|" + botNickname;
        Long last = lastPushMsByRoomBot.get(key);
        long now = System.currentTimeMillis();
        return last == null || now - last >= minMs;
    }

    private static String actionTypeToPoolKey(DpNpcEngine.BotActionType type) {
        if (type == null) {
            return "GENERIC";
        }
        return switch (type) {
            case FOLD -> "FOLD";
            case CALL_OR_CHECK -> "CALL_OR_CHECK";
            case RAISE -> "RAISE";
            case ALL_IN -> "ALL_IN";
        };
    }

}
