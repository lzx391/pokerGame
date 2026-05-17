package com.example.mgdemoplus.controller;

import com.example.mgdemoplus.bo.DpRoomBO;
import com.example.mgdemoplus.bo.DpRoomLobbySearchParamBO;
import com.example.mgdemoplus.entity.DpRoom;
import com.example.mgdemoplus.entity.DpUser;
import com.example.mgdemoplus.mapper.DpUserMapper;
import com.example.mgdemoplus.service.DpRoomHallService;
import com.example.mgdemoplus.service.serviceImpl.DpRoomServiceImpl;
import com.example.mgdemoplus.utils.ResultUtil;
import com.example.mgdemoplus.service.serviceImpl.DpRoomServiceImpl.KickPlayersBatchResult;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.server.ResponseStatusException;


@RestController
@RequestMapping("/dpRoom")
public class DpRoomController {

    @Autowired
    private DpRoomServiceImpl dpRoomService;
    @Autowired
    private DpRoomHallService dpRoomHallService;
    @Autowired
    private DpUserMapper dpUserMapper;

    // 注意：不要在 Controller 里再建 roomMap，所有数据操作都走 Service

    private DpUser requireCurrentUser(ResultUtil fallback) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
            fallback.setSuccess(false);
            fallback.setMessage("未登录或登录已失效");
            return null;
        }
        DpUser u = dpUserMapper.selectByNickname(auth.getName());
        if (u == null) {
            fallback.setSuccess(false);
            fallback.setMessage("用户不存在或未同步");
            return null;
        }
        return u;
    }

    @PostMapping("/createRoom")
    public DpRoomBO createRoom(@RequestParam String nickname,
                             @RequestParam(required = false) Integer userId,
                             @RequestParam(required = false, defaultValue = "5") int smallBlindChips,
                             @RequestParam(required = false, defaultValue = "10") int bigBlindChips,
                             @RequestParam(required = false, defaultValue = "50") int startingStackBb,
                             @RequestParam(required = false, defaultValue = "9") int maxSeatCount,
                             @RequestParam(required = false) String roomPassword) {
        if (maxSeatCount < DpRoomBO.MIN_SEAT_COUNT
                || maxSeatCount > DpRoomBO.MAX_SEAT_COUNT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "人数上限需在 2～9 之间");
        }
        return dpRoomService.createRoom(nickname, userId, smallBlindChips, bigBlindChips, startingStackBb, roomPassword, maxSeatCount);
    }

    @GetMapping("/{roomId}/chat/recent")
    public ResultUtil recentRoomChat(
            @PathVariable String roomId,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        ResultUtil err = ResultUtil.error();
        DpUser me = requireCurrentUser(err);
        if (me == null) {
            return err.data("message", err.getMessage());
        }
        ResultUtil payload = dpRoomService.listRecentRoomChat(roomId, me.getNickname(), limit);
        if (payload == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "房间不存在");
        }
        return payload;
    }

    @GetMapping("/getNowRoom")
    public DpRoomBO getNowRoom(@RequestParam String roomId,
                             @RequestParam(required = false) String nickname) {
        return dpRoomService.getRoomSnapshotForViewer(roomId, nickname);
    }

    @PostMapping("/joinRoom")
    public String joinRoom(@RequestParam String roomId, @RequestParam String nickname,
                           @RequestParam(required = false) Integer userId,
                           @RequestParam(required = false) String roomPassword) {
        return dpRoomService.joinRoom(roomId, nickname, userId, roomPassword);
    }
    /**
     * 与 {@link #joinRoom} 业务相同；鉴权由全局 {@link com.example.mgdemoplus.security.JwtAuthenticationFilter} 完成，
     * 此处仅校验 JWT subject（昵称）与参数 {@code nickname} 一致。
     */
    @PostMapping("/joinRoom2")
    public ResultUtil joinRoom2(@RequestParam String roomId,
                                @RequestParam String nickname,
                                @RequestParam(required = false) Integer userId,
                                @RequestParam(required = false) String roomPassword) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String jwtNickname = auth != null ? auth.getName() : null;
        if (jwtNickname == null || !jwtNickname.equals(nickname)) {
            return ResultUtil.error().data("message", "token 与当前昵称不一致");
        }

        String outcome = dpRoomService.joinRoom(roomId, nickname, userId, roomPassword);
        if ("ok".equals(outcome) || "游戏已开始".equals(outcome)) {
            return ResultUtil.ok().data("message", outcome);
        }
        return ResultUtil.error().data("message", outcome);
    }

    /**
     * 大厅快速匹配（需登录）：先尝试并进已有公开房；若无空位则入默认 FIFO 队列（小盲 5、9 人桌），满两名玩家服务端自动建新公开房。
     * 排队中由 {@code /ws/dp-quick-match} 推送状态；离开队列可 {@link #quickMatchCancel2}。
     */
    @PostMapping("/quickMatch2")
    public ResultUtil quickMatch2(@RequestParam String nickname,
                                   @RequestParam(required = false) Integer userId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String jwtNickname = auth != null ? auth.getName() : null;
        if (jwtNickname == null || !jwtNickname.equals(nickname)) {
            return ResultUtil.error().data("message", "token 与当前昵称不一致");
        }
        return dpRoomService.quickMatchJoinQueueOrImmediate(nickname, userId);
    }

    /** 取消默认快匹排队（无需在匹配成功后的房间再调）。 */
    @PostMapping("/quickMatchCancel2")
    public ResultUtil quickMatchCancel2(@RequestParam String nickname) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String jwtNickname = auth != null ? auth.getName() : null;
        if (jwtNickname == null || !jwtNickname.equals(nickname)) {
            return ResultUtil.error().data("message", "token 与当前昵称不一致");
        }
        boolean removed = dpRoomService.cancelDefaultQuickMatchWait(nickname);
        return ResultUtil.ok().data("cancelled", removed);
    }
