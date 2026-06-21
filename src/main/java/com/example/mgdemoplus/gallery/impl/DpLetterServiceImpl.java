package com.example.mgdemoplus.gallery.impl;

import com.example.mgdemoplus.gallery.DpLetterService;
import com.example.mgdemoplus.gallery.entity.DpGalleryLetter;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.gallery.mapper.DpLetterMapper;
import com.example.mgdemoplus.moderation.DpSensitiveWordService;
import com.example.mgdemoplus.security.DpCurrentUserSupport;
import com.example.mgdemoplus.user.impl.DpUserServiceImpl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DpLetterServiceImpl implements DpLetterService {

    @Autowired
    private DpLetterMapper dpLetterMapper;
    @Autowired
    private DpUserMapper dpUserMapper;
    @Autowired
    private DpCurrentUserSupport currentUserSupport;
    @Autowired
    private DpSensitiveWordService sensitiveWordService;

    @Override
    public int writeLetter(String letter) {
        Integer userId = currentUserSupport.requireUserId();
        if (userId == null) {
            return WRITE_FAIL;
        }
        String content = letter != null ? letter.trim() : "";
        if (content.isEmpty()) {
            return WRITE_FAIL;
        }
        if (sensitiveWordService.containsSensitive(content)) {
            return WRITE_SENSITIVE;
        }
        return dpLetterMapper.upsert(userId, content) >= 1 ? WRITE_OK : WRITE_FAIL;
    }

    @Override
    public DpGalleryLetter getMyLetter() {
        Integer userId = currentUserSupport.requireUserId();
        if (userId == null) {
            return null;
        }
        return getLetterByUserId(userId);
    }

    @Override
    public DpGalleryLetter getLetterByUserId(int userId) {
        if (dpUserMapper.selectById(userId) == null) {
            return null;
        }
        return dpLetterMapper.selectByUserId(userId);
    }

    @Override
    public int clearMyLetter() {
        Integer userId = currentUserSupport.requireUserId();
        if (userId == null) {
            return WRITE_FAIL;
        }
        return dpLetterMapper.deleteByUserId(userId) >= 0 ? WRITE_OK : WRITE_FAIL;
    }
}
