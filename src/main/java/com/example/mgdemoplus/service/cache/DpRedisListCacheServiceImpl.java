package com.example.mgdemoplus.service.cache;

import com.example.mgdemoplus.entity.DpMusicTrack;
import com.example.mgdemoplus.service.DpRedisListCacheService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 曲库上架列表只读缓存（JSON 存 Redis），减轻 {@code GET /dpMusic/list} 对 MySQL 的重复查询。
 * Redis 不可用时回源，不阻断请求。
 */
@Service
public class DpRedisListCacheServiceImpl implements DpRedisListCacheService{

    private static final Logger log = LoggerFactory.getLogger(DpRedisListCacheServiceImpl.class);

    static final String KEY_MUSIC_LIST = "mgdemo:cache:dpMusic:listEnabled";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private final long musicListTtlSeconds;
    // 这是是什么语法现象？答案：这是Java
    // 8引入的函数式编程特性，使用Lambda表达式创建了一个TypeReference<List<DpMusicTrack>>对象。
    // TypeReference<List<DpMusicTrack>>是一个泛型类，表示一个List<DpMusicTrack>的类型引用。
    // new TypeReference<>() {}是一个匿名内部类，表示创建一个TypeReference<List<DpMusicTrack>>对象。
    // MUSIC_TYPE是一个静态常量，表示一个List<DpMusicTrack>的类型引用。
    // objectMapper.readValue(json, MUSIC_TYPE)表示将json字符串转换为List<DpMusicTrack>对象。
    // objectMapper.writeValueAsString(fresh)表示将List<DpMusicTrack>对象转换为json字符串。
    private static final TypeReference<List<DpMusicTrack>> MUSIC_TYPE = new TypeReference<>() {
    };

    public DpRedisListCacheServiceImpl(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            @Value("${mgdemoplus.cache.music-list-ttl-seconds:300}") long musicListTtlSeconds) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.musicListTtlSeconds = Math.max(1L, musicListTtlSeconds);
    }

    /**
     * 获取曲库上架列表
     * 
     * @param loader
     * @return
     */
    public List<DpMusicTrack> getMusicList(Supplier<List<DpMusicTrack>> loader) {
        try {
            // 缓存存在，从缓存中获取
            String json = stringRedisTemplate.opsForValue().get(KEY_MUSIC_LIST);
            if (json != null && !json.isEmpty()) {
                return objectMapper.readValue(json, MUSIC_TYPE);
            }
        } catch (Exception e) {
            log.warn("Redis get/decode {} failed, loading from source: {}", KEY_MUSIC_LIST, e.toString());
        }
        // 缓存不存在，从数据库加载
        List<DpMusicTrack> fresh = loader.get();

        try {
            stringRedisTemplate.opsForValue().set(
                    KEY_MUSIC_LIST, objectMapper.writeValueAsString(fresh), musicListTtlSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Redis set {} failed, returning uncached list: {}", KEY_MUSIC_LIST, e.toString());
        }
        return fresh;
    }

    /** 曲库有变更（如上传）时删除缓存，下次列表请求回源。 */
    public void evictMusicList() {
        try {
            stringRedisTemplate.delete(KEY_MUSIC_LIST);
        } catch (Exception e) {
            log.warn("Redis evict {} failed, ignoring: {}", KEY_MUSIC_LIST, e.toString());
        }
    }
}
