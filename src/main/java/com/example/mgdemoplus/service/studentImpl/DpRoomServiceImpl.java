package com.example.mgdemoplus.service.studentImpl;

import com.example.mgdemoplus.dto.DpRoomDTO;
import com.example.mgdemoplus.entity.DpPlayer;
import com.example.mgdemoplus.entity.DpPot;
import com.example.mgdemoplus.entity.DpRoom;
import com.example.mgdemoplus.entity.PlayerStats;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class DpRoomServiceImpl {
    private final Map<String, DpRoom> roomMap = new ConcurrentHashMap<>();

    /**
     * 简单演示用的 NPC 昵称。
     * 后续如果要扩展多种类型的机器人，可以在此基础上扩展为配置或枚举。
     */
    private static final String DEMO_BOT_NICKNAME = "BOT_Demo";
    private static final String MANIAC_BOT_NICKNAME = "BOT_Maniac";

    public DpRoomServiceImpl() {
        // 心跳清理 + 超时行动
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                for (DpRoom room : roomMap.values()) {
                    Iterator<DpPlayer> it = room.getPlayers().iterator();
                    while (it.hasNext()) {
                        DpPlayer p = it.next();
                        // 演示用机器人不依赖前端心跳，直接视为始终在线
                        if (isBotPlayer(p)) {
                            p.setLastHeartBeat(System.currentTimeMillis());
                            continue;
                        }
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
                        if (!kickPlayer.isLeftThisHand() && !isBotPlayer(kickPlayer)) {
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
                    if (room.isPlaying()
                            && room.getCurrentActorIndex() >= 0
                            && room.getCurrentActorIndex() < room.getPlayers().size()) {
                        DpPlayer p = room.getPlayers().get(room.getCurrentActorIndex());
                        // 如果当前行动者是 NPC，则直接由后端自动决策行动，不等待前端操作
                        if (isBotPlayer(p)) {//决策入口
                            autoActForBot(room, p);
                        } else {
                            if (p.isLeftThisHand()) {
                                moveToNextValidActor(room);
                                autoAdvanceIfRoundFinished(room);
                            } else if (System.currentTimeMillis() - room.getLastActionTime() > DpRoom.getActionTimeout()) {
                                p.setFold(true);
                                moveToNextValidActor(room);
                                autoAdvanceIfRoundFinished(room);
                            }
                        }
                    }

                    // 结算后准备阶段：机器人自动补码并自动准备
                    if (room.isPlaying() && "settled".equals(room.getCurrentStage())) {
                        boolean botTouched = false;
                        //只有场上的NPC才能自动准备，踢走的不准备
                        for (DpPlayer p : room.getPlayers()) {
                            if (!isBotPlayer(p)) continue;
                            // 若筹码不足大盲，自动补码（内部已有阶段与筹码校验）
                            if (p.getChips() < DpRoom.getBBChips()) {
                                rebuy(room.getRoomId(), p.getNickname());
                            }
                            // 筹码充足时自动准备
                            if (p.getChips() >= DpRoom.getBBChips() && !p.isReady()) {
                                p.setReady(true);
                                botTouched = true;
                            }
                        }
                        // 只要有机器人准备状态被更新，就检查是否可以直接开下一局
                        if (botTouched) {
                            checkAndStartNextHandAfterSettle(room);
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
        }, 0, 1000);
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
            if (!getActivePlayer.isLeftThisHand() && !isBotPlayer(getActivePlayer)) {
                size += 1;//如果有活人就加一个
                System.out.println("退出按钮检测：" + getActivePlayer.getNickname() + "是活人");
            }
        }
        if (size == 0) {
            System.out.println("giveOwner：没有活人了");
            roomMap.remove(roomId);
        } else {
            for (DpPlayer candidate : room.getPlayers()) {//非本人也非僵尸
                if (!candidate.getNickname().equals(ownerNickname) && !candidate.isLeftThisHand() && !isBotPlayer(candidate)) {
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
            if(!isBotPlayer(ps.get(idx))){//如果是机器人直接删掉，如果不是就踢到观众席
                List<String> spectators = getNewSpectators(r);
                if(!spectators.contains(nickname)){
                    spectators.add(nickname);
                }
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

    /**
     * 将演示用 NPC 加入指定房间的下一局等待列表。
     * 对外作为一个简单的演示入口，后续可以替换为更通用的「添加机器人」接口。
     */
    public boolean addDemoBotToNextHand(String roomId) {
        return readyNextHand(roomId, DEMO_BOT_NICKNAME);
    }

    /**
     * 将疯子风格 NPC 加入指定房间的下一局等待列表。
     */
    public boolean addManiacBotToNextHand(String roomId) {
        return readyNextHand(roomId, MANIAC_BOT_NICKNAME);
    }

    /**
     * 将聪明型 NPC（BOT_Shark）加入指定房间的下一局等待列表。
     * 该机器人会基于最近几手对手的行为做简单“读对手”决策。
     */
    public boolean addSharkBotToNextHand(String roomId) {
        return readyNextHand(roomId, "BOT_Shark");
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
            if (player.getChips() >= 10 && !isBotPlayer(player)) {
                size += 1;
            }
        }
        //算已经准备的人数
        for (DpPlayer player : r.getPlayers()) {
            if (player.isReady() && !isBotPlayer(player)) {
                size1 += 1;
            }
        }
        // 如果当前没有任何有能力继续玩的真人玩家（size==0），
        // 不在这里自动开新局，改由结算阶段的超时逻辑 handleReadyTimeout 统一处理
        if (size == 0) {
            return;
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

        // 非 showdown 阶段，优先判断后续是否还可能有新的下注
        if (noFurtherBettingPossible(r)) {
            // 已经不可能再出现新的下注：不设置行动者，让外层循环自动连推到摊牌
            r.setCurrentActorIndex(-1);
            return true;
        }

        // 否则按正常逻辑找下一位行动者
        int newIndex = findFirstActorAfterDealer(r);
        int stillInCount = countPlayersStillInHand(r);
        if (newIndex >= 0 && stillInCount <= 1) {
            // 只剩一人未弃牌，等价于直接收池
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

    /**
     * 判断后续街是否已经不可能再出现新的下注：
     * - 仍在本手牌中的玩家中，非 all-in 的人数 <= 1。
     *   （要么全部 all-in，要么只有一个人还有筹码，但其它人都 all-in 了）
     * 这种情况下，无论有无边池，系统都可以直接连推到摊牌。
     */
    private boolean noFurtherBettingPossible(DpRoom r) {
        if (r == null || r.getPlayers() == null) return false;
        int notAllIn = 0;
        for (DpPlayer p : r.getPlayers()) {
            if (p.isLeftThisHand() || p.isFold()) continue;
            if (!p.isAllIn()) {
                notAllIn++;
                if (notAllIn > 1) {
                    return false;
                }
            }
        }
        // 所有人 all-in（notAllIn == 0）或仅剩一个人没 all-in，且其余都 all-in
        return notAllIn <= 1;
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

        // ==== 更新玩家行为统计：用于高级机器人（如 BOT_Shark）判断对手风格 ====
        if (r.getPlayerStatsMap() != null) {
            Map<String, PlayerStats> statsMap = r.getPlayerStatsMap();
            for (DpPlayer p : r.getPlayers()) {
                String name = p.getNickname();
                PlayerStats stats = statsMap.get(name);
                if (stats == null) {
                    stats = new PlayerStats();
                    statsMap.put(name, stats);
                }
                PlayerStats.SingleHandStats hand = new PlayerStats.SingleHandStats();
                // 参与：本手有过下注（totalBet>0）且未在翻前直接弃牌
                hand.setParticipated(p.getTotalBet() > 0 && !p.isFold());
                // 加注：简单视为本手中有过超过大盲的下注
                hand.setRaised(p.getTotalBet() > DpRoom.getBBChips());
                // 摊牌：到 showdown 阶段且未弃牌
                hand.setWentToShowdown("showdown".equals(r.getCurrentStage()) && !p.isFold());
                stats.addHand(hand, 10);
            }
        }

        // 根据赢/输调整机器人情绪：赢到筹码的机器人情绪略微变高，输掉筹码的略微变低
        for (DpPlayer p : r.getPlayers()) {
            if (!isBotPlayer(p)) {
                continue;
            }
            int initialChips = DpRoom.getChips();
            int diff = p.getChips() - initialChips;
            double moodDelta;
            if (diff > 0) {
                moodDelta = 0.2;
            } else if (diff < 0) {
                moodDelta = -0.2;
            } else {
                continue;
            }
            double newMood = p.getMood() + moodDelta;
            if (newMood > 1.0) newMood = 1.0;
            if (newMood < -1.0) newMood = -1.0;
            p.setMood(newMood);
        }

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

    // ========== 简单 NPC 逻辑：统一收敛的决策入口 ==========

    /**
     * 机器人类型（后续可扩展：紧凶、紧弱、松凶、松弱、石头人、疯子等）。
     * 当前实现：
     * - DEMO：普通风格；
     * - MANIAC：疯子风格，喜欢乱加注、偶尔 all-in。
     */
    private enum BotType {
        DEMO,
        MANIAC,
        SHARK
    }

    /**
     * 机器人对当前牌力的粗粒度感知，用于决策逻辑：
     * - WEAK：垃圾牌 / 纯高牌等；
     * - MEDIUM：一对、两对、三条、一般顺子/同花；
     * - STRONG：葫芦及以上或非常强的成牌。
     */
    private enum SimpleStrength {
        WEAK,
        MEDIUM,
        STRONG
    }

    /**
     * 机器人动作类型：弃牌 / 过牌或跟注 / 加注 / 全下。
     */
    private enum BotActionType {
        FOLD, CALL_OR_CHECK, RAISE, ALL_IN
    }

    /**
     * 机器人决策结果（动作 + 金额）。
     * 对于 FOLD 不关心 amount；CALL_OR_CHECK 表示“跟到当前跟注额或 0”；RAISE / ALL_IN 使用给定金额。
     */
    private static class BotAction {
        private final BotActionType type;
        private final int amount;

        BotAction(BotActionType type, int amount) {
            this.type = type;
            this.amount = amount;
        }

        public BotActionType getType() {
            return type;
        }

        public int getAmount() {
            return amount;
        }
    }

    /**
     * 判断一个玩家是否为机器人。当前版本识别：
     * - BOT_Demo
     * - BOT_Maniac
     */
    private boolean isBotPlayer(DpPlayer p) {
        if (p == null) return false;
        String name = p.getNickname();
        return DEMO_BOT_NICKNAME.equals(name)
                || MANIAC_BOT_NICKNAME.equals(name)
                || "BOT_Shark".equals(name);
    }

    /**
     * 根据昵称映射机器人类型，方便未来扩展多种性格机器人。
     * 目前：
     * - BOT_Demo   → DEMO
     * - BOT_Maniac → MANIAC
     */
    private BotType getBotTypeByNickname(String nickname) {
        if (DEMO_BOT_NICKNAME.equals(nickname)) {
            return BotType.DEMO;
        }
        if (MANIAC_BOT_NICKNAME.equals(nickname)) {
            return BotType.MANIAC;
        }
        if ("BOT_Shark".equals(nickname)) {
            return BotType.SHARK;
        }
        return null;
    }

    /**
     * 将 HandStrength（详细牌力）压缩成机器人决策用的粗粒度档位。
     */
    private SimpleStrength toSimpleStrength(HandStrength hs, String stage, List<String> holeCards, List<String> community) {
        if (hs == null) {
            return SimpleStrength.WEAK;
        }
        int cat = hs.rankCategory;

        // 翻牌后：更细地按牌型粗分
        if ("flop".equals(stage) || "turn".equals(stage) || "river".equals(stage)) {
            // 10 皇家同花顺, 9 同花顺, 8 四条, 7 葫芦, 6 同花, 5 顺子, 4 三条, 3 两对, 2 一对, 1 高牌
            if (cat >= 7) {
                // 葫芦、四条、同花顺、皇家同花顺：绝对强牌
                return SimpleStrength.STRONG;
            }
            if (cat == 6 || cat == 5) {
                // 单纯同花或顺子：偏强，接近强牌
                return SimpleStrength.STRONG;
            }
            if (cat == 4 || cat == 3) {
                // 三条或两对：中强牌，视为中档偏上
                return SimpleStrength.MEDIUM;
            }
            // 一对 / 高牌
            return SimpleStrength.WEAK;
        }

        // preflop：简单看起手牌质量
        if (holeCards == null || holeCards.size() < 2) {
            return SimpleStrength.WEAK;
        }
        int r1 = getRankFromCard(holeCards.get(0));
        int r2 = getRankFromCard(holeCards.get(1));
        boolean pair = (r1 == r2);
        int high = Math.max(r1, r2);
        int low = Math.min(r1, r2);

        if (pair && high >= 10) {
            // TT+ 口袋对
            return SimpleStrength.STRONG;
        }
        if (pair || (high >= 11 && low >= 9)) {
            // 其它对子 / 高张连牌
            return SimpleStrength.MEDIUM;
        }
        return SimpleStrength.WEAK;
    }

    /**
     * 粗略评估机器人当前这手牌的强度，用于行为决策，而不是用于精确比牌。
     */
    private SimpleStrength estimateCurrentStrength(DpRoom room, DpPlayer bot) {
        List<String> hole = bot.getHoleCards();
        List<String> community = room.getCommunityCards();
        if (hole == null || hole.size() < 2) {
            return SimpleStrength.WEAK;
        }
        List<String> all = new ArrayList<>(hole);
        if (community != null) {
            all.addAll(community);
        }
        if (all.size() < 5) {
            // 公共牌不足 3 张时，用简单 preflop 规则
            return toSimpleStrength(null, room.getCurrentStage(), hole, community);
        }
        HandStrength hs = evaluateBestHand(all);
        return toSimpleStrength(hs, room.getCurrentStage(), hole, community);
    }

    /**
     * 简单评估公共牌危险度：
     * - DRY：无明显顺子/同花结构；
     * - WET：存在大量连张或同花牌，容易被听顺/听同花。
     */
    private enum BoardDanger {
        DRY,
        WET
    }

    private BoardDanger evaluateBoardDanger(List<String> communityCards) {
        if (communityCards == null || communityCards.size() < 3) {
            return BoardDanger.DRY;
        }
        Set<Integer> ranks = new HashSet<>();
        Map<String, Integer> suitCount = new HashMap<>();
        for (String c : communityCards) {
            if (c == null || !c.contains("_")) continue;
            String[] parts = c.split("_", 2);
            if (parts.length != 2) continue;
            String suit = parts[0];
            String rankStr = parts[1];
            int r = CARD_RANK_MAP.getOrDefault(rankStr, 0);
            if (r > 0) {
                ranks.add(r);
            }
            suitCount.put(suit, suitCount.getOrDefault(suit, 0) + 1);
        }
        // 同花危险：某一花色数量 >= 3
        for (Integer cnt : suitCount.values()) {
            if (cnt >= 3) {
                return BoardDanger.WET;
            }
        }
        // 顺子危险：有 3 张以上接近的连张结构
        if (ranks.size() >= 3) {
            List<Integer> list = new ArrayList<>(ranks);
            Collections.sort(list);
            int maxRun = 1;
            int currentRun = 1;
            for (int i = 1; i < list.size(); i++) {
                if (list.get(i) == list.get(i - 1) + 1) {
                    currentRun++;
                    maxRun = Math.max(maxRun, currentRun);
                } else if (list.get(i) > list.get(i - 1) + 1) {
                    currentRun = 1;
                }
            }
            if (maxRun >= 3) {
                return BoardDanger.WET;
            }
        }
        return BoardDanger.DRY;
    }

    /**
     * 在定时任务中调用：如果轮到机器人行动，则由后端统一通过决策函数 decideBotAction
     * 计算动作，并最终调用现有的 bet / fold 接口。
     */
    private void autoActForBot(DpRoom room, DpPlayer bot) {
        if (room == null || bot == null || !room.isPlaying()) {
            return;
        }
        int actorIndex = room.getCurrentActorIndex();
        if (actorIndex < 0 || actorIndex >= room.getPlayers().size()) {
            return;
        }
        // 防御：当前行动位必须就是这个 bot，且未弃牌/未 all-in
        DpPlayer current = room.getPlayers().get(actorIndex);
        if (current != bot) {
            return;
        }
        if (bot.isFold() || bot.isAllIn() || bot.isLeftThisHand()) {
            return;
        }

        // 先根据当前局面为机器人设置一次“思考时间”，到点前不真正出手，
        // 用于营造强牌长考、弱牌秒出的节奏感。
        long now = System.currentTimeMillis();
        long nextTime = bot.getNextBotActionTime();
        if (nextTime <= 0L) {
            long delayMs = calculateBotThinkDelay(room, bot);
            bot.setNextBotActionTime(now + delayMs);
            return;
        }
        if (now < nextTime) {
            // 还在思考时间窗口内，暂不行动
            return;
        }
        // 本轮行动完成后清零，下次成为行动者时重新计算
        bot.setNextBotActionTime(0L);

        // 通过名字决定类型
        BotType type = getBotTypeByNickname(bot.getNickname());
        if (type == null) {
            return;
        }

        BotAction action = decideBotAction(room, bot, type);
        if (action == null) {
            return;
        }

        String roomId = room.getRoomId();

        switch (action.getType()) {
            case FOLD:
                fold(roomId, bot.getNickname());
                break;
            case CALL_OR_CHECK: {
                int callAmount = Math.max(0, room.getCurrentBetToCall() - bot.getBet());
                bet(roomId, bot.getNickname(), callAmount);
                break;
            }
            case RAISE:
            case ALL_IN: {
                int amount = Math.max(0, action.getAmount());
                bet(roomId, bot.getNickname(), amount);
                break;
            }
            default:
                break;
        }
    }

    // ========== 机器人“思考时间”配置 ==========

    /**
     * 思考时间配置（毫秒）。
     * 你可以把这里当成“调参区”：不同机器人类型可以完全分开设置。
     */
    private static class BotThinkProfile {
        final long weakMinMs;
        final long weakMaxMs;
        final long mediumMinMs;
        final long mediumMaxMs;
        final long strongMinMs;
        final long strongMaxMs;

        // 便宜跟注/免费过牌时的“秒出手”区间
        final long cheapMinMs;
        final long cheapMaxMs;

        // 满足 cheap 条件时，直接走 cheap 区间的概率（否则仍按牌力区间抽）
        final double cheapSnapProb;

        // 无论牌力/cheap 与否，都可能“秒出手”的概率（用来做出真人式的秒 call / 秒 raise）
        final double globalSnapProb;

        // 最高思考时间上限（防止拖慢节奏）
        final long maxCapMs;

        BotThinkProfile(long weakMinMs, long weakMaxMs,
                        long mediumMinMs, long mediumMaxMs,
                        long strongMinMs, long strongMaxMs,
                        long cheapMinMs, long cheapMaxMs,
                        double cheapSnapProb,
                        double globalSnapProb,
                        long maxCapMs) {
            this.weakMinMs = weakMinMs;
            this.weakMaxMs = weakMaxMs;
            this.mediumMinMs = mediumMinMs;
            this.mediumMaxMs = mediumMaxMs;
            this.strongMinMs = strongMinMs;
            this.strongMaxMs = strongMaxMs;
            this.cheapMinMs = cheapMinMs;
            this.cheapMaxMs = cheapMaxMs;
            this.cheapSnapProb = cheapSnapProb;
            this.globalSnapProb = globalSnapProb;
            this.maxCapMs = maxCapMs;
        }
    }

    // 你想“分开设置”就改这里（单位：毫秒）
    private static final BotThinkProfile THINK_DEMO = new BotThinkProfile(
            250, 1700,   // WEAK
            1200, 4200,  // MEDIUM
            2800, 7500,  // STRONG
            0, 450,      // cheap snap range
            0.85,        // cheapSnapProb
            0.03,        // globalSnapProb（偶尔秒出）
            12000        // max cap
    );

    private static final BotThinkProfile THINK_MANIAC = new BotThinkProfile(
            0, 500,      // WEAK：基本秒出
            200, 900,    // MEDIUM：也很快
            500, 1800,   // STRONG：强牌也不会长考太久（更像“手快疯子”）
            0, 250,      // cheap snap range
            0.95,        // cheapSnapProb
            0.18,        // globalSnapProb（明显更爱秒 call/秒 raise）
            6000         // max cap（疯子不拖节奏）
    );

    private static final BotThinkProfile THINK_SHARK = new BotThinkProfile(
            200, 1600,   // WEAK
            1400, 5200,  // MEDIUM：更爱思考
            3200, 9000,  // STRONG：更可能“故作长考慢打”
            0, 500,      // cheap snap range
            0.70,        // cheapSnapProb（便宜时也会经常秒 call）
            0.08,        // globalSnapProb（偶尔秒出，制造对手读牌困难）
            12000        // max cap
    );

    private BotThinkProfile getThinkProfile(BotType type) {
        if (type == null) return THINK_DEMO;
        switch (type) {
            case MANIAC:
                return THINK_MANIAC;
            case SHARK:
                return THINK_SHARK;
            case DEMO:
            default:
                return THINK_DEMO;
        }
    }

    private long randomBetween(long minInclusive, long maxInclusive, Random random) {
        long min = Math.max(0L, minInclusive);
        long max = Math.max(0L, maxInclusive);
        if (max <= min) return min;
        long span = max - min + 1;
        long v = Math.floorMod(random.nextLong(), span);
        return min + v;
    }

    /**
     * 为机器人当前这一手行动计算一个“思考时间”延迟。
     * 大致规则：
     * - 强牌：倾向 3~8 秒的长考；
     * - 中等牌：1.5~5 秒；
     * - 弱牌：0.2~1.5 秒；
     * - 若弱牌且需要跟注筹码极小（或可 free check），会出现 0~0.4 秒的“秒 call / 秒过牌”效果；
     * - 疯子型/情绪高的机器人整体会更快，石头/情绪差的略慢。
     */
    private long calculateBotThinkDelay(DpRoom room, DpPlayer bot) {
        int chips = bot.getChips();
        int callAmount = Math.max(0, room.getCurrentBetToCall() - bot.getBet());
        double callRatio = chips == 0 ? 1.0 : (callAmount * 1.0 / Math.max(1, chips));

        SimpleStrength strength = estimateCurrentStrength(room, bot);
        BotType type = getBotTypeByNickname(bot.getNickname());
        double mood = bot.getMood();
        Random random = new Random();

        BotThinkProfile profile = getThinkProfile(type);

        // 全局“秒出手”
        if (profile.globalSnapProb > 0 && random.nextDouble() < profile.globalSnapProb) {
            return randomBetween(0, Math.max(0L, profile.cheapMaxMs), random);
        }

        // cheap snap：free check 或者跟注很便宜
        boolean isCheap = (callAmount == 0) || (callRatio <= 0.2);
        if (isCheap && profile.cheapSnapProb > 0 && random.nextDouble() < profile.cheapSnapProb) {
            return randomBetween(profile.cheapMinMs, profile.cheapMaxMs, random);
        }

        // 基础区间（毫秒）
        long minMs;
        long maxMs;
        switch (strength) {
            case STRONG:
                minMs = profile.strongMinMs;
                maxMs = profile.strongMaxMs;
                break;
            case MEDIUM:
                minMs = profile.mediumMinMs;
                maxMs = profile.mediumMaxMs;
                break;
            default:
                minMs = profile.weakMinMs;
                maxMs = profile.weakMaxMs;
                break;
        }

        // 情绪：高兴时更快，低落时更慢
        // mood ∈ [-1,1]，映射到 [0.8, 1.2] 左右的速度因子
        double moodFactor = 1.0 - mood * 0.2;
        if (moodFactor < 0.8) moodFactor = 0.8;
        if (moodFactor > 1.2) moodFactor = 1.2;

        long base = randomBetween(minMs, maxMs, random);
        long result = (long) (base * moodFactor);

        // 安全下限/上限保护：避免过短或超过超时踢人的 30 秒窗口
        if (result < 0L) result = 0L;
        long cap = profile.maxCapMs > 0 ? profile.maxCapMs : 12000L;
        if (cap > 20000L) cap = 20000L; // 兜底：永远不要太离谱
        if (result > cap) result = cap;
        return result;
    }

    /**
     * 统一的机器人决策函数。
     * 未来新增类型（紧凶 / 紧弱 / 松凶 / 松弱 / 石头人 / 疯子）时，只需要在这里增加分支。
     *
     * 当前 DEMO 策略：
     * - 若需要跟注筹码超过自身筹码的一半，直接弃牌；
     * - 否则 80% 概率选择过牌/跟注，20% 概率在翻牌后尝试小额加注。
     */
    private BotAction decideBotAction(DpRoom room, DpPlayer bot, BotType type) {
        // 若已无筹码，直接弃牌
        int chips = bot.getChips();
        if (chips <= 0) {
            return new BotAction(BotActionType.FOLD, 0);
        }

        int callAmount = Math.max(0, room.getCurrentBetToCall() - bot.getBet());
        SimpleStrength strength = estimateCurrentStrength(room, bot);
        double callRatio = chips == 0 ? 1.0 : (callAmount * 1.0 / chips);
        BoardDanger boardDanger = evaluateBoardDanger(room.getCommunityCards());
        double mood = bot.getMood();
        Random random = new Random();

        switch (type) {
            case DEMO: {
                // 基础弃牌倾向：弱牌 + 跟注代价偏大 + 牌面危险，偏向弃牌
                double foldBase = 0.0;
                if (strength == SimpleStrength.WEAK && callAmount > 0 && callRatio > 0.5) {
                    foldBase = 0.6;
                    if (boardDanger == BoardDanger.WET) {
                        foldBase += 0.2;
                    }
                }
                // 情绪修正：情绪负时更容易弃牌，正时稍微硬一点
                double foldProb = Math.min(1.0, Math.max(0.0, foldBase + (-mood) * 0.2));
                if (foldProb > 0 && random.nextDouble() < foldProb) {
                    return new BotAction(BotActionType.FOLD, 0);
                }

                double r = random.nextDouble();

                // 大部分时间选择过牌/跟注；情绪高时略微提高主动进攻意愿
                double callOrCheckProb = 0.75 - mood * 0.1;
                if (r < callOrCheckProb) {
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }

                // 小概率：在翻牌之后尝试小额加注，强牌更倾向大一点
                String stage = room.getCurrentStage();
                if (!"preflop".equals(stage) && chips > callAmount) {
                    int raiseBase = callAmount;
                    int extraBB;
                    if (strength == SimpleStrength.STRONG) {
                        extraBB = 2;
                    } else if (strength == SimpleStrength.MEDIUM) {
                        extraBB = 1;
                    } else {
                        extraBB = 1;
                    }
                    int minExtra = DpRoom.getBBChips() * extraBB;
                    int raiseAmount = Math.min(chips, raiseBase + minExtra);
                    return new BotAction(BotActionType.RAISE, raiseAmount);
                }

                // 其他情况回退到过牌/跟注
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            case MANIAC: {
                // 疯子风格：明显更激进，极少弃牌，经常加注甚至乱 all-in
                String stage = room.getCurrentStage();

                // 如果必须顶着对方 all-in：大部分时间跟着冲，极少数情况下才会弃牌
                if (callAmount >= chips) {
                    double rAllIn = random.nextDouble();
                    if (rAllIn < 0.08) {
                        // 8% 直接弃牌（偶尔“理智”一下）
                        return new BotAction(BotActionType.FOLD, 0);
                    } else {
                        // 92% 直接 all-in 跟上去
                        return new BotAction(BotActionType.ALL_IN, chips);
                    }
                }

                double r = random.nextDouble();

                // 没有需要跟注的筹码（可以 free check 的场景）
                if (callAmount == 0) {
                    // 情绪越高，越喜欢主动下注，基础就已经很爱开火
                    double raiseProb = 0.75 + mood * 0.15; // 60%~90% 左右
                    raiseProb = Math.max(0.6, Math.min(0.95, raiseProb));

                    if (r < raiseProb) {
                        // 直接加注 2~4 个大盲，强牌更倾向更大，弱牌也经常乱开火
                        int maxMulti;
                        int minMulti;
                        if (strength == SimpleStrength.STRONG) {
                            minMulti = 3;
                            maxMulti = 5;
                        } else if (strength == SimpleStrength.MEDIUM) {
                            minMulti = 2;
                            maxMulti = 4;
                        } else {
                            minMulti = 1;
                            maxMulti = 3;
                        }
                        int range = Math.max(1, maxMulti - minMulti + 1);
                        int multiplier = minMulti + random.nextInt(range);
                        int raiseAmount = Math.min(chips, DpRoom.getBBChips() * multiplier);
                        if (raiseAmount <= 0) {
                            return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                        }
                        return new BotAction(BotActionType.RAISE, raiseAmount);
                    }
                    // 少数情况先装一装，check 看牌
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }

                // 有需要跟注的筹码：整体很不爱弃牌
                double maniFoldBase = 0.03; // 基础弃牌意愿极低
                if (strength == SimpleStrength.WEAK && callRatio > 0.85 && boardDanger == BoardDanger.WET) {
                    // 弱牌遇到巨大压力且牌面危险时，才明显增加弃牌倾向
                    maniFoldBase = 0.2;
                }
                // 情绪高时更不爱弃牌（甚至负数，下面 clamp）
                double maniFoldProb = maniFoldBase - mood * 0.2;
                if (maniFoldProb < 0.0) maniFoldProb = 0.0;
                if (maniFoldProb > 0.5) maniFoldProb = 0.5;
                if (random.nextDouble() < maniFoldProb) {
                    return new BotAction(BotActionType.FOLD, 0);
                }

                // 在“还没 all-in”的情况下，分配：少量跟注 + 大量加注 + 一定比例无脑 all-in
                if (r < 0.25 + mood * 0.05) {
                    // 大约 20%~30% 选择跟注，情绪越高越少只是跟注
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }
                if (r < 0.85 + mood * 0.05) {
                    // 大约 55%~65% 选择加注：在需要跟注的基础上再加 2~5 个大盲
                    int minMulti;
                    int maxMulti;
                    if (strength == SimpleStrength.STRONG) {
                        minMulti = 3;
                        maxMulti = 6;
                    } else if (strength == SimpleStrength.MEDIUM) {
                        minMulti = 2;
                        maxMulti = 5;
                    } else {
                        minMulti = 1;
                        maxMulti = 4;
                    }
                    int range2 = Math.max(1, maxMulti - minMulti + 1);
                    int multiplier2 = minMulti + random.nextInt(range2);
                    int extra = DpRoom.getBBChips() * multiplier2;
                    int target = callAmount + extra;
                    int raiseAmount = Math.min(chips, target);
                    if (raiseAmount <= 0) {
                        return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                    }
                    return new BotAction(BotActionType.RAISE, raiseAmount);
                }
                // 剩下约 10%~20%：直接 all-in，体现疯子风格
                return new BotAction(BotActionType.ALL_IN, chips);
            }
            case SHARK: {
                // 聪明型：在 DEMO 基础上，根据对手风格适当调整弃牌/跟注/加注概率
                BoardDanger bd = boardDanger;
                SimpleStrength st = strength;

                double baseFold = 0.0;
                if (st == SimpleStrength.WEAK && callAmount > 0 && callRatio > 0.4) {
                    baseFold = (bd == BoardDanger.WET) ? 0.7 : 0.5;
                }

                // 如果当前有明显的加注者，尝试根据其近期风格调整
                PlayerStats.SingleHandStats lastAggressorStats = null;
                PlayerStats aggressorStats = null;
                Map<String, PlayerStats> statsMap = room.getPlayerStatsMap();
                if (statsMap != null) {
                    // 简单选择上一轮最大下注的玩家作为主要对手
                    DpPlayer aggressor = null;
                    int maxBet = 0;
                    for (DpPlayer p : room.getPlayers()) {
                        if (p.isFold() || p.isLeftThisHand()) continue;
                        if (p.getBet() > maxBet) {
                            maxBet = p.getBet();
                            aggressor = p;
                        }
                    }
                    if (aggressor != null && !aggressor.getNickname().equals(bot.getNickname())) {
                        aggressorStats = statsMap.get(aggressor.getNickname());
                        if (aggressorStats != null && !aggressorStats.getRecentHands().isEmpty()) {
                            lastAggressorStats = aggressorStats.getRecentHands().peekLast();
                        }
                    }
                }

                if (aggressorStats != null) {
                    double overallPart = aggressorStats.getOverallParticipationRate();
                    double overallRaise = aggressorStats.getOverallRaiseRate();

                    // 粗暴规则：
                    // - 长期参与率 & 加注率都比较高 → 松凶
                    // - 长期参与率很低 & 加注率也低 → 紧弱/石头
                    boolean looseAggressiveLong = overallPart > 0.5 && overallRaise > 0.3;
                    boolean tightPassiveLong = overallPart < 0.25 && overallRaise < 0.15;

                    boolean looseAggressiveRecent = lastAggressorStats != null
                            && lastAggressorStats.isParticipated() && lastAggressorStats.isRaised();
                    boolean tightPassiveRecent = lastAggressorStats != null
                            && !lastAggressorStats.isParticipated() && !lastAggressorStats.isRaised();

                    if (looseAggressiveLong || looseAggressiveRecent) {
                        // 对疯狗型：明显减少弃牌概率
                        baseFold *= 0.4;
                    } else if (tightPassiveLong || tightPassiveRecent) {
                        // 对石头人：明显增加弃牌概率
                        baseFold = Math.min(1.0, baseFold + 0.35);
                    }
                }

                double foldProbShark = Math.min(1.0, Math.max(0.0, baseFold + (-mood) * 0.1));
                if (foldProbShark > 0 && random.nextDouble() < foldProbShark) {
                    return new BotAction(BotActionType.FOLD, 0);
                }

                double r = random.nextDouble();

                String stage = room.getCurrentStage();

                // 超强牌时增加“慢打”可能：前两街小跟/小加，河牌再猛烈出手
                boolean isLateStreet = "river".equals(stage);

                // 大部分时间跟注/过牌，强牌和好情绪时提升加注几率
                double callProb = 0.6 + mood * 0.05;
                if (st == SimpleStrength.STRONG) {
                    // 强牌整体更偏向进攻：减少只跟概率
                    callProb -= 0.25;
                    if (!isLateStreet) {
                        // 前面街保留一部分慢打空间
                        callProb += 0.15;
                    }
                } else if (st == SimpleStrength.MEDIUM) {
                    callProb += 0.05;
                }

                if (r < Math.max(0.1, Math.min(0.9, callProb))) {
                    return new BotAction(BotActionType.CALL_OR_CHECK, 0);
                }

                if (!"preflop".equals(stage) && chips > callAmount) {
                    int raiseBase = callAmount;
                    int extraBB;
                    if (st == SimpleStrength.STRONG) {
                        // 强牌：河牌阶段更愿意打大一点
                        extraBB = isLateStreet ? 4 : 2;
                    } else if (st == SimpleStrength.MEDIUM) {
                        extraBB = 2;
                    } else {
                        extraBB = 1;
                    }
                    int minExtra = DpRoom.getBBChips() * extraBB;
                    int raiseAmount = Math.min(chips, raiseBase + minExtra);
                    return new BotAction(BotActionType.RAISE, raiseAmount);
                }
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
            }
            default:
                return new BotAction(BotActionType.CALL_OR_CHECK, 0);
        }
    }
}
