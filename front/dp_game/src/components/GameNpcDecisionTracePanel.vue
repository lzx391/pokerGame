<template>
  <transition name="dp-trace-panel">
    <div
        v-if="visible"
        class="dp-trace-panel"
        :style="{ zIndex: panelZIndex }"
        @click.self="close"
    >
      <div class="dp-trace-panel__shell" role="dialog" aria-labelledby="dp-trace-panel-title">
        <div class="dp-trace-panel__scanlines" aria-hidden="true" />
        <header class="dp-trace-panel__head">
          <span id="dp-trace-panel-title" class="dp-trace-panel__title">&gt; DECISION_TRACE // TAG</span>
          <button type="button" class="dp-trace-panel__close" aria-label="关闭" @click="close">[X]</button>
        </header>

        <div class="dp-trace-panel__toolbar">
          <button
              v-if="screen !== 'hand-list'"
              type="button"
              class="dp-trace-panel__btn"
              @click="goBack"
          >
            &lt; BACK
          </button>
          <button
              type="button"
              class="dp-trace-panel__btn"
              :disabled="refreshing"
              @click="onRefresh"
          >
            {{ refreshing ? 'REFRESH...' : '[REFRESH]' }}
          </button>
          <span class="dp-trace-panel__breadcrumb">{{ breadcrumb }}</span>
        </div>

        <p v-if="statusLine" class="dp-trace-panel__status" role="status">{{ statusLine }}</p>
        <p v-if="errorMessage" class="dp-trace-panel__err" role="alert">&gt; ERR: {{ errorMessage }}</p>

        <div class="dp-trace-panel__body dp-retro-scrollbar">
          <!-- hand list -->
          <div v-if="screen === 'hand-list'" class="dp-trace-panel__screen">
            <p v-if="!hands.length && !refreshing" class="dp-trace-panel__empty">&gt; no sealed hands yet — play a hand with TAG NPC</p>
            <button
                v-for="hand in hands"
                :key="'hand-' + hand.handSeed"
                type="button"
                class="dp-trace-panel__row"
                @click="openHand(hand)"
            >
              <span class="dp-trace-panel__row-main">#{{ hand.handIndex != null ? hand.handIndex : '?' }}</span>
              <span class="dp-trace-panel__row-sub">{{ formatTime(hand.sealedAtMs) }}</span>
              <span class="dp-trace-panel__row-meta">{{ hand.actionCount != null ? hand.actionCount : 0 }} ACT</span>
            </button>
          </div>

          <!-- action list -->
          <div v-else-if="screen === 'action-list'" class="dp-trace-panel__screen">
            <p v-if="!selectedHand" class="dp-trace-panel__empty">&gt; hand context lost</p>
            <p v-else-if="!actionRows.length" class="dp-trace-panel__empty">&gt; no TAG actions in this hand</p>
            <button
                v-for="act in actionRows"
                :key="'act-' + act.actionId"
                type="button"
                class="dp-trace-panel__row"
                @click="openAction(act)"
            >
              <span class="dp-trace-panel__row-main">#{{ act.actionSeq }} {{ act.street }}</span>
              <span class="dp-trace-panel__row-sub">{{ act.actorNickname }}</span>
              <span class="dp-trace-panel__row-meta">{{ formatFinalAction(act.finalAction) }}</span>
            </button>
          </div>

          <!-- action detail -->
          <div v-else-if="screen === 'action-detail'" class="dp-trace-panel__screen dp-trace-panel__screen--detail">
            <p v-if="detailLoading" class="dp-trace-panel__empty">&gt; loading trace...</p>
            <template v-else-if="actionDetail">
              <div class="dp-trace-panel__detail-head">
                <span>#{{ actionDetail.actionSeq }} {{ actionDetail.street }}</span>
                <span>{{ actionDetail.actorNickname }}</span>
                <span>{{ formatFinalAction(actionDetail.finalAction) }}</span>
                <span v-if="actionDetail.holeCards && actionDetail.holeCards.length">
                  {{ actionDetail.holeCards.join(' ') }}
                </span>
              </div>
              <div class="dp-trace-panel__steps">
                <div
                    v-for="step in actionDetail.steps || []"
                    :key="'step-' + step.seq"
                    class="dp-trace-panel__step"
                >
                  <span class="dp-trace-panel__phase">{{ step.phase }}</span>
                  <span class="dp-trace-panel__step-msg">{{ step.message }}</span>
                </div>
                <p v-if="!(actionDetail.steps && actionDetail.steps.length)" class="dp-trace-panel__empty">&gt; no steps recorded</p>
              </div>
              <game-npc-decision-trace-matrix-grid
                  v-if="actionDetail.preflopMatrix"
                  :matrix="actionDetail.preflopMatrix"
              />
            </template>
            <p v-else class="dp-trace-panel__empty">&gt; action detail unavailable</p>
          </div>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
