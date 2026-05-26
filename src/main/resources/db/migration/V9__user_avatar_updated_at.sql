ALTER TABLE dp_user
    ADD COLUMN avatar_updated_at DATETIME(3) NULL COMMENT '头像最后更新时间';

UPDATE dp_user
SET avatar_updated_at = CURRENT_TIMESTAMP(3)
WHERE avatar_url IS NOT NULL
  AND avatar_updated_at IS NULL;
