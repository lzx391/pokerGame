/** 牌型说明弹窗展示用（从大到小） */
export const HAND_RANK_REFERENCE = [
  { name: '皇家同花顺', cards: ['hearts_A', 'hearts_K', 'hearts_Q', 'hearts_J', 'hearts_10'] },
  { name: '同花顺', cards: ['hearts_9', 'hearts_8', 'hearts_7', 'hearts_6', 'hearts_5'] },
  { name: '四条', cards: ['hearts_A', 'spades_A', 'diamonds_A', 'clubs_A', 'hearts_2'] },
  { name: '葫芦', cards: ['hearts_A', 'spades_A', 'diamonds_A', 'hearts_K', 'spades_K'] },
  { name: '同花', cards: ['hearts_A', 'hearts_J', 'hearts_9', 'hearts_6', 'hearts_2'] },
  { name: '顺子', cards: ['hearts_10', 'spades_9', 'diamonds_8', 'clubs_7', 'hearts_6'] },
  { name: '三条', cards: ['hearts_A', 'spades_A', 'diamonds_A', 'hearts_K', 'clubs_2'] },
  { name: '两对', cards: ['hearts_A', 'spades_A', 'hearts_K', 'diamonds_K', 'clubs_2'] },
  { name: '一对', cards: ['hearts_A', 'spades_A', 'hearts_K', 'diamonds_Q', 'clubs_2'] },
  { name: '高牌', cards: ['hearts_A', 'spades_K', 'diamonds_Q', 'clubs_J', 'hearts_9'] }
]
