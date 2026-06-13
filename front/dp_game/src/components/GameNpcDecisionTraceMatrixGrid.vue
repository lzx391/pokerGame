<template>
  <div
      class="dp-trace-matrix"
      :class="{ 'dp-trace-matrix--default': variant === 'default' }"
      role="grid"
      aria-label="翻前范围矩阵"
  >
    <div class="dp-trace-matrix__meta" v-if="matrix">
      <span class="dp-trace-matrix__tag">{{ matrix.matrixKind || 'matrix' }}</span>
      <span v-if="matrix.spot" class="dp-trace-matrix__tag">{{ matrix.spot }}</span>
      <span v-if="matrix.position" class="dp-trace-matrix__tag">{{ matrix.position }}</span>
      <span v-if="matrix.rangeLevel != null" class="dp-trace-matrix__tag">RL{{ matrix.rangeLevel }}</span>
      <span v-if="matrix.heroHandLabel" class="dp-trace-matrix__tag dp-trace-matrix__tag--hero">{{ matrix.heroHandLabel }}</span>
    </div>
    <div v-if="showLegend" class="dp-trace-matrix__legend" aria-label="范围图例">
      <span class="dp-trace-matrix__legend-item">
        <span class="dp-trace-matrix__legend-swatch dp-trace-matrix__legend-swatch--light" aria-hidden="true" />
        <span>可行动范围</span>
      </span>
      <span class="dp-trace-matrix__legend-item">
        <span class="dp-trace-matrix__legend-swatch dp-trace-matrix__legend-swatch--dark" aria-hidden="true" />
        <span>约 {{ legendRaisePct }}% 3bet/4bet</span>
      </span>
      <span class="dp-trace-matrix__legend-note">橙色 ≠ 一定加注</span>
    </div>
    <div class="dp-trace-matrix__wrap">
      <div class="dp-trace-matrix__corner" aria-hidden="true" />
      <div
          v-for="(label, colIdx) in rankLabels"
          :key="'col-' + colIdx"
          class="dp-trace-matrix__col-label"
          :style="{ gridColumn: colIdx + 2, gridRow: 1 }"
      >{{ label }}</div>
      <template v-for="(rowLabel, rowIdx) in rankLabels">
        <div
            :key="'row-label-' + rowIdx"
            class="dp-trace-matrix__row-label"
            :style="{ gridColumn: 1, gridRow: rowIdx + 2 }"
        >{{ rowLabel }}</div>
        <div
            v-for="(colLabel, colIdx) in rankLabels"
            :key="'cell-' + rowIdx + '-' + colIdx"
            class="dp-trace-matrix__cell"
            :class="cellClass(rowIdx, colIdx)"
            :style="{ gridColumn: colIdx + 2, gridRow: rowIdx + 2 }"
            :title="cellTitle(rowIdx, colIdx)"
            role="gridcell"
        >
          <span class="dp-trace-matrix__cell-inner">{{ cellHandLabel(rowIdx, colIdx) }}</span>
        </div>
      </template>
    </div>
  </div>
</template>

<script>
var DEFAULT_LABELS = ['A', 'K', 'Q', 'J', 'T', '9', '8', '7', '6', '5', '4', '3', '2']

