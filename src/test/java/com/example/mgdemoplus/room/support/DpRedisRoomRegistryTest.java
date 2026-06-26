package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.SetOperations;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DpRedisRoomRegistryTest {

    private StringRedisTemplate redis;
    private DpRoomRedisCodec codec;
    private DpRoomDistributedLock lock;
    private DpRoomEventPublisher publisher;
    private DpRedisRoomRegistry registry;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        codec = new DpRoomRedisCodec();
        lock = mock(DpRoomDistributedLock.class);
        publisher = mock(DpRoomEventPublisher.class);
        registry = new DpRedisRoomRegistry(redis, codec, lock, publisher);

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
}
