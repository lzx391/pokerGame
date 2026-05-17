package com.example.mgdemoplus.quickmatch;

import com.example.mgdemoplus.bo.DpRoomBO;
import com.example.mgdemoplus.entity.DpPlayer;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归：同缺额多房间不得互相覆盖；桶内字典序稳定。
 */
class JoinableQuickMatchRoomIndexTest {

    private static DpRoomBO publicRoomAlmostFull(String roomId, int maxSeats, int liveHumans, long now) {
        DpRoomBO r = new DpRoomBO();
        r.setRoomId(roomId);
        r.setRoomPassword("");
        r.setMaxSeatCount(maxSeats);
        for (int i = 0; i < liveHumans; i++) {
            DpPlayer p = new DpPlayer();
            p.setNickname("p" + i + "_" + roomId);
            p.setLastHeartBeat(now);
            p.setLeftThisHand(false);
            r.getPlayers().add(p);
        }
        return r;
    }

    @Test
    void threeRoomsWithSameShortageRetainAllInIndex() {
        long now = System.currentTimeMillis();
        JoinableQuickMatchRoomIndex idx = new JoinableQuickMatchRoomIndex();
        idx.addOrRefresh("z1", publicRoomAlmostFull("z1", 4, 3, now), now);
        idx.addOrRefresh("m2", publicRoomAlmostFull("m2", 4, 3, now), now);
        idx.addOrRefresh("a3", publicRoomAlmostFull("a3", 4, 3, now), now);

        assertEquals(3, idx.indexedRoomCount());
        List<String> bucket = idx.roomIdsInBucket(1);
        assertEquals(3, bucket.size());
        assertEquals(List.of("a3", "m2", "z1"), bucket);
    }

    @Test
    void rebuildAllPreservesMultiValuedBuckets() {
        long now = System.currentTimeMillis();
        Map<String, DpRoomBO> roomMap = new HashMap<>();
        roomMap.put("r2", publicRoomAlmostFull("r2", 5, 4, now));
        roomMap.put("r1", publicRoomAlmostFull("r1", 5, 4, now));

        JoinableQuickMatchRoomIndex idx = new JoinableQuickMatchRoomIndex();
        idx.rebuildAll(roomMap, now);
        assertEquals(2, idx.indexedRoomCount());
        assertEquals(2, idx.roomIdsInBucket(1).size());
    }

    @Test
    void pollPrefersSmallestShortageFirst() {
        long now = System.currentTimeMillis();
        JoinableQuickMatchRoomIndex idx = new JoinableQuickMatchRoomIndex();
        idx.addOrRefresh("b", publicRoomAlmostFull("b", 5, 3, now), now);
        idx.addOrRefresh("a", publicRoomAlmostFull("a", 6, 4, now), now);
        idx.addOrRefresh("c", publicRoomAlmostFull("c", 5, 4, now), now);

        Map<String, DpRoomBO> m = new HashMap<>();
        m.put("a", publicRoomAlmostFull("a", 6, 4, now));
        m.put("b", publicRoomAlmostFull("b", 5, 3, now));
        m.put("c", publicRoomAlmostFull("c", 5, 4, now));

        Optional<String> first = idx.pollBestCandidate(m::get, r -> true);
        assertTrue(first.isPresent());
        assertEquals("c", first.get());
    }

    @Test
    void pollWithinSameBucketUsesLexicographicRoomId() {
        long now = System.currentTimeMillis();
        JoinableQuickMatchRoomIndex idx = new JoinableQuickMatchRoomIndex();
        idx.addOrRefresh("b", publicRoomAlmostFull("b", 5, 3, now), now);
        idx.addOrRefresh("a", publicRoomAlmostFull("a", 6, 4, now), now);

        Map<String, DpRoomBO> m = new HashMap<>();
        m.put("a", publicRoomAlmostFull("a", 6, 4, now));
        m.put("b", publicRoomAlmostFull("b", 5, 3, now));

        Optional<String> first = idx.pollBestCandidate(m::get, r -> true);
        assertTrue(first.isPresent());
        assertEquals("a", first.get());
    }
}
