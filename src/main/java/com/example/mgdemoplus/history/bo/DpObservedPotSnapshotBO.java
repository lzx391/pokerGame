package com.example.mgdemoplus.history.bo;

import java.util.List;

/**
 * 结算前某一池（主池/边池）。
 */
public final class DpObservedPotSnapshotBO {
    public final int amount;
    public final List<String> eligibleNicknames;

    public DpObservedPotSnapshotBO(int amount, List<String> eligibleNicknames) {
        this.amount = amount;
        this.eligibleNicknames = eligibleNicknames == null
                ? List.of()
                : List.copyOf(eligibleNicknames);
    }
}
