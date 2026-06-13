package com.example.mgdemoplus.npc.trace;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.room.DpRoomService;
import com.example.mgdemoplus.room.support.DpExperimentalDeckPresetPasswordGuard;
import com.example.mgdemoplus.utils.ResultUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DpNpcDecisionTraceQueryServiceImplTest {

    @Mock
    private DpRoomService roomService;
    @Mock
    private DpExperimentalDeckPresetPasswordGuard experimentalDeckPresetPasswordGuard;

    @Test
    void listHands_skipsExperimentalPasswordWhenFlagEnabled() {
        DpNpcDecisionTraceQueryServiceImpl svc = new DpNpcDecisionTraceQueryServiceImpl(
                roomService, experimentalDeckPresetPasswordGuard, true);
        when(roomService.getAllRooms("room-1")).thenReturn(new DpRoomBO());
        when(roomService.isRoomOwnerNickname("room-1", "owner")).thenReturn(true);

        ResultUtil result = svc.listHands("room-1", "owner", null);

        assertTrue(result.getSuccess());
        verify(experimentalDeckPresetPasswordGuard, never()).gate(anyString());
    }

    @Test
    void listHands_requiresExperimentalPasswordWhenFlagDisabled() {
        DpNpcDecisionTraceQueryServiceImpl svc = new DpNpcDecisionTraceQueryServiceImpl(
                roomService, experimentalDeckPresetPasswordGuard, false);
        when(roomService.getAllRooms("room-1")).thenReturn(new DpRoomBO());
        when(roomService.isRoomOwnerNickname("room-1", "owner")).thenReturn(true);
        when(experimentalDeckPresetPasswordGuard.gate(null))
                .thenReturn(ResultUtil.error().data("message", "请先验证实验排牌访问密码"));

        ResultUtil result = svc.listHands("room-1", "owner", null);

        assertFalse(result.getSuccess());
        verify(experimentalDeckPresetPasswordGuard).gate(null);
    }

    @Test
    void listHands_stillRequiresOwnerWhenPasswordSkipped() {
        DpNpcDecisionTraceQueryServiceImpl svc = new DpNpcDecisionTraceQueryServiceImpl(
                roomService, experimentalDeckPresetPasswordGuard, true);
        when(roomService.getAllRooms("room-1")).thenReturn(new DpRoomBO());
        when(roomService.isRoomOwnerNickname("room-1", "guest")).thenReturn(false);

        ResultUtil result = svc.listHands("room-1", "guest", null);

        assertFalse(result.getSuccess());
        verify(experimentalDeckPresetPasswordGuard, never()).gate(anyString());
    }
}
