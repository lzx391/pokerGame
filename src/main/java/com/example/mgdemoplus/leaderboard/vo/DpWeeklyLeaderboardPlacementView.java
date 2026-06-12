package com.example.mgdemoplus.leaderboard.vo;

import java.math.BigDecimal;

/**
 * 某用户在某周榜上的位次与倍数（与 {@code /dp/leaderboard/weekly/*} 的 myRank / myMultiplier 语义一致）。
 */
public class DpWeeklyLeaderboardPlacementView {

    /** {@code hand} 或 {@code room} */
    private String board;
    /** 当前自然周周一，ISO 日期 */
    private String weekMonday;
    /** 竞赛排名（1 起）；未上榜为 null */
    private Integer rank;
    /** 本周最佳倍数；无有效记录为 null */
    private BigDecimal multiplier;

    public String getBoard() {
        return board;
    }

    public void setBoard(String board) {
        this.board = board;
    }

    public String getWeekMonday() {
        return weekMonday;
    }

    public void setWeekMonday(String weekMonday) {
        this.weekMonday = weekMonday;
    }

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

    public BigDecimal getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(BigDecimal multiplier) {
        this.multiplier = multiplier;
    }
}
