package com.example.mgdemoplus.npc.strategypro.l4;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotType;
import com.example.mgdemoplus.npc.eval.DpNpcMadeHandCategory;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DpNpcL4RaiseEscalationTest {

    @Test
    void thirdReRaiseIncrementExceedsFirst() {
        int pot = 400;
        int bb = 10;
        int lastInc = 30;
        int extraBb = 3;

        int inc1 = DpNpcRaiseEscalation.incrementForLevel(1, pot, bb, lastInc, extraBb);
        int inc3 = DpNpcRaiseEscalation.incrementForLevel(3, pot, bb, lastInc, extraBb);

        assertTrue(inc3 > inc1,
                "raiseLevel=3 increment (" + inc3 + ") should exceed level=1 (" + inc1 + ")");
    }

    @Test
    void antiRepeatBumpWhenIncrementTooCloseToLast() {
        DpRoomBO room = new DpRoomBO();
        room.setBigBlindChips(10);
        room.setSmallBlindChips(5);
        room.setPot(500);
        room.setRaiseLevel(2);
        room.setLastRaiseIncrement(40);

        Random random = new Random(42L);
        DpNpcRaiseEscalation.EscalationResult result = DpNpcRaiseEscalation.computeFacingBetRaise(
                room,
                100,
                2000,
                DpNpcMadeHandCategory.TOP_PAIR_TOP_KICKER,
                "turn",
                BotType.TAG,
                2,
                3,
                random);

        int increment = result.raiseAmount - 100;
        assertTrue(increment >= 40,
                "anti-repeat should bump increment above lastRaiseIncrement, got " + increment);
    }

    @Test
    void maniacReRaiseJamProbPositiveAtLevelTwo() {
        double prob = DpNpcRaiseEscalation.maniacReRaiseJamProb(2, DpNpcMadeHandCategory.HIGH_CARD);
        assertTrue(prob >= 0.18, "MANIAC raiseLevel>=2 should have jam path, got " + prob);
    }

    @Test
    void maniacHighReRaiseCanSuggestJam() {
        DpRoomBO room = new DpRoomBO();
        room.setBigBlindChips(10);
        room.setSmallBlindChips(5);
        room.setPot(600);
        room.setRaiseLevel(3);
        room.setLastRaiseIncrement(50);

        int jamHints = 0;
        for (int i = 0; i < 100; i++) {
            DpNpcRaiseEscalation.EscalationResult result = DpNpcRaiseEscalation.computeFacingBetRaise(
                    room,
                    120,
                    3000,
                    DpNpcMadeHandCategory.TRIPS,
                    "turn",
                    BotType.MANIAC,
                    3,
                    5,
                    new Random(1000L + i));
            if (result.suggestJam) {
                jamHints++;
            }
        }
        assertTrue(jamHints >= 20,
                "MANIAC TRIPS+ at raiseLevel=3 should often suggest jam, got " + jamHints + "/100");
    }

    @Test
    void deepStackTripsCommitFactorBoosted() {
        double base = 0.72;
        double adjusted = DpNpcRaiseEscalation.adjustDeepStackCommitFactor(
                base, DpNpcMadeHandCategory.TRIPS, 85.0);
        assertTrue(adjusted > base,
                "TRIPS+ deep stack should boost commit factor, got " + adjusted);
    }
}
