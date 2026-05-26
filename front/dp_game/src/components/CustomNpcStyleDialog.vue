<template>
  <div
    v-if="visible"
    class="dp-custom-npc-overlay"
    role="dialog"
    aria-modal="true"
    aria-labelledby="dp-custom-npc-title"
    @click.stop.self="onCancel"
  >
    <div class="dp-custom-npc-panel" @click.stop>
      <div class="dp-custom-npc-panel__head">
        <span id="dp-custom-npc-title" class="dp-custom-npc-panel__title">自定义 NPC 性格参数</span>
        <button
          type="button"
          class="dp-custom-npc-panel__close"
          aria-label="关闭"
          @click="onCancel"
        >
          ×
        </button>
      </div>
      <div class="dp-custom-npc-panel__body">
        <p class="custom-npc-style-dialog__intro">
          本批将加入 <strong>{{ pendingCount }}</strong> 个 BOT_CUSTOM，共用下面这一套参数（默认与 TAG 猫相同）。上桌后不可改，要试新参数请踢掉再重新添加。
        </p>
        <div class="custom-npc-style-dialog__form">
          <div
            v-for="field in styleFields"
            :key="field.key"
            class="custom-npc-style-dialog__item"
          >
            <div class="custom-npc-style-dialog__label-row">
              <span class="custom-npc-style-dialog__label-main">{{ field.label }}</span>
              <span class="custom-npc-style-dialog__label-hint">{{ field.hint }}</span>
            </div>
            <div class="custom-npc-style-dialog__row">
              <el-slider
                v-model="draft[field.key]"
                :min="0"
                :max="1"
                :step="0.01"
                :show-tooltip="true"
                :format-tooltip="formatSliderTooltip"
              />
              <el-input-number
                v-model="draft[field.key]"
                :min="0"
                :max="1"
                :step="0.01"
                :precision="2"
                controls-position="right"
                class="custom-npc-style-dialog__num"
              />
            </div>
          </div>
        </div>
      </div>
      <div class="dp-custom-npc-panel__foot">
        <button type="button" class="dp-custom-npc-panel__btn dp-custom-npc-panel__btn--ghost" @click="onCancel">
          取消
        </button>
        <button
          type="button"
          class="dp-custom-npc-panel__btn dp-custom-npc-panel__btn--primary"
          :disabled="submitting"
          @click="onConfirm"
        >
          {{ submitting ? '提交中…' : '确定添加' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import {
  CUSTOM_NPC_STYLE_FIELDS,
  NPC_STYLE_TAG_PRESET,
  clampNpcStyleProfile,
  cloneNpcStyleProfile
} from '../constants/npcStylePresets'

export default {
  name: 'CustomNpcStyleDialog',
  props: {
    visible: { type: Boolean, default: false },
    pendingCount: { type: Number, default: 1 },
    submitting: { type: Boolean, default: false }
  },
  data () {
    return {
      styleFields: CUSTOM_NPC_STYLE_FIELDS,
      draft: cloneNpcStyleProfile(NPC_STYLE_TAG_PRESET)
    }
  },
  watch: {
    visible: {
      immediate: true,
      handler: function (open) {
        if (open) {
          this.draft = cloneNpcStyleProfile(NPC_STYLE_TAG_PRESET)
        }
      }
    }
  },
  methods: {
    formatSliderTooltip (val) {
      return (Math.round(Number(val) * 100) / 100).toFixed(2)
    },
    onCancel () {
      this.$emit('cancel')
    },
    onConfirm () {
      if (this.submitting) return
      this.$emit('confirm', clampNpcStyleProfile(this.draft))
    }
  }
}
</script>

<style scoped>
.dp-custom-npc-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  box-sizing: border-box;
  background: rgba(0, 0, 0, 0.58);
}

.dp-custom-npc-panel {
  width: 100%;
  max-width: 520px;
  max-height: min(88vh, 720px);
  display: flex;
  flex-direction: column;
  background: var(--dp-panel-bg, #1e1e1e);
  color: var(--dp-text-primary, #e8e8e8);
  border: 1px solid var(--dp-panel-border, rgba(255, 255, 255, 0.12));
  border-radius: 14px;
  box-shadow: var(--dp-panel-shadow, 0 10px 40px rgba(0, 0, 0, 0.45));
  overflow: hidden;
}

.dp-custom-npc-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--dp-panel-border, rgba(255, 255, 255, 0.12));
  flex-shrink: 0;
}

.dp-custom-npc-panel__title {
  font-size: 16px;
  font-weight: 700;
}

.dp-custom-npc-panel__close {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 8px;
  background: var(--dp-input-bg, rgba(255, 255, 255, 0.08));
  color: var(--dp-text-primary, #fff);
  font-size: 22px;
  line-height: 1;
  cursor: pointer;
}

.dp-custom-npc-panel__body {
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding: 12px 14px;
  flex: 1 1 auto;
  min-height: 0;
}

.dp-custom-npc-panel__foot {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 12px 14px max(14px, env(safe-area-inset-bottom, 0px));
  border-top: 1px solid var(--dp-panel-border, rgba(255, 255, 255, 0.12));
  flex-shrink: 0;
}

.dp-custom-npc-panel__btn {
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  border: none;
}

.dp-custom-npc-panel__btn--ghost {
  background: var(--dp-btn-ghost-bg, rgba(255, 255, 255, 0.08));
  color: var(--dp-text-primary, #e8e8e8);
  border: 1px solid var(--dp-panel-border, rgba(255, 255, 255, 0.12));
}

.dp-custom-npc-panel__btn--primary {
  background: var(--dp-btn-primary-bg, #1677ff);
  color: var(--dp-btn-primary-fg, #fff);
}

.dp-custom-npc-panel__btn--primary:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.custom-npc-style-dialog__intro {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--dp-text-secondary, #595959);
}

.custom-npc-style-dialog__form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.custom-npc-style-dialog__label-row {
  margin-bottom: 6px;
}

.custom-npc-style-dialog__label-main {
  font-weight: 600;
  color: var(--dp-text-primary, #e8e8e8);
}

.custom-npc-style-dialog__label-hint {
  margin-left: 6px;
  font-size: 11px;
  color: var(--dp-text-muted, #8c8c8c);
}

.custom-npc-style-dialog__row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.custom-npc-style-dialog__row >>> .el-slider {
  flex: 1;
  min-width: 0;
}

.custom-npc-style-dialog__num {
  width: 108px;
  flex-shrink: 0;
}
</style>
