package com.example.mgdemoplus.service.serviceImpl;

import com.example.mgdemoplus.entity.DpObservedHandHistory;
import com.example.mgdemoplus.entity.DpUser;
import com.example.mgdemoplus.mapper.DpHandHistoryQueryMapper;
import com.example.mgdemoplus.mapper.DpObservedHandHistoryMapper;
import com.example.mgdemoplus.mapper.DpUserMapper;
import com.example.mgdemoplus.service.DpHandHistoryService;
import com.example.mgdemoplus.vo.DpHandHistoryDetailVO;
import com.example.mgdemoplus.vo.DpHandHistoryListItemVO;
import com.example.mgdemoplus.vo.DpHandHistoryPageVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;

//这个服务类是负责前端查询返回的
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
     * 两人共同参与过的对局：同一手牌上两条参与者（均按 user_id）；分页由 PageHelper + PageInfo。
     */
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
     * 当前登录用户（仅按 dp_user.id，参与者表须含对应 user_id）。
     */
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
     * 单条牌谱详情：仅当该 user_id 在参与者表中关联该手时可读。
     */
    public DpHandHistoryDetailVO getDetail(long handHistoryId, Integer userId) {

        if (userId == null) {
            return null;
        }
        if (dpUserMapper.selectById(userId) == null) {
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
