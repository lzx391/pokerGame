<template>
  <transition name="dp-retro-confirm">
    <div v-if="dialogVisible" class="dp-retro-confirm" @click.self="onBackdropClick">
      <div
        class="dp-retro-confirm__shell"
        role="alertdialog"
        :aria-labelledby="titleId"
        :aria-describedby="bodyId"
      >
        <div class="dp-retro-confirm__scanlines" aria-hidden="true" />
        <div class="dp-retro-confirm__head">
          <span :id="titleId" class="dp-retro-confirm__title">{{ title }}</span>
          <button type="button" class="dp-retro-confirm__close" aria-label="关闭" @click="cancel">[X]</button>
        </div>
        <div :id="bodyId" class="dp-retro-confirm__body">
          <p v-if="prompt" class="dp-retro-confirm__prompt">{{ prompt }}</p>
          <p v-for="(line, idx) in logs" :key="idx" class="dp-retro-confirm__log">{{ line }}</p>
          <p v-if="message" class="dp-retro-confirm__message">{{ message }}</p>
        </div>
        <div class="dp-retro-confirm__actions">
          <button type="button" class="dp-retro-confirm__btn" @click="cancel">{{ cancelLabel }}</button>
          <button
            type="button"
            class="dp-retro-confirm__btn"
            :class="danger ? 'dp-retro-confirm__btn--danger' : 'dp-retro-confirm__btn--primary'"
            @click="confirm"
          >
            {{ confirmLabel }}
          </button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
export default {
  name: 'DpRetroConfirmDialog',
  props: {
    visible: { type: Boolean, default: false },
    title: { type: String, required: true },
    prompt: { type: String, default: '' },
    message: { type: String, default: '' },
    logs: { type: Array, default: function () { return [] } },
    confirmLabel: { type: String, default: 'EXECUTE' },
    cancelLabel: { type: String, default: 'ABORT' },
    danger: { type: Boolean, default: false },
    closeOnBackdrop: { type: Boolean, default: true }
  },
  computed: {
    dialogVisible: {
      get: function () {
        return this.visible
      },
      set: function (v) {
        this.$emit('update:visible', v)
      }
    },
    titleId: function () {
      return 'dp-retro-confirm-title-' + this._uid
    },
    bodyId: function () {
      return 'dp-retro-confirm-body-' + this._uid
    }
  },
  watch: {
    visible: function (v) {
      if (v) {
        document.addEventListener('keydown', this.onDocumentKeydown)
      } else {
        document.removeEventListener('keydown', this.onDocumentKeydown)
      }
    }
  },
  beforeDestroy: function () {
    document.removeEventListener('keydown', this.onDocumentKeydown)
  },
  methods: {
    onDocumentKeydown: function (e) {
      if (!this.visible) return
      if (e.key === 'Escape') {
        e.preventDefault()
        this.cancel()
      } else if (e.key === 'Enter') {
        e.preventDefault()
        this.confirm()
      }
    },
    onBackdropClick: function () {
      if (this.closeOnBackdrop) {
        this.cancel()
      }
    },
    cancel: function () {
      this.dialogVisible = false
      this.$emit('cancel')
    },
    confirm: function () {
      this.dialogVisible = false
      this.$emit('confirm')
    }
  }
}
</script>

<style scoped>
.dp-retro-confirm {
  position: fixed;
  inset: 0;
  z-index: 9360;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(0, 0, 0, 0.55);
}
.dp-retro-confirm-enter-active,
.dp-retro-confirm-leave-active {
  transition: opacity 0.15s ease;
}
.dp-retro-confirm-enter,
.dp-retro-confirm-leave-to {
  opacity: 0;
}
.dp-retro-confirm__shell {
  position: relative;
  width: min(440px, 100%);
  background: rgba(8, 10, 12, 0.96);
  border: 2px solid rgba(74, 246, 38, 0.34);
  box-shadow: 0 0 0 1px #000, 0 12px 40px rgba(0, 0, 0, 0.65);
  font-family: 'Courier New', ui-monospace, 'PingFang SC', monospace;
  overflow: hidden;
}
.dp-retro-confirm__scanlines {
  position: absolute;
  inset: 0;
  z-index: 2;
  pointer-events: none;
  background: repeating-linear-gradient(to bottom, transparent 0 2px, rgba(0, 0, 0, 0.12) 2px 3px);
  background-size: 100% 3px;
  opacity: 0.16;
}
.dp-retro-confirm__head {
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
.dp-retro-confirm__title {
  font-family: 'Press Start 2P', monospace;
  font-size: 9px;
  color: #4af626;
  text-shadow: 0 0 6px rgba(74, 246, 38, 0.55);
  letter-spacing: 0.04em;
}
.dp-retro-confirm__close {
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
.dp-retro-confirm__close:hover {
  background: #4af626;
  color: #080a0c;
}
.dp-retro-confirm__body {
  position: relative;
  z-index: 1;
  padding: 14px 16px 10px;
  font-size: 12px;
  line-height: 1.55;
}
.dp-retro-confirm__prompt {
  margin: 0 0 6px;
  color: #ffe066;
  text-shadow: 0 0 4px rgba(255, 224, 102, 0.35);
}
.dp-retro-confirm__log {
  margin: 0 0 4px;
  color: #72f052;
  text-shadow: 0 0 3px rgba(114, 240, 82, 0.3);
}
.dp-retro-confirm__message {
  margin: 10px 0 0;
  color: #e0f0d8;
  text-shadow: 0 0 3px rgba(224, 240, 216, 0.2);
}
.dp-retro-confirm__actions {
  position: relative;
  z-index: 3;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 10px 16px 14px;
  border-top: 1px solid rgba(74, 246, 38, 0.1);
}
.dp-retro-confirm__btn {
  min-width: 88px;
  padding: 8px 12px;
  border: 1px solid rgba(74, 246, 38, 0.28);
  background: #0a0c0e;
  color: #4af626;
  font-family: 'Press Start 2P', monospace;
  font-size: 8px;
  cursor: pointer;
}
.dp-retro-confirm__btn:hover {
  background: rgba(74, 246, 38, 0.12);
}
.dp-retro-confirm__btn--primary {
  border-color: #4af626;
  box-shadow: 0 0 8px rgba(74, 246, 38, 0.25);
}
.dp-retro-confirm__btn--primary:hover {
  background: #4af626;
  color: #080a0c;
}
.dp-retro-confirm__btn--danger {
  border-color: rgba(255, 102, 102, 0.55);
  color: #ff6666;
  box-shadow: 0 0 8px rgba(255, 102, 102, 0.2);
}
.dp-retro-confirm__btn--danger:hover {
  background: #ff6666;
  color: #080a0c;
}
</style>
