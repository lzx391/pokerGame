package com.example.mgdemoplus.service.serviceImpl.dp;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mgdemoplus.bo.DpRoomBO;
import com.example.mgdemoplus.bo.DpRoomLobbySearchParamBO;
import com.example.mgdemoplus.entity.dp.DpPlayer;
import com.example.mgdemoplus.entity.dp.DpRoom;
import com.example.mgdemoplus.entity.dp.DpRoomLobby;
import com.example.mgdemoplus.mapper.dp.DpRoomLobbyMapper;
import com.example.mgdemoplus.mapper.dp.DpRoomLobbyMpMapper;
import com.example.mgdemoplus.service.dp.DpRoomHallService;
import com.example.mgdemoplus.vo.DpRoomPublicRoomsPageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
@Service
public class DpRoomHallServiceImpl implements DpRoomHallService {
    private static final Logger log = LoggerFactory.getLogger(DpRoomHallServiceImpl.class);

    private static final String REV_KEY = "mgdemo:cache:dpRoom:publicRooms:rev";
    private static final String DATA_PREFIX = "mgdemo:cache:dpRoom:publicRooms:data:";

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_ROOM_ID_LEN = 32;

    private final DpRoomLobbyMapper dpRoomLobbyMapper;
    private final DpRoomLobbyMpMapper dpRoomLobbyMpMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final long dataTtlSeconds;

    public DpRoomHallServiceImpl(
            DpRoomLobbyMapper dpRoomLobbyMapper,
            DpRoomLobbyMpMapper dpRoomLobbyMpMapper,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            @Value("${mgdemoplus.cache.dp-room-public-rooms-ttl-seconds:120}") long dataTtlSeconds) {
        this.dpRoomLobbyMapper = dpRoomLobbyMapper;
        this.dpRoomLobbyMpMapper = dpRoomLobbyMpMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.dataTtlSeconds = Math.max(30L, dataTtlSeconds);
    }

    @Override
    public void upsertRoomSummary(DpRoomBO room) {
        if (room == null || room.getRoomId() == null || room.getRoomId().isEmpty()) {
            return;
        }
        try {
            DpRoom summary = toSummary(room);
            int updated = dpRoomLobbyMapper.updateRoomSummary(summary);
            if (updated <= 0) {
                dpRoomLobbyMapper.insertRoomSummary(summary);
            }
            bumpRevisionAndCleanupCache();
        } catch (Exception e) {
            log.warn("upsert room lobby failed for roomId={}, ignore: {}", room.getRoomId(), e.toString());
        }
    }

    @Override
    public void deleteRoomSummary(String roomId) {

        if (roomId == null || roomId.isEmpty()) {
            return;
        }
        try {
            dpRoomLobbyMapper.deleteRoomSummaryByRoomId(roomId);
            bumpRevisionAndCleanupCache();
        } catch (Exception e) {
            log.warn("delete room lobby failed for roomId={}, ignore: {}", roomId, e.toString());
        }
    }

