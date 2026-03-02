package com.example.mgdemoplus.controller;

    import com.example.mgdemoplus.entity.DpRoom;
    import com.example.mgdemoplus.service.studentImpl.DpRoomServiceImpl;
    import org.springframework.beans.factory.annotation.Autowired;
    import org.springframework.web.bind.annotation.*;
    import java.util.List;

@RestController
@RequestMapping("/dpRoom")
public class DpRoomController {

    @Autowired
    private DpRoomServiceImpl service;

    @PostMapping("/createRoom")
    public DpRoom createRoom(@RequestParam String nickname) {
        return service.createRoom(nickname);
    }

    @GetMapping("/getAllRooms")
    public List<DpRoom> getAllRooms() {
        return service.getAllRooms();
    }

    @PostMapping("/joinRoom")
    public String joinRoom(@RequestParam String roomId, @RequestParam String nickname) {
        return service.joinRoom(roomId, nickname);
    }

    @PostMapping("/toggleReady")
    public String toggleReady(@RequestParam String roomId, @RequestParam String nickname) {
        return service.toggleReady(roomId, nickname) ? "ok" : "fail";
    }

    @PostMapping("/exitRoom")
    public String exitRoom(@RequestParam String roomId, @RequestParam String nickname) {
        return service.exitRoom(roomId, nickname) ? "ok" : "fail";
    }

    @PostMapping("/startGame")
    public String startGame(@RequestParam String roomId, @RequestParam String ownerNickname) {
        return service.startGame(roomId, ownerNickname) ? "游戏开始" : "失败";
    }

    @PostMapping("/dealCards")
    public String dealCards(@RequestParam String roomId, @RequestParam String owner) {
        return service.dealCards(roomId, owner) ? "发牌成功" : "失败";
    }

    @PostMapping("/bet")
    public String bet(@RequestParam String roomId, @RequestParam String nickname, @RequestParam int bet) {
        return service.bet(roomId, nickname, bet) ? "下注成功" : "失败";
    }

    @PostMapping("/fold")
    public String fold(@RequestParam String roomId, @RequestParam String nickname) {
        return service.fold(roomId, nickname) ? "弃牌成功" : "失败";
    }

    @PostMapping("/judgeWin")
    public String judgeWin(@RequestParam String roomId, @RequestParam String winner) {
        return service.judgeWin(roomId, winner) ? "判赢成功" : "失败";
    }
    @PostMapping("/heartbeat")
    public void heartbeat(
            @RequestParam String roomId,
            @RequestParam String nickname) {
        service.heartbeat(roomId, nickname);
    }
}