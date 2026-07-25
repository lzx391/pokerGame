package com.example.mgdemoplus.npc.trace;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.npc.trace.model.DpNpcHandTraceBundle;
import com.example.mgdemoplus.websocket.DpGameRoomPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** hand seal 后向房主 WS 推送 TAG 决策 trace 摘要。 */
@Service
public class DpNpcTagDecisionTracePushService {

    private static final Logger log = LoggerFactory.getLogger(DpNpcTagDecisionTracePushService.class);

    private final DpGameRoomPushService pushService;
    private final ObjectMapper objectMapper;

    public DpNpcTagDecisionTracePushService(DpGameRoomPushService pushService, ObjectMapper objectMapper) {
        this.pushService = pushService;
        this.objectMapper = objectMapper;
    }

    public void pushHandSealed(DpRoomBO room, DpNpcHandTraceBundle bundle) {
        if (room == null || bundle == null || room.getOwner() == null || room.getOwner().isEmpty()) {
            return;
        }
        try {
            ObjectNode root = objectMapper.createObjectNode();
            root.put("_ws", "npcDecisionTraceHand");
            root.put("roomId", bundle.roomId);
            root.put("handSeed", bundle.handSeed);
            root.put("handIndex", bundle.handIndex);
            root.put("sealedAtMs", bundle.sealedAtMs);
            root.put("actionCount", bundle.actionCount);
            root.set("bundle", objectMapper.valueToTree(DpNpcTagDecisionTraceStore.bundleForWsPush(bundle)));
            String json = objectMapper.writeValueAsString(root);
            pushService.sendRawJsonToRoomOwner(room.getRoomId(), room.getOwner(), json);
        } catch (Exception e) {
            log.warn("npc decision trace push failed roomId={}", room.getRoomId(), e);
        }
    }
}
