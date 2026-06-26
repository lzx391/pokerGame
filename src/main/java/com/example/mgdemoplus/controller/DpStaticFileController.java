package com.example.mgdemoplus.controller;

import com.example.mgdemoplus.storage.DpObjectStorage;
import com.example.mgdemoplus.storage.DpWebPathSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * 由后端代理 {@code /images/**}、{@code /music/**}、{@code /files/**} 读请求（MinIO）。
 */
@RestController
public class DpStaticFileController {

    private static final int IMAGES_CACHE_SECONDS = 31536000;

    private final DpObjectStorage objectStorage;

    public DpStaticFileController(DpObjectStorage objectStorage) {
        this.objectStorage = objectStorage;
    }

    @GetMapping("/images/**")
    public ResponseEntity<InputStreamResource> serveImages(HttpServletRequest request) {
        return serve(request, DpWebPathSupport.IMAGES_PREFIX, true);
    }

    @GetMapping("/music/**")
    public ResponseEntity<InputStreamResource> serveMusic(HttpServletRequest request) {
        return serve(request, DpWebPathSupport.MUSIC_PREFIX, false);
    }

    @GetMapping("/files/**")
    public ResponseEntity<InputStreamResource> serveFiles(HttpServletRequest request) {
        return serve(request, DpWebPathSupport.FILES_PREFIX, false);
    }

    private ResponseEntity<InputStreamResource> serve(
            HttpServletRequest request, String prefix, boolean longCache) {
        String uri = request.getRequestURI();
        if (!uri.startsWith(prefix)) {
            return ResponseEntity.notFound().build();
        }
        String webPath = uri;
        if (!DpWebPathSupport.isValidWebPath(webPath)) {
            return ResponseEntity.badRequest().build();
        }
        Optional<DpObjectStorage.StoredObject> stored = objectStorage.get(webPath);
        if (stored.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        DpObjectStorage.StoredObject obj = stored.get();
        MediaType mediaType = MediaType.parseMediaType(obj.contentType());
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(obj.size()));
        if (longCache) {
            builder.header(HttpHeaders.CACHE_CONTROL, "max-age=" + IMAGES_CACHE_SECONDS);
        }
        return builder.body(new InputStreamResource(obj.stream()));
    }
}
