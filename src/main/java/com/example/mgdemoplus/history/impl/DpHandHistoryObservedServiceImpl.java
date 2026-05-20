package com.example.mgdemoplus.history.impl;

import com.example.mgdemoplus.history.bo.DpObservedHandActionRecordBO;
import com.example.mgdemoplus.history.bo.DpObservedHandRecordBO;
import com.example.mgdemoplus.history.bo.DpObservedPotSnapshotBO;
import com.example.mgdemoplus.history.bo.DpObservedSeatAtHandStartBO;
import com.example.mgdemoplus.history.bo.DpObservedStreetBoardBO;
import com.example.mgdemoplus.common.bo.DpRoomBO;
import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.common.entity.DpPot;
import com.example.mgdemoplus.history.DpHandHistoryObservedService;
import com.example.mgdemoplus.history.types.DpObservedHandActionType;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本桌有玩家时即记录完整牌谱，供入库与回放。
 *
 * <p>与 {@link DpNpcStreetActionLog} 并行：该类记录逐街动作用于统计。</p>
 */
@Service
public final class DpHandHistoryObservedServiceImpl implements DpHandHistoryObservedService {
    //本模块分为以下模块：
    //1. DpObservedHandActionType枚举：负责记录行动类型
    //2. DpObservedHandActionRecordDTO类：负责记录行动
    //3. DpObservedStreetBoardDTO类：负责记录公共牌
    //4. DpObservedSeatAtHandStartDTO类：负责记录座位
    //5. DpObservedPotSnapshotDTO类：负责记录池
    //6. DpObservedHandRecordDTO类：负责记录牌谱
    //7. Key类：负责记录房间id和手种子
    //8. HandBuilder类：负责记录牌谱
    //9. BUILDERS类：负责记录牌谱
    //10. isEnabledForRoom方法：负责判断是否启用
    //11. beginHand方法：负责开始手
    //12. markHandReadyAfterBlinds方法：负责标记手准备
    //13. recordBoardState方法：负责记录公共牌
    //14. recordBlind方法：负责记录盲注
    //15. recordFold方法：负责记录弃牌
    //16. recordBetLikeAction方法：负责记录行动
    //17. capturePotsBeforeClear方法：负责记录池
    public DpHandHistoryObservedServiceImpl() {
    }

    private static final class Key {
        final String roomId;
        final long handSeed;

        Key(String roomId, long handSeed) {
            this.roomId = roomId == null ? "" : roomId;
            this.handSeed = handSeed;
        }
/**
 * 已学习，本模块代码精讲如下：
 * 1. equals方法：负责判断两个Key对象是否相等
 * 2. hashCode方法：负责计算Key对象的哈希值
 */
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Key key = (Key) o;
            return handSeed == key.handSeed && Objects.equals(roomId, key.roomId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(roomId, handSeed);
        }
    }

    private static final class HandBuilder {
        long startedAtMs = System.currentTimeMillis();//开始时间
        String dealerNickname = "";//庄家
        final List<DpObservedSeatAtHandStartBO> seatsAtStart = new ArrayList<>();//座位
        final List<DpObservedStreetBoardBO> boardsByStreet = new ArrayList<>();//公共牌
        final List<DpObservedHandActionRecordBO> actions = new ArrayList<>();//行动
        List<DpObservedPotSnapshotBO> potsBeforeSettlement = List.of();//池
        int mainPotTotalBeforeSettlement;//主池
        final Map<String, Integer> chipsAfterBlinds = new HashMap<>();//筹码
    }

    private static final ConcurrentHashMap<Key, HandBuilder> BUILDERS = new ConcurrentHashMap<>();

    /**
     * 是否为本桌记牌谱：有人上桌即开启。
     */
    public static boolean isEnabledForRoom(DpRoomBO room) {
        if (room == null || room.getPlayers() == null) {
            return false;
        }
        for (DpPlayer p : room.getPlayers()) {
            if (p != null) {
                return true;
            }
        }
        return false;
    }
//这里是开始一手牌谱的记录
//已学习
    public void beginHand(DpRoomBO room) {
        if (!isEnabledForRoom(room)) return;
        Key k = new Key(room.getRoomId(), room.getCurrentHandSeed());
        HandBuilder b = new HandBuilder();
        b.startedAtMs = System.currentTimeMillis();
        //将手种子和房间id作为键，将HandBuilder对象作为值，存入BUILDERS中
        BUILDERS.put(k, b);//BUILDERS是一个ConcurrentHashMap，用于存储手种子和房间id作为键，HandBuilder对象作为值
    }

