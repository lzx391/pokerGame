package com.example.mgdemoplus.npc.trace;

import com.example.mgdemoplus.utils.ResultUtil;

/** NPC TAG 决策 trace REST 查询。 */
public interface DpNpcDecisionTraceQueryService {

    ResultUtil listHands(String roomId, String requesterNickname, String experimentalPassword);

    ResultUtil getHand(String roomId, long handSeed, String requesterNickname, String experimentalPassword);

    ResultUtil getAction(String roomId, long handSeed, String actionId, String requesterNickname, String experimentalPassword);
}
