package com.example.mgdemoplus.controller;

import com.example.mgdemoplus.entity.DpRoom;
import com.example.mgdemoplus.service.studentImpl.DpRoomServiceImpl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@CrossOrigin(origins = "*", allowCredentials = "false")
@RestController
@RequestMapping("/dpRoom")
public class DpRoomController {

    @Autowired
    private DpRoomServiceImpl dpRoomService;

    @PostMapping("/createRoom")
    public DpRoom createRoom(@RequestParam String nickname) {
        return dpRoomService.createRoom(nickname);
    }

    @GetMapping("/getAllRooms")
    public List<DpRoom> getAllRooms() {
        return dpRoomService.getAllRooms();
    }

    @PostMapping("/joinRoom")
    public String joinRoom(@RequestParam String roomId, @RequestParam String nickname) {
        return dpRoomService.joinRoom(roomId, nickname);
    }

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

    @PostMapping("/nextStage")
    public String nextStage(@RequestParam String roomId, @RequestParam String ownerNickname) {
        return dpRoomService.nextStage(roomId) ? "ok" : "fail";
    }

    @PostMapping("/judgeWin")
    public String judgeWin(@RequestParam String roomId, @RequestParam String winnerNickname) {
        return dpRoomService.judgeWin(roomId, winnerNickname) ? "ok" : "fail";
    }

    @PostMapping("/heartbeat")
    public void heartbeat(@RequestParam String roomId, @RequestParam String nickname) {
        dpRoomService.heartbeat(roomId, nickname);
    }
}