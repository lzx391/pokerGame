package com.example.mgdemoplus.controller.dp;

import com.example.mgdemoplus.bo.DpRoomBO;
import com.example.mgdemoplus.bo.DpRoomLobbySearchParamBO;
import com.example.mgdemoplus.entity.dp.DpRoom;
import com.example.mgdemoplus.service.dp.DpRoomHallService;
import com.example.mgdemoplus.service.serviceImpl.dp.DpRoomServiceImpl;
import com.example.mgdemoplus.utils.ResultUtil;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/dpRoom")
public class DpRoomController {

    @Autowired
    private DpRoomServiceImpl dpRoomService;
    @Autowired
    private DpRoomHallService dpRoomHallService;

    // 注意：不要在 Controller 里再建 roomMap，所有数据操作都走 Service

    @PostMapping("/createRoom")
    public DpRoomBO createRoom(@RequestParam String nickname,
                             @RequestParam(required = false) Integer userId,
                             @RequestParam(required = false, defaultValue = "5") int smallBlindChips,
                             @RequestParam(required = false, defaultValue = "10") int bigBlindChips,
                             @RequestParam(required = false, defaultValue = "50") int startingStackBb,
                             @RequestParam(required = false) String roomPassword) {
        return dpRoomService.createRoom(nickname, userId, smallBlindChips, bigBlindChips, startingStackBb, roomPassword);
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
     * 结算后筹码为 0 的玩家补码到初始筹码。
     */
    @PostMapping("/rebuy")
    public String rebuy(@RequestParam String roomId, @RequestParam String nickname) {
        return dpRoomService.rebuy(roomId, nickname) ? "ok" : "fail";
    }

    /**
     * 将简单鱼式 NPC（BOT_Fish，原 BOT_Demo）加入到指定房间的「下一局加入」列表中。
     * 主要用于当前阶段验证机器人流程是否正常工作，后续可扩展为通用添加机器人接口。
     */
    @PostMapping("/addDemoBot")
    public String addDemoBot(@RequestParam String roomId) {
        return dpRoomService.addDemoBotToNextHand(roomId) ? "ok" : "fail";
    }

    /**
     * 将疯子型 NPC（BOT_Maniac）加入到指定房间的「下一局加入」列表中。
     */
    @PostMapping("/addManiacBot")
    public String addManiacBot(@RequestParam String roomId) {
        return dpRoomService.addManiacBotToNextHand(roomId) ? "ok" : "fail";
    }

    /**
     * 兼容旧前端：下一局加入 BOT_Shark（与 TAG 共用同一套规则决策）。
     */
    @PostMapping("/addSharkBot")
    public String addSharkBot(@RequestParam String roomId) {
        return dpRoomService.addSharkBotToNextHand(roomId) ? "ok" : "fail";
    }

    /**
     * 将紧凶型 NPC（BOT_Tag）加入到指定房间的「下一局加入」列表中。
     */
    @PostMapping("/addTagBot")
    public String addTagBot(@RequestParam String roomId) {
        return dpRoomService.addTagBotToNextHand(roomId) ? "ok" : "fail";
    }

    /**
     * 将大模型 NPC（BOT_LLM）加入下一局；需配置环境变量 ARK_API_KEY、ARK_ENDPOINT_ID。
     */
    @PostMapping("/addLlmBot")
    public String addLlmBot(@RequestParam String roomId) {
        return dpRoomService.addLlmBotToNextHand(roomId) ? "ok" : "fail";
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
