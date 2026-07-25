/**
 * TAG 决策 trace：step.data 字段键与枚举值 → 中文展示。
 * 主数据源为 step.data，不解析 message 英文字符串。
 */

/** 决策变量卡字段顺序（已知键优先，其余键按字母序追加） */
var POSTFLOP_SUMMARY_FIELD_ORDER = [
  'position',
  'stage',
  'street',
  'pot',
  'potSize',
  'callAmount',
  'toCall',
  'chips',
  'stack',
  'heroStack',
  'spr',
  'heroSpr',
  'effStackBB',
  'made',
  'draw',
  'board',
  'boardCards',
  'tex',
  'texture',
  'plan',
  'barrels',
  'barrelsLeft',
  'equity',
  'equityEst',
  'potOdds',
  'activeVillains',
  'villainCount',
  'opponents',
  'activePlayers',
  'aggressor',
  'heroHand',
  'holeCards',
  'rangeLevel',
  'spot',
  'raiseLevel'
]

/** 字段键 → 中文标签 */
var FIELD_LABELS = {
  position: '位置',
  stage: '街道',
  street: '街道',
  pot: '底池',
  potSize: '底池',
  callAmount: '跟注',
  toCall: '跟注',
  chips: '筹码',
  stack: '筹码',
  heroStack: '筹码',
  spr: 'SPR',
  heroSpr: 'SPR',
  effStackBB: '有效筹码(BB)',
  made: '成牌',
  draw: '听牌',
  board: '牌面',
  boardCards: '牌面',
  tex: '牌面结构',
  texture: '牌面结构',
  plan: '计划',
  barrels: '持续进攻',
  barrelsLeft: '剩余 barrel',
  equity: '胜率估计',
  equityEst: '胜率估计',
  potOdds: '底池赔率',
  activeVillains: '对手人数',
  villainCount: '对手人数',
  opponents: '对手人数',
  activePlayers: '在场人数',
  aggressor: '主动方',
  heroHand: '手牌',
  holeCards: '手牌',
  rangeLevel: '范围档位',
  spot: '翻前 spot',
  raiseLevel: '加注层级',
  // PROB / EVAL 常见键
  baseFold: '基础弃牌率',
  foldProb: '弃牌概率',
  raiseProb: '加注概率',
  valueBetProb: '价值下注概率',
  roll: '随机 roll',
  random: '随机值',
  inRange: '在范围内',
  commitThreshold: '承诺阈值',
  commitFactor: '承诺系数',
  potFraction: '底池比例',
  callRatio: '跟注占比',
  amount: '金额',
  branch: '分支',
  result: '结果',
  passed: '通过',
  blocked: '被拦截'
}

/** 成牌档 */
var MADE_LABELS = {
  HIGH_CARD: '高牌',
  BOTTOM_PAIR: '底对',
  MIDDLE_PAIR: '中对',
  TOP_PAIR_WEAK_KICKER: '顶对弱踢',
  TOP_PAIR_TOP_KICKER: '顶对强踢',
  TWO_PAIR: '两对',
  TRIPS: '三条',
  STRAIGHT: '顺子',
  FLUSH: '同花',
  FULL_HOUSE: '葫芦',
  QUADS: '四条',
  ROCKET: '火箭对'
}

/** 听牌档 */
var DRAW_LABELS = {
  NONE: '无',
  GUTSHOT: '卡顺',
  OESD: '两头顺',
  FLUSH_DRAW: '同花听',
  COMBO_DRAW: '组合听牌'
}

/** 整手计划 */
var PLAN_LABELS = {
  VALUE: '价值',
  BLUFF: '诈唬',
  POT_CONTROL: '控池',
  GIVE_UP: '放弃'
}

/** 位置 */
var POSITION_LABELS = {
  EARLY: '前位',
  MIDDLE: '中位',
  LATE: '后位',
  BLINDS: '盲位'
}

/** 街道 */
var STAGE_LABELS = {
  preflop: '翻前',
  flop: '翻牌',
  turn: '转牌',
  river: '河牌'
}

