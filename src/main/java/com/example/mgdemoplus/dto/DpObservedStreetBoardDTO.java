package com.example.mgdemoplus.dto;

import java.util.List;

/**
 * 某一街的公共牌快照。
 */
public final class DpObservedStreetBoardDTO {
    public final String stage;
    public final List<String> communityCards;

    public DpObservedStreetBoardDTO(String stage, List<String> communityCards) {
        this.stage = stage == null ? "" : stage;
        this.communityCards = communityCards == null
                ? List.of()
                : List.copyOf(communityCards);
    }
}
