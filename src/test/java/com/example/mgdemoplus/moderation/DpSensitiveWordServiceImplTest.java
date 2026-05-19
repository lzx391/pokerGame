package com.example.mgdemoplus.moderation;

import com.example.mgdemoplus.moderation.impl.DpSensitiveWordServiceImpl;
import com.github.houbb.sensitive.word.bs.SensitiveWordBs;
import com.github.houbb.sensitive.word.support.deny.WordDenys;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DpSensitiveWordServiceImplTest {

    private DpSensitiveWordServiceImpl service;

    @BeforeEach
    void setUp() {
        SensitiveWordBs sw = SensitiveWordBs.newInstance()
                .wordDeny(WordDenys.empty())
                .init();
        sw.addWord(List.of("坏词"));
        service = new DpSensitiveWordServiceImpl(sw);
    }

    @Test
    void containsSensitive_matchesSensitiveNickname() {
        assertTrue(service.containsSensitive("坏词"));
        assertTrue(service.containsSensitive("包含坏词的昵称"));
        assertFalse(service.containsSensitive("正常昵称"));
        assertFalse(service.containsSensitive(""));
        assertFalse(service.containsSensitive(null));
    }

    @Test
    void maskForChat_replacesHitsWithStars() {
        assertEquals("你好**世界", service.maskForChat("你好坏词世界"));
        assertEquals("正常聊天", service.maskForChat("正常聊天"));
        assertEquals("", service.maskForChat(""));
        assertEquals(null, service.maskForChat(null));
    }
}
