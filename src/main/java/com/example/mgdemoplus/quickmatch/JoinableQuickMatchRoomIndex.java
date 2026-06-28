package com.example.mgdemoplus.quickmatch;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 大厅快速匹配：对「无密码、符合快匹空位规则、且尚有快匹可读空位」的公开房，按<b>缺人数</b>建桶，
 * 供 Agent 2 以 O(logN) 选取「越满越优先」的候选桌，避免全表扫描 {@code roomMap}。
 *
 * <p>P2：生产环境以 Redis 为全集群共享索引（{@link QuickMatchRedisKeys}）；单元测试使用无参构造的内存实现。
 *
 * <h2>不变量</h2>
 * <ul>
 *   <li>主索引：{@code dp:qm:join:bucket:{shortage}} ZSET + {@code dp:qm:join:active_buckets}，键为<b>缺几人</b>。</li>
 *   <li>反向表：{@code dp:qm:join:room2bucket} HASH，保证 {@link #addOrRefresh} 幂等迁移、{@link #remove} O(1) 定位。</li>
 *   <li>桶内按 {@code roomId} 字典序（ZSET score=0）；缺人数更小 = 更满。</li>
 *   <li>语义与 {@link DpQuickMatchRoomSemantics} 一致：密码房、已满、超员异常房均<b>不在</b>索引内。</li>
 * </ul>
 *
 * <h2>写路径</h2>
 * <p>Redis 房态在 {@link com.example.mgdemoplus.room.support.DpRedisRoomRegistry#saveAfterMutation} 持房间锁后同步更新本索引；
 * 不再依赖 {@code dp:room:events} 订阅方刷新。
 *
 * <h2>读路径（配对）</h2>
 * <p>{@link #pollBestCandidate} 等变更型读方法约定：调用方已持有 {@link DpQuickMatchPairingLock}。
 *
 * @see DpQuickMatchRoomSemantics
 */
@Component
public class JoinableQuickMatchRoomIndex {

    /**
     * Atomically migrate roomId from old bucket to new shortage (0 = remove only).
     * KEYS[1]=room2bucket, KEYS[2]=active_buckets; ARGV[1]=roomId, ARGV[2]=newShortage, ARGV[3]=bucketPrefix.
     */
    private static final String JOIN_INDEX_UPSERT_LUA = ""
            + "local roomId = ARGV[1]\n"
            + "local newShortage = tonumber(ARGV[2])\n"
            + "local bucketPrefix = ARGV[3]\n"
            + "local oldS = redis.call('HGET', KEYS[1], roomId)\n"
            + "if oldS then\n"
            + "  local oldBucket = bucketPrefix .. oldS\n"
            + "  redis.call('ZREM', oldBucket, roomId)\n"
            + "  if redis.call('ZCARD', oldBucket) == 0 then\n"
            + "    redis.call('ZREM', KEYS[2], oldS)\n"
            + "  end\n"
            + "  redis.call('HDEL', KEYS[1], roomId)\n"
            + "end\n"
            + "if newShortage and newShortage > 0 then\n"
            + "  redis.call('HSET', KEYS[1], roomId, tostring(newShortage))\n"
            + "  local newBucket = bucketPrefix .. tostring(newShortage)\n"
            + "  redis.call('ZADD', newBucket, 0, roomId)\n"
            + "  redis.call('ZADD', KEYS[2], newShortage, tostring(newShortage))\n"
            + "end\n"
            + "return 1\n";

    private static final DefaultRedisScript<Long> JOIN_INDEX_UPSERT_SCRIPT =
            new DefaultRedisScript<>(JOIN_INDEX_UPSERT_LUA, Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final InMemoryJoinableQuickMatchRoomIndex inMemory;

    @Autowired
    public JoinableQuickMatchRoomIndex(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.inMemory = null;
    }

    /** Unit tests without Redis — same semantics as production index. */
    public JoinableQuickMatchRoomIndex() {
        this.stringRedisTemplate = null;
        this.inMemory = new InMemoryJoinableQuickMatchRoomIndex();
    }

    public void addOrRefresh(String roomId, DpRoomBO room, long nowMs) {
        if (roomId == null) {
            return;
        }
        if (inMemory != null) {
            inMemory.addOrRefresh(roomId, room, nowMs);
            return;
        }
        int shortage = 0;
        if (DpQuickMatchRoomSemantics.shouldIndexPublicQuickMatchRoom(room, nowMs)) {
            shortage = DpQuickMatchRoomSemantics.vacancyBucketKeyForIndex(room, nowMs);
            if (shortage <= 0) {
                shortage = 0;
            }
        }
        upsertRedis(roomId, shortage);
    }

    public void remove(String roomId) {
        if (roomId == null) {
            return;
        }
        if (inMemory != null) {
            inMemory.remove(roomId);
            return;
        }
        upsertRedis(roomId, 0);
    }

    /**
     * 自「最满」桶起（缺人数最小），按 roomId 字典序尝试，返回首个满足 {@code rule} 的 roomId，并<strong>从索引移除</strong>该条。
     * 调用方若进房失败，应再次 {@link #addOrRefresh}。若 {@code roomById} 返回 null，视为脏项并从索引剔除后继续。
     */
    public Optional<String> pollBestCandidate(Function<String, DpRoomBO> roomById, Predicate<DpRoomBO> rule) {
        if (roomById == null || rule == null) {
            return Optional.empty();
        }
        if (inMemory != null) {
            return inMemory.pollBestCandidate(roomById, rule);
        }
        List<Integer> buckets = activeShortageBucketsAscending();
        for (int shortage : buckets) {
            List<String> roomIds = roomIdsInBucket(shortage);
            for (String rid : roomIds) {
                DpRoomBO r = roomById.apply(rid);
                if (r == null) {
                    remove(rid);
                    continue;
                }
                if (rule.test(r)) {
                    remove(rid);
                    return Optional.of(rid);
                }
            }
        }
        return Optional.empty();
    }

    /** 当前仍缺人的最小缺额桶键；若无则 empty。 */
    public Optional<Integer> firstVacancyBucket() {
        if (inMemory != null) {
            return inMemory.firstVacancyBucket();
        }
        List<Integer> buckets = activeShortageBucketsAscending();
        if (buckets.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(buckets.get(0));
    }

    /** 只读快照：某缺人数桶内的 roomId（字典序拷贝）。 */
    public List<String> roomIdsInBucket(int shortage) {
        if (inMemory != null) {
            return inMemory.roomIdsInBucket(shortage);
        }
        Set<String> members = stringRedisTemplate.opsForZSet()
                .range(QuickMatchRedisKeys.joinBucketKey(shortage), 0, -1);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(members);
    }

    public int indexedRoomCount() {
        if (inMemory != null) {
            return inMemory.indexedRoomCount();
        }
        Long len = stringRedisTemplate.opsForHash().size(QuickMatchRedisKeys.JOIN_ROOM2BUCKET);
        return len != null ? len.intValue() : 0;
    }

    public void rebuildAll(Map<String, DpRoomBO> roomMap, long nowMs) {
        if (inMemory != null) {
            inMemory.rebuildAll(roomMap, nowMs);
            return;
        }
        clearAllJoinKeys();
        if (roomMap == null || roomMap.isEmpty()) {
            return;
        }
        for (Map.Entry<String, DpRoomBO> e : roomMap.entrySet()) {
            addOrRefresh(e.getKey(), e.getValue(), nowMs);
        }
    }

    private void upsertRedis(String roomId, int shortage) {
        stringRedisTemplate.execute(
                JOIN_INDEX_UPSERT_SCRIPT,
                List.of(QuickMatchRedisKeys.JOIN_ROOM2BUCKET, QuickMatchRedisKeys.JOIN_ACTIVE_BUCKETS),
                roomId,
                String.valueOf(shortage),
                QuickMatchRedisKeys.JOIN_BUCKET_PREFIX);
    }

    private List<Integer> activeShortageBucketsAscending() {
        Set<String> members = stringRedisTemplate.opsForZSet()
                .range(QuickMatchRedisKeys.JOIN_ACTIVE_BUCKETS, 0, -1);
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        List<Integer> out = new ArrayList<>(members.size());
        for (String shortage : members) {
            if (shortage != null && !shortage.isEmpty()) {
                try {
                    out.add(Integer.parseInt(shortage));
                } catch (NumberFormatException ignored) {
                    // skip corrupt member
                }
            }
        }
        return out;
    }

    private void clearAllJoinKeys() {
        Set<String> bucketMembers = stringRedisTemplate.opsForZSet()
                .range(QuickMatchRedisKeys.JOIN_ACTIVE_BUCKETS, 0, -1);
        if (bucketMembers != null) {
            for (String shortage : bucketMembers) {
                if (shortage != null && !shortage.isEmpty()) {
                    stringRedisTemplate.delete(QuickMatchRedisKeys.joinBucketKey(Integer.parseInt(shortage)));
                }
            }
        }
        stringRedisTemplate.delete(QuickMatchRedisKeys.JOIN_ROOM2BUCKET);
        stringRedisTemplate.delete(QuickMatchRedisKeys.JOIN_ACTIVE_BUCKETS);
    }
}
