package com.example.mgdemoplus.controller;

import com.example.mgdemoplus.download.DpDownloadService;
import com.example.mgdemoplus.download.dto.DpDownloadDeleteRequest;
import com.example.mgdemoplus.download.dto.DpDownloadVerifyAdminPasswordRequest;
import com.example.mgdemoplus.download.entity.DpDownloadAsset;
import com.example.mgdemoplus.room.support.DpExperimentalDeckPresetPasswordGuard;
import com.example.mgdemoplus.storage.DpObjectStorage;
import com.example.mgdemoplus.storage.DpWebPathSupport;
import com.example.mgdemoplus.utils.ResultUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 下载中心：上传写入磁盘 other 目录 + {@code dp_download_asset}；列表与静态文件下载 permitAll。
 * 上传/下架需 JWT + 与实验排牌相同的管理密码。
 */
@RestController
@RequestMapping("/dpDownload")
public class DpDownloadController {

    private static final Set<String> ALLOWED_EXT = Set.of(".exe", ".apk", ".msi", ".zip");

    @Autowired
    private DpDownloadService dpDownloadService;

    @Autowired
    private DpExperimentalDeckPresetPasswordGuard experimentalDeckPresetPasswordGuard;
    @Autowired
    private DpObjectStorage objectStorage;

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

    private static String gateMessage(ResultUtil gate) {
        if (gate == null) {
            return "管理密码校验失败";
        }
        if (gate.getData() != null && gate.getData().get("message") != null) {
            return String.valueOf(gate.getData().get("message"));
        }
        return gate.getMessage() != null ? gate.getMessage() : "管理密码校验失败";
    }

    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "displayName", required = false) String displayName,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
            @RequestParam(value = "userId", required = false) Integer userId,
            @RequestParam(value = "adminPassword", required = false) String adminPassword) throws IOException {
        ResultUtil gate = experimentalDeckPresetPasswordGuard.gate(adminPassword);
        if (gate != null) {
            return ResponseEntity.badRequest().body(Map.of("error", gateMessage(gate)));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请选择文件"));
        }
        String ext = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXT.contains(ext)) {
            return ResponseEntity.badRequest().body(Map.of("error", "仅支持 exe、apk、msi、zip"));
        }

        String storedFilename = UUID.randomUUID() + ext;
        String webPath = DpWebPathSupport.FILES_PREFIX + storedFilename;
        try {
            objectStorage.put(webPath, file.getInputStream(), file.getSize(), DpWebPathSupport.guessContentType(webPath));
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "保存文件失败"));
        }
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

    @PostMapping("/delete")
    public ResultUtil delete(@RequestBody DpDownloadDeleteRequest req) {
        if (req == null || req.getId() == null) {
            return ResultUtil.error().data("message", "缺少资源 ID");
        }
        ResultUtil gate = experimentalDeckPresetPasswordGuard.gate(req.getAdminPassword());
        if (gate != null) {
            return gate;
        }
        DpDownloadAsset existing = dpDownloadService.findById(req.getId());
        if (existing == null || !Boolean.TRUE.equals(existing.getEnabled())) {
            return ResultUtil.error().data("message", "资源不存在或已下架");
        }
        int updated = dpDownloadService.disableById(req.getId());
        if (updated <= 0) {
            return ResultUtil.error().data("message", "下架失败");
        }
        return ResultUtil.ok().data("message", "已下架");
    }

    @PostMapping("/verifyAdminPassword")
    public ResultUtil verifyAdminPassword(@RequestBody DpDownloadVerifyAdminPasswordRequest req) {
        String password = req != null ? req.getAdminPassword() : null;
        ResultUtil gate = experimentalDeckPresetPasswordGuard.gate(password);
        if (gate != null) {
            return gate;
        }
        return ResultUtil.ok().data("message", "管理密码验证通过");
    }

    @GetMapping("/list")
    public List<DpDownloadAsset> list() {
        return dpDownloadService.listEnabled();
    }
}
