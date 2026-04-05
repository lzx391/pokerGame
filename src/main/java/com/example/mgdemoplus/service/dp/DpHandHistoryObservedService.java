package com.example.mgdemoplus.service.dp;

import com.example.mgdemoplus.dto.DpObservedHandRecordDTO;
import com.example.mgdemoplus.entity.dp.DpPlayer;
import com.example.mgdemoplus.entity.dp.DpRoom;

public interface DpHandHistoryObservedService {

    void beginHand(DpRoom room);

    void markHandReadyAfterBlinds(DpRoom room);

    void recordBoardState(DpRoom room);

    void recordBlind(DpRoom room, String nickname, boolean isSb, int amount, int potBefore);

    void recordFold(DpRoom room, DpPlayer actor, int potBefore);

    void recordBetLikeAction(DpRoom room, DpPlayer actor, int amount, int betToCallBefore, int actorBetBefore, int potBefore, boolean becameAllIn, boolean isRaise);

    void capturePotsBeforeClear(DpRoom room);

    DpObservedHandRecordDTO finalizeHand(DpRoom room);

    void clearHand(DpRoom room);
}
