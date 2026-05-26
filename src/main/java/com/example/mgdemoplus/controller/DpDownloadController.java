package com.example.mgdemoplus.controller;

import com.example.mgdemoplus.download.DpDownloadService;
import com.example.mgdemoplus.download.entity.DpDownloadAsset;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 下载中心：上传写入磁盘 other 目录 + {@code dp_download_asset}；列表与静态文件下载 permitAll。
 */
@RestController
@RequestMapping("/dpDownload")
public class DpDownloadController {

    private static final Set<String> ALLOWED_EXT = Set.of(".exe", ".apk", ".msi", ".zip");

    @Autowired
    private DpDownloadService dpDownloadService;

    @Value("${mgdemoplus.files.file-location:file:P:/javaworkspace/DPGameFiles/other/}")
    private String filesFileLocation;

    private static String toPhysicalDir(String fileLocation) {
        if (fileLocation == null || fileLocation.isBlank()) {
            return "P:/javaworkspace/DPGameFiles/other/";
        }
        String s = fileLocation.trim();
        if (s.startsWith("file:")) {
            s = s.substring(5);
        }
        if (!s.endsWith("/") && !s.endsWith("\\")) {
            s = s + "/";
        }
        return s;
    }

    private static String extensionOf(String originalFilename) {
        if (originalFilename == null || originalFilename.isEmpty()) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot >= originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String stripExtension(String name) {
        if (name == null || name.isEmpty()) {
            return "未命名";
        }
        String base = name.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        return base.isEmpty() ? "未命名" : base;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "displayName", required = false) String displayName,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
            @RequestParam(value = "userId", required = false) Integer userId) throws IOException {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请选择文件"));
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXT.contains(ext)) {
            return ResponseEntity.badRequest().body(Map.of("error", "仅支持 exe、apk、msi、zip"));
        }

        String dir = toPhysicalDir(filesFileLocation);
        File folder = new File(dir);
        if (!folder.exists() && !folder.mkdirs()) {
            return ResponseEntity.internalServerError().body(Map.of("error", "无法创建下载文件目录"));
        }

        String storedFilename = UUID.randomUUID() + ext;
        file.transferTo(new File(dir + storedFilename));

        String webPath = "/files/" + storedFilename;
        String title = StringUtils.hasText(displayName)
                ? displayName.trim()
                : stripExtension(file.getOriginalFilename());
        int order = sortOrder != null ? sortOrder : 0;

        DpDownloadAsset row = new DpDownloadAsset();
        row.setStoredFilename(storedFilename);
        row.setDisplayName(title);
        row.setWebPath(webPath);
        row.setSortOrder(order);
        row.setUploaderUserId(userId);

        dpDownloadService.insert(row);

        Map<String, Object> ok = new HashMap<>();
        ok.put("id", row.getId());
        ok.put("webPath", webPath);
        ok.put("displayName", title);
        ok.put("sortOrder", order);
        return ResponseEntity.ok(ok);
    }

    @GetMapping("/list")
    public List<DpDownloadAsset> list() {
        return dpDownloadService.listEnabled();
    }
}
