-- 局内聊天：房内 id 为每房 1,2,3…，全局单列主键会导致多房摘房 INSERT 冲突。
-- 改为 (room_id, id) 联合主键，与内存缓冲及 ix_room_chat_room_id 语义一致。

-- 若迁移失败并提示 Duplicate entry on PRIMARY (room_id, id)：
--   SELECT room_id, id, COUNT(*) c FROM dp_room_chat_message GROUP BY room_id, id HAVING c > 1;
--   清理重复行后再执行本脚本。

ALTER TABLE `dp_room_chat_message`
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (`room_id`, `id`);
