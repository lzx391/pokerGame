package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;

/** Shared human/bot counting helpers for room runtime and eviction. */
public final class DpRoomHumanCounts {

    private DpRoomHumanCounts() {}

    public static int liveHumanTableCount(DpRoomBO room) {
        if (room == null || room.getPlayers() == null) {
            return 0;
        }
        int n = 0;
        for (DpPlayer p : room.getPlayers()) {
            if (p == null || p.isLeftThisHand() || DpNpcEngine.isBotPlayer(p)) {
                continue;
            }
            n++;
        }
        return n;
    }
}
