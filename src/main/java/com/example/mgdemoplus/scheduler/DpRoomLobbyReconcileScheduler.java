package com.example.mgdemoplus.scheduler;

import com.example.mgdemoplus.service.dp.DpRoomHallService;
import com.example.mgdemoplus.service.serviceImpl.dp.DpRoomServiceImpl;
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

    @Override
    public void run(ApplicationArguments args) {
        reconcile("startup");
    }

    @Scheduled(
            fixedDelayString = "${mgdemoplus.dp-lobby-reconcile-ms:60000}",
            initialDelayString = "${mgdemoplus.dp-lobby-reconcile-ms:60000}")
    public void scheduledReconcile() {
        reconcile("scheduled");
    }

    private void reconcile(String reason) {
        try {
            dpRoomHallService.reconcileLobbyWithRuntimeRoomIds(dpRoomService.getRoomIdsInMemory());
        } catch (Exception e) {
            log.warn("dp lobby reconcile ({}) failed: {}", reason, e.toString());
        }
    }
}
