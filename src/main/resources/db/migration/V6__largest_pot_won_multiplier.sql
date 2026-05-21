-- V6: 单局最高净赢由 BC 改为带入倍数（历史 BC 值无法换算，清零后改列类型）
UPDATE dp_user_stats SET largest_pot_won = 0;

ALTER TABLE dp_user_stats
    MODIFY COLUMN largest_pot_won DECIMAL(10, 2) NOT NULL DEFAULT 0.00
        COMMENT '单局最高净赢倍数=本手净赢筹码/累计带入，保留两位小数';
