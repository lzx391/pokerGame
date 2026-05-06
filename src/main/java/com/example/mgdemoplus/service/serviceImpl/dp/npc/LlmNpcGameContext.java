package com.example.mgdemoplus.service.serviceImpl.dp.npc;

import java.util.Locale;
import java.util.Objects;

/**
 * 供 {@link LlmNpc} 使用的对局摘要：由 {@code DpLlmNpcContextMapper}
 * 从 {@code DpUtilSmartContext} 与房间状态拼装，避免 {@code npc} 包直接依赖引擎内部类型。
 */
public final class LlmNpcGameContext {

    private final String stage;
    private final int potChips;
    private final int callAmountChips;
    private final int heroChips;
    private final String heroNickname;
    private final String communityCardsText;
    private final String holeCardsText;
    private final String tablePosition;
    private final String simpleStrength;
    /** 服务器从七张牌算出的最佳成牌紧凑标签（英文 token），与大模型自检结果冲突时以此为真。 */
    private final String handStrengthLine;
    private final String aggressorNickname;
    private final String villainRangeTier;
    private final String actionCredibility;
    private final double showdownBluffiness;
    private final double potOdds;
    private final double equityEstimate;
    private final int villainMinStackChips;
    private final int villainMaxStackChips;
    private final int villainAvgStackChips;
    private final double villainMinStackBb;
    private final double villainMaxStackBb;
    private final double villainAvgStackBb;
    private final int activeVillains;
    private final String multiwayVillainsSummary;
    private final int tightBehindCount;
    private final int deepBehindCount;
    private final int shortBehindCount;
    private final String counterStrategySummary;
    /** 身后激进短码等对「平跟后被挤压」风险的简述（服务端推算）。 */
    private final String squeezeRiskSummary;

    /**
     * 构造函数，初始化所有字段
     */
    public LlmNpcGameContext(
            String stage,
            int potChips,
            int callAmountChips,
            int heroChips,
            String heroNickname,
            String communityCardsText,
            String holeCardsText,
            String tablePosition,
            String simpleStrength,
            String handStrengthLine,
            String aggressorNickname,
            String villainRangeTier,
            String actionCredibility,
            double showdownBluffiness,
            double potOdds,
            double equityEstimate,
            int villainMinStackChips,
            int villainMaxStackChips,
            int villainAvgStackChips,
            double villainMinStackBb,
            double villainMaxStackBb,
            double villainAvgStackBb,
            int activeVillains,
            String multiwayVillainsSummary,
            int tightBehindCount,
            int deepBehindCount,
            int shortBehindCount,
            String counterStrategySummary,
            String squeezeRiskSummary) {
        this.stage = stage != null ? stage : "";
        this.potChips = potChips;
        this.callAmountChips = callAmountChips;
        this.heroChips = heroChips;
        this.heroNickname = heroNickname != null ? heroNickname : "";
        this.communityCardsText = communityCardsText != null ? communityCardsText : "";
        this.holeCardsText = holeCardsText != null ? holeCardsText : "";
        this.tablePosition = tablePosition != null ? tablePosition : "";
        this.simpleStrength = simpleStrength != null ? simpleStrength : "";
        this.handStrengthLine = handStrengthLine != null ? handStrengthLine : "";
        this.aggressorNickname = aggressorNickname != null ? aggressorNickname : "";
        this.villainRangeTier = villainRangeTier != null ? villainRangeTier : "";
        this.actionCredibility = actionCredibility != null ? actionCredibility : "";
        this.showdownBluffiness = showdownBluffiness;
        this.potOdds = potOdds;
        this.equityEstimate = equityEstimate;
        this.villainMinStackChips = villainMinStackChips;
        this.villainMaxStackChips = villainMaxStackChips;
        this.villainAvgStackChips = villainAvgStackChips;
        this.villainMinStackBb = villainMinStackBb;
        this.villainMaxStackBb = villainMaxStackBb;
        this.villainAvgStackBb = villainAvgStackBb;
        this.activeVillains = activeVillains;
        this.multiwayVillainsSummary = multiwayVillainsSummary != null ? multiwayVillainsSummary : "";
        this.tightBehindCount = tightBehindCount;
        this.deepBehindCount = deepBehindCount;
        this.shortBehindCount = shortBehindCount;
        this.counterStrategySummary = counterStrategySummary != null ? counterStrategySummary : "";
        this.squeezeRiskSummary = squeezeRiskSummary != null ? squeezeRiskSummary : "";
    }

