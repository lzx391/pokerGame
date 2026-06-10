-- Flyway V20: 成就语义与文案更新（27终结者 / 王座更迭 / 牌桌消消乐 / 天灾 / 灵魂阅读者 / 横扫一切）

UPDATE dp_achievement
SET description = '在 6 人及以上场次，以 2-7 杂色手牌赢下一局'
WHERE code = 'twenty_seven_terminator';

UPDATE dp_achievement
SET description = '摊牌时用更大的火箭击败对手的火箭'
WHERE code = 'throne_usurper';

UPDATE dp_achievement
SET description = '在 6 人及以上场次，迫使所有其他玩家弃牌'
WHERE code = 'table_clear';

UPDATE dp_achievement
SET description = '翻牌两对及以上领先，却被对手转牌与河牌连续反超'
WHERE code = 'natural_disaster';

UPDATE dp_achievement
SET description = '作为本手净赢家，以高牌或底对摊牌击败对手的诈唬'
WHERE code = 'soul_reader';

UPDATE dp_achievement
SET description = '摊牌赢下底池，且所有摊牌对手筹码归零'
WHERE code = 'sweep_all';
