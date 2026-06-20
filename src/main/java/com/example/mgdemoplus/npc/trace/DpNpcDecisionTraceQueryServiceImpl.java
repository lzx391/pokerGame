package com.example.mgdemoplus.npc.trace;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.npc.trace.model.DpNpcActionTrace;
import com.example.mgdemoplus.npc.trace.model.DpNpcHandTraceBundle;
import com.example.mgdemoplus.rbac.DpPermissionService;
import com.example.mgdemoplus.rbac.support.DpPermissionCodes;
import com.example.mgdemoplus.room.DpRoomService;
import com.example.mgdemoplus.utils.ResultUtil;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DpNpcDecisionTraceQueryServiceImpl implements DpNpcDecisionTraceQueryService {

    private final DpRoomService roomService;
    private final DpPermissionService permissionService;

    public DpNpcDecisionTraceQueryServiceImpl(
            DpRoomService roomService,
            DpPermissionService permissionService) {
        this.roomService = roomService;
        this.permissionService = permissionService;
    }

    @Override
    public ResultUtil listHands(String roomId, String requesterNickname, String experimentalPassword) {
        ResultUtil denied = authorize(roomId, requesterNickname, experimentalPassword);
        if (denied != null) {
            return denied;
        }
        DpRoomBO live = roomService.getAllRooms(roomId);
        List<DpNpcHandTraceBundle> hands = DpNpcTagDecisionTraceStore.listHandSummaries(roomId, live);
        return ResultUtil.ok().data("hands", hands);
    }

    @Override
    public ResultUtil getHand(String roomId, long handSeed, String requesterNickname, String experimentalPassword) {
        ResultUtil denied = authorize(roomId, requesterNickname, experimentalPassword);
        if (denied != null) {
            return denied;
        }
        DpRoomBO live = roomService.getAllRooms(roomId);
        DpNpcHandTraceBundle bundle = DpNpcTagDecisionTraceStore.getHandBundle(roomId, handSeed, live);
        if (bundle == null) {
            return ResultUtil.error().data("message", "未找到该手 trace");
        }
        return ResultUtil.ok().data("bundle", bundle);
    }

    @Override
    public ResultUtil getAction(String roomId, long handSeed, String actionId, String requesterNickname, String experimentalPassword) {
        ResultUtil denied = authorize(roomId, requesterNickname, experimentalPassword);
        if (denied != null) {
            return denied;
        }
        DpNpcActionTrace action = DpNpcTagDecisionTraceStore.getActionDetail(roomId, handSeed, actionId);
        if (action == null) {
            return ResultUtil.error().data("message", "未找到该 action trace");
        }
        return ResultUtil.ok().data("action", action);
    }

    private ResultUtil authorize(String roomId, String requesterNickname, String experimentalPassword) {
        if (roomService.getAllRooms(roomId) == null) {
            return ResultUtil.error().data("message", "房间不存在");
        }
        if (requesterNickname == null || requesterNickname.isBlank()
                || !permissionService.hasPermi(requesterNickname.trim(), DpPermissionCodes.GAME_NPC_DECISION_TRACE)) {
            return ResultUtil.error().data("message", "无决策追踪权限");
        }
        return null;
    }
}