    /**
     * 中文结构化局面正文（由 smartContext 映射字段拼装，不改变情报来源）。
     * hsl 仍为服务端英文标签，便于与评测器一致。
     */
    public String toPromptBlock() {
        String board = communityCardsText == null || communityCardsText.isEmpty() ? "（尚无）" : communityCardsText;
        String hole = holeCardsText == null || holeCardsText.isEmpty() ? "（尚无）" : holeCardsText;
        String hsl = handStrengthLine == null || handStrengthLine.isEmpty() ? "—" : handStrengthLine;
        String agg = aggressorNickname.isEmpty() ? "无" : aggressorNickname;
        String ctr = counterStrategySummary.isEmpty() ? "无" : counterStrategySummary;
        String stageZh = zhStage(stage);
        String posZh = zhTablePosition(tablePosition);
        String rkZh = zhRk(simpleStrength);
        String credZh = zhCred(actionCredibility);

        double spr = potChips > 0 ? (heroChips * 1.0 / potChips) : 0.0;
        String implExplain;
        if (callAmountChips <= 0) {
            implExplain = "不适用（本轮你还须支付为 0：底池赔率列的 0 表示不要拿去和胜率估计比弃跟）";
        } else {
            int denom = potChips + callAmountChips;
            double impl = denom > 0 ? (callAmountChips * 1.0 / denom) : 0.0;
            implExplain = String.format(Locale.ROOT,
                    "%.4f（门槛含义：长期要能获利，胜率一般需不低于该比例；应与下行「底池赔率」一致）",
                    impl);
        }
        String potOddsExplain = callAmountChips > 0
                ? "可与「胜率估计」对照：若底池赔率明显高于胜率估计，跟注多偏亏损。"
                : "本轮不适用（你还须支付为 0）。";

        StringBuilder sb = new StringBuilder(900);
        sb.append("【街道】").append(stageZh).append('\n');
        sb.append("【你是】").append(heroNickname).append("｜抽象位置：").append(posZh).append("（仅供参考）\n");
        sb.append("【底池与支付】当前底池=").append(potChips)
                .append("｜你还须支付才能跟满本轮最高注=").append(callAmountChips)
                .append("｜你剩余后手=").append(heroChips).append('\n');
        sb.append(String.format(Locale.ROOT, "【筹码深度】SPR≈%.2f（你的后手÷当前底池；底池为 0 时记 0）\n", spr));
        sb.append("【跟注胜率门槛】").append(implExplain).append('\n');
        sb.append(String.format(Locale.ROOT, "【底池赔率】%.2f｜%s\n", potOdds, potOddsExplain));
        sb.append(String.format(Locale.ROOT,
                "【胜率估计】%.2f（服务端估算：rk 四档 + 翻前手牌结构 + 翻后成牌类型校正；非蒙特卡洛精确胜率）\n",
                equityEstimate));
        sb.append("【你的底牌】").append(hole).append('\n');
        sb.append("【公共牌】").append(board).append('\n');
        sb.append("【服务端认定的最佳成牌英文标签】").append(hsl).append("（以此为准，勿自行推翻）\n");
        sb.append("【强度桶 rk】").append(simpleStrength).append("｜").append(rkZh).append('\n');
        sb.append("【PAIR_OF_x 提示】若标签为 PAIR_OF_某点且你的底牌不含该点，多为「板对弱踢脚」，勿口述成顶对。\n");
        sb.append("【最近下注或加注者】").append(agg).append('\n');
        sb.append("【主对手风格档】").append(villainRangeTier).append("（原文勿改写成口语绰号）\n");
        sb.append(String.format(Locale.ROOT, "【线路可信度】%s｜%s｜【摊牌诈唬偏好】%.2f\n",
                actionCredibility, credZh, showdownBluffiness));
        sb.append("【仍在局的活跃对手人数】").append(activeVillains).append('\n');
        sb.append("【你身后人数】紧凶=").append(tightBehindCount)
                .append("｜深码=").append(deepBehindCount)
                .append("｜短码=").append(shortBehindCount).append('\n');
        sb.append("【对策关键词】").append(ctr).append('\n');
        sb.append("【挤压风险】").append(squeezeRiskSummary.isEmpty() ? "（无）" : squeezeRiskSummary).append('\n');
        boolean hasStacks = villainMinStackChips > 0 || villainMaxStackChips > 0 || villainAvgStackChips > 0;
        if (hasStacks) {
            sb.append(String.format(Locale.ROOT,
                    "【对手后手概况】最低=%d｜最高=%d｜平均=%d（筹码；BB 口径最低=%.1f 最高=%.1f 平均=%.1f）\n",
                    villainMinStackChips,
                    villainMaxStackChips,
                    villainAvgStackChips,
                    villainMinStackBb,
                    villainMaxStackBb,
                    villainAvgStackBb));
        }
        if (multiwayVillainsSummary != null && !multiwayVillainsSummary.isBlank() && !"(无)".equals(
                multiwayVillainsSummary.trim())) {
            sb.append("【多路对手摘要】\n").append(multiwayVillainsSummary).append('\n');
        }
        return sb.toString();
    }

