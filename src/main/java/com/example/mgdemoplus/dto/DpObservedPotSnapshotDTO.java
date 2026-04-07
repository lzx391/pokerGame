package com.example.mgdemoplus.dto;

import java.util.List;

/**
 * 结算前某一池（主池/边池）。
 */
public final class DpObservedPotSnapshotDTO {
    public final int amount;
    public final List<String> eligibleNicknames;

    public DpObservedPotSnapshotDTO(int amount, List<String> eligibleNicknames) {
        this.amount = amount;
        this.eligibleNicknames = eligibleNicknames == null
                ? List.of()
                : List.copyOf(eligibleNicknames);
    }
}
