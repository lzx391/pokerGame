<template>
  <div class="dp-trace-body" :class="{ 'dp-trace-body--retro8bit': uiVariant === 'retro8bit' }">
    <p v-if="statusLine" class="dp-trace-body__status" role="status">{{ statusLine }}</p>
    <p v-if="errorMessage" class="dp-trace-body__err" role="alert">{{ errorMessage }}</p>

    <div class="dp-trace-body__scroll">
      <!-- hand list -->
      <div v-if="screen === 'hand-list'" class="dp-trace-body__screen">
        <p v-if="!hands.length && !loading && !refreshing" class="dp-trace-body__empty">
          暂无已封存手牌 — 与 TAG NPC 打完一手后点「刷新」
        </p>
        <button
            v-for="hand in hands"
            :key="'hand-' + hand.handSeed"
            type="button"
            class="dp-trace-body__row"
            @click="openHand(hand)"
        >
          <span class="dp-trace-body__row-main">
            #{{ hand.handIndex != null ? hand.handIndex : '?' }}
            <span v-if="hand.inProgress" class="dp-trace-body__badge">进行中</span>
          </span>
          <span class="dp-trace-body__row-sub">{{ formatHandTime(hand) }}</span>
          <span class="dp-trace-body__row-meta">{{ hand.actionCount != null ? hand.actionCount : 0 }} 行动</span>
        </button>
      </div>

      <!-- L2 action matrix (always visible) + L3 detail stack (info top / steps bottom) -->
      <div
          v-else-if="screen === 'action-grid'"
          class="dp-trace-body__screen"
          :class="{ 'dp-trace-body__screen--with-detail': hasActionSelection }"
      >
        <p v-if="!selectedHand" class="dp-trace-body__empty">手牌上下文丢失</p>
        <template v-else>
          <div
              class="dp-trace-body__matrix-bar"
              :class="{ 'dp-trace-body__matrix-bar--compact': hasActionSelection }"
          >
            <game-npc-decision-trace-action-grid
                :actions="selectedHandActions"
                :selected-action-id="selectedActionId"
                :compact="hasActionSelection"
                @select-action="openAction"
            />
          </div>
          <div v-if="hasActionSelection" class="dp-trace-body__detail-stack">
            <p v-if="detailLoading" class="dp-trace-body__empty">加载决策详情…</p>
            <template v-else-if="actionDetail">
              <section class="dp-trace-body__info-pane" aria-label="行动信息">
                <div class="dp-trace-body__detail-head">
                  <span>#{{ actionDetail.actionSeq }} {{ actionDetail.street }}</span>
                  <span>{{ actionDetail.actorNickname }}</span>
                  <span>{{ formatFinalAction(actionDetail.finalAction) }}</span>
                  <span v-if="actionDetail.holeCards && actionDetail.holeCards.length" class="dp-trace-body__hole-cards">
                    <span
                        v-for="(c, ci) in actionDetail.holeCards"
                        :key="'hc-' + ci"
                        class="dp-hd__mini-card"
                        :class="miniCardClass(c)"
                    >{{ cardFace(c) }}</span>
                  </span>
                </div>
                <div
                    v-if="postflopSummaryRows.length"
                    class="dp-trace-body__postflop-summary"
                    aria-label="翻后决策变量"
                >
                  <div class="dp-trace-body__postflop-summary-head">
                    <span class="dp-trace-body__postflop-summary-title">决策变量</span>
                    <span class="dp-trace-body__postflop-summary-tag">POSTFLOP</span>
                  </div>
                  <p class="dp-trace-body__postflop-hint">plan 为意图；最终以 COMMIT 为准。</p>
                  <div class="dp-trace-body__summary-grid">
                    <div
                        v-for="row in postflopSummaryRows"
                        :key="'pf-' + row.key"
                        class="dp-trace-body__summary-cell"
                    >
                      <span class="dp-trace-body__summary-label">{{ row.label }}</span>
                      <span class="dp-trace-body__summary-value">{{ row.display }}</span>
                    </div>
                  </div>
                </div>
                <div v-if="otherContextSteps.length" class="dp-trace-body__context">
                  <div
                      v-for="step in otherContextSteps"
                      :key="'ctx-' + step.seq"
                      class="dp-trace-body__context-row"
                  >
                    <span class="dp-trace-body__phase">{{ step.phase }}</span>
                    <span class="dp-trace-body__step-msg">{{ step.message }}</span>
                  </div>
                </div>
              </section>
              <section class="dp-trace-body__steps-pane" aria-label="决策推理">
                <div class="dp-trace-body__steps">
                  <div
                      v-for="step in reasoningSteps"
                      :key="'step-' + step.seq"
                      class="dp-trace-body__step"
                      :class="'dp-trace-body__step--' + String(step.phase || '').toLowerCase()"
                  >
                    <span class="dp-trace-body__phase">{{ step.phase }}</span>
                    <div class="dp-trace-body__step-content">
                      <span v-if="step.code" class="dp-trace-body__step-code">{{ step.code }}</span>
                      <span class="dp-trace-body__step-msg">{{ step.message }}</span>
                      <div
                          v-if="stepInlineData(step).length"
                          class="dp-trace-body__step-data"
                          aria-label="步骤数据"
                      >
                        <span
                            v-for="chip in stepInlineData(step)"
                            :key="'chip-' + step.seq + '-' + chip.key"
                            class="dp-trace-body__data-chip"
                        >{{ chip.label }}: {{ chip.display }}</span>
                      </div>
                    </div>
                  </div>
                  <p v-if="!reasoningSteps.length" class="dp-trace-body__empty">暂无推理步骤</p>
                </div>
                <div class="dp-trace-body__matrix-foot">
                  <game-npc-decision-trace-matrix-grid
                      v-if="actionDetail.preflopMatrix"
                      variant="default"
                      :matrix="actionDetail.preflopMatrix"
                      :matrix-secondary="actionDetail.preflopMatrixSecondary"
                      :raise-meta="actionDetail.raiseMeta"
                  />
                  <p v-else-if="showPreflopMatrixEmpty" class="dp-trace-body__matrix-empty">
                    本行动暂无翻前范围矩阵。常见于 4bet 线、特殊 spot，或 TAG 在翻后 street 的行动。
                  </p>
                </div>
              </section>
            </template>
            <p v-else class="dp-trace-body__empty">行动详情不可用</p>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script>