    private static String zhStage(String st) {
        if (st == null || st.isEmpty()) {
            return "未知";
        }
        return switch (st) {
            case "preflop" -> "翻前";
            case "flop" -> "翻牌";
            case "turn" -> "转牌";
            case "river" -> "河牌";
            default -> st;
        };
    }

    private static String zhTablePosition(String pos) {
        if (pos == null || pos.isEmpty()) {
            return "未知";
        }
        return switch (pos) {
            case "EARLY" -> "前位";
            case "MIDDLE" -> "中位";
            case "LATE" -> "后位";
            case "BLINDS" -> "盲注位";
            default -> pos;
        };
    }

    private static String zhRk(String rk) {
        if (rk == null || rk.isEmpty()) {
            return "未知桶";
        }
        return switch (rk) {
            case "WEAK" -> "弱";
            case "MEDIUM" -> "中";
            case "STRONG" -> "强";
            case "MONSTER" -> "极强";
            default -> rk;
        };
    }

    private static String zhCred(String c) {
        if (c == null || c.isEmpty()) {
            return "未知";
        }
        return switch (c) {
            case "LOW" -> "偏低（线路偏怪异或不稳定）";
            case "MEDIUM" -> "中等";
            case "HIGH" -> "偏高（更像扎实价值）";
            default -> c;
        };
    }

    /**
     * 判断两个对象的字段值是否全部相等
     * @param o 传入的比较对象
     * @return 相等返回true，否则false
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        LlmNpcGameContext that = (LlmNpcGameContext) o;
        return potChips == that.potChips
                && callAmountChips == that.callAmountChips
                && heroChips == that.heroChips
                && Double.compare(that.showdownBluffiness, showdownBluffiness) == 0
                && Double.compare(that.potOdds, potOdds) == 0
                && Double.compare(that.equityEstimate, equityEstimate) == 0
                && villainMinStackChips == that.villainMinStackChips
                && villainMaxStackChips == that.villainMaxStackChips
                && villainAvgStackChips == that.villainAvgStackChips
                && Double.compare(that.villainMinStackBb, villainMinStackBb) == 0
                && Double.compare(that.villainMaxStackBb, villainMaxStackBb) == 0
                && Double.compare(that.villainAvgStackBb, villainAvgStackBb) == 0
                && activeVillains == that.activeVillains
                && tightBehindCount == that.tightBehindCount
                && deepBehindCount == that.deepBehindCount
                && shortBehindCount == that.shortBehindCount
                && Objects.equals(stage, that.stage)
                && Objects.equals(heroNickname, that.heroNickname)
                && Objects.equals(communityCardsText, that.communityCardsText)
                && Objects.equals(holeCardsText, that.holeCardsText)
                && Objects.equals(tablePosition, that.tablePosition)
                && Objects.equals(simpleStrength, that.simpleStrength)
                && Objects.equals(handStrengthLine, that.handStrengthLine)
                && Objects.equals(aggressorNickname, that.aggressorNickname)
                && Objects.equals(villainRangeTier, that.villainRangeTier)
                && Objects.equals(actionCredibility, that.actionCredibility)
                && Objects.equals(multiwayVillainsSummary, that.multiwayVillainsSummary)
                && Objects.equals(counterStrategySummary, that.counterStrategySummary)
                && Objects.equals(squeezeRiskSummary, that.squeezeRiskSummary);
    }

    /**
     * 计算对象的hash值，便于作为key存储等
     * @return hash值
     */
    @Override
    public int hashCode() {
        return Objects.hash(
                stage, potChips, callAmountChips, heroChips, heroNickname,
                communityCardsText, holeCardsText, tablePosition, simpleStrength, handStrengthLine,
                aggressorNickname, villainRangeTier, actionCredibility,
                showdownBluffiness, potOdds, equityEstimate,
                villainMinStackChips, villainMaxStackChips, villainAvgStackChips,
                villainMinStackBb, villainMaxStackBb, villainAvgStackBb,
                activeVillains, multiwayVillainsSummary,
                tightBehindCount, deepBehindCount, shortBehindCount,
                counterStrategySummary, squeezeRiskSummary);
    }
}
