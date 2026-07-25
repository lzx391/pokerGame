/**
 * 对局顶栏文案：retro8bit 用英文像素字；其它主题仍用中文（见 GameTopBar 模板）。
 */

export function isRetroTopBarEnglish(theme) {
  return theme === 'retro8bit'
}

var STAGE_EN = Object.freeze({
  preflop: 'PREFLOP',
  flop: 'FLOP',
  /** 后端 stage=turn；8bit 顶栏对齐「半决赛」 */
  turn: 'SEMIFINAL',
  /** 后端 stage=river；8bit 顶栏对齐「决赛圈」 */
  river: 'FINAL',
  showdown: 'SHOWDOWN',
  settled: 'SETTLED'
})

var LABEL_ZH = Object.freeze({
  room: '房间',
  phase: '阶段',
  pot: '小鱼干池',
  toCall: '需对齐',
  stack: '持有',
  invested: '已消耗'
})

var LABEL_EN = Object.freeze({
  room: 'ROOM',
  phase: 'PHASE',
  pot: 'POT',
  toCall: 'TO CALL',
  stack: 'STACK',
  invested: 'INVESTED'
})

/** @param {'room'|'phase'|'pot'|'toCall'|'stack'|'invested'} key */
export function formatTopBarLabel(theme, key) {
  if (isRetroTopBarEnglish(theme)) {
    return LABEL_EN[key] || LABEL_ZH[key] || key
  }
  return LABEL_ZH[key] || key
}

export function dpTopBarStageLabel(theme, stageKey, stageLabelZh) {
  if (!isRetroTopBarEnglish(theme)) return stageLabelZh || ''
  if (stageKey == null || stageKey === '') return stageLabelZh || ''
  var k = String(stageKey)
  return STAGE_EN[k] || (stageLabelZh ? String(stageLabelZh).toUpperCase() : k.toUpperCase())
}

var HERO_SECONDARY_ZH_TO_EN = Object.freeze({
  本轮: 'BET',
  还需补: 'TO CALL'
})

export function dpTopBarHeroSecondaryLabel(theme, labelZh) {
  if (!isRetroTopBarEnglish(theme)) return labelZh
  return HERO_SECONDARY_ZH_TO_EN[labelZh] || 'BET'
}

export function dpTopBarHeroEconomyAria(theme, chips, secondaryLabelZh, secondaryValue, carryIn) {
  if (!isRetroTopBarEnglish(theme)) {
    return (
        '剩余小鱼干 ' +
        chips +
        '，' +
        secondaryLabelZh +
        ' ' +
        secondaryValue +
        '，已消耗 ' +
        carryIn
    )
  }
  var sec = dpTopBarHeroSecondaryLabel(theme, secondaryLabelZh)
  return 'STACK ' + chips + ', ' + sec + ' ' + secondaryValue + ', INVESTED ' + carryIn
}
