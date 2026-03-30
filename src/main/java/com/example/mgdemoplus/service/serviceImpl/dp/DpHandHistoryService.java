package com.example.mgdemoplus.service.serviceImpl.dp;

import com.example.mgdemoplus.dto.DpHandHistoryListItemDTO;
import com.example.mgdemoplus.dto.DpHandHistoryPageDTO;
import com.example.mgdemoplus.entity.dp.DpUser;
import com.example.mgdemoplus.mapper.dp.DpHandHistoryQueryMapper;
import com.example.mgdemoplus.mapper.dp.DpUserMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class DpHandHistoryService {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final DpHandHistoryQueryMapper queryMapper;
    private final DpUserMapper dpUserMapper;

    public DpHandHistoryService(DpHandHistoryQueryMapper queryMapper, DpUserMapper dpUserMapper) {
        this.queryMapper = queryMapper;
        this.dpUserMapper = dpUserMapper;
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
}
