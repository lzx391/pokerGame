-- V5: 单房间最高净赢由 BC 改为带入倍数（历史 BC 值无法换算，清零后改列类型）
UPDATE dp_user_stats SET largest_room_net = 0;

ALTER TABLE dp_user_stats
    MODIFY COLUMN largest_room_net DECIMAL(10, 2) NOT NULL DEFAULT 0.00
        COMMENT '单房间最高净赢倍数=(离场筹码-累计带入)/累计带入，保留两位小数';