import { dpNextZIndex } from '@/utils/dpModalZIndex'
import { dpResultMessage } from '@/utils/dpApiResult'
import { fetchTraceAction } from '@/utils/dpNpcDecisionTrace'
import { dpDeckPresetSessionPassword } from '@/utils/dpDeckPresetUnlock'
import GameNpcDecisionTraceMatrixGrid from './GameNpcDecisionTraceMatrixGrid.vue'

export default {
  name: 'GameNpcDecisionTracePanel',
  components: { GameNpcDecisionTraceMatrixGrid },
  props: {
    visible: { type: Boolean, default: false },
    roomId: { type: String, default: '' },
    hands: {
      type: Array,
      default: function () {
        return []
      }
    },
    loading: { type: Boolean, default: false },
    loadError: { type: String, default: '' },
    newHandNotice: { type: String, default: '' },
    onAuthFailure: {
      type: Function,
      default: null
    }
  },
  data: function () {
    return {
      panelZIndex: dpNextZIndex('decisionTrace'),
      screen: 'hand-list',
      selectedHand: null,
      selectedActionSummary: null,
      actionDetail: null,
      detailLoading: false,
      detailError: '',
      refreshing: false
    }
  },
  computed: {
    actionRows: function () {
      if (!this.selectedHand || !Array.isArray(this.selectedHand.actions)) return []
      return this.selectedHand.actions.slice().sort(function (a, b) {
        return (a.actionSeq || 0) - (b.actionSeq || 0)
      })
    },
    breadcrumb: function () {
      if (this.screen === 'hand-list') return 'HANDS'
      if (this.screen === 'action-list') {
        var hi = this.selectedHand && this.selectedHand.handIndex != null ? this.selectedHand.handIndex : '?'
        return 'HAND #' + hi + ' / ACTIONS'
      }
      if (this.screen === 'action-detail') {
        var seq = this.selectedActionSummary && this.selectedActionSummary.actionSeq != null
          ? this.selectedActionSummary.actionSeq
          : '?'
        return 'ACTION #' + seq + ' / DETAIL'
      }
      return ''
    },
    errorMessage: function () {
      return this.detailError || this.loadError || ''
    },
    statusLine: function () {
      if (this.newHandNotice) return this.newHandNotice
      if (this.loading) return '> syncing hand list...'
      return ''
    }
  },
  watch: {
    visible: function (v) {
      if (v) {
        this.panelZIndex = dpNextZIndex('decisionTrace')
        this.resetNavigation()
      }
    },
    loading: function (v, prev) {
      if (prev && !v) {
        this.refreshing = false
      }
    }
  },
  methods: {
    close: function () {
      this.$emit('update:visible', false)
      this.$emit('close')
    },
    resetNavigation: function () {
      this.screen = 'hand-list'
      this.selectedHand = null
      this.selectedActionSummary = null
      this.actionDetail = null
      this.detailError = ''
      this.detailLoading = false
    },
    goBack: function () {
      if (this.screen === 'action-detail') {
        this.screen = 'action-list'
        this.actionDetail = null
        this.detailError = ''
        return
      }
      if (this.screen === 'action-list') {
        this.screen = 'hand-list'
        this.selectedHand = null
      }
    },
    onRefresh: function () {
      this.refreshing = true
      this.$emit('refresh')
    },
    openHand: function (hand) {
      this.selectedHand = hand
      this.screen = 'action-list'
    },
    openAction: async function (act) {
      if (!this.selectedHand || !act) return
      this.selectedActionSummary = act
      this.screen = 'action-detail'
      this.actionDetail = null
      this.detailError = ''
      this.detailLoading = true
      var pwd = dpDeckPresetSessionPassword(this.roomId)
      if (!pwd) {
        this.detailLoading = false
        this.detailError = 'SESSION LOCKED — re-enter password'
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
          var msg = dpResultMessage(result.body) || 'load failed'
          this.detailError = msg
          if (typeof this.onAuthFailure === 'function') {
            this.onAuthFailure(result.body)
          }
          return
        }
        this.actionDetail = result.action || null
      } catch (err) {
        this.detailError = 'NETWORK ERROR'
      } finally {
        this.detailLoading = false
      }
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
    }
  }
}
</script>