    @Override
    public DpRoomPublicRoomsPageVO getPublicRoomsPage(int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE;
        safeSize = Math.min(MAX_PAGE_SIZE, safeSize);

        try {
            String rev = safeGetRevision();
            String cacheKey = buildDataKey(rev, safePage, safeSize);
            String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null && !cachedJson.isEmpty()) {
                return objectMapper.readValue(cachedJson, DpRoomPublicRoomsPageVO.class);
            }
            DpRoomPublicRoomsPageVO fresh = queryPageFromDb(safePage, safeSize);
            safeWriteDataCache(cacheKey, fresh);
            return fresh;
        } catch (DataAccessException e) {
            log.warn("query public rooms failed by db issue, fallback empty: {}", e.toString());
            return DpRoomPublicRoomsPageVO.empty(safePage, safeSize);
        } catch (Exception e) {
            log.warn("query public rooms failed, fallback empty: {}", e.toString());
            return DpRoomPublicRoomsPageVO.empty(safePage, safeSize);
        }
    }

    @Override
    public DpRoomPublicRoomsPageVO queryPublicRoomsFromDb(DpRoomLobbySearchParamBO param) {
        DpRoomLobbySearchParamBO p = param != null ? param : new DpRoomLobbySearchParamBO();
        int safePage = Math.max(1, p.getPage());
        int safeSize = p.getPageSize() > 0 ? p.getPageSize() : DEFAULT_PAGE_SIZE;
        safeSize = Math.min(MAX_PAGE_SIZE, safeSize);
        try {
            LambdaQueryWrapper<DpRoomLobby> w = new LambdaQueryWrapper<>();
            w.eq(DpRoomLobby::getRoomStatus, 0);
            if (p.getRoomId() != null && !p.getRoomId().isBlank()) {
                String t = p.getRoomId().trim();
                if (t.length() > MAX_ROOM_ID_LEN) {
                    t = t.substring(0, MAX_ROOM_ID_LEN);
                }
                w.eq(DpRoomLobby::getRoomId, t);
            }
            if (p.getMinBigBlindChips() != null) {
                w.ge(DpRoomLobby::getBigBlindChips, p.getMinBigBlindChips());
            }
            if (p.getMaxBigBlindChips() != null) {
                w.le(DpRoomLobby::getBigBlindChips, p.getMaxBigBlindChips());
            }
            if (p.getMinPlayerCount() != null) {
                w.ge(DpRoomLobby::getPlayerCount, p.getMinPlayerCount());
            }
            if (p.getMaxPlayerCount() != null) {
                w.le(DpRoomLobby::getPlayerCount, p.getMaxPlayerCount());
            }
            if (p.getPasswordProtected() != null) {
                w.eq(DpRoomLobby::getPasswordProtected, p.getPasswordProtected());
            }
            w.orderByDesc(DpRoomLobby::getCreatedAt);
            Page<DpRoomLobby> result = dpRoomLobbyMpMapper.selectPage(new Page<>(safePage, safeSize), w);
            List<DpRoom> vos = new ArrayList<>();
            for (DpRoomLobby row : result.getRecords()) {
                vos.add(lobbyRowToVo(row));
            }
            DpRoomPublicRoomsPageVO out = new DpRoomPublicRoomsPageVO();
            out.setList(vos);
            out.setTotal(result.getTotal());
            out.setPage(safePage);
            out.setPageSize(safeSize);
            return out;
        } catch (DataAccessException e) {
            log.warn("query public rooms (db filter) failed: {}", e.toString());
            return DpRoomPublicRoomsPageVO.empty(safePage, safeSize);
        } catch (Exception e) {
            log.warn("query public rooms (db filter) failed: {}", e.toString());
            return DpRoomPublicRoomsPageVO.empty(safePage, safeSize);
        }
    }

    private DpRoom lobbyRowToVo(DpRoomLobby row) {
        DpRoom dto = new DpRoom();
        dto.setRoomId(row.getRoomId());
        dto.setOwner(row.getOwnerNickname() != null ? row.getOwnerNickname() : "");
        dto.setPlayerSize(row.getPlayerCount() != null ? row.getPlayerCount() : 0);
        dto.setSmallBlindChips(row.getSmallBlindChips() != null ? row.getSmallBlindChips() : 0);
        dto.setBigBlindChips(row.getBigBlindChips() != null ? row.getBigBlindChips() : 0);
        dto.setStartingStackBb(row.getStartingStackBb() != null ? row.getStartingStackBb() : 0);
        dto.setPasswordProtected(Boolean.TRUE.equals(row.getPasswordProtected()));
        return dto;
    }

    private DpRoomPublicRoomsPageVO queryPageFromDb(int page, int pageSize) {
        PageHelper.startPage(page, pageSize);
        List<DpRoom> rows = dpRoomLobbyMapper.selectActiveRoomSummaries();
        PageInfo<DpRoom> pageInfo = new PageInfo<>(rows);
        DpRoomPublicRoomsPageVO out = new DpRoomPublicRoomsPageVO();
        out.setList(rows == null ? new ArrayList<>() : rows);
        out.setTotal(pageInfo.getTotal());
        out.setPage(page);
        out.setPageSize(pageSize);
        return out;
    }

    private void safeWriteDataCache(String key, DpRoomPublicRoomsPageVO payload) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(payload), dataTtlSeconds, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("cache public rooms data failed key={}, ignore: {}", key, e.toString());
        }
    }

    private String safeGetRevision() {
        try {
            String rev = stringRedisTemplate.opsForValue().get(REV_KEY);
            if (rev != null && !rev.isEmpty()) {
                return rev;
            }
            Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(REV_KEY, "1");
            if (Boolean.TRUE.equals(ok)) {
                return "1";
            }
            String after = stringRedisTemplate.opsForValue().get(REV_KEY);
            return (after == null || after.isEmpty()) ? "1" : after;
        } catch (Exception e) {
            log.warn("get public rooms revision failed, fallback to 1: {}", e.toString());
            return "1";
        }
    }

    private void bumpRevisionAndCleanupCache() {
        try {
            // 状态变更后直接 INCR rev，后续读请求会拼接新 rev 的分页缓存键。
            stringRedisTemplate.opsForValue().increment(REV_KEY, 1L);
        } catch (Exception e) {
            log.warn("bump public rooms revision failed, skip cache cleanup: {}", e.toString());
            return;
        }

        // 按约定删除所有旧 data 页缓存；失败仅日志，不阻断业务。
        try {
            List<String> keys = scanDataKeys();
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("cleanup stale public rooms cache failed, ignore: {}", e.toString());
        }
    }

    private List<String> scanDataKeys() {
        return stringRedisTemplate.execute((RedisConnection connection) -> {
            List<String> keys = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions().match(DATA_PREFIX + "*").count(200).build();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    byte[] raw = cursor.next();
                    if (raw != null && raw.length > 0) {
                        keys.add(new String(raw, StandardCharsets.UTF_8));
                    }
                }
            }
            return keys;
        });
    }

    private String buildDataKey(String rev, int page, int pageSize) {
        return DATA_PREFIX + rev + ":" + page + ":" + pageSize;
    }
//以下代码作用是将DpRoom对象转换为DpRoomVO对象，用于大厅展示所有房间（房间号 / 房主 / 在线人数）
    private DpRoom toSummary(DpRoomBO room) {
        DpRoom dto = new DpRoom();
        dto.setRoomId(room.getRoomId());
        dto.setOwner(room.getOwner());
        int playerSize = 0;
        if (room.getPlayers() != null) {
            for (DpPlayer p : room.getPlayers()) {
                if (p != null && !p.isLeftThisHand()) {
                    playerSize++;
                }
            }
        }
        dto.setPlayerSize(playerSize);
        dto.setSmallBlindChips(room.getSmallBlindChips());
        dto.setBigBlindChips(room.getBigBlindChips());
        dto.setStartingStackBb(room.getStartingStackBb());
        dto.setPasswordProtected(room.isPasswordProtected());
        return dto;
    }
}
