package com.example.mgdemoplus.quickmatch.pairing;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.quickmatch.JoinableQuickMatchRoomIndex;

import java.util.List;
import java.util.Map;

/**
 * 快匹「尝试配对」与 {@link com.example.mgdemoplus.room.impl.DpRoomServiceImpl} 之间的回调面：由 Agent C 注入实现，
 * 内部可继续调用现有 {@code joinRoom}、{@code createRoom}、{@code roomMap}、索引维护与 WS 推送等。
 */
public interface DpQuickMatchPairingHost {

    /** 与业务侧 {@code defaultQmLock} 必须为同一实例（对象监视器保护 {@code defaultQmWaiters}）。 */
    Object defaultQmLock();

    Map<String, DpRoomBO> roomMap();

    JoinableQuickMatchRoomIndex joinableQuickMatchRoomIndex();

    long nowMillis();

    /**
     * 在已持 {@link #defaultQmLock()} 下：修剪超时 / 已在房内者，并返回因<strong>超时</strong>被移出者的昵称（供推送）。
     */
    List<String> pruneQueueWhileLocked();

    /** 已持队列锁时返回当前等待人数。 */
    int defaultQueueSizeWhileLocked();

    /**
     * 从队头按 FIFO 取出至多 {@code maxTake} 条；不足则取尽。
     * 仅在已持 {@link #defaultQmLock()} 时调用。
     */
    List<DpQuickMatchWaitEntry> pollHeadWhileLocked(int maxTake);

    /** 将若干条目按原顺序插回队头；已持队列锁。 */
    void unshiftHeadWhileLocked(List<DpQuickMatchWaitEntry> entries);

    /** 将若干条目依次追加到队尾；已持队列锁。 */
    void addTailWhileLocked(List<DpQuickMatchWaitEntry> entries);

    boolean nicknameAlreadyInSomeRoom(String nickname);

    /**
     * 在已 {@code synchronized(room)} 内：完成该玩家进房与准备（对齐历史
     * {@code joinRoomMutateAssumeLocked} 语义）。失败时应由实现方记录/处理；协调器侧仅在见异常时把条目退回队列。
     */
    void joinAndReadyWhileRoomLocked(DpRoomBO room, DpQuickMatchWaitEntry entry);

    /**
     * 无可用公开桌时批量建房：以 {@code owner} 为房主创建默认快匹房（小盲/大盲/初始筹码/座位上限与产品一致）。
     */
    DpRoomBO createDefaultQuickMatchRoomForPairing(DpQuickMatchWaitEntry owner);

    void startGameForQuickMatchRoom(String roomId, String ownerNickname);

    void notifyQuickMatchMatched(String nickname, String roomId);

    /** 进房改写 BO 后、在释放房监视器之后刷新索引/大厅摘要等。 */
    void afterRoomMutationRefreshQuickMatchIndex(String roomId, String joinDetailMessageOrNull);

    /**
     * 与历史 {@code tryFlushDefaultQuickMatchWaitersIntoPublicRooms} 对齐：先把队列中可进已有公开桌的玩家扫入房。
     * 实现内部<strong>不得</strong>在持 {@link #defaultQmLock()} 与某 {@code DpRoomBO} 监视器时反向嵌套违反「先房后队」。
     */
    void flushQueuedWaitersIntoJoinablePublicRooms();
}
