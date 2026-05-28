package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.npc.engine.DpNpcEngine;
import com.example.mgdemoplus.utils.ResultUtil;

/**
 * Internal callbacks from {@code room.support.*} helpers into {@link com.example.mgdemoplus.room.impl.DpRoomServiceImpl}.
 * Not part of the public {@link com.example.mgdemoplus.room.DpRoomService} API.
 */
public interface DpRoomServiceCallbacks {

    String joinRoomMutateAssumeLocked(
            String roomId, String nickname, Integer userId, String roomPassword, DpRoomBO r);

    boolean applyReadyNextHandWhileLocked(DpRoomBO r, String nickname, Integer userId);

    boolean ensureLobbyReady(DpRoomBO r, String nickname);

    DpRoomBO createRoom(
            String ownerNickname,
            Integer ownerUserId,
            int smallBlindChips,
            int bigBlindChips,
            int startingStackBb,
            String roomPassword,
            int maxSeatCount);

    boolean startGame(String roomId, String ownerNickname);

    ResultUtil quickMatchJoinAndReady(String nickname, Integer userId);

    DpRoomBO findRoomContainingNickname(String nickname);

    void refreshQmIndexAfterJoinOutcomeOutsideRoomLock(String roomId, String outcome);

    void refreshJoinableQuickMatchIndexRoom(String roomId, long nowMs);

    void presenceMarkInGameHuman(DpRoomBO r, String nickname, Integer requestedUserId, String trigger);

    void presenceMarkIdleHuman(int dpUserId, String trigger);

    boolean exitRoom(String roomId, String nickname);

    void giveOwner(String roomId, String ownerNickname);

    void removeRoom(String roomId);

    boolean fold(String roomId, String nickname);

    boolean bet(String roomId, String nickname, int amount);

    void moveToNextValidActor(DpRoomBO r);

    void autoAdvanceIfRoundFinished(DpRoomBO r);

    boolean rebuy(String roomId, String nickname);

    boolean checkAndStartNextHandAfterSettleReturning(DpRoomBO r);

    void handleReadyTimeout(DpRoomBO room);

    void presenceTryMarkIdleFullyLeft(String nickname, Integer hintedUserId, DpRoomBO roomHint, String trigger);

    void npcAction(DpRoomBO room, DpPlayer p, DpNpcEngine.BotAction action);

    DpRoomBO getAllRooms(String roomId);

    boolean isNicknameInRoom(DpRoomBO room, String nickname);

    void presenceMarkIdleAllHumansOnRoomSnapshot(DpRoomBO r, String trigger);

    int clampRaiseAmountForLegal(DpRoomBO room, DpPlayer p, int amount);
}
