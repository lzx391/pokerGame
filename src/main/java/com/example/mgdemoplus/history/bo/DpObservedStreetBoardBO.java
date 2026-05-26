package com.example.mgdemoplus.history.bo;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 某一街的公共牌快照。
 */
public final class DpObservedStreetBoardBO {
    public final String stage;
    public final List<String> communityCards;
    /** 该街结束时各玩家最佳牌型中文大类（如「顺子」），仅展示文案；翻前或无足够公共牌时为空。 */
    public final Map<String, String> handRankNameByPlayer;

    public DpObservedStreetBoardBO(String stage, List<String> communityCards) {
        this(stage, communityCards, Map.of());
    }

    public DpObservedStreetBoardBO(String stage, List<String> communityCards,
                                   Map<String, String> handRankNameByPlayer) {
        this.stage = stage == null ? "" : stage;
        this.communityCards = communityCards == null
                ? List.of()
                : List.copyOf(communityCards);
        if (handRankNameByPlayer == null || handRankNameByPlayer.isEmpty()) {
            this.handRankNameByPlayer = Map.of();
        } else {
            this.handRankNameByPlayer = Collections.unmodifiableMap(new LinkedHashMap<>(handRankNameByPlayer));
        }
    }
}
