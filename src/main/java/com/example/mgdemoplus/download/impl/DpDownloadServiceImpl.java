package com.example.mgdemoplus.download.impl;

import com.example.mgdemoplus.download.DpDownloadService;
import com.example.mgdemoplus.download.entity.DpDownloadAsset;
import com.example.mgdemoplus.download.mapper.DpDownloadAssetMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DpDownloadServiceImpl implements DpDownloadService {

    @Autowired
    private DpDownloadAssetMapper dpDownloadAssetMapper;

    @Override
    public int insert(DpDownloadAsset row) {
        return dpDownloadAssetMapper.insert(row);
    }

    @Override
    public List<DpDownloadAsset> listEnabled() {
        return dpDownloadAssetMapper.listEnabled();
    }
}
