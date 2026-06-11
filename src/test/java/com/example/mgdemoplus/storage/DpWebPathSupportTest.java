package com.example.mgdemoplus.storage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DpWebPathSupportTest {

    @Test
    void webPathToObjectKey_stripsLeadingSlash() {
        assertThat(DpWebPathSupport.webPathToObjectKey("/images/12.jpg")).isEqualTo("images/12.jpg");
        assertThat(DpWebPathSupport.webPathToObjectKey("/music/a.mp3")).isEqualTo("music/a.mp3");
    }

    @Test
    void isValidWebPath_rejectsTraversal() {
        assertThat(DpWebPathSupport.isValidWebPath("/images/12.jpg")).isTrue();
        assertThat(DpWebPathSupport.isValidWebPath("/images/../etc/passwd")).isFalse();
        assertThat(DpWebPathSupport.isValidWebPath("/images/sub/12.jpg")).isFalse();
        assertThat(DpWebPathSupport.isValidWebPath("/unknown/x")).isFalse();
    }

    @Test
    void guessContentType_mapsCommonExtensions() {
        assertThat(DpWebPathSupport.guessContentType("/images/a.png")).isEqualTo("image/png");
        assertThat(DpWebPathSupport.guessContentType("/music/b.mp3")).isEqualTo("audio/mpeg");
        assertThat(DpWebPathSupport.guessContentType("/files/c.zip")).isEqualTo("application/zip");
    }
}
