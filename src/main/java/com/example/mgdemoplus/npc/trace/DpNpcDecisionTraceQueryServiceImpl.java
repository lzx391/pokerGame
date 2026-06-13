package com.example.mgdemoplus.npc.trace;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.npc.trace.model.DpNpcActionTrace;
import com.example.mgdemoplus.npc.trace.model.DpNpcHandTraceBundle;
import com.example.mgdemoplus.room.DpRoomService;
import com.example.mgdemoplus.room.support.DpExperimentalDeckPresetPasswordGuard;
import com.example.mgdemoplus.utils.ResultUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DpNpcDecisionTraceQueryServiceImpl implements DpNpcDecisionTraceQueryService {

    private final DpRoomService roomService;
    private final DpExperimentalDeckPresetPasswordGuard experimentalDeckPresetPasswordGuard;
    private final boolean skipExperimentalPassword;

    public DpNpcDecisionTraceQueryServiceImpl(
            DpRoomService roomService,
            DpExperimentalDeckPresetPasswordGuard experimentalDeckPresetPasswordGuard,
            @Value("${mgdemoplus.npc-decision-trace.skip-password:false}") boolean skipExperimentalPassword) {
        this.roomService = roomService;
        this.experimentalDeckPresetPasswordGuard = experimentalDeckPresetPasswordGuard;
        this.skipExperimentalPassword = skipExperimentalPassword;
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
        if (!roomService.isRoomOwnerNickname(roomId, requesterNickname)) {
            return ResultUtil.error().data("message", "仅房主可访问决策分析");
        }
        if (!skipExperimentalPassword) {
            ResultUtil gate = experimentalDeckPresetPasswordGuard.gate(experimentalPassword);
            if (gate != null) {
                return gate;
            }
        }
        return null;
    }
}
