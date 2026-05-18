package com.example.mgdemoplus.room;

import java.util.ArrayList;
import java.util.List;

/** Batch kick result for {@link DpRoomService#kickPlayersBatch}. */
public final class KickPlayersBatchResult {
    public int attempted;
    public int successCount;
    public int failCount;
    public final List<String> failedNicknames = new ArrayList<>();

    public int getAttempted() {
        return attempted;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public List<String> getFailedNicknames() {
        return failedNicknames;
    }
}
