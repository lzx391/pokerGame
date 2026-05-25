package com.example.mgdemoplus.npc.tabletalk;

import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.npc.mood.NpcMoodState;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * npc-lines v2：happy/calm/sad 各 6 键、每键 ≥10 条；敏感词禁入。
 */
class NpcLinesYamlContractTest {

    private static final List<String> STYLES =
            List.of("tag", "nit", "fish", "call", "lag", "maniac", "custom");
    private static final List<String> ACTION_KEYS =
            List.of("FOLD", "CALL_OR_CHECK", "RAISE", "ALL_IN", "SETTLE_WIN", "SETTLE_LOSE");
    private static final List<String> MOOD_BLOCKS = List.of("happy", "calm", "sad");
    private static final Pattern BANNED = Pattern.compile(
            "激进|筹码|全押|全下|all-in|all in|梭|推池|加注|下注", Pattern.CASE_INSENSITIVE);

    private final DpNpcLinePoolLoader loader = new DpNpcLinePoolLoader();

    @Test
    void flattenedPools_haveAtLeastTenLinesPerMoodActionKey() {
        for (String style : STYLES) {
            NpcLinePool pool = loader.loadByResourceKey(style).orElseThrow();
            assertTrue(pool.isEnabled(), style);
            for (NpcMoodState mood : NpcMoodState.values()) {
                for (String actionKey : ACTION_KEYS) {
                    String flatKey = mood.moodPoolKey(actionKey);
                    List<String> lines = pool.linesForPoolKey(flatKey);
                    assertTrue(
                            lines.size() >= 10,
                            () -> style + " " + flatKey + " size=" + lines.size());
                }
            }
        }
    }

    @Test
    void rawYaml_hasNoBannedSubstrings() throws Exception {
        for (String style : STYLES) {
            String path = "npc-lines/" + style + ".yaml";
            ClassPathResource res = new ClassPathResource(path);
            assertTrue(res.exists(), path);
            try (InputStream in = res.getInputStream()) {
                String text = new String(in.readAllBytes());
                assertFalse(
                        BANNED.matcher(text).find(),
                        () -> path + " matched banned: " + BANNED.matcher(text).find());
                for (String block : MOOD_BLOCKS) {
                    assertTrue(text.contains(block + ":"), path + " missing " + block);
                }
                assertFalse(text.contains("pools:"), path + " must not use legacy pools:");
            }
        }
    }

    @Test
    void resourceKeys_coverAllRuleStyles() {
        for (DpNpcEngine.NpcStyle style : DpNpcEngine.NpcStyle.values()) {
            String key = DpNpcLinePoolLoader.resourceKeyForStyle(style);
            assertTrue(loader.load(style).isPresent(), key);
        }
        assertTrue(loader.loadByResourceKey("custom").isPresent());
    }
}
