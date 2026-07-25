package com.example.mgdemoplus.rbac;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.moderation.DpSensitiveWordService;
import com.example.mgdemoplus.rbac.support.DpPermissionCodes;
import com.example.mgdemoplus.room.support.DpRoomRegistry;
import com.example.mgdemoplus.room.support.DpRoomServiceCallbacks;
import com.example.mgdemoplus.room.support.DpRoomSnapshotSupport;
import com.example.mgdemoplus.roomchat.buffer.RoomChatBuffer;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DpRbacHoleCardsTest {

    private DpPermissionService permissionService;
    private DpRoomSnapshotSupport snapshotSupport;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        permissionService = mock(DpPermissionService.class);
        snapshotSupport = new DpRoomSnapshotSupport(
                mock(DpRoomRegistry.class),
                mock(RoomChatBuffer.class),
                objectMapper,
                mock(DpRoomServiceCallbacks.class),
                mock(DpSensitiveWordService.class),
                permissionService);
    }

    @Test
    void owner_withoutRbacPermission_sanitizesOthersHoleCards() {
        when(permissionService.hasPermi(eq(DpPermissionCodes.GAME_HOLE_CARDS_VIEW))).thenReturn(false);

        DpRoomBO snapshot = snapshotSupport.snapshotForViewerFromLive(sampleRoom(), "owner");

        assertThat(findPlayer(snapshot, "other").getHoleCards()).isEmpty();
        assertThat(findPlayer(snapshot, "other").getBestHandCards()).isEmpty();
    }

    @Test
    void nonOwner_withHoleCardsViewPermission_keepsOthersHoleCards() {
        when(permissionService.hasPermi(eq(DpPermissionCodes.GAME_HOLE_CARDS_VIEW))).thenReturn(true);

        DpRoomBO snapshot = snapshotSupport.snapshotForViewerFromLive(sampleRoom(), "viewer");

        assertThat(findPlayer(snapshot, "other").getHoleCards()).containsExactly("Ah", "Kd");
    }

    @Test
    void nonOwner_withoutPermission_sanitizesOthersHoleCards() {
        when(permissionService.hasPermi(eq(DpPermissionCodes.GAME_HOLE_CARDS_VIEW))).thenReturn(false);

        DpRoomBO snapshot = snapshotSupport.snapshotForViewerFromLive(sampleRoom(), "viewer");

        assertThat(findPlayer(snapshot, "other").getHoleCards()).isEmpty();
        assertThat(findPlayer(snapshot, "other").getBestHandCards()).isEmpty();
    }

    @Test
    void afterGrantEvictAndRevoke_sanitizesOthersHoleCards() {
        when(permissionService.hasPermi(eq(DpPermissionCodes.GAME_HOLE_CARDS_VIEW)))
                .thenReturn(true)
                .thenReturn(false);

        DpRoomBO granted = snapshotSupport.snapshotForViewerFromLive(sampleRoom(), "viewer");
        assertThat(findPlayer(granted, "other").getHoleCards()).containsExactly("Ah", "Kd");

        DpRoomBO revoked = snapshotSupport.snapshotForViewerFromLive(sampleRoom(), "viewer");
        assertThat(findPlayer(revoked, "other").getHoleCards()).isEmpty();
    }

    private static DpRoomBO sampleRoom() {
        DpRoomBO room = new DpRoomBO();
        room.setOwner("owner");
        room.setCurrentStage("preflop");

        DpPlayer owner = new DpPlayer();
        owner.setNickname("owner");
        owner.setHoleCards(List.of("2c", "3d"));

        DpPlayer viewer = new DpPlayer();
        viewer.setNickname("viewer");
        viewer.setHoleCards(List.of("4h", "5s"));

        DpPlayer other = new DpPlayer();
        other.setNickname("other");
        other.setHoleCards(List.of("Ah", "Kd"));
        other.setBestHandCards(List.of("Ah", "Kd", "Qc", "Jc", "Tc"));
        other.setHandRankName("高牌");
        other.setHandRankDetail("A高");

        room.setPlayers(List.of(owner, viewer, other));
        return room;
    }

    private static DpPlayer findPlayer(DpRoomBO room, String nickname) {
        return room.getPlayers().stream()
                .filter(p -> nickname.equals(p.getNickname()))
                .findFirst()
                .orElseThrow();
    }
}