export default {
  name: 'GameNpcDecisionTraceMatrixGrid',
  props: {
    matrix: {
      type: Object,
      default: function () {
        return null
      }
    },
    /** 副矩阵（3bet/4bet 子范围），仅 default 主题叠色展示 */
    matrixSecondary: {
      type: Object,
      default: function () {
        return null
      }
    },
    /** raise 概率元数据，供图例展示 baseRaiseProb */
    raiseMeta: {
      type: Object,
      default: function () {
        return null
      }
    },
    /** 'retro8bit'（默认）| 'default' — 默认主题可读字号 */
    variant: {
      type: String,
      default: 'retro8bit',
      validator: function (v) {
        return v === 'retro8bit' || v === 'default'
      }
    }
  },
  computed: {
    rankLabels: function () {
      if (this.matrix && Array.isArray(this.matrix.labels) && this.matrix.labels.length === 13) {
        return this.matrix.labels
      }
      return DEFAULT_LABELS
    },
    cells: function () {
      return this.matrix && Array.isArray(this.matrix.cells) ? this.matrix.cells : []
    },
    heroCell: function () {
      return this.matrix && this.matrix.heroCell ? this.matrix.heroCell : null
    },
    secondaryCells: function () {
      return this.matrixSecondary && Array.isArray(this.matrixSecondary.cells)
        ? this.matrixSecondary.cells
        : []
    },
    hasSecondaryLayer: function () {
      return this.variant === 'default' && this.secondaryCells.length > 0
    },
    /** 副矩阵是否存在 inRange 格（UNOPENED 无 3bet 子范围时为 false） */
    hasSecondaryInRange: function () {
      if (this.variant !== 'default' || !this.secondaryCells.length) return false
      for (var r = 0; r < this.secondaryCells.length; r++) {
        var row = this.secondaryCells[r]
        if (!row) continue
        for (var c = 0; c < row.length; c++) {
          if (row[c]) return true
        }
      }
      return false
    },
    legendRaisePct: function () {
      if (!this.raiseMeta || this.raiseMeta.baseRaiseProb == null) return null
      var p = Number(this.raiseMeta.baseRaiseProb)
      if (isNaN(p)) return null
      return Math.round(Math.max(0, Math.min(1, p)) * 100)
    },
    showLegend: function () {
      return this.hasSecondaryInRange && this.legendRaisePct != null
    }
  },
  methods: {
    cellValue: function (row, col) {
      var rowArr = this.cells[row]
      if (!rowArr || col < 0 || col >= rowArr.length) return 0
      return rowArr[col] ? 1 : 0
    },
    secondaryCellValue: function (row, col) {
      var rowArr = this.secondaryCells[row]
      if (!rowArr || col < 0 || col >= rowArr.length) return 0
      return rowArr[col] ? 1 : 0
    },
    isHeroCell: function (row, col) {
      var h = this.heroCell
      return h && h.row === row && h.col === col
    },
    cellClass: function (row, col) {
      var inRange = this.cellValue(row, col) === 1
      var inSecondary = this.hasSecondaryLayer && this.secondaryCellValue(row, col) === 1
      return {
        'dp-trace-matrix__cell--in': inRange,
        'dp-trace-matrix__cell--out': !inRange,
        'dp-trace-matrix__cell--in-secondary': inSecondary,
        'dp-trace-matrix__cell--hero': this.isHeroCell(row, col)
      }
    },
    cellHandLabel: function (row, col) {
      var hi = this.rankLabels[row]
      var lo = this.rankLabels[col]
      if (!hi || !lo) return ''
      if (row === col) return hi + lo
      if (row < col) return hi + lo + 's'
      return lo + hi + 'o'
    },
    cellTitle: function (row, col) {
      var label = this.cellHandLabel(row, col)
      var inRange = this.cellValue(row, col) === 1
      var inSecondary = this.hasSecondaryLayer && this.secondaryCellValue(row, col) === 1
      var hero = this.isHeroCell(row, col) ? ' · HERO' : ''
      var rangeTag = inRange ? ' · IN' : ' · OUT'
      if (inSecondary) rangeTag += ' · RE-RAISE'
      return label + rangeTag + hero
    }
  }
}
</script>

<style scoped>
.dp-trace-matrix {
  margin-top: 10px;
}
.dp-trace-matrix__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}
.dp-trace-matrix__tag {
  padding: 2px 6px;
  border: 1px solid rgba(74, 246, 38, 0.22);
  font-size: 9px;
  color: #72f052;
  font-family: 'Courier New', ui-monospace, monospace;
}
.dp-trace-matrix__tag--hero {
  border-color: #ffe066;
  color: #ffe066;
}
.dp-trace-matrix__wrap {
  display: grid;
  grid-template-columns: 22px repeat(13, minmax(18px, 1fr));
  grid-template-rows: 18px repeat(13, minmax(18px, 1fr));
  gap: 2px;
  max-width: 100%;
  overflow-x: auto;
}
.dp-trace-matrix__corner {
  grid-column: 1;
  grid-row: 1;
}
.dp-trace-matrix__col-label,
.dp-trace-matrix__row-label {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 8px;
  color: #8bdc78;
  font-family: 'Press Start 2P', monospace;
}
.dp-trace-matrix__cell {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 20px;
  border: 1px solid rgba(40, 50, 44, 0.9);
  background: #14181c;
  font-size: 7px;
  color: #4a5550;
  font-family: 'Courier New', ui-monospace, monospace;
  line-height: 1;
}
.dp-trace-matrix__cell--in {
  background: rgba(74, 246, 38, 0.22);
  color: #b8ffb0;
  border-color: rgba(74, 246, 38, 0.35);
  text-shadow: 0 0 4px rgba(74, 246, 38, 0.35);
}
.dp-trace-matrix__cell--out {
  background: #0e1012;
  color: #3a4240;
}
.dp-trace-matrix__cell--hero {
  box-shadow: inset 0 0 0 2px #ffe066;
  border-color: #ffe066;
}
.dp-trace-matrix__cell-inner {
  transform: scale(0.85);
  white-space: nowrap;
}

