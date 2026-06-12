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

    @Override
    public DpDownloadAsset findById(Long id) {
        if (id == null) {
            return null;
        }
        return dpDownloadAssetMapper.selectById(id);
    }

    @Override
    public int disableById(Long id) {
        if (id == null) {
            return 0;
        }
        return dpDownloadAssetMapper.disableById(id);
    }
}
