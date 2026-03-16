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
                        // 本手已离线的“占位”玩家不因心跳踢出，留到结算时再移除，以保持行动顺序
                        if (p.isLeftThisHand()) continue;
                        if (System.currentTimeMillis() - p.getLastHeartBeat() > DpRoom.getHeartTimeout()) {
                            if (p.getNickname().equals(room.getOwner())) {
                                giveOwner(room.getRoomId(), p.getNickname());
                            }
                            System.out.println("未收到" + p.getNickname() + "的心跳,已移除房间");
                            it.remove();
                        }
                    }
                    int size = 0;//检测活人逻辑
                    for (DpPlayer kickPlayer : room.getPlayers()) {
                        if (!kickPlayer.isLeftThisHand()) {
                            size += 1;
//                            System.out.println("定时器检测："+kickPlayer.getNickname()+"是活人");
                        }
                    }
                    if (size == 0) {
                        System.out.println("定时器检测：房间：" + room.getRoomId() + "没活人了");
                        roomMap.remove(room.getRoomId());//房间空了就清人
                        continue;
                    }
                    // 30秒超时弃牌；若当前行动位是已离线占位，直接跳过不等待
                    if (room.isPlaying() && room.getCurrentActorIndex() >= 0) {
                        DpPlayer p = room.getPlayers().get(room.getCurrentActorIndex());
                        if (p.isLeftThisHand()) {
                            moveToNextValidActor(room);
                            autoAdvanceIfRoundFinished(room);
                        } else if (System.currentTimeMillis() - room.getLastActionTime() > DpRoom.getActionTimeout()) {
                            p.setFold(true);
                            moveToNextValidActor(room);
                            autoAdvanceIfRoundFinished(room);
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
     * 房主主动移交房主给房间内另一位玩家
     */
    public boolean transferOwner(String roomId, String fromNickname, String toNickname) {
        DpRoom r = roomMap.get(roomId);
        if (r == null) return false;
        // 只有当前房主可以发起移交
        if (!fromNickname.equals(r.getOwner())) return false;
        if (fromNickname.equals(toNickname)) return false;

        // 目标玩家必须在当前房间、且不是本手已离开的僵尸位
        boolean found = false;
        for (DpPlayer p : r.getPlayers()) {
            if (p.getNickname().equals(toNickname) && !p.isLeftThisHand()) {
                found = true;
                break;
            }
        }
        if (!found) return false;

        r.setOwner(toNickname);
        System.out.println("房间 " + r.getRoomId() + " 房主由 " + fromNickname + " 移交给: " + toNickname);
        return true;
    }

    // ========== 顺位移交房主操作 =========
    public void giveOwner(String roomId, String ownerNickname) {
        DpRoom room = roomMap.get(roomId);
        int size = 0;
        for (DpPlayer getActivePlayer : room.getPlayers()) {
            if (!getActivePlayer.isLeftThisHand()) {
                size += 1;//如果有活人就加一个
                System.out.println("退出按钮检测：" + getActivePlayer.getNickname() + "是活人");
            }
        }
        if (size == 0) {
            System.out.println("giveOwner：没有活人了");
            roomMap.remove(roomId);
        } else {
            for (DpPlayer candidate : room.getPlayers()) {//非本人也非僵尸
                if (!candidate.getNickname().equals(ownerNickname) && !candidate.isLeftThisHand()) {
                    room.setOwner(candidate.getNickname());
                    System.out.println("giveOwner：房间 " + room.getRoomId() + " 房主易位给: " + candidate.getNickname());
                    break;
                }
            }
        }
    }

    /**
     * 房主踢人至观众席
     *
     */
    public boolean kickPlayer(String roomId, String nickname) {
        DpRoom r = roomMap.get(roomId);
        int idx = -1;
        List<DpPlayer> ps = r.getPlayers();
        for (int i = 0; i < ps.size(); i++) {//找到这个人的下标返回
            DpPlayer p = ps.get(i);
            if (p.getNickname().equals(nickname)) {
                idx = i;
                break;
            }
        }
        if (idx != -1) {
            if (r.getCurrentActorIndex() == idx) {
                // fold 会将该玩家标记为弃牌并轮到下一个人
                fold(roomId, nickname);
            } else {
                // 非当前行动玩家，直接视为弃牌
                if (!ps.get(idx).isFold()) {
                    ps.get(idx).setFold(true);
                }
            }
            ps.get(idx).setReady(false);
            ps.get(idx).setLeftThisHand(true);
            List<String> spectators = getNewSpectators(r);
            if(!spectators.contains(nickname)){
                spectators.add(nickname);
            }
            return true;
        } else {
            return false;
        }


    }

    //======== 获取观众席防null版本 =========
    public List<String> getNewSpectators(DpRoom r) {
        List<String> spectators = r.getSpectators();
        if (spectators == null) {
            spectators = new ArrayList<>();
            return spectators;
        }
        return spectators;
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

            // 跳过已离线位；没弃牌、没全下、且（还没行动 或 下注不够）
            if (nextP.isLeftThisHand()) continue;
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

    // ========== 开局前：房间与准备 ==========

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

    /**
     * 房间列表摘要：用于大厅展示所有房间（房间号 / 房主 / 在线人数）
     */
    public List<DpRoomDTO> getAllRooms2() {
        return roomMap.values().stream()
                .map(room -> {
                    DpRoomDTO dto = new DpRoomDTO();
                    dto.setRoomId(room.getRoomId());
                    dto.setOwner(room.getOwner());
                    // 房间展示人数只统计“非僵尸位”的真实玩家
                    int aliveSize = (int) room.getPlayers().stream()
                            .filter(p -> !p.isLeftThisHand())
                            .count();
                    dto.setPlayerSize(aliveSize);
                    return dto;
                })
                .collect(Collectors.toList());
    }

    public DpRoom getAllRooms(String roomId) {
        DpRoom r = roomMap.get(roomId);
        if (r == null) return null;
        // 当公共牌不少于 3 张时，为每位有手牌的玩家计算并填充「最大牌型的 5 张牌」，供前端展示
        List<String> community = r.getCommunityCards();
        if (community != null && community.size() >= 3) {
            for (DpPlayer p : r.getPlayers()) {
                if (p.getHoleCards() != null && p.getHoleCards().size() >= 2) {
                    List<String> all = new ArrayList<>(p.getHoleCards());
                    all.addAll(community);
                    p.setBestHandCards(getBestHandCards(all));
                } else {
                    p.setBestHandCards(Collections.emptyList());
                }
            }
        } else {
            for (DpPlayer p : r.getPlayers()) {
                p.setBestHandCards(Collections.emptyList());
            }
        }
        return r;
    }


    public String joinRoom(String roomId, String nickname) {
        DpRoom r = roomMap.get(roomId);
        if (r == null) return "房间不存在";
        if (r.isPlaying()) {
            // 游戏中途进来：作为观众进入，记录到观众席名单
            // 为了保证“每次重新进入都是干净状态”，这里先确保不再保留上一轮的下一局预约状态
            //游戏已开局则获取等待者列表，如果有自己就抹除掉，这是防御进来之后准备，然后退出去再进的时候直接被拉入下一把，因为准备列表还有自己
            List<String> waiters = r.getWaitNextHand();
            if (waiters != null) {
                waiters.remove(nickname);
            }
            //没啥意义
            List<String> spectators = r.getSpectators();
            if (spectators == null) {
                spectators = new ArrayList<>();
                r.setSpectators(spectators);
            }
            //如果观众里没自己就给自己加进来
            if (!spectators.contains(nickname)) {
                spectators.add(nickname);
            }
            return "游戏已开始";
        }
        //存疑
//        for (DpPlayer p : r.getPlayers()) {
//            // 已经在房间中的活跃玩家不允许重复加入；
//            // 如果是上一手已经标记离开的玩家(leftThisHand)，视为不在房间内，可以重新加入
//            if (p.getNickname().equals(nickname) && !p.isLeftThisHand()) return "你已在房间中";
//        }
        //添加新的玩家
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
//        for (DpPlayer p : r.getPlayers()) {
//            // 已经在当前手牌里的活跃玩家（未标记离开）不需要再报名下一局；
//            // 对于本手已离开的“僵尸位”（leftThisHand = true），依然允许作为观众报名下一局
//            if (p.getNickname().equals(nickname) && !p.isLeftThisHand()) {
//                return true;
//            }
//        }

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

    private void checkAndStartNextHandAfterSettle(DpRoom r) {
        int size = 0;
        int size1 = 0;
        //算所有场上有能力准备下一场的人数
        for (DpPlayer player : r.getPlayers()) {
            if (player.getChips() >= 10) {
                size += 1;
            }
        }
        //算已经准备的人数
        for (DpPlayer player : r.getPlayers()) {
            if (player.isReady()) {
                size1 += 1;
            }
        }
        //如果有能力的人齐了就可以开始newHand了，拉人踢人开场
        if (size == size1) {
            newHand(r.getRoomId());
        }
    }

    public boolean exitRoom(String roomId, String nickname) {
        DpRoom r = roomMap.get(roomId);
        if (r == null) return false;
        // 先从观众席中移除这个人（无论是否在牌桌上）
        List<String> spectators = r.getSpectators();
        // 同时清理该玩家在“下一局预约列表”中的记录，避免下次进来时仍然是预约状态
        List<String> waiters = r.getWaitNextHand();
        if (spectators != null) {
            spectators.remove(nickname);
        }
        if (waiters != null) {
            waiters.remove(nickname);
        }

        // 如果当前不在对局中（还没开始或本手已结束），直接从玩家列表移除
        if (!r.isPlaying()) {
            // 如果房主不在了（被踢了或主动走了），顺位继承
            if (r.getOwner().equals(nickname)) {

                giveOwner(roomId, nickname);
            }
            return r.getPlayers().removeIf(p -> p.getNickname().equals(nickname));
        }

        // ===== 正在对局中：本手牌内不直接删人，而是视为弃牌 + 标记本手结束后清理 =====

        // 房主离开时，移交给仍在桌上的其他任意一名玩家
        if (r.getOwner().equals(nickname)) {
//            for (DpPlayer candidate : r.getPlayers()) {
//                if (!candidate.getNickname().equals(nickname)) {
//                    r.setOwner(candidate.getNickname());
//                    System.out.println("房间 " + r.getRoomId() + " 房主易位给: " + candidate.getNickname());
//                    break;
//                }
//            }
            giveOwner(roomId, nickname);
        }
//这一步看他在不在桌上，不在直接退，在的话要设置为弃牌，甚至如果该他行动了，也要继续推进进程
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

        // 标记为本手已离开：保留座位以维持庄家/行动顺序，但不再保留任何信息，仅作“该玩家已离线”占位
        target.setLeftThisHand(true);//神之一手，变成僵尸了
        target.setHoleCards(new ArrayList<>());  // 不保留手牌信息改
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


    // ========== 游戏开始与流程 ==========

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
//        for (DpPlayer p : r.getPlayers()) {
//            p.setChips(DpRoom.getChips());
//            p.setFold(false);
//            p.setBet(0);
//            p.setTotalBet(0);
//            p.setAllIn(false);
//            p.setActed(false);
//            p.setHoleCards(new ArrayList<>());
//            p.setDealer(false);
//            p.setBlind(0);
//        }
        r.getPlayers().get(0).setDealer(true);
        return newHand(roomId);
    }

    public boolean newHand(String roomId) {
        DpRoom r = roomMap.get(roomId);
        if (r == null || !r.isPlaying()) return false;

        // 把上一局观战并标记“下一局加入”的玩家，加入到本局的玩家列表中，并更新观众列表
// 检测是否有积分不足的玩家，清理到观众厅，都在getAllCanPlayer方法里
        r.setPlayers(getAllCanPlayer(r));

        r.setDeck(newDeck());
        r.setCommunityCards(new ArrayList<>());
        r.setCurrentStage("preflop");
        r.setPot(0);
        r.setPots(new ArrayList<>());
        r.setCurrentBetToCall(0);
        r.setReadyDeadline(0L);
        int did = 0;//庄家索引
        List<DpPlayer> ps = r.getPlayers();
        for (DpPlayer p : ps) {//这里需要及时重置状态
            p.setFold(false);
            p.setBet(0);
            p.setBlind(0);
            p.setActed(false);
            p.setTotalBet(0);
            p.setAllIn(false);
            p.setLeftThisHand(false);
            p.setHoleCards(Arrays.asList(r.getDeck().remove(0), r.getDeck().remove(0)));
        }
        //留着上一把遗留下来的按钮以便确认下一把的按钮
//找当前玩家里是庄家的，如果没有默认从0号位开始
        // 庄家轮动
        if (r.getLastDealerIndex() != 0) {//如果上局不是0，那就把did更新，如果是0，那按上把是0算
            did = r.getLastDealerIndex();
        }
        System.out.println("上局庄位索引" + did);
//        System.out.println("玩家是:" + r.getPlayers().get(did).getNickname());
        for (DpPlayer p : ps) p.setDealer(false);//更新一轮
        did = (did + 1) % ps.size();

        ps.get(did).setDealer(true);
        r.setLastDealerIndex(did);//把这局的庄家索引记录下来
        System.out.println("本局庄位" + did);
        System.out.println("玩家是:" + r.getPlayers().get(did).getNickname());
        // 大小盲，如果人数大于2才有大小盲位
        if (ps.size() >= 2) {
            int sb = (did + 1) % ps.size();
            int bb = (did + 2) % ps.size();

            ps.get(sb).setBlind(1);
            ps.get(sb).setChips(ps.get(sb).getChips() - DpRoom.getSBChips());
            ps.get(sb).setBet(DpRoom.getSBChips());
            ps.get(sb).setTotalBet(DpRoom.getSBChips());      // 记入累计下注

            ps.get(bb).setBlind(2);
            ps.get(bb).setChips(ps.get(bb).getChips() - DpRoom.getBBChips());
            ps.get(bb).setBet(DpRoom.getBBChips());
            ps.get(bb).setTotalBet(DpRoom.getBBChips());     // 记入累计下注

            r.setCurrentBetToCall(DpRoom.getBBChips());
            r.setPot(DpRoom.getBBChips() + DpRoom.getSBChips());                    // 大小盲计入底池
        }

        r.setCurrentActorIndex((did + 3) % ps.size());//翻前从大盲的下一个开始行动
        r.setLastActionTime(System.currentTimeMillis());//方便计时间用
        return true;
    }

    // =========== 检查所有可参与游戏的人 =========
    public List<DpPlayer> getAllCanPlayer(DpRoom r) {
        //防null
        List<String> spectators = getNewSpectators(r);
        r.setSpectators(spectators);
        //防筹码不足
        List<DpPlayer> canPlay = new ArrayList<>();
        for (DpPlayer p : r.getPlayers()) {
            if (p.getChips() < DpRoom.getBBChips()) {
                if (!spectators.contains(p.getNickname())) {
                    spectators.add(p.getNickname());
                }
            } else {
                canPlay.add(p);
            }
        }
        //拉取准备下一把的
        List<String> waiters = r.getWaitNextHand();
        if (waiters != null && !waiters.isEmpty()) {
            //这段意思说场上准备的人和观众席准备的人都会被加入waiters，如果场上在的就是exists不管，场上不在的就拉下来当新玩家，把观众厅的名字移除掉，最后清理掉等待者为下一把做准备
            for (String name : waiters) {
//
                DpPlayer np = new DpPlayer();
                np.setNickname(name);
                // 新加入的玩家带着默认筹码参与新一局
                np.setChips(DpRoom.getChips());
                np.setReady(true);
                canPlay.add(np);//准备下一把的新人加入

                // 这些人已经回到牌桌，不再属于观众席
                if (spectators != null) {//从观众席里拉下来了
                    spectators.remove(name);
                }
            }
            waiters.clear();//及时清理掉等待者列表
        }
        return canPlay;

    }
    // ========== 游戏进行中：下注 / 弃牌 ==========

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

        // 从 D 的下家开始循环一圈，寻找第一个可行动玩家（跳过已离线位）
        for (int step = 1; step <= ps.size(); step++) {
            int idx = (dealerIdx + step) % ps.size();
            DpPlayer p = ps.get(idx);
            if (p.isLeftThisHand()) continue;
            if (!p.isFold() && !p.isAllIn()) {
                return idx;
            }
        }

        // 全部人都弃牌或 all-in 时，没有人需要行动
        return -1;
    }

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

    // ========== 游戏进行中：阶段推进 ==========

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
        // 若场上只剩一人未弃牌，和“全员 all-in”一样：不设行动者，连推到摊牌并结算
        int stillInCount = countPlayersStillInHand(r);
        if (newIndex >= 0 && stillInCount <= 1) {
            r.setCurrentActorIndex(-1);
            return true;
        }
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
     * 统计本手牌中仍未弃牌且在局的玩家数量（未离开本手）。
     * 用于判断“只剩一人”时是否连推到摊牌。
     */
    private int countPlayersStillInHand(DpRoom r) {
        if (r == null || r.getPlayers() == null) return 0;
        int count = 0;
        for (DpPlayer p : r.getPlayers()) {
            if (!p.isLeftThisHand() && !p.isFold()) count++;
        }
        return count;
    }

    // ========== 游戏尾声：结算与下一局准备 ==========

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
                //settle阶段会自动把大家的准备状态设置为false
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
     * 从 7 张牌中选出构成最大牌型的那 5 张，返回其原始字符串列表（用于前端展示「最大牌型」的牌面）。
     */
    private List<String> getBestHandCards(List<String> cards) {
        List<int[]> parsed = new ArrayList<>();

        for (String c : cards) {
            if (c == null) continue;
            String[] parts = c.split("_");
            if (parts.length != 2) continue;
            String suit = parts[0];
            String rankStr = parts[1];
            Integer rank = CARD_RANK_MAP.get(rankStr);
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
            return Collections.emptyList();
        }

        int n = parsed.size();
        HandStrength best = null;
        List<String> bestCards = null;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                for (int k = j + 1; k < n; k++) {
                    for (int a = k + 1; a < n; a++) {
                        for (int b = a + 1; b < n; b++) {
                            List<int[]> hand = Arrays.asList(
                                    parsed.get(i), parsed.get(j), parsed.get(k), parsed.get(a), parsed.get(b)
                            );
                            HandStrength hs = evaluateFiveCardHand(hand);
                            if (best == null || hs.compareTo(best) > 0) {
                                best = hs;
                                bestCards = Arrays.asList(
                                        cards.get(i), cards.get(j), cards.get(k), cards.get(a), cards.get(b)
                                );
                            }
                        }
                    }
                }
            }
        }

        if (bestCards == null || best == null) {
            return Collections.emptyList();
        }
        return sortCardsForDisplay(new ArrayList<>(bestCards), best);
    }


    private static final Map<String, Integer> CARD_RANK_MAP = buildCardRankMap();

    private static Map<String, Integer> buildCardRankMap() {
        Map<String, Integer> m = new HashMap<>();
        m.put("2", 2);
        m.put("3", 3);
        m.put("4", 4);
        m.put("5", 5);
        m.put("6", 6);
        m.put("7", 7);
        m.put("8", 8);
        m.put("9", 9);
        m.put("10", 10);
        m.put("J", 11);
        m.put("Q", 12);
        m.put("K", 13);
        m.put("A", 14);
        return m;
    }

    /**
     * 从牌字符串 "suit_rank" 解析出点数（2~14，A=14）。
     */
    private static int getRankFromCard(String card) {
        if (card == null || !card.contains("_")) return 0;
        String rankStr = card.split("_", 2)[1];
        return CARD_RANK_MAP.getOrDefault(rankStr, 0);
    }

    /**
     * 按牌型将 5 张牌排成「易读顺序」：三条为 J J J A 2，两对为 A A K K 2，一对为 J J A K 2 等。
     */
    private List<String> sortCardsForDisplay(List<String> cards, HandStrength strength) {
        if (cards == null || cards.size() != 5 || strength == null) return cards;
        int cat = strength.rankCategory;
        List<Integer> ranks = strength.ranks;

        // 高牌、顺子、同花、同花顺、皇家：按点数从大到小
        if (cat == 1 || cat == 5 || cat == 6 || cat == 9 || cat == 10) {
            boolean wheel = (cat == 5 || cat == 9) && ranks.size() >= 5 && ranks.get(0) == 5;
            cards.sort((a, b) -> {
                int ra = getRankFromCard(a);
                int rb = getRankFromCard(b);
                if (wheel) {
                    int ia = (ra == 14) ? ranks.indexOf(1) : ranks.indexOf(ra);
                    int ib = (rb == 14) ? ranks.indexOf(1) : ranks.indexOf(rb);
                    return Integer.compare(ia, ib);
                }
                return Integer.compare(rb, ra);
            });
            return cards;
        }

        // 一对、两对、三条、葫芦、四条：按 ranks 中的组顺序排
        cards.sort((a, b) -> {
            int ra = getRankFromCard(a);
            int rb = getRankFromCard(b);
            int ia = ranks.indexOf(ra);
            int ib = ranks.indexOf(rb);
            if (ia < 0) ia = 999;
            if (ib < 0) ib = 999;
            int c = Integer.compare(ia, ib);
            if (c != 0) return c;
            return Integer.compare(rb, ra);
        });
        return cards;
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
     * 结算后准备阶段到时间：未准备的玩家被踢到观众席，准备好的玩家自动开下一局。
     * 先让积分不足大盲(10)的人去观众厅，再按准备状态分人，避免输光的被强行带入下一把。
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

        // 先让积分 < 10 的人去观众厅（没补码的不能参与下一局）
        List<String> spectators = r.getSpectators();
        if (spectators == null) {
            spectators = new ArrayList<>();
            r.setSpectators(spectators);
        }
        List<DpPlayer> afterChips = new ArrayList<>();
        for (DpPlayer p : players) {
            if (p.getChips() < DpRoom.getBBChips()) {
                if (!spectators.contains(p.getNickname())) spectators.add(p.getNickname());
            } else {
                afterChips.add(p);
            }
        }
        r.setPlayers(afterChips);
        players = afterChips;
        if (players.isEmpty()) {
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
        spectators = r.getSpectators();
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

        // 如果下一手总人数仍不足 2 个：
        if (nextCount < 2) {
            // 1）如果还有至少 1 个玩家（典型场景：只剩一个人准备），允许“单人娱乐局”
            if (remain.size() >= 1) {
                // 清掉准备倒计时，直接开新一局（仍然按正常德扑流程发牌/算牌力）
                r.setReadyDeadline(0L);
                newHand(r.getRoomId());
                return;
            }

            // 2）没有任何玩家时，结束对局并清空状态
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
     * 结算后筹码不足大盲（10）的玩家补码到初始筹码。
     */
    public boolean rebuy(String roomId, String nickname) {
        DpRoom r = roomMap.get(roomId);
        if (r == null || !r.isPlaying()) return false;
        if (!"settled".equals(r.getCurrentStage())) return false;
        for (DpPlayer p : r.getPlayers()) {
            if (p.getNickname().equals(nickname)) {
                // 筹码充足（>=10）则不允许补码
                if (p.getChips() >= DpRoom.getBBChips()) {
                    return false;
                }
                p.setChips(DpRoom.getChips());
                return true;
            }
        }
        return false;
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
