package com.example.mgdemoplus.service;

import com.example.mgdemoplus.entity.dp.DpMusicTrack;

import java.util.List;

public interface DpMusicService {
    int insert(DpMusicTrack row);
    List<DpMusicTrack> listEnabled(); 
} 
