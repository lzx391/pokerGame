package com.example.mgdemoplus.entity;

/**
 * 表 dp_shark_opponent_profile（MyBatis 读写）。
 */
public class DpSharkOpponentProfile {
    private String playerNickname;
    private String statsJson;
    private String learnedJson;
    private Integer payloadVersion;

    public String getPlayerNickname() {
        return playerNickname;
    }

    public void setPlayerNickname(String playerNickname) {
        this.playerNickname = playerNickname;
    }

    public String getStatsJson() {
        return statsJson;
    }

    public void setStatsJson(String statsJson) {
        this.statsJson = statsJson;
    }

    public String getLearnedJson() {
        return learnedJson;
    }

    public void setLearnedJson(String learnedJson) {
        this.learnedJson = learnedJson;
    }

    public Integer getPayloadVersion() {
        return payloadVersion;
    }

    public void setPayloadVersion(Integer payloadVersion) {
        this.payloadVersion = payloadVersion;
    }
}
