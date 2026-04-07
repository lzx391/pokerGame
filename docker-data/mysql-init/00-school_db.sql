-- 首次初始化数据卷时由 MySQL 官方镜像执行（仅 datadir 为空时跑一遍）。
-- 建表脚本单一来源：仓库内 src/main/resources/db/（见 compose 中的 /docker-schema 挂载）。
USE school_db;
SOURCE /docker-schema/dp_user.sql;
SOURCE /docker-schema/dp_observed_hand_history.sql;
SOURCE /docker-schema/dp_observed_hand_participant.sql;
SOURCE /docker-schema/dp_shark_opponent_profile.sql;
SOURCE /docker-schema/dp_music_track.sql;
