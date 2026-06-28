package com.example.mgdemoplus.lobby.impl;

import com.example.mgdemoplus.lobby.mapper.DpRoomLobbyMapper;
import com.example.mgdemoplus.lobby.mapper.DpRoomLobbyMpMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DpRoomHallServiceImplTest {

    private DpRoomLobbyMapper lobbyMapper;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private DpRoomHallServiceImpl hallService;

    @BeforeEach
    void setUp() {
        lobbyMapper = mock(DpRoomLobbyMapper.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        hallService = new DpRoomHallServiceImpl(
                lobbyMapper,
                mock(DpRoomLobbyMpMapper.class),
                redis,
                new ObjectMapper(),
                120L);
    }

    @Test
    void deleteRoomSummary_stillClearsCacheWhenRevisionIncrementFails() {
        when(lobbyMapper.deleteRoomSummaryByRoomId("ghost-room")).thenReturn(1);
        doThrow(new RuntimeException("redis down")).when(valueOps).increment(
                eq("mgdemo:cache:dpRoom:publicRooms:rev"), eq(1L));
        when(redis.<List<String>>execute(any(RedisCallback.class)))
                .thenReturn(List.of("mgdemo:cache:dpRoom:publicRooms:data:1:1:10"));
        when(redis.delete(anyList())).thenReturn(1L);

        hallService.deleteRoomSummary("ghost-room");

        verify(lobbyMapper).deleteRoomSummaryByRoomId("ghost-room");
        verify(redis).delete(anyList());
    }

    @Test
    void reconcileLobbyWithRuntimeRoomIds_softDeletesGhostAndClearsCache() {
        when(lobbyMapper.selectActiveLobbyRoomIds()).thenReturn(List.of("ghost-room", "live-room"));
        when(redis.<List<String>>execute(any(RedisCallback.class))).thenReturn(Collections.emptyList());
        when(valueOps.increment(anyString(), anyLong())).thenReturn(2L);
        when(redis.delete(anyList())).thenReturn(0L);

        hallService.reconcileLobbyWithRuntimeRoomIds(java.util.Set.of("live-room"));

        verify(lobbyMapper).deleteRoomSummaryByRoomId("ghost-room");
        verify(valueOps).increment(eq("mgdemo:cache:dpRoom:publicRooms:rev"), eq(1L));
    }
}
