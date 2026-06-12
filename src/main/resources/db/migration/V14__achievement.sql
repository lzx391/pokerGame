-- Flyway V14: 成就定义与用户解锁记录

CREATE TABLE dp_achievement (
    id           INT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '成就主键',
    code         VARCHAR(64)  NOT NULL COMMENT '成就唯一编码',
    title        VARCHAR(128) NOT NULL COMMENT '成就标题',
    description  VARCHAR(512) NOT NULL COMMENT '成就说明',
    sort_order   INT          NOT NULL DEFAULT 0 COMMENT '展示排序（升序）',
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_dp_achievement_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='成就定义表';

CREATE TABLE dp_user_achievement (
    user_id        INT       NOT NULL COMMENT 'dp_user.id',
    achievement_id INT       NOT NULL COMMENT 'dp_achievement.id',
    unlocked       TINYINT   NOT NULL DEFAULT 0 COMMENT '0 未解锁 1 已解锁',
    unlocked_at    TIMESTAMP NULL DEFAULT NULL COMMENT '解锁时间',
    PRIMARY KEY (user_id, achievement_id),
    KEY idx_dp_user_achievement_achievement (achievement_id),
    CONSTRAINT fk_dp_user_achievement_user FOREIGN KEY (user_id) REFERENCES dp_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_dp_user_achievement_achievement FOREIGN KEY (achievement_id) REFERENCES dp_achievement (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='用户成就解锁记录';

INSERT INTO dp_achievement (code, title, description, sort_order)
VALUES ('quad_nightmare', '四条噩梦', '指摊牌时该玩家四条撞上了更大的同花顺', 1);
