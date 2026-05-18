package com.example.mgdemoplus.npc.llm;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;

/**
 * BOT_LLM user 局面包唯一出口：固定「键表 + ---- + 值表」字节级模板，便于方舟前缀缓存。
 * 规则与教学不在此重复，见 {@link DpLlmNpcDecisionService} 内 {@code LLM_SYSTEM_PROMPT}。
 */
public final class LlmNpcUserSnapshot {

    private static final Logger LOG = LoggerFactory.getLogger(LlmNpcUserSnapshot.class);

    static final String VERSION_HEADER = "v1|BOT_LLM|snap\n";
    /** 与 {@linkplain #VERSION_HEADER} 同源键表；仅标识不同，便于多轮 bot 与人类阅读区分。 */
    static final String VERSION_HEADER_GLOBAL = "v1|BOT_LLM_GLOBAL|snap\n";

    /**
     * 键名表：行数与值表一一对应；禁止增删行或改顺序（除非同步改值生成与文档）。
     */
    static final String KEYS_BLOCK = """
            stage_en
            hero_seat_label_zh
            small_blind_chips
            big_blind_chips
            raise_level_rl
            pot_chips
            call_amount_chips
            pot_after_call_chips
            hero_stack_chips
            sb_player_nick
            bb_player_nick
            btn_player_nick
            hero_nick
            abstract_pos_bucket_en
            abstract_pos_bucket_zh
            spr
            equity_gate_rule_tag
            equity_gate_impl_ratio
            pot_odds_value
            pot_odds_rule_tag
            equity_estimate
            hole_cards
            board
            hsl_en
            rk_en
            rk_zh
            aggressor_nick
            villain_style_tier_en
            credibility_en
            credibility_zh
            showdown_bluffiness
            active_villains
            tight_behind
            deep_behind
            short_behind
            counter_strategy_kw
            squeeze_risk_esc
            villain_stack_min
            villain_stack_max
            villain_stack_avg
            villain_stack_min_bb
            villain_stack_max_bb
            villain_stack_avg_bb
            multiway_summary_esc
            street_action_esc
            """.stripIndent();

    static final String VALUES_SEPARATOR = "----\n";

    /**
     * 固定头+键表+分隔符长度（UTF-16 代码单元，与 {@link String#length()} 一致）；值表从此之后开始变化。
     */
    public static final int STABLE_PREFIX_LENGTH = VERSION_HEADER.length() + KEYS_BLOCK.length()
            + VALUES_SEPARATOR.length();

    /** 前缀缓存哈希/长度指标：BOT_LLM 与 BOT_LLM_GLOBAL 头长度不同。 */
    public static int stablePrefixLength(boolean globalVariant) {
        String head = globalVariant ? VERSION_HEADER_GLOBAL : VERSION_HEADER;
        return head.length() + KEYS_BLOCK.length() + VALUES_SEPARATOR.length();
    }

    private LlmNpcUserSnapshot() {
    }

    /** 完整 user 正文：固定头 + 键表 + 分隔符 + 值行（每行一条，顺序与键表一致）。 */
    public static String formatUserPayload(DpRoomBO room, DpPlayer bot, LlmNpcGameContext ctx) {
        return formatUserPayload(room, bot, ctx, false);
    }

    /** @param globalVariant {@code true} 时使用 {@link #VERSION_HEADER_GLOBAL} */
    public static String formatUserPayload(DpRoomBO room, DpPlayer bot, LlmNpcGameContext ctx,
            boolean globalVariant) {
        StringBuilder sb = new StringBuilder(1800);
        sb.append(globalVariant ? VERSION_HEADER_GLOBAL : VERSION_HEADER);
        sb.append(KEYS_BLOCK);
        sb.append(VALUES_SEPARATOR);
        appendValueLines(sb, room, bot, ctx);
        return sb.toString();
    }

