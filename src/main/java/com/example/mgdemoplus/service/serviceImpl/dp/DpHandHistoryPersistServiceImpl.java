package com.example.mgdemoplus.service.serviceImpl.dp;

import com.example.mgdemoplus.dto.DpObservedHandActionRecordDTO;
import com.example.mgdemoplus.dto.DpObservedHandRecordDTO;
import com.example.mgdemoplus.dto.DpObservedPotSnapshotDTO;
import com.example.mgdemoplus.dto.DpObservedSeatAtHandStartDTO;
import com.example.mgdemoplus.dto.DpObservedStreetBoardDTO;
import com.example.mgdemoplus.entity.dp.DpObservedHandHistory;
import com.example.mgdemoplus.entity.dp.DpObservedHandParticipant;
import com.example.mgdemoplus.entity.dp.DpPlayer;
import com.example.mgdemoplus.entity.dp.DpRoom;
import com.example.mgdemoplus.entity.dp.DpUser;
import com.example.mgdemoplus.mapper.dp.DpObservedHandHistoryMapper;
import com.example.mgdemoplus.mapper.dp.DpObservedHandParticipantMapper;
import com.example.mgdemoplus.mapper.dp.DpUserMapper;
import com.example.mgdemoplus.service.dp.DpHandHistoryPersistService;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
/**
 * 将 {@link DpObservedHandRecordDTO} 写入表 dp_observed_hand_history，
 * 并对非机器人玩家写入 dp_observed_hand_participant（user_id 优先来自 dpUserId，否则按昵称查 dp_user；均可无则仅存昵称快照）。
 * 在 {@link DpRoomServiceImpl} 结算流程中调用；表不存在或 DB 异常时仅打日志，不中断游戏。
 */
//这个服务类是负责将DpNpcSharkObservedHandHistory.ObservedHandRecord写入dp_observed_hand_history表和dp_observed_hand_participant表的
@Service
public class DpHandHistoryPersistServiceImpl implements DpHandHistoryPersistService {
    //本类分为以下模块：
    //1. save方法：将DpNpcSharkObservedHandHistory.ObservedHandRecord写入dp_observed_hand_history表
    //2. insertParticipants方法：将非机器人玩家写入dp_observed_hand_participant表
    //3. Payload类：将DpNpcSharkObservedHandHistory.ObservedHandRecord转换为Payload对象
    //4. SeatDto类：将DpNpcSharkObservedHandHistory.SeatAtHandStart转换为SeatDto对象
    //5. BoardDto类：将DpNpcSharkObservedHandHistory.StreetBoard转换为BoardDto对象
    //6. ActionDto类：将DpNpcSharkObservedHandHistory.ActionRecord转换为ActionDto对象
    //7. PotDto类：将DpNpcSharkObservedHandHistory.PotSnapshot转换为PotDto对象

    private static final Logger log = LoggerFactory.getLogger(DpHandHistoryPersistServiceImpl.class);
    private static final int PAYLOAD_VERSION = 1;

    private final DpObservedHandHistoryMapper mapper;
    private final DpObservedHandParticipantMapper participantMapper;
    private final DpUserMapper dpUserMapper;
    private final ObjectMapper payloadMapper;

