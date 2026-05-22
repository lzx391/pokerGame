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
 * 预制资源使用顶层 {@code lines}（见方案文档）；实现期临时格式可用 {@code pools}。
 */
@Component
public class DpNpcLinePoolLoader {

    private static final Logger log = LoggerFactory.getLogger(DpNpcLinePoolLoader.class);
    private static final String RESOURCE_PREFIX = "npc-lines/";
    private static final double DEFAULT_SPEAK_PROBABILITY = 0.28;

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
            Map<String, List<String>> pools = Collections.emptyMap();
            Object poolsObj = map.get("pools");
            if (poolsObj == null) {
                poolsObj = map.get("lines");
            }
            if (poolsObj instanceof Map<?, ?> poolsMap) {
                pools = parsePools(poolsMap);
            }
            if (pools.isEmpty()) {
                log.warn("npc-lines has no lines/pools entries: {}", path);
            }
            return Optional.of(new NpcLinePool(enabled, speakProbability, pools));
        } catch (Exception e) {
            log.warn("npc-lines load failed: {}", path, e);
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> parsePools(Map<?, ?> poolsMap) {
        java.util.HashMap<String, List<String>> out = new java.util.HashMap<>();
        for (Map.Entry<?, ?> e : poolsMap.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String poolKey = String.valueOf(e.getKey()).trim().toUpperCase(Locale.ROOT);
            if (e.getValue() instanceof List<?> list) {
                out.put(poolKey, list.stream().map(String::valueOf).filter(s -> !s.isBlank()).toList());
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
