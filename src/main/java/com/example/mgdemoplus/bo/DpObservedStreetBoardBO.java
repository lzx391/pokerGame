package com.example.mgdemoplus.bo;

import java.util.List;

/**
 * 某一街的公共牌快照。
 */
public final class DpObservedStreetBoardBO {
    public final String stage;
    public final List<String> communityCards;

    public DpObservedStreetBoardBO(String stage, List<String> communityCards) {
        this.stage = stage == null ? "" : stage;
        this.communityCards = communityCards == null
                ? List.of()
                : List.copyOf(communityCards);
    }
}
