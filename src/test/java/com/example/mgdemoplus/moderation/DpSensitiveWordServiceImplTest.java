package com.example.mgdemoplus.moderation;

import com.example.mgdemoplus.moderation.impl.DpSensitiveWordServiceImpl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DpSensitiveWordServiceImplTest {

    private final DpSensitiveWordServiceImpl service = new DpSensitiveWordServiceImpl();

    @BeforeEach
    void setWords() {
        ReflectionTestUtils.setField(service, "words", List.of("坏词", "bad"));
    }

    @Test
    void containsSensitive_matchesCompactedNickname() {
        assertTrue(service.containsSensitive("坏_词"));
        assertTrue(service.containsSensitive("BAD"));
        assertFalse(service.containsSensitive("正常昵称"));
    }

    @Test
    void maskForChat_replacesHitsWithStars() {
        assertEquals("你好**世界", service.maskForChat("你好坏词世界"));
        assertEquals("say *** here", service.maskForChat("say bad here"));
    }
}
