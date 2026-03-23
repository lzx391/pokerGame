package com.example.mgdemoplus.service.serviceImpl;

import com.example.mgdemoplus.mapper.UploadMapper;
import com.example.mgdemoplus.service.UploadService;
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
