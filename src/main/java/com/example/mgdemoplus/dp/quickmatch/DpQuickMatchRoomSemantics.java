package com.example.mgdemoplus.dp.quickmatch;

import com.example.mgdemoplus.bo.DpRoomBO;
import com.example.mgdemoplus.entity.dp.DpPlayer;
import com.example.mgdemoplus.service.serviceImpl.dp.DpNpcEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 大厅快速匹配（公开房）下，对 {@link DpRoomBO} 的**唯一权威**人数语义：与
 * {@code DpRoomServiceImpl#quickMatchJoinAndReady}、历史 {@code quickMatchVacancy} 口径一致。
 *
 * <p><b>满员（PM）</b>：已计入人数相对 {@link DpRoomBO#getMaxSeatCount()} 达到上限，即「缺 0 人」。
 * <b>缺几人</b>：{@code maxSeatCount − 当前有效人数}，对调用方展示时不得小于 0。
 *
 * <p><b>有效人数</b>：桌上「占名额」的非僵尸座次（{@code leftThisHand} 不计入）且（NPC 始终算在线；真人须心跳未超时），
 * 再加 {@link DpRoomBO#getWaitNextHand()} 预约人数。与文档「空位 = 上限 − 非离线座 − waitNextHand」一致。
 *
 * <p><b>数据异常</b>：若有效人数 {@code &gt; maxSeatCount}，则 {@link #rawVacancyForQuickMatch} 为负；{@link #vacancyForQuickMatch} 钳为 0，
 * 并打 WARN；索引层应对该类房间 {@linkplain JoinableQuickMatchRoomIndex 不予收录}。
 */
public final class DpQuickMatchRoomSemantics {

    private static final Logger log = LoggerFactory.getLogger(DpQuickMatchRoomSemantics.class);

    private DpQuickMatchRoomSemantics() {}

    /**
     * 计入快匹「满员」判定的有效人数：{@link #seatedNonOfflineCount} + {@link #waitNextHandSize}。
     * 与历史「越满越优先」排序分数 {@code quickMatchFillScore} 相同。
     */
    public static int eligibleOccupantsForQuickMatch(DpRoomBO r, long nowMs) {
        return seatedNonOfflineCount(r, nowMs) + waitNextHandSize(r);
    }

    /**
     * 算空位数，负数或0表示满员
     */
    public static int rawVacancyForQuickMatch(DpRoomBO r, long nowMs) {
        if (r == null) {
            return 0;
        }
        return r.getMaxSeatCount() - eligibleOccupantsForQuickMatch(r, nowMs);
    }

    /**
     * 产品「缺几人」：=max(0, raw)；raw&lt;0 时记录 WARN（超员异常），返回 0。
     */
    public static int vacancyForQuickMatch(DpRoomBO r, long nowMs) {
        int raw = rawVacancyForQuickMatch(r, nowMs);
        if (raw < 0) {
            log.warn("quickMatch raw vacancy negative (capacity anomaly) roomId={} maxSeatCount={} eligible={}",
                    r != null ? r.getRoomId() : null,
                    r != null ? r.getMaxSeatCount() : null,
                    r != null ? eligibleOccupantsForQuickMatch(r, nowMs) : null);
            return 0;
        }
        return raw;
    }

    /** 已满或超员（无快匹可读空位）：rawVacancy &lt;= 0。 */
    public static boolean isFullForQuickMatch(DpRoomBO r, long nowMs) {
        return rawVacancyForQuickMatch(r, nowMs) <= 0;
    }

    /** 排序用「越满越优先」分数；等同于 {@link #eligibleOccupantsForQuickMatch}。 */
    public static int quickMatchFillScore(DpRoomBO r, long nowMs) {
        return eligibleOccupantsForQuickMatch(r, nowMs);
    }

    /**
     * 桌上「占名额」的玩家数（快匹口径）：{@code leftThisHand} 不占；真人须心跳存活；NPC 视为始终在线。
     */
    public static int seatedNonOfflineCount(DpRoomBO r, long nowMs) {
        if (r == null || r.getPlayers() == null) {
            return 0;
        }
        long hb = DpRoomBO.getHeartTimeout();
        int n = 0;
        for (DpPlayer p : r.getPlayers()) {
            if (p == null || p.isLeftThisHand()) {
                continue;
            }
            if (DpNpcEngine.isBotPlayer(p)) {
                n++;
                continue;
            }
            if (nowMs - p.getLastHeartBeat() <= hb) {
                n++;
            }
        }
        return n;
    }

    public static int waitNextHandSize(DpRoomBO r) {
        if (r == null) {
            return 0;
        }
        var w = r.getWaitNextHand();
        return w == null ? 0 : w.size();
    }

    /**
     * 是否应作为「无密码公开快匹可进」房间参与索引：有密码或已无空位（raw&lt;=0）为 false；
     * raw&lt;0（超员）亦为 false，且应由调用方记录/修复内存态。
     */
    public static boolean shouldIndexPublicQuickMatchRoom(DpRoomBO r, long nowMs) {
        if (r == null || r.isPasswordProtected()) {
            return false;
        }
        // raw < 0：超员异常，不入索引（告警见 {@link #vacancyForQuickMatch} 若需展示缺人数）
        return rawVacancyForQuickMatch(r, nowMs) > 0;
    }

    /** 桶键：缺人数，仅当应收录时 ∈ [1, maxSeatCount]；与 {@link #vacancyForQuickMatch} 在 raw&gt;=0 时一致。 */
    public static int vacancyBucketKeyForIndex(DpRoomBO r, long nowMs) {
        return vacancyForQuickMatch(r, nowMs);
    }
}