/* 默认主题：14–16px 可读字号，不用像素/CRT 配色 */
.dp-trace-matrix--default .dp-trace-matrix__tag {
  font-size: 13px;
  font-family: var(--dp-font-ui, 'Segoe UI', 'PingFang SC', sans-serif);
  border-color: var(--dp-border-subtle, rgba(255, 255, 255, 0.15));
  color: var(--dp-text-secondary, #a0a0a0);
}
.dp-trace-matrix--default .dp-trace-matrix__tag--hero {
  border-color: var(--dp-accent, #409eff);
  color: var(--dp-accent, #409eff);
}
.dp-trace-matrix--default .dp-trace-matrix__wrap {
  grid-template-columns: 28px repeat(13, minmax(22px, 1fr));
  grid-template-rows: 22px repeat(13, minmax(22px, 1fr));
  gap: 3px;
}
.dp-trace-matrix--default .dp-trace-matrix__col-label,
.dp-trace-matrix--default .dp-trace-matrix__row-label {
  font-size: 14px;
  font-family: var(--dp-font-ui, 'Segoe UI', sans-serif);
  color: var(--dp-text-secondary, #909399);
}
.dp-trace-matrix--default .dp-trace-matrix__cell {
  min-height: 24px;
  font-size: 14px;
  font-family: var(--dp-font-ui, 'Segoe UI', sans-serif);
  border-color: var(--dp-border-subtle, rgba(255, 255, 255, 0.1));
  background: var(--dp-surface-raised, rgba(255, 255, 255, 0.04));
  color: var(--dp-text-secondary, #606266);
}
.dp-trace-matrix--default .dp-trace-matrix__cell--in {
  background: #dbeafe;
  color: #1e3a5f;
  border-color: #93c5fd;
  text-shadow: none;
}
.dp-trace-matrix--default .dp-trace-matrix__cell--out {
  background: var(--dp-surface-raised, rgba(0, 0, 0, 0.2));
  color: var(--dp-text-secondary, #606266);
}
.dp-trace-matrix--default .dp-trace-matrix__cell--hero {
  box-shadow: inset 0 0 0 2px var(--dp-accent, #409eff);
  border-color: var(--dp-accent, #409eff);
}
.dp-trace-matrix--default .dp-trace-matrix__cell-inner {
  transform: none;
}
.dp-trace-matrix--default .dp-trace-matrix__cell--in-secondary {
  background: #ea580c;
  color: #fff;
  border-color: #c2410c;
}
.dp-trace-matrix--default .dp-trace-matrix__legend {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 14px;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--dp-text-secondary, #909399);
  font-family: var(--dp-font-ui, 'Segoe UI', 'PingFang SC', sans-serif);
}
.dp-trace-matrix--default .dp-trace-matrix__legend-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.dp-trace-matrix--default .dp-trace-matrix__legend-swatch {
  width: 14px;
  height: 14px;
  border: 1px solid var(--dp-border-subtle, rgba(255, 255, 255, 0.15));
  border-radius: 2px;
  flex-shrink: 0;
}
.dp-trace-matrix--default .dp-trace-matrix__legend-swatch--light {
  background: #dbeafe;
  border-color: #93c5fd;
}
.dp-trace-matrix--default .dp-trace-matrix__legend-swatch--dark {
  background: #ea580c;
  border-color: #c2410c;
}
.dp-trace-matrix--default .dp-trace-matrix__legend-note {
  font-size: 12px;
  color: var(--dp-text-secondary, #787c82);
  font-style: italic;
}
</style>
