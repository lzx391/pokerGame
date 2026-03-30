package com.example.mgdemoplus.controller.dp;

import com.example.mgdemoplus.dto.DpHandHistoryPageDTO;
import com.example.mgdemoplus.service.serviceImpl.dp.DpHandHistoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dpHandHistory")
public class DpHandHistoryController {

    private final DpHandHistoryService handHistoryService;

    public DpHandHistoryController(DpHandHistoryService handHistoryService) {
        this.handHistoryService = handHistoryService;
    }

    /**
     * 我参与过的对局摘要分页（参与者表 JOIN 牌谱主表；无 PageHelper，使用 COUNT + LIMIT）。
     *
     * @param nickname 必填，与登录一致
     * @param userId   可选；若传则须与 dp_user 中该 id 的昵称一致
     * @param page     页码，从 1 开始，默认 1
     * @param pageSize 每页条数，默认 10，最大 100
     */
    @GetMapping("/list")
    public DpHandHistoryPageDTO list(
            @RequestParam String nickname,
            @RequestParam(required = false) Integer userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize
    ) {
        return handHistoryService.listMyHandsPage(userId, nickname, page, pageSize);
    }
}