/**
 * 该房间内玩家是否能准备成功的接口
 * */
    @PostMapping("/toggleReady")
    public String toggleReady(@RequestParam String roomId, @RequestParam String nickname) {
        return dpRoomService.toggleReady(roomId, nickname) ? "ok" : "fail";
    }

    @PostMapping("/exitRoom")
    public String exitRoom(@RequestParam String roomId, @RequestParam String nickname) {
        return dpRoomService.exitRoom(roomId, nickname) ? "ok" : "fail";
    }

    @PostMapping("/startGame")
    public String startGame(@RequestParam String roomId, @RequestParam String ownerNickname) {
        return dpRoomService.startGame(roomId, ownerNickname) ? "ok" : "fail";
    }

    @PostMapping("/newHand")
    public String newHand(@RequestParam String roomId, @RequestParam String ownerNickname) {
        return dpRoomService.newHand(roomId) ? "ok" : "fail";
    }

    @PostMapping("/bet")
    public String bet(@RequestParam String roomId, @RequestParam String nickname, @RequestParam int bet) {
        return dpRoomService.bet(roomId, nickname, bet) ? "ok" : "fail";
    }

    @PostMapping("/fold")
    public String fold(@RequestParam String roomId, @RequestParam String nickname) {
        return dpRoomService.fold(roomId, nickname) ? "ok" : "fail";
    }

//    @PostMapping("/nextStage")
//    public String nextStage(@RequestParam String roomId, @RequestParam String ownerNickname) {
//        return dpRoomService.nextStage(roomId) ? "ok" : "fail";
//    }

    // 按池结算：参数格式 "0:Alice;1:Bob,Charlie"
