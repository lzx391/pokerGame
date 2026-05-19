package com.example.mgdemoplus.music;

import com.example.mgdemoplus.music.entity.DpMusicTrack;

import java.util.List;

public interface DpMusicService {
    int insert(DpMusicTrack row);
    List<DpMusicTrack> listEnabled(); 
} 
