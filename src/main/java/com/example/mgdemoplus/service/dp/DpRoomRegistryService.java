package com.example.mgdemoplus.service.dp;

import com.example.mgdemoplus.config.DpGameInstanceProperties;
import com.example.mgdemoplus.dto.DpPublicRoomsPageDTO;
import com.example.mgdemoplus.dto.DpRoomDTO;
import com.example.mgdemoplus.dto.DpRoomRoutePayload;
import com.example.mgdemoplus.entity.dp.DpRoom;
import com.example.mgdemoplus.entity.dp.DpRoomRegistry;
import com.example.mgdemoplus.mapper.dp.DpRoomRegistryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 房间注册：MySQL 持久化大厅列表，Redis 缓存按房间号路由（方案二：每节点 {@link DpGameInstanceProperties#getPublicWsUrl()}）。
 * <p>
 * 大厅分页缓存失效：凡会改变「大厅列表」所依赖的 {@code dp_room_registry} 行时，必须调用
 * {@link #invalidatePublicRoomsPageCache()}（内部 revision + 删除分页 data 键）。集中入口：
 * <ul>
 *   <li>{@link #registerNewRoom} — 创建房间内存成功后插入注册表（{@code DpRoomServiceImpl#createRoom} 等）</li>
 *   <li>{@link #syncFromRoom} — 人数/房主/盲注/是否游戏中/密码等变更（加入房间、开局、踢人、退房主移交、exit 等）</li>
 *   <li>{@link #removeFromRegistry} — 关房并从大厅消失（无人接盘、房间销毁等）</li>
 * </ul>
 * 业务侧仅通过 {@link com.example.mgdemoplus.service.serviceImpl.dp.DpRoomServiceImpl} 调用上述三处，无旁路写表。
 */
@Service
public class DpRoomRegistryService {

    private static final Logger log = LoggerFactory.getLogger(DpRoomRegistryService.class);
    private static final String GAME_CODE_DP = "DP";
    private static final int DEFAULT_MAX_SEATS = 9;
    private static final String REDIS_ROUTE_PREFIX = "dp:room:route:";
    /** 递增后使大厅分页缓存键空间切换，旧键自然过期 */
    private static final String REDIS_PUBLIC_ROOMS_REV = "mgdemo:cache:dpRoom:publicRooms:rev";
    private static final String REDIS_PUBLIC_ROOMS_DATA_PREFIX = "mgdemo:cache:dpRoom:publicRooms:data:";

    private final DpRoomRegistryMapper registryMapper;
    private final DpGameInstanceProperties gameInstanceProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final int publicRoomsCacheTtlSeconds;

    public DpRoomRegistryService(
            DpRoomRegistryMapper registryMapper,
            DpGameInstanceProperties gameInstanceProperties,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            @Value("${mgdemoplus.cache.dp-room-public-rooms-ttl-seconds:20}") int publicRoomsCacheTtlSeconds) {
        this.registryMapper = registryMapper;
        this.gameInstanceProperties = gameInstanceProperties;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.publicRoomsCacheTtlSeconds = Math.max(1, publicRoomsCacheTtlSeconds);
    }

    public void registerNewRoom(DpRoom room) {
        if (room == null || room.getRoomId() == null) {
            return;
        }
        try {
            DpRoomRegistry row = buildRowFromRoom(room);
            registryMapper.insert(row);
            writeRedisRoute(row);
            invalidatePublicRoomsPageCache();
        } catch (Exception e) {
            log.warn("dp_room_registry insert failed roomId={} (表未建或 DB 不可用时可忽略): {}", room.getRoomId(), e.getMessage());
        }
    }

    /**
     * 从房间同步到注册表。UPDATE 未命中时：若尚无行则补 {@link #registerNewRoom}；若行已存在（含已关房），仍失效大厅缓存，避免列表滞留。
     */
    public void syncFromRoom(DpRoom room) {
        if (room == null || room.getRoomId() == null) {
            return;
        }
        try {
            DpRoomRegistry row = buildRowFromRoom(room);
            int n = registryMapper.updateSnapshot(row);
            if (n > 0) {
                invalidatePublicRoomsPageCache();
                DpRoomRegistry fresh = registryMapper.selectByRoomId(room.getRoomId());
                if (fresh != null) {
                    writeRedisRoute(fresh);
                }
            } else {
                DpRoomRegistry existing = registryMapper.selectByRoomId(room.getRoomId());
                if (existing == null) {
                    registerNewRoom(room);
                } else {
                    invalidatePublicRoomsPageCache();
                }
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
            invalidatePublicRoomsPageCache();
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

    /**
     * 大厅分页：先尝试 Redis 整页缓存（按 revision + page + pageSize），失败或未命中则查 MySQL。
     */
    public DpPublicRoomsPageDTO listPublicRoomsPage(int page, int pageSize) {
        int p = Math.max(1, page);
        int ps = pageSize < 1 ? 20 : Math.min(100, pageSize);
        long rev = currentPublicRoomsCacheRevision();
        String cacheKey = REDIS_PUBLIC_ROOMS_DATA_PREFIX + rev + ":" + p + ":" + ps;
        try {
            String cached = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cached != null && !cached.isEmpty()) {
                return objectMapper.readValue(cached, DpPublicRoomsPageDTO.class);
            }
        } catch (Exception e) {
            log.debug("public rooms cache read failed: {}", e.getMessage());
        }

        try {
            PageHelper.startPage(p, ps);
            List<DpRoomRegistry> rows = registryMapper.selectActiveForPublicHall();
            PageInfo<DpRoomRegistry> pageInfo = new PageInfo<>(rows);
            long total = pageInfo.getTotal();
            List<DpRoomDTO> list = (rows == null || rows.isEmpty())
                    ? Collections.emptyList()
                    : rows.stream().map(this::toDto).collect(Collectors.toList());
            DpPublicRoomsPageDTO dto = new DpPublicRoomsPageDTO();
            dto.setList(list);
            dto.setTotal(total);
            dto.setPage(p);
            dto.setPageSize(ps);
            try {
                String json = objectMapper.writeValueAsString(dto);
                int ttl = Math.max(1, publicRoomsCacheTtlSeconds);
                stringRedisTemplate.opsForValue().set(cacheKey, json, ttl, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.debug("public rooms cache write failed: {}", e.getMessage());
            }
            return dto;
        } catch (Exception e) {
            log.warn("dp_room_registry page query failed: {}", e.getMessage());
            DpPublicRoomsPageDTO empty = new DpPublicRoomsPageDTO();
            empty.setList(Collections.emptyList());
            empty.setTotal(0);
            empty.setPage(p);
            empty.setPageSize(ps);
            return empty;
        }
    }

    private long currentPublicRoomsCacheRevision() {
        try {
            String s = stringRedisTemplate.opsForValue().get(REDIS_PUBLIC_ROOMS_REV);
            if (s == null || s.isEmpty()) {
                return 0L;
            }
            return Long.parseLong(s.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 大厅分页缓存失效：递增 revision（新读走新 key 空间）并 SCAN 删除 {@value #REDIS_PUBLIC_ROOMS_DATA_PREFIX}*，
     * 避免旧页仅靠 TTL 才消失。revision 键本身不删。Redis 异常时仅打日志，不抛。
     */
    private void invalidatePublicRoomsPageCache() {
        try {
            //当有更新的时候，更新版本号，因为缓存键里有rev,所以更新后旧的房间数据全部失效，这时候删掉旧缓存，腾出空间写入新数据
            stringRedisTemplate.opsForValue().increment(REDIS_PUBLIC_ROOMS_REV, 1L);
        } catch (Exception e) {
            log.debug("redis bump public rooms cache revision failed: {}", e.getMessage());
        }
        deletePublicRoomsDataCacheKeys();
    }
/*
 * 删除大厅分页缓存
 */
    private void deletePublicRoomsDataCacheKeys() {
        try {
            List<String> batch = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(REDIS_PUBLIC_ROOMS_DATA_PREFIX + "*")
                    .count(200)
                    .build();
            try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    batch.add(cursor.next());
                    if (batch.size() >= 400) {
                        stringRedisTemplate.delete(batch);
                        batch.clear();
                    }
                }
            }
            if (!batch.isEmpty()) {
                stringRedisTemplate.delete(batch);
            }
        } catch (Exception e) {
            log.debug("redis scan delete public rooms page cache keys failed: {}", e.getMessage());
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
