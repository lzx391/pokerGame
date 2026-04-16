package com.example.mgdemoplus.service.dp;

import com.example.mgdemoplus.dto.DpObservedHandRecordDTO;
import com.example.mgdemoplus.entity.dp.DpPlayer;
import com.example.mgdemoplus.entity.dp.DpRoom;

public interface DpHandHistoryObservedService {

    void beginHand(DpRoom room);

    void markHandReadyAfterBlinds(DpRoom room);

    void recordBoardState(DpRoom room);
/**
 * 记录盲注
 * @param room 房间
 * @param nickname 玩家昵称
 * @param isSb 是否是小盲
 * @param amount 盲注金额
 * @param potBefore 盲注前的底池金额
 */
    void recordBlind(DpRoom room, String nickname, boolean isSb, int amount, int potBefore);

    void recordFold(DpRoom room, DpPlayer actor, int potBefore);
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
    void recordBetLikeAction(DpRoom room, DpPlayer actor, int amount, int betToCallBefore, int actorBetBefore, int potBefore, boolean becameAllIn, boolean isRaise);

    void capturePotsBeforeClear(DpRoom room);

    DpObservedHandRecordDTO finalizeHand(DpRoom room);

    void clearHand(DpRoom room);
}
