package com.example.mgdemoplus.controller.dp;

import com.example.mgdemoplus.entity.dp.DpMusicTrack;
import com.example.mgdemoplus.mapper.dp.DpMusicTrackMapper;
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
 * DP 曲库：上传写入磁盘 + {@code dp_music_track}；列表供对局 BGM 使用。
 */
@RestController
@RequestMapping("/dpMusic")
public class DpMusicController {

    private static final Set<String> ALLOWED_EXT = Set.of(".mp3", ".m4a", ".wav", ".ogg", ".flac");

    @Autowired
    private DpMusicTrackMapper dpMusicTrackMapper;

    @Value("${mgdemoplus.music.file-location:file:P:/javaworkspace/DPGameFiles/music/}")
    private String musicFileLocation;

    private static String toPhysicalDir(String fileLocation) {
        if (fileLocation == null || fileLocation.isBlank()) {
            return "P:/javaworkspace/DPGameFiles/music/";
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

    /**
     * 上传音频并入库；可选展示名、排序、上传人 userId。
     */
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
            return ResponseEntity.badRequest().body(Map.of("error", "仅支持 mp3、m4a、wav、ogg、flac"));
        }

        String dir = toPhysicalDir(musicFileLocation);
        File folder = new File(dir);
        if (!folder.exists() && !folder.mkdirs()) {
            return ResponseEntity.internalServerError().body(Map.of("error", "无法创建音乐目录"));
        }

        String storedFilename = UUID.randomUUID() + ext;
        file.transferTo(new File(dir + storedFilename));

        String webPath = "/music/" + storedFilename;
        String title = StringUtils.hasText(displayName)
                ? displayName.trim()
                : stripExtension(file.getOriginalFilename());
        int order = sortOrder != null ? sortOrder : 0;

        DpMusicTrack row = new DpMusicTrack();
        row.setStoredFilename(storedFilename);
        row.setDisplayName(title);
        row.setWebPath(webPath);
        row.setSortOrder(order);
        row.setUploaderUserId(userId);

        dpMusicTrackMapper.insert(row);

        Map<String, Object> ok = new HashMap<>();
        ok.put("id", row.getId());
        ok.put("webPath", webPath);
        ok.put("displayName", title);
        ok.put("sortOrder", order);
        return ResponseEntity.ok(ok);
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

    @GetMapping("/list")
    public List<DpMusicTrack> list() {
        return dpMusicTrackMapper.listEnabled();
    }
}
