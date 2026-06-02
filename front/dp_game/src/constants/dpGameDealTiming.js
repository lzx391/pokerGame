/** 发牌节奏：与公共牌飞入 stagger 一致（GameCommunityCards） */
export var DP_DEAL_STAGGER_MS = 350

/** retro8bit 新一手/新发公共牌：全场同时飞入，无座位与张数错开 */
export function dealStaggerMsForTheme(gameUiTheme) {
  return gameUiTheme === 'retro8bit' ? 0 : DP_DEAL_STAGGER_MS
}
