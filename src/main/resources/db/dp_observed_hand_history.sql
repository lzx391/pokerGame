-- =============================================================================
-- Shark 观测牌谱持久化（与 Java ObservedHandRecord 一一对应）
-- 要求：MySQL 8.0+（使用 JSON 类型；5.7 也支持 JSON 可同样执行）
--
-- 设计说明：
--   * 玩家标识：本项目中进房即用「用户昵称」作为 DpPlayer.nickname，且业务上昵称全局唯一，
--     故牌谱里所有人物键（actorNickname、dealer、eligibleNicknames、holeCardsAtEnd、netChipsChange 等）
--     均使用昵称字符串，不依赖单独 numeric user_id。若日后引入账号主键，可在 payload 中增字段或另表关联，
--     不必改动本表主键逻辑。
--   * 热字段（room_id、时间、hand_seed）便于按房间/时间分页查询；
--   * payload_json 存整手快照（座位、每街公共牌、全员行动含弃牌、边池、洞牌、盈亏），
--     与当前内存结构对齐，避免多张关联表与 ORM 来回映射。
--   * 洞牌在服务端视角是「全知」数据，勿对前端/日志原样暴露；持久化注意权限与脱敏策略。
--
-- 若使用 PostgreSQL：将 JSON 改为 JSONB，AUTO_INCREMENT 改为 SERIAL/BIGSERIAL，
-- 索引写法改为 CREATE INDEX ... ON ... USING gin (payload_json) 等按需调整。
-- =============================================================================

CREATE TABLE IF NOT EXISTS dp_observed_hand_history (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    room_id             VARCHAR(64)     NOT NULL COMMENT '房间 ID，对应 DpRoom.roomId',
    hand_seed           BIGINT          NOT NULL COMMENT '本手种子，对应 DpRoom.currentHandSeed',
    started_at_ms       BIGINT          NOT NULL COMMENT '本手开始时间（毫秒时间戳）',
    ended_at_ms         BIGINT          NOT NULL COMMENT '本手结束/归档时间（毫秒时间戳）',
    small_blind_chips   INT             NOT NULL DEFAULT 0 COMMENT '小盲筹码',
    big_blind_chips     INT             NOT NULL DEFAULT 0 COMMENT '大盲筹码',
    dealer_nickname     VARCHAR(128)    NOT NULL DEFAULT '' COMMENT '庄家昵称',
    main_pot_before_settlement INT      NOT NULL DEFAULT 0 COMMENT '结算前主池总额（与 Java 字段一致）',
    payload_version     SMALLINT        NOT NULL DEFAULT 1 COMMENT 'payload_json 结构版本，便于以后演进',
    payload_json        JSON            NOT NULL COMMENT '整手快照：seats、boards、actions、pots、holes、netChips 等',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_room_hand_seed (room_id, hand_seed),
    KEY idx_room_ended (room_id, ended_at_ms DESC),
    KEY idx_room_started (room_id, started_at_ms DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='BOT_Shark 在场时归档的完整牌谱（服务端全知，含弃牌者动作与洞牌）';


-- -----------------------------------------------------------------------------
-- payload_json 推荐结构（与 com.example...DpNpcSharkObservedHandHistory.ObservedHandRecord 对齐）
-- 应用层可用 ObjectMapper 序列化/反序列化；枚举 ActionType 存 name() 字符串。
-- -----------------------------------------------------------------------------
-- {
--   "seatsAtStart": [ { "seatIndex": 0, "nickname": "...", "blind": 0, "chipsAfterBlinds": 500 } ],
--   "boardsByStreet": [ { "stage": "preflop", "communityCards": [] }, { "stage": "flop", "communityCards": ["..."] } ],
--   "actions": [
--     { "tsMs": 0, "stage": "preflop", "actorNickname": "...", "type": "FOLD",
--       "amount": 0, "betToCallBefore": 10, "actorBetBefore": 0, "raiseLevelAfter": 1, "potBefore": 15 }
--   ],
--   "potsBeforeSettlement": [ { "amount": 100, "eligibleNicknames": ["a","b"] } ],
--   "holeCardsAtEnd": { "BOT_Shark": ["suit_rank", "suit_rank"] },
--   "netChipsChange": { "BOT_Shark": 50, "Player1": -50 }
-- }
-- -----------------------------------------------------------------------------


-- 按「某昵称」跨房间查其参与过的牌谱（依赖 payload JSON；MySQL 8 可用多值索引或应用层拉取后过滤）
-- 若此类查询极多，可另建 dp_player_hand_participation(nickname, hand_history_id) 冗余表。
--
-- 按房间取「最近 N 手」用于启动时灌入内存（示例，N=10 时在应用里拼 LIMIT）
-- SELECT id, room_id, hand_seed, started_at_ms, ended_at_ms,
--        small_blind_chips, big_blind_chips, dealer_nickname,
--        main_pot_before_settlement, payload_version, payload_json
-- FROM dp_observed_hand_history
-- WHERE room_id = ?
-- ORDER BY ended_at_ms DESC
-- LIMIT 10;
