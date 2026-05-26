package com.example.mgdemoplus.download;

import com.example.mgdemoplus.download.entity.DpDownloadAsset;

import java.util.List;

public interface DpDownloadService {
    int insert(DpDownloadAsset row);

    List<DpDownloadAsset> listEnabled();
}
