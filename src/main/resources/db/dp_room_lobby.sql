-- 大厅房间列表（与内存 roomMap 同步；分页 + Redis 版本键见 DpRoomHallService）
CREATE TABLE IF NOT EXISTS dp_room_lobby (
    room_id              VARCHAR(32)     NOT NULL COMMENT '房间 ID，对应 DpRoom.roomId',
    owner_nickname       VARCHAR(128)    NOT NULL COMMENT '房主昵称',
    room_status          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '房间状态：0 游戏中，1 已结束',
    player_count         INT             NOT NULL DEFAULT 0 COMMENT '桌上非僵尸位人数（与 getAllRooms2 一致）',
    small_blind_chips    INT             NOT NULL,
    big_blind_chips      INT             NOT NULL,
    starting_stack_bb    INT             NOT NULL DEFAULT 50,
    password_protected   TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '1 需要密码',
    max_seat_count       INT             NOT NULL DEFAULT 9 COMMENT '一桌人数上限（2～9），与内存房间 maxSeatCount 一致',
    created_at           DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '首次创建时间，用于排序',
    PRIMARY KEY (room_id),
    KEY idx_lobby_status_created (room_status, created_at DESC),
    KEY idx_lobby_created (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='DP 大厅房间摘要';
