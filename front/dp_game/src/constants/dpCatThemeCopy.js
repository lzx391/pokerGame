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

/** 对局阶段展示名（后端 stage 不变，仅界面文案） */
export const DP_GAME_STAGE_LABELS = Object.freeze({
  preflop: '翻前圈',
  flop: '翻后圈',
  turn: '半决赛',
  river: '决赛圈',
  showdown: '结算阶段',
  settled: '结算阶段'
})

export function dpGameStageDisplay(stage) {
  if (stage == null || stage === '') return ''
  var s = String(stage)
  return DP_GAME_STAGE_LABELS[s] || s
}

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
  anteLine: '开局鱼干',
  mainPot: '主鱼干池',
  sidePot: '边鱼干池',
  bigBlindFish: '大猫鱼干',
  actionFold: '盖牌',
  actionCall: '跟投',
  actionRaise: '加投',
  actionBet: '投入',
  actionCheck: '观望',
  actionAllIn: '全投',
  blindPositionHint: '小猫/大猫位',
  /** 快捷按钮文案可保留；语义 = 当前小鱼干池的比例 */
  potPresetThird: '⅓池',
  potPresetHalf: '½池',
  potPresetThreeQuarter: '¾池',
  potPresetOneX: '1×池',
  potPresetOneHalf: '1½池',
  potPresetAriaNote: '按小鱼干池比例快捷加投',
  appAuthTitle: '猫咪牌局',
  themePotChipsLabel: '小鱼干池/小鱼干强调',
  themeRaiseBtnLabel: '主操作按钮（加投）',
  themeAllInBtnLabel: '全投按钮',
  loadFailedRetry: '暂时无法加载，请稍后再试',
  handHistoryEmptyHint:
    '暂无记录。若牌谱参与者信息未同步（例如未登录或未能识别昵称），则不会出现在此列表。',
  musicLibraryHint:
    '音频保存在服务器目录，会写入曲库；支持 mp3、m4a、wav、ogg、flac，单文件建议不超过 80MB。',
  musicLibraryNotReady: '曲库未就绪，请确认后端已启动。',
  musicListLoadFailed: '暂时无法加载曲库，请稍后再试'
})

/** 池展示名：0=主鱼干池，>0=边鱼干池 N */
export function dpPotDisplayLabel(potIndex) {
  var i = Number(potIndex)
  if (i === 0) return CAT_COPY.mainPot
  return CAT_COPY.sidePot + ' ' + i
}
