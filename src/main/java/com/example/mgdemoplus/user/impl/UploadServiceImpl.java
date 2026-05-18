package com.example.mgdemoplus.user.impl;

import com.example.mgdemoplus.user.UploadService;
import com.example.mgdemoplus.user.mapper.UploadMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UploadServiceImpl implements UploadService {
    @Autowired
    private UploadMapper uploadMapper;
    public int upload(int id, String url){

        return uploadMapper.upload(id,url);
    }
}
