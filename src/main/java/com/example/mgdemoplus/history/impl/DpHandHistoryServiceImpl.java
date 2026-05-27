package com.example.mgdemoplus.history.impl;

import com.example.mgdemoplus.history.entity.DpObservedHandHistory;
import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.history.mapper.DpHandHistoryQueryMapper;
import com.example.mgdemoplus.history.mapper.DpObservedHandHistoryMapper;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import com.example.mgdemoplus.history.DpHandHistoryService;
import com.example.mgdemoplus.history.support.DpHandHistoryPayloadSanitizer;
import com.example.mgdemoplus.history.vo.DpHandHistoryDetailVO;
import com.example.mgdemoplus.history.vo.DpHandHistoryListItemVO;
import com.example.mgdemoplus.history.vo.DpHandHistoryPageVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class DpHandHistoryServiceImpl implements DpHandHistoryService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final DpHandHistoryQueryMapper queryMapper;
    private final DpUserMapper dpUserMapper;
    private final DpObservedHandHistoryMapper observedHandHistoryMapper;
    private final ObjectMapper objectMapper;

    public DpHandHistoryServiceImpl(
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
     * 涓や汉鍏卞悓鍙備笌杩囩殑瀵瑰眬锛氬悓涓€鎵嬬墝涓婁袱鏉″弬涓庤€咃紙鍧囨寜 user_id锛夛紱鍒嗛〉鐢?PageHelper + PageInfo銆?     */
    public DpHandHistoryPageVO checkUserAndOtherPlayerHandHistoryList(
            Integer userId,
            Integer otherUserId,
            int page,
            int pageSize
    ) {
        DpHandHistoryPageVO out = new DpHandHistoryPageVO();
        out.setPage(Math.max(page, 1));
        int size = pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE;
        size = Math.min(size, MAX_PAGE_SIZE);
        out.setPageSize(size);

        if (userId == null || otherUserId == null) {
            out.setTotal(0);
            out.setRecords(Collections.emptyList());
            return out;
        }

        DpUser u = dpUserMapper.selectById(userId);
        DpUser other = dpUserMapper.selectById(otherUserId);
        if (u == null || other == null) {
            out.setTotal(0);
            out.setRecords(Collections.emptyList());
            return out;
        }

        PageHelper.startPage(out.getPage(), size);
        List<DpHandHistoryListItemVO> records = queryMapper.listCommonHandsBothUserIds(userId, otherUserId);
        PageInfo<DpHandHistoryListItemVO> pageInfo = new PageInfo<>(records);
        out.setTotal(pageInfo.getTotal());
        out.setRecords(records);
        return out;
    }

    /**
     * 褰撳墠鐧诲綍鐢ㄦ埛锛堜粎鎸?dp_user.id锛屽弬涓庤€呰〃椤诲惈瀵瑰簲 user_id锛夈€?     */
    public DpHandHistoryPageVO listMyHandsPage(Integer userId, int page, int pageSize) {
        DpHandHistoryPageVO out = new DpHandHistoryPageVO();
        out.setPage(Math.max(page, 1));
        int size = pageSize > 0 ? pageSize : DEFAULT_PAGE_SIZE;
        size = Math.min(size, MAX_PAGE_SIZE);
        out.setPageSize(size);

        if (userId == null) {
            out.setTotal(0);
            out.setRecords(Collections.emptyList());
            return out;
        }

        if (dpUserMapper.selectById(userId) == null) {
            out.setTotal(0);
            out.setRecords(Collections.emptyList());
            return out;
        }

        PageHelper.startPage(out.getPage(), size);
        List<DpHandHistoryListItemVO> records = queryMapper.listForUserId(userId);
        PageInfo<DpHandHistoryListItemVO> pageInfo = new PageInfo<>(records);
        out.setTotal(pageInfo.getTotal());
        out.setRecords(records);
        return out;
    }

    /**
     * 鍗曟潯鐗岃氨璇︽儏锛氫粎褰撹 user_id 鍦ㄥ弬涓庤€呰〃涓叧鑱旇鎵嬫椂鍙銆?     */
    public DpHandHistoryDetailVO getDetail(long handHistoryId, Integer userId) {

        if (userId == null) {
            return null;
        }
        DpUser viewer = dpUserMapper.selectById(userId);
        if (viewer == null) {
            return null;
        }
        if (queryMapper.countParticipantForHand(handHistoryId, userId) == 0) {
            return null;
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

        payload = DpHandHistoryPayloadSanitizer.sanitize(payload, viewer.getNickname());

        DpHandHistoryDetailVO dto = new DpHandHistoryDetailVO();
        dto.setHandHistoryId(row.getId());
        dto.setRoomId(row.getRoomId());
        dto.setHandSeed(row.getHandSeed());
        dto.setStartedAtMs(row.getStartedAtMs());
        dto.setEndedAtMs(row.getEndedAtMs());
        dto.setSmallBlindChips(row.getSmallBlindChips());
        dto.setBigBlindChips(row.getBigBlindChips());
        dto.setDealerNickname(row.getDealerNickname());
        dto.setMainPotBeforeSettlement(row.getMainPotBeforeSettlement());
        dto.setPayloadVersion(row.getPayloadVersion());
        dto.setPayload(payload);
        return dto;
    }

}
