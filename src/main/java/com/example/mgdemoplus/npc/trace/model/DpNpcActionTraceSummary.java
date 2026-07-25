package com.example.mgdemoplus.npc.trace.model;

/** TAG action 摘要（hand 列表 / WS 推送）。 */
public class DpNpcActionTraceSummary {
    public String actionId;
    public int actionSeq;
    public String actorNickname;
    public int seatIndex;
    public String street;
    public long timestampMs;
    public FinalAction finalAction;
    public String[] holeCards;

    public static final class FinalAction {
        public String type;
        public int amount;

        public FinalAction() {
        }

        public FinalAction(String type, int amount) {
            this.type = type;
            this.amount = amount;
        }
    }
}
