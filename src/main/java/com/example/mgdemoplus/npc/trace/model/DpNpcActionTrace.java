package com.example.mgdemoplus.npc.trace.model;

import java.util.ArrayList;
import java.util.List;

/** TAG action 完整 trace（含 steps 与翻前 matrix）。 */
public class DpNpcActionTrace extends DpNpcActionTraceSummary {
    public List<DpNpcTraceStep> steps = new ArrayList<>();
    public DpNpcPreflopMatrixSnapshot preflopMatrix;
    /** FACING_OPEN / FACING_3BET 时的 re-raise 子范围（3bet/4bet value matrix）。 */
    public DpNpcPreflopMatrixSnapshot preflopMatrixSecondary;
    /** 决策前 re-raise 概率与 eligibility，UNOPENED 等 spot 为 null。 */
    public DpNpcPreflopRaiseMeta raiseMeta;
}
