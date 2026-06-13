package com.example.mgdemoplus.npc.trace.model;

import java.util.ArrayList;
import java.util.List;

/** 一手牌 TAG 决策 trace 包（sealed 或进行中）。 */
public class DpNpcHandTraceBundle {
    public String roomId;
    public long handSeed;
    /** true 表示进行中手，尚未 seal。 */
    public boolean inProgress;
    public long sealedAtMs;
    public int handIndex;
    public String dealerNickname;
    public int smallBlind;
    public int bigBlind;
    public int actionCount;
    public List<DpNpcActionTraceSummary> actions = new ArrayList<>();
}