import { dpResultMessage } from '@shared/utils/dpApiResult'
import { getCardClass, getCardDisplay } from '@features/room/utils/dpGameCardVisual'
import { fetchTraceAction } from '../utils/dpNpcDecisionTrace'
import { dpNpcDecisionTraceAuthPassword, isNpcDecisionTraceUnlocked } from '../utils/dpNpcDecisionTraceAuth'
import {
  buildPostflopSummaryRows,
  buildStepDataInline
} from '../utils/dpNpcDecisionTraceLabels'
import GameNpcDecisionTraceActionGrid from './GameNpcDecisionTraceActionGrid.vue'
import GameNpcDecisionTraceMatrixGrid from './GameNpcDecisionTraceMatrixGrid.vue'

export default {
  name: 'GameNpcDecisionTraceBody',
  components: {
    GameNpcDecisionTraceActionGrid,
    GameNpcDecisionTraceMatrixGrid
  },
  props: {
    roomId: { type: String, default: '' },
    hands: {
      type: Array,
      default: function () {
        return []
      }
    },
    loading: { type: Boolean, default: false },
    loadError: { type: String, default: '' },
    refreshing: { type: Boolean, default: false },
    onAuthFailure: {
      type: Function,
      default: null
    },
    /** 预留：'default' | 'retro8bit'，矩阵仍用 default 可读字号 */
    uiVariant: {
      type: String,
      default: 'default'
    }
  },
  data: function () {
    return {
      screen: 'hand-list',
      selectedHand: null,
      selectedActionSummary: null,
      actionDetail: null,
      detailLoading: false,
      detailError: ''
    }
  },
  computed: {
    selectedHandActions: function () {
      if (!this.selectedHand || !Array.isArray(this.selectedHand.actions)) return []
      return this.selectedHand.actions
    },
    selectedActionId: function () {
      return this.selectedActionSummary && this.selectedActionSummary.actionId
        ? this.selectedActionSummary.actionId
        : ''
    },
    hasActionSelection: function () {
      return !!this.selectedActionSummary
    },
    errorMessage: function () {
      return this.detailError || this.loadError || ''
    },
    statusLine: function () {
      if (this.loading && !this.refreshing) return '正在同步手牌列表…'
      if (this.refreshing) return '正在刷新…'
      return ''
    },
    breadcrumb: function () {
      if (this.screen === 'hand-list') return '手牌列表'
      if (this.screen === 'action-grid') {
        var hi = this.selectedHand && this.selectedHand.handIndex != null ? this.selectedHand.handIndex : '?'
        var base = '手牌 #' + hi + ' / 矩阵'
        if (this.selectedActionSummary && this.selectedActionSummary.actionSeq != null) {
          return base + ' / 行动 #' + this.selectedActionSummary.actionSeq
        }
        return base
      }
      return ''
    },
    canGoBack: function () {
      return this.screen !== 'hand-list'
    },
    postflopSpotStep: function () {
      if (!this.actionDetail || !Array.isArray(this.actionDetail.steps)) return null
      for (var i = 0; i < this.actionDetail.steps.length; i++) {
        var s = this.actionDetail.steps[i]
        if (s && String(s.phase || '').toUpperCase() === 'CONTEXT' &&
            String(s.code || '').toUpperCase() === 'POSTFLOP_SPOT') {
          return s
        }
      }
      return null
    },
    postflopSummaryRows: function () {
      var step = this.postflopSpotStep
      if (!step || !step.data) return []
      return buildPostflopSummaryRows(step.data)
    },
    otherContextSteps: function () {
      if (!this.actionDetail || !Array.isArray(this.actionDetail.steps)) return []
      return this.actionDetail.steps.filter(function (s) {
        if (!s || String(s.phase || '').toUpperCase() !== 'CONTEXT') return false
        return String(s.code || '').toUpperCase() !== 'POSTFLOP_SPOT'
      })
    },
    reasoningSteps: function () {
      if (!this.actionDetail || !Array.isArray(this.actionDetail.steps)) return []
      return this.actionDetail.steps.filter(function (s) {
        return s && String(s.phase || '').toUpperCase() !== 'CONTEXT'
      })
    },
    layoutWide: function () {
      return false
    },
    showPreflopMatrixEmpty: function () {
      if (!this.actionDetail) return false
      var street = String(this.actionDetail.street || '').toLowerCase()
      if (street !== 'preflop') return false
      return !this.actionDetail.preflopMatrix
    }
  },
  watch: {
    hands: function (nextHands) {
      this.syncSelectedHandFromList(nextHands)
    },
    screen: function () {
      this.emitNavChange()
    },
    selectedActionSummary: function () {
      this.emitNavChange()
    },
    loading: function (v, prev) {
      if (prev && !v && this.refreshing) {
        this.$emit('refresh-done')
      }
    }
  },
  mounted: function () {
    this.emitNavChange()
  },
  methods: {
    emitNavChange: function () {
      this.$emit('nav-change', {
        breadcrumb: this.breadcrumb,
        canGoBack: this.canGoBack,
        layoutWide: this.layoutWide
      })
    },
    resetNavigation: function () {
      this.screen = 'hand-list'
      this.selectedHand = null
      this.selectedActionSummary = null
      this.actionDetail = null
      this.detailError = ''
      this.detailLoading = false
      this.emitNavChange()
    },
    goBack: function () {
      if (this.screen === 'action-grid' && this.selectedActionSummary) {
        this.clearActionSelection()
        return
      }
      if (this.screen === 'action-grid') {
        this.screen = 'hand-list'
        this.selectedHand = null
        this.emitNavChange()
      }
    },
    clearActionSelection: function () {
      this.selectedActionSummary = null
      this.actionDetail = null
      this.detailError = ''
      this.detailLoading = false
      this.emitNavChange()
    },
    openHand: function (hand) {
      this.selectedHand = hand
      this.selectedActionSummary = null
      this.actionDetail = null
      this.detailError = ''
      this.detailLoading = false
      this.screen = 'action-grid'
      this.emitNavChange()
    },
    openAction: async function (act) {
      if (!this.selectedHand || !act) return
      if (this.selectedActionSummary && this.selectedActionSummary.actionId === act.actionId && this.actionDetail) {
        return
      }
      this.selectedActionSummary = act
      this.actionDetail = null
      this.detailError = ''
      this.detailLoading = true
      this.emitNavChange()
      var pwd = dpNpcDecisionTraceAuthPassword(this.roomId)
      if (!isNpcDecisionTraceUnlocked(this.roomId)) {
        this.detailLoading = false
        this.detailError = '会话已锁定，请重新输入密码'
        if (typeof this.onAuthFailure === 'function') {
          this.onAuthFailure({ message: '访问验证已失效' })
        }
        return
      }
      try {
        var result = await fetchTraceAction(
          this.$http,
          this.roomId,
          this.selectedHand.handSeed,
          act.actionId,
          pwd
        )
        if (!result.ok) {
          this.detailError = dpResultMessage(result.body) || '加载失败'
          if (typeof this.onAuthFailure === 'function') {
            this.onAuthFailure(result.body)
          }
          return
        }
        this.actionDetail = result.action || null
      } catch (err) {
        this.detailError = '网络错误'
      } finally {
        this.detailLoading = false
      }
    },
    syncSelectedHandFromList: function (hands) {
      if (!this.selectedHand || this.selectedHand.handSeed == null) return
      var seed = this.selectedHand.handSeed
      var list = Array.isArray(hands) ? hands : []
      for (var i = 0; i < list.length; i++) {
        if (list[i] && list[i].handSeed === seed) {
          this.selectedHand = list[i]
          return
        }
      }
    },
    cardFace: function (c) {
      return getCardDisplay(c)
    },
    miniCardClass: function (c) {
      return getCardClass(c).replace('card-base', '').trim()
    },
    formatHandTime: function (hand) {
      if (hand && hand.inProgress) return '—'
      return this.formatTime(hand && hand.sealedAtMs)
    },
    formatTime: function (ms) {
      if (!ms) return '—'
      try {
        var d = new Date(ms)
        return d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' })
      } catch (e) {
        return String(ms)
      }
    },
    formatFinalAction: function (fa) {
      if (!fa || !fa.type) return '—'
      var t = String(fa.type)
      if (fa.amount != null && fa.amount > 0) return t + ' ' + fa.amount
      return t
    },
    stepInlineData: function (step) {
      return buildStepDataInline(step)
    }
  }
}
</script>

