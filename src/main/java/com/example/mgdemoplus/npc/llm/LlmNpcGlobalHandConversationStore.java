package com.example.mgdemoplus.npc.llm;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.llm.OpenAiCompatibleChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BOT_LLM_GLOBAL 多轮对话：按同一牌局生命周期内 {@code roomId + botNick + handSeed} 存档 user/assistant 交替消息。
 * <p>
 * 策略：<ul>
 *   <li>新一手牌（{@code handSeed} 变化）使用新键；本手结束后由 {@link #clearHand} 移除当手各 bot 条目。</li>
 *   <li>仅在一次决策被主线程<strong>采纳</strong>模型动作后写入一轮（user 快照正文 + assistant 原始 JSON 正文）。</li>
 *   <li>在途请求被票据作废或超时：不会追加，以免污染下一轮。</li>
 *   <li>同一手内保留全部已采纳轮次（无条数上限）；map 键数由 {@link #pruneExcessEntries} 软限制。</li>
 * </ul>
 */
@Component
public final class LlmNpcGlobalHandConversationStore {

    /** 粗略上限：避免长期运行 map 单向增长（例如机器人久留旁观者席）。 */
    static final int MAX_TOTAL_KEYS_SOFT = 1024;

    private final ConcurrentHashMap<String, List<OpenAiCompatibleChatClient.ChatMessage>> histories =
            new ConcurrentHashMap<>();

    private static String key(String roomId, String botNickname, long handSeed) {
        return Objects.requireNonNull(roomId, "roomId") + '|' + Objects.requireNonNull(botNickname, "botNickname") + '|'
                + handSeed;
    }

    /**
     * 不含 system（由客户端单独传入），对话历史列表。
     */
    public List<OpenAiCompatibleChatClient.ChatMessage> copyHistory(String roomId, String botNickname, long handSeed) {
        List<OpenAiCompatibleChatClient.ChatMessage> list = histories.get(key(roomId, botNickname, handSeed));
        if (list == null || list.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(list);
    }

    /** 仅在模型输出被采纳为有效 {@link com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction} 之后调用。 */
    public void recordSuccessfulTurn(String roomId, String botNickname, long handSeed, String userSnapshot,
            String assistantJsonOrText) {
        if (roomId == null || botNickname == null || userSnapshot == null || assistantJsonOrText == null) {
            return;
        }
        pruneExcessEntries();
        List<OpenAiCompatibleChatClient.ChatMessage> list =
                histories.computeIfAbsent(key(roomId, botNickname, handSeed), k -> new ArrayList<>(16));
        synchronized (list) {
            list.add(new OpenAiCompatibleChatClient.ChatMessage("user", userSnapshot));
            list.add(new OpenAiCompatibleChatClient.ChatMessage("assistant", assistantJsonOrText.strip()));
        }
    }

    /** 本手结束后清理（与 {@link DpNpcStreetActionLog#clearHand} 对称）。 */
    public void clearHand(DpRoomBO room) {
        if (room == null) {
            return;
        }
        clearHandForRoom(room.getRoomId(), room.getCurrentHandSeed());
    }

    public void clearHandForRoom(String roomId, long handSeed) {
        if (roomId == null) {
            return;
        }
        String prefix = roomId + '|';
        String suffix = "|" + handSeed;
        histories.keySet().removeIf(k -> k.startsWith(prefix) && k.endsWith(suffix));
    }

    public void removeRoom(String roomId) {
        if (roomId == null) {
            return;
        }
        String prefix = roomId + '|';
        histories.keySet().removeIf(k -> k.startsWith(prefix));
    }

    private void pruneExcessEntries() {
        if (histories.size() <= MAX_TOTAL_KEYS_SOFT) {
            return;
        }
        Iterator<String> it = histories.keySet().iterator();
        int drop = histories.size() - MAX_TOTAL_KEYS_SOFT + 128;
        while (drop > 0 && it.hasNext()) {
            histories.remove(it.next());
            drop--;
        }
    }
}
