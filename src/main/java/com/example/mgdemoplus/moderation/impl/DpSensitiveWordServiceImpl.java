package com.example.mgdemoplus.moderation.impl;

import com.example.mgdemoplus.moderation.DpSensitiveWordService;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;

import org.springframework.stereotype.Service;

@Service
public class DpSensitiveWordServiceImpl implements DpSensitiveWordService {

    private final SensitiveWordBs sw;

    public DpSensitiveWordServiceImpl(SensitiveWordBs sw) {
        this.sw = sw;
    }

    @Override
    public boolean containsSensitive(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return sw.contains(text);
    }

    @Override
    public String maskForChat(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return sw.replace(text);
    }
}
