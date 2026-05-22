/**
 * NPC 风格参数默认值，与后端 StyleProfile.presetTag() / §6.3 一致。
 */
export const NPC_STYLE_TAG_PRESET = {
  vpip: 0.24,
  pfr: 0.76,
  cbetFreq: 0.82,
  bluffFreq: 0.36,
  callStation: 0.18,
  foldToPressure: 0.22
}

/** 弹窗滑条字段：大白话标签 + 技术副标题 */
export const CUSTOM_NPC_STYLE_FIELDS = [
  { key: 'vpip', label: '有多爱进场看牌', hint: 'vpip' },
  { key: 'pfr', label: '翻前爱不爱加注', hint: 'pfr' },
  { key: 'cbetFreq', label: '翻后爱不爱继续下注', hint: 'cbetFreq' },
  { key: 'bluffFreq', label: '牌不好也爱唬人', hint: 'bluffFreq' },
  { key: 'callStation', label: '爱跟注不爱弃牌', hint: 'callStation' },
  { key: 'foldToPressure', label: '一被施压就想跑', hint: 'foldToPressure' }
]

export function cloneNpcStyleProfile (src) {
  var base = src || NPC_STYLE_TAG_PRESET
  return {
    vpip: base.vpip,
    pfr: base.pfr,
    cbetFreq: base.cbetFreq,
    bluffFreq: base.bluffFreq,
    callStation: base.callStation,
    foldToPressure: base.foldToPressure
  }
}

export function clampNpcStyle01 (v) {
  var n = Number(v)
  if (isNaN(n)) return 0
  return Math.max(0, Math.min(1, Math.round(n * 100) / 100))
}

export function clampNpcStyleProfile (profile) {
  var out = cloneNpcStyleProfile(profile)
  var keys = ['vpip', 'pfr', 'cbetFreq', 'bluffFreq', 'callStation', 'foldToPressure']
  for (var i = 0; i < keys.length; i++) {
    out[keys[i]] = clampNpcStyle01(out[keys[i]])
  }
  return out
}
