package com.example.mgdemoplus.common.bo;

import com.example.mgdemoplus.common.entity.DpPlayer;
import com.example.mgdemoplus.common.entity.DpPlayerStats;
import com.example.mgdemoplus.common.entity.DpPot;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DpRoomBO {
    private String roomId;
    private String owner;
    private List<DpPlayer> players = new ArrayList<>();
    private boolean isPlaying = false;
    /**
     * 观众席：当前在房间内但不在牌桌上的昵称列表
     */
    private List<String> spectators = new ArrayList<>();

    /**
     * 观众席真人在线时间戳（毫秒）：由 HTTP 心跳与对局页 WebSocket 建连刷新，用于剔除长时间无保活的观众；不下发 JSON。
     */
    @JsonIgnore
    private final Map<String, Long> spectatorLastPresenceMs = new ConcurrentHashMap<>();

    // 德扑核心
    private String currentStage = "preflop";
    private List<String> communityCards = new ArrayList<>();
    private List<String> deck = new ArrayList<>();
    private int pot = 0;
    private int currentBetToCall = 0;
    /**
     * 当前下注轮中，自首次主动下注以来的“加注层级计数”：
     * 1 = open（第一次下注），2 = 第一次 re-raise（3bet），3 = 第二次 re-raise（4bet），>=4 视为进入 all-in/平跟/弃牌的终局博弈阶段。
     */
    private int raiseLevel = 0;
    /**
     * 当前下注轮内「上一次加注的增量」：标准 NLHE 最小再加注额 = 该值。
     * 新一圈（含发牌后）重置为大盲；翻前 posting 后亦为大盲（面对 BB 时最小 open 总注至少 2BB）。
     * 有人把档位从 prevCall 抬到 totalBet 时，更新为 (totalBet - prevCall)。
     */
    private int lastRaiseIncrement = 0;

    /** 默认小盲/大盲（与旧版全局常量一致）；建房时可覆盖。 */
    public static final int DEFAULT_SMALL_BLIND_CHIPS = 5;
    public static final int DEFAULT_BIG_BLIND_CHIPS = 10;
    /** 默认每人带入深度（用大盲计）：{@code startingChips = bigBlindChips × startingStackBb} */
    public static final int DEFAULT_STARTING_STACK_BB = 50;

    /** 一桌最多玩家数（建房时指定，合法区间 {@value #MIN_SEAT_COUNT}～{@value #MAX_SEAT_COUNT}）。 */
    public static final int MIN_SEAT_COUNT = 2;
    public static final int MAX_SEAT_COUNT = 9;
    public static final int DEFAULT_MAX_SEAT_COUNT = MAX_SEAT_COUNT;

    /** 本桌小盲/大盲；逻辑与下注校验均使用实例值，不再使用静态全局盲注。 */
    private int smallBlindChips = DEFAULT_SMALL_BLIND_CHIPS;
    private int bigBlindChips = DEFAULT_BIG_BLIND_CHIPS;
    /**
     * 每人初始/补码对应的「大盲倍数」：实际筹码 = {@code bigBlindChips × startingStackBb}，写入 {@link #startingChips}。
     */
    private int startingStackBb = DEFAULT_STARTING_STACK_BB;
    /**
     * 上桌与补码时的目标筹码（整数），等于 {@code bigBlindChips × startingStackBb}（建房时计算）。
     */
    private int startingChips = DEFAULT_BIG_BLIND_CHIPS * DEFAULT_STARTING_STACK_BB;
    /**
     * 进房密码（仅内存）；空或 null 表示不设密码。不下发 JSON。
     */
    private String roomPassword = "";

    /**
     * 人数上限：当前桌玩家数与「下一局」预约总人数不得超过该值（创建房间时写入）。
     */
    private int maxSeatCount = DEFAULT_MAX_SEAT_COUNT;

    // 行动顺序
    private int lastDealerIndex =0;
    private int currentActorIndex = -1;
    private long lastActionTime = 0;
    private static final int ACTION_TIMEOUT = 30000; // 30秒
    private static final int HEART_TIMEOUT = 20000; // 20秒
    private List<DpPot> pots = new ArrayList<>();
    // 等待在下一局加入的玩家昵称列表（当前局仅旁观）
    private List<String> waitNextHand = new ArrayList<>();

    /**
     * 结算后准备阶段的截止时间戳（毫秒）。为 0 表示当前没有准备倒计时。
     */
    private long readyDeadline = 0L;

    /**
     * 玩家行为统计：用于规则 NPC 根据最近几手的表现
     * 粗略揣测每个玩家是紧/松、凶/弱，从而调整跟注/加注策略。
     * key 为玩家昵称，value 为该玩家的统计信息。
     */
    private Map<String, DpPlayerStats> playerStatsMap = new HashMap<>();

    /**
     * 当前这手牌的随机种子：用于让机器人在同一局内的随机行为可复现、跨多次调用共享。
     * 由服务层在 newHand 时生成。
     */
    private long currentHandSeed;

    /**
     * 最近一次 {@code autoSettle} 后，场上筹码并列最高的玩家昵称（未结算过为空列表）。
     * 供前端座位底牌光效使用，不持久化。
     */
    private List<String> chipLeaderNicknames = new ArrayList<>();

    /**
     * 上一手进入结算时是否至少两名未弃牌玩家参与比牌（摊牌公开底牌）。
     * 单人收池时为 false，用于对外 JSON 时隐藏赢家底牌，避免“未摊牌却被看见”。
     */
    private boolean lastHandHoleCardsPublic = false;

    /**
     * 昵称 → dp_user.id，由进房/预约下一局等接口在传入 userId 且校验通过后写入；
     * 用于下一局从 wait 列表拉人上桌时补全 {@link DpPlayer#setDpUserId}，不参与房间 JSON。
     */
    private final ConcurrentHashMap<String, Integer> registeredDpUserIdByNickname = new ConcurrentHashMap<>();

    /**
     * 本桌机器人昵称共用递增序号（各档位与大模型 BOT 占位均占用）；房间内唯一，重启房间后归零。
     * 初始 0，首次分配从 1 起。
     */
    @JsonIgnore
    private final AtomicInteger botNicknameSeq = new AtomicInteger(0);

    /**
     * 连续占用 {@code count} 个序号。
     *
     * @param count 须为正整数
     * @return 本批的第一个序号（含），后续为 {@code first+1 .. first+count-1}
     */
    public int allocateBotNicknameSeqBatch(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        return botNicknameSeq.addAndGet(count) - count + 1;
    }

    public void putRegisteredDpUserId(String nickname, Integer dpUserId) {
        if (nickname == null || nickname.isEmpty() || dpUserId == null) {
            return;
        }
        registeredDpUserIdByNickname.put(nickname, dpUserId);
    }

    public Integer getRegisteredDpUserId(String nickname) {
        if (nickname == null) {
            return null;
        }
        return registeredDpUserIdByNickname.get(nickname);
    }

    // getter & setter
    public String getRoomId() { return roomId; }
    public void setRoomId(String roomId) { this.roomId = roomId; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
    public List<DpPlayer> getPlayers() { return players; }
    public void setPlayers(List<DpPlayer> players) { this.players = players; }
    public List<String> getSpectators() { return spectators; }
    public void setSpectators(List<String> spectators) { this.spectators = spectators; }

    public void touchSpectatorPresence(String nickname, long timeMs) {
        if (nickname != null && !nickname.isEmpty()) {
            spectatorLastPresenceMs.put(nickname, timeMs);
        }
    }

    public void removeSpectatorPresence(String nickname) {
        if (nickname != null) {
            spectatorLastPresenceMs.remove(nickname);
        }
    }

    public Long getSpectatorLastPresenceMs(String nickname) {
        return nickname == null ? null : spectatorLastPresenceMs.get(nickname);
    }
    public boolean isPlaying() { return isPlaying; }
    public void setPlaying(boolean playing) { isPlaying = playing; }
    public String getCurrentStage() { return currentStage; }
    public void setCurrentStage(String currentStage) { this.currentStage = currentStage; }
    public List<String> getCommunityCards() { return communityCards; }
    public void setCommunityCards(List<String> communityCards) { this.communityCards = communityCards; }
    /** 剩余牌序不得下发给客户端，否则可推算未发公共牌与他人底牌。 */
    @JsonIgnore
    public List<String> getDeck() { return deck; }
    public void setDeck(List<String> deck) { this.deck = deck; }
    public int getPot() { return pot; }
    public void setPot(int pot) { this.pot = pot; }
    public int getCurrentBetToCall() { return currentBetToCall; }
    public void setCurrentBetToCall(int currentBetToCall) { this.currentBetToCall = currentBetToCall; }
    public int getRaiseLevel() { return raiseLevel; }
    public void setRaiseLevel(int raiseLevel) { this.raiseLevel = raiseLevel; }
    public int getLastRaiseIncrement() { return lastRaiseIncrement; }
    public void setLastRaiseIncrement(int lastRaiseIncrement) { this.lastRaiseIncrement = lastRaiseIncrement; }
    public int getCurrentActorIndex() { return currentActorIndex; }
    public void setCurrentActorIndex(int currentActorIndex) { this.currentActorIndex = currentActorIndex; }
    public long getLastActionTime() { return lastActionTime; }
    public void setLastActionTime(long lastActionTime) { this.lastActionTime = lastActionTime; }
    public static int getActionTimeout() { return ACTION_TIMEOUT; }
    public List<DpPot> getPots() { return pots; }
    public void setPots(List<DpPot> pots) { this.pots = pots; }
    public List<String> getWaitNextHand() { return waitNextHand; }
    public void setWaitNextHand(List<String> waitNextHand) { this.waitNextHand = waitNextHand; }
    public long getReadyDeadline() { return readyDeadline; }
    public void setReadyDeadline(long readyDeadline) { this.readyDeadline = readyDeadline; }
    public static int getHeartTimeout() { return HEART_TIMEOUT; }

    public int getSmallBlindChips() {
        return smallBlindChips;
    }

    public void setSmallBlindChips(int smallBlindChips) {
        this.smallBlindChips = smallBlindChips;
    }

    public int getBigBlindChips() {
        return bigBlindChips;
    }

    public void setBigBlindChips(int bigBlindChips) {
        this.bigBlindChips = bigBlindChips;
    }

    public int getStartingChips() {
        return startingChips;
    }

    public void setStartingChips(int startingChips) {
        this.startingChips = startingChips;
    }

    public int getStartingStackBb() {
        return startingStackBb;
    }

    public void setStartingStackBb(int startingStackBb) {
        this.startingStackBb = startingStackBb;
    }

    public boolean isPasswordProtected() {
        return roomPassword != null && !roomPassword.isEmpty();
    }

    /** 入参比对用；不序列化给客户端。 */
    @JsonIgnore
    public String getRoomPassword() {
        return roomPassword;
    }

    public void setRoomPassword(String roomPassword) {
        this.roomPassword = roomPassword == null ? "" : roomPassword.trim();
    }

    public int getMaxSeatCount() {
        return maxSeatCount;
    }

    public void setMaxSeatCount(int maxSeatCount) {
        this.maxSeatCount = maxSeatCount;
    }

    /** 加入房间时校验；未设密码时恒为 true。 */
    public boolean matchesRoomPassword(String attempt) {
        if (!isPasswordProtected()) {
            return true;
        }
        if (attempt == null) {
            return false;
        }
        return roomPassword.equals(attempt.trim());
    }

    public int getLastDealerIndex() {
        return lastDealerIndex;
    }

    public void setLastDealerIndex(int lastDealerIndex) {
        this.lastDealerIndex = lastDealerIndex;
    }

    public Map<String, DpPlayerStats> getPlayerStatsMap() {
        return playerStatsMap;
    }

    public void setPlayerStatsMap(Map<String, DpPlayerStats> playerStatsMap) {
        this.playerStatsMap = playerStatsMap;
    }

    public long getCurrentHandSeed() {
        return currentHandSeed;
    }

    public void setCurrentHandSeed(long currentHandSeed) {
        this.currentHandSeed = currentHandSeed;
    }

    public List<String> getChipLeaderNicknames() {
        return chipLeaderNicknames;
    }

    public void setChipLeaderNicknames(List<String> chipLeaderNicknames) {
        this.chipLeaderNicknames = chipLeaderNicknames != null ? chipLeaderNicknames : new ArrayList<>();
    }

    public boolean isLastHandHoleCardsPublic() {
        return lastHandHoleCardsPublic;
    }

    public void setLastHandHoleCardsPublic(boolean lastHandHoleCardsPublic) {
        this.lastHandHoleCardsPublic = lastHandHoleCardsPublic;
    }

    /**
     * 本 action 要构成合法加注（非短全下）时，玩家至少需要再下的筹码数。
     * 开池（当前无人下注）时等价于至少补到一个大盲的本轮投入。
     */
    public int minChipsToLegalRaise(DpPlayer p) {
        if (p == null) {
            return 0;
        }
        int bb = bigBlindChips;
        int call = Math.max(0, currentBetToCall - p.getBet());
        int inc = lastRaiseIncrement > 0 ? lastRaiseIncrement : bb;
        return Math.max(0, call + inc);
    }
}