package com.example.mgdemoplus.npc.strategypro.preflop;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotAction;
import com.example.mgdemoplus.npc.engine.DpNpcEngine.BotActionType;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkProperties;
import com.example.mgdemoplus.npc.rulethink.DpNpcRuleThinkSampler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DpNpcUnifiedPreflopSpotByTest {

    @BeforeEach
    void disableRuleThink() {
        DpNpcRuleThinkProperties props = new DpNpcRuleThinkProperties();
        props.setEnabled(false);
        DpNpcRuleThinkSampler.bind(props);
    }

    @Test
    void huSbCompleteBlind_isUnopenedNotUnknown() throws Exception {
        assertSpot(0, 5, "UNOPENED");
    }

    @Test
    void sixMaxUtgFacingBb_isUnopenedNotUnknown() throws Exception {
        assertSpot(0, 10, "UNOPENED");
    }

    @Test
    void facingOpenRaiseLevelOneStillFacingOpen() throws Exception {
        assertSpot(1, 10, "FACING_OPEN");
    }

    @Test
    void huSbCompleteBlind_aaOpensViaDecideUnopened() {
        DpRoomBO room = buildHuSbCompleteBlindRoom("hearts_A", "diamonds_A");
        DpPlayer hero = room.getPlayers().get(0);
        int callAmount = room.getCurrentBetToCall() - hero.getBet();

        BotAction action = DpNpcUnifiedPreflopStrategy.decide(
                room,
                hero,
                callAmount,
                0.0,
                0.48,
                0.90,
                0.22,
                0.10,
                new Random(42L),
                DpNpcEngine.BotType.MANIAC);

        assertNotEquals(BotActionType.CALL_OR_CHECK, action.getType(),
                "AA in UNOPENED spot should open-raise, not unknownSpotFallback check");
        assertEquals(BotActionType.RAISE, action.getType());
    }

    private static void assertSpot(int raiseLevel, int callAmount, String expected) throws Exception {
        Method spotBy = DpNpcUnifiedPreflopStrategy.class.getDeclaredMethod("spotBy", int.class, int.class);
        spotBy.setAccessible(true);
        Enum<?> spot = (Enum<?>) spotBy.invoke(null, raiseLevel, callAmount);
        assertEquals(expected, spot.name(),
                "raiseLevel=" + raiseLevel + " callAmount=" + callAmount);
    }

    /** HU：seat0=SB(hero, 已下 5)，seat1=BB(10)；补盲 callAmount=5，raiseLevel=0。 */
    private static DpRoomBO buildHuSbCompleteBlindRoom(String heroCard1, String heroCard2) {
        DpRoomBO room = new DpRoomBO();
        room.setPlaying(true);
        room.setCurrentStage("preflop");
        room.setBigBlindChips(10);
        room.setSmallBlindChips(5);
        room.setPot(15);
        room.setRaiseLevel(0);
        room.setCurrentBetToCall(10);

        DpPlayer sb = new DpPlayer();
        sb.setNickname("SB");
        sb.setChips(5000);
        sb.setBet(5);
        sb.setBlind(1);
        sb.setFold(false);
        sb.setHoleCards(List.of(heroCard1, heroCard2));

        DpPlayer bb = new DpPlayer();
        bb.setNickname("BB");
        bb.setChips(4990);
        bb.setBet(10);
        bb.setBlind(2);
        bb.setDealer(true);
        bb.setFold(false);

        room.getPlayers().add(sb);
        room.getPlayers().add(bb);
        return room;
    }
}
