package com.example.mgdemoplus.npc.tabletalk;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcLinePoolTest {

    @Test
    void linesForActionKey_doesNotFallBackToGeneric() {
        NpcLinePool pool = new NpcLinePool(
                true,
                0.2,
                0.4,
                Map.of(
                        "FOLD", List.of("fold line"),
                        "GENERIC", List.of("generic only")));
        assertEquals(List.of("fold line"), pool.linesForActionKey("FOLD"));
        assertTrue(pool.linesForActionKey("RAISE").isEmpty());
    }

    @Test
    void linesForPoolKey_readsSettleBuckets() {
        NpcLinePool pool = new NpcLinePool(
                true,
                0.2,
                0.5,
                Map.of(
                        "SETTLE_WIN", List.of("win"),
                        "SETTLE_LOSE", List.of("lose")));
        assertEquals(List.of("win"), pool.linesForPoolKey("SETTLE_WIN"));
        assertEquals(List.of("lose"), pool.linesForPoolKey("SETTLE_LOSE"));
    }
}
