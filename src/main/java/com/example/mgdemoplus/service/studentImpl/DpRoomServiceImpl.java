package com.example.mgdemoplus.service.studentImpl;


import com.example.mgdemoplus.entity.DpPlayer;
import com.example.mgdemoplus.entity.DpRoom;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DpRoomServiceImpl {
    private final Map<String, DpRoom> roomMap = new ConcurrentHashMap<>();

    public DpRoomServiceImpl() {
        // 心跳清理 + 超时行动
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                for (DpRoom room : roomMap.values()) {
                    Iterator<DpPlayer> it = room.getPlayers().iterator();
                    while (it.hasNext()) {
                        DpPlayer p = it.next();
                        if (System.currentTimeMillis() - p.getLastHeartBeat() > 10000) {
                            it.remove();
                        }
                    }
                    if (room.getPlayers().isEmpty()) {
                        roomMap.remove(room.getRoomId());
                        continue;
                    }
                    // 30秒超时弃牌
                    if (room.isPlaying() && room.getCurrentActorIndex() >= 0) {
                        if (System.currentTimeMillis() - room.getLastActionTime() > 30000) {
                            DpPlayer p = room.getPlayers().get(room.getCurrentActorIndex());
                            p.setFold(true);
                            nextActor(room);
                        }
                    }
                }
            }
        }, 0, 2000);
    }

    private List<String> newDeck() {
        List<String> deck = new ArrayList<>();
        // h-红桃, d-方块, c-梅花, s-黑桃
        String[] suits = {"hearts", "diamonds", "clubs", "spades"};
        String[] ranks = {"2","3","4","5","6","7","8","9","10","J","Q","K","A"};
        for (String s : suits) {
            for (String r : ranks) {
                deck.add(s + "_" + r);
            }
        }
        Collections.shuffle(deck);
        return deck;
    }

    private void nextActor(DpRoom room) {
        int size = room.getPlayers().size();
        for (int i = 1; i <= size; i++) {
            int idx = (room.getCurrentActorIndex() + i) % size;
            if (!room.getPlayers().get(idx).isFold()) {
                room.setCurrentActorIndex(idx);
                room.setLastActionTime(System.currentTimeMillis());
                return;
            }
        }
        room.setCurrentActorIndex(-1);
    }

    public DpRoom createRoom(String ownerNickname) {
        String id = UUID.randomUUID().toString().substring(0,8);
        DpRoom r = new DpRoom();
        r.setRoomId(id);
        r.setOwner(ownerNickname);
        DpPlayer p = new DpPlayer();
        p.setNickname(ownerNickname);
        p.setReady(true);
        r.getPlayers().add(p);
        roomMap.put(id, r);
        return r;
    }

    public List<DpRoom> getAllRooms() {
        return new ArrayList<>(roomMap.values());
    }

    public String joinRoom(String roomId, String nickname) {
        DpRoom r = roomMap.get(roomId);
        if (r == null) return "房间不存在";
        if (r.isPlaying()) return "游戏已开始";
        for (DpPlayer p : r.getPlayers()) if (p.getNickname().equals(nickname)) return "你已在房间中";
        DpPlayer p = new DpPlayer();
        p.setNickname(nickname);
        r.getPlayers().add(p);
        return "ok";
    }

    public boolean toggleReady(String roomId, String nickname) {
        DpRoom r = roomMap.get(roomId);
        if (r == null || r.isPlaying()) return false;
        for (DpPlayer p : r.getPlayers()) {
            if (p.getNickname().equals(nickname)) {
                p.setReady(!p.isReady());
                return true;
            }
        }
        return false;
    }

    public boolean exitRoom(String roomId, String nickname) {
        DpRoom r = roomMap.get(roomId);
        if (r == null) return false;
        if (r.getOwner().equals(nickname)) {
            roomMap.remove(roomId);
            return true;
        }
        return r.getPlayers().removeIf(p -> p.getNickname().equals(nickname));
    }

    public boolean startGame(String roomId, String ownerNickname) {
        DpRoom r = roomMap.get(roomId);
        if (r == null || !r.getOwner().equals(ownerNickname)) return false;
        r.setPlaying(true);
        r.setCurrentStage("preflop");
        r.setCommunityCards(new ArrayList<>());
        r.setPot(0);
        r.setCurrentBetToCall(0);
        for (DpPlayer p : r.getPlayers()) {
            p.setChips(500);
            p.setFold(false);
            p.setBet(0);
            p.setHoleCards(new ArrayList<>());
            p.setDealer(false);
            p.setBlind(0);
        }
        r.getPlayers().get(0).setDealer(true);
        return newHand(roomId);
    }

    public boolean newHand(String roomId) {
        DpRoom r = roomMap.get(roomId);
        if (r == null || !r.isPlaying()) return false;
        r.setDeck(newDeck());
        r.setCommunityCards(new ArrayList<>());
        r.setCurrentStage("preflop");
        r.setPot(0);
        r.setCurrentBetToCall(0);
        List<DpPlayer> ps = r.getPlayers();
        for (DpPlayer p : ps) {
            p.setFold(false);
            p.setBet(0);
            p.setBlind(0);
            p.setActed(false);  // ← 加这一行
            p.setHoleCards(Arrays.asList(r.getDeck().remove(0), r.getDeck().remove(0)));
        }
        // 庄家轮动
        Optional<DpPlayer> dealer = ps.stream().filter(DpPlayer::isDealer).findFirst();
        int did = dealer.map(ps::indexOf).orElse(0);
        for (DpPlayer p : ps) p.setDealer(false);
        did = (did + 1) % ps.size();
        ps.get(did).setDealer(true);
        // 大小盲
        if (ps.size() >= 2) {
            int sb = (did + 1) % ps.size();
            int bb = (did + 2) % ps.size();
            ps.get(sb).setBlind(1);
            ps.get(sb).setChips(ps.get(sb).getChips() - 5);
            ps.get(sb).setBet(5);
            ps.get(bb).setBlind(2);
            ps.get(bb).setChips(ps.get(bb).getChips() - 10);
            ps.get(bb).setBet(10);
            r.setCurrentBetToCall(10);
            r.setPot(15);  //
        }
        r.setCurrentActorIndex((did + 3) % ps.size());
        r.setLastActionTime(System.currentTimeMillis());
        return true;
    }

    public boolean bet(String roomId, String nickname, int amount) {
        DpRoom r = roomMap.get(roomId);
        if (r == null || r.getCurrentActorIndex() < 0) return false;

        DpPlayer p = r.getPlayers().get(r.getCurrentActorIndex());
        if (!p.getNickname().equals(nickname)) return false;

        // --- 陛下看这里：新增校验 ---
        // 校验1：防止积分变成负数
        if (amount < 0 || p.getChips() < amount) return false;

        int totalBet = p.getBet() + amount;

        // 校验2：下注总额必须【至少跟平】当前注额 (解决下注为0跳过的问题)
        // 只有在 totalBet >= r.getCurrentBetToCall() 时才合法
        if (totalBet < r.getCurrentBetToCall()) return false;

        // 执行扣款和增注
        p.setChips(p.getChips() - amount);
        p.setBet(totalBet);
        p.setActed(true);   // ← 加这一行
        r.setPot(r.getPot() + amount);

        // 如果是加注，更新全局最高注额
        if (totalBet > r.getCurrentBetToCall()) {
            r.setCurrentBetToCall(totalBet);
        }

        // 移动到下一个行动者
        moveToNextValidActor(r);
        return true;
    }

    private void moveToNextValidActor(DpRoom r) {
        int size = r.getPlayers().size();
        int startIdx = r.getCurrentActorIndex();

        for (int i = 1; i <= size; i++) {
            int nextIdx = (startIdx + i) % size;
            DpPlayer nextP = r.getPlayers().get(nextIdx);

            // 没弃牌 且 (还没行动过 或 下注不够)
            if (!nextP.isFold() && (!nextP.isActed() || nextP.getBet() < r.getCurrentBetToCall())) {
                r.setCurrentActorIndex(nextIdx);
                r.setLastActionTime(System.currentTimeMillis());
                return;
            }
        }

        r.setCurrentActorIndex(-1);
    }

    public boolean fold(String roomId, String nickname) {
        DpRoom r = roomMap.get(roomId);
        if (r == null || !r.isPlaying() || r.getCurrentActorIndex() < 0) return false;
        DpPlayer p = r.getPlayers().get(r.getCurrentActorIndex());
        if (!p.getNickname().equals(nickname) || p.isFold()) return false;
        p.setFold(true);
        nextActor(r);
        return true;
    }

    // 2. 完善下一轮逻辑：解决结算闭环
    public boolean nextStage(String roomId) {
        DpRoom r = roomMap.get(roomId);
        // 只有当所有人筹码跟平（currentActorIndex 为 -1）时，房主才能点下一轮
        if (r == null || !r.isPlaying() || r.getCurrentActorIndex() >= 0) return false;

        // 清理本轮投注数据
        for (DpPlayer p : r.getPlayers()) {
            p.setBet(0);
            p.setActed(false);  // ← 加这一行
        }

        r.setCurrentBetToCall(0);

        List<String> deck = r.getDeck();
        switch (r.getCurrentStage()) {
            case "preflop":
                r.getCommunityCards().add(deck.remove(0));
                r.getCommunityCards().add(deck.remove(0));
                r.getCommunityCards().add(deck.remove(0));
                r.setCurrentStage("flop"); break;
            case "flop":
                r.getCommunityCards().add(deck.remove(0));
                r.setCurrentStage("turn"); break;
            case "turn":
                r.getCommunityCards().add(deck.remove(0));
                r.setCurrentStage("river"); break;
            case "river":
                r.setCurrentStage("showdown");
                // 进入摊牌，所有人停止下注，等待房主结算
                r.setCurrentActorIndex(-1);
                return true; // 直接返回，不需要重置行动人
            default: return false;
        }

        // 非 showdown 阶段，需要重新寻找第一位行动者（通常是庄家左手第一个没弃牌的人）
        Optional<DpPlayer> first = r.getPlayers().stream().filter(p -> !p.isFold()).findFirst();
        first.ifPresent(dpPlayer -> r.setCurrentActorIndex(r.getPlayers().indexOf(dpPlayer)));
        r.setLastActionTime(System.currentTimeMillis());
        return true;
    }

    // 3. 结算逻辑：陛下手动发钱
    public boolean judgeWin(String roomId, String winnerNicknames) {
        DpRoom r = roomMap.get(roomId);
        if (r == null || !r.isPlaying() || !"showdown".equals(r.getCurrentStage())) return false;

        // 1. 解析昵称（支持单个或多个用逗号分隔）
        String[] winners = winnerNicknames.split(",");
        if (winners.length == 0) return false;

        // 2. 计算平分金额（整除）
        int share = r.getPot() / winners.length;
        // 如果有余数（比如底池25，分给2个人），剩下的1分可以留在下个底池，或者给第一个人
        int remainder = r.getPot() % winners.length;

        // 3. 给选中的人发钱
        for (String name : winners) {
            for (DpPlayer p : r.getPlayers()) {
                if (p.getNickname().equals(name.trim())) {
                    p.setChips(p.getChips() + share);
                    // 把余数给第一个匹配到的赢家，保证底池完全分完
                    if (remainder > 0) {
                        p.setChips(p.getChips() + remainder);
                        remainder = 0;
                    }
                    break;
                }
            }
        }

        r.setPot(0); // 清空底池
        return newHand(roomId); // 开启下一局
    }

    public void heartbeat(String roomId, String nickname) {
        DpRoom r = roomMap.get(roomId);
        if (r == null) return;
        for (DpPlayer p : r.getPlayers()) {
            if (p.getNickname().equals(nickname)) p.setLastHeartBeat(System.currentTimeMillis());
        }
    }
}