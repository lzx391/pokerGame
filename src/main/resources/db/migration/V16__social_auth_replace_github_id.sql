-- V16: 用 dp_user_social_auth 关联表替代 V13 的单字段 github_id 方案
-- 支持多 provider（github/wechat/qq/...），一个用户可绑多个渠道

-- 1. 安全清理 V13 遗留（用存储过程判断列是否存在，避免 V13 未执行时报错）
SET @col_exists = (SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'dp_user' AND column_name = 'github_id');
SET @sql_drop = IF(@col_exists > 0,
    'ALTER TABLE dp_user DROP INDEX uk_dp_user_github_id, DROP COLUMN github_id',
    'SELECT 1 AS v16_noop');
PREPARE stmt FROM @sql_drop;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 确保 password 可空（V13 已做，此处幂等）
ALTER TABLE dp_user MODIFY COLUMN password VARCHAR(64) NULL DEFAULT NULL COMMENT 'bcrypt；OAuth 账号可为 NULL';

-- 3. 建关联表
CREATE TABLE IF NOT EXISTS dp_user_social_auth (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    user_id     INT          NOT NULL,
    provider    VARCHAR(32)  NOT NULL COMMENT 'github / wechat / qq / google / apple ...',
    open_id     VARCHAR(128) NOT NULL COMMENT '第三方返回的唯一标识（GitHub 为 login 名）',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX uq_provider_open (provider, open_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='第三方登录关联表';
