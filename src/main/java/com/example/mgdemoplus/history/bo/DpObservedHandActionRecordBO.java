package com.example.mgdemoplus.history.bo;

import com.example.mgdemoplus.history.types.DpObservedHandActionType;

/**
 * 观测牌谱中单条行动（含弃牌、过牌、跟注、加注等）。
 */
public final class DpObservedHandActionRecordBO {
    public final long tsMs;
    public final String stage;
    public final String actorNickname;
    public final DpObservedHandActionType type;
    public final int amount;
    public final int betToCallBefore;
    public final int actorBetBefore;
    public final int raiseLevelAfter;
    public final int potBefore;

    public DpObservedHandActionRecordBO(long tsMs, String stage, String actorNickname, DpObservedHandActionType type,
                                         int amount, int betToCallBefore, int actorBetBefore,
                                         int raiseLevelAfter, int potBefore) {
        this.tsMs = tsMs;
        this.stage = stage == null ? "" : stage;
        this.actorNickname = actorNickname == null ? "" : actorNickname;
        this.type = type;
        this.amount = amount;
        this.betToCallBefore = betToCallBefore;
        this.actorBetBefore = actorBetBefore;
        this.raiseLevelAfter = raiseLevelAfter;
        this.potBefore = potBefore;
    }
}
