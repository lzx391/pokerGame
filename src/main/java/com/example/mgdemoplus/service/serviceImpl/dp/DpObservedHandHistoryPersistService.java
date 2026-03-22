package com.example.mgdemoplus.service.serviceImpl.dp;

import com.example.mgdemoplus.entity.dp.DpObservedHandHistory;
import com.example.mgdemoplus.mapper.dp.DpObservedHandHistoryMapper;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 将 {@link DpNpcSharkObservedHandHistory.ObservedHandRecord} 写入表 dp_observed_hand_history。
 * 在 {@link DpRoomServiceImpl} 结算流程中调用；表不存在或 DB 异常时仅打日志，不中断游戏。
 */
@Service
public class DpObservedHandHistoryPersistService {

    private static final Logger log = LoggerFactory.getLogger(DpObservedHandHistoryPersistService.class);
    private static final int PAYLOAD_VERSION = 1;

    private final DpObservedHandHistoryMapper mapper;
    private final ObjectMapper payloadMapper;

    public DpObservedHandHistoryPersistService(
            DpObservedHandHistoryMapper mapper,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.payloadMapper = objectMapper.copy();
        this.payloadMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        this.payloadMapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
        this.payloadMapper.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
    }

    public void save(DpNpcSharkObservedHandHistory.ObservedHandRecord rec) {
        if (rec == null) {
            return;
        }
        try {
            Payload payload = Payload.from(rec);
            String json = payloadMapper.writeValueAsString(payload);
            DpObservedHandHistory row = new DpObservedHandHistory();
            row.setRoomId(rec.roomId);
            row.setHandSeed(rec.handSeed);
            row.setStartedAtMs(rec.startedAtMs);
            row.setEndedAtMs(rec.endedAtMs);
            row.setSmallBlindChips(rec.smallBlindChips);
            row.setBigBlindChips(rec.bigBlindChips);
            row.setDealerNickname(rec.dealerNickname);
            row.setMainPotBeforeSettlement(rec.mainPotTotalBeforeSettlement);
            row.setPayloadVersion(PAYLOAD_VERSION);
            row.setPayloadJson(json);
            mapper.insert(row);
        } catch (Exception e) {
            log.warn("dp_observed_hand_history insert failed roomId={} handSeed={}: {}",
                    rec.roomId, rec.handSeed, e.getMessage());
        }
    }

    /**
     * 与 SQL 注释中的 payload 结构一致的可序列化 DTO（避免直接序列化内部嵌套类名路径）。
     */
    private static final class Payload {
        public java.util.List<SeatDto> seatsAtStart;
        public java.util.List<BoardDto> boardsByStreet;
        public java.util.List<ActionDto> actions;
        public java.util.List<PotDto> potsBeforeSettlement;
        public java.util.Map<String, java.util.List<String>> holeCardsAtEnd;
        public java.util.Map<String, Integer> netChipsChange;

        static Payload from(DpNpcSharkObservedHandHistory.ObservedHandRecord rec) {
            Payload p = new Payload();
            p.seatsAtStart = new java.util.ArrayList<>();
            for (DpNpcSharkObservedHandHistory.SeatAtHandStart s : rec.seatsAtStart) {
                SeatDto d = new SeatDto();
                d.seatIndex = s.seatIndex;
                d.nickname = s.nickname;
                d.blind = s.blind;
                d.chipsAfterBlinds = s.chipsAfterBlinds;
                p.seatsAtStart.add(d);
            }
            p.boardsByStreet = new java.util.ArrayList<>();
            for (DpNpcSharkObservedHandHistory.StreetBoard b : rec.boardsByStreet) {
                BoardDto d = new BoardDto();
                d.stage = b.stage;
                d.communityCards = new java.util.ArrayList<>(b.communityCards);
                p.boardsByStreet.add(d);
            }
            p.actions = new java.util.ArrayList<>();
            for (DpNpcSharkObservedHandHistory.ActionRecord a : rec.actions) {
                ActionDto d = new ActionDto();
                d.tsMs = a.tsMs;
                d.stage = a.stage;
                d.actorNickname = a.actorNickname;
                d.type = a.type.name();
                d.amount = a.amount;
                d.betToCallBefore = a.betToCallBefore;
                d.actorBetBefore = a.actorBetBefore;
                d.raiseLevelAfter = a.raiseLevelAfter;
                d.potBefore = a.potBefore;
                p.actions.add(d);
            }
            p.potsBeforeSettlement = new java.util.ArrayList<>();
            for (DpNpcSharkObservedHandHistory.PotSnapshot pot : rec.potsBeforeSettlement) {
                PotDto d = new PotDto();
                d.amount = pot.amount;
                d.eligibleNicknames = new java.util.ArrayList<>(pot.eligibleNicknames);
                p.potsBeforeSettlement.add(d);
            }
            p.holeCardsAtEnd = new java.util.LinkedHashMap<>(rec.holeCardsAtEnd);
            p.netChipsChange = new java.util.LinkedHashMap<>(rec.netChipsChange);
            return p;
        }
    }

    private static final class SeatDto {
        public int seatIndex;
        public String nickname;
        public int blind;
        public int chipsAfterBlinds;
    }

    private static final class BoardDto {
        public String stage;
        public java.util.List<String> communityCards;
    }

    private static final class ActionDto {
        public long tsMs;
        public String stage;
        public String actorNickname;
        public String type;
        public int amount;
        public int betToCallBefore;
        public int actorBetBefore;
        public int raiseLevelAfter;
        public int potBefore;
    }

    private static final class PotDto {
        public int amount;
        public java.util.List<String> eligibleNicknames;
    }
}
