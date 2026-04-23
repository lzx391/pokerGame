<template>
  <div v-if="visible" class="hand-rank-modal-mask" @click="onMaskClick">
    <div
        class="hand-rank-modal hand-rank-modal--play-guide"
        role="dialog"
        aria-modal="true"
        aria-labelledby="dp-play-guide-title"
        @click.stop
    >
      <div class="dp-game-dialog__head dp-play-guide__head">
        <span id="dp-play-guide-title" class="dp-game-dialog__title dp-sr-only">玩法说明</span>
        <div class="dp-play-guide__tabs" role="tablist" aria-label="玩法说明分栏">
          <button
              type="button"
              class="dp-play-guide__tab"
              :class="{ 'is-active': activeTab === 'flow' }"
              role="tab"
              :aria-selected="activeTab === 'flow' ? 'true' : 'false'"
              @click="setTab('flow')"
          >
            流程说明
          </button>
          <button
              type="button"
              class="dp-play-guide__tab"
              :class="{ 'is-active': activeTab === 'ranks' }"
              role="tab"
              :aria-selected="activeTab === 'ranks' ? 'true' : 'false'"
              @click="setTab('ranks')"
          >
            牌型说明
          </button>
        </div>
        <button
            type="button"
            class="dp-game-dialog__close"
            aria-label="关闭"
            @click="close"
        >
          ×
        </button>
      </div>
      <div
          class="dp-game-dialog__body dp-play-guide__body"
          :class="{ 'dp-play-guide__body--ranks': activeTab === 'ranks' }"
      >
        <div v-show="activeTab === 'flow'" class="dp-play-guide__panel" role="tabpanel">
          <game-play-flow-content />
        </div>
        <div v-show="activeTab === 'ranks'" class="dp-play-guide__panel" role="tabpanel">
          <p class="dp-play-guide__ranks-hint">牌型大小参考（从大到小）</p>
          <div class="hand-rank-list">
            <div v-for="(item, idx) in items" :key="idx" class="hand-rank-item">
              <span class="hand-rank-num">{{ idx + 1 }}</span>
              <span class="hand-rank-name">{{ item.name }}</span>
              <div class="hand-rank-cards">
                <div v-for="c in item.cards" :key="c" :class="getCardClass(c)" class="hand-rank-card">
                  {{ getCardDisplay(c) }}
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
      <div v-if="firstRun" class="dp-play-guide__first-run">
        <label class="dp-play-guide__check">
          <input v-model="dontShowAgain" type="checkbox" />
          不再自动弹出（仍可在「玩法说明」里查看）
        </label>
        <button type="button" class="dp-play-guide__ok" @click="confirmFirstRun">我知道了</button>
      </div>
    </div>
  </div>
</template>

<script>
import { getCardClass, getCardDisplay } from '../utils/dpGameCardVisual'
import GamePlayFlowContent from './GamePlayFlowContent.vue'

export default {
  name: 'GamePlayGuideModal',
  components: { GamePlayFlowContent },
  props: {
    visible: { type: Boolean, default: false },
    activeTab: { type: String, default: 'flow' },
    items: { type: Array, required: true },
    /** 大厅首次引导：底栏含「不再自动弹出」与「我知道了」；点遮罩不关闭 */
    firstRun: { type: Boolean, default: false }
  },
  data() {
    return { dontShowAgain: false }
  },
  watch: {
    visible(v) {
      if (v && this.firstRun) this.dontShowAgain = false
    }
  },
  methods: {
    getCardClass,
    getCardDisplay,
    close() {
      this.$emit('close')
    },
    onMaskClick() {
      if (this.firstRun) return
      this.close()
    },
    confirmFirstRun() {
      this.$emit('confirm', { dontShowAgain: this.dontShowAgain })
      this.close()
    },
    setTab(t) {
      this.$emit('tab-change', t)
    }
  }
}
</script>

<style src="../styles/dp-poker-cards.css"></style>
<style src="../styles/dp-game-modals.css"></style>
<style scoped>
.dp-sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
.dp-play-guide__head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.dp-play-guide__tabs {
  display: flex;
  flex: 1;
  min-width: 0;
  gap: 4px;
  border-radius: 8px;
  padding: 2px;
  background: var(--dp-subpanel-bg, rgba(0, 0, 0, 0.06));
}
.dp-play-guide__tab {
  flex: 1;
  min-width: 0;
  padding: 8px 10px;
  font-size: 13px;
  font-weight: 600;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  color: var(--dp-text-secondary, #606266);
  background: transparent;
  transition: background 0.15s, color 0.15s;
}
.dp-play-guide__tab.is-active {
  color: var(--dp-text-primary, #303133);
  background: var(--dp-panel-bg, #fff);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
}
.dp-play-guide__body--ranks {
  max-height: min(70vh, 520px);
  overflow-y: auto;
}
.dp-play-guide__ranks-hint {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--dp-text-secondary, #909399);
}
.dp-play-guide__first-run {
  padding: 10px 14px 14px;
  border-top: 1px solid var(--dp-subpanel-border, rgba(0, 0, 0, 0.08));
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.dp-play-guide__check {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: 13px;
  color: var(--dp-text-secondary, #606266);
  cursor: pointer;
}
.dp-play-guide__check input {
  margin-top: 3px;
}
.dp-play-guide__ok {
  align-self: flex-end;
  padding: 8px 16px;
  font-size: 14px;
  font-weight: 600;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  color: #fff;
  background: var(--dp-accent, #409eff);
}
</style>
