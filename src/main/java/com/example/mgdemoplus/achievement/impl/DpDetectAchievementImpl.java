package com.example.mgdemoplus.achievement.impl;

import com.example.mgdemoplus.achievement.DpAchievementService;
import com.example.mgdemoplus.achievement.DpDetectAchievement;
import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.history.bo.DpObservedHandActionRecordBO;
import com.example.mgdemoplus.history.bo.DpObservedHandRecordBO;
import com.example.mgdemoplus.history.bo.DpObservedStreetBoardBO;
import com.example.mgdemoplus.history.types.DpObservedHandActionType;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
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

    @Autowired
    private DpAchievementService dpAchievementService;

    @Override
    public void detect(DpSettlePersistJob job) {
        if (job == null || job.archived() == null) {
            return;
        }
        detectQuadNightmare(job.archived(), job.roomSnapshotForParticipants());
    }

    /**
     * 四条噩梦：仅摊牌参与者可解锁。摊牌者 = {@code holeCardsAtEnd} 中底牌非空且本局未 FOLD 的玩家。
     * 归档时弃牌者仍可能留有底牌副本，不能仅凭 map 键存在判定。
     */
    private void detectQuadNightmare(DpObservedHandRecordBO archived, DpRoomBO roomSnapshot) {
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
            dpAchievementService.unlockIfAbsent(userId, DpAchievementService.CODE_QUAD_NIGHTMARE);
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
