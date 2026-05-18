package com.example.mgdemoplus.lobby.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.lobby.bo.DpRoomLobbySearchParamBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.common.entity.DpRoom;
import com.example.mgdemoplus.lobby.entity.DpRoomLobby;
import com.example.mgdemoplus.lobby.mapper.DpRoomLobbyMapper;
import com.example.mgdemoplus.lobby.mapper.DpRoomLobbyMpMapper;
import com.example.mgdemoplus.lobby.DpRoomHallService;
import com.example.mgdemoplus.lobby.vo.DpRoomPublicRoomsPageVO;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
@Service
public class DpRoomHallServiceImpl implements DpRoomHallService {
    private static final Logger log = LoggerFactory.getLogger(DpRoomHallServiceImpl.class);

    private static final String REV_KEY = "mgdemo:cache:dpRoom:publicRooms:rev";
    private static final String DATA_PREFIX = "mgdemo:cache:dpRoom:publicRooms:data:";
    /** 筛选分页结果：{@code mgdemo:cache:dpRoom:lobbyQuery:data:{rev}:{paramHash}} */
    private static final String LOBBY_QUERY_DATA_PREFIX = "mgdemo:cache:dpRoom:lobbyQuery:data:";

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
            // 先更新数据库，再刷新Redis缓存，保证数据一致性。只有数据库操作成功后才更新缓存，防止不一致。
            DpRoom summary = toSummary(room);
            int updated = dpRoomLobbyMapper.updateRoomSummary(summary);
            if (updated <= 0) {
                dpRoomLobbyMapper.insertRoomSummary(summary);
            }
            // 数据库写入完成后再刷新相关Redis缓存，避免缓存提前导致并发读到脏数据
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
//通过对比单实例里房间内存的房间和数据库里的所有“活跃”房间，从而找到没有正常关闭的幽灵房间
    @Override
    public int reconcileLobbyWithRuntimeRoomIds(Set<String> runtimeRoomIds) {
        Set<String> live = runtimeRoomIds != null ? runtimeRoomIds : Collections.emptySet();
        try {
            List<String> dbIds = dpRoomLobbyMapper.selectActiveLobbyRoomIds();//获取数据库的所有房间
            if (dbIds == null || dbIds.isEmpty()) {
                return 0;
            }
            int n = 0;
            for (String id : dbIds) {
                if (id == null || id.isEmpty()) {
                    continue;
                }
                if (!live.contains(id)) {
                    dpRoomLobbyMapper.deleteRoomSummaryByRoomId(id);
                    n++;
                }
            }
            if (n > 0) {
                bumpRevisionAndCleanupCache();
                log.info("dp_room_lobby reconcile: soft-deleted {} ghost row(s)", n);
            }
            return n;
        } catch (Exception e) {
            log.warn("reconcileLobbyWithRuntimeRoomIds failed: {}", e.toString());
            return 0;
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
            String rev = safeGetRevision();
            String canonical = buildLobbyQueryCanonicalString(p, safePage, safeSize);
            String paramHash = sha256HexPrefix16(canonical);
            String cacheKey = LOBBY_QUERY_DATA_PREFIX + rev + ":" + paramHash;
            String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null && !cachedJson.isEmpty()) {
                return objectMapper.readValue(cachedJson, DpRoomPublicRoomsPageVO.class);
            }
            DpRoomPublicRoomsPageVO fresh = executeFilteredLobbyPageQuery(p, safePage, safeSize);
            safeWriteDataCache(cacheKey, fresh);
            return fresh;
        } catch (DataAccessException e) {
            log.warn("query public rooms (db filter) failed: {}", e.toString());
            return DpRoomPublicRoomsPageVO.empty(safePage, safeSize);
        } catch (Exception e) {
            log.warn("query public rooms (db filter) failed: {}", e.toString());
            return DpRoomPublicRoomsPageVO.empty(safePage, safeSize);
        }
    }

    /**
     * 与 {@link #executeFilteredLobbyPageQuery} 中 WHERE 条件一致，用于缓存键指纹；格式变更时请升版本前缀（v1→v2）。
     */
    private static String buildLobbyQueryCanonicalString(DpRoomLobbySearchParamBO p, int safePage, int safeSize) {
        String roomPart = "";
        if (p.getRoomId() != null && !p.getRoomId().isBlank()) {
            String t = p.getRoomId().trim();
            if (t.length() > MAX_ROOM_ID_LEN) {
                t = t.substring(0, MAX_ROOM_ID_LEN);
            }
            roomPart = t;
        }
        String minBb = p.getMinBigBlindChips() != null ? Integer.toString(p.getMinBigBlindChips()) : "";
        String maxBb = p.getMaxBigBlindChips() != null ? Integer.toString(p.getMaxBigBlindChips()) : "";
        String minPc = p.getMinPlayerCount() != null ? Integer.toString(p.getMinPlayerCount()) : "";
        String maxPc = p.getMaxPlayerCount() != null ? Integer.toString(p.getMaxPlayerCount()) : "";
        String pwd;
        if (p.getPasswordProtected() == null) {
            pwd = "";
        } else {
            pwd = p.getPasswordProtected() ? "1" : "0";
        }
        return "v1|" + safePage + "|" + safeSize + "|" + roomPart + "|" + minBb + "|" + maxBb + "|" + minPc + "|" + maxPc + "|" + pwd;
    }

    private static String sha256HexPrefix16(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private DpRoomPublicRoomsPageVO executeFilteredLobbyPageQuery(DpRoomLobbySearchParamBO p, int safePage, int safeSize) {
        // 创建一个 LambdaQueryWrapper 对象，参数泛型指定为 DpRoomLobby，这个对象用于构造数据库查询的各种条件。
        LambdaQueryWrapper<DpRoomLobby> w = new LambdaQueryWrapper<>();
        // 添加一个等值条件，指定 roomStatus 字段的值等于 0。DpRoomLobby::getRoomStatus 是方法引用，指定字段。
        w.eq(DpRoomLobby::getRoomStatus, 0);

        // 判断参数 p 的 roomId 字段不为 null 且不为空字符串
        if (p.getRoomId() != null && !p.getRoomId().isBlank()) {
            // 对 roomId 进行去前后空格，并赋值给 t
            String t = p.getRoomId().trim();
            // 如果 t 的长度超过最大长度限制，则截断
            if (t.length() > MAX_ROOM_ID_LEN) {
                t = t.substring(0, MAX_ROOM_ID_LEN);
            }
            // 添加 roomId 等值查询条件
            w.eq(DpRoomLobby::getRoomId, t);
        }
        // 判断最小大盲注不为 null，则添加对应的查询条件（大于等于 minBigBlindChips）
        if (p.getMinBigBlindChips() != null) {
            w.ge(DpRoomLobby::getBigBlindChips, p.getMinBigBlindChips());
        }
        // 判断最大大盲注不为 null，则添加对应的查询条件（小于等于 maxBigBlindChips）
        if (p.getMaxBigBlindChips() != null) {
            w.le(DpRoomLobby::getBigBlindChips, p.getMaxBigBlindChips());
        }
        // 判断最小玩家人数不为 null，则添加查询条件（大于等于 minPlayerCount）
        if (p.getMinPlayerCount() != null) {
            w.ge(DpRoomLobby::getPlayerCount, p.getMinPlayerCount());
        }
        // 判断最大玩家人数不为 null，则添加查询条件（小于等于 maxPlayerCount）
        if (p.getMaxPlayerCount() != null) {
            w.le(DpRoomLobby::getPlayerCount, p.getMaxPlayerCount());
        }
        // 判断是否限定密码保护房，如果有则添加相应的等值特性过滤
        if (p.getPasswordProtected() != null) {
            // 这里的 eq 方法是 MyBatis-Plus 提供的，用于添加等于（=）的条件到查询中。
            // DpRoomLobby::getPasswordProtected 指定了要查询的实体字段（password_protected）。
            // p.getPasswordProtected() 是你的查询参数里限制的密码保护状态（true/false）。
            // 组合起来就是：只查 password_protected 字段等于 p.getPasswordProtected() 的那些房间。
            w.eq(DpRoomLobby::getPasswordProtected, p.getPasswordProtected());
        }
        // 按照创建时间降序排序
        // DpRoomLobby::getCreatedAt 这里的 :: 是 Java 的方法引用语法，等价于 (x) -> x.getCreatedAt()
        // 也就是 "取出 DpRoomLobby 的 createdAt 属性"
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
        int cap = row.getMaxSeatCount() != null ? row.getMaxSeatCount() : DpRoomBO.DEFAULT_MAX_SEAT_COUNT;
        dto.setMaxSeatCount(cap);
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
            List<String> keys = new ArrayList<>();
            keys.addAll(scanKeysWithPrefix(DATA_PREFIX));
            keys.addAll(scanKeysWithPrefix(LOBBY_QUERY_DATA_PREFIX));
            if (!keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("cleanup stale public rooms cache failed, ignore: {}", e.toString());
        }
    }

    private List<String> scanKeysWithPrefix(String prefix) {
        return stringRedisTemplate.execute((RedisConnection connection) -> {
            List<String> keys = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions().match(prefix + "*").count(200).build();
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
        dto.setMaxSeatCount(room.getMaxSeatCount());
        return dto;
    }
}
