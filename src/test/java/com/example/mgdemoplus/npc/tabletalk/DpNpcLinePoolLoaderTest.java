package com.example.mgdemoplus.npc.tabletalk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DpNpcLinePoolLoaderTest {

    @Test
    void loadsHappyCalmSad_asMoodPrefixedPools() {
        NpcLinePool pool = new DpNpcLinePoolLoader().loadByResourceKey("tag").orElseThrow();
        List<String> highFold = pool.linesForPoolKey("MOOD_HIGH_FOLD");
        List<String> neutralFold = pool.linesForPoolKey("MOOD_NEUTRAL_FOLD");
        List<String> lowFold = pool.linesForPoolKey("MOOD_LOW_FOLD");
        assertFalse(highFold.isEmpty());
        assertFalse(neutralFold.isEmpty());
        assertFalse(lowFold.isEmpty());
        assertFalse(highFold.equals(neutralFold));
        assertEquals(List.of(), pool.linesForPoolKey("FOLD"));
    }
}
