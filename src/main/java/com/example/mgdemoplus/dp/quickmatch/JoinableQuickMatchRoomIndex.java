package com.example.mgdemoplus.dp.quickmatch;

import com.example.mgdemoplus.bo.DpRoomBO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 大厅快速匹配：对「无密码、符合快匹空位规则、且尚有快匹可读空位」的公开房，按<b>缺人数</b>建桶，
 * 供 Agent 2 以 O(logN) 选取「越满越优先」的候选桌，避免全表扫描 {@code roomMap}。
 *
 * <h2>不变量</h2>
 * <ul>
 *   <li>主索引：{@code TreeMap<Integer, NavigableSet<String>>}，键为<b>缺几人</b>（∈[1,maxSeatCount]），
 *       值为该缺额下房间 id 集合（同键<b>多房间</b>，禁止单值 Map 覆盖）。</li>
 *   <li>反向表：{@code roomId → 当前桶键}，保证 {@link #addOrRefresh} 幂等迁移、{@link #remove} O(1) 定位。</li>
 *   <li>桶内 {@link NavigableSet} 按 {@code roomId} 字典序，与产品「同缺额下稳定、可再现」一致；缺人数更小 = 更满，
 *       {@link TreeMap} 升序遍历天然「越满越先」。</li>
 *   <li>语义与收录条件与 {@link DpQuickMatchRoomSemantics} / 现有快匹一致：密码房、已满、超员异常房均<b>不在</b>索引内。</li>
 * </ul>
 *
 * <h2>何时 insert / remove</h2>
 * <p>在房间「可能影响快匹空位」的事件后调用 {@link #addOrRefresh}：进退房、候补、心跳导致的离线、
 * {@code waitNextHand} 变更、改密、{@code maxSeatCount} 变更等。房间销毁或不再符合条件时 {@link #remove}。
 *
 * <h2>与 {@code roomMap} 漂移时</h2>
 * <p>若漏调维护或并发异常导致索引与内存房态不一致，应 Stop-the-world 式
 * {@link #rebuildAll(Map, long)}（由业务在持分配锁下调用）。全量遍历 {@code roomMap}，清空本索引后仅对符合条件的房间重新 {@link #addOrRefresh}。
 *
 * <h2>锁与顺序（避免与 {@code DpRoomBO}、{@code dpQuickMatchAssignmentLock} 死锁）</h2>
 * <ul>
 *   <li>本类以 {@link #indexLock} 保护全部读写；<b>不要</b>在持 <b>单房</b> {@code synchronized(房间)} 时再去抢本锁。</li>
 *   <li>推荐调用序：{@code synchronized(dpQuickMatchAssignmentLock)} → 本索引 mutate →（再对候选房 {@code synchronized(r)}），
 *       与 {@code DpRoomServiceImpl#quickMatchJoinAndReady} 一致。</li>
 *   <li>若索引方法与房锁交叉：全局固定为 <b>分配锁 → 本索引锁 → 房间锁</b>；禁止「房锁 → 索引锁」。</li>
 * </ul>
 *
 * @see DpQuickMatchRoomSemantics
 */
public class JoinableQuickMatchRoomIndex {

    private final Object indexLock = new Object();

    /** 缺人数（升序） → 该缺额下可快匹加入的房间 id（字典序）。 */
    private final TreeMap<Integer, NavigableSet<String>> byShortage = new TreeMap<>();

    private final Map<String, Integer> roomIdToShortage = new HashMap<>();

    public void addOrRefresh(String roomId, DpRoomBO room, long nowMs) {
        if (roomId == null) {
            return;
        }
        synchronized (indexLock) {
            removeUnderLock(roomId);
            if (!DpQuickMatchRoomSemantics.shouldIndexPublicQuickMatchRoom(room, nowMs)) {
                return;
            }
            int key = DpQuickMatchRoomSemantics.vacancyBucketKeyForIndex(room, nowMs);
            if (key <= 0) {
                return;
            }
            byShortage.computeIfAbsent(key, k -> new TreeSet<>()).add(roomId);
            roomIdToShortage.put(roomId, key);
        }
    }

    public void remove(String roomId) {
        if (roomId == null) {
            return;
        }
        synchronized (indexLock) {
            removeUnderLock(roomId);
        }
    }

    /**
     * 自「最满」桶起（缺人数最小），按 roomId 字典序尝试，返回首个满足 {@code rule} 的 roomId，并<strong>从索引移除</strong>该条。
     * 调用方若进房失败，应再次 {@link #addOrRefresh}。若 {@code roomById} 返回 null，视为脏项并从索引剔除后继续。
     */
    public Optional<String> pollBestCandidate(Function<String, DpRoomBO> roomById, Predicate<DpRoomBO> rule) {
        if (roomById == null || rule == null) {
            return Optional.empty();
        }
        synchronized (indexLock) {
            Iterator<Map.Entry<Integer, NavigableSet<String>>> it = byShortage.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, NavigableSet<String>> e = it.next();
                NavigableSet<String> set = e.getValue();
                if (set == null || set.isEmpty()) {
                    it.remove();
                    continue;
                }
                List<String> snapshot = new ArrayList<>(set);
                for (String rid : snapshot) {
                    DpRoomBO r = roomById.apply(rid);
                    if (r == null) {
                        removeUnderLock(rid);
                        continue;
                    }
                    if (rule.test(r)) {
                        removeUnderLock(rid);
                        return Optional.of(rid);
                    }
                }
            }
            return Optional.empty();
        }
    }

    /** 当前仍缺人的最小缺额桶键；若无则 empty。 */
    public Optional<Integer> firstVacancyBucket() {
        synchronized (indexLock) {
            if (byShortage.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(byShortage.firstKey());
        }
    }

    /**
     * 只读快照：某缺人数桶内的 roomId（字典序拷贝）。
     */
    public List<String> roomIdsInBucket(int shortage) {
        synchronized (indexLock) {
            NavigableSet<String> set = byShortage.get(shortage);
            if (set == null || set.isEmpty()) {
                return List.of();
            }
            return new ArrayList<>(set);
        }
    }

    public int indexedRoomCount() {
        synchronized (indexLock) {
            return roomIdToShortage.size();
        }
    }

    public void rebuildAll(Map<String, DpRoomBO> roomMap, long nowMs) {
        synchronized (indexLock) {
            byShortage.clear();
            roomIdToShortage.clear();
            if (roomMap == null || roomMap.isEmpty()) {
                return;
            }
            for (Map.Entry<String, DpRoomBO> e : roomMap.entrySet()) {
                addOrRefreshUnderLock(e.getKey(), e.getValue(), nowMs);
            }
        }
    }

    private void addOrRefreshUnderLock(String roomId, DpRoomBO room, long nowMs) {
        removeUnderLock(roomId);
        if (!DpQuickMatchRoomSemantics.shouldIndexPublicQuickMatchRoom(room, nowMs)) {
            return;
        }
        int key = DpQuickMatchRoomSemantics.vacancyBucketKeyForIndex(room, nowMs);
        if (key <= 0) {
            return;
        }
        byShortage.computeIfAbsent(key, k -> new TreeSet<>()).add(roomId);
        roomIdToShortage.put(roomId, key);
    }

    private void removeUnderLock(String roomId) {
        Integer k = roomIdToShortage.remove(roomId);
        if (k == null) {
            return;
        }
        NavigableSet<String> set = byShortage.get(k);
        if (set == null) {
            return;
        }
        set.remove(roomId);
        if (set.isEmpty()) {
            byShortage.remove(k);
        }
    }
}