    static void appendValueLines(StringBuilder sb, DpRoomBO room, DpPlayer bot, LlmNpcGameContext ctx) {
        LlmNpcGameContext c = ctx != null ? ctx : emptyContextFallback(room, bot);
        int callAmount = Math.max(0, room.getCurrentBetToCall() - bot.getBet());
        int potNow = room.getPot();
        int potAfter = potNow + callAmount;
        String stageEn = nz(room.getCurrentStage());
        String seatZh = heroSeatLabelZh(room, bot);
        String posEn = nz(c.getTablePosition());
        String posZh = zhTablePosition(posEn);
        double spr = potNow > 0 ? (bot.getChips() * 1.0 / potNow) : 0.0;

        String gateTag = callAmount <= 0 ? "PAY0_NO_FOLD_GATE" : "PAY_GT0_USE_IMPL";
        double implRatio;
        if (callAmount <= 0) {
            implRatio = 0.0;
        } else {
            int denom = potNow + callAmount;
            implRatio = denom > 0 ? (callAmount * 1.0 / denom) : 0.0;
        }

        String potOddsTag = callAmount > 0 ? "ON" : "OFF";
        String board = c.getCommunityCardsText().isEmpty() ? "—" : escLine(c.getCommunityCardsText());
        String hole = c.getHoleCardsText().isEmpty() ? "—" : escLine(c.getHoleCardsText());
        String hsl = c.getHandStrengthLine().isEmpty() ? "—" : escLine(c.getHandStrengthLine());
        String rkEn = nz(c.getSimpleStrength());
        String rkZh = zhRk(rkEn);
        String agg = c.getAggressorNickname().isEmpty() ? "—" : escLine(c.getAggressorNickname());
        String tier = nz(c.getVillainRangeTier());
        String credEn = nz(c.getActionCredibility());
        String credZh = zhCred(credEn);
        String ctr = c.getCounterStrategySummary().isEmpty() ? "—" : escLine(c.getCounterStrategySummary());

        BlindSeatNames blinds = resolveBlindSeatNames(room);

        appendLn(sb, stageEn);
        appendLn(sb, escLine(seatZh));
        appendLn(sb, String.valueOf(room.getSmallBlindChips()));
        appendLn(sb, String.valueOf(room.getBigBlindChips()));
        appendLn(sb, String.valueOf(room.getRaiseLevel()));
        appendLn(sb, String.valueOf(potNow));
        appendLn(sb, String.valueOf(callAmount));
        appendLn(sb, String.valueOf(potAfter));
        appendLn(sb, String.valueOf(bot.getChips()));
        appendLn(sb, blinds == null ? "—" : escLine(blinds.sbNick()));
        appendLn(sb, blinds == null ? "—" : escLine(blinds.bbNick()));
        appendLn(sb, escLine(getDealerPlayerNickname(room)));
        appendLn(sb, escLine(nz(bot.getNickname())));
        appendLn(sb, posEn.isEmpty() ? "—" : escLine(posEn));
        appendLn(sb, escLine(posZh));
        appendLn(sb, String.format(Locale.ROOT, "%.4f", spr));
        appendLn(sb, gateTag);
        appendLn(sb, callAmount <= 0 ? "—" : String.format(Locale.ROOT, "%.6f", implRatio));
        appendLn(sb, String.format(Locale.ROOT, "%.4f", c.getPotOdds()));
        appendLn(sb, potOddsTag);
        if (callAmount > 0) {
            double ctxPo = c.getPotOdds();
            if (Math.abs(ctxPo - implRatio) > 1e-5) {
                LOG.warn(
                        "[BOT_LLM] pot_odds_value 与同窗重算 implRatio 不一致：ctx={} derived={} pot={} call={}",
                        ctxPo, implRatio, potNow, callAmount);
            }
        }
        appendLn(sb, String.format(Locale.ROOT, "%.4f", c.getEquityEstimate()));
        appendLn(sb, hole);
        appendLn(sb, board);
        appendLn(sb, hsl);
        appendLn(sb, rkEn.isEmpty() ? "—" : escLine(rkEn));
        appendLn(sb, escLine(rkZh));
        appendLn(sb, agg);
        appendLn(sb, tier.isEmpty() ? "—" : escLine(tier));
        appendLn(sb, credEn.isEmpty() ? "—" : escLine(credEn));
        appendLn(sb, escLine(credZh));
        appendLn(sb, String.format(Locale.ROOT, "%.4f", c.getShowdownBluffiness()));
        appendLn(sb, String.valueOf(c.getActiveVillains()));
        appendLn(sb, String.valueOf(c.getTightBehindCount()));
        appendLn(sb, String.valueOf(c.getDeepBehindCount()));
        appendLn(sb, String.valueOf(c.getShortBehindCount()));
        appendLn(sb, ctr);
        appendLn(sb, escLine(squeezeLine(c)));
        appendLn(sb, String.valueOf(c.getVillainMinStackChips()));
        appendLn(sb, String.valueOf(c.getVillainMaxStackChips()));
        appendLn(sb, String.valueOf(c.getVillainAvgStackChips()));
        appendLn(sb, String.format(Locale.ROOT, "%.4f", c.getVillainMinStackBb()));
        appendLn(sb, String.format(Locale.ROOT, "%.4f", c.getVillainMaxStackBb()));
        appendLn(sb, String.format(Locale.ROOT, "%.4f", c.getVillainAvgStackBb()));
        String multi = c.getMultiwayVillainsSummary();
        appendLn(sb, multi.isBlank() || "(无)".equals(multi.trim()) ? "—" : escLine(multi));
        appendLn(sb, escLine(buildStreetActionOneLine(room, bot, callAmount)));

        int ctxCall = c.getCallAmountChips();
        if (ctxCall != callAmount) {
            LOG.warn("[BOT_LLM] 同食快照下 call_amount 不一致 ctx={} roomDerived={}（请查 buildLlmNpcGameSnapshot 与 appendValueLines 调用链）",
                    ctxCall, callAmount);
        }
    }

