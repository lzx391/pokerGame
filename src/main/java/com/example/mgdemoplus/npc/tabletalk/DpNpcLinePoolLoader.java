package com.example.mgdemoplus.npc.tabletalk;

import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 加载 {@code classpath:npc-lines/{key}.yaml}。
 * v2 格式：顶层 {@code happy}/{@code calm}/{@code sad} 各含行动分桶，展平为
 * {@code MOOD_HIGH_*} / {@code MOOD_NEUTRAL_*} / {@code MOOD_LOW_*}（与 {@link com.example.mgdemoplus.npc.mood.NpcMoodState} 一致）。
 */
@Component
public class DpNpcLinePoolLoader {

    private static final Logger log = LoggerFactory.getLogger(DpNpcLinePoolLoader.class);
    private static final String RESOURCE_PREFIX = "npc-lines/";
    private static final double DEFAULT_SPEAK_PROBABILITY = 0.28;
    private static final double DEFAULT_SETTLE_SPEAK_MULTIPLIER = 1.45;

    private static final String[][] MOOD_BLOCK_TO_PREFIX = {
            {"happy", "MOOD_HIGH_"},
            {"calm", "MOOD_NEUTRAL_"},
            {"sad", "MOOD_LOW_"},
    };

    private final ConcurrentHashMap<String, Optional<NpcLinePool>> cache = new ConcurrentHashMap<>();
    private final Yaml yaml = new Yaml();

    /** {@link DpNpcEngine.NpcStyle} → 资源文件名（{@code CALL_STATION} → {@code call.yaml}）。 */
    public static String resourceKeyForStyle(DpNpcEngine.NpcStyle style) {
        if (style == null) {
            return null;
        }
        return switch (style) {
            case CALL_STATION -> "call";
            default -> style.name().toLowerCase(Locale.ROOT);
        };
    }

    public Optional<NpcLinePool> load(DpNpcEngine.NpcStyle style) {
        String key = resourceKeyForStyle(style);
        if (key == null) {
            return Optional.empty();
        }
        return loadByResourceKey(key);
    }

    public Optional<NpcLinePool> loadByResourceKey(String resourceKey) {
        if (resourceKey == null || resourceKey.isBlank()) {
            return Optional.empty();
        }
        String key = resourceKey.trim().toLowerCase(Locale.ROOT);
        return cache.computeIfAbsent(key, this::loadFromClasspath);
    }

    @SuppressWarnings("unchecked")
    private Optional<NpcLinePool> loadFromClasspath(String styleKey) {
        String path = RESOURCE_PREFIX + styleKey + ".yaml";
        ClassPathResource res = new ClassPathResource(path);
        if (!res.exists()) {
            log.warn("npc-lines missing: {}", path);
            return Optional.empty();
        }
        try (InputStream in = res.getInputStream()) {
            Object raw = yaml.load(in);
            if (!(raw instanceof Map<?, ?> map)) {
                log.warn("npc-lines invalid root (not map): {}", path);
                return Optional.empty();
            }
            boolean enabled = bool(map.get("enabled"), true);
            double speakProbability = dbl(map.get("speakProbability"), DEFAULT_SPEAK_PROBABILITY);
            double settleSpeakProbability = dbl(
                    map.get("settleSpeakProbability"),
                    Math.min(1.0, speakProbability * DEFAULT_SETTLE_SPEAK_MULTIPLIER));
            Map<String, List<String>> pools = parseMoodBlocks(map);
            if (pools.isEmpty()) {
                log.warn("npc-lines has no happy/calm/sad entries: {}", path);
            }
            return Optional.of(new NpcLinePool(enabled, speakProbability, settleSpeakProbability, pools));
        } catch (Exception e) {
            log.warn("npc-lines load failed: {}", path, e);
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> parseMoodBlocks(Map<?, ?> root) {
        java.util.HashMap<String, List<String>> out = new java.util.HashMap<>();
        for (String[] block : MOOD_BLOCK_TO_PREFIX) {
            String blockKey = block[0];
            String prefix = block[1];
            Object blockObj = root.get(blockKey);
            if (!(blockObj instanceof Map<?, ?> moodMap)) {
                continue;
            }
            for (Map.Entry<?, ?> e : moodMap.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                String actionKey = String.valueOf(e.getKey()).trim().toUpperCase(Locale.ROOT);
                String flatKey = prefix + actionKey;
                if (e.getValue() instanceof List<?> list) {
                    out.put(flatKey, list.stream().map(String::valueOf).filter(s -> !s.isBlank()).toList());
                }
            }
        }
        return out;
    }

    private static boolean bool(Object o, boolean def) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o != null) {
            return Boolean.parseBoolean(String.valueOf(o));
        }
        return def;
    }

    private static double dbl(Object o, double def) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o != null) {
            try {
                return Double.parseDouble(String.valueOf(o));
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }
}
