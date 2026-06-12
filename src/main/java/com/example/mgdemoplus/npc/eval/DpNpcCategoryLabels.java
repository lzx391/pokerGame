package com.example.mgdemoplus.npc.eval;

/**
 * 12 档成牌 / 听牌 / 翻前档的中英文标签，供 LLM 局面包 v2 使用。
 */
public final class DpNpcCategoryLabels {

    private DpNpcCategoryLabels() {
    }

    public static String madeEn(DpNpcHandSnapshot snap) {
        if (snap == null || snap.made == null) {
            return "";
        }
        return snap.made.name();
    }

    public static String madeZh(DpNpcHandSnapshot snap) {
        if (snap == null || snap.made == null) {
            return "";
        }
        return switch (snap.made) {
            case HIGH_CARD -> "高牌";
            case BOTTOM_PAIR -> "底对";
            case MIDDLE_PAIR -> "中对";
            case TOP_PAIR_WEAK_KICKER -> "顶对弱踢";
            case TOP_PAIR_TOP_KICKER -> "顶对强踢";
            case TWO_PAIR -> "两对";
            case TRIPS -> "三条";
            case STRAIGHT -> "顺子";
            case FLUSH -> "同花";
            case FULL_HOUSE -> "葫芦";
            case QUADS -> "四条";
            case ROCKET -> "皇家同花顺";
        };
    }

    public static String drawEn(DpNpcHandSnapshot snap) {
        if (snap == null || snap.draw == null || snap.draw == DpNpcDrawCategory.NONE) {
            return "NONE";
        }
        return snap.draw.name();
    }

    public static String drawZh(DpNpcHandSnapshot snap) {
        if (snap == null || snap.draw == null || snap.draw == DpNpcDrawCategory.NONE) {
            return "无听牌";
        }
        return switch (snap.draw) {
            case GUTSHOT -> "卡顺听";
            case OESD -> "两头顺听";
            case FLUSH_DRAW -> "同花听";
            case COMBO_DRAW -> "组合听";
            default -> "无听牌";
        };
    }

    public static String preflopCat(DpNpcHandSnapshot snap) {
        if (snap == null || snap.preflop == null) {
            return "";
        }
        return snap.preflop.name();
    }

    public static String boardTexCompact(DpNpcHandSnapshot snap) {
        DpBoardTexture tex = snap != null ? snap.boardTexture : null;
        if (tex == null) {
            return "";
        }
        return tex.compactToken();
    }
}