/** 牌面结构 */
var TEX_LABELS = {
  wet: '湿润',
  dry: '干燥',
  WET: '湿润',
  DRY: '干燥'
}

/** 翻前 spot（常见） */
var SPOT_LABELS = {
  UNOPENED: '未开局',
  FACING_OPEN: '面对 open',
  FACING_3BET: '面对 3bet',
  FACING_4BET: '面对 4bet',
  SQUEEZE: 'squeeze',
  LIMPED: 'limp 池'
}

/** PROB/EVAL step code → 简短标题（可选，message 仍保留） */
var STEP_CODE_LABELS = {
  FOLD_ROLL: '弃牌掷骰',
  RAISE_ROLL: '加注掷骰',
  VALUE_BET_ROLL: '价值下注掷骰',
  EQUITY_EVAL: '胜率评估',
  POT_ODDS_EVAL: '底池赔率'
}

function isEmptyValue (v) {
  if (v == null) return true
  if (typeof v === 'string' && v.trim() === '') return true
  if (Array.isArray(v) && v.length === 0) return true
  return false
}

/**
 * 字段键中文标签；未知键回退原键名。
 * @param {string} key
 * @returns {string}
 */
export function labelTraceField (key) {
  if (!key) return ''
  var k = String(key)
  if (FIELD_LABELS[k] != null) return FIELD_LABELS[k]
  return k
}

/**
 * 枚举值中文；category 可选：made|draw|plan|position|stage|tex|spot
 * @param {string} category
 * @param {string|number|boolean} value
 * @returns {string}
 */
export function labelTraceEnum (category, value) {
  if (value == null) return '—'
  if (typeof value === 'boolean') return value ? '是' : '否'
  var s = String(value)
  var cat = String(category || '').toLowerCase()
  var upper = s.toUpperCase()
  if (cat === 'made' || MADE_LABELS[upper] != null && cat === '') {
    if (MADE_LABELS[upper] != null) return MADE_LABELS[upper]
  }
  if (cat === 'made' && MADE_LABELS[upper] != null) return MADE_LABELS[upper]
  if (cat === 'draw' && DRAW_LABELS[upper] != null) return DRAW_LABELS[upper]
  if (cat === 'plan' && PLAN_LABELS[upper] != null) return PLAN_LABELS[upper]
  if (cat === 'position' && POSITION_LABELS[upper] != null) return POSITION_LABELS[upper]
  if ((cat === 'stage' || cat === 'street') && STAGE_LABELS[s.toLowerCase()] != null) {
    return STAGE_LABELS[s.toLowerCase()]
  }
  if ((cat === 'tex' || cat === 'texture') && TEX_LABELS[s] != null) return TEX_LABELS[s]
  if (cat === 'spot' && SPOT_LABELS[upper] != null) return SPOT_LABELS[upper]
  // 无 category 时尝试通用表
  if (MADE_LABELS[upper] != null) return MADE_LABELS[upper]
  if (DRAW_LABELS[upper] != null) return DRAW_LABELS[upper]
  if (PLAN_LABELS[upper] != null) return PLAN_LABELS[upper]
  if (POSITION_LABELS[upper] != null) return POSITION_LABELS[upper]
  if (STAGE_LABELS[s.toLowerCase()] != null) return STAGE_LABELS[s.toLowerCase()]
  if (TEX_LABELS[s] != null) return TEX_LABELS[s]
  if (SPOT_LABELS[upper] != null) return SPOT_LABELS[upper]
  return s
}

function inferEnumCategory (key) {
  var k = String(key || '').toLowerCase()
  if (k === 'made') return 'made'
  if (k === 'draw') return 'draw'
  if (k === 'plan') return 'plan'
  if (k === 'position') return 'position'
  if (k === 'stage' || k === 'street') return 'stage'
  if (k === 'tex' || k === 'texture') return 'tex'
  if (k === 'spot') return 'spot'
  return ''
}

