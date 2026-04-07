package com.example.mgdemoplus.service.serviceImpl.dp;

import com.example.mgdemoplus.mapper.dp.DpMusicTrackMapper;
import com.example.mgdemoplus.service.dp.DpMusicService;
import com.example.mgdemoplus.entity.dp.DpMusicTrack;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DpMusicServiceImpl implements DpMusicService{
    @Autowired
    private DpMusicTrackMapper dpMusicTrackMapper;
    public int insert(DpMusicTrack row){
        return dpMusicTrackMapper.insert(row);
    }
    public List<DpMusicTrack> listEnabled(){
        return dpMusicTrackMapper.listEnabled();
    }
}
