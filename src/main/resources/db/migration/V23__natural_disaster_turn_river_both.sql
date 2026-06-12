-- 天灾：强调转牌与河牌须各自参与终局成牌（顺/花/葫芦等连追）
UPDATE dp_achievement
SET description = '翻牌两对及以上领先对手弱牌，转牌仍不输，却被对手转河各补一张连续成花、成顺或葫芦反超'
WHERE code = 'natural_disaster';
