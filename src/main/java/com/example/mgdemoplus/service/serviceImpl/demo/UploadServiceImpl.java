package com.example.mgdemoplus.service.serviceImpl.demo;

import com.example.mgdemoplus.mapper.demo.UploadMapper;
import com.example.mgdemoplus.service.demo.UploadService;

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
