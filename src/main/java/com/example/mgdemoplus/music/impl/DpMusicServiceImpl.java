package com.example.mgdemoplus.music.impl;

import com.example.mgdemoplus.music.DpMusicService;
import com.example.mgdemoplus.music.entity.DpMusicTrack;
import com.example.mgdemoplus.music.mapper.DpMusicTrackMapper;
import com.example.mgdemoplus.music.cache.DpRedisListCacheService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DpMusicServiceImpl implements DpMusicService {
    @Autowired
    private DpMusicTrackMapper dpMusicTrackMapper;
    @Autowired
    private DpRedisListCacheService listCacheService;

    public int insert(DpMusicTrack row) {
        int n = dpMusicTrackMapper.insert(row);
        if (n > 0) {
            listCacheService.evictMusicList();
        }
        return n;
    }

    public List<DpMusicTrack> listEnabled() {
        return listCacheService.getMusicList(() -> dpMusicTrackMapper.listEnabled());// 这种写法是啥意思？答案：这是Java
                                                                                     // 8引入的函数式编程特性，使用Lambda表达式创建了一个Supplier<List<DpMusicTrack>>对象。
        // Supplier<List<DpMusicTrack>>是一个函数式接口，表示一个不接受参数但返回List<DpMusicTrack>的函数。
        // dpMusicTrackMapper.listEnabled()是一个方法引用，表示调用dpMusicTrackMapper的listEnabled方法。
        // listCacheService.getMusicList(() ->
        // dpMusicTrackMapper.listEnabled())表示调用listCacheService的getMusicList方法，并传入一个Supplier<List<DpMusicTrack>>对象。
        // getMusicList方法会先检查缓存中是否存在曲库上架列表，如果存在则直接返回缓存中的列表，否则会调用Supplier<List<DpMusicTrack>>对象的get方法，即调用dpMusicTrackMapper的listEnabled方法，并返回列表。
        // 最后，返回列表。
    }
}
