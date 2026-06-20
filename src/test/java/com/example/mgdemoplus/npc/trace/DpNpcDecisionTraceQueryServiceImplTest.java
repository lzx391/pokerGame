package com.example.mgdemoplus.npc.trace;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.rbac.DpPermissionService;
import com.example.mgdemoplus.rbac.support.DpPermissionCodes;
import com.example.mgdemoplus.room.DpRoomService;
import com.example.mgdemoplus.utils.ResultUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DpNpcDecisionTraceQueryServiceImplTest {

    @Mock
    private DpRoomService roomService;
    @Mock
    private DpPermissionService permissionService;

    @Test
    void listHands_allowsWhenNpcDecisionTracePermissionGranted() {
        DpNpcDecisionTraceQueryServiceImpl svc = new DpNpcDecisionTraceQueryServiceImpl(
                roomService, permissionService);
        when(roomService.getAllRooms("room-1")).thenReturn(new DpRoomBO());
        when(permissionService.hasPermi(eq("viewer"), eq(DpPermissionCodes.GAME_NPC_DECISION_TRACE)))
                .thenReturn(true);

        ResultUtil result = svc.listHands("room-1", "viewer", null);

        assertTrue(result.getSuccess());
    }

    @Test
    void listHands_deniesWithoutNpcDecisionTracePermission() {
        DpNpcDecisionTraceQueryServiceImpl svc = new DpNpcDecisionTraceQueryServiceImpl(
                roomService, permissionService);
        when(roomService.getAllRooms("room-1")).thenReturn(new DpRoomBO());
        when(permissionService.hasPermi(eq("owner"), eq(DpPermissionCodes.GAME_NPC_DECISION_TRACE)))
                .thenReturn(false);

        ResultUtil result = svc.listHands("room-1", "owner", "secret");

        assertFalse(result.getSuccess());
        assertThat(result.getData().get("message")).isEqualTo("无决策追踪权限");
    }

    @Test
    void listHands_deniesGuestWithoutPermission_evenIfDeckPresetGranted() {
        DpNpcDecisionTraceQueryServiceImpl svc = new DpNpcDecisionTraceQueryServiceImpl(
                roomService, permissionService);
        when(roomService.getAllRooms("room-1")).thenReturn(new DpRoomBO());
        when(permissionService.hasPermi(eq("guest"), eq(DpPermissionCodes.GAME_NPC_DECISION_TRACE)))
                .thenReturn(false);

        ResultUtil result = svc.listHands("room-1", "guest", null);

        assertFalse(result.getSuccess());
        assertThat(result.getData().get("message")).isEqualTo("无决策追踪权限");
    }
}
