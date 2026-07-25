package com.example.mgdemoplus.npc.trace.model;

import java.util.Map;

/** TAG 决策 trace 单步记录。 */
public class DpNpcTraceStep {
    public int seq;
    public String phase;
    public String code;
    public String message;
    public Map<String, Object> data;

    public DpNpcTraceStep() {
    }

    public DpNpcTraceStep(int seq, String phase, String code, String message, Map<String, Object> data) {
        this.seq = seq;
        this.phase = phase;
        this.code = code;
        this.message = message;
        this.data = data;
    }
}