<style scoped>
.dp-trace-panel {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
  background: rgba(0, 0, 0, 0.62);
  font-family: 'Courier New', ui-monospace, 'PingFang SC', monospace;
}
.dp-trace-panel-enter-active,
.dp-trace-panel-leave-active {
  transition: opacity 0.15s ease;
}
.dp-trace-panel-enter,
.dp-trace-panel-leave-to {
  opacity: 0;
}
.dp-trace-panel__shell {
  position: relative;
  width: min(720px, 100%);
  max-height: min(88vh, 820px);
  display: flex;
  flex-direction: column;
  background: rgba(8, 10, 12, 0.97);
  border: 2px solid rgba(74, 246, 38, 0.34);
  box-shadow: 0 0 0 1px #000, 0 12px 40px rgba(0, 0, 0, 0.65);
  overflow: hidden;
}
.dp-trace-panel__scanlines {
  position: absolute;
  inset: 0;
  z-index: 2;
  pointer-events: none;
  background: repeating-linear-gradient(to bottom, transparent 0 2px, rgba(0, 0, 0, 0.12) 2px 3px);
  background-size: 100% 3px;
  opacity: 0.14;
}
.dp-trace-panel__head {
  position: relative;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  border-bottom: 1px solid rgba(74, 246, 38, 0.22);
  background: color-mix(in srgb, #12151a 88%, #4af626 12%);
}
.dp-trace-panel__title {
  font-family: 'Press Start 2P', monospace;
  font-size: 8px;
  color: #4af626;
  text-shadow: 0 0 6px rgba(74, 246, 38, 0.55);
  letter-spacing: 0.04em;
}
.dp-trace-panel__close {
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 1px solid rgba(74, 246, 38, 0.28);
  background: #0a0c0e;
  color: #4af626;
  font-size: 13px;
  cursor: pointer;
}
.dp-trace-panel__close:hover {
  background: #4af626;
  color: #080a0c;
}
.dp-trace-panel__toolbar {
  position: relative;
  z-index: 3;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid rgba(74, 246, 38, 0.1);
}
.dp-trace-panel__btn {
  padding: 6px 10px;
  border: 1px solid rgba(74, 246, 38, 0.28);
  background: #0a0c0e;
  color: #4af626;
  font-family: 'Press Start 2P', monospace;
  font-size: 7px;
  cursor: pointer;
}
.dp-trace-panel__btn:hover:not(:disabled) {
  background: rgba(74, 246, 38, 0.12);
}
.dp-trace-panel__btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.dp-trace-panel__breadcrumb {
  margin-left: auto;
  font-size: 9px;
  color: #ffe066;
  text-shadow: 0 0 4px rgba(255, 224, 102, 0.35);
}
.dp-trace-panel__status {
  margin: 0;
  padding: 6px 12px 0;
  font-size: 11px;
  color: #72f052;
}
.dp-trace-panel__err {
  margin: 0;
  padding: 4px 12px 0;
  font-size: 11px;
  color: #ff6666;
}
.dp-trace-panel__body {
  position: relative;
  z-index: 1;
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 10px 12px 14px;
}
.dp-trace-panel__screen--detail {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.dp-trace-panel__empty {
  margin: 8px 0;
  font-size: 12px;
  color: #6a7a68;
}
.dp-trace-panel__row {
  display: grid;
  grid-template-columns: 72px 1fr auto;
  gap: 8px;
  align-items: center;
  width: 100%;
  margin-bottom: 6px;
  padding: 10px 12px;
  border: 1px solid rgba(74, 246, 38, 0.18);
  background: rgba(10, 14, 12, 0.85);
  color: #c8e8c0;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}
.dp-trace-panel__row:hover {
  border-color: rgba(74, 246, 38, 0.45);
  background: rgba(74, 246, 38, 0.08);
}
.dp-trace-panel__row-main {
  color: #4af626;
  font-weight: bold;
}
.dp-trace-panel__row-sub {
  color: #9ab898;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dp-trace-panel__row-meta {
  color: #ffe066;
  font-size: 11px;
  white-space: nowrap;
}
.dp-trace-panel__detail-head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 8px 10px;
  border: 1px solid rgba(74, 246, 38, 0.2);
  background: rgba(12, 16, 14, 0.9);
  font-size: 11px;
  color: #b8ffb0;
}
.dp-trace-panel__steps {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.dp-trace-panel__step {
  display: grid;
  grid-template-columns: 72px 1fr;
  gap: 8px;
  padding: 8px 10px;
  border-left: 2px solid rgba(74, 246, 38, 0.35);
  background: rgba(8, 10, 12, 0.75);
  font-size: 11px;
  line-height: 1.45;
}
.dp-trace-panel__phase {
  font-family: 'Press Start 2P', monospace;
  font-size: 7px;
  color: #ffe066;
  padding-top: 2px;
}
.dp-trace-panel__step-msg {
  color: #d8f0d0;
  word-break: break-word;
}
</style>
