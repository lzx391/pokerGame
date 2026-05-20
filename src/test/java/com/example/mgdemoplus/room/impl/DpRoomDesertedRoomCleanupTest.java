package com.example.mgdemoplus.room.impl;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.user.mapper.DpUserStatsMapper;
import com.example.mgdemoplus.presence.DpFriendPresenceService;
import com.example.mgdemoplus.history.DpHandHistoryObservedService;
import com.example.mgdemoplus.history.DpHandHistoryPersistService;
import com.example.mgdemoplus.roomchat.buffer.RoomChatBuffer;
import com.example.mgdemoplus.roomchat.DpRoomChatPersistenceService;
import com.example.mgdemoplus.npc.llm.DpLlmNpcDecisionService;
import com.example.mgdemoplus.npc.llm.LlmNpcGlobalHandConversationStore;
import com.example.mgdemoplus.lobby.DpRoomHallService;
import com.example.mgdemoplus.websocket.DpGameRoomPushService;
import com.example.mgdemoplus.websocket.DpQuickMatchPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 回归：「无桌上真人且观众为空」时，与 1s 定时器同口径的 {@link DpRoomServiceImpl#removeDesertedRoomInGlobalTickIfNoLiveHumans}
 * 能摘掉房间；以及退座/离席链路后房间应从 {@code roomMap} 消失。
 * <p>
 * 不启动真实 Timer（依赖 {@link DpRoomServiceImpl#suppressGlobalRoomTimerForTests}），改为直接反射调用
 * {@code removeDesertedRoomInGlobalTickIfNoLiveHumans}，等价于定时器里「检查可解散房间」那一步。
 */
class DpRoomDesertedRoomCleanupTest {

    private DpRoomServiceImpl svc;

    @BeforeEach
    void setUp() {
        DpRoomServiceImpl.suppressGlobalRoomTimerForTests = true;
        svc = new DpRoomServiceImpl(
                mock(DpHandHistoryPersistService.class),
                mock(DpLlmNpcDecisionService.class),
                mock(DpGameRoomPushService.class),
                mock(DpUserMapper.class),
                mock(DpUserStatsMapper.class),
                mock(DpHandHistoryObservedService.class),
                new LlmNpcGlobalHandConversationStore(),
                mock(DpRoomHallService.class),
                new ObjectMapper(),
                mock(DpQuickMatchPushService.class),
                mock(DpFriendPresenceService.class),
                new RoomChatBuffer(),
                mock(DpRoomChatPersistenceService.class),
                mock(com.example.mgdemoplus.moderation.DpSensitiveWordService.class));
    }

    @AfterEach
    void tearDown() {
        DpRoomServiceImpl.suppressGlobalRoomTimerForTests = false;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, DpRoomBO> roomMap(DpRoomServiceImpl s) throws Exception {
        Field f = DpRoomServiceImpl.class.getDeclaredField("roomMap");
        f.setAccessible(true);
        return (Map<String, DpRoomBO>) f.get(s);
    }

    /** 与定时器内 {@code removeDesertedRoomInGlobalTickIfNoLiveHumans(room)} 相同。 */
    private static boolean invokeRemoveDeserted(DpRoomServiceImpl s, DpRoomBO room) throws Exception {
        Method m = DpRoomServiceImpl.class.getDeclaredMethod("removeDesertedRoomInGlobalTickIfNoLiveHumans", DpRoomBO.class);
        m.setAccessible(true);
        return (boolean) m.invoke(s, room);
    }

    private static DpRoomBO passwordRoom(String roomId, String ownerNick) {
        DpRoomBO r = new DpRoomBO();
        r.setRoomId(roomId);
        r.setOwner(ownerNick);
        r.setRoomPassword("test-pw-only-for-unit-test");
        r.setPlaying(true);
        r.setCurrentStage("preflop");
        r.setSpectators(new ArrayList<>());
        r.setWaitNextHand(new ArrayList<>());
        return r;
    }

    private static DpPlayer humanOnRoom(DpRoomBO r, String nick, boolean leftThisHand) {
        DpPlayer p = new DpPlayer();
        p.setNickname(nick);
        p.setLeftThisHand(leftThisHand);
        p.setChips(Math.max(500, r.getBigBlindChips() * 50));
        p.setLastHeartBeat(System.currentTimeMillis());
        return p;
    }

    /** 仅行上一名真人「离座」、无观众：定时器口径应立即摘房。 */
    @Test
    void timerRemovesRoomWhenOnlyZombieSeatAndNoSpectators() throws Exception {
        Map<String, DpRoomBO> map = roomMap(svc);
        DpRoomBO r = passwordRoom("rid-z", "zombieNick");
        r.getPlayers().add(humanOnRoom(r, "zombieNick", true));
        map.put(r.getRoomId(), r);

        assertTrue(invokeRemoveDeserted(svc, r));
        assertNull(map.get(r.getRoomId()));
    }

    /** 僵尸 + 观众；观众 exit 后应无房（exit 内 tryUnregister 或随后定时器一步）。 */
    @Test
    void spectatorExitAfterZombieLeavesRoomRemoved() throws Exception {
        Map<String, DpRoomBO> map = roomMap(svc);
        DpRoomBO r = passwordRoom("rid-s", "zombieNick");
        r.getPlayers().add(humanOnRoom(r, "zombieNick", true));
        r.getSpectators().add("bob");
        r.touchSpectatorPresence("bob", System.currentTimeMillis());
        map.put(r.getRoomId(), r);

        assertTrue(svc.exitRoom(r.getRoomId(), "bob"));
        assertNull(map.get(r.getRoomId()), "room should be removed after spectator exits");
    }

    /** 一名上桌真人直接退座：应摘房。 */
    @Test
    void seatedPlayerExitRemovesRoom() throws Exception {
        Map<String, DpRoomBO> map = roomMap(svc);
        DpRoomBO r = passwordRoom("rid-p", "alice");
        DpPlayer alice = humanOnRoom(r, "alice", false);
        r.getPlayers().add(alice);
        r.setCurrentActorIndex(-1);
        map.put(r.getRoomId(), r);

        assertTrue(svc.exitRoom(r.getRoomId(), "alice"));
        assertNull(map.get(r.getRoomId()));
    }

    /**
     * 先丢到观众（僵尸 + 还 spectators），再退出房间：应摘房。
     * 等价「离席再退座」。
     */
    @Test
    void kickToSpectatorThenExitRemovesRoom() throws Exception {
        Map<String, DpRoomBO> map = roomMap(svc);
        DpRoomBO r = passwordRoom("rid-k", "alice");
        r.getPlayers().add(humanOnRoom(r, "alice", false));
        r.setCurrentActorIndex(-1);
        map.put(r.getRoomId(), r);

        assertTrue(svc.kickPlayer(r.getRoomId(), "alice"));
        DpRoomBO afterKick = map.get(r.getRoomId());
        assertNotNull(afterKick);
        assertTrue(afterKick.getPlayers().get(0).isLeftThisHand());
        assertTrue(afterKick.getSpectators().contains("alice"));

        assertTrue(svc.exitRoom(r.getRoomId(), "alice"));
        assertNull(map.get(r.getRoomId()));
    }
}
