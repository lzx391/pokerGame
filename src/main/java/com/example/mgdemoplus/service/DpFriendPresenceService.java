package com.example.mgdemoplus.service;

import com.example.mgdemoplus.presence.DpFriendPresenceState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 好友房内态（在座或观众）：<b>不入库</b>，单机 {@link ConcurrentHashMap} 仅存 IDLE / IN_GAME。
 * 站点级在线见 {@link DpSitePresenceService}；列表展示态由 {@link com.example.mgdemoplus.service.serviceImpl.DpFriendSocialService#listFriends} 合成。
 */
@Service
public class DpFriendPresenceService {

    private static final Logger log = LoggerFactory.getLogger(DpFriendPresenceService.class);

    private final ConcurrentHashMap<Integer, DpFriendPresenceState> presenceByUserId = new ConcurrentHashMap<>();

    /** 未出现在 map 中时视为 IDLE（隐含默认态，避免注册用户必须预热）。 */
    public DpFriendPresenceState getEffective(int dpUserId) {
        return presenceByUserId.getOrDefault(dpUserId, DpFriendPresenceState.IDLE);
    }

    /**
     * 批量读取房内内存态（无额外远程 IO）；缺省仍为 {@link DpFriendPresenceState#IDLE}。
     */
    public Map<Integer, DpFriendPresenceState> getEffectiveMany(Collection<Integer> dpUserIds) {
        Map<Integer, DpFriendPresenceState> out = new LinkedHashMap<>();
        if (dpUserIds == null) {
            return out;
        }
        for (Integer id : dpUserIds) {
            if (id != null && id > 0) {
                out.put(id, getEffective(id));
            }
        }
        return out;
    }

    public void markInGame(int dpUserId, String triggerTag) {
        DpFriendPresenceState prev = presenceByUserId.put(dpUserId, DpFriendPresenceState.IN_GAME);
        if (log.isDebugEnabled() && prev != DpFriendPresenceState.IN_GAME) {
            log.debug("friend_presence userId={} {} -> IN_GAME trigger={}", dpUserId, prev == null ? "∅(idle)"
                    : prev, triggerTag);
        }
    }

    public void markIdle(int dpUserId, String triggerTag) {
        DpFriendPresenceState prev = presenceByUserId.put(dpUserId, DpFriendPresenceState.IDLE);
        if (prev != DpFriendPresenceState.IDLE && log.isDebugEnabled()) {
            log.debug("friend_presence userId={} {} -> IDLE trigger={}", dpUserId, prev == null ? "∅(idle)"
                    : prev, triggerTag);
        }
    }
}
