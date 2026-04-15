package com.example.mgdemoplus.service.dp;

import com.example.mgdemoplus.config.DpGameInstanceProperties;
import com.example.mgdemoplus.dto.DpRoomDTO;
import com.example.mgdemoplus.dto.DpRoomRoutePayload;
import com.example.mgdemoplus.entity.dp.DpRoom;
import com.example.mgdemoplus.entity.dp.DpRoomRegistry;
import com.example.mgdemoplus.mapper.dp.DpRoomRegistryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 房间注册：MySQL 持久化大厅列表，Redis 缓存按房间号路由（方案二：每节点 {@link DpGameInstanceProperties#getPublicWsUrl()}）。
 */
@Service
public class DpRoomRegistryService {

    private static final Logger log = LoggerFactory.getLogger(DpRoomRegistryService.class);
    private static final String GAME_CODE_DP = "DP";
    private static final int DEFAULT_MAX_SEATS = 9;
    private static final String REDIS_ROUTE_PREFIX = "dp:room:route:";

    private final DpRoomRegistryMapper registryMapper;
    private final DpGameInstanceProperties gameInstanceProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public DpRoomRegistryService(
            DpRoomRegistryMapper registryMapper,
            DpGameInstanceProperties gameInstanceProperties,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper) {
        this.registryMapper = registryMapper;
        this.gameInstanceProperties = gameInstanceProperties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public void registerNewRoom(DpRoom room) {
        if (room == null || room.getRoomId() == null) {
            return;
        }
        try {
            DpRoomRegistry row = buildRowFromRoom(room);
            registryMapper.insert(row);
            writeRedisRoute(row);
        } catch (Exception e) {
            log.warn("dp_room_registry insert failed roomId={} (表未建或 DB 不可用时可忽略): {}", room.getRoomId(), e.getMessage());
        }
    }
/** 从房间同步到注册表，更新房间状态 */
    public void syncFromRoom(DpRoom room) {
        if (room == null || room.getRoomId() == null) {
            return;
        }
        try {
            DpRoomRegistry row = buildRowFromRoom(room);
            int n = registryMapper.updateSnapshot(row);
            if (n <= 0) {
                return;
            }
            DpRoomRegistry fresh = registryMapper.selectByRoomId(room.getRoomId());
            if (fresh != null) {
                writeRedisRoute(fresh);
            }
        } catch (Exception e) {
            log.warn("dp_room_registry sync failed roomId={}: {}", room.getRoomId(), e.getMessage());
        }
    }

    public void removeFromRegistry(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return;
        }
        try {
            registryMapper.closeByRoomId(roomId);
        } catch (Exception e) {
            log.warn("dp_room_registry close failed roomId={}: {}", roomId, e.getMessage());
        }
        try {
            stringRedisTemplate.delete(REDIS_ROUTE_PREFIX + roomId);
        } catch (Exception e) {
            log.warn("redis delete room route failed roomId={}: {}", roomId, e.getMessage());
        }
    }

    /**
     * 优先读 Redis，否则 MySQL；仅返回未关闭且状态为等待/游戏中的记录。
     */
    public DpRoomRegistry lookup(String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return null;
        }
        try {
            String json = stringRedisTemplate.opsForValue().get(REDIS_ROUTE_PREFIX + roomId);
            if (json != null && !json.isEmpty()) {
                DpRoomRoutePayload p = objectMapper.readValue(json, DpRoomRoutePayload.class);
                DpRoomRegistry r = new DpRoomRegistry();
                r.setRoomId(p.getRoomId());
                r.setShardId(p.getShardId());
                r.setWsRoute(p.getWsRoute());
                r.setStatus(p.getStatus());
                r.setPlayerCount(p.getPlayerCount());
                r.setSpectatorCount(p.getSpectatorCount());
                r.setHasPassword(p.isPasswordProtected() ? 1 : 0);
                return r;
            }
        } catch (Exception e) {
            log.debug("redis lookup parse failed roomId={}: {}", roomId, e.getMessage());
        }
        try {
            DpRoomRegistry row = registryMapper.selectByRoomId(roomId);
            if (row == null || row.getClosedAt() != null || row.getStatus() == null || row.getStatus() == 2) {
                return null;
            }
            writeRedisRoute(row);
            return row;
        } catch (Exception e) {
            log.warn("dp_room_registry select failed roomId={}: {}", roomId, e.getMessage());
            return null;
        }
    }

    public List<DpRoomDTO> listPublicRoomsFromDb() {
        try {
            List<DpRoomRegistry> rows = registryMapper.selectActiveList();
            if (rows == null || rows.isEmpty()) {
                return Collections.emptyList();
            }
            return rows.stream().map(this::toDto).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("dp_room_registry list failed: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private DpRoomDTO toDto(DpRoomRegistry r) {
        DpRoomDTO dto = new DpRoomDTO();
        dto.setRoomId(r.getRoomId());
        dto.setOwner(r.getOwnerNickname() != null ? r.getOwnerNickname() : "");
        dto.setPlayerSize(r.getPlayerCount() != null ? r.getPlayerCount() : 0);
        dto.setPasswordProtected(r.getHasPassword() != null && r.getHasPassword() != 0);
        dto.setWsRoute(r.getWsRoute());
        dto.setShardId(r.getShardId());
        dto.setSmallBlindChips(r.getSmallBlindChips() != null ? r.getSmallBlindChips() : 0);
        dto.setBigBlindChips(r.getBigBlindChips() != null ? r.getBigBlindChips() : 0);
        dto.setStartingStackBb(r.getStartingStackBb() != null ? r.getStartingStackBb() : 0);
        return dto;
    }

    private DpRoomRegistry buildRowFromRoom(DpRoom room) {
        DpRoomRegistry row = new DpRoomRegistry();
        row.setRoomId(room.getRoomId());
        row.setGameCode(GAME_CODE_DP);
        row.setShardId(gameInstanceProperties.getShardId());
        row.setWsRoute(gameInstanceProperties.getPublicWsUrl());
        row.setStatus(room.isPlaying() ? 1 : 0);
        row.setHasPassword(room.isPasswordProtected() ? 1 : 0);
        row.setMaxSeats(DEFAULT_MAX_SEATS);
        row.setPlayerCount(countTablePlayers(room));
        row.setSpectatorCount(room.getSpectators() != null ? room.getSpectators().size() : 0);
        row.setOwnerNickname(room.getOwner());
        row.setSmallBlindChips(room.getSmallBlindChips());
        row.setBigBlindChips(room.getBigBlindChips());
        row.setStartingStackBb(room.getStartingStackBb());
        return row;
    }

    private static int countTablePlayers(DpRoom room) {
        return (int) room.getPlayers().stream()
                .filter(p -> p != null && !p.isLeftThisHand())
                .count();
    }
/**
 * 写入Redis路由
 * @param row
 */
    private void writeRedisRoute(DpRoomRegistry row) {
        if (row == null || row.getRoomId() == null) {
            return;
        }
        try {
            DpRoomRoutePayload p = new DpRoomRoutePayload();
            p.setRoomId(row.getRoomId());
            p.setShardId(row.getShardId());
            p.setWsRoute(row.getWsRoute());
            p.setStatus(row.getStatus() != null ? row.getStatus() : 0);
            p.setPlayerCount(row.getPlayerCount() != null ? row.getPlayerCount() : 0);
            p.setSpectatorCount(row.getSpectatorCount() != null ? row.getSpectatorCount() : 0);
            p.setPasswordProtected(row.getHasPassword() != null && row.getHasPassword() != 0);
            String json = objectMapper.writeValueAsString(p);
            int ttl = Math.max(30, gameInstanceProperties.getRouteRedisTtlSeconds());
            stringRedisTemplate.opsForValue().set(REDIS_ROUTE_PREFIX + row.getRoomId(), json, ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("redis write room route failed roomId={}: {}", row.getRoomId(), e.getMessage());
        }
    }
}
