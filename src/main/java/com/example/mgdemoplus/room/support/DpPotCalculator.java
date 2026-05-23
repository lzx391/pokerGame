package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.common.entity.DpPot;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 主池 / 边池分层（与 {@code DpRoomServiceImpl#calculatePots} 逻辑一致，便于单测）。
 */
public final class DpPotCalculator {

    private DpPotCalculator() {
    }

    /**
     * 根据本手累计下注 {@link DpPlayer#getTotalBet()} 与弃牌状态计算主池及边池。
     */
    public static List<DpPot> calculate(List<DpPlayer> players) {
        if (players == null || players.isEmpty()) {
            return List.of();
        }
        List<Integer> levels = players.stream()
                .map(DpPlayer::getTotalBet)
                .filter(b -> b > 0)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        List<DpPot> pots = new ArrayList<>();
        int prevLevel = 0;

        for (int level : levels) {
            int potAmount = 0;
            List<String> eligible = new ArrayList<>();

            for (DpPlayer p : players) {
                if (p == null) {
                    continue;
                }
                int contribution = Math.min(p.getTotalBet(), level) - Math.min(p.getTotalBet(), prevLevel);
                potAmount += contribution;
                if (!p.isFold() && p.getTotalBet() >= level) {
                    eligible.add(p.getNickname());
                }
            }

            if (potAmount > 0) {
                DpPot pot = new DpPot();
                pot.setAmount(potAmount);
                pot.setEligiblePlayers(eligible);
                pots.add(pot);
            }
            prevLevel = level;
        }

        return pots;
    }
}
