-- -----------------------------------------------------------------------------
-- dp_download_asset — 下载中心安装包元数据（对齐 dp_music_track 模式）
-- -----------------------------------------------------------------------------
CREATE TABLE `dp_download_asset` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `stored_filename` varchar(512) NOT NULL COMMENT '磁盘文件名（uuid + 扩展名）',
    `display_name` varchar(256) NOT NULL COMMENT '界面展示名称',
    `web_path` varchar(512) NOT NULL COMMENT '浏览器访问路径，如 /files/xxx.exe',
    `sort_order` int NOT NULL DEFAULT 0 COMMENT '列表排序，越大越靠前',
    `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '1=上架 0=下架（不删文件）',
    `uploader_user_id` int DEFAULT NULL COMMENT '可选：上传人 dp_user.id',
    `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
    `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后修改时间',
    PRIMARY KEY (`id`),
    KEY `idx_dp_download_asset_enabled_sort` (`enabled`, `sort_order`),
    KEY `idx_dp_download_asset_web_path` (`web_path`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='下载中心安装包元数据';
