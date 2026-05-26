package com.example.mgdemoplus.npc;

import com.example.mgdemoplus.common.entity.DpPlayer;

/**
 * 自定义 BOT 六维风格快照（0～1）；候补阶段存于 {@link com.example.mgdemoplus.common.bo.DpRoomBO} pending map，入座后拷贝到 {@link DpPlayer}。
 */
public final class CustomNpcStyleSnapshot {

    private final double vpip;
    private final double pfr;
    private final double cbetFreq;
    private final double bluffFreq;
    private final double callStation;
    private final double foldToPressure;

    public CustomNpcStyleSnapshot(
            double vpip,
            double pfr,
            double cbetFreq,
            double bluffFreq,
            double callStation,
            double foldToPressure) {
        this.vpip = clamp01(vpip);
        this.pfr = clamp01(pfr);
        this.cbetFreq = clamp01(cbetFreq);
        this.bluffFreq = clamp01(bluffFreq);
        this.callStation = clamp01(callStation);
        this.foldToPressure = clamp01(foldToPressure);
    }

    public static CustomNpcStyleSnapshot fromTagPreset() {
        return new CustomNpcStyleSnapshot(0.24, 0.76, 0.82, 0.36, 0.18, 0.22);
    }

    public static double clamp01(double v) {
        if (v < 0.0) {
            return 0.0;
        }
        if (v > 1.0) {
            return 1.0;
        }
        return v;
    }

    /**
     * 从请求 DTO 解析；任一字段缺失或非法则返回 null。
     */
    public static CustomNpcStyleSnapshot fromRequestProfile(CustomNpcStyleProfileDto dto) {
        if (dto == null) {
            return null;
        }
        if (!isFinite01(dto.getVpip())
                || !isFinite01(dto.getPfr())
                || !isFinite01(dto.getCbetFreq())
                || !isFinite01(dto.getBluffFreq())
                || !isFinite01(dto.getCallStation())
                || !isFinite01(dto.getFoldToPressure())) {
            return null;
        }
        return new CustomNpcStyleSnapshot(
                dto.getVpip(),
                dto.getPfr(),
                dto.getCbetFreq(),
                dto.getBluffFreq(),
                dto.getCallStation(),
                dto.getFoldToPressure());
    }

    private static boolean isFinite01(Double v) {
        return v != null && !v.isNaN() && !v.isInfinite() && v >= 0.0 && v <= 1.0;
    }

    public CustomNpcStyleSnapshot cloneSnapshot() {
        return new CustomNpcStyleSnapshot(vpip, pfr, cbetFreq, bluffFreq, callStation, foldToPressure);
    }

    public void copyTo(DpPlayer player) {
        if (player == null) {
            return;
        }
        player.setNpcStyleVpip(vpip);
        player.setNpcStylePfr(pfr);
        player.setNpcStyleCbetFreq(cbetFreq);
        player.setNpcStyleBluffFreq(bluffFreq);
        player.setNpcStyleCallStation(callStation);
        player.setNpcStyleFoldToPressure(foldToPressure);
        player.setNpcCustomStyleReady(true);
    }

    public double getVpip() {
        return vpip;
    }

    public double getPfr() {
        return pfr;
    }

    public double getCbetFreq() {
        return cbetFreq;
    }

    public double getBluffFreq() {
        return bluffFreq;
    }

    public double getCallStation() {
        return callStation;
    }

    public double getFoldToPressure() {
        return foldToPressure;
    }
}
