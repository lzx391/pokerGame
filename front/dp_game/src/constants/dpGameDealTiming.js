/** 发牌节奏：与公共牌飞入 stagger 一致（GameCommunityCards） */
export var DP_DEAL_STAGGER_MS = 350

/** default 等主题：扫描飞入结束后再翻牌 */
export var DP_COMMUNITY_FLIP_DELAY_MS = 520
export var DP_COMMUNITY_FLIP_DURATION_MS = 480

/** retro8bit：磷光脉冲消退起点（dp-retro-deal-afterglow 70% × 0.5s） */
export var DP_RETRO_COMMUNITY_REVEAL_MS = 350
/** retro8bit：消退显现时长（脉冲 70%→100%） */
export var DP_RETRO_COMMUNITY_REVEAL_DURATION_MS = 150

/** retro8bit 新一手/新发公共牌：全场同时飞入，无座位与张数错开 */
export function dealStaggerMsForTheme(gameUiTheme) {
  return gameUiTheme === 'retro8bit' ? 0 : DP_DEAL_STAGGER_MS
}

/** 公共牌翻开/显现延迟：retro 与磷光 pulse 对齐，其它主题保持原 flip 链 */
export function communityFlipDelayMsForTheme(gameUiTheme, cardIndexOffset) {
  var stagger = dealStaggerMsForTheme(gameUiTheme)
  if (gameUiTheme === 'retro8bit') {
    return DP_RETRO_COMMUNITY_REVEAL_MS + stagger * cardIndexOffset
  }
  return DP_COMMUNITY_FLIP_DELAY_MS + stagger * cardIndexOffset
}

/** 本批新公共牌全部翻开/显现完成的时间点 */
export function communityFlipCompleteMsForTheme(gameUiTheme, numNew) {
  if (numNew <= 0) return 0
  var stagger = dealStaggerMsForTheme(gameUiTheme)
  var lastOffset = numNew - 1
  if (gameUiTheme === 'retro8bit') {
    return DP_RETRO_COMMUNITY_REVEAL_MS + stagger * lastOffset + DP_RETRO_COMMUNITY_REVEAL_DURATION_MS
  }
  return DP_COMMUNITY_FLIP_DELAY_MS + stagger * lastOffset + DP_COMMUNITY_FLIP_DURATION_MS
}
