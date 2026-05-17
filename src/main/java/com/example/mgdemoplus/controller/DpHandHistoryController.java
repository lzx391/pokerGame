package com.example.mgdemoplus.controller;

import com.example.mgdemoplus.service.DpHandHistoryService;
import com.example.mgdemoplus.vo.DpHandHistoryDetailVO;
import com.example.mgdemoplus.vo.DpHandHistoryPageVO;

// import com.example.mgdemoplus.service.serviceImpl.DpHandHistoryServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dpHandHistory")
public class DpHandHistoryController {

    // private final DpHandHistoryServiceImpl handHistoryService;
    private final DpHandHistoryService handHistoryService;
    public DpHandHistoryController(DpHandHistoryService handHistoryService) {
        this.handHistoryService = handHistoryService;
    }

    /**
     * 我参与过的对局摘要分页（参与者表按 user_id；PageHelper 分页，总条数见 PageInfo）。
     *
     * @param userId   必填，dp_user 主键
     * @param page     页码，从 1 开始，默认 1
     * @param pageSize 每页条数，默认 10，最大 100
     */
    @GetMapping("/list")
    public DpHandHistoryPageVO list(
            @RequestParam Integer userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return handHistoryService.listMyHandsPage(userId, page, pageSize);
    }

    /**
     * 单条牌谱回放数据（payload）；仅参与者（user_id）可读，他人洞牌已脱敏。
     */
    @GetMapping("/detail")
    public ResponseEntity<DpHandHistoryDetailVO> detail(
            @RequestParam long handHistoryId,
            @RequestParam Integer userId
    ) {
        DpHandHistoryDetailVO dto = handHistoryService.getDetail(handHistoryId, userId);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/checkUserAndOtherPlayerHandHistoryList")
    public DpHandHistoryPageVO checkUserAndOtherPlayerHandHistoryList(
            @RequestParam Integer userId,
            @RequestParam Integer otherUserId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return handHistoryService.checkUserAndOtherPlayerHandHistoryList(userId, otherUserId, page, pageSize);
    }
}