    private static LlmNpcGameContext emptyContextFallback(DpRoomBO room, DpPlayer bot) {
        return new LlmNpcGameContext(
                nz(room.getCurrentStage()),
                room.getPot(),
                Math.max(0, room.getCurrentBetToCall() - bot.getBet()),
                bot.getChips(),
                nz(bot.getNickname()),
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                0.0,
                0.0,
                0.0,
                0,
                0,
                0,
                0.0,
                0.0,
                0.0,
                0,
                "(无)",
                0,
                0,
                0,
                "NEUTRAL",
                "");
    }

    private static String squeezeLine(LlmNpcGameContext c) {
        String s = c.getSqueezeRiskSummary();
        return s.isEmpty() ? "—" : s;
    }

    private static void appendLn(StringBuilder sb, String line) {
        sb.append(line).append('\n');
    }

    /** 行内转义：避免值表换行错乱；空用「—」。 */
    static String escLine(String s) {
        if (s == null || s.isEmpty()) {
            return "—";
        }
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String heroSeatLabelZh(DpRoomBO room, DpPlayer bot) {
        List<DpPlayer> ps = room.getPlayers();
        if (ps == null) {
            return "—";
        }
        int idx = ps.indexOf(bot);
        if (idx < 0) {
            return "—";
        }
        if (bot.isDealer()) {
            return "按钮位(BTN)";
        }
        int did = room.getLastDealerIndex();
        if (did >= 0 && ps.size() >= 2) {
            int sbIdx = (did + 1) % ps.size();
            int bbIdx = (did + 2) % ps.size();
            if (idx == sbIdx) {
                return "小盲位(SB)";
            }
            if (idx == bbIdx) {
                return "大盲位(BB)";
            }
            int dist = (idx - did + ps.size()) % ps.size();
            if (dist <= 2) {
                return "前位";
            }
            if (dist <= ps.size() - 2) {
                return "中位";
            }
            return "后位";
        }
        return "—";
    }

    private static String getDealerPlayerNickname(DpRoomBO room) {
        List<DpPlayer> ps = room.getPlayers();
        if (ps == null) {
            return "—";
        }
        for (DpPlayer p : ps) {
            if (p != null && p.isDealer()) {
                String nick = p.getNickname();
                return nick != null ? nick : "—";
            }
        }
        return "—";
    }

    private record BlindSeatNames(String sbNick, String bbNick) {}

    private static BlindSeatNames resolveBlindSeatNames(DpRoomBO room) {
        if (room == null) {
            return null;
        }
        List<DpPlayer> ps = room.getPlayers();
        if (ps == null || ps.size() < 2) {
            return null;
        }
        int did = room.getLastDealerIndex();
        if (did < 0 || did >= ps.size()) {
            return null;
        }
        int sbIdx = (did + 1) % ps.size();
        int bbIdx = (did + 2) % ps.size();
        DpPlayer sbp = ps.get(sbIdx);
        DpPlayer bbp = ps.get(bbIdx);
        String sbn = sbp != null && sbp.getNickname() != null ? sbp.getNickname() : "—";
        String bbn = bbp != null && bbp.getNickname() != null ? bbp.getNickname() : "—";
        return new BlindSeatNames(sbn, bbn);
    }

    /**
     * 本街行动流水：单行，段落以 {@code | } 分隔。数值口径统一为「本街已投入筹码」与
     * {@code currentBetToCall}（面对档位）；你还须支付仅以 {@code call_amount_chips} 为准，避免把他人注额误当跟注额。
     *
     * @param callAmount 与局面包 {@code call_amount_chips} 相同快照下算出：{@code max(0, betToCall - hero.bet)}
     */
    private static String buildStreetActionOneLine(DpRoomBO room, DpPlayer hero, int callAmount) {
        StringBuilder sb = new StringBuilder();
        List<DpPlayer> ps = room.getPlayers();
        if (ps == null || ps.isEmpty()) {
            return "—";
        }
        int n = ps.size();
        int did = room.getLastDealerIndex();
        if (did < 0 || did >= n) {
            did = 0;
        }
        int bbIdx = n >= 2 ? (did + 2) % n : -1;
        DpPlayer bbPlayer = bbIdx >= 0 ? ps.get(bbIdx) : null;
        int start = (did + 1) % n;
        String stage = room.getCurrentStage();
        int bbChips = Math.max(1, room.getBigBlindChips());
        int betToCall = room.getCurrentBetToCall();
        int heroStreetBet = hero != null ? hero.getBet() : 0;
        boolean first = true;
        for (int i = 0; i < n; i++) {
            int idx = (start + i) % n;
            DpPlayer p = ps.get(idx);
            if (p == null || p.isLeftThisHand()) {
                continue;
            }
            if (!first) {
                sb.append(" | ");
            }
            if (p == hero) {
                sb.append(">>ACT_HERO<<·本街已下").append(heroStreetBet)
                        .append("·面对档位").append(betToCall)
                        .append("·还须支付").append(callAmount);
                first = false;
                continue;
            }
            String actionDesc;
            if (p.isFold()) {
                actionDesc = "已弃牌(本街)";
            } else if (p.isAllIn()) {
                int b = p.getBet();
                actionDesc = b > 0 ? ("全下·本街贡献" + b) : "全下·本街贡献0(侧池)";
            } else if (p.getBet() > 0) {
                boolean preflopBbPost = "preflop".equals(stage)
                        && p == bbPlayer
                        && p.getBet() == bbChips
                        && betToCall <= bbChips;
                if (preflopBbPost) {
                    actionDesc = "下大盲(" + bbChips + ")";
                } else {
                    actionDesc = "本街贡献" + p.getBet();
                }
            } else if (p.isActed()) {
                actionDesc = "过牌(本街)";
            } else {
                actionDesc = "尚未行动(本街)";
            }
            sb.append(p.getNickname()).append(": ").append(actionDesc);
            first = false;
        }
        return sb.length() == 0 ? "—" : sb.toString();
    }

    private static String zhTablePosition(String pos) {
        if (pos == null || pos.isEmpty()) {
            return "—";
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
        if (rk == null || rk.isEmpty() || "—".equals(rk)) {
            return "—";
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
            return "—";
        }
        return switch (c) {
            case "LOW" -> "偏低（线路偏怪异或不稳定）";
            case "MEDIUM" -> "中等";
            case "HIGH" -> "偏高（更像扎实价值）";
            default -> c;
        };
    }
}
