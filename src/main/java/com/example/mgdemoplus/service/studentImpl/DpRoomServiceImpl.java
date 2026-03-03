package com.example.mgdemoplus.service.studentImpl;

import com.example.mgdemoplus.entity.DpPlayer;
import com.example.mgdemoplus.entity.DpPot;
import com.example.mgdemoplus.entity.DpRoom;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
                        roomMap.remove(room.getRoomId());//房间空了就清人
                        continue;
                    }
                    // 30秒超时弃牌，距离上一个最后一个人行动后超过30秒不动弹则设置为弃牌
                    if (room.isPlaying() && room.getCurrentActorIndex() >= 0) {
                        if (System.currentTimeMillis() - room.getLastActionTime() > 30000) {
                            DpPlayer p = room.getPlayers().get(room.getCurrentActorIndex());
                            p.setFold(true);
                            moveToNextValidActor(room);  // 统一用新方法
                        }
                    }
                }
            }
        }, 0, 2000);
    }

    // ========== 工具方法 ==========

    private List<String> newDeck() {
        List<String> deck = new ArrayList<>();
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

    /**
     * 统一的行动者轮转方法
     * 跳过：已弃牌、已全下、已行动且下注已跟齐 的玩家
     */
    private void moveToNextValidActor(DpRoom r) {
        int size = r.getPlayers().size();
        int startIdx = r.getCurrentActorIndex();

        for (int i = 1; i <= size; i++) {
            int nextIdx = (startIdx + i) % size;
            DpPlayer nextP = r.getPlayers().get(nextIdx);

            // 没弃牌、没全下、且（还没行动 或 下注不够）
            if (!nextP.isFold() && !nextP.isAllIn()
                    && (!nextP.isActed() || nextP.getBet() < r.getCurrentBetToCall())) {
                r.setCurrentActorIndex(nextIdx);
                r.setLastActionTime(System.currentTimeMillis());
                return;
            }
        }

        r.setCurrentActorIndex(-1);  // 本轮所有人都行动完毕
    }

    /**
     * 计算主池和边池
     * 在进入 showdown 时调用
     */
    private void calculatePots(DpRoom r) {
        List<Integer> levels = r.getPlayers().stream()
                .map(DpPlayer::getTotalBet)
                .filter(b -> b > 0)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        List<DpPot> pots = new ArrayList<>();
        int prevLevel = 0;

        for (int level : levels) {
            int potAmount = 0;
            List<String> eligible = new ArrayList<>();

            for (DpPlayer p : r.getPlayers()) {
                int contribution = Math.min(p.getTotalBet(), level) - Math.min(p.getTotalBet(), prevLevel);
                potAmount += contribution;
                // 没弃牌 且 totalBet >= 这一层，才有资格赢这个池
                if (!p.isFold() && p.getTotalBet() >= level) {
                    eligible.add(p.getNickname());
                }
            }

            if (potAmount > 0) {
                DpPot pot = new DpPot();
                pot.setAmount(potAmount);
                pot.setEligiblePlayers(eligible);
                pots.add(pot);
            }
            prevLevel = level;
        }

        r.setPots(pots);
    }

    // ========== 房间管理 ==========

    public DpRoom createRoom(String ownerNickname) {
        String id = UUID.randomUUID().toString().substring(0, 8);
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
        for (DpPlayer p : r.getPlayers()) {
            if (p.getNickname().equals(nickname)) return "你已在房间中";
        }
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

    // ========== 游戏流程 ==========

    public boolean startGame(String roomId, String ownerNickname) {
        DpRoom r = roomMap.get(roomId);
        if (r == null || !r.getOwner().equals(ownerNickname)) return false;
        r.setPlaying(true);
        r.setCurrentStage("preflop");
        r.setCommunityCards(new ArrayList<>());
        r.setPot(0);
        r.setPots(new ArrayList<>());
        r.setCurrentBetToCall(0);
        for (DpPlayer p : r.getPlayers()) {
            p.setChips(500);
            p.setFold(false);
            p.setBet(0);
            p.setTotalBet(0);
            p.setAllIn(false);
            p.setActed(false);
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
        r.setPots(new ArrayList<>());
        r.setCurrentBetToCall(0);

        List<DpPlayer> ps = r.getPlayers();
        for (DpPlayer p : ps) {
            p.setFold(false);
            p.setBet(0);
            p.setBlind(0);
            p.setActed(false);
            p.setTotalBet(0);
            p.setAllIn(false);
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
            ps.get(sb).setTotalBet(5);      // 记入累计下注

            ps.get(bb).setBlind(2);
            ps.get(bb).setChips(ps.get(bb).getChips() - 10);
            ps.get(bb).setBet(10);
            ps.get(bb).setTotalBet(10);     // 记入累计下注

            r.setCurrentBetToCall(10);
            r.setPot(15);                    // 大小盲计入底池
        }

        r.setCurrentActorIndex((did + 3) % ps.size());
        r.setLastActionTime(System.currentTimeMillis());
        return true;
    }

    // ========== 下注 / 弃牌 ==========

    public boolean bet(String roomId, String nickname, int amount) {
        DpRoom r = roomMap.get(roomId);
        if (r == null || !r.isPlaying()) return false;

        DpPlayer p = r.getPlayers().stream()
                .filter(pl -> pl.getNickname().equals(nickname))
                .findFirst().orElse(null);
        if (p == null || p.isFold() || p.isAllIn()) return false;

        // 如果下注金额 >= 玩家剩余筹码，视为 all-in
        if (amount >= p.getChips()) {
            amount = p.getChips();
            p.setAllIn(true);
        }

        int totalBet = p.getBet() + amount;
        if (totalBet > r.getCurrentBetToCall()) {
            r.setCurrentBetToCall(totalBet);
        }

        p.setChips(p.getChips() - amount);
        p.setBet(totalBet);
        p.setTotalBet(p.getTotalBet() + amount);
        p.setActed(true);
        r.setPot(r.getPot() + amount);

        moveToNextValidActor(r);
        return true;
    }

    public boolean fold(String roomId, String nickname) {
        DpRoom r = roomMap.get(roomId);
        if (r == null || !r.isPlaying() || r.getCurrentActorIndex() < 0) return false;

        DpPlayer p = r.getPlayers().get(r.getCurrentActorIndex());
        if (!p.getNickname().equals(nickname) || p.isFold()) return false;

        p.setFold(true);
        moveToNextValidActor(r);  // 统一用新方法，不再用旧的 nextActor
        return true;
    }

    // ========== 阶段推进 ==========

    public boolean nextStage(String roomId) {
        DpRoom r = roomMap.get(roomId);
        if (r == null || !r.isPlaying() || r.getCurrentActorIndex() >= 0) return false;

        // 清理本轮投注数据（不清 totalBet 和 allIn）
        for (DpPlayer p : r.getPlayers()) {
            p.setBet(0);
            p.setActed(false);
        }
        r.setCurrentBetToCall(0);

        List<String> deck = r.getDeck();
        switch (r.getCurrentStage()) {
            case "preflop":
                r.getCommunityCards().add(deck.remove(0));
                r.getCommunityCards().add(deck.remove(0));
                r.getCommunityCards().add(deck.remove(0));
                r.setCurrentStage("flop");
                break;
            case "flop":
                r.getCommunityCards().add(deck.remove(0));
                r.setCurrentStage("turn");
                break;
            case "turn":
                r.getCommunityCards().add(deck.remove(0));
                r.setCurrentStage("river");
                break;
            case "river":
                r.setCurrentStage("showdown");
                calculatePots(r);             // 进入摊牌时计算主池/边池
                r.setCurrentActorIndex(-1);
                return true;
            default:
                return false;
        }

        // 非 showdown 阶段，找第一个可行动的人（跳过弃牌和 all-in）
        List<DpPlayer> ps = r.getPlayers();
        for (int i = 0; i < ps.size(); i++) {
            DpPlayer p = ps.get(i);
            if (!p.isFold() && !p.isAllIn()) {
                r.setCurrentActorIndex(i);
                r.setLastActionTime(System.currentTimeMillis());
                return true;
            }
        }

        // 所有人都 all-in 或弃牌了，没人能行动，直接标记本轮结束
        r.setCurrentActorIndex(-1);
        return true;
    }

    // ========== 结算（按池分配） ==========

    /**
     * 按池结算
     * @param potWinners 格式 "0:Alice;1:Bob,Charlie"  表示第0个池给Alice，第1个池平分给Bob和Charlie
     */
    public boolean judgeWin(String roomId, String potWinners) {
        DpRoom r = roomMap.get(roomId);
        if (r == null || !r.isPlaying() || !"showdown".equals(r.getCurrentStage())) return false;

        if (r.getPots() == null || r.getPots().isEmpty()) {
            // 如果没有边池数据（兼容旧逻辑），把整个 pot 当主池处理
            // potWinners 格式退化为 "0:Alice,Bob"
            String[] winners = potWinners.contains(":") ?
                    potWinners.split(":")[1].split(",") :
                    potWinners.split(",");

            int share = r.getPot() / winners.length;
            int remainder = r.getPot() % winners.length;

            for (String name : winners) {
                for (DpPlayer p : r.getPlayers()) {
                    if (p.getNickname().equals(name.trim())) {
                        p.setChips(p.getChips() + share);
                        if (remainder > 0) {
                            p.setChips(p.getChips() + remainder);
                            remainder = 0;
                        }
                        break;
                    }
                }
            }
        } else {
            // 按池分配
            String[] parts = potWinners.split(";");
            for (String part : parts) {
                String[] kv = part.split(":");
                if (kv.length < 2) continue;

                int potIdx = Integer.parseInt(kv[0].trim());
                String[] winners = kv[1].split(",");

                if (potIdx >= r.getPots().size()) continue;
                DpPot pot = r.getPots().get(potIdx);
                int share = pot.getAmount() / winners.length;
                int remainder = pot.getAmount() % winners.length;

                for (String w : winners) {
                    String name = w.trim();
                    for (DpPlayer p : r.getPlayers()) {
                        if (p.getNickname().equals(name)) {
                            p.setChips(p.getChips() + share);
                            if (remainder > 0) {
                                p.setChips(p.getChips() + remainder);
                                remainder = 0;
                            }
                            break;
                        }
                    }
                }
            }
        }

        r.setPot(0);
        r.setPots(new ArrayList<>());
        r.setCurrentStage("settled");  // 设为已结算，等房主点"重新发牌"
        return true;
    }

    // ========== 心跳 ==========

    public void heartbeat(String roomId, String nickname) {
        DpRoom r = roomMap.get(roomId);
        if (r == null) return;
        for (DpPlayer p : r.getPlayers()) {
            if (p.getNickname().equals(nickname)) {
                p.setLastHeartBeat(System.currentTimeMillis());
            }
        }
    }
}