    /**
     * 发牌与下盲结束后调用：固定座位序、盲注后筹码，并记一条 preflop 空_board。
     */
    //已学习，本模块代码精讲如下：翻前行动前快照
   public  void markHandReadyAfterBlinds(DpRoomBO room) {
        if (!isEnabledForRoom(room)) return;
        Key k = new Key(room.getRoomId(), room.getCurrentHandSeed());
        HandBuilder b = BUILDERS.get(k);
        if (b == null) return;

        List<DpPlayer> ps = room.getPlayers();
        if (ps != null) {
            for (int i = 0; i < ps.size(); i++) {
                DpPlayer p = ps.get(i);
                if (p == null) continue;
                b.seatsAtStart.add(new DpObservedSeatAtHandStartBO(i, p.getNickname(), p.getBlind(), p.getChips()));
                b.chipsAfterBlinds.put(p.getNickname(), p.getChips());
                if (p.isDealer()) {
                    b.dealerNickname = p.getNickname() == null ? "" : p.getNickname();
                }
            }
        }
        b.boardsByStreet.add(new DpObservedStreetBoardBO("preflop", room.getCommunityCards() == null
                ? List.of() : new ArrayList<>(room.getCommunityCards())));
    }
//之后行动记录用
   public  void recordBoardState(DpRoomBO room) {
        if (!isEnabledForRoom(room)) return;
        Key k = new Key(room.getRoomId(), room.getCurrentHandSeed());
        HandBuilder b = BUILDERS.get(k);
        if (b == null) return;
        String st = room.getCurrentStage() == null ? "" : room.getCurrentStage();
        if ("showdown".equals(st) || "settled".equals(st)) return;
        List<String> cc = room.getCommunityCards() == null
                ? List.of()
                : new ArrayList<>(room.getCommunityCards());
        b.boardsByStreet.add(new DpObservedStreetBoardBO(st, cc));
    }

   public  void recordBlind(DpRoomBO room, String nickname, boolean isSb, int amount, int potBefore) {
        if (!isEnabledForRoom(room)) return;
        DpObservedHandActionType t = isSb ? DpObservedHandActionType.POST_BLIND_SB : DpObservedHandActionType.POST_BLIND_BB;
        append(room, new DpObservedHandActionRecordBO(System.currentTimeMillis(), "preflop", nickname, t, amount,
                0, 0, room.getRaiseLevel(), potBefore));
    }

  public   void recordFold(DpRoomBO room, DpPlayer actor, int potBefore) {
        if (!isEnabledForRoom(room)) return;
        if (actor == null) return;
        append(room, new DpObservedHandActionRecordBO(System.currentTimeMillis(), room.getCurrentStage(),
                actor.getNickname(), DpObservedHandActionType.FOLD, 0,
                room.getCurrentBetToCall(), actor.getBet(), room.getRaiseLevel(), potBefore));
    }

   public  void recordBetLikeAction(DpRoomBO room, DpPlayer actor, int amount,
                                    int betToCallBefore, int actorBetBefore, int potBefore,
                                    boolean becameAllIn, boolean isRaise) {
        if (!isEnabledForRoom(room)) return;
        if (actor == null) return;
        DpObservedHandActionType t;
        if (amount <= 0) {
            t = (betToCallBefore <= actorBetBefore) ? DpObservedHandActionType.CHECK : DpObservedHandActionType.CALL;
        } else if (becameAllIn) {
            t = DpObservedHandActionType.ALL_IN;
        } else if (isRaise) {
            t = DpObservedHandActionType.RAISE;
        } else {
            t = (betToCallBefore <= actorBetBefore) ? DpObservedHandActionType.BET : DpObservedHandActionType.CALL;
        }
        append(room, new DpObservedHandActionRecordBO(System.currentTimeMillis(), room.getCurrentStage(),
                actor.getNickname(), t, amount, betToCallBefore, actorBetBefore,
                room.getRaiseLevel(), potBefore));
    }

    /** 在筹码分配前调用：复制主池与边池结构 */
  public   void capturePotsBeforeClear(DpRoomBO room) {//由HandBuilder类接收
        if (!isEnabledForRoom(room)) return;
        Key k = new Key(room.getRoomId(), room.getCurrentHandSeed());
        HandBuilder b = BUILDERS.get(k);
        if (b == null) return;
        b.mainPotTotalBeforeSettlement = room.getPot();
        List<DpPot> pots = room.getPots();
        if (pots == null || pots.isEmpty()) {
            b.potsBeforeSettlement = List.of();
            return;
        }
        List<DpObservedPotSnapshotBO> snap = new ArrayList<>();
        for (DpPot pot : pots) {
            if (pot == null) continue;
            snap.add(new DpObservedPotSnapshotBO(pot.getAmount(),
                    pot.getEligiblePlayers() == null ? List.of() : new ArrayList<>(pot.getEligiblePlayers())));
        }
        b.potsBeforeSettlement = snap;
    }

    /**
     * 丢弃本手未归档的构建数据（与 {@link com.example.mgdemoplus.npc.engine.DpNpcStreetActionLog#clearHand} 对称）。
     */
    @Override
    public void clearHand(DpRoomBO room) {
        if (room == null) return;
        BUILDERS.remove(new Key(room.getRoomId(), room.getCurrentHandSeed()));
    }

