package com.example.mgdemoplus.service.studentImpl;

import com.example.mgdemoplus.dto.DpRoomDTO;
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
                        if (System.currentTimeMillis() - p.getLastHeartBeat() > DpRoom.getHeartTimeout()) {
                            System.out.println("未收到"+p.getNickname()+"的心跳,已移除房间");
                            it.remove();
                        }
                    }
                    if (room.getPlayers().isEmpty()) {
                        roomMap.remove(room.getRoomId());//房间空了就清人
                        continue;
                    }
                    // 30秒超时弃牌，距离上一个最后一个人行动后超过30秒不动弹则设置为弃牌
                    if (room.isPlaying() && room.getCurrentActorIndex() >= 0) {
                        if (System.currentTimeMillis() - room.getLastActionTime() > DpRoom.getActionTimeout()) {
                            DpPlayer p = room.getPlayers().get(room.getCurrentActorIndex());
                            p.setFold(true);
                            moveToNextValidActor(room);  // 统一用新方法
                            autoAdvanceIfRoundFinished(room); // 如有需要自动推进下一阶段
                        }
                    }

                    // 结算后准备阶段：超时踢未准备玩家到观众席，并自动开下一局
                    if (room.isPlaying()
                            && "settled".equals(room.getCurrentStage())
                            && room.getReadyDeadline() > 0
                            && System.currentTimeMillis() > room.getReadyDeadline()) {
                        handleReadyTimeout(room);
                    }
                }
            }
        }, 0, 2000);
    }

    // ========== 工具方法 ==========

    private List<String> newDeck() {
        List<String> deck = new ArrayList<>();
        String[] suits = {"hearts", "diamonds", "clubs", "spades"};
        String[] ranks = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
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
        String id = UUID.randomUUID().toString().substring(0, 8);//随机生成的id?
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

    public DpRoom getAllRooms(String roomId) {
        return roomMap.get(roomId);
    }

    public String joinRoom(String roomId, String nickname) {
        DpRoom r = roomMap.get(roomId);
        if (r == null) return "房间不存在";
        if (r.isPlaying()) {
            // 游戏中途进来：作为观众进入，记录到观众席名单
            List<String> spectators = r.getSpectators();
            if (spectators == null) {
                spectators = new ArrayList<>();
                r.setSpectators(spectators);
            }
            if (!spectators.contains(nickname)) {
                spectators.add(nickname);
            }
            return "游戏已开始";
        }
        for (DpPlayer p : r.getPlayers()) {
            // 已经在房间中的活跃玩家不允许重复加入；
            // 如果是上一手已经标记离开的玩家(leftThisHand)，视为不在房间内，可以重新加入
            if (p.getNickname().equals(nickname) && !p.isLeftThisHand()) return "你已在房间中";
        }
        DpPlayer p = new DpPlayer();
        p.setNickname(nickname);
        r.getPlayers().add(p);
        return "ok";
    }

    /**
     * 观战期间，标记“下一局加入”
     * 当前局没有作为玩家参与的人，可以通过该接口在下一局开局时被加入 players
     */
    public boolean readyNextHand(String roomId, String nickname) {
        DpRoom r = roomMap.get(roomId);
        if (r == null) return false;

        // 已经在桌上的玩家，不需要再标记下一局，直接视为成功
        for (DpPlayer p : r.getPlayers()) {
            // 已经在当前手牌里的活跃玩家（未标记离开）不需要再报名下一局；
            // 对于本手已离开的“僵尸位”（leftThisHand = true），依然允许作为观众报名下一局
            if (p.getNickname().equals(nickname) && !p.isLeftThisHand()) {
                return true;
            }
        }

        List<String> waiters = r.getWaitNextHand();
        if (waiters == null) {
            waiters = new ArrayList<>();
            r.setWaitNextHand(waiters);
        }
        if (!waiters.contains(nickname)) {
            waiters.add(nickname);
        }
        return true;
    }

    public boolean toggleReady(String roomId, String nickname) {
        DpRoom r = roomMap.get(roomId);
        if (r == null) return false;

        // 1. 首次开局前的准备：房间未开始
        if (!r.isPlaying()) {
            for (DpPlayer p : r.getPlayers()) {
                if (p.getNickname().equals(nickname)) {
                    p.setReady(!p.isReady());
                    return true;
                }
            }
            return false;
        }

        // 2. 结算后的下一局准备：当前处于 settled 阶段
        if (r.isPlaying() && "settled".equals(r.getCurrentStage())) {
            for (DpPlayer p : r.getPlayers()) {
                if (p.getNickname().equals(nickname)) {
                    // 筹码不足以支付一手牌的大盲（10）时不能准备，需先补码
                    if (p.getChips() < 10) {
                        return false;
                    }
                    p.setReady(!p.isReady());
                    // 每次有人切换准备状态后，检查是否可以立刻开下一局
                    checkAndStartNextHandAfterSettle(r);
                    return true;
                }
            }
            return false;
        }

        // 其它阶段不允许切换准备
        for (DpPlayer p : r.getPlayers()) {
            if (p.getNickname().equals(nickname)) {
                return false;
            }
        }
        return false;
    }

    public boolean exitRoom(String roomId, String nickname) {
        DpRoom r = roomMap.get(roomId);
        if (r == null) return false;
        // 先从观众席中移除这个人（无论是否在牌桌上）
        List<String> spectators = r.getSpectators();
        if (spectators != null) {
            spectators.remove(nickname);
        }

        // 如果当前不在对局中（还没开始或本手已结束），直接从玩家列表移除
        if (!r.isPlaying()) {
            // 如果房主不在了（被踢了或主动走了），顺位继承
            if (r.getOwner().equals(nickname)) {
                if (r.getPlayers().size() > 1) {
                    for (DpPlayer candidate : r.getPlayers()) {
                        if (!candidate.getNickname().equals(nickname)) {
                            r.setOwner(candidate.getNickname());
                            System.out.println("房间 " + r.getRoomId() + " 房主易位给: " + candidate.getNickname());
                            break;
                        }
                    }
                } else {
                    // 没人了，准备销毁房间
                    roomMap.remove(r.getRoomId());
                    return true;
                }
            }
            return r.getPlayers().removeIf(p -> p.getNickname().equals(nickname));
        }

        // ===== 正在对局中：本手牌内不直接删人，而是视为弃牌 + 标记本手结束后清理 =====

        // 房主离开时，移交给仍在桌上的其他任意一名玩家
        if (r.getOwner().equals(nickname)) {
            for (DpPlayer candidate : r.getPlayers()) {
                if (!candidate.getNickname().equals(nickname)) {
                    r.setOwner(candidate.getNickname());
                    System.out.println("房间 " + r.getRoomId() + " 房主易位给: " + candidate.getNickname());
                    break;
                }
            }
        }

        DpPlayer target = null;
        int idx = -1;
        List<DpPlayer> ps = r.getPlayers();
        for (int i = 0; i < ps.size(); i++) {
            DpPlayer p = ps.get(i);
            if (p.getNickname().equals(nickname)) {
                target = p;
                idx = i;
                break;
            }
        }
        if (target == null) {
            // 不在牌桌上，仅作为观众离开
            return true;
        }

        // 标记为本手已离开：这一手后续不再参与，但座位保留到本手结束
        target.setLeftThisHand(true);

        // 如果当前正轮到他行动，等价于自动弃牌，再推进后续流程
        if (r.getCurrentActorIndex() == idx) {
            // fold 会将该玩家标记为弃牌并轮到下一个人
            fold(roomId, nickname);
        } else {
            // 非当前行动玩家，直接视为弃牌
            if (!target.isFold()) {
                target.setFold(true);
            }
        }

        return true;
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
        r.setReadyDeadline(0L);
        for (DpPlayer p : r.getPlayers()) {
            p.setChips(DpRoom.getChips());
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

        // 把上一局观战并标记“下一局加入”的玩家，加入到本局的玩家列表中
        List<String> waiters = r.getWaitNextHand();
        if (waiters != null && !waiters.isEmpty()) {
            List<String> spectators = r.getSpectators();
            for (String name : waiters) {
                boolean exists = false;
                for (DpPlayer existing : r.getPlayers()) {
                    if (existing.getNickname().equals(name)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    DpPlayer np = new DpPlayer();
                    np.setNickname(name);
                    // 新加入的玩家带着默认筹码参与新一局
                    np.setChips(DpRoom.getChips());
                    r.getPlayers().add(np);
                }
                // 这些人已经回到牌桌，不再属于观众席
                if (spectators != null) {
                    spectators.remove(name);
                }
            }
            waiters.clear();
        }

        r.setDeck(newDeck());
        r.setCommunityCards(new ArrayList<>());
        r.setCurrentStage("preflop");
        r.setPot(0);
        r.setPots(new ArrayList<>());
        r.setCurrentBetToCall(0);
        r.setReadyDeadline(0L);

        List<DpPlayer> ps = r.getPlayers();
        for (DpPlayer p : ps) {
            p.setFold(false);
            p.setBet(0);
            p.setBlind(0);
            p.setActed(false);
            p.setTotalBet(0);
            p.setAllIn(false);
            p.setLeftThisHand(false);
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
        autoAdvanceIfRoundFinished(r);
        return true;
    }

    public boolean fold(String roomId, String nickname) {
        DpRoom r = roomMap.get(roomId);
        if (r == null || !r.isPlaying() || r.getCurrentActorIndex() < 0) return false;

        DpPlayer p = r.getPlayers().get(r.getCurrentActorIndex());
        if (!p.getNickname().equals(nickname) || p.isFold()) return false;

        p.setFold(true);
        moveToNextValidActor(r);  // 统一用新方法，不再用旧的 nextActor
        autoAdvanceIfRoundFinished(r);
        return true;
    }

    // ========== 阶段推进 ==========

    /**
     * 外部调用：房主手动点击“下一阶段”
     */
    public boolean nextStage(String roomId) {
        DpRoom r = roomMap.get(roomId);
        if (r == null || !r.isPlaying() || r.getCurrentActorIndex() >= 0) return false;
        return advanceStage(r);
    }

    /**
     * 内部通用：在一轮下注结束后推进阶段（翻牌/转牌/河牌/摊牌）
     */
    private boolean advanceStage(DpRoom r) {
        if (r == null || !r.isPlaying()) return false;

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
                // 摊牌后立即自动结算
                autoSettle(r);
                return true;
            default:
                return false;
        }

        // 非 showdown 阶段，找第一个可行动的人（跳过弃牌和 all-in）
        int newIndex = findFirstActorAfterDealer(r);
        if (newIndex >= 0) {
            r.setCurrentActorIndex(newIndex);
            r.setLastActionTime(System.currentTimeMillis());
            return true;
        }

// 所有人都 all-in 或弃牌了，没人能行动，直接标记本轮结束
        r.setCurrentActorIndex(-1);
        return true;
    }

    /**
     * 自动推进阶段：当本轮所有可行动玩家都行动完毕 (currentActorIndex == -1) 时，
     * 自动从 preflop/flop/turn/river 往后走，直到出现新的行动者或进入 showdown。
     */
    private void autoAdvanceIfRoundFinished(DpRoom r) {
        if (r == null || !r.isPlaying()) return;
        // 只有当当前轮已经没有行动者时才考虑推进
        if (r.getCurrentActorIndex() >= 0) return;

        // 连续推进，处理“所有人都 all-in”的情况
        while (true) {
            if (!advanceStage(r)) break;
            // 自动结算逻辑在 advanceStage 中处理，结算后阶段会切到 settled
            if ("settled".equals(r.getCurrentStage())) break;
            // 如果出现了新的行动者（还有人可以下注），也停止，等待玩家操作
            if (r.getCurrentActorIndex() >= 0) break;
        }
    }

    // ========== 结算（按池分配） ==========

    /**
     * 手牌强度，用于比较大小：先比牌型，再比 5 张牌的从大到小点数。
     */
    private static class HandStrength implements Comparable<HandStrength> {
        private final int rankCategory;   // 10 皇家同花顺, 9 同花顺, ..., 1 高牌
        private final List<Integer> ranks; // 从高到低的点数列表，用于打平

        HandStrength(int rankCategory, List<Integer> ranks) {
            this.rankCategory = rankCategory;
            this.ranks = ranks;
        }

        @Override
        public int compareTo(HandStrength o) {
            if (this.rankCategory != o.rankCategory) {
                return Integer.compare(this.rankCategory, o.rankCategory);
            }
            int size = Math.min(this.ranks.size(), o.ranks.size());
            for (int i = 0; i < size; i++) {
                int c = Integer.compare(this.ranks.get(i), o.ranks.get(i));
                if (c != 0) return c;
            }
            return 0;
        }
    }

    /**
     * 从 7 张牌中选出最佳 5 张组合并评估强度。
     */
    private HandStrength evaluateBestHand(List<String> cards) {
        List<int[]> parsed = new ArrayList<>();
        Map<String, Integer> rankMap = new HashMap<>();
        rankMap.put("2", 2);
        rankMap.put("3", 3);
        rankMap.put("4", 4);
        rankMap.put("5", 5);
        rankMap.put("6", 6);
        rankMap.put("7", 7);
        rankMap.put("8", 8);
        rankMap.put("9", 9);
        rankMap.put("10", 10);
        rankMap.put("J", 11);
        rankMap.put("Q", 12);
        rankMap.put("K", 13);
        rankMap.put("A", 14);

        for (String c : cards) {
            if (c == null) continue;
            String[] parts = c.split("_");
            if (parts.length != 2) continue;
            String suit = parts[0];
            String rankStr = parts[1];
            Integer rank = rankMap.get(rankStr);
            if (rank == null) continue;
            int suitCode;
            switch (suit) {
                case "hearts":
                    suitCode = 0;
                    break;
                case "diamonds":
                    suitCode = 1;
                    break;
                case "clubs":
                    suitCode = 2;
                    break;
                case "spades":
                    suitCode = 3;
                    break;
                default:
                    suitCode = 4;
            }
            parsed.add(new int[]{rank, suitCode});
        }

        if (parsed.size() < 5) {
            return new HandStrength(1, Collections.singletonList(0));
        }

        // 生成所有 5 张组合
        int n = parsed.size();
        HandStrength best = null;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                for (int k = j + 1; k < n; k++) {
                    for (int a = k + 1; a < n; a++) {
                        for (int b = a + 1; b < n; b++) {
                            List<int[]> hand = new ArrayList<>();
                            hand.add(parsed.get(i));
                            hand.add(parsed.get(j));
                            hand.add(parsed.get(k));
                            hand.add(parsed.get(a));
                            hand.add(parsed.get(b));
                            HandStrength hs = evaluateFiveCardHand(hand);
                            if (best == null || hs.compareTo(best) > 0) {
                                best = hs;
                            }
                        }
                    }
                }
            }
        }

        return best == null ? new HandStrength(1, Collections.singletonList(0)) : best;
    }

    /**
     * 单独评估 5 张牌的牌型和强度。
     */
    private HandStrength evaluateFiveCardHand(List<int[]> hand) {
        hand.sort((a, b) -> Integer.compare(b[0], a[0]));
        int[] ranks = new int[5];
        int[] suits = new int[5];
        for (int i = 0; i < 5; i++) {
            ranks[i] = hand.get(i)[0];
            suits[i] = hand.get(i)[1];
        }

        boolean isFlush = true;
        for (int i = 1; i < 5; i++) {
            if (suits[i] != suits[0]) {
                isFlush = false;
                break;
            }
        }

        boolean isStraight = false;
        int[] distinct = Arrays.stream(ranks).distinct().toArray();
        if (distinct.length == 5 && ranks[0] - ranks[4] == 4) {
            isStraight = true;
        }
        // A-5 小顺子
        if (ranks[0] == 14 && ranks[1] == 5 && ranks[2] == 4 && ranks[3] == 3 && ranks[4] == 2) {
            isStraight = true;
            ranks = new int[]{5, 4, 3, 2, 1};
        }

        Map<Integer, Integer> countMap = new HashMap<>();
        for (int r : ranks) {
            countMap.put(r, countMap.getOrDefault(r, 0) + 1);
        }

        List<Map.Entry<Integer, Integer>> entries = new ArrayList<>(countMap.entrySet());
        entries.sort((e1, e2) -> {
            int c = Integer.compare(e2.getValue(), e1.getValue());
            if (c != 0) return c;
            return Integer.compare(e2.getKey(), e1.getKey());
        });

        int firstCount = entries.get(0).getValue();
        int firstRank = entries.get(0).getKey();
        int secondCount = entries.size() > 1 ? entries.get(1).getValue() : 0;
        int secondRank = entries.size() > 1 ? entries.get(1).getKey() : 0;

        // 皇家同花顺 / 同花顺
        if (isFlush && isStraight) {
            if (ranks[0] == 14 && ranks[1] == 13) {
                return new HandStrength(10, Arrays.asList(14, 13, 12, 11, 10));
            }
            return new HandStrength(9, Arrays.stream(ranks).boxed().collect(Collectors.toList()));
        }

        // 四条
        if (firstCount == 4) {
            List<Integer> kickers = new ArrayList<>();
            kickers.add(firstRank);
            for (int r : ranks) {
                if (r != firstRank) kickers.add(r);
            }
            return new HandStrength(8, kickers);
        }

        // 葫芦
        if (firstCount == 3 && secondCount == 2) {
            return new HandStrength(7, Arrays.asList(firstRank, secondRank));
        }

        // 同花
        if (isFlush) {
            return new HandStrength(6, Arrays.stream(ranks).boxed().collect(Collectors.toList()));
        }

        // 顺子
        if (isStraight) {
            return new HandStrength(5, Arrays.stream(ranks).boxed().collect(Collectors.toList()));
        }

        // 三条
        if (firstCount == 3) {
            List<Integer> kickers = new ArrayList<>();
            kickers.add(firstRank);
            for (int r : ranks) {
                if (r != firstRank) kickers.add(r);
            }
            return new HandStrength(4, kickers);
        }

        // 两对
        if (firstCount == 2 && secondCount == 2) {
            int kicker = 0;
            for (int r : ranks) {
                if (r != firstRank && r != secondRank) {
                    kicker = r;
                    break;
                }
            }
            List<Integer> list = new ArrayList<>();
            list.add(Math.max(firstRank, secondRank));
            list.add(Math.min(firstRank, secondRank));
            list.add(kicker);
            return new HandStrength(3, list);
        }

        // 一对
        if (firstCount == 2) {
            List<Integer> kickers = new ArrayList<>();
            kickers.add(firstRank);
            for (int r : ranks) {
                if (r != firstRank) kickers.add(r);
            }
            return new HandStrength(2, kickers);
        }

        // 高牌
        return new HandStrength(1, Arrays.stream(ranks).boxed().collect(Collectors.toList()));
    }

    /**
     * 自动结算当前房间：为每个池按牌力选出赢家并分配筹码，然后进入结算完成/准备下一局阶段。
     */
    private void autoSettle(DpRoom r) {
        if (r == null || !r.isPlaying()) return;

        // 没有任何下注，直接标记为结算完成
        if (r.getPot() <= 0 && (r.getPots() == null || r.getPots().isEmpty())) {
            r.setCurrentStage("settled");
            r.setReadyDeadline(System.currentTimeMillis() + 30_000L);
            for (DpPlayer p : r.getPlayers()) {
                p.setReady(false);
            }
            return;
        }

        // 先确保已经有主池/边池数据
        if (r.getPots() == null || r.getPots().isEmpty()) {
            // 兼容：如果还没算过 pots，就根据总 pot 和没弃牌玩家算一个主池
            DpPot pot = new DpPot();
            pot.setAmount(r.getPot());
            List<String> eligible = r.getPlayers().stream()
                    .filter(p -> !p.isFold())
                    .map(DpPlayer::getNickname)
                    .collect(Collectors.toList());
            pot.setEligiblePlayers(eligible);
            List<DpPot> list = new ArrayList<>();
            list.add(pot);
            r.setPots(list);
        }

        // 预先解析每个玩家的 7 张牌，评估牌力
        Map<String, HandStrength> strengthMap = new HashMap<>();
        for (DpPlayer p : r.getPlayers()) {
            if (p.isFold()) continue;
            List<String> allCards = new ArrayList<>();
            if (p.getHoleCards() != null) {
                allCards.addAll(p.getHoleCards());
            }
            if (r.getCommunityCards() != null) {
                allCards.addAll(r.getCommunityCards());
            }
            if (allCards.size() < 5) continue;
            HandStrength hs = evaluateBestHand(allCards);
            strengthMap.put(p.getNickname(), hs);
        }

        // 按池分配：每个池在 eligiblePlayers 中找到牌力最高的一批人，平分该池
        for (DpPot pot : r.getPots()) {
            List<String> eligible = pot.getEligiblePlayers();
            if (eligible == null || eligible.isEmpty()) continue;

            HandStrength best = null;
            List<DpPlayer> winners = new ArrayList<>();

            for (String name : eligible) {
                HandStrength hs = strengthMap.get(name);
                if (hs == null) continue;
                if (best == null || hs.compareTo(best) > 0) {
                    best = hs;
                    winners.clear();
                    DpPlayer winnerPlayer = r.getPlayers().stream()
                            .filter(p -> p.getNickname().equals(name))
                            .findFirst().orElse(null);
                    if (winnerPlayer != null) {
                        winners.add(winnerPlayer);
                    }
                } else if (hs.compareTo(best) == 0) {
                    DpPlayer winnerPlayer = r.getPlayers().stream()
                            .filter(p -> p.getNickname().equals(name))
                            .findFirst().orElse(null);
                    if (winnerPlayer != null) {
                        winners.add(winnerPlayer);
                    }
                }
            }

            if (winners.isEmpty()) continue;

            int share = pot.getAmount() / winners.size();
            int remainder = pot.getAmount() % winners.size();
            for (DpPlayer w : winners) {
                w.setChips(w.getChips() + share);
                if (remainder > 0) {
                    w.setChips(w.getChips() + 1);
                    remainder -= 1;
                }
            }
        }

        r.setPot(0);
        r.setPots(new ArrayList<>());

        // 结算完成后，先清理本手中已离开的“僵尸位”玩家
        List<DpPlayer> currentPlayers = r.getPlayers();
        if (currentPlayers != null && !currentPlayers.isEmpty()) {
            currentPlayers.removeIf(DpPlayer::isLeftThisHand);
        }

        // 进入“结算完成，等待准备下一局”阶段，并开始 30 秒准备倒计时
        r.setCurrentStage("settled");
        r.setReadyDeadline(System.currentTimeMillis() + 30_000L);
        for (DpPlayer p : r.getPlayers()) {
            p.setReady(false);
        }
    }

    /**
     * 结算后准备阶段到时间：未准备的玩家被踢到观众席，准备好的玩家自动开下一局。
     */
    private void handleReadyTimeout(DpRoom r) {
        if (r == null || !r.isPlaying()) return;
        if (!"settled".equals(r.getCurrentStage())) return;

        List<DpPlayer> players = r.getPlayers();
        if (players == null || players.isEmpty()) {
            r.setPlaying(false);
            r.setReadyDeadline(0L);
            return;
        }

        List<DpPlayer> remain = new ArrayList<>();
        List<String> kicked = new ArrayList<>();
        for (DpPlayer p : players) {
            if (p.isReady()) {
                remain.add(p);
            } else {
                kicked.add(p.getNickname());
            }
        }

        // 把未准备的人移到观众席
        List<String> spectators = r.getSpectators();
        if (spectators == null) {
            spectators = new ArrayList<>();
            r.setSpectators(spectators);
        }
        for (String name : kicked) {
            if (!spectators.contains(name)) {
                spectators.add(name);
            }
        }

        r.setPlayers(remain);
        r.setReadyDeadline(0L);

        // 计算下一手理论上的人数：当前已准备的玩家 + 报名下一手的观众(waitNextHand)
        int nextCount = remain.size();
        List<String> waiters = r.getWaitNextHand();
        if (waiters != null) {
            nextCount += waiters.size();
        }

        // 如果下一手总人数仍不足 2 个，就结束对局，回到未开始状态
        if (nextCount < 2) {
            r.setPlaying(false);
            r.setCurrentStage("preflop");
            r.setCommunityCards(new ArrayList<>());
            r.setDeck(new ArrayList<>());
            r.setPot(0);
            r.setCurrentBetToCall(0);
            r.setPots(new ArrayList<>());
            r.setCurrentActorIndex(-1);
            return;
        }

        // 下一手有足够玩家（当前准备好的 + 报名的观众），自动开新一局
        newHand(r.getRoomId());
    }

    /**
     * 在 settled 阶段，每次有人切换准备状态后检查：
     * - 如果还在玩牌
     * - 且玩家数 >= 2
     * - 且所有还在牌桌上的玩家都已 ready
     * 则立刻开下一局，不用等 30 秒超时。
     */
    private void checkAndStartNextHandAfterSettle(DpRoom r) {
        if (r == null || !r.isPlaying()) return;
        if (!"settled".equals(r.getCurrentStage())) return;

        List<DpPlayer> ps = r.getPlayers();
        if (ps == null || ps.isEmpty()) return;

        boolean hasParticipatingPlayer = false;
        for (DpPlayer p : ps) {
            // 筹码大于 0 的玩家视为“在场要参与的人”，必须准备；
            // 筹码为 0 的玩家默认视为本手不参与（可以选择补码或观战），不阻塞开局。
            if (p.getChips() > 0) {
                hasParticipatingPlayer = true;
                if (!p.isReady()) {
                    return;
                }
            }
        }
        if (!hasParticipatingPlayer) return;

        // 所有在场参与玩家都已准备，立即开下一局
        r.setReadyDeadline(0L);
        newHand(r.getRoomId());
    }

    /**
     * 自动按池结算：根据实际牌力为每个池选出赢家。
     * 兼容旧接口调用，但不再使用前端传入的 potWinners 参数。
     */
    public boolean judgeWin(String roomId, String potWinners) {
        DpRoom r = roomMap.get(roomId);
        if (r == null || !r.isPlaying()) return false;
        autoSettle(r);
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

    /**
     * 结算后筹码不足大盲（10）的玩家补码到初始筹码。
     */
    public boolean rebuy(String roomId, String nickname) {
        DpRoom r = roomMap.get(roomId);
        if (r == null || !r.isPlaying()) return false;
        if (!"settled".equals(r.getCurrentStage())) return false;
        for (DpPlayer p : r.getPlayers()) {
            if (p.getNickname().equals(nickname)) {
                // 筹码充足（>=10）则不允许补码
                if (p.getChips() >= 10) {
                    return false;
                }
                p.setChips(DpRoom.getChips());
                return true;
            }
        }
        return false;
    }
    /**
     * 找到翻后每圈的第一个行动者：
     * 从庄家位 D 左边第一个玩家开始，跳过已弃牌 / 已 all-in 的玩家。
     * 若没有庄家标记或没人能行动，则返回 -1。
     */
    private int findFirstActorAfterDealer(DpRoom r) {
        List<DpPlayer> ps = r.getPlayers();
        if (ps == null || ps.isEmpty()) {
            return -1;
        }

        // 找到当前庄家下标
        int dealerIdx = -1;
        for (int i = 0; i < ps.size(); i++) {
            if (ps.get(i).isDealer()) {
                dealerIdx = i;
                break;
            }
        }
        if (dealerIdx < 0) {
            // 理论上不该发生：没有 D，就让调用方自己处理
            return -1;
        }

        // 从 D 的下家开始循环一圈，寻找第一个可行动玩家
        for (int step = 1; step <= ps.size(); step++) {
            int idx = (dealerIdx + step) % ps.size();
            DpPlayer p = ps.get(idx);
            if (!p.isFold() && !p.isAllIn()) {
                return idx;
            }
        }

        // 全部人都弃牌或 all-in 时，没有人需要行动
        return -1;
    }
    public List<DpRoomDTO> getAllRooms2() {
        return roomMap.values().stream()
                .map(room -> {
                    DpRoomDTO dto = new DpRoomDTO();
                    dto.setRoomId(room.getRoomId());
                    dto.setOwner(room.getOwner());
                    // 房间展示人数只统计“非僵尸位”的真实玩家，避免 leftThisHand 的假人占位
                    int aliveSize = (int) room.getPlayers().stream()
                            .filter(p -> !p.isLeftThisHand())
                            .count();
                    dto.setPlayerSize(aliveSize);
                    return dto;
                })
                .collect(Collectors.toList());
    }
}
