package com.example.mgdemoplus.service.studentImpl;



    import com.example.mgdemoplus.entity.DpPlayer;
    import com.example.mgdemoplus.entity.DpRoom;
    import org.springframework.stereotype.Service;
    import java.util.*;
    import java.util.concurrent.ConcurrentHashMap;

@Service
public class DpRoomServiceImpl {
    private final Map<String, DpRoom> roomMap = new ConcurrentHashMap<>();

    // 创建房间
    public DpRoom createRoom(String ownerNickname) {
        String roomId = UUID.randomUUID().toString().substring(0, 8);
        DpRoom room = new DpRoom();
        room.setRoomId(roomId);
        room.setOwner(ownerNickname);
        room.setPlayers(new ArrayList<>());
        room.setPlaying(false);

        DpPlayer player = new DpPlayer();
        player.setNickname(ownerNickname);
        player.setReady(true);
        room.getPlayers().add(player);

        roomMap.put(roomId, room);
        return room;
    }

    // 获取所有房间
    public List<DpRoom> getAllRooms() {
        return new ArrayList<>(roomMap.values());
    }

    // 加入房间
    public String joinRoom(String roomId, String nickname) {
        DpRoom room = roomMap.get(roomId);
        if (room == null) return "房间不存在";
        if (room.getPlaying()) return "游戏已开始";

        boolean exists = room.getPlayers().stream()
                .anyMatch(p -> p.getNickname().equals(nickname));
        if (exists) return "你已在房间中";

        DpPlayer p = new DpPlayer();
        p.setNickname(nickname);
        p.setReady(false);
        room.getPlayers().add(p);
        return "加入成功";
    }

    // 准备/取消
    public boolean toggleReady(String roomId, String nickname) {
        DpRoom room = roomMap.get(roomId);
        if (room == null || room.getPlaying()) return false;
        for (DpPlayer p : room.getPlayers()) {
            if (p.getNickname().equals(nickname)) {
                p.setReady(!p.isReady());
                return true;
            }
        }
        return false;
    }

    // 退出房间
    public boolean exitRoom(String roomId, String nickname) {
        DpRoom room = roomMap.get(roomId);
        if (room == null) return false;
        if (room.getOwner().equals(nickname)) {
            roomMap.remove(roomId);
            return true;
        }
        return room.getPlayers().removeIf(p -> p.getNickname().equals(nickname));
    }

    // 开始游戏
    public boolean startGame(String roomId, String owner) {
        DpRoom room = roomMap.get(roomId);
        if (room == null || !room.getOwner().equals(owner)) return false;
        room.setPlaying(true);
        room.getPlayers().forEach(p -> {
            p.setChips(50);
            p.setFold(false);
            p.setBet(0);
            p.setCard1(null);
            p.setCard2(null);
        });
        return true;
    }

    // 房主发牌
    public boolean dealCards(String roomId, String owner) {
        DpRoom room = roomMap.get(roomId);
        if (room == null || !room.getOwner().equals(owner)) return false;
        List<String> cards = Arrays.asList(
                "2","3","4","5","6","7","8","9","10",
                "J","Q","K","A"
        );
        Random r = new Random();
        for (DpPlayer p : room.getPlayers()) {
            String c1 = cards.get(r.nextInt(cards.size()));
            String c2 = cards.get(r.nextInt(cards.size()));
            p.setCard1(c1);
            p.setCard2(c2);
        }
        return true;
    }

    // 下注
    public boolean bet(String roomId, String nickname, int bet) {
        DpRoom room = roomMap.get(roomId);
        if (room == null || !room.getPlaying()) return false;
        for (DpPlayer p : room.getPlayers()) {
            if (p.getNickname().equals(nickname)) {
                if (p.getChips() < bet) return false;
                p.setChips(p.getChips() - bet);
                p.setBet(p.getBet() + bet);
                return true;
            }
        }
        return false;
    }

    // 弃牌
    public boolean fold(String roomId, String nickname) {
        DpRoom room = roomMap.get(roomId);
        if (room == null || !room.getPlaying()) return false;
        for (DpPlayer p : room.getPlayers()) {
            if (p.getNickname().equals(nickname)) {
                p.setFold(true);
                return true;
            }
        }
        return false;
    }

    // 房主判赢
    public boolean judgeWin(String roomId, String winner) {
        DpRoom room = roomMap.get(roomId);
        if (room == null || !room.getPlaying()) return false;

        int total = room.getPlayers().stream().mapToInt(DpPlayer::getBet).sum();
        for (DpPlayer p : room.getPlayers()) {
            if (p.getNickname().equals(winner)) {
                p.setChips(p.getChips() + total);
                p.setBet(0);
                p.setCard1(null);
                p.setCard2(null);
                p.setFold(false);
            } else {
                p.setBet(0);
                p.setCard1(null);
                p.setCard2(null);
                p.setFold(false);
            }
        }
        return true;
    }

    // 检查所有人是否准备
    public boolean isAllReady(String roomId) {
        DpRoom room = roomMap.get(roomId);
        if (room == null) return false;
        return room.getPlayers().stream().allMatch(DpPlayer::isReady);
    }
    // 心跳：更新在线时间
    public void heartbeat(String roomId, String nickname) {
        DpRoom room = roomMap.get(roomId);
        if (room == null) return;
        for (DpPlayer p : room.getPlayers()) {
            if (p.getNickname().equals(nickname)) {
                p.setLastHeartBeat(System.currentTimeMillis());
                break;
            }
        }
    }
    // 构造方法里自动启动清理线程
    public DpRoomServiceImpl() {
        // 每3秒清理一次超时玩家
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                for (DpRoom room : roomMap.values()) {
                    Iterator<DpPlayer> iterator = room.getPlayers().iterator();
                    while (iterator.hasNext()) {
                        DpPlayer p = iterator.next();
                        long now = System.currentTimeMillis();
                        // 10秒没心跳 → 踢出
                        if (now - p.getLastHeartBeat() > 10000) {
                            iterator.remove();
                            System.out.println(p.getNickname() + " 超时掉线，已移出房间");
                        }
                    }
                    // 房间没人 → 销毁
                    if (room.getPlayers().isEmpty()) {
                        roomMap.remove(room.getRoomId());
                        System.out.println("房间 " + room.getRoomId() + " 无人，已清空");
                    }
                }
            }
        }, 0, 3000);
    }
}
