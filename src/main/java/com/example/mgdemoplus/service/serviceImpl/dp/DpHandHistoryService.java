package com.example.mgdemoplus.service.serviceImpl.dp;

import com.example.mgdemoplus.dto.DpHandHistoryDetailDTO;
import com.example.mgdemoplus.dto.DpHandHistoryListItemDTO;
import com.example.mgdemoplus.dto.DpHandHistoryPageDTO;
import com.example.mgdemoplus.entity.dp.DpObservedHandHistory;
import com.example.mgdemoplus.entity.dp.DpUser;
import com.example.mgdemoplus.mapper.dp.DpHandHistoryQueryMapper;
import com.example.mgdemoplus.mapper.dp.DpObservedHandHistoryMapper;
import com.example.mgdemoplus.mapper.dp.DpUserMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class DpHandHistoryService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final DpHandHistoryQueryMapper queryMapper;
    private final DpUserMapper dpUserMapper;
    private final DpObservedHandHistoryMapper observedHandHistoryMapper;
    private final ObjectMapper objectMapper;

    public DpHandHistoryService(
            DpHandHistoryQueryMapper queryMapper,
            DpUserMapper dpUserMapper,
            DpObservedHandHistoryMapper observedHandHistoryMapper,
            ObjectMapper objectMapper
    ) {
        this.queryMapper = queryMapper;
        this.dpUserMapper = dpUserMapper;
        this.observedHandHistoryMapper = observedHandHistoryMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 当前登录用户（昵称必传；userId 若传须与 dp_user 中昵称一致）。
     *
     * @param page 从 1 开始
     */
    public DpHandHistoryPageDTO listMyHandsPage(Integer userId, String nickname, int page, int pageSize) {
        DpHandHistoryPageDTO out = new DpHandHistoryPageDTO();
        out.setPage(Math.max(page, 1));
        int size = pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE;
        size = Math.min(size, MAX_PAGE_SIZE);
        out.setPageSize(size);

        if (nickname == null || nickname.isEmpty()) {
            out.setTotal(0);
            out.setRecords(Collections.emptyList());
            return out;
        }

        int offset = (out.getPage() - 1) * size;

        if (userId != null) {
            DpUser u = dpUserMapper.selectById(userId);
            if (u == null || !nickname.equals(u.getNickname())) {
                out.setTotal(0);
                out.setRecords(Collections.emptyList());
                return out;
            }
            long total = queryMapper.countForUserWithId(userId, nickname);
            out.setTotal(total);
            List<DpHandHistoryListItemDTO> records = total == 0
                    ? Collections.emptyList()
                    : queryMapper.listForUserWithId(userId, nickname, offset, size);
            out.setRecords(records);
            return out;
        }

        long total = queryMapper.countForNicknameOnly(nickname);
        out.setTotal(total);
        List<DpHandHistoryListItemDTO> records = total == 0
                ? Collections.emptyList()
                : queryMapper.listForNicknameOnly(nickname, offset, size);
        out.setRecords(records);
        return out;
    }

    /**
     * 单条牌谱详情：仅当当前用户在参与者表中关联该手时可读；
     * payload 含完整 holeCardsAtEnd（服务端归档），前端按街与弃牌时机决定展示。
     */
    public DpHandHistoryDetailDTO getDetail(long handHistoryId, Integer userId, String nickname) {
        if (nickname == null || nickname.isEmpty()) {
            return null;
        }
        if (userId != null) {
            DpUser u = dpUserMapper.selectById(userId);
            if (u == null || !nickname.equals(u.getNickname())) {
                return null;
            }
            if (queryMapper.countParticipantForHandWithUserId(handHistoryId, userId, nickname) == 0) {
                return null;
            }
        } else {
            if (queryMapper.countParticipantForHandNicknameOnly(handHistoryId, nickname) == 0) {
                return null;
            }
        }

        DpObservedHandHistory row = observedHandHistoryMapper.selectById(handHistoryId);
        if (row == null) {
            return null;
        }

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(
                    row.getPayloadJson(),
                    new TypeReference<Map<String, Object>>() {
                    }
            );
        } catch (Exception e) {
            return null;
        }

        DpHandHistoryDetailDTO out = new DpHandHistoryDetailDTO();
        out.setHandHistoryId(row.getId());
        out.setRoomId(row.getRoomId());
        out.setHandSeed(row.getHandSeed());
        out.setStartedAtMs(row.getStartedAtMs());
        out.setEndedAtMs(row.getEndedAtMs());
        out.setSmallBlindChips(row.getSmallBlindChips());
        out.setBigBlindChips(row.getBigBlindChips());
        out.setDealerNickname(row.getDealerNickname());
        out.setMainPotBeforeSettlement(row.getMainPotBeforeSettlement());
        out.setPayloadVersion(row.getPayloadVersion());
        out.setPayload(payload);
        return out;
    }
}
