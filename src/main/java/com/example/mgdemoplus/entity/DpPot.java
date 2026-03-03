package com.example.mgdemoplus.entity;

import java.util.List;

public class DpPot {
    private int amount;                    // 该池金额
    private List<String> eligiblePlayers;  // 有资格赢这个池的玩家昵称

    @Override
    public String toString() {
        return "DpPot{" +
                "amount=" + amount +
                ", eligiblePlayers=" + eligiblePlayers +
                '}';
    }

    public int getAmount() {
        return amount;
    }

    public void setAmount(int amount) {
        this.amount = amount;
    }

    public List<String> getEligiblePlayers() {
        return eligiblePlayers;
    }

    public void setEligiblePlayers(List<String> eligiblePlayers) {
        this.eligiblePlayers = eligiblePlayers;
    }
}
