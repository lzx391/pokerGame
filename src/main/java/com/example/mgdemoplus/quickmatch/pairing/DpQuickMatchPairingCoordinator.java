package com.example.mgdemoplus.quickmatch.pairing;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.quickmatch.DpQuickMatchRoomSemantics;
import com.example.mgdemoplus.quickmatch.JoinableQuickMatchRoomIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 新快匹「尝试配对」单入口协调器：先尽量把队列流入「房索引有桌」的公开房；否则在无桌且队列至少两人时，
 * 自队头批量取出至多 {@value #MAX_NEW_ROOM_BATCH} 人建房开局。
 *
 * <h2>与 Agent C 的契约</h2>
 * <ul>
 *   <li>C 在 {@code DpRoomServiceImpl#createRoom}（无密码公开房路径）<strong>末尾</strong>调用
 *       {@link #attemptPairing()}，使新房入索引后立刻消费队列；须保证此时<strong>未</strong>仍持有
 *       {@link DpQuickMatchPairingHost#defaultQmLock()}（否则与队内临界区嵌套）。</li>
 *   <li>本协调器<strong>不</strong>使用旧版 {@code dpQuickMatchAssignmentLock} /
 *       {@code ThreadLocal} 重入深度；重入用 {@link #pairingDepth} 仅在协调器内计数。</li>
 * </ul>
 *
 * <h2>非重入死锁说明（取代 ThreadLocal 深度）</h2>
 * <p>当 {@link DpQuickMatchPairingHost#createDefaultQuickMatchRoomForPairing} 的链路再次调用
 * {@link #attemptPairing()} 时，{@link #pairingDepth} &gt; 1。内层只执行
 * {@link DpQuickMatchPairingHost#flushQueuedWaitersIntoJoinablePublicRooms()} 即<strong>冲公开桌</strong>，
 * 不再进入「无桌批量建房」递归路径，从而避免在已展开建房流程时再次无锁建房或与外层持锁顺序冲突。
 *
 * <h2>持锁顺序（同一路径需同时触碰房监视器与队列锁时，强制「先房后队」）</h2>
 * <p>凡同一代码路径既要 {@code synchronized(DpRoomBO r)} 又要
 * {@code synchronized(host.defaultQmLock())}：<b>必须先</b> {@code r}，<b>再</b> 队列锁。
 *
 * <table border="1" summary="快匹相关路径持锁顺序">
 *   <caption>路径与监视器顺序</caption>
 *   <thead>
 *     <tr><th>路径</th><th>顺序（从早到晚）</th></tr>
 *   </thead>
 *   <tbody>
 *     <tr>
 *       <td>{@link #attemptPairing()} — 有桌填充</td>
 *       <td>{@code synchronized(r)} → {@code synchronized(defaultQmLock)}（队内 poll 队头 k 人）→ 释队列锁 →
 *           仍在房锁内 join → 释房锁 → （锁外）刷新索引</td>
 *     </tr>
 *     <tr>
 *       <td>{@link #attemptPairing()} — 无桌建房</td>
 *       <td>仅 {@code synchronized(defaultQmLock)}（计数与 batch poll）→ 释队列锁 → 无 intersect；
 *           继而 {@code synchronized(新房)} 内 join / 准备 / 开局</td>
 *     </tr>
 *     <tr>
 *       <td>flush（由 Host 实现）</td>
 *       <td>由 C 实现：对齐历史「仅队列锁内 prune+快照；锁外逐人进房」—不进
 *           「持队列锁 + 持房锁」嵌套</td>
 *     </tr>
 *     <tr>
 *       <td>外层 joinRoom / quickMatchJoinAndReady（现有代码）</td>
 *       <td>仍由 C 维护；迁入本协调器后公共扫房应由 {@link DpQuickMatchPairingHost#flushQueuedWaitersIntoJoinablePublicRooms()}
 *           表达</td>
 *     </tr>
 *     <tr>
 *       <td>退房/定时器触发配对（未来接 C）</td>
 *       <td>须在释放单房监视器后调用 {@link #attemptPairing()}；若未来与队列同事务，遵循先房后队</td>
 *     </tr>
 *   </tbody>
 * </table>
 */
public final class DpQuickMatchPairingCoordinator {

    /** 无桌时单次出队上限，与默认快匹最大座位一致。 */
    public static final int MAX_NEW_ROOM_BATCH = DpRoomBO.MAX_SEAT_COUNT;

    private final DpQuickMatchPairingHost host;

    /**
     * 取代 ServiceImpl 中 {@code ThreadLocal}：仅在本协调器内统计 {@link #attemptPairing()} 嵌套深度。
     */
    private final AtomicInteger pairingDepth = new AtomicInteger();

    public DpQuickMatchPairingCoordinator(DpQuickMatchPairingHost host) {
        this.host = host;
    }

    /**
     * 单入口：循环尝试填充已有桌或批量建房，直到本轮无进展。
     */
    public void attemptPairing() {
        int d = pairingDepth.incrementAndGet();
        try {
            if (d > 1) {
                host.flushQueuedWaitersIntoJoinablePublicRooms();
                return;
            }
            progressLoop: while (true) {
                host.flushQueuedWaitersIntoJoinablePublicRooms();

                JoinableQuickMatchRoomIndex index = host.joinableQuickMatchRoomIndex();
                var vacancyBucket = index.firstVacancyBucket();
                if (vacancyBucket.isPresent()) {
                    List<String> ids = new ArrayList<>(index.roomIdsInBucket(vacancyBucket.get()));
                    boolean filledAny = false;
                    for (String roomId : ids) {
                        if (fillHeadIntoJoinableRoom(roomId)) {
                            filledAny = true;
                            break;
                        }
                    }
                    if (filledAny) {
                        continue progressLoop;
                    }
                }

                if (!drainBatchNewRoomWhenNoJoinableTable()) {
                    break;
                }
            }
        } finally {
            pairingDepth.decrementAndGet();
        }
    }

    /**
     * 「房索引有桌」分支：对给定 {@code roomId}，自队头严格取 {@code k=min(可读空位, 队列人数)}。
     *
     * @return {@code true} 若本房成功自队列摘下过至少一批（含部分 join 失败但已 poll 情形仍可能为 true）
     */
    private boolean fillHeadIntoJoinableRoom(String roomId) {
        JoinableQuickMatchRoomIndex index = host.joinableQuickMatchRoomIndex();
        DpRoomBO r = host.roomMap().get(roomId);
        if (r == null) {
            index.remove(roomId);
            return true;
        }

        Object qLock = host.defaultQmLock();
        List<DpQuickMatchWaitEntry> batch = List.of();
        synchronized (r) {
            if (!roomId.equals(r.getRoomId()) || host.roomMap().get(roomId) != r) {
                return true;
            }
            long now = host.nowMillis();
            if (r.isPasswordProtected()) {
                return false;
            }
            int vac = DpQuickMatchRoomSemantics.vacancyForQuickMatch(r, now);
            if (vac <= 0) {
                return false;
            }
            synchronized (qLock) {
                host.pruneQueueWhileLocked();
                int qSize = host.defaultQueueSizeWhileLocked();
                int k = Math.min(vac, qSize);
                if (k <= 0) {
                    return false;
                }
                batch = host.pollHeadWhileLocked(k);
            }
            List<DpQuickMatchWaitEntry> failedRequeue = new ArrayList<>();
            for (DpQuickMatchWaitEntry e : batch) {
                if (host.nicknameAlreadyInSomeRoom(e.nickname())) {
                    continue;
                }
                try {
                    host.joinAndReadyWhileRoomLocked(r, e);
                    host.notifyQuickMatchMatched(e.nickname(), roomId);
                } catch (RuntimeException ex) {
                    failedRequeue.add(e);
                }
            }
            if (!failedRequeue.isEmpty()) {
                synchronized (qLock) {
                    host.unshiftHeadWhileLocked(failedRequeue);
                }
            }
        }

        if (batch.isEmpty()) {
            return false;
        }
        host.afterRoomMutationRefreshQuickMatchIndex(roomId, "ok");
        return true;
    }

    /**
     * 「无桌」分支：队中至多 1 人则整段不建房；否则一次出队 {@code min(MAX_NEW_ROOM_BATCH, size)} 人建房。
     */
    private boolean drainBatchNewRoomWhenNoJoinableTable() {
        Object qLock = host.defaultQmLock();
        List<DpQuickMatchWaitEntry> batch;
        synchronized (qLock) {
            host.pruneQueueWhileLocked();
            int sz = host.defaultQueueSizeWhileLocked();
            if (sz <= 1) {
                return false;
            }
            int n = Math.min(MAX_NEW_ROOM_BATCH, sz);
            batch = host.pollHeadWhileLocked(n);
        }
        if (batch.size() < 2) {
            synchronized (qLock) {
                host.unshiftHeadWhileLocked(batch);
            }
            return false;
        }

        if (batch.get(0).nickname().equals(batch.get(1).nickname())) {
            synchronized (qLock) {
                host.addTailWhileLocked(batch);
            }
            return false;
        }

        DpQuickMatchWaitEntry owner = batch.get(0);
        DpRoomBO room = host.createDefaultQuickMatchRoomForPairing(owner);
        String newRoomId = room.getRoomId();

        synchronized (room) {
            for (int i = 1; i < batch.size(); i++) {
                DpQuickMatchWaitEntry e = batch.get(i);
                if (host.nicknameAlreadyInSomeRoom(e.nickname())) {
                    continue;
                }
                host.joinAndReadyWhileRoomLocked(room, e);
                host.notifyQuickMatchMatched(e.nickname(), newRoomId);
            }
        }

        host.afterRoomMutationRefreshQuickMatchIndex(newRoomId, "ok");
        host.notifyQuickMatchMatched(owner.nickname(), newRoomId);
        host.startGameForQuickMatchRoom(newRoomId, owner.nickname());

        host.flushQueuedWaitersIntoJoinablePublicRooms();
        return true;
    }
}
