<template>
  <div class="dp-trace-action-grid-wrap" :class="{ 'dp-trace-action-grid-wrap--compact': compact }">
    <p v-if="!matrix || !matrix.npcs.length" class="dp-trace-action-grid__empty">本手暂无 TAG 行动记录</p>
    <table
        v-else
        class="dp-trace-action-grid"
        role="grid"
        aria-label="NPC 行动矩阵"
    >
      <thead>
        <tr>
          <th scope="col" class="dp-trace-action-grid__th-npc">NPC</th>
          <th
              v-for="col in matrix.streets"
              :key="'th-' + col.key"
              scope="col"
          >{{ col.label }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="npc in matrix.npcs" :key="'row-' + npc">
          <th scope="row" class="dp-trace-action-grid__npc">{{ npc }}</th>
          <td
              v-for="col in matrix.streets"
              :key="'cell-' + npc + '-' + col.key"
              class="dp-trace-action-grid__cell"
          >
            <template v-if="cellActions(npc, col.key).length">
              <button
                  v-for="act in cellActions(npc, col.key)"
                  :key="'chip-' + act.actionId"
                  type="button"
                  class="dp-trace-action-grid__chip"
                  :class="{ 'dp-trace-action-grid__chip--selected': act.actionId === selectedActionId }"
                  :title="chipTitle(act)"
                  @click="onSelect(act)"
              >{{ chipLabel(act) }}</button>
            </template>
            <span v-else class="dp-trace-action-grid__dash" aria-hidden="true">—</span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script>
import {
  buildTraceStreetMatrix,
  formatTraceActionLabel,
  formatTraceActionShort
} from '../utils/dpNpcDecisionTraceMatrix'

export default {
  name: 'GameNpcDecisionTraceActionGrid',
  props: {
    actions: {
      type: Array,
      default: function () {
        return []
      }
    },
    selectedActionId: {
      type: String,
      default: ''
    },
  /** 选中行动后压缩行高，便于同屏保留矩阵与详情 */
    compact: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    matrix: function () {
      return buildTraceStreetMatrix(this.actions)
    }
  },
  methods: {
    cellActions: function (npc, streetKey) {
      return this.matrix.getCellActions(npc, streetKey)
    },
    chipLabel: function (act) {
      return formatTraceActionShort(act && act.finalAction)
    },
    chipTitle: function (act) {
      if (!act) return ''
      var parts = ['#' + (act.actionSeq != null ? act.actionSeq : '?')]
      parts.push(formatTraceActionLabel(act.finalAction))
      return parts.join(' · ')
    },
    onSelect: function (act) {
      if (!act) return
      this.$emit('select-action', act)
    }
  }
}
</script>

<style scoped>
.dp-trace-action-grid-wrap {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  margin: 0 -2px;
  padding: 0 2px 4px;
}
.dp-trace-action-grid-wrap--compact .dp-trace-action-grid th,
.dp-trace-action-grid-wrap--compact .dp-trace-action-grid td {
  padding: 6px 6px;
}
.dp-trace-action-grid-wrap--compact .dp-trace-action-grid__chip {
  padding: 2px 6px;
  font-size: 11px;
  margin-bottom: 2px;
}
.dp-trace-action-grid-wrap--compact .dp-trace-action-grid thead th {
  font-size: 11px;
}
.dp-trace-action-grid {
  width: 100%;
  min-width: 320px;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
  line-height: 1.4;
  color: var(--dp-text-primary, #e8e8e8);
}
.dp-trace-action-grid th,
.dp-trace-action-grid td {
  border: none;
  border-bottom: 1px solid var(--dp-border-subtle, rgba(255, 255, 255, 0.1));
  padding: 10px 8px;
  text-align: left;
  vertical-align: top;
}
.dp-trace-action-grid tbody tr:last-child th,
.dp-trace-action-grid tbody tr:last-child td {
  border-bottom: none;
}
.dp-trace-action-grid thead th {
  background: var(--dp-surface-raised, rgba(255, 255, 255, 0.06));
  color: var(--dp-text-secondary, #a0a0a0);
  font-weight: 700;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.02em;
  white-space: nowrap;
}
.dp-trace-action-grid__th-npc {
  min-width: 72px;
}
.dp-trace-action-grid tbody tr:nth-child(even) td,
.dp-trace-action-grid tbody tr:nth-child(even) th[scope='row'] {
  background: rgba(255, 255, 255, 0.02);
}
.dp-trace-action-grid tbody tr:hover td,
.dp-trace-action-grid tbody tr:hover th[scope='row'] {
  background: var(--dp-surface-hover, rgba(64, 158, 255, 0.06));
}
.dp-trace-action-grid__npc {
  font-weight: 600;
  color: var(--dp-accent, #409eff);
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dp-trace-action-grid__cell {
  min-width: 56px;
}
.dp-trace-action-grid__chip {
  display: block;
  width: 100%;
  margin: 0 0 4px;
  padding: 4px 8px;
  border: 1px solid var(--dp-border-subtle, rgba(255, 255, 255, 0.14));
  border-radius: 4px;
  background: var(--dp-surface-raised, rgba(255, 255, 255, 0.05));
  color: var(--dp-text-primary, #e8e8e8);
  font-size: 12px;
  font-weight: 600;
  font-family: var(--dp-font-ui, 'Segoe UI', 'PingFang SC', sans-serif);
  text-align: center;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}
.dp-trace-action-grid__chip:last-child {
  margin-bottom: 0;
}
.dp-trace-action-grid__chip:hover {
  border-color: var(--dp-accent, #409eff);
  background: var(--dp-surface-hover, rgba(64, 158, 255, 0.1));
}
.dp-trace-action-grid__chip--selected {
  border-color: var(--dp-accent, #409eff);
  background: rgba(64, 158, 255, 0.18);
  box-shadow: inset 0 0 0 1px rgba(64, 158, 255, 0.35);
}
.dp-trace-action-grid__dash {
  color: var(--dp-text-secondary, #606266);
  font-size: 13px;
}
.dp-trace-action-grid__empty {
  margin: 8px 0;
  font-size: 14px;
  color: var(--dp-text-secondary, #909399);
  line-height: 1.5;
}
</style>
