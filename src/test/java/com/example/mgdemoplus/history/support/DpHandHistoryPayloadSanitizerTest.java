package com.example.mgdemoplus.history.support;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DpHandHistoryPayloadSanitizerTest {

    @Test
    void sanitize_viewerA_hidesFoldedPlayerB() {
        Map<String, Object> payload = samplePayloadWithBFold();

        Map<String, Object> out = DpHandHistoryPayloadSanitizer.sanitize(payload, "Alice");

        @SuppressWarnings("unchecked")
        Map<String, List<String>> holes = (Map<String, List<String>>) out.get("holeCardsAtEnd");
        assertThat(holes).containsKey("Alice");
        assertThat(holes).doesNotContainKey("Bob");
        assertThat(holes).containsKey("Carol");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> boards = (List<Map<String, Object>>) out.get("boardsByStreet");
        for (Map<String, Object> board : boards) {
            @SuppressWarnings("unchecked")
            Map<String, String> ranks = (Map<String, String>) board.get("handRankNameByPlayer");
            assertThat(ranks).containsKey("Alice");
            assertThat(ranks).doesNotContainKey("Bob");
            assertThat(ranks).containsKey("Carol");
        }
    }

    @Test
    void sanitize_viewerB_keepsOwnFoldedCards() {
        Map<String, Object> payload = samplePayloadWithBFold();

        Map<String, Object> out = DpHandHistoryPayloadSanitizer.sanitize(payload, "Bob");

        @SuppressWarnings("unchecked")
        Map<String, List<String>> holes = (Map<String, List<String>>) out.get("holeCardsAtEnd");
        assertThat(holes).containsKeys("Alice", "Bob", "Carol");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> boards = (List<Map<String, Object>>) out.get("boardsByStreet");
        for (Map<String, Object> board : boards) {
            @SuppressWarnings("unchecked")
            Map<String, String> ranks = (Map<String, String>) board.get("handRankNameByPlayer");
            assertThat(ranks).containsKeys("Alice", "Bob", "Carol");
        }
    }

    @Test
    void sanitize_doesNotMutateOriginalPayload() {
        Map<String, Object> payload = samplePayloadWithBFold();

        DpHandHistoryPayloadSanitizer.sanitize(payload, "Alice");

        @SuppressWarnings("unchecked")
        Map<String, List<String>> holes = (Map<String, List<String>>) payload.get("holeCardsAtEnd");
        assertThat(holes).containsKeys("Alice", "Bob", "Carol");
    }

    @Test
    void collectFoldedNicknames_readsFoldActions() {
        Map<String, Object> payload = samplePayloadWithBFold();
        assertThat(DpHandHistoryPayloadSanitizer.collectFoldedNicknames(payload))
                .containsExactly("Bob");
    }

    private static Map<String, Object> samplePayloadWithBFold() {
        Map<String, Object> payload = new LinkedHashMap<>();

        Map<String, List<String>> holes = new LinkedHashMap<>();
        holes.put("Alice", List.of("As", "Kh"));
        holes.put("Bob", List.of("2c", "3d"));
        holes.put("Carol", List.of("Qh", "Qd"));
        payload.put("holeCardsAtEnd", holes);

        List<Map<String, Object>> boards = new ArrayList<>();
        Map<String, Object> flop = new LinkedHashMap<>();
        flop.put("stage", "flop");
        flop.put("communityCards", List.of("Ah", "7s", "2d"));
        Map<String, String> flopRanks = new LinkedHashMap<>();
        flopRanks.put("Alice", "一对");
        flopRanks.put("Bob", "高牌");
        flopRanks.put("Carol", "一对");
        flop.put("handRankNameByPlayer", flopRanks);
        boards.add(flop);
        payload.put("boardsByStreet", boards);

        List<Map<String, Object>> actions = new ArrayList<>();
        Map<String, Object> fold = new LinkedHashMap<>();
        fold.put("type", "FOLD");
        fold.put("actorNickname", "Bob");
        fold.put("stage", "flop");
        actions.add(fold);
        payload.put("actions", actions);

        return payload;
    }
}
