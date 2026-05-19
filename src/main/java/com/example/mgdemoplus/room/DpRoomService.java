package com.example.mgdemoplus.room;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpRoom;
import com.example.mgdemoplus.utils.ResultUtil;

import java.util.List;
import java.util.Set;

/**
 * In-memory poker room lifecycle API (implemented by {@link com.example.mgdemoplus.room.impl.DpRoomServiceImpl}).
 */
public interface DpRoomService {

    DpRoomBO createRoom(String ownerNickname, Integer ownerUserId,
                        int smallBlindChips, int bigBlindChips, int startingStackBb,
                        String roomPassword, int maxSeatCount);

    ResultUtil listRecentRoomChat(String roomId, String viewerNickname, int limit);

    DpRoomBO getRoomSnapshotForViewer(String roomId, String viewerNickname);

    DpRoomBO snapshotForViewerFromLive(DpRoomBO live, String viewerNickname);

    DpRoomBO getAllRooms(String roomId);

    Integer resolveDpUserIdForNickname(DpRoomBO room, String nickname);

    boolean isNicknameInRoom(DpRoomBO room, String nickname);

    String joinRoom(String roomId, String nickname, Integer userId, String roomPassword);

    boolean dpRoomExistsInMemory(String roomId);

    boolean canActiveMemberInviteFriends(String roomId, String nickname);

    String joinRoomInviteAsSpectator(String roomId, String nickname, Integer userId);

    boolean readyNextHand(String roomId, String nickname, Integer userId);

    boolean cancelReadyNextHand(String roomId, String nickname, Integer userId);

    ResultUtil quickMatchJoinQueueOrImmediate(String nickname, Integer userId);

    void pushQuickMatchLobbySnapshot(String nickname);

    boolean cancelDefaultQuickMatchWait(String nickname);

    String findRoomIdContainingNickname(String nickname);

    String findActiveRoomIdForNickname(String nickname);

    boolean toggleReady(String roomId, String nickname);

    boolean exitRoom(String roomId, String nickname);

    boolean startGame(String roomId, String ownerNickname);

    boolean newHand(String roomId);

    boolean bet(String roomId, String nickname, int amount);

    boolean fold(String roomId, String nickname);

    boolean kickPlayer(String roomId, String nickname);

    KickPlayersBatchResult kickPlayersBatch(String roomId, String nicknamesCsv);

    void heartbeat(String roomId, String nickname);

    boolean rebuy(String roomId, String nickname);

    boolean addDemoBotToNextHand(String roomId);

    boolean addManiacBotToNextHand(String roomId);

    boolean addSharkBotToNextHand(String roomId);

    boolean addTagBotToNextHand(String roomId);

    boolean addLagBotToNextHand(String roomId);

    boolean addNitBotToNextHand(String roomId);

    boolean addCallStationBotToNextHand(String roomId);

    boolean addRuleNpcBatchToNextHand(String roomId, String archetypeKey, int count);

    boolean addLlmBotToNextHand(String roomId);

    boolean addGlobalLlmBotToNextHand(String roomId);

    boolean transferOwner(String roomId, String fromNickname, String toNickname);

    List<DpRoom> getAllRooms2();

    Set<String> getRoomIdsInMemory();
}

