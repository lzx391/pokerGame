package com.example.mgdemoplus.npc;

/**
 * 请求体中的 6 维 profile 字段（JSON 键名与方案一致）。
 */
public class CustomNpcStyleProfileDto {
    private Double vpip;
    private Double pfr;
    private Double cbetFreq;
    private Double bluffFreq;
    private Double callStation;
    private Double foldToPressure;

    public Double getVpip() {
        return vpip;
    }

    public void setVpip(Double vpip) {
        this.vpip = vpip;
    }

    public Double getPfr() {
        return pfr;
    }

    public void setPfr(Double pfr) {
        this.pfr = pfr;
    }

    public Double getCbetFreq() {
        return cbetFreq;
    }

    public void setCbetFreq(Double cbetFreq) {
        this.cbetFreq = cbetFreq;
    }

    public Double getBluffFreq() {
        return bluffFreq;
    }

    public void setBluffFreq(Double bluffFreq) {
        this.bluffFreq = bluffFreq;
    }

    public Double getCallStation() {
        return callStation;
    }

    public void setCallStation(Double callStation) {
        this.callStation = callStation;
    }

    public Double getFoldToPressure() {
        return foldToPressure;
    }

    public void setFoldToPressure(Double foldToPressure) {
        this.foldToPressure = foldToPressure;
    }
}
