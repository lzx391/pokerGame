package com.example.mgdemoplus.history.bo;

import java.util.List;
import java.util.Map;

/**
 * 一手结束后的完整观测记录（服务端视角：含未摊牌者底牌，便于 AI 离线分析；勿直接原样下发客户端）。
 */
public final class DpObservedHandRecordBO {
    public final String roomId;
    public final long handSeed;
    public final long startedAtMs;
    public final long endedAtMs;
    public final int smallBlindChips;
    public final int bigBlindChips;
    /** 建房带入倍数（BB）；payload v2+。 */
    public final int startingStackBb;
    public final String dealerNickname;
    public final List<DpObservedSeatAtHandStartBO> seatsAtStart;
    public final List<DpObservedStreetBoardBO> boardsByStreet;
    public final List<DpObservedHandActionRecordBO> actions;
    public final List<DpObservedPotSnapshotBO> potsBeforeSettlement;
    /**
     * 结算前「有效」底池总额：仅含至少两名玩家有资格赢取的那几层池之和。
     */
    public final int mainPotTotalBeforeSettlement;
    public final Map<String, List<String>> holeCardsAtEnd;
    public final Map<String, Integer> netChipsChange;
    /** 一手结束时的桌上筹码（payload v3+）。 */
    public final Map<String, Integer> chipsAtEnd;

    public DpObservedHandRecordBO(String roomId, long handSeed, long startedAtMs, long endedAtMs,
                                   int smallBlindChips, int bigBlindChips, String dealerNickname,
                                   List<DpObservedSeatAtHandStartBO> seatsAtStart,
                                   List<DpObservedStreetBoardBO> boardsByStreet,
                                   List<DpObservedHandActionRecordBO> actions,
                                   List<DpObservedPotSnapshotBO> potsBeforeSettlement,
                                   int mainPotTotalBeforeSettlement,
                                   Map<String, List<String>> holeCardsAtEnd,
                                   Map<String, Integer> netChipsChange) {
        this(roomId, handSeed, startedAtMs, endedAtMs, smallBlindChips, bigBlindChips, 0, dealerNickname,
                seatsAtStart, boardsByStreet, actions, potsBeforeSettlement, mainPotTotalBeforeSettlement,
                holeCardsAtEnd, netChipsChange, Map.of());
    }

    public DpObservedHandRecordBO(String roomId, long handSeed, long startedAtMs, long endedAtMs,
                                   int smallBlindChips, int bigBlindChips, int startingStackBb,
                                   String dealerNickname,
                                   List<DpObservedSeatAtHandStartBO> seatsAtStart,
                                   List<DpObservedStreetBoardBO> boardsByStreet,
                                   List<DpObservedHandActionRecordBO> actions,
                                   List<DpObservedPotSnapshotBO> potsBeforeSettlement,
                                   int mainPotTotalBeforeSettlement,
                                   Map<String, List<String>> holeCardsAtEnd,
                                   Map<String, Integer> netChipsChange) {
        this(roomId, handSeed, startedAtMs, endedAtMs, smallBlindChips, bigBlindChips, startingStackBb,
                dealerNickname, seatsAtStart, boardsByStreet, actions, potsBeforeSettlement,
                mainPotTotalBeforeSettlement, holeCardsAtEnd, netChipsChange, Map.of());
    }

    public DpObservedHandRecordBO(String roomId, long handSeed, long startedAtMs, long endedAtMs,
                                   int smallBlindChips, int bigBlindChips, int startingStackBb,
                                   String dealerNickname,
                                   List<DpObservedSeatAtHandStartBO> seatsAtStart,
                                   List<DpObservedStreetBoardBO> boardsByStreet,
                                   List<DpObservedHandActionRecordBO> actions,
                                   List<DpObservedPotSnapshotBO> potsBeforeSettlement,
                                   int mainPotTotalBeforeSettlement,
                                   Map<String, List<String>> holeCardsAtEnd,
                                   Map<String, Integer> netChipsChange,
                                   Map<String, Integer> chipsAtEnd) {
        this.roomId = roomId == null ? "" : roomId;
        this.handSeed = handSeed;
        this.startedAtMs = startedAtMs;
        this.endedAtMs = endedAtMs;
        this.smallBlindChips = smallBlindChips;
        this.bigBlindChips = bigBlindChips;
        this.startingStackBb = startingStackBb;
        this.dealerNickname = dealerNickname == null ? "" : dealerNickname;
        this.seatsAtStart = List.copyOf(seatsAtStart);
        this.boardsByStreet = List.copyOf(boardsByStreet);
        this.actions = List.copyOf(actions);
        this.potsBeforeSettlement = List.copyOf(potsBeforeSettlement);
        this.mainPotTotalBeforeSettlement = mainPotTotalBeforeSettlement;
        this.holeCardsAtEnd = Map.copyOf(holeCardsAtEnd);
        this.netChipsChange = Map.copyOf(netChipsChange);
        this.chipsAtEnd = chipsAtEnd == null ? Map.of() : Map.copyOf(chipsAtEnd);
    }
}
