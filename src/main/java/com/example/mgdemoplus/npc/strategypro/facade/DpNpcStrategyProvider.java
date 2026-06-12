package com.example.mgdemoplus.npc.strategypro.facade;

/**
 * 规则 NPC 策略门面单例提供者。
 */
public final class DpNpcStrategyProvider {

    private static final DpNpcStrategyFacade INSTANCE = new DpNpcStrategyFacadeImpl();

    private DpNpcStrategyProvider() {
    }

    public static DpNpcStrategyFacade get() {
        return INSTANCE;
    }
}
