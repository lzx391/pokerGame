package com.example.mgdemoplus.quickmatch;

import com.example.mgdemoplus.quickmatch.pairing.DpQuickMatchWaitEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Redis-backed FIFO quick-match wait queue. All mutating methods assume caller holds
 * {@link DpQuickMatchPairingLock} (same contract as legacy {@code defaultQmLock}).
 */
@Component
public class DpQuickMatchWaitQueue {

    private static final Logger log = LoggerFactory.getLogger(DpQuickMatchWaitQueue.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public DpQuickMatchWaitQueue(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public int sizeWhileLocked() {
        Long len = stringRedisTemplate.opsForList().size(QuickMatchRedisKeys.WAIT_QUEUE);
        return len != null ? len.intValue() : 0;
    }

    public List<DpQuickMatchWaitEntry> snapshotWhileLocked() {
        return readAll();
    }

    public void enqueueTailIfAbsentWhileLocked(DpQuickMatchWaitEntry entry) {
        if (entry == null || entry.nickname() == null || entry.nickname().isBlank()) {
            return;
        }
        List<DpQuickMatchWaitEntry> all = readAll();
        for (DpQuickMatchWaitEntry e : all) {
            if (entry.nickname().equals(e.nickname())) {
                return;
            }
        }
        all.add(entry);
        writeAll(all);
    }

    public boolean removeByNicknameWhileLocked(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return false;
        }
        List<DpQuickMatchWaitEntry> all = readAll();
        boolean removed = all.removeIf(e -> nickname.equals(e.nickname()));
        if (removed) {
            writeAll(all);
        }
        return removed;
    }

    public int queuePositionWhileLocked(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return 0;
        }
        List<DpQuickMatchWaitEntry> all = readAll();
        for (int i = 0; i < all.size(); i++) {
            if (nickname.equals(all.get(i).nickname())) {
                return i + 1;
            }
        }
        return 0;
    }

    public List<DpQuickMatchWaitEntry> pollHeadWhileLocked(int maxTake) {
        if (maxTake <= 0) {
            return List.of();
        }
        List<DpQuickMatchWaitEntry> all = readAll();
        if (all.isEmpty()) {
            return List.of();
        }
        int n = Math.min(maxTake, all.size());
        List<DpQuickMatchWaitEntry> out = new ArrayList<>(all.subList(0, n));
        writeAll(new ArrayList<>(all.subList(n, all.size())));
        return out;
    }

    public void unshiftHeadWhileLocked(List<DpQuickMatchWaitEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        List<DpQuickMatchWaitEntry> all = readAll();
        List<DpQuickMatchWaitEntry> merged = new ArrayList<>(entries.size() + all.size());
        merged.addAll(entries);
        merged.addAll(all);
        writeAll(merged);
    }

    public void addTailWhileLocked(List<DpQuickMatchWaitEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        List<DpQuickMatchWaitEntry> all = readAll();
        all.addAll(entries);
        writeAll(all);
    }

    /**
     * Remove timed-out waiters and those already seated in a room.
     *
     * @return nicknames removed due to timeout (for idle push)
     */
    public List<String> pruneWhileLocked(long nowMs, long maxWaitMs, Predicate<String> alreadyInRoom) {
        List<DpQuickMatchWaitEntry> all = readAll();
        List<String> timedOut = new ArrayList<>();
        boolean changed = all.removeIf(e -> {
            boolean tooLong = nowMs - e.enqueuedMs() > maxWaitMs;
            boolean inRoom = alreadyInRoom != null && alreadyInRoom.test(e.nickname());
            if (tooLong) {
                timedOut.add(e.nickname());
            }
            return tooLong || inRoom;
        });
        if (changed) {
            writeAll(all);
        }
        return timedOut;
    }

    private List<DpQuickMatchWaitEntry> readAll() {
        List<String> raw = stringRedisTemplate.opsForList().range(QuickMatchRedisKeys.WAIT_QUEUE, 0, -1);
        if (raw == null || raw.isEmpty()) {
            return new ArrayList<>();
        }
        List<DpQuickMatchWaitEntry> out = new ArrayList<>(raw.size());
        for (String json : raw) {
            if (json == null || json.isBlank()) {
                continue;
            }
            try {
                out.add(objectMapper.readValue(json, DpQuickMatchWaitEntry.class));
            } catch (Exception e) {
                log.warn("skip corrupt qm wait entry: {}", e.toString());
            }
        }
        return out;
    }

    private void writeAll(List<DpQuickMatchWaitEntry> entries) {
        stringRedisTemplate.delete(QuickMatchRedisKeys.WAIT_QUEUE);
        if (entries == null || entries.isEmpty()) {
            return;
        }
        List<String> serialized = new ArrayList<>(entries.size());
        for (DpQuickMatchWaitEntry e : entries) {
            try {
                serialized.add(objectMapper.writeValueAsString(e));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("serialize qm wait entry failed nick=" + e.nickname(), ex);
            }
        }
        stringRedisTemplate.opsForList().rightPushAll(QuickMatchRedisKeys.WAIT_QUEUE, serialized);
    }
}
