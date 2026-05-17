package com.example.mgdemoplus.scheduler;

import com.example.mgdemoplus.service.DpRoomHallService;
import com.example.mgdemoplus.service.serviceImpl.DpRoomServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时将 {@code dp_room_lobby} 与单机内存 {@link DpRoomServiceImpl} 中的房间对齐，
 * 清掉服务重启、异常路径下残留的「大厅幽灵房」。
 * <p>
 * 多实例部署时每个节点 roomMap 不一致，请勿开启（或需改为分布式房间注册后再对齐）。
 */
@Component
@ConditionalOnProperty(name = "mgdemoplus.dp-lobby-reconcile-enabled", havingValue = "true", matchIfMissing = true)
public class DpRoomLobbyReconcileScheduler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DpRoomLobbyReconcileScheduler.class);

    private final DpRoomServiceImpl dpRoomService;
    private final DpRoomHallService dpRoomHallService;

    public DpRoomLobbyReconcileScheduler(DpRoomServiceImpl dpRoomService, DpRoomHallService dpRoomHallService) {
        this.dpRoomService = dpRoomService;
        this.dpRoomHallService = dpRoomHallService;
    }

    /**
     * 应用启动后立即进行一次大厅与内存房间的对齐，防止启动前异常导致的“幽灵房”遗留。
     */
    @Override
    public void run(ApplicationArguments args) {
        reconcile("startup");
    }

    /**
     * 按配置的周期（默认每分钟）定期对齐 dp_room_lobby 与内存房间列表，清理异常“幽灵房”。
     * fixedDelay: 每次执行结束后间隔（ms）再执行下一次。
     * initialDelay: 启动后首次执行前的延迟（ms）。
     * 配置项：mgdemoplus.dp-lobby-reconcile-ms，默认为 60000ms（1 分钟）
     */
    @Scheduled(
            fixedDelayString = "${mgdemoplus.dp-lobby-reconcile-ms:60000}",
            initialDelayString = "${mgdemoplus.dp-lobby-reconcile-ms:60000}")
    public void scheduledReconcile() {
        reconcile("scheduled");
    }

    /**
     * 核心对齐逻辑：将 dp_room_lobby 表与当前 JVM 内存中的房间 ID 对齐。
     * 若数据库存在但内存中已无的房间将被清除（清理幽灵房）。
     *
     * @param reason 触发原因（如 "startup" 或 "scheduled"），仅用于日志。
     */
    private void reconcile(String reason) {
        try {
            // 调用大厅服务进行房间对齐。可能会涉及数据库与内存列表的交互。
            dpRoomHallService.reconcileLobbyWithRuntimeRoomIds(dpRoomService.getRoomIdsInMemory());
        } catch (Exception e) {
            // 捕获异常并警告日志，避免调度器因异常中断。
            log.warn("dp lobby reconcile ({}) failed: {}", reason, e.toString());
        }
    }
}
