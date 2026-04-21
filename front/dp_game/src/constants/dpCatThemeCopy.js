/**
 * 猫咪派对主题：仅用于前端展示文案，不改变接口字段名与请求体。
 */

export const DP_CAT_TUTORIAL_SESSION_KEY = 'dp_show_cat_tutorial'
export const DP_CAT_TUTORIAL_DISMISS_KEY = 'dp_cat_tutorial_dismissed'

export function flagCatTutorialAfterLogin() {
  try {
    sessionStorage.setItem(DP_CAT_TUTORIAL_SESSION_KEY, '1')
  } catch (e) {
    /* ignore */
  }
}

export function peekCatTutorialRequested() {
  try {
    return sessionStorage.getItem(DP_CAT_TUTORIAL_SESSION_KEY) === '1'
  } catch (e) {
    return false
  }
}

export function clearCatTutorialSessionFlag() {
  try {
    sessionStorage.removeItem(DP_CAT_TUTORIAL_SESSION_KEY)
  } catch (e) {
    /* ignore */
  }
}

export function isCatTutorialDismissedPermanently() {
  try {
    return localStorage.getItem(DP_CAT_TUTORIAL_DISMISS_KEY) === '1'
  } catch (e) {
    return false
  }
}

export function setCatTutorialDismissedPermanently() {
  try {
    localStorage.setItem(DP_CAT_TUTORIAL_DISMISS_KEY, '1')
  } catch (e) {
    /* ignore */
  }
}

/** 紧凑台呢标：发牌猫 */
export const DEALER_BADGE_CHAR = '发'

export const CAT_COPY = Object.freeze({
  chips: '小鱼干',
  smallBlind: '小猫',
  bigBlind: '大猫',
  smallBlindAbbr: 'SC',
  bigBlindAbbr: 'BC',
  dealer: '发牌猫',
  pot: '小鱼干池',
  needMatch: '需对齐',
  /** 座位卡上「后手」位置用的短标签 */
  stackShort: '小鱼干',
  /** 与「本轮 bet」搭配 */
  roundShort: '本轮',
  anteLine: '开局注'
})