function isProbKey (key) {
  var k = String(key || '').toLowerCase()
  return k.indexOf('prob') >= 0 || k === 'potodds' || k === 'equity' || k === 'equityest' ||
    k === 'callratio' || k === 'roll' || k === 'random' || k === 'potfraction'
}

function formatNumber (n, digits) {
  if (!Number.isFinite(n)) return String(n)
  if (digits != null) return n.toFixed(digits)
  if (Number.isInteger(n)) return String(n)
  return n.toFixed(3).replace(/\.?0+$/, function (m) {
    return m === '.000' ? '' : m
  })
}

function formatCardsArray (arr) {
  if (!Array.isArray(arr)) return String(arr)
  return arr.map(function (c) {
    return c != null ? String(c) : ''
  }).filter(Boolean).join(' ')
}

/**
 * 格式化单个 data 字段展示值。
 * @param {string} key
 * @param {*} value
 * @returns {string}
 */
export function formatTraceDataValue (key, value) {
  if (isEmptyValue(value)) return '—'
  var cat = inferEnumCategory(key)
  if (cat) {
    return labelTraceEnum(cat, value)
  }
  if (typeof value === 'boolean') return value ? '是' : '否'
  if (Array.isArray(value)) {
    var lk = String(key || '').toLowerCase()
    if (lk.indexOf('board') >= 0 || lk.indexOf('card') >= 0 || lk === 'holecards') {
      return formatCardsArray(value)
    }
    return value.map(function (v) { return String(v) }).join(', ')
  }
  if (typeof value === 'number') {
    if (isProbKey(key) && value >= 0 && value <= 1) {
      return formatNumber(value * 100, 1) + '%'
    }
    return formatNumber(value)
  }
  return String(value)
}

/**
 * 从 POSTFLOP_SPOT data 构建有序展示行。
 * @param {Record<string, *>} data
 * @returns {{ key: string, label: string, display: string }[]}
 */
export function buildPostflopSummaryRows (data) {
  if (!data || typeof data !== 'object') return []
  var rows = []
  var seen = {}
  var i
  for (i = 0; i < POSTFLOP_SUMMARY_FIELD_ORDER.length; i++) {
    var fk = POSTFLOP_SUMMARY_FIELD_ORDER[i]
    if (data[fk] != null && !isEmptyValue(data[fk])) {
      rows.push({
        key: fk,
        label: labelTraceField(fk),
        display: formatTraceDataValue(fk, data[fk])
      })
      seen[fk] = true
    }
  }
  var rest = Object.keys(data).filter(function (k) {
    return !seen[k] && !isEmptyValue(data[k])
  }).sort()
  for (i = 0; i < rest.length; i++) {
    var rk = rest[i]
    rows.push({
      key: rk,
      label: labelTraceField(rk),
      display: formatTraceDataValue(rk, data[rk])
    })
  }
  return rows
}

/**
 * PROB / EVAL / L1 等步骤 data 内联 chips。
 * @param {{ phase?: string, code?: string, data?: Record<string, *> }} step
 * @returns {{ key: string, label: string, display: string }[]}
 */
export function buildStepDataInline (step) {
  if (!step || !step.data || typeof step.data !== 'object') return []
  var phase = String(step.phase || '').toUpperCase()
  if (phase !== 'PROB' && phase !== 'EVAL' && phase !== 'L1') return []
  var keys = Object.keys(step.data).filter(function (k) {
    return !isEmptyValue(step.data[k])
  })
  if (!keys.length) return []
  keys.sort()
  return keys.map(function (k) {
    return {
      key: k,
      label: labelTraceField(k),
      display: formatTraceDataValue(k, step.data[k])
    }
  })
}

/**
 * step code 中文（辅助）
 * @param {string} code
 * @returns {string}
 */
export function labelTraceStepCode (code) {
  if (!code) return ''
  var c = String(code)
  if (STEP_CODE_LABELS[c] != null) return STEP_CODE_LABELS[c]
  return c
}