<style scoped>
.dp-trace-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  font-family: var(--dp-font-ui, 'Segoe UI', 'PingFang SC', sans-serif);
  font-size: 14px;
  color: var(--dp-text-primary, #e8e8e8);
}
.dp-trace-body__status {
  margin: 0 0 6px;
  font-size: 13px;
  color: var(--dp-text-secondary, #a0a0a0);
}
.dp-trace-body__err {
  margin: 0 0 6px;
  font-size: 13px;
  color: #f56c6c;
}
.dp-trace-body__scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.dp-trace-body__screen--with-detail {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.dp-trace-body__matrix-bar {
  flex-shrink: 0;
  position: sticky;
  top: 0;
  z-index: 2;
  margin-bottom: 8px;
  padding-bottom: 8px;
  background: var(--dp-game-bg, #1a1d24);
  border-bottom: 1px solid var(--dp-border-subtle, rgba(255, 255, 255, 0.1));
}
.dp-trace-body__matrix-bar--compact {
  padding-bottom: 6px;
  margin-bottom: 6px;
}
.dp-trace-body__detail-stack {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-height: 0;
  flex: 1 1 auto;
}
.dp-trace-body__info-pane {
  flex: 0 0 auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--dp-border-subtle, rgba(255, 255, 255, 0.1));
}
.dp-trace-body__steps-pane {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-top: 12px;
}
.dp-trace-body__matrix-foot {
  flex-shrink: 0;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  margin-top: 4px;
  padding-top: 12px;
  border-top: 1px dashed var(--dp-border-subtle, rgba(255, 255, 255, 0.1));
}
.dp-trace-body__context {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.dp-trace-body__context-row {
  display: grid;
  grid-template-columns: 80px 1fr;
  gap: 10px;
  padding: 8px 12px;
  border-left: 3px solid var(--dp-text-secondary, #909399);
  background: var(--dp-surface-raised, rgba(255, 255, 255, 0.02));
  font-size: 13px;
  line-height: 1.5;
  border-radius: 0 6px 6px 0;
}
.dp-trace-body__empty {
  margin: 8px 0;
  font-size: 14px;
  color: var(--dp-text-secondary, #909399);
  line-height: 1.5;
}
.dp-trace-body__row {
  display: grid;
  grid-template-columns: 64px 1fr auto;
  gap: 8px;
  align-items: center;
  width: 100%;
  margin-bottom: 6px;
  padding: 10px 12px;
  border: 1px solid var(--dp-border-subtle, rgba(255, 255, 255, 0.12));
  border-radius: 6px;
  background: var(--dp-surface-raised, rgba(255, 255, 255, 0.04));
  color: var(--dp-text-primary, #e8e8e8);
  font-size: 14px;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.dp-trace-body__row:hover {
  border-color: var(--dp-accent, #409eff);
  background: var(--dp-surface-hover, rgba(64, 158, 255, 0.08));
}
.dp-trace-body__row-main {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: var(--dp-accent, #409eff);
}
.dp-trace-body__badge {
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.4;
  color: #e6a23c;
  background: rgba(230, 162, 60, 0.15);
  border: 1px solid rgba(230, 162, 60, 0.35);
}
.dp-trace-body__row-sub {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--dp-text-secondary, #a0a0a0);
}
.dp-trace-body__row-meta {
  font-size: 13px;
  color: var(--dp-text-secondary, #909399);
  white-space: nowrap;
}
.dp-trace-body__detail-head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--dp-border-subtle, rgba(255, 255, 255, 0.12));
  border-radius: 6px;
  background: var(--dp-surface-raised, rgba(255, 255, 255, 0.04));
  font-size: 14px;
}
.dp-trace-body__hole-cards {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.dp-trace-body__matrix-empty {
  margin: 0;
  padding: 10px 12px;
  border: 1px dashed var(--dp-border-subtle, rgba(255, 255, 255, 0.12));
  border-radius: 6px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--dp-text-secondary, #909399);
}
.dp-trace-body__steps {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.dp-trace-body__step {
  display: grid;
  grid-template-columns: 80px 1fr;
  gap: 10px;
  padding: 10px 12px;
  border-left: 3px solid var(--dp-accent, #409eff);
  background: var(--dp-surface-raised, rgba(255, 255, 255, 0.03));
  font-size: 14px;
  line-height: 1.5;
  border-radius: 0 6px 6px 0;
}
.dp-trace-body__phase {
  font-size: 12px;
  font-weight: 600;
  color: var(--dp-accent-muted, #79bbff);
  text-transform: uppercase;
}
.dp-trace-body__step-content {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.dp-trace-body__step-code {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--dp-text-secondary, #a8abb2);
}
.dp-trace-body__step-msg {
  word-break: break-word;
  color: var(--dp-text-primary, #e0e0e0);
}
.dp-trace-body__step-data {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 2px;
}
.dp-trace-body__data-chip {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--dp-accent-muted, #79bbff);
  background: rgba(64, 158, 255, 0.1);
  border: 1px solid rgba(64, 158, 255, 0.22);
}
.dp-trace-body__step--prob .dp-trace-body__data-chip {
  color: #e6a23c;
  background: rgba(230, 162, 60, 0.1);
  border-color: rgba(230, 162, 60, 0.28);
}
.dp-trace-body__step--eval .dp-trace-body__data-chip {
  color: #67c23a;
  background: rgba(103, 194, 58, 0.1);
  border-color: rgba(103, 194, 58, 0.28);
}
.dp-trace-body__postflop-summary {
  padding: 10px 12px;
  border: 1px solid var(--dp-border-subtle, rgba(255, 255, 255, 0.12));
  border-radius: 6px;
  background: var(--dp-surface-raised, rgba(255, 255, 255, 0.04));
}
.dp-trace-body__postflop-summary-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.dp-trace-body__postflop-summary-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--dp-text-primary, #e8e8e8);
}
.dp-trace-body__postflop-summary-tag {
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.05em;
  color: var(--dp-accent, #409eff);
  background: rgba(64, 158, 255, 0.12);
  border: 1px solid rgba(64, 158, 255, 0.28);
}
.dp-trace-body__postflop-hint {
  margin: 0 0 10px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--dp-text-secondary, #909399);
}
.dp-trace-body__summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(132px, 1fr));
  gap: 8px;
}
.dp-trace-body__summary-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px 8px;
  border-radius: 4px;
  background: rgba(0, 0, 0, 0.18);
  border: 1px solid rgba(255, 255, 255, 0.06);
  min-width: 0;
}
.dp-trace-body__summary-label {
  font-size: 11px;
  color: var(--dp-text-secondary, #909399);
  line-height: 1.3;
}
.dp-trace-body__summary-value {
  font-size: 13px;
  font-weight: 500;
  color: var(--dp-text-primary, #e8e8e8);
  line-height: 1.35;
  word-break: break-word;
}

/* retro8bit：终端绿强调，沿用 trace dock 变量 */
.dp-trace-body--retro8bit .dp-trace-body__postflop-summary {
  border-color: rgba(74, 246, 38, 0.28);
  background: rgba(0, 20, 8, 0.55);
}
.dp-trace-body--retro8bit .dp-trace-body__postflop-summary-title {
  font-family: 'Courier New', ui-monospace, monospace;
  font-size: 12px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #4af626;
}
.dp-trace-body--retro8bit .dp-trace-body__postflop-summary-tag {
  border-radius: 0;
  font-family: 'Courier New', ui-monospace, monospace;
  color: #72f052;
  background: rgba(74, 246, 38, 0.1);
  border-color: rgba(74, 246, 38, 0.35);
}
.dp-trace-body--retro8bit .dp-trace-body__postflop-hint,
.dp-trace-body--retro8bit .dp-trace-body__summary-label {
  font-family: 'Courier New', ui-monospace, monospace;
  font-size: 11px;
  color: #6bdc58;
}
.dp-trace-body--retro8bit .dp-trace-body__summary-value {
  font-family: 'Courier New', ui-monospace, monospace;
  color: #d8ffe0;
}
.dp-trace-body--retro8bit .dp-trace-body__summary-cell {
  border-color: rgba(74, 246, 38, 0.12);
  background: rgba(0, 0, 0, 0.35);
}
.dp-trace-body--retro8bit .dp-trace-body__data-chip {
  border-radius: 0;
  font-family: 'Courier New', ui-monospace, monospace;
  color: #72f052;
  background: rgba(74, 246, 38, 0.08);
  border-color: rgba(74, 246, 38, 0.28);
}
.dp-trace-body--retro8bit .dp-trace-body__step--prob .dp-trace-body__data-chip {
  color: #f0d060;
  background: rgba(240, 208, 96, 0.08);
  border-color: rgba(240, 208, 96, 0.28);
}
.dp-trace-body--retro8bit .dp-trace-body__step-code {
  font-family: 'Courier New', ui-monospace, monospace;
  color: #6bdc58;
}
</style>
