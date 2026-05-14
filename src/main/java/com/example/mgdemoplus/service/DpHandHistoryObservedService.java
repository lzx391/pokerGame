package com.example.mgdemoplus.service;

import com.example.mgdemoplus.bo.DpObservedHandRecordBO;
import com.example.mgdemoplus.bo.DpRoomBO;
import com.example.mgdemoplus.entity.DpPlayer;

public interface DpHandHistoryObservedService {

    void beginHand(DpRoomBO room);

    void markHandReadyAfterBlinds(DpRoomBO room);

    void recordBoardState(DpRoomBO room);
/**
 * 记录盲注
 * @param room 房间
 * @param nickname 玩家昵称
 * @param isSb 是否是小盲
 * @param amount 盲注金额
 * @param potBefore 盲注前的底池金额
 */
    void recordBlind(DpRoomBO room, String nickname, boolean isSb, int amount, int potBefore);

    void recordFold(DpRoomBO room, DpPlayer actor, int potBefore);
/**
 * 记录行动
 * @param room 房间
 * @param actor 玩家
 * @param amount 行动金额
 * @param betToCallBefore 行动前的跟注金额
 * @param actorBetBefore 行动前的玩家金额
 * @param potBefore 行动前的底池金额
 * @param becameAllIn 是否全押
 * @param isRaise 是否加注
 */
    void recordBetLikeAction(DpRoomBO room, DpPlayer actor, int amount, int betToCallBefore, int actorBetBefore, int potBefore, boolean becameAllIn, boolean isRaise);

    void capturePotsBeforeClear(DpRoomBO room);

    DpObservedHandRecordBO finalizeHand(DpRoomBO room);

    void clearHand(DpRoomBO room);
}