    public DpHandHistoryPersistServiceImpl(
            DpObservedHandHistoryMapper mapper,
            DpObservedHandParticipantMapper participantMapper,
            DpUserMapper dpUserMapper,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.participantMapper = participantMapper;
        this.dpUserMapper = dpUserMapper;
        this.payloadMapper = objectMapper.copy();
        this.payloadMapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        this.payloadMapper.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE);
        this.payloadMapper.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
    }

    /**
     * @param room 结算当刻的房间（用于参与者与 dp_user 关联）；可为 null 则只写牌谱主表。
     */
    public void save(DpObservedHandRecordDTO rec, DpRoom room) {
        if (rec == null) {
            return;
        }
        try {
            //将DpNpcSharkObservedHandHistory.ObservedHandRecord转换为Payload对象
            Payload payload = Payload.from(rec);
            //将Payload对象转换为JSON字符串
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
            //获取插入后的id给dp_observed_hand_participant表使用
            Long handId = row.getId();
            if (handId != null && room != null) {
                insertParticipants(rec, handId, room);
            }
        } catch (Exception e) {
            log.warn("dp_observed_hand_history insert failed roomId={} handSeed={}: {}",
                    rec.roomId, rec.handSeed, e.getMessage());
        }
    }

    private void insertParticipants(DpObservedHandRecordDTO rec, long handHistoryId, DpRoom room) {
        List<DpPlayer> ps = room.getPlayers();
        if (ps == null) {
            return;
        }
        for (int i = 0; i < ps.size(); i++) {
            DpPlayer p = ps.get(i);
            if (p == null || DpNpcEngine.isBotPlayer(p)) {
                continue;
            }
            String nick = p.getNickname();
            if (nick == null || nick.isEmpty()) {
                continue;
            }
            Integer uid = p.getDpUserId();
            if (uid == null) {
                DpUser u = dpUserMapper.selectByNickname(nick);
                if (u != null) {
                    uid = u.getId();
                }
            }
            DpObservedHandParticipant row = new DpObservedHandParticipant();
            row.setHandHistoryId(handHistoryId);
            row.setUserId(uid);
            row.setNicknameSnapshot(nick);
            row.setSeatIndex(i);
            row.setDealer(p.isDealer());
            row.setBlindPos(p.getBlind());
            Integer net = rec.netChipsChange.get(p.getNickname());
            row.setNetChips(net != null ? net : 0);
            try {
                participantMapper.insert(row);
            } catch (Exception e) {
                log.warn("dp_observed_hand_participant insert failed handHistoryId={} nick={} userId={}: {}",
                        handHistoryId, nick, uid, e.getMessage());
            }
        }
    }

    /**
     * 与 SQL 注释中的 payload 结构一致的可序列化 DTO（避免直接序列化内部嵌套类名路径）。
     */
    //所谓的DTO就是数据传输对象，这里是为了将DpNpcSharkObservedHandHistory.ObservedHandRecord转换为Payload对象
    private static final class Payload {
        public List<SeatDto> seatsAtStart;
        public List<BoardDto> boardsByStreet;
        public List<ActionDto> actions;
        public List<PotDto> potsBeforeSettlement;
        public Map<String, List<String>> holeCardsAtEnd;
        public Map<String, Integer> netChipsChange;
        //已学习，本模块代码精讲如下：
        //1. from方法：将DpNpcSharkObservedHandHistory.ObservedHandRecord转换为Payload对象
        //2. Payload对象：负责记录座位，公共牌，行动，池，洞牌，净盈亏
        //3. SeatDto对象：负责记录座位
        //4. BoardDto对象：负责记录公共牌
        //5. ActionDto对象：负责记录行动
        //6. PotDto对象：负责记录池
        //7. HoleCardsAtEnd对象：负责记录洞牌
        //8. NetChipsChange对象：负责记录净盈亏
        static Payload from(DpObservedHandRecordDTO rec) {
            Payload p = new Payload();
            p.seatsAtStart = new ArrayList<>();
            for (DpObservedSeatAtHandStartDTO s : rec.seatsAtStart) {
                SeatDto d = new SeatDto();
                d.seatIndex = s.seatIndex;
                d.nickname = s.nickname;
                d.blind = s.blind;
                d.chipsAfterBlinds = s.chipsAfterBlinds;
                p.seatsAtStart.add(d);
            }
            p.boardsByStreet = new ArrayList<>();
            for (DpObservedStreetBoardDTO b : rec.boardsByStreet) {
                BoardDto d = new BoardDto();
                d.stage = b.stage;
                d.communityCards = new ArrayList<>(b.communityCards);
                p.boardsByStreet.add(d);
            }
            p.actions = new ArrayList<>();
            for (DpObservedHandActionRecordDTO a : rec.actions) {
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
            p.potsBeforeSettlement = new ArrayList<>();
            for (DpObservedPotSnapshotDTO pot : rec.potsBeforeSettlement) {
                PotDto d = new PotDto();
                d.amount = pot.amount;
                d.eligibleNicknames = new ArrayList<>(pot.eligibleNicknames);
                p.potsBeforeSettlement.add(d);
            }
            p.holeCardsAtEnd = new LinkedHashMap<>(rec.holeCardsAtEnd);
            p.netChipsChange = new LinkedHashMap<>(rec.netChipsChange);
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
        public List<String> communityCards;
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
        public List<String> eligibleNicknames;
    }
}
