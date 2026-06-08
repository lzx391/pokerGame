package com.example.mgdemoplus.history.impl;

import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.history.bo.DpObservedHandRecordBO;
import com.example.mgdemoplus.history.entity.DpObservedHandHistory;
import com.example.mgdemoplus.history.mapper.DpObservedHandHistoryMapper;
import com.example.mgdemoplus.history.mapper.DpObservedHandParticipantMapper;
import com.example.mgdemoplus.history.types.DpObservedHandActionType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DpHandHistoryPayloadV2Test {

    @Mock
    private DpObservedHandHistoryMapper historyMapper;
    @Mock
    private DpObservedHandParticipantMapper participantMapper;
    @Mock
    private DpUserMapper dpUserMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private DpHandHistoryObservedServiceImpl observedService;
    private DpHandHistoryPersistServiceImpl persistService;

    @BeforeEach
    void setUp() {
        observedService = new DpHandHistoryObservedServiceImpl();
        persistService = new DpHandHistoryPersistServiceImpl(
                historyMapper, participantMapper, dpUserMapper, objectMapper);
        doAnswer(inv -> {
            DpObservedHandHistory row = inv.getArgument(0);
            row.setId(99L);
            return 1;
        }).when(historyMapper).insert(any(DpObservedHandHistory.class));
        when(participantMapper.insert(any())).thenReturn(1);
    }

    @Test
    void observedService_recordsActorChipsPotAtStreetEndAndStartingStackBb() {
        DpRoomBO room = sampleRoom();
        observedService.beginHand(room);
        observedService.markHandReadyAfterBlinds(room);

        DpPlayer alice = room.getPlayers().get(0);
        DpPlayer bob = room.getPlayers().get(1);
        observedService.recordBlind(room, alice.getNickname(), true, 5, 0);
        observedService.recordBlind(room, bob.getNickname(), false, 10, 5);

        alice.setChips(240);
        observedService.recordBetLikeAction(room, alice, 20, 10, 5, 15, false, true);
        observedService.recordPotAtStreetEnd(room, "preflop", 45);

        room.getCommunityCards().addAll(List.of("Ah", "Kd", "Qc"));
        room.setCurrentStage("flop");
        observedService.recordBoardState(room);

        bob.setChips(220);
        observedService.recordFold(room, bob, 45);

        DpObservedHandRecordBO rec = observedService.finalizeHand(room);

        assertThat(rec.startingStackBb).isEqualTo(50);
        assertThat(rec.actions).hasSize(4);
        assertThat(rec.actions.stream()
                .filter(a -> a.type == DpObservedHandActionType.POST_BLIND_SB)
                .findFirst().orElseThrow().actorChipsAfter).isEqualTo(245);
        assertThat(rec.actions.stream()
                .filter(a -> a.type == DpObservedHandActionType.RAISE)
                .findFirst().orElseThrow().actorChipsAfter).isEqualTo(240);
        assertThat(rec.actions.stream()
                .filter(a -> a.type == DpObservedHandActionType.FOLD)
                .findFirst().orElseThrow().actorChipsAfter).isEqualTo(220);
        assertThat(rec.boardsByStreet.get(0).potTotalAtStreetEnd).isEqualTo(45);
    }

    @Test
    void persistService_writesPayloadVersion2WithNewFields() throws Exception {
        DpRoomBO room = sampleRoom();
        observedService.beginHand(room);
        observedService.markHandReadyAfterBlinds(room);
        observedService.recordPotAtStreetEnd(room, "preflop", 15);
        room.getPlayers().get(0).setChips(490);
        observedService.recordBetLikeAction(room, room.getPlayers().get(0), 10, 10, 5, 15, false, false);
        DpObservedHandRecordBO rec = observedService.finalizeHand(room);

        persistService.save(rec, room);

        ArgumentCaptor<DpObservedHandHistory> captor = ArgumentCaptor.forClass(DpObservedHandHistory.class);
        org.mockito.Mockito.verify(historyMapper).insert(captor.capture());
        DpObservedHandHistory row = captor.getValue();

        assertThat(row.getPayloadVersion()).isEqualTo(2);
        assertThat(row.getStartingStackBb()).isEqualTo(50);

        Map<String, Object> payload = objectMapper.readValue(
                row.getPayloadJson(), new TypeReference<>() {});
        assertThat(payload.get("startingStackBb")).isEqualTo(50);

        List<Map<String, Object>> actions = castList(payload.get("actions"));
        assertThat(actions.get(0)).containsEntry("actorChipsAfter", 490);

        List<Map<String, Object>> boards = castList(payload.get("boardsByStreet"));
        assertThat(boards.get(0)).containsEntry("potTotalAtStreetEnd", 15);
    }

    private static DpRoomBO sampleRoom() {
        DpRoomBO room = new DpRoomBO();
        room.setRoomId("room-v2");
        room.setCurrentHandSeed(42L);
        room.setSmallBlindChips(5);
        room.setBigBlindChips(10);
        room.setStartingStackBb(50);
        room.setCurrentStage("preflop");
        room.setRaiseLevel(0);
        room.setCurrentBetToCall(10);
        room.setPot(15);

        DpPlayer alice = new DpPlayer();
        alice.setNickname("Alice");
        alice.setChips(245);
        alice.setBet(5);
        alice.setBlind(1);
        alice.setDealer(true);
        alice.setHoleCards(List.of("As", "Ks"));

        DpPlayer bob = new DpPlayer();
        bob.setNickname("Bob");
        bob.setChips(240);
        bob.setBet(10);
        bob.setBlind(2);
        bob.setHoleCards(List.of("7h", "7d"));

        room.setPlayers(new ArrayList<>(List.of(alice, bob)));
        room.setCommunityCards(new ArrayList<>());
        return room;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castList(Object value) {
        return (List<Map<String, Object>>) value;
    }
}
