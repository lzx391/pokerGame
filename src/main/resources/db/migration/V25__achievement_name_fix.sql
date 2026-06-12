-- 天灾：强调转牌与河牌须各自参与终局成牌（顺/花/葫芦等连追）
UPDATE dp_achievement
SET description = '翻牌两对及以上领先对手弱牌，被对手连追两张反超'
WHERE code = 'natural_disaster';

UPDATE dp_achievement
SET description = '指摊牌时该玩家四条撞上了更大的火箭'
WHERE code = 'quad_nightmare';

UPDATE dp_achievement
SET description = '与对手同点数杂色底牌下但你凭命中四张同花胜出'
WHERE code = 'mirror_duel';

UPDATE dp_achievement
SET description = '摊牌赢下底池，且使四个及以上所有摊牌对手筹码归零'
WHERE code = 'sweep_all';