    /**
     * 无效逻辑从这里判定
     * @return 若本手已归档则返回记录（供入库）；未启用或无构建数据则 null
     
    已学习，本模块代码精讲如下：
    1. finalizeHand方法：负责将牌谱归档
    2. isEnabledForRoom方法：负责判断是否启用  
    3. Key类：负责记录房间id和手种子  
    4. HandBuilder类：负责记录牌谱  
    5. BUILDERS类：负责记录牌谱  
    6. append方法：负责记录行动  
    7. blindPostedChipsForSeat方法：负责记录盲注  
    */
   public DpObservedHandRecordBO finalizeHand(DpRoomBO room) {
      if(room.getPlayers().size()<=1){
    // System.out.println("场上只有一个玩家，不落库保存");
        return null;//如果场上只有1个玩家，则不记录牌谱
      }
        if (!isEnabledForRoom(room)) {
            return null;
        }
        Key k = new Key(room.getRoomId(), room.getCurrentHandSeed());
        HandBuilder b = BUILDERS.remove(k);
        if (b == null) {
            return null;
        }

        long ended = System.currentTimeMillis();
        Map<String, List<String>> holes = new HashMap<>();
        Map<String, Integer> net = new HashMap<>();
        List<DpPlayer> ps = room.getPlayers();
        if (ps != null) {
            for (DpPlayer p : ps) {
                if (p == null) continue;
                String name = p.getNickname();
                if (name == null) continue;
                List<String> hc = p.getHoleCards();
                if (hc != null && !hc.isEmpty()) {
                    holes.put(name, List.copyOf(hc));
                } else {
                    holes.put(name, List.of());
                }
                // 基准须为「下盲注前」筹码：chipsAfterBlinds 已是扣盲后，若直接作差会把已交的盲注从盈亏里漏掉
                //（例如仅输掉大盲时显示 0 而非 -BB）。beforeHand = afterBlinds + 本手已下盲注额。
                int afterBlinds = b.chipsAfterBlinds.getOrDefault(name, p.getChips());
                int blindPostedThisHand = blindPostedChipsForSeat(room, b.seatsAtStart, name);
                int beforeHand = afterBlinds + blindPostedThisHand;
                net.put(name, p.getChips() - beforeHand);
            }
        }

        int effectiveMainPotTotal = effectiveMainPotTotalBeforeSettlement(
                b.potsBeforeSettlement, b.mainPotTotalBeforeSettlement);

        DpObservedHandRecordBO rec = new DpObservedHandRecordBO(
                room.getRoomId(),
                room.getCurrentHandSeed(),
                b.startedAtMs,
                ended,
                room.getSmallBlindChips(),
                room.getBigBlindChips(),
                b.dealerNickname,
                b.seatsAtStart,
                b.boardsByStreet,
                b.actions,
                b.potsBeforeSettlement,
                effectiveMainPotTotal,
                holes,
                net
        );
        return rec;
    }
//已学习，记录行动用的方法
    private static void append(DpRoomBO room, DpObservedHandActionRecordBO e) {
        Key k = new Key(room.getRoomId(), room.getCurrentHandSeed());
        HandBuilder b = BUILDERS.computeIfAbsent(k, kk -> {
            HandBuilder nb = new HandBuilder();
            nb.startedAtMs = System.currentTimeMillis();
            return nb;
        });
        b.actions.add(e);
    }

    /**
     * 本手开局时该座位已下盲注（与 {@link com.example.mgdemoplus.room.impl.DpRoomServiceImpl#newHand} 中 SB/BB 一致）；
     * 用于把「盲注后筹码」还原为「盲注前筹码」，使 net 表示本手相对发牌前的净变化。
     */
    /**
     * 与 {@link com.example.mgdemoplus.room.impl.DpRoomServiceImpl#calculatePots} 分层一致：仅一人有资格赢取的池为深码多出的无效竞争部分，不计入。
     */
    private static int effectiveMainPotTotalBeforeSettlement(List<DpObservedPotSnapshotBO> pots, int totalPotFallback) {
        if (pots == null || pots.isEmpty()) {
            return totalPotFallback;
        }
        int sum = 0;
        for (DpObservedPotSnapshotBO pot : pots) {
            if (pot == null) {
                continue;
            }
            List<String> el = pot.eligibleNicknames;
            if (el != null && el.size() >= 2) {
                sum += pot.amount;
            }
        }
        return sum > 0 ? sum : totalPotFallback;
    }

    private static int blindPostedChipsForSeat(DpRoomBO room, List<DpObservedSeatAtHandStartBO> seatsAtStart, String nickname) {
        if (room == null || seatsAtStart == null || nickname == null) {
            return 0;
        }
        for (DpObservedSeatAtHandStartBO s : seatsAtStart) {
            if (s == null) {
                continue;
            }
            if (!nickname.equals(s.nickname)) {
                continue;
            }
            if (s.blind == 1) {
                return room.getSmallBlindChips();
            }
            if (s.blind == 2) {
                return room.getBigBlindChips();
            }
            return 0;
        }
        return 0;
    }
}
