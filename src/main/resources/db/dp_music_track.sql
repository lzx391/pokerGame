-- 局内 BGM 曲库元数据：音频文件落在服务器磁盘（如 DPGameFiles/music/），本表仅存展示名与访问路径等。
-- 在已存在的 school_db 上执行；不设外键，与项目其它表一致。

CREATE TABLE IF NOT EXISTS `dp_music_track` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `stored_filename` varchar(512) NOT NULL COMMENT '磁盘文件名（建议含 uuid 前缀，避免重名）',
    `display_name` varchar(256) NOT NULL COMMENT '界面展示标题',
    `web_path` varchar(512) NOT NULL COMMENT '浏览器访问路径，如 /music/xxx.mp3',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '列表排序，越大越靠前或反之由业务约定',
    `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '1=上架 0=下架（不删文件）',
    `uploader_user_id` int DEFAULT NULL COMMENT '可选：上传人 dp_user.id',
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    PRIMARY KEY (`id`),
    KEY `idx_dp_music_track_enabled_sort` (`enabled`, `sort_order`),
    KEY `idx_dp_music_track_web_path` (`web_path`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='DP 对局 BGM 曲库（元数据）';
