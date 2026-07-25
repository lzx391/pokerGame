package com.example.mgdemoplus.npc.trace;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.room.DpRoomService;
import com.example.mgdemoplus.utils.ResultUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DpNpcDecisionTraceQueryServiceImplTest {

    @Mock
    private DpRoomService roomService;

    @Test
    void listHands_returnsHandsWhenRoomExists() {
        DpNpcDecisionTraceQueryServiceImpl svc = new DpNpcDecisionTraceQueryServiceImpl(roomService);
        when(roomService.getAllRooms("room-1")).thenReturn(new DpRoomBO());

        ResultUtil result = svc.listHands("room-1", "viewer", null);

        assertTrue(result.getSuccess());
    }

    @Test
    void listHands_deniesWhenRoomMissing() {
        DpNpcDecisionTraceQueryServiceImpl svc = new DpNpcDecisionTraceQueryServiceImpl(roomService);
        when(roomService.getAllRooms("room-1")).thenReturn(null);

        ResultUtil result = svc.listHands("room-1", "viewer", null);

        assertFalse(result.getSuccess());
        assertThat(result.getData().get("message")).isEqualTo("房间不存在");
    }
}
