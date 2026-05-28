package com.example.mgdemoplus.room.impl;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.history.DpHandHistoryObservedService;
import com.example.mgdemoplus.history.DpHandHistoryPersistService;
import com.example.mgdemoplus.leaderboard.impl.DpLeaderboardWeeklyWriteService;
import com.example.mgdemoplus.lobby.DpRoomHallService;
import com.example.mgdemoplus.npc.llm.DpLlmNpcDecisionService;
import com.example.mgdemoplus.npc.tabletalk.DpNpcTableTalkService;
import com.example.mgdemoplus.npc.llm.LlmNpcGlobalHandConversationStore;
import com.example.mgdemoplus.presence.DpFriendPresenceService;
import com.example.mgdemoplus.presence.DpFriendPresenceState;
import com.example.mgdemoplus.room.support.DpRoomHeartbeatScheduler;
import com.example.mgdemoplus.roomchat.DpRoomChatPersistenceService;
import com.example.mgdemoplus.roomchat.buffer.RoomChatBuffer;
import com.example.mgdemoplus.user.mapper.DpUserStatsMapper;
import com.example.mgdemoplus.websocket.DpGameRoomPushService;
import com.example.mgdemoplus.websocket.DpQuickMatchPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 验证 {@link DpRoomServiceImpl#exitRoom} / {@link DpRoomServiceImpl#presenceTryMarkIdleFullyLeft}
 * 在「最后一人退房 / 僵尸位 / 心跳踢人」边界下是否会漏 {@link DpFriendPresenceService#markIdle}。
 * <p>
 * 使用真实 {@link DpFriendPresenceService}（非 mock），与 {@link DpRoomDesertedRoomCleanupTest} 同口径。
 */
class DpRoomExitPresenceMarkIdleTest {

    private static final int ALICE_UID = 101;
    private static final int BOB_UID = 102;

    private DpFriendPresenceService friendPresence;
    private DpRoomServiceImpl svc;

    @BeforeEach
    void setUp() {
        DpRoomServiceImpl.suppressGlobalRoomTimerForTests = true;
        friendPresence = new DpFriendPresenceService();
        svc = new DpRoomServiceImpl(
                mock(DpHandHistoryPersistService.class),
                mock(com.example.mgdemoplus.room.support.DpSettlePersistenceDispatcher.class),
                mock(DpLlmNpcDecisionService.class),
                mock(DpNpcTableTalkService.class),
                mock(com.example.mgdemoplus.npc.mood.DpNpcMoodProperties.class),
                mock(DpGameRoomPushService.class),
                mock(DpUserMapper.class),
                mock(DpUserStatsMapper.class),
                mock(DpLeaderboardWeeklyWriteService.class),
                mock(DpHandHistoryObservedService.class),
                new LlmNpcGlobalHandConversationStore(),
                mock(DpRoomHallService.class),
                new ObjectMapper(),
                mock(DpQuickMatchPushService.class),
                friendPresence,
                new RoomChatBuffer(),
                mock(DpRoomChatPersistenceService.class),
                mock(com.example.mgdemoplus.moderation.DpSensitiveWordService.class));
    }

    @AfterEach
    void tearDown() {
        DpRoomServiceImpl.suppressGlobalRoomTimerForTests = false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, DpRoomBO> roomMap() throws Exception {
        Field f = DpRoomServiceImpl.class.getDeclaredField("roomMap");
        f.setAccessible(true);
        return (Map<String, DpRoomBO>) f.get(svc);
    }

    private static DpRoomBO idleLobbyRoom(String roomId, String ownerNick) {
        DpRoomBO r = new DpRoomBO();
        r.setRoomId(roomId);
        r.setOwner(ownerNick);
        r.setRoomPassword("unit-test-pw");
        r.setPlaying(false);
        r.setSpectators(new ArrayList<>());
        r.setWaitNextHand(new ArrayList<>());
        return r;
    }

    private static DpPlayer human(String nick, int dpUserId) {
        DpPlayer p = new DpPlayer();
        p.setNickname(nick);
        p.setDpUserId(dpUserId);
        p.setChips(500);
        p.setLastHeartBeat(System.currentTimeMillis());
        return p;
    }

    private static DpPlayer humanLeftThisHand(String nick, int dpUserId) {
        DpPlayer p = human(nick, dpUserId);
        p.setLeftThisHand(true);
        p.setFold(true);
        return p;
    }

    // ---- 用例 A：最后一人退房（未开局，玩家已从 players 摘除） ----

    @Test
    @DisplayName("A — 最后一人退房(未开局): 离开者应 markIdle")
    void lastHumanExitWhenRoomNotPlaying_marksLeaverIdle() throws Exception {
        Map<String, DpRoomBO> map = roomMap();
        DpRoomBO r = idleLobbyRoom("rid-last-idle", "alice");
        r.getPlayers().add(human("alice", ALICE_UID));
        r.putRegisteredDpUserId("alice", ALICE_UID);
        map.put(r.getRoomId(), r);

        friendPresence.markInGame(ALICE_UID, "test:join");
        assertThat(friendPresence.getEffective(ALICE_UID)).isEqualTo(DpFriendPresenceState.IN_GAME);

        assertTrue(svc.exitRoom(r.getRoomId(), "alice"));
        assertNull(map.get(r.getRoomId()), "room should be removed");

        assertThat(friendPresence.getEffective(ALICE_UID))
                .as("最后一人 exitRoom(droppedEmpty) 后，离开者 presence 应为 IDLE")
                .isEqualTo(DpFriendPresenceState.IDLE);
    }

    @Test
    @DisplayName("A2 — 最后一人退房(对局进行中): 僵尸位仍在 snapshot，离开者应 markIdle")
    void lastHumanExitWhilePlaying_marksLeaverIdleViaRoomSnapshot() throws Exception {
        Map<String, DpRoomBO> map = roomMap();
        DpRoomBO r = idleLobbyRoom("rid-last-playing", "alice");
        r.setPlaying(true);
        r.setCurrentStage("preflop");
        r.setCurrentActorIndex(-1);
        r.getPlayers().add(human("alice", ALICE_UID));
        r.putRegisteredDpUserId("alice", ALICE_UID);
        map.put(r.getRoomId(), r);

        friendPresence.markInGame(ALICE_UID, "test:join");
        assertTrue(svc.exitRoom(r.getRoomId(), "alice"));
        assertNull(map.get(r.getRoomId()));

        assertThat(friendPresence.getEffective(ALICE_UID))
                .as("进行中最后一人 exit 后，corpse 内僵尸位仍应触发 markIdle")
                .isEqualTo(DpFriendPresenceState.IDLE);
    }

    // ---- 用例 B：非最后一人退房（对照） ----

    @Test
    @DisplayName("B — 非最后一人退房: 离开者 markIdle，留座者仍 IN_GAME")
    void twoHumansOneExits_leaverIdleRemainingInGame() throws Exception {
        Map<String, DpRoomBO> map = roomMap();
        DpRoomBO r = idleLobbyRoom("rid-two", "alice");
        r.getPlayers().add(human("alice", ALICE_UID));
        r.getPlayers().add(human("bob", BOB_UID));
        r.putRegisteredDpUserId("alice", ALICE_UID);
        r.putRegisteredDpUserId("bob", BOB_UID);
        map.put(r.getRoomId(), r);

        friendPresence.markInGame(ALICE_UID, "test:join");
        friendPresence.markInGame(BOB_UID, "test:join");

        assertTrue(svc.exitRoom(r.getRoomId(), "alice"));
        assertThat(map.get(r.getRoomId())).isNotNull();

        assertThat(friendPresence.getEffective(ALICE_UID))
                .as("非最后一人 exit 应走 presenceTryMarkIdleFullyLeft")
                .isEqualTo(DpFriendPresenceState.IDLE);
        assertThat(friendPresence.getEffective(BOB_UID))
                .isEqualTo(DpFriendPresenceState.IN_GAME);
    }

    // ---- 用例 C：leftThisHand 僵尸位 + presenceTryMarkIdleFullyLeft ----

    @Test
    @DisplayName("C — leftThisHand 僵尸位: presenceTryMarkIdleFullyLeft 应 markIdle")
    void leftThisHandZombieStillInPlayers_tryMarkIdleFullyLeft() throws Exception {
        Map<String, DpRoomBO> map = roomMap();
        DpRoomBO r = idleLobbyRoom("rid-zombie", "alice");
        r.setPlaying(true);
        r.getPlayers().add(humanLeftThisHand("alice", ALICE_UID));
        r.putRegisteredDpUserId("alice", ALICE_UID);
        map.put(r.getRoomId(), r);

        friendPresence.markInGame(ALICE_UID, "test:join");

        svc.presenceTryMarkIdleFullyLeft("alice", ALICE_UID, r, "hand_settle_zombie_removed");

        assertThat(friendPresence.getEffective(ALICE_UID))
                .as("leftThisHand 不算「有效在房」，tryMarkIdleFullyLeft 应置 IDLE")
                .isEqualTo(DpFriendPresenceState.IDLE);
    }

    // ---- 用例 D：心跳踢人后 IDLE ----

    @Test
    @DisplayName("D — 心跳踢出最后一名上桌真人: 被踢者应 markIdle")
    void heartbeatEvictsLastSeatedHuman_marksIdle() throws Exception {
        Map<String, DpRoomBO> map = roomMap();
        DpRoomBO r = idleLobbyRoom("rid-hb", "alice");
        r.setPlaying(true);
        r.setCurrentStage("preflop");
        DpPlayer alice = human("alice", ALICE_UID);
        alice.setLastHeartBeat(System.currentTimeMillis() - DpRoomBO.getHeartTimeout() - 1_000L);
        r.getPlayers().add(alice);
        r.putRegisteredDpUserId("alice", ALICE_UID);
        map.put(r.getRoomId(), r);

        friendPresence.markInGame(ALICE_UID, "test:join");

        Field schedField = DpRoomServiceImpl.class.getDeclaredField("heartbeatScheduler");
        schedField.setAccessible(true);
        DpRoomHeartbeatScheduler scheduler = (DpRoomHeartbeatScheduler) schedField.get(svc);
        scheduler.runGlobalSecondTickForSingleRoom(r);

        assertThat(friendPresence.getEffective(ALICE_UID))
                .as("心跳踢人路径会先 presenceMarkIdleHuman 再 tryMarkIdleFullyLeft")
                .isEqualTo(DpFriendPresenceState.IDLE);
    }
}
