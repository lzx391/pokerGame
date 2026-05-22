/**
 * 仅用于「自定义」主题的调色板 UI：一行可绑定多个 --dp-*，写入 overrides 时值相同。
 * 不修改各预设主题在 dp-game-themes.css 中的定义。
 */
import { CAT_COPY } from './dpCatThemeCopy'

export var DP_CUSTOM_THEME_PALETTE_GROUPS = [
  {
    id: 'shell',
    label: '页面与面板',
    rows: [
      { label: '页面背景', keys: ['--dp-game-bg'] },
      { label: '面板背景', keys: ['--dp-panel-bg'] },
      { label: '面板边框', keys: ['--dp-panel-border'] }
    ]
  },
  {
    id: 'text',
    label: '文字',
    rows: [
      {
        label: '文字颜色（主/次/弱统一）',
        keys: ['--dp-text-primary', '--dp-text-secondary', '--dp-text-muted']
      }
    ]
  },
  {
    id: 'semantic',
    label: '语义色',
    rows: [
      { label: '强调色', keys: ['--dp-accent'] },
      { label: CAT_COPY.themePotChipsLabel, keys: ['--dp-pot'] },
      { label: '警告', keys: ['--dp-warning'] },
      { label: '成功', keys: ['--dp-success'] },
      { label: '危险', keys: ['--dp-danger'] },
      { label: '青色点缀', keys: ['--dp-cyan'] }
    ]
  },
  {
    id: 'buttons',
    label: '按钮',
    rows: [
      {
        label: CAT_COPY.themeRaiseBtnLabel + '背景',
        keys: ['--dp-btn-primary-bg', '--dp-btn-raise-bg']
      },
      {
        label: CAT_COPY.themeRaiseBtnLabel + '文字',
        keys: ['--dp-btn-primary-fg', '--dp-btn-raise-fg']
      },
      {
        label: CAT_COPY.themeAllInBtnLabel + '（底与字统一）',
        keys: ['--dp-btn-allin-bg', '--dp-btn-allin-fg']
      },
      { label: '线框按钮背景', keys: ['--dp-btn-ghost-bg'] },
      {
        label: '线框按钮边线与文字',
        keys: ['--dp-btn-ghost-border', '--dp-btn-ghost-fg']
      }
    ]
  },
  {
    id: 'input',
    label: '输入与计时',
    rows: [
      { label: '输入框背景', keys: ['--dp-input-bg'] },
      { label: '输入框边框', keys: ['--dp-input-border'] },
      { label: '行动计时环', keys: ['--dp-timer-ring'] },
      { label: '计时文字', keys: ['--dp-timer-text'] }
    ]
  },
  {
    id: 'owner',
    label: '房主标识',
    rows: [
      { label: '紫标签背景', keys: ['--dp-owner-purple-bg'] },
      { label: '紫标签文字', keys: ['--dp-owner-purple-fg'] },
      { label: '橙标签背景', keys: ['--dp-owner-orange-bg'] },
      { label: '橙标签文字', keys: ['--dp-owner-orange-fg'] }
    ]
  },
  {
    id: 'subpanel',
    label: '子面板',
    rows: [
      { label: '子面板背景', keys: ['--dp-subpanel-bg'] },
      { label: '子面板边框', keys: ['--dp-subpanel-border'] }
    ]
  },
  {
    id: 'seats',
    label: '座位卡',
    rows: [
      { label: '座位卡背景', keys: ['--dp-player-card-bg'] },
      { label: '离线座位背景', keys: ['--dp-player-card-offline-bg'] },
      { label: '离线座位边框', keys: ['--dp-player-card-offline-border'] },
      { label: '行动中座位背景', keys: ['--dp-player-card-turn-bg'] },
      { label: '行动中座位边框', keys: ['--dp-player-card-turn-border'] },
      { label: '本家座位描边', keys: ['--dp-player-border-me'] },
      { label: '赢家座位背景', keys: ['--dp-player-card-winner-bg'] },
      { label: '赢家座位边框', keys: ['--dp-player-card-winner-border'] },
      { label: '摊牌描边', keys: ['--dp-player-showdown-border'] }
    ]
  }
]

/** 采样模板用：去重后的全部 --dp-* */
export function dpCustomPaletteFlatKeys() {
  var seen = {}
  var out = []
  for (var i = 0; i < DP_CUSTOM_THEME_PALETTE_GROUPS.length; i++) {
    var rows = DP_CUSTOM_THEME_PALETTE_GROUPS[i].rows
    for (var r = 0; r < rows.length; r++) {
      var keys = rows[r].keys
      for (var k = 0; k < keys.length; k++) {
        var key = keys[k]
        if (!seen[key]) {
          seen[key] = true
          out.push(key)
        }
      }
    }
  }
  return out
}
