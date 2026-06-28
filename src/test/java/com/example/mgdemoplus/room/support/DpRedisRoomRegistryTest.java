package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.quickmatch.JoinableQuickMatchRoomIndex;
import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.SetOperations;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DpRedisRoomRegistryTest {

    private StringRedisTemplate redis;
    private DpRoomRedisCodec codec;
    private DpRoomDistributedLock lock;
    private DpRoomEventPublisher publisher;
    private JoinableQuickMatchRoomIndex joinableIndex;
    private DpRedisRoomRegistry registry;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        codec = new DpRoomRedisCodec();
        lock = mock(DpRoomDistributedLock.class);
        publisher = mock(DpRoomEventPublisher.class);
        joinableIndex = mock(JoinableQuickMatchRoomIndex.class);
        registry = new DpRedisRoomRegistry(redis, codec, lock, publisher, joinableIndex);

        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        SetOperations<String, String> setOps = mock(SetOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForSet()).thenReturn(setOps);
        when(valueOps.increment(anyString())).thenReturn(1L);
    }

    @Test
    void codecRoundTripPreservesDeckAndStorageVersion() throws Exception {
        DpRoomBO original = new DpRoomBO();
        original.setRoomId("room-1");
        original.setOwner("alice");
        original.setStorageVersion(3L);
        original.getDeck().add("hearts_A");
        original.getCarryInChips().put("alice", 500);
        original.getPlayers().add(new DpPlayer());

        String json = codec.toJson(original);
        DpRoomBO restored = codec.fromJson(json);

        assertNotNull(restored);
        assertEquals("room-1", restored.getRoomId());
        assertEquals(3L, restored.getStorageVersion());
        assertEquals(1, restored.getDeck().size());
        assertEquals(500, restored.getCarryInChips().get("alice"));
    }

    @Test
    void putIndexesRoomAndPublishes() throws Exception {
        ValueOperations<String, String> valueOps = redis.opsForValue();
        SetOperations<String, String> setOps = redis.opsForSet();

        DpRoomBO room = new DpRoomBO();
        room.setRoomId("r99");
        room.setOwner("bob");

        registry.put("r99", room);

        verify(valueOps).set(eq("dp:room:state:r99"), anyString());
        verify(setOps).add("dp:room:index", "r99");
        verify(joinableIndex).addOrRefresh(eq("r99"), any(DpRoomBO.class), any(Long.class));
        verify(publisher).publish(eq("r99"), eq(1L), anyString());
    }

    @Test
    void runExclusiveAcquiresLockAndSaves() {
        when(lock.tryAcquire("r1")).thenReturn("token-1");
        DpRoomBO room = new DpRoomBO();
        room.setRoomId("r1");
        ValueOperations<String, String> valueOps = redis.opsForValue();
        when(valueOps.get("dp:room:state:r1")).thenReturn("{\"roomId\":\"r1\",\"owner\":\"x\"}");

        Boolean ok = registry.runExclusive("r1", r -> {
            r.setOwner("y");
            return true;
        });

        assertTrue(ok);
        verify(lock).release("r1", "token-1");
    }

    @Test
    void runExclusiveIsReentrantOnSameThread() {
        when(lock.tryAcquire("r1")).thenReturn("token-1");
        ValueOperations<String, String> valueOps = redis.opsForValue();
        when(valueOps.get("dp:room:state:r1")).thenReturn("{\"roomId\":\"r1\",\"owner\":\"a\"}");
        when(redis.hasKey("dp:room:state:r1")).thenReturn(true);

        Boolean ok = registry.runExclusive("r1", outer -> registry.runExclusive("r1", inner -> {
            inner.setOwner("b");
            return true;
        }));

        assertTrue(ok);
        verify(lock).tryAcquire("r1");
        verify(lock).release("r1", "token-1");
    }

    /**
     * Heartbeat tick holds outer {@link #runExclusive}; nested rebuy must mutate the same object
     * or outer save overwrites Redis with stale chips (NPC rebuy regression).
     */
    @Test
    void nestedRunExclusiveMutationsSurviveOuterSave() throws Exception {
        Map<String, String> store = new HashMap<>();
        DpRoomBO seed = settledRoomWithBustedBot();
        store.put("dp:room:state:r1", codec.toJson(seed));

        when(lock.tryAcquire("r1")).thenReturn("token-1");
        when(redis.hasKey("dp:room:state:r1")).thenAnswer(inv -> store.containsKey("dp:room:state:r1"));
        ValueOperations<String, String> valueOps = redis.opsForValue();
        when(valueOps.get("dp:room:state:r1")).thenAnswer(inv -> store.get("dp:room:state:r1"));
        doAnswer(inv -> {
            store.put("dp:room:state:r1", inv.getArgument(1));
            return null;
        }).when(valueOps).set(eq("dp:room:state:r1"), anyString());
        when(valueOps.increment(anyString())).thenReturn(1L);

        registry.runExclusive("r1", outer -> {
            // Simulate heartbeat → tickSettledBotsAutoReady → callbacks.rebuy
            registry.runExclusive("r1", inner -> {
                for (DpPlayer p : inner.getPlayers()) {
                    if ("BOT_FISH_1".equals(p.getNickname())) {
                        p.setChips(inner.getStartingChips());
                    }
                }
                return true;
            });
            return null;
        });

        DpRoomBO finalState = codec.fromJson(store.get("dp:room:state:r1"));
        assertNotNull(finalState);
        DpPlayer bot = finalState.getPlayers().stream()
                .filter(p -> "BOT_FISH_1".equals(p.getNickname()))
                .findFirst()
                .orElseThrow();
        assertEquals(finalState.getStartingChips(), bot.getChips(), "nested rebuy must persist after outer save");
    }

    private static DpRoomBO settledRoomWithBustedBot() {
        DpRoomBO r = new DpRoomBO();
        r.setRoomId("r1");
        r.setPlaying(true);
        r.setCurrentStage("settled");
        r.setBigBlindChips(10);
        r.setStartingChips(500);
        r.setSettledAtMs(System.currentTimeMillis());
        DpPlayer bot = new DpPlayer();
        bot.setNickname("BOT_FISH_1");
        bot.setChips(3);
        r.getPlayers().add(bot);
        return r;
    }

    @Test
    void pruneOrphanIndexEntriesRemovesIdsWithoutStateKey() {
        SetOperations<String, String> setOps = redis.opsForSet();
        when(setOps.members("dp:room:index")).thenReturn(Set.of("gone", "live"));
        when(redis.hasKey("dp:room:state:gone")).thenReturn(false);
        when(redis.hasKey("dp:room:state:live")).thenReturn(true);

        registry.pruneOrphanIndexEntries();

        verify(setOps).remove("dp:room:index", "gone");
    }
}
