package com.example.mgdemoplus.service;

import java.util.List;
import java.util.function.Supplier;

import com.example.mgdemoplus.entity.DpMusicTrack;

public interface DpRedisListCacheService {
    /**
     * 获取曲库上架列表
     * @param loader
     * @return
     */
    public List<DpMusicTrack> getMusicList(Supplier<List<DpMusicTrack>> loader);
    /**
     * 删除曲库上架列表缓存
     */

    public void evictMusicList();
}
