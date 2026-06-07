package com.example.mgdemoplus.npc.eval;

/**
 * 静态绑定 {@link DpNpcGranularStrengthProperties}，供 {@link com.example.mgdemoplus.npc.engine.DpNpcEngine} 读取。
 */
public final class DpNpcGranularStrength {

    private static volatile DpNpcGranularStrengthProperties properties = new DpNpcGranularStrengthProperties();

    private DpNpcGranularStrength() {
    }

    public static void bind(DpNpcGranularStrengthProperties props) {
        properties = props != null ? props : new DpNpcGranularStrengthProperties();
    }

    public static boolean isEnabled() {
        return properties.isEnabled();
    }

    public static DpNpcGranularStrengthProperties currentProperties() {
        return properties;
    }
}
