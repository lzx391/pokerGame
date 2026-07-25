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

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DpNpcNitUnopenFoldTest {

    private static final double NIT_VPIP = 0.11;
    private static final double NIT_PFR = 0.15;
    private static final double NIT_CALL_STATION = 0.37;
    private static final double NIT_FOLD = 0.92;

    private static final double TAG_VPIP = 0.24;
    private static final double TAG_PFR = 0.76;
    private static final double TAG_CALL_STATION = 0.18;
    private static final double TAG_FOLD = 0.22;

    @BeforeEach
    void disableRuleThink() {
        DpNpcRuleThinkProperties props = new DpNpcRuleThinkProperties();
        props.setEnabled(false);
        DpNpcRuleThinkSampler.bind(props);
    }

    @Test
    void nitWeakHandAtSbCompleteBlind_limpChecks() {
        DpRoomBO room = buildHuSbCompleteBlindRoom("hearts_7", "clubs_2");
        DpPlayer hero = room.getPlayers().get(0);
        int callAmount = room.getCurrentBetToCall() - hero.getBet();

        BotAction action = decide(room, hero, callAmount, DpNpcEngine.BotType.NIT);

        assertEquals(BotActionType.CALL_OR_CHECK, action.getType(),
                "NIT 72o @ SB (callAmount>0) should limp/check like other bots, not fold");
    }

    @Test
    void nitWeakHandAtBbFreeCheck_checks() {
        DpRoomBO room = buildHuBbFreeCheckRoom("hearts_7", "clubs_2");
        DpPlayer hero = room.getPlayers().get(1);
        int callAmount = room.getCurrentBetToCall() - hero.getBet();

        BotAction action = decide(room, hero, callAmount, DpNpcEngine.BotType.NIT);

        assertEquals(BotActionType.CALL_OR_CHECK, action.getType(),
                "NIT 72o @ BB (callAmount==0) should check, not fold");
    }

    @Test
    void nitPremiumHandAtUnopened_stillOpens() {
        DpRoomBO room = buildHuSbCompleteBlindRoom("hearts_A", "diamonds_A");
        DpPlayer hero = room.getPlayers().get(0);
        int callAmount = room.getCurrentBetToCall() - hero.getBet();

        BotAction action = decide(room, hero, callAmount, DpNpcEngine.BotType.NIT);

        assertEquals(BotActionType.RAISE, action.getType(),
                "NIT AA in open range should still raise");
    }

    @Test
    void tagWeakHandAtSbCompleteBlind_stillLimpChecks() {
        DpRoomBO room = buildHuSbCompleteBlindRoom("hearts_7", "clubs_2");
        DpPlayer hero = room.getPlayers().get(0);
        int callAmount = room.getCurrentBetToCall() - hero.getBet();

        BotAction action = decide(room, hero, callAmount, DpNpcEngine.BotType.TAG);

        assertEquals(BotActionType.CALL_OR_CHECK, action.getType(),
                "TAG 72o @ SB should remain limp/check, not fold");
        assertNotEquals(BotActionType.FOLD, action.getType());
    }

    private static BotAction decide(
            DpRoomBO room,
            DpPlayer hero,
            int callAmount,
            DpNpcEngine.BotType botType) {
        double vpip = botType == DpNpcEngine.BotType.NIT ? NIT_VPIP : TAG_VPIP;
        double pfr = botType == DpNpcEngine.BotType.NIT ? NIT_PFR : TAG_PFR;
        double callStation = botType == DpNpcEngine.BotType.NIT ? NIT_CALL_STATION : TAG_CALL_STATION;
        double fold = botType == DpNpcEngine.BotType.NIT ? NIT_FOLD : TAG_FOLD;

        return DpNpcUnifiedPreflopStrategy.decide(
                room,
                hero,
                callAmount,
                0.0,
                vpip,
                pfr,
                callStation,
                fold,
                new Random(42L),
                botType);
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

    /** HU：SB limp 后 BB(hero) 免费看牌，callAmount=0，raiseLevel=0。 */
    private static DpRoomBO buildHuBbFreeCheckRoom(String heroCard1, String heroCard2) {
        DpRoomBO room = new DpRoomBO();
        room.setPlaying(true);
        room.setCurrentStage("preflop");
        room.setBigBlindChips(10);
        room.setSmallBlindChips(5);
        room.setPot(20);
        room.setRaiseLevel(0);
        room.setCurrentBetToCall(10);

        DpPlayer sb = new DpPlayer();
        sb.setNickname("SB");
        sb.setChips(4990);
        sb.setBet(10);
        sb.setBlind(1);
        sb.setFold(false);

        DpPlayer bb = new DpPlayer();
        bb.setNickname("BB");
        bb.setChips(4990);
        bb.setBet(10);
        bb.setBlind(2);
        bb.setDealer(true);
        bb.setFold(false);
        bb.setHoleCards(List.of(heroCard1, heroCard2));

        room.getPlayers().add(sb);
        room.getPlayers().add(bb);
        return room;
    }
}
