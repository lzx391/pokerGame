package com.example.mgdemoplus.achievement.impl;

import com.example.mgdemoplus.achievement.DpAchievementService;
import com.example.mgdemoplus.achievement.DpDetectAchievement;
import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.history.bo.DpObservedHandActionRecordBO;
import com.example.mgdemoplus.history.bo.DpObservedSeatAtHandStartBO;
import com.example.mgdemoplus.history.bo.DpObservedHandRecordBO;
import com.example.mgdemoplus.history.bo.DpObservedStreetBoardBO;
import com.example.mgdemoplus.history.types.DpObservedHandActionType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.eval.DpDrawDetector;
import com.example.mgdemoplus.npc.eval.DpNpcDrawCategory;
import com.example.mgdemoplus.npc.eval.DpNpcHandClassifier;
import com.example.mgdemoplus.npc.eval.DpNpcHandSnapshot;
import com.example.mgdemoplus.npc.eval.DpNpcMadeHandCategory;
import com.example.mgdemoplus.room.support.DpSettlePersistJob;
import com.example.mgdemoplus.utils.DpUtilHandEvaluator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DpDetectAchievementImpl implements DpDetectAchievement {

    /** 6 人桌及以上成就：本手参与人数口径为 {@link DpObservedHandRecordBO#seatsAtStart} 人数（盲注后、行动前在桌座位）。 */
    private static final int MIN_PARTICIPANTS_FOR_SIX_MAX = 6;

    @Autowired
    private DpAchievementService dpAchievementService;

    @Override
    public void detect(DpSettlePersistJob job, Long handHistoryId) {
        if (job == null || job.archived() == null) {
            return;
        }
        detectQuadNightmare(job.archived(), job.roomSnapshotForParticipants(), handHistoryId);
        detectTwentySevenTerminator(job.archived(), job.roomSnapshotForParticipants(), handHistoryId);
        detectThroneUsurper(job.archived(), job.roomSnapshotForParticipants(), handHistoryId);
        detectTableClear(job.archived(), job.roomSnapshotForParticipants(), handHistoryId);
        detectDrawInsulator(job.archived(), job.roomSnapshotForParticipants(), handHistoryId);
        detectNaturalDisaster(job.archived(), job.roomSnapshotForParticipants(), handHistoryId);
        detectSoulReader(job.archived(), job.roomSnapshotForParticipants(), handHistoryId);
        detectSweepAll(job.archived(), job.roomSnapshotForParticipants(), handHistoryId);
        detectOneStreetHeaven(job.archived(), job.roomSnapshotForParticipants(), handHistoryId);
        detectFinalOracle(job.archived(), job.roomSnapshotForParticipants(), handHistoryId);
        detectMirrorDuel(job.archived(), job.roomSnapshotForParticipants(), handHistoryId);
    }

    /**
     * 四条噩梦：仅摊牌参与者可解锁。摊牌者 = {@code holeCardsAtEnd} 中底牌非空且本局未 FOLD 的玩家。
     * 归档时弃牌者仍可能留有底牌副本，不能仅凭 map 键存在判定。
     */
    /**
     * 27终结者：6 人及以上场次，本局净赢筹码的玩家，底牌为 2 与 7 且杂色（顺序无关）。
     */
    private void detectTwentySevenTerminator(DpObservedHandRecordBO archived, DpRoomBO roomSnapshot,
                                             Long handHistoryId) {
        Map<String, Integer> netChipsChange = archived.netChipsChange;
        Map<String, List<String>> holeCardsAtEnd = archived.holeCardsAtEnd;
        if (netChipsChange == null || netChipsChange.isEmpty()
                || holeCardsAtEnd == null || holeCardsAtEnd.isEmpty()) {
            return;
        }
        if (!hasMinParticipants(archived, MIN_PARTICIPANTS_FOR_SIX_MAX)) {
            return;
        }

        Map<String, Integer> nicknameToUserId = buildNicknameToUserId(roomSnapshot);
        for (Map.Entry<String, Integer> entry : netChipsChange.entrySet()) {
            String nickname = entry.getKey();
            Integer net = entry.getValue();
            if (nickname == null || nickname.isEmpty() || net == null || net <= 0) {
                continue;
            }
            List<String> holeCards = holeCardsAtEnd.get(nickname);
            if (!isOffsuitTwoSeven(holeCards)) {
                continue;
            }
            Integer userId = nicknameToUserId.get(nickname);
            if (userId == null || userId <= 0) {
                continue;
            }
            dpAchievementService.unlockIfAbsent(userId, DpAchievementService.CODE_TWENTY_SEVEN_TERMINATOR, handHistoryId);
        }
    }

    private static boolean isOffsuitTwoSeven(List<String> holeCards) {
        if (holeCards == null || holeCards.size() != 2) {
            return false;
        }
        String suitOfTwo = null;
        String suitOfSeven = null;
        for (String card : holeCards) {
            if (card == null || !card.contains("_")) {
                return false;
            }
            String[] parts = card.split("_", 2);
            if (parts.length != 2) {
                return false;
            }
            String suit = parts[0];
            String rank = parts[1];
            if ("2".equals(rank)) {
                suitOfTwo = suit;
            } else if ("7".equals(rank)) {
                suitOfSeven = suit;
            } else {
                return false;
            }
        }
        return suitOfTwo != null && suitOfSeven != null && !suitOfTwo.equals(suitOfSeven);
    }

    private void detectQuadNightmare(DpObservedHandRecordBO archived, DpRoomBO roomSnapshot, Long handHistoryId) {
        Map<String, List<String>> holeCardsAtEnd = archived.holeCardsAtEnd;
        if (holeCardsAtEnd == null || holeCardsAtEnd.isEmpty()) {
            return;
        }

        List<String> community = resolveFinalCommunity(archived.boardsByStreet);
        if (community.size() < 5) {
            return;
        }

        Set<String> folded = collectFoldedNicknames(archived.actions);
        Map<String, Integer> nicknameToUserId = buildNicknameToUserId(roomSnapshot);

        boolean hasStraightFlushAmongShowdown = false;
        List<String> quadShowdownNicknames = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : holeCardsAtEnd.entrySet()) {
            String nickname = entry.getKey();
            if (!isShowdownParticipant(nickname, entry.getValue(), folded)) {
                continue;
            }

            List<String> allCards = new ArrayList<>(entry.getValue());
            allCards.addAll(community);
            if (allCards.size() < 5) {
                continue;
            }

            DpUtilHandEvaluator.HandStrength hs = DpUtilHandEvaluator.evaluateBestHand(allCards);
            if (hs == null) {
                continue;
            }
            int cat = hs.rankCategory;
            if (cat >= 9) {
                hasStraightFlushAmongShowdown = true;
            } else if (cat == 8) {
                quadShowdownNicknames.add(nickname);
            }
        }

        if (!hasStraightFlushAmongShowdown || quadShowdownNicknames.isEmpty()) {
            return;
        }

        for (String nickname : quadShowdownNicknames) {
            Integer userId = nicknameToUserId.get(nickname);
            if (userId == null || userId <= 0) {
                continue;
            }
            dpAchievementService.unlockIfAbsent(userId, DpAchievementService.CODE_QUAD_NIGHTMARE, handHistoryId);
        }
    }

    private static boolean isShowdownParticipant(String nickname, List<String> holeCards, Set<String> folded) {
        if (nickname == null || nickname.isEmpty()) {
            return false;
        }
        if (folded.contains(nickname)) {
            return false;
        }
        return holeCards != null && !holeCards.isEmpty();
    }

    private static Set<String> collectFoldedNicknames(List<DpObservedHandActionRecordBO> actions) {
        Set<String> folded = new HashSet<>();
        if (actions == null) {
            return folded;
        }
        for (DpObservedHandActionRecordBO action : actions) {
            if (action == null || action.type != DpObservedHandActionType.FOLD) {
                continue;
            }
            if (action.actorNickname != null && !action.actorNickname.isEmpty()) {
                folded.add(action.actorNickname);
            }
        }
        return folded;
    }

    private static List<String> resolveFinalCommunity(List<DpObservedStreetBoardBO> boards) {
        if (boards == null || boards.isEmpty()) {
            return List.of();
        }
        List<String> best = List.of();
        int bestSize = 0;
        for (DpObservedStreetBoardBO board : boards) {
            if (board == null || board.communityCards == null) {
                continue;
            }
            if (board.communityCards.size() > bestSize) {
                bestSize = board.communityCards.size();
                best = board.communityCards;
            }
        }
        return best;
    }

    /**
     * 王座更迭：摊牌赢家以火箭（straight flush，含皇家）击败另一名摊牌火箭对手。仅真人玩家。
     */
    private void detectThroneUsurper(DpObservedHandRecordBO archived, DpRoomBO roomSnapshot, Long handHistoryId) {
        Map<String, Integer> netChipsChange = archived.netChipsChange;
        Map<String, List<String>> holeCardsAtEnd = archived.holeCardsAtEnd;
        if (netChipsChange == null || netChipsChange.isEmpty()
                || holeCardsAtEnd == null || holeCardsAtEnd.isEmpty()) {
            return;
        }

        List<String> community = resolveFinalCommunity(archived.boardsByStreet);
        if (community.size() < 5) {
            return;
        }

        Set<String> folded = collectFoldedNicknames(archived.actions);
        Map<String, Integer> nicknameToUserId = buildNicknameToUserId(roomSnapshot);
        Map<String, DpUtilHandEvaluator.HandStrength> showdownStrength = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : holeCardsAtEnd.entrySet()) {
            String nickname = entry.getKey();
            if (!isShowdownParticipant(nickname, entry.getValue(), folded)) {
                continue;
            }
            List<String> allCards = new ArrayList<>(entry.getValue());
            allCards.addAll(community);
            DpUtilHandEvaluator.HandStrength hs = DpUtilHandEvaluator.evaluateBestHand(allCards);
            if (hs != null && hs.rankCategory >= 9) {
                showdownStrength.put(nickname, hs);
            }
        }

        if (showdownStrength.size() < 2) {
            return;
        }

        for (Map.Entry<String, Integer> entry : netChipsChange.entrySet()) {
            String hero = entry.getKey();
            Integer net = entry.getValue();
            if (hero == null || hero.isEmpty() || net == null || net <= 0) {
                continue;
            }
            DpUtilHandEvaluator.HandStrength heroHs = showdownStrength.get(hero);
            if (heroHs == null) {
                continue;
            }
            boolean beatLowerStraightFlush = false;
            for (Map.Entry<String, DpUtilHandEvaluator.HandStrength> oppEntry : showdownStrength.entrySet()) {
                if (hero.equals(oppEntry.getKey())) {
                    continue;
                }
                DpUtilHandEvaluator.HandStrength oppHs = oppEntry.getValue();
                if (heroHs.compareTo(oppHs) > 0) {
                    beatLowerStraightFlush = true;
                    break;
                }
            }
            if (!beatLowerStraightFlush) {
                continue;
            }
            Integer userId = nicknameToUserId.get(hero);
            if (userId == null || userId <= 0) {
                continue;
            }
            dpAchievementService.unlockIfAbsent(userId, DpAchievementService.CODE_THRONE_USURPER, handHistoryId);
        }
    }

    /**
     * 牌桌消消乐：6 人及以上场次，净赢且本手无摊牌（所有对手均已弃牌）。仅真人。
     */
    private void detectTableClear(DpObservedHandRecordBO archived, DpRoomBO roomSnapshot, Long handHistoryId) {
        Map<String, Integer> netChipsChange = archived.netChipsChange;
        if (netChipsChange == null || netChipsChange.size() < 2) {
            return;
        }
        if (!hasMinParticipants(archived, MIN_PARTICIPANTS_FOR_SIX_MAX)) {
            return;
        }

        Set<String> folded = collectFoldedNicknames(archived.actions);
        Map<String, Integer> nicknameToUserId = buildNicknameToUserId(roomSnapshot);
        Map<String, List<String>> holeCardsAtEnd = archived.holeCardsAtEnd;

        for (Map.Entry<String, Integer> entry : netChipsChange.entrySet()) {
            String hero = entry.getKey();
            Integer net = entry.getValue();
            if (hero == null || hero.isEmpty() || net == null || net <= 0) {
                continue;
            }
            Integer userId = nicknameToUserId.get(hero);
            if (userId == null || userId <= 0) {
                continue;
            }
            if (!allOpponentsFolded(hero, netChipsChange, folded, holeCardsAtEnd)) {
                continue;
            }
            dpAchievementService.unlockIfAbsent(userId, DpAchievementService.CODE_TABLE_CLEAR, handHistoryId);
        }
    }

    /**
     * 听牌绝缘体：翻牌或转牌曾花顺双抽，河牌未成顺/同花或更强。不要求赢牌。仅真人。
     */
    private void detectDrawInsulator(DpObservedHandRecordBO archived, DpRoomBO roomSnapshot, Long handHistoryId) {
        Map<String, List<String>> holeCardsAtEnd = archived.holeCardsAtEnd;
        if (holeCardsAtEnd == null || holeCardsAtEnd.isEmpty()) {
            return;
        }

        List<String> flop = resolveCommunityUpTo(archived.boardsByStreet, 3);
        List<String> turn = resolveCommunityUpTo(archived.boardsByStreet, 4);
        List<String> river = resolveFinalCommunity(archived.boardsByStreet);
        if (river.size() < 5) {
            return;
        }

        Map<String, Integer> nicknameToUserId = buildNicknameToUserId(roomSnapshot);
        for (Map.Entry<String, List<String>> entry : holeCardsAtEnd.entrySet()) {
            String nickname = entry.getKey();
            List<String> hole = entry.getValue();
            if (nickname == null || nickname.isEmpty() || hole == null || hole.size() != 2) {
                continue;
            }
            Integer userId = nicknameToUserId.get(nickname);
            if (userId == null || userId <= 0) {
                continue;
            }
            if (!hadComboDrawOnFlopOrTurn(hole, flop, turn)) {
                continue;
            }
            DpUtilHandEvaluator.HandStrength riverHs = DpUtilHandEvaluator.evaluateBestHand(
                    concatCards(hole, river));
            // 花顺均未命中：牌型低于顺子（cat&lt;5），保守不触发两对及以上「碰巧成牌」
            if (riverHs == null || riverHs.rankCategory >= 5) {
                continue;
            }
            dpAchievementService.unlockIfAbsent(userId, DpAchievementService.CODE_DRAW_INSULATOR, handHistoryId);
        }
    }

    /**
     * 天灾：受害者（摊牌输家）翻后两对及以上领先，某对手翻后仅高牌或一对；转牌仍不输；河牌被该对手反超。
     * 连追：翻后对手未成强听牌（排除 4-to-flush、8-out 顺听等）；转、河各补一张且均参与终局成牌（顺/花/葫芦等）。
     * 成花时转牌与河牌须同为成花花色，且翻后未成 4-to-flush。仅真人。
     */
    private void detectNaturalDisaster(DpObservedHandRecordBO archived, DpRoomBO roomSnapshot, Long handHistoryId) {
        Map<String, Integer> netChipsChange = archived.netChipsChange;
        Map<String, List<String>> holeCardsAtEnd = archived.holeCardsAtEnd;
        if (netChipsChange == null || holeCardsAtEnd == null) {
            return;
        }

        List<String> flop = resolveCommunityUpTo(archived.boardsByStreet, 3);
        List<String> turn = resolveCommunityUpTo(archived.boardsByStreet, 4);
        List<String> river = resolveFinalCommunity(archived.boardsByStreet);
        if (flop.size() < 3 || turn.size() < 4 || river.size() < 5) {
            return;
        }

        Set<String> folded = collectFoldedNicknames(archived.actions);
        Map<String, Integer> nicknameToUserId = buildNicknameToUserId(roomSnapshot);

        for (Map.Entry<String, Integer> entry : netChipsChange.entrySet()) {
            String hero = entry.getKey();
            Integer heroNet = entry.getValue();
            if (hero == null || hero.isEmpty() || heroNet == null || heroNet >= 0) {
                continue;
            }
            List<String> heroHole = holeCardsAtEnd.get(hero);
            if (!isShowdownParticipant(hero, heroHole, folded)) {
                continue;
            }
            Integer userId = nicknameToUserId.get(hero);
            if (userId == null || userId <= 0) {
                continue;
            }

            DpUtilHandEvaluator.HandStrength heroFlop = evalHand(heroHole, flop);
            DpUtilHandEvaluator.HandStrength heroTurn = evalHand(heroHole, turn);
            DpUtilHandEvaluator.HandStrength heroRiver = evalHand(heroHole, river);
            if (heroFlop == null || heroTurn == null || heroRiver == null) {
                continue;
            }

            boolean disaster = false;
            for (Map.Entry<String, Integer> oppEntry : netChipsChange.entrySet()) {
                String opp = oppEntry.getKey();
                Integer oppNet = oppEntry.getValue();
                if (opp == null || opp.equals(hero) || oppNet == null || oppNet <= 0) {
                    continue;
                }
                List<String> oppHole = holeCardsAtEnd.get(opp);
                if (!isShowdownParticipant(opp, oppHole, folded)) {
                    continue;
                }
                DpUtilHandEvaluator.HandStrength oppFlop = evalHand(oppHole, flop);
                DpUtilHandEvaluator.HandStrength oppTurn = evalHand(oppHole, turn);
                DpUtilHandEvaluator.HandStrength oppRiver = evalHand(oppHole, river);
                if (oppFlop == null || oppTurn == null || oppRiver == null) {
                    continue;
                }
                if (heroFlop.rankCategory < 3 || oppFlop.rankCategory >= 3) {
                    continue;
                }
                if (heroTurn.compareTo(oppTurn) < 0 || oppRiver.compareTo(heroRiver) <= 0) {
                    continue;
                }
                if (!isConsecutiveTurnRiverComeback(oppHole, flop, turn, river, oppFlop, oppTurn, oppRiver)) {
                    continue;
                }
                disaster = true;
                break;
            }
            if (disaster) {
                dpAchievementService.unlockIfAbsent(userId, DpAchievementService.CODE_NATURAL_DISASTER, handHistoryId);
            }
        }
    }

    /**
     * 转、河连追：翻后未成强听牌；转、河各补一张且均参与终局成顺/成花/葫芦或更强。
     * 成花：翻后未成 4-to-flush，转牌与河牌须同为成花花色且各补一张完成同花。
     * 成顺/葫芦等：转、河牌均出现在河牌终局最佳五张中，且去掉任一张都无法保持终局牌力。
     */
    private static boolean isConsecutiveTurnRiverComeback(List<String> oppHole,
                                                          List<String> flop,
                                                          List<String> turn,
                                                          List<String> river,
                                                          DpUtilHandEvaluator.HandStrength oppFlop,
                                                          DpUtilHandEvaluator.HandStrength oppTurn,
                                                          DpUtilHandEvaluator.HandStrength oppRiver) {
        if (oppFlop == null || oppTurn == null || oppRiver == null
                || flop.size() < 3 || turn.size() < 4 || river.size() < 5) {
            return false;
        }
        if (oppRiver.rankCategory < 5) {
            return false;
        }
        if (hasStrongDrawAfterFlop(oppHole, flop, oppFlop)) {
            return false;
        }
        if (oppRiver.rankCategory == 6) {
            return isFlushTurnRiverChase(oppHole, flop, turn, river, oppTurn, oppRiver);
        }
        String turnCard = turn.get(3);
        String riverCard = river.get(4);
        if (!bothStreetCardsInBestFive(oppHole, river, turnCard, riverCard)) {
            return false;
        }
        return oppFlop.compareTo(oppRiver) < 0
                && oppTurn.compareTo(oppRiver) < 0;
    }

    /** 转、河两张公共牌均出现在对手河牌终局最佳五张中。 */
    private static boolean bothStreetCardsInBestFive(List<String> hole,
                                                     List<String> riverBoard,
                                                     String turnCard,
                                                     String riverCard) {
        List<String> bestFive = DpUtilHandEvaluator.getBestHandCards(concatCards(hole, riverBoard));
        return bestFive != null
                && bestFive.contains(turnCard)
                && bestFive.contains(riverCard);
    }

    /** 翻后已具备强听牌（4-to-flush、8-out 顺听、花顺双抽）则不算天灾连追。 */
    private static boolean hasStrongDrawAfterFlop(List<String> hole,
                                                  List<String> flop,
                                                  DpUtilHandEvaluator.HandStrength strengthOnFlop) {
        if (strengthOnFlop != null && strengthOnFlop.rankCategory >= 5) {
            return false;
        }
        DpNpcDrawCategory draw = DpDrawDetector.detect(strengthOnFlop, hole, flop, "flop");
        return draw == DpNpcDrawCategory.FLUSH_DRAW
                || draw == DpNpcDrawCategory.OESD
                || draw == DpNpcDrawCategory.COMBO_DRAW;
    }

    /**
     * 成花连追：翻后同花花色 &lt; 4；转、河各发一张该花色，河牌才成同花。
     */
    private static boolean isFlushTurnRiverChase(List<String> oppHole,
                                                 List<String> flop,
                                                 List<String> turn,
                                                 List<String> river,
                                                 DpUtilHandEvaluator.HandStrength oppTurn,
                                                 DpUtilHandEvaluator.HandStrength oppRiver) {
        if (oppRiver.rankCategory < 6 || oppTurn.rankCategory >= 6) {
            return false;
        }
        int flushSuit = resolveFlushSuit(oppHole, river);
        if (flushSuit < 0) {
            return false;
        }
        int flopSuitCount = countSuitInCards(concatCards(oppHole, flop), flushSuit);
        if (flopSuitCount >= 4) {
            return false;
        }
        String turnCard = turn.get(3);
        String riverCard = river.get(4);
        if (suitCode(turnCard) != flushSuit || suitCode(riverCard) != flushSuit) {
            return false;
        }
        int turnSuitCount = countSuitInCards(concatCards(oppHole, turn), flushSuit);
        int riverSuitCount = countSuitInCards(concatCards(oppHole, river), flushSuit);
        return turnSuitCount == flopSuitCount + 1
                && riverSuitCount == turnSuitCount + 1
                && riverSuitCount >= 5;
    }

    private static int resolveFlushSuit(List<String> hole, List<String> fullBoard) {
        int[] counts = new int[4];
        for (String card : concatCards(hole, fullBoard)) {
            int suit = suitCode(card);
            if (suit >= 0 && suit < 4) {
                counts[suit]++;
            }
        }
        for (int suit = 0; suit < 4; suit++) {
            if (counts[suit] >= 5) {
                return suit;
            }
        }
        return -1;
    }

    private static int countSuitInCards(List<String> cards, int suit) {
        if (cards == null) {
            return 0;
        }
        int count = 0;
        for (String card : cards) {
            if (suitCode(card) == suit) {
                count++;
            }
        }
        return count;
    }

    private static int suitCode(String card) {
        if (card == null || !card.contains("_")) {
            return -1;
        }
        return switch (card.split("_", 2)[0]) {
            case "hearts" -> 0;
            case "diamonds" -> 1;
            case "clubs" -> 2;
            case "spades" -> 3;
            default -> -1;
        };
    }

    /**
     * 横扫一切：至少两名摊牌参与者；赢家净赢筹码 &gt; 0；其余<strong>摊牌参与者</strong>终局筹码均为 0（不含已弃牌者）。
     */
    private void detectSweepAll(DpObservedHandRecordBO archived, DpRoomBO roomSnapshot, Long handHistoryId) {
        Map<String, Integer> netChipsChange = archived.netChipsChange;
        Map<String, List<String>> holeCardsAtEnd = archived.holeCardsAtEnd;
        if (netChipsChange == null || netChipsChange.isEmpty()
                || holeCardsAtEnd == null || holeCardsAtEnd.isEmpty()) {
            return;
        }

        Set<String> folded = collectFoldedNicknames(archived.actions);
        List<String> showdownParticipants = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : holeCardsAtEnd.entrySet()) {
            if (isShowdownParticipant(entry.getKey(), entry.getValue(), folded)) {
                showdownParticipants.add(entry.getKey());
            }
        }
        if (showdownParticipants.size() < 2) {
            return;
        }

        Map<String, Integer> nicknameToUserId = buildNicknameToUserId(roomSnapshot);
        for (Map.Entry<String, Integer> entry : netChipsChange.entrySet()) {
            String hero = entry.getKey();
            Integer net = entry.getValue();
            if (hero == null || hero.isEmpty() || net == null || net <= 0) {
                continue;
            }
            if (!isShowdownParticipant(hero, holeCardsAtEnd.get(hero), folded)) {
                continue;
            }
            Integer userId = nicknameToUserId.get(hero);
            if (userId == null || userId <= 0) {
                continue;
            }
            if (!allOtherShowdownOpponentsBusted(hero, showdownParticipants, archived)) {
                continue;
            }
            dpAchievementService.unlockIfAbsent(userId, DpAchievementService.CODE_SWEEP_ALL, handHistoryId);
        }
    }

    private static boolean allOtherShowdownOpponentsBusted(String hero,
                                                           List<String> showdownParticipants,
                                                           DpObservedHandRecordBO archived) {
        for (String opp : showdownParticipants) {
            if (opp == null || opp.equals(hero)) {
                continue;
            }
            int chips = resolveChipsAtEnd(archived, opp);
            if (chips != 0) {
                return false;
            }
        }
        return true;
    }

    private static int resolveChipsAtEnd(DpObservedHandRecordBO archived, String nickname) {
        if (archived.chipsAtEnd != null && archived.chipsAtEnd.containsKey(nickname)) {
            Integer v = archived.chipsAtEnd.get(nickname);
            return v != null ? v : 0;
        }
        Integer net = archived.netChipsChange != null ? archived.netChipsChange.get(nickname) : null;
        if (archived.seatsAtStart != null) {
            for (DpObservedSeatAtHandStartBO seat : archived.seatsAtStart) {
                if (seat != null && nickname.equals(seat.nickname)) {
                    return seat.chipsAfterBlinds + (net != null ? net : 0);
                }
            }
        }
        return net != null ? net : 0;
    }

    /**
     * 灵魂阅读者：本手净赢家且摊牌最强，成牌为高牌或底对，击败转/河有进攻动作的诈唬对手。仅真人。
     */
    private void detectSoulReader(DpObservedHandRecordBO archived, DpRoomBO roomSnapshot, Long handHistoryId) {
        Map<String, Integer> netChipsChange = archived.netChipsChange;
        Map<String, List<String>> holeCardsAtEnd = archived.holeCardsAtEnd;
        if (netChipsChange == null || holeCardsAtEnd == null) {
            return;
        }

        List<String> river = resolveFinalCommunity(archived.boardsByStreet);
        if (river.size() < 5) {
            return;
        }

        Set<String> folded = collectFoldedNicknames(archived.actions);
        Map<String, Integer> nicknameToUserId = buildNicknameToUserId(roomSnapshot);

        for (Map.Entry<String, Integer> entry : netChipsChange.entrySet()) {
            String hero = entry.getKey();
            Integer net = entry.getValue();
            if (hero == null || hero.isEmpty() || net == null || net <= 0) {
                continue;
            }
            List<String> heroHole = holeCardsAtEnd.get(hero);
            if (!isShowdownParticipant(hero, heroHole, folded)) {
                continue;
            }
            Integer userId = nicknameToUserId.get(hero);
            if (userId == null || userId <= 0) {
                continue;
            }

            DpNpcHandSnapshot heroSnap = DpNpcHandClassifier.classify(heroHole, river, "river");
            DpNpcMadeHandCategory made = heroSnap.made;
            if (made != DpNpcMadeHandCategory.HIGH_CARD && made != DpNpcMadeHandCategory.BOTTOM_PAIR) {
                continue;
            }
            DpUtilHandEvaluator.HandStrength heroHs = evalHand(heroHole, river);
            if (heroHs == null) {
                continue;
            }

            boolean sawAggressiveBluffer = false;
            boolean beatsAllShowdownOpponents = true;
            for (Map.Entry<String, List<String>> oppEntry : holeCardsAtEnd.entrySet()) {
                String opp = oppEntry.getKey();
                if (opp == null || opp.equals(hero)) {
                    continue;
                }
                List<String> oppHole = oppEntry.getValue();
                if (!isShowdownParticipant(opp, oppHole, folded)) {
                    continue;
                }
                DpUtilHandEvaluator.HandStrength oppHs = evalHand(oppHole, river);
                if (oppHs == null || heroHs.compareTo(oppHs) <= 0) {
                    beatsAllShowdownOpponents = false;
                    break;
                }
                if (hasAggressivePostflopAction(opp, archived.actions, "turn", "river")) {
                    sawAggressiveBluffer = true;
                }
            }
            if (beatsAllShowdownOpponents && sawAggressiveBluffer) {
                dpAchievementService.unlockIfAbsent(userId, DpAchievementService.CODE_SOUL_READER, handHistoryId);
            }
        }
    }

    /**
     * 本手参与人数：优先 {@code seatsAtStart}（盲注后在桌座位），否则回退结算/底牌 map 键数。
     */
    private static int resolveHandParticipantCount(DpObservedHandRecordBO archived) {
        if (archived.seatsAtStart != null && !archived.seatsAtStart.isEmpty()) {
            return archived.seatsAtStart.size();
        }
        if (archived.netChipsChange != null && !archived.netChipsChange.isEmpty()) {
            return archived.netChipsChange.size();
        }
        if (archived.holeCardsAtEnd != null) {
            return archived.holeCardsAtEnd.size();
        }
        return 0;
    }

    private static boolean hasMinParticipants(DpObservedHandRecordBO archived, int min) {
        return resolveHandParticipantCount(archived) >= min;
    }

    private static boolean hadComboDrawOnFlopOrTurn(List<String> hole, List<String> flop, List<String> turn) {
        if (flop.size() >= 3) {
            DpUtilHandEvaluator.HandStrength hs = DpUtilHandEvaluator.evaluateBestHand(concatCards(hole, flop));
            if (DpDrawDetector.detect(hs, hole, flop, "flop") == DpNpcDrawCategory.COMBO_DRAW) {
                return true;
            }
        }
        if (turn.size() >= 4) {
            DpUtilHandEvaluator.HandStrength hs = DpUtilHandEvaluator.evaluateBestHand(concatCards(hole, turn));
            return DpDrawDetector.detect(hs, hole, turn, "turn") == DpNpcDrawCategory.COMBO_DRAW;
        }
        return false;
    }

    private static boolean allOpponentsFolded(String hero,
                                              Map<String, Integer> netChipsChange,
                                              Set<String> folded,
                                              Map<String, List<String>> holeCardsAtEnd) {
        for (String nickname : netChipsChange.keySet()) {
            if (nickname == null || nickname.equals(hero)) {
                continue;
            }
            if (!folded.contains(nickname)) {
                List<String> hole = holeCardsAtEnd != null ? holeCardsAtEnd.get(nickname) : null;
                if (isShowdownParticipant(nickname, hole, folded)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasAggressivePostflopAction(String nickname,
                                                       List<DpObservedHandActionRecordBO> actions,
                                                       String... stages) {
        if (actions == null || nickname == null) {
            return false;
        }
        Set<String> stageSet = Set.of(stages);
        for (DpObservedHandActionRecordBO action : actions) {
            if (action == null || !nickname.equals(action.actorNickname)) {
                continue;
            }
            if (!stageSet.contains(action.stage)) {
                continue;
            }
            if (action.type == DpObservedHandActionType.BET
                    || action.type == DpObservedHandActionType.RAISE
                    || action.type == DpObservedHandActionType.ALL_IN) {
                return true;
            }
        }
        return false;
    }

    private static DpUtilHandEvaluator.HandStrength evalHand(List<String> hole, List<String> community) {
        return DpUtilHandEvaluator.evaluateBestHand(concatCards(hole, community));
    }

    private static List<String> concatCards(List<String> hole, List<String> community) {
        List<String> all = new ArrayList<>();
        if (hole != null) {
            all.addAll(hole);
        }
        if (community != null) {
            all.addAll(community);
        }
        return all;
    }

    private static List<String> resolveCommunityUpTo(List<DpObservedStreetBoardBO> boards, int cardCount) {
        List<String> finalBoard = resolveFinalCommunity(boards);
        if (finalBoard.size() >= cardCount) {
            return List.copyOf(finalBoard.subList(0, cardCount));
        }
        return finalBoard;
    }

    /**
     * 一街天堂：受害者（摊牌输家）翻后严格领先，转牌仍不输，河牌被单张反超。仅真人。
     */
    private void detectOneStreetHeaven(DpObservedHandRecordBO archived, DpRoomBO roomSnapshot, Long handHistoryId) {
        Map<String, Integer> netChipsChange = archived.netChipsChange;
        Map<String, List<String>> holeCardsAtEnd = archived.holeCardsAtEnd;
        if (netChipsChange == null || holeCardsAtEnd == null) {
            return;
        }

        List<String> flop = resolveCommunityUpTo(archived.boardsByStreet, 3);
        List<String> turn = resolveCommunityUpTo(archived.boardsByStreet, 4);
        List<String> river = resolveFinalCommunity(archived.boardsByStreet);
        if (flop.size() < 3 || turn.size() < 4 || river.size() < 5) {
            return;
        }

        Set<String> folded = collectFoldedNicknames(archived.actions);
        Map<String, Integer> nicknameToUserId = buildNicknameToUserId(roomSnapshot);

        for (Map.Entry<String, Integer> entry : netChipsChange.entrySet()) {
            String hero = entry.getKey();
            Integer heroNet = entry.getValue();
            if (hero == null || hero.isEmpty() || heroNet == null || heroNet >= 0) {
                continue;
            }
            List<String> heroHole = holeCardsAtEnd.get(hero);
            if (!isShowdownParticipant(hero, heroHole, folded)) {
                continue;
            }
            Integer userId = nicknameToUserId.get(hero);
            if (userId == null || userId <= 0) {
                continue;
            }

            DpUtilHandEvaluator.HandStrength heroFlop = evalHand(heroHole, flop);
            DpUtilHandEvaluator.HandStrength heroTurn = evalHand(heroHole, turn);
            DpUtilHandEvaluator.HandStrength heroRiver = evalHand(heroHole, river);
            if (heroFlop == null || heroTurn == null || heroRiver == null) {
                continue;
            }
            if (!isStrictlyStrongestAmongShowdown(hero, heroFlop, holeCardsAtEnd, folded, flop)) {
                continue;
            }
            if (!stillLeadingOrTiedAtTurn(hero, heroTurn, holeCardsAtEnd, folded, turn)) {
                continue;
            }
            if (!riverSingleCardOvertake(hero, heroTurn, heroRiver, holeCardsAtEnd, folded, turn, river)) {
                continue;
            }
            dpAchievementService.unlockIfAbsent(userId, DpAchievementService.CODE_ONE_STREET_HEAVEN, handHistoryId);
        }
    }

    /**
     * 决赛神谕：净赢摊牌赢家，转牌弱于至少一名摊牌对手，终局最强赢池。仅真人。
     */
    private void detectFinalOracle(DpObservedHandRecordBO archived, DpRoomBO roomSnapshot, Long handHistoryId) {
        Map<String, Integer> netChipsChange = archived.netChipsChange;
        Map<String, List<String>> holeCardsAtEnd = archived.holeCardsAtEnd;
        if (netChipsChange == null || holeCardsAtEnd == null) {
            return;
        }

        List<String> turn = resolveCommunityUpTo(archived.boardsByStreet, 4);
        List<String> river = resolveFinalCommunity(archived.boardsByStreet);
        if (turn.size() < 4 || river.size() < 5) {
            return;
        }

        Set<String> folded = collectFoldedNicknames(archived.actions);
        Map<String, Integer> nicknameToUserId = buildNicknameToUserId(roomSnapshot);

        for (Map.Entry<String, Integer> entry : netChipsChange.entrySet()) {
            String hero = entry.getKey();
            Integer net = entry.getValue();
            if (hero == null || hero.isEmpty() || net == null || net <= 0) {
                continue;
            }
            List<String> heroHole = holeCardsAtEnd.get(hero);
            if (!isShowdownParticipant(hero, heroHole, folded)) {
                continue;
            }
            Integer userId = nicknameToUserId.get(hero);
            if (userId == null || userId <= 0) {
                continue;
            }

            DpUtilHandEvaluator.HandStrength heroTurn = evalHand(heroHole, turn);
            DpUtilHandEvaluator.HandStrength heroRiver = evalHand(heroHole, river);
            if (heroTurn == null || heroRiver == null) {
                continue;
            }
            if (!wasStrictlyBehindAtTurn(hero, heroTurn, holeCardsAtEnd, folded, turn)) {
                continue;
            }
            if (!isStrictlyStrongestAmongShowdown(hero, heroRiver, holeCardsAtEnd, folded, river)) {
                continue;
            }
            dpAchievementService.unlockIfAbsent(userId, DpAchievementService.CODE_FINAL_ORACLE, handHistoryId);
        }
    }

    /**
     * 镜像对决：杂色底牌赢家河牌成同花，转牌与另一名杂色摊牌对手 handRankName 相同。仅真人赢家。
     */
    private void detectMirrorDuel(DpObservedHandRecordBO archived, DpRoomBO roomSnapshot, Long handHistoryId) {
        Map<String, Integer> netChipsChange = archived.netChipsChange;
        Map<String, List<String>> holeCardsAtEnd = archived.holeCardsAtEnd;
        if (netChipsChange == null || holeCardsAtEnd == null) {
            return;
        }

        List<String> turn = resolveCommunityUpTo(archived.boardsByStreet, 4);
        List<String> river = resolveFinalCommunity(archived.boardsByStreet);
        if (turn.size() < 4 || river.size() < 5) {
            return;
        }

        Set<String> folded = collectFoldedNicknames(archived.actions);
        Map<String, Integer> nicknameToUserId = buildNicknameToUserId(roomSnapshot);
        int showdownCount = countShowdownParticipants(holeCardsAtEnd, folded);
        if (showdownCount < 2) {
            return;
        }

        for (Map.Entry<String, Integer> entry : netChipsChange.entrySet()) {
            String hero = entry.getKey();
            Integer net = entry.getValue();
            if (hero == null || hero.isEmpty() || net == null || net <= 0) {
                continue;
            }
            List<String> heroHole = holeCardsAtEnd.get(hero);
            if (!isShowdownParticipant(hero, heroHole, folded) || !isOffsuitHole(heroHole)) {
                continue;
            }
            Integer userId = nicknameToUserId.get(hero);
            if (userId == null || userId <= 0) {
                continue;
            }

            DpUtilHandEvaluator.HandStrength heroTurn = evalHand(heroHole, turn);
            DpUtilHandEvaluator.HandStrength heroRiver = evalHand(heroHole, river);
            if (heroTurn == null || heroRiver == null) {
                continue;
            }
            if (heroTurn.rankCategory >= 6 || heroRiver.rankCategory != 6) {
                continue;
            }
            String heroTurnRankName = resolveHandRankNameOnTurn(hero, archived, heroHole, turn);
            if (heroTurnRankName == null || heroTurnRankName.isEmpty()) {
                continue;
            }

            boolean mirrorOpponent = false;
            for (Map.Entry<String, List<String>> oppEntry : holeCardsAtEnd.entrySet()) {
                String opp = oppEntry.getKey();
                if (opp == null || opp.equals(hero)) {
                    continue;
                }
                List<String> oppHole = oppEntry.getValue();
                if (!isShowdownParticipant(opp, oppHole, folded) || !isOffsuitHole(oppHole)) {
                    continue;
                }
                String oppTurnRankName = resolveHandRankNameOnTurn(opp, archived, oppHole, turn);
                if (heroTurnRankName.equals(oppTurnRankName)) {
                    mirrorOpponent = true;
                    break;
                }
            }
            if (mirrorOpponent) {
                dpAchievementService.unlockIfAbsent(userId, DpAchievementService.CODE_MIRROR_DUEL, handHistoryId);
            }
        }
    }

    private static boolean isStrictlyStrongestAmongShowdown(String hero,
                                                             DpUtilHandEvaluator.HandStrength heroStrength,
                                                             Map<String, List<String>> holeCardsAtEnd,
                                                             Set<String> folded,
                                                             List<String> community) {
        for (Map.Entry<String, List<String>> entry : holeCardsAtEnd.entrySet()) {
            String opp = entry.getKey();
            if (opp == null || opp.equals(hero)) {
                continue;
            }
            if (!isShowdownParticipant(opp, entry.getValue(), folded)) {
                continue;
            }
            DpUtilHandEvaluator.HandStrength oppStrength = evalHand(entry.getValue(), community);
            if (oppStrength == null || heroStrength.compareTo(oppStrength) <= 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean stillLeadingOrTiedAtTurn(String hero,
                                                    DpUtilHandEvaluator.HandStrength heroTurn,
                                                    Map<String, List<String>> holeCardsAtEnd,
                                                    Set<String> folded,
                                                    List<String> turn) {
        for (Map.Entry<String, List<String>> entry : holeCardsAtEnd.entrySet()) {
            String opp = entry.getKey();
            if (opp == null || opp.equals(hero)) {
                continue;
            }
            if (!isShowdownParticipant(opp, entry.getValue(), folded)) {
                continue;
            }
            DpUtilHandEvaluator.HandStrength oppTurn = evalHand(entry.getValue(), turn);
            if (oppTurn == null || heroTurn.compareTo(oppTurn) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean riverSingleCardOvertake(String hero,
                                                   DpUtilHandEvaluator.HandStrength heroTurn,
                                                   DpUtilHandEvaluator.HandStrength heroRiver,
                                                   Map<String, List<String>> holeCardsAtEnd,
                                                   Set<String> folded,
                                                   List<String> turn,
                                                   List<String> river) {
        for (Map.Entry<String, List<String>> entry : holeCardsAtEnd.entrySet()) {
            String opp = entry.getKey();
            if (opp == null || opp.equals(hero)) {
                continue;
            }
            if (!isShowdownParticipant(opp, entry.getValue(), folded)) {
                continue;
            }
            DpUtilHandEvaluator.HandStrength oppTurn = evalHand(entry.getValue(), turn);
            DpUtilHandEvaluator.HandStrength oppRiver = evalHand(entry.getValue(), river);
            if (oppTurn == null || oppRiver == null) {
                continue;
            }
            if (oppTurn.compareTo(heroTurn) <= 0 && oppRiver.compareTo(heroRiver) > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean wasStrictlyBehindAtTurn(String hero,
                                                   DpUtilHandEvaluator.HandStrength heroTurn,
                                                   Map<String, List<String>> holeCardsAtEnd,
                                                   Set<String> folded,
                                                   List<String> turn) {
        for (Map.Entry<String, List<String>> entry : holeCardsAtEnd.entrySet()) {
            String opp = entry.getKey();
            if (opp == null || opp.equals(hero)) {
                continue;
            }
            if (!isShowdownParticipant(opp, entry.getValue(), folded)) {
                continue;
            }
            DpUtilHandEvaluator.HandStrength oppTurn = evalHand(entry.getValue(), turn);
            if (oppTurn != null && heroTurn.compareTo(oppTurn) < 0) {
                return true;
            }
        }
        return false;
    }

    private static int countShowdownParticipants(Map<String, List<String>> holeCardsAtEnd, Set<String> folded) {
        int count = 0;
        for (Map.Entry<String, List<String>> entry : holeCardsAtEnd.entrySet()) {
            if (isShowdownParticipant(entry.getKey(), entry.getValue(), folded)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isOffsuitHole(List<String> holeCards) {
        if (holeCards == null || holeCards.size() != 2) {
            return false;
        }
        int suit0 = suitCode(holeCards.get(0));
        int suit1 = suitCode(holeCards.get(1));
        return suit0 >= 0 && suit1 >= 0 && suit0 != suit1;
    }

    private static String resolveHandRankNameOnTurn(String nickname,
                                                    DpObservedHandRecordBO archived,
                                                    List<String> hole,
                                                    List<String> turnBoard) {
        if (archived.boardsByStreet != null) {
            for (DpObservedStreetBoardBO board : archived.boardsByStreet) {
                if (board == null || !"turn".equals(board.stage)) {
                    continue;
                }
                Map<String, String> ranks = board.handRankNameByPlayer;
                if (ranks != null && ranks.containsKey(nickname)) {
                    String name = ranks.get(nickname);
                    if (name != null && !name.isEmpty()) {
                        return name;
                    }
                }
                break;
            }
        }
        DpUtilHandEvaluator.HandStrength hs = evalHand(hole, turnBoard);
        if (hs == null) {
            return null;
        }
        return DpUtilHandEvaluator.rankCategoryNameZh(hs.rankCategory);
    }

    private static Map<String, Integer> buildNicknameToUserId(DpRoomBO roomSnapshot) {
        Map<String, Integer> out = new HashMap<>();
        if (roomSnapshot == null || roomSnapshot.getPlayers() == null) {
            return out;
        }
        for (DpPlayer player : roomSnapshot.getPlayers()) {
            if (player == null || DpNpcEngine.isBotPlayer(player)) {
                continue;
            }
            String nickname = player.getNickname();
            Integer userId = player.getDpUserId();
            if (nickname == null || nickname.isEmpty() || userId == null || userId <= 0) {
                continue;
            }
            out.put(nickname, userId);
        }
        return out;
    }
}
