-- 天灾：收紧「转河连追」语义，排除翻后已 4-to-flush 等强听牌场景
UPDATE dp_achievement
SET description = '翻牌两对及以上领先对手高牌，转牌仍不输，却被对手转河连续成花或成顺反超'
WHERE code = 'natural_disaster';
