package com.example.mgdemoplus.service.serviceImpl;

import com.example.mgdemoplus.llm.OpenAiCompatibleChatClient;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BOT_LLM_GLOBAL 多轮对话：按同一牌局生命周期内 {@code roomId + botNick + handSeed} 存档 user/assistant 交替消息。
 * <p>
 * 策略：<ul>
 *   <li>新一手牌（{@code handSeed} 变化）天然使用新键，旧键可留在 map 直至 {@link #pruneExcessEntries()}。</li>
 *   <li>仅在一次决策被主线程<strong>采纳</strong>模型动作后写入一轮（user 快照正文 + assistant 原始 JSON 正文）。</li>
 *   <li>在途请求被票据作废或超时：不会追加，以免污染下一轮。</li>
 * </ul>
 */
@Component
public final class LlmNpcGlobalHandConversationStore {

    /** 最多保留的 user+assistant «轮» 数（每轮 2 条 message）。 */
    static final int MAX_TURN_PAIRS = 6;
    /** 粗略上限：避免长期运行 map 单向增长（例如机器人久留旁观者席）。 */
    static final int MAX_TOTAL_KEYS_SOFT = 1024;

    private final ConcurrentHashMap<String, Deque<OpenAiCompatibleChatClient.ChatMessage>> histories =
            new ConcurrentHashMap<>();
/**
 * 拼map的键
 * @param roomId
 * @param botNickname
 * @param handSeed
 * @return
 */
    private static String key(String roomId, String botNickname, long handSeed) {
        return Objects.requireNonNull(roomId, "roomId") + '|' + Objects.requireNonNull(botNickname, "botNickname") + '|'
                + handSeed;
    }

    /**
     * 不含 system（由客户端单独传入），对话历史队列。
     */
    public List<OpenAiCompatibleChatClient.ChatMessage> copyHistory(String roomId, String botNickname, long handSeed) {
        Deque<OpenAiCompatibleChatClient.ChatMessage> deque = histories.get(key(roomId, botNickname, handSeed));
        if (deque == null || deque.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(deque);
    }

    /** 仅在模型输出被采纳为有效 {@link com.example.mgdemoplus.service.serviceImpl.DpNpcEngine.BotAction} 之后调用。 */
    public void recordSuccessfulTurn(String roomId, String botNickname, long handSeed, String userSnapshot,
            String assistantJsonOrText) {
        if (roomId == null || botNickname == null || userSnapshot == null || assistantJsonOrText == null) {
            return;
        }
        pruneExcessEntries();
        Deque<OpenAiCompatibleChatClient.ChatMessage> deque =
                histories.computeIfAbsent(key(roomId, botNickname, handSeed), k -> new ArrayDeque<>(16));
        synchronized (deque) {
            deque.addLast(new OpenAiCompatibleChatClient.ChatMessage("user", userSnapshot));
            deque.addLast(new OpenAiCompatibleChatClient.ChatMessage("assistant", assistantJsonOrText.strip()));
            while (deque.size() > MAX_TURN_PAIRS * 2L) {
                deque.pollFirst();
                if (!deque.isEmpty()) {
                    deque.pollFirst();
                }
            }
        }
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
