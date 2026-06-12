-- GitHub OAuth：唯一 github_id；OAuth 账号可无本地密码
ALTER TABLE dp_user
    ADD COLUMN github_id BIGINT NULL DEFAULT NULL COMMENT 'GitHub 用户 id，OAuth 账号唯一键';

CREATE UNIQUE INDEX uk_dp_user_github_id ON dp_user (github_id);

ALTER TABLE dp_user
    MODIFY COLUMN password VARCHAR(64) NULL DEFAULT NULL COMMENT 'bcrypt；OAuth 账号可为 NULL';
