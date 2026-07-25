package com.example.mgdemoplus.npc.trace.model;

/** 翻前 re-raise（3bet/4bet）概率元数据，供前端深色子范围展示。 */
public class DpNpcPreflopRaiseMeta {
    /** 决策前 clamp 后的 raise 概率（已乘 pfrScale）。 */
    public double baseRaiseProb;
    /** 副矩阵 kind，如 vsOpen3BetValueAllow / vs3Bet4BetValueAllow。 */
    public String matrixKind;
    /** hero 是否在 value re-raise 范围内。 */
    public boolean valueEligible;
    /** hero 是否满足 bluff re-raise 候选（含 villain tier / 有效筹码等条件）。 */
    public boolean bluffEligible;
    /** pfr 激进度缩放系数 0.35~1.0。 */
    public double pfrScale;
}
