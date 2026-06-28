package com.example.mgdemoplus.quickmatch;

import com.example.mgdemoplus.quickmatch.pairing.DpQuickMatchWaitEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DpQuickMatchWaitQueueTest {

    private StringRedisTemplate redis;
    private ListOperations<String, String> listOps;
    private DpQuickMatchWaitQueue queue;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        listOps = mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(listOps);
        queue = new DpQuickMatchWaitQueue(redis, new ObjectMapper());
    }

    @Test
    void enqueueTailIfAbsentWhileLocked_deduplicatesNickname() throws Exception {
        String existing = new ObjectMapper().writeValueAsString(new DpQuickMatchWaitEntry("alice", 1, 100L));
        when(listOps.range(QuickMatchRedisKeys.WAIT_QUEUE, 0, -1)).thenReturn(List.of(existing));

        queue.enqueueTailIfAbsentWhileLocked(new DpQuickMatchWaitEntry("alice", 1, 200L));

        verify(listOps).range(QuickMatchRedisKeys.WAIT_QUEUE, 0, -1);
    }

    @Test
    void pollHeadWhileLocked_returnsFifoOrder() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String a = mapper.writeValueAsString(new DpQuickMatchWaitEntry("a", 1, 1L));
        String b = mapper.writeValueAsString(new DpQuickMatchWaitEntry("b", 2, 2L));
        when(listOps.range(QuickMatchRedisKeys.WAIT_QUEUE, 0, -1)).thenReturn(List.of(a, b));

        List<DpQuickMatchWaitEntry> polled = queue.pollHeadWhileLocked(1);

        assertEquals(1, polled.size());
        assertEquals("a", polled.get(0).nickname());
        verify(listOps).rightPushAll(eq(QuickMatchRedisKeys.WAIT_QUEUE), anyList());
    }

    @Test
    void removeByNicknameWhileLocked() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String a = mapper.writeValueAsString(new DpQuickMatchWaitEntry("a", 1, 1L));
        String b = mapper.writeValueAsString(new DpQuickMatchWaitEntry("b", 2, 2L));
        when(listOps.range(QuickMatchRedisKeys.WAIT_QUEUE, 0, -1)).thenReturn(List.of(a, b));

        assertTrue(queue.removeByNicknameWhileLocked("a"));
        verify(redis).delete(QuickMatchRedisKeys.WAIT_QUEUE);
    }

    @Test
    void queuePositionWhileLocked_isOneBased() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String a = mapper.writeValueAsString(new DpQuickMatchWaitEntry("a", 1, 1L));
        String b = mapper.writeValueAsString(new DpQuickMatchWaitEntry("b", 2, 2L));
        when(listOps.range(QuickMatchRedisKeys.WAIT_QUEUE, 0, -1)).thenReturn(List.of(a, b));

        assertEquals(2, queue.queuePositionWhileLocked("b"));
        assertEquals(0, queue.queuePositionWhileLocked("missing"));
    }

    @Test
    void pruneWhileLocked_removesTimedOutOnly() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        long now = 10_000L;
        String fresh = mapper.writeValueAsString(new DpQuickMatchWaitEntry("fresh", 1, now - 1_000L));
        String stale = mapper.writeValueAsString(new DpQuickMatchWaitEntry("stale", 2, now - 999_999L));
        when(listOps.range(QuickMatchRedisKeys.WAIT_QUEUE, 0, -1)).thenReturn(List.of(fresh, stale));

        List<String> timedOut = queue.pruneWhileLocked(now, 60_000L, nick -> false);

        assertEquals(List.of("stale"), timedOut);
        assertFalse(timedOut.contains("fresh"));
    }
}
