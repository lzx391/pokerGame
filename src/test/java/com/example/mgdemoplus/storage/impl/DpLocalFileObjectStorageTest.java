package com.example.mgdemoplus.storage.impl;

import com.example.mgdemoplus.storage.DpObjectStorage;
import com.example.mgdemoplus.storage.DpWebPathSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DpLocalFileObjectStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void putGetDelete_roundTripAcrossPrefixes() throws Exception {
        String imagesLoc = "file:" + tempDir.resolve("images").toString().replace('\\', '/') + "/";
        String musicLoc = "file:" + tempDir.resolve("music").toString().replace('\\', '/') + "/";
        String filesLoc = "file:" + tempDir.resolve("files").toString().replace('\\', '/') + "/";
        DpLocalFileObjectStorage storage = new DpLocalFileObjectStorage(imagesLoc, musicLoc, filesLoc);

        String webPath = DpWebPathSupport.IMAGES_PREFIX + "7.png";
        byte[] data = "png-bytes".getBytes(StandardCharsets.UTF_8);
        storage.put(webPath, new ByteArrayInputStream(data), data.length, "image/png");

        assertThat(storage.exists(webPath)).isTrue();
        DpObjectStorage.StoredObject stored = storage.get(webPath).orElseThrow();
        try (InputStream in = stored.stream()) {
            assertThat(in.readAllBytes()).isEqualTo(data);
        }

        List<String> keys = storage.listObjectKeysByPrefix("images/7.");
        assertThat(keys).contains("images/7.png");

        storage.delete(webPath);
        assertThat(storage.exists(webPath)).isFalse();
    }
}