//    @PostMapping("/judgeWin")
//    public String judgeWin(@RequestParam String roomId, @RequestParam String potWinners) {
//        return dpRoomService.judgeWin(roomId, potWinners) ? "ok" : "fail";
//    }
    @PostMapping("/kickPlayer")
    public String kickPlayer(@RequestParam String roomId,@RequestParam String nickname){
       return dpRoomService.kickPlayer(roomId,nickname) ? "ok":"fail";
    }

    /**
     * 房主批量踢人至观众席；{@code nicknames} 为英文逗号分隔（会去重，顺序执行，大厅最多同步一次）。
     */
    @PostMapping("/kickPlayersBatch")
    public ResultUtil kickPlayersBatch(@RequestParam String roomId, @RequestParam String nicknames) {
        KickPlayersBatchResult batch = dpRoomService.kickPlayersBatch(roomId, nicknames);
        if (batch.getAttempted() == 0) {
            return ResultUtil.error().data("message", "未包含有效玩家昵称");
        }
        if (batch.getSuccessCount() == 0) {
            return ResultUtil.error()
                    .data("message", "未能踢出任何玩家（请确认均在座）")
                    .data("successCount", 0)
                    .data("failCount", batch.getFailCount())
                    .data("failedNicknames", batch.getFailedNicknames());
        }
        ResultUtil ok = ResultUtil.ok();
        ok.data("successCount", batch.getSuccessCount());
        ok.data("failCount", batch.getFailCount());
        ok.data("failedNicknames", batch.getFailedNicknames());
        if (batch.getFailCount() > 0) {
            ok.data("message", "部分玩家未能踢出");
        }
        return ok;
    }
    @PostMapping("/heartbeat")
    public void heartbeat(@RequestParam String roomId, @RequestParam String nickname) {
        dpRoomService.heartbeat(roomId, nickname);
    }

    /**
     * 观战玩家：标记在下一局加入对局
     */
    @PostMapping("/readyNextHand")
    public String readyNextHand(@RequestParam String roomId, @RequestParam String nickname,
                                @RequestParam(required = false) Integer userId) {
        return dpRoomService.readyNextHand(roomId, nickname, userId) ? "ok" : "人数已满";
    }

    /**
     * 观战玩家：取消下一局加入，从候补列表移除。
     */
    @PostMapping("/cancelReadyNextHand")
    public String cancelReadyNextHand(@RequestParam String roomId, @RequestParam String nickname,
                                      @RequestParam(required = false) Integer userId) {
        return dpRoomService.cancelReadyNextHand(roomId, nickname, userId) ? "ok" : "fail";
    }

    /**
     * 结算后筹码为 0 的玩家补码到初始筹码。
     */
    @PostMapping("/rebuy")
    public String rebuy(@RequestParam String roomId, @RequestParam String nickname) {
        return dpRoomService.rebuy(roomId, nickname) ? "ok" : "fail";
    }

    /**
     * 鱼式 NPC：服务端生成 {@code BOT_FISH_<房间序号>} 加入下一局。
     */
    @PostMapping("/addDemoBot")
    public String addDemoBot(@RequestParam String roomId) {
        return dpRoomService.addDemoBotToNextHand(roomId) ? "ok" : "fail";
    }

    /**
     * 疯子 NPC：{@code BOT_MANIAC_<房间序号>}。
     */
    @PostMapping("/addManiacBot")
    public String addManiacBot(@RequestParam String roomId) {
        return dpRoomService.addManiacBotToNextHand(roomId) ? "ok" : "fail";
    }

    /**
     * 兼容旧前端：固定昵称 BOT_Shark（紧凶）。
     */
    @PostMapping("/addSharkBot")
    public String addSharkBot(@RequestParam String roomId) {
        return dpRoomService.addSharkBotToNextHand(roomId) ? "ok" : "fail";
    }

    /**
     * 紧凶 NPC：{@code BOT_TAG_<房间序号>}。
     */
    @PostMapping("/addTagBot")
    public String addTagBot(@RequestParam String roomId) {
        return dpRoomService.addTagBotToNextHand(roomId) ? "ok" : "fail";
    }

    /** 松凶 {@code BOT_LAG_<房间序号>} */
    @PostMapping("/addLagBot")
    public String addLagBot(@RequestParam String roomId) {
        return dpRoomService.addLagBotToNextHand(roomId) ? "ok" : "fail";
    }

    /** 紧弱 Nit {@code BOT_NIT_<房间序号>} */
    @PostMapping("/addNitBot")
    public String addNitBot(@RequestParam String roomId) {
        return dpRoomService.addNitBotToNextHand(roomId) ? "ok" : "fail";
    }

    /** 跟注站 {@code BOT_CALL_<房间序号>} */
    @PostMapping("/addCallStationBot")
    public String addCallStationBot(@RequestParam String roomId) {
        return dpRoomService.addCallStationBotToNextHand(roomId) ? "ok" : "fail";
    }

    /**
     * 批量添加同一档位 NPC（共用房间内递增序号）。{@code archetype} 支持 TAG、LAG、NIT、FISH、CALL、MANIAC（或带 BOT_ 前缀）。
     */
    @PostMapping("/addRuleNpcBatch")
    public String addRuleNpcBatch(@RequestParam String roomId,
            @RequestParam String archetype,
            @RequestParam(defaultValue = "1") int count) {
        return dpRoomService.addRuleNpcBatchToNextHand(roomId, archetype, count) ? "ok" : "fail";
    }

    /**
     * 大模型 NPC：{@code BOT_LLM_<房间序号>}；需配置 ARK_API_KEY、ARK_ENDPOINT_ID。
     */
    @PostMapping("/addLlmBot")
    public String addLlmBot(@RequestParam String roomId) {
        return dpRoomService.addLlmBotToNextHand(roomId) ? "ok" : "fail";
    }

    /** 全局叙事版大模型 NPC：{@code BOT_LLM_GLOBAL_<序号>}。 */
    @PostMapping("/addLlmGlobalBot")
    public String addLlmGlobalBot(@RequestParam String roomId) {
        return dpRoomService.addGlobalLlmBotToNextHand(roomId) ? "ok" : "fail";
    }

    /**
     * 房主主动移交房主给房间内的另一位玩家
     */
    @PostMapping("/transferOwner")
    public String transferOwner(@RequestParam String roomId,
                                @RequestParam String fromNickname,
                                @RequestParam String toNickname) {
        return dpRoomService.transferOwner(roomId, fromNickname, toNickname) ? "ok" : "fail";
    }
    @GetMapping("/getAllRooms2")
    public List<DpRoom> getAllRooms2() {
        return dpRoomService.getAllRooms2();
    }

    @GetMapping("/publicRooms")
    public Map<String, Object> publicRooms(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int pageSize) {
        Map<String, Object> out = new HashMap<>();
        var payload = dpRoomHallService.getPublicRoomsPage(page, pageSize);
        out.put("list", payload.getList());
        out.put("total", payload.getTotal());
        out.put("page", payload.getPage());
        out.put("pageSize", payload.getPageSize());
        return out;
    }

    /** 筛选 / 精确房间号：只走 MySQL（MyBatis-Plus），不经 Redis */
    @GetMapping("/publicRooms/query")
    public Map<String, Object> publicRoomsQuery(DpRoomLobbySearchParamBO param) {
        Map<String, Object> out = new HashMap<>();
        var payload = dpRoomHallService.queryPublicRoomsFromDb(param);
        out.put("list", payload.getList());
        out.put("total", payload.getTotal());
        out.put("page", payload.getPage());
        out.put("pageSize", payload.getPageSize());
        return out;
    }
}
