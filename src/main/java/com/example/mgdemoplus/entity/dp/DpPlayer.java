package com.example.mgdemoplus.entity.dp;

import java.util.ArrayList;
import java.util.List;

public class DpPlayer {
    private String nickname;
    private boolean ready = false;
    private int chips = 500;       // 初始500积分 = 50BB
    private List<String> holeCards = new ArrayList<>();
    private boolean fold = false;
    private int bet = 0;//本轮
    private boolean isDealer = false;
    private int blind = 0;         // 0无 1小盲 2大盲
    private long lastHeartBeat = System.currentTimeMillis();
    private boolean acted = false;  // 本轮是否已行动，用来解决翻前跳过BB位的bug

    private int totalBet = 0;  // 本手牌累计下注（跨阶段不清零，newHand时才清零）
    private boolean allIn = false;  // 是否已全下
    /**
     * 是否在本手牌中已主动离开（exitRoom），
     * 在本手结束前仍保留座位但视为已弃牌，开新一手时才真正清理出玩家列表。
     */
    private boolean leftThisHand = false;

    /**
     * 当前阶段下，该玩家用 2 张手牌 + 公共牌组成的最佳 5 张牌（用于前端展示“最大牌型”的牌面）。
     * 由服务端在返回房间状态时按当前公共牌计算并填充，不持久化。
     */
    private List<String> bestHandCards = new ArrayList<>();

    /**
     * 当前房间内连续赢下完整一手牌的次数（至少赢下本手任意底池的一份即计为赢）。
     * 非赢家在本手结算后归零；用于前端展示「连胜」标记。
     */
    private int winStreak = 0;

    /**
     * 机器人情绪值：范围建议在 [-1.0, 1.0] 内，
     * 用于简单模拟“连赢变得更放松，连输变得更保守”的状态。
     * 对真人玩家逻辑没有影响，只有在 NPC 决策时才会读取。
     */
    private double mood = 0.0;

    /**
     * 机器人下一次允许自动行动的时间戳（毫秒）。
     * 仅 NPC 使用，用于模拟“思考时间”：强牌更爱“长考”、弱牌/诈唬更容易秒出手。
     * 对真人玩家逻辑没有影响。
     */
    private long nextBotActionTime = 0L;

    /**
     * 当前这手牌的“整手牌计划”类型，仅 NPC 使用。
     * 取值来自内部枚举 PlanType 的 name()，例如：VALUE / BLUFF / POT_CONTROL / GIVE_UP。
     * 典型使用方式：在 flop 首次行动时写入，hand 结束或新一手开局时清空。
     */
    private String npcHandPlanType;

    /**
     * 当前整手计划还准备“主动出手”的最大街数倒计数：
     * - 常见范围：0~3；
     * - 数值越大，代表更愿意多街持续下注（例如 3-barrel bluff / value）。
     * 建议根据机器人风格与难度调整：紧凶 TAG 可设为 1~2，Shark 可设为 2~3。
     */
    private int npcHandPlanMaxBarrels;

    /**
     * 当前整手计划的翻后激进度，范围建议在 [0.0, 1.0]：
     * - 越接近 1.0：同样牌力下更偏向较大下注 / 多次下注；
     * - 越接近 0.0：更偏向 check / 小额 probing bet / 控池。
     * 默认由 NPC 引擎在 flop 初始化 HandPlan 时写入。
     */
    private double npcHandPlanAggression;

    /**
     * 本手计划重点针对的主要对手昵称，仅用于 Shark 等高级机器人微调策略或输出调试日志。
     * 可为空，纯辅助信息，不影响正常结算。
     */
    private String npcHandPlanTargetVillain;

    // getter & setter
    public boolean isActed() { return acted; }
    public void setActed(boolean acted) { this.acted = acted; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public boolean isReady() { return ready; }
    public void setReady(boolean ready) { this.ready = ready; }
    public int getChips() { return chips; }
    public void setChips(int chips) { this.chips = chips; }
    public List<String> getHoleCards() { return holeCards; }
    public void setHoleCards(List<String> holeCards) { this.holeCards = holeCards; }
    public boolean isFold() { return fold; }
    public void setFold(boolean fold) { this.fold = fold; }
    public int getBet() { return bet; }
    public void setBet(int bet) { this.bet = bet; }
    public boolean isDealer() { return isDealer; }
    public void setDealer(boolean dealer) { isDealer = dealer; }
    public int getBlind() { return blind; }
    public void setBlind(int blind) { this.blind = blind; }
    public long getLastHeartBeat() { return lastHeartBeat; }
    public void setLastHeartBeat(long lastHeartBeat) { this.lastHeartBeat = lastHeartBeat; }
    public int getTotalBet() { return totalBet; }
    public void setTotalBet(int totalBet) { this.totalBet = totalBet; }
    public boolean isAllIn() { return allIn; }
    public void setAllIn(boolean allIn) { this.allIn = allIn; }
    public boolean isLeftThisHand() { return leftThisHand; }
    public void setLeftThisHand(boolean leftThisHand) { this.leftThisHand = leftThisHand; }
    public List<String> getBestHandCards() { return bestHandCards; }
    public void setBestHandCards(List<String> bestHandCards) { this.bestHandCards = bestHandCards != null ? bestHandCards : new ArrayList<>(); }
    public int getWinStreak() { return winStreak; }
    public void setWinStreak(int winStreak) { this.winStreak = Math.max(0, winStreak); }
    public double getMood() { return mood; }
    public void setMood(double mood) { this.mood = mood; }
    public long getNextBotActionTime() { return nextBotActionTime; }
    public void setNextBotActionTime(long nextBotActionTime) { this.nextBotActionTime = nextBotActionTime; }

    public String getNpcHandPlanType() {
        return npcHandPlanType;
    }

    public void setNpcHandPlanType(String npcHandPlanType) {
        this.npcHandPlanType = npcHandPlanType;
    }

    public int getNpcHandPlanMaxBarrels() {
        return npcHandPlanMaxBarrels;
    }

    public void setNpcHandPlanMaxBarrels(int npcHandPlanMaxBarrels) {
        this.npcHandPlanMaxBarrels = npcHandPlanMaxBarrels;
    }

    public double getNpcHandPlanAggression() {
        return npcHandPlanAggression;
    }

    public void setNpcHandPlanAggression(double npcHandPlanAggression) {
        this.npcHandPlanAggression = npcHandPlanAggression;
    }

    public String getNpcHandPlanTargetVillain() {
        return npcHandPlanTargetVillain;
    }

    public void setNpcHandPlanTargetVillain(String npcHandPlanTargetVillain) {
        this.npcHandPlanTargetVillain = npcHandPlanTargetVillain;
    }
}