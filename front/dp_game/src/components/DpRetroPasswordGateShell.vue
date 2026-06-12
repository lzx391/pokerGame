<template>
  <transition :name="transitionName">
    <div v-if="dialogVisible" :class="rootClasses" @click.self="onBackdropClick">
      <div
        class="dp-retro-pw-gate__shell"
        :class="{ 'dp-retro-pw-gate__shell--shake': errorShaking }"
        role="dialog"
        :aria-labelledby="titleId"
      >
        <div class="dp-retro-pw-gate__scanlines" aria-hidden="true" />
        <div class="dp-retro-pw-gate__head">
          <span :id="titleId" class="dp-retro-pw-gate__title">{{ title }}</span>
          <button type="button" class="dp-retro-pw-gate__close" aria-label="关闭" @click="close">[X]</button>
        </div>
        <div class="dp-retro-pw-gate__body">
          <p v-if="prompt" class="dp-retro-pw-gate__prompt">{{ prompt }}</p>
          <p v-for="(line, idx) in logs" :key="idx" class="dp-retro-pw-gate__log">{{ line }}</p>
          <div class="dp-retro-pw-gate__input-line">
            <span class="dp-retro-pw-gate__prompt">&gt;</span>
            <span class="dp-retro-pw-gate__preinput">{{ maskedDisplay }}</span><span class="dp-retro-pw-gate__cursor">█</span>
            <input
              ref="retroInput"
              v-model="passwordInput"
              class="dp-retro-pw-gate__hidden-input"
              type="password"
              autocomplete="off"
              autocorrect="off"
              spellcheck="false"
              :disabled="submitting"
              :aria-label="inputAriaLabel"
              @keydown.enter.prevent="onSubmit"
            />
          </div>
          <p v-if="errorMessage" class="dp-retro-pw-gate__err" role="alert">&gt; ERR: {{ errorMessage }}</p>
        </div>
        <div class="dp-retro-pw-gate__actions">
          <button type="button" class="dp-retro-pw-gate__btn" :disabled="submitting" @click="close">{{ cancelLabel }}</button>
          <button
            type="button"
            class="dp-retro-pw-gate__btn dp-retro-pw-gate__btn--primary"
            :disabled="submitting"
            @click="onSubmit"
          >
            {{ submitting ? primaryLoadingLabel : primaryLabel }}
          </button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
export default {
  name: 'DpRetroPasswordGateShell',
  props: {
    visible: { type: Boolean, default: false },
    title: { type: String, required: true },
    prompt: { type: String, default: '' },
    logs: { type: Array, default: function () { return [] } },
    errorMessage: { type: String, default: '' },
    submitting: { type: Boolean, default: false },
    primaryLabel: { type: String, default: 'EXECUTE' },
    primaryLoadingLabel: { type: String, default: 'VERIFY...' },
    cancelLabel: { type: String, default: 'ABORT' },
    inputAriaLabel: { type: String, default: '密码' },
    closeOnBackdrop: { type: Boolean, default: true },
    transitionName: { type: String, default: 'dp-retro-pw-gate' },
    rootClass: { type: String, default: 'dp-retro-pw-gate' }
  },
  data: function () {
    return {
      passwordInput: '',
      errorShaking: false,
      errorShakeTimer: null
    }
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
      return 'dp-retro-pw-gate-title-' + this._uid
    },
    rootClasses: function () {
      var classes = ['dp-retro-pw-gate']
      if (this.rootClass && this.rootClass !== 'dp-retro-pw-gate') {
        classes.push(this.rootClass)
      }
      return classes
    },
    maskedDisplay: function () {
      var len = (this.passwordInput || '').length
      if (!len) return ''
      return '*'.repeat(len)
    }
  },
  watch: {
    visible: function (v) {
      if (v) {
        this.passwordInput = ''
        this.focusInput()
        document.addEventListener('keydown', this.onDocumentKeydown)
      } else {
        document.removeEventListener('keydown', this.onDocumentKeydown)
      }
    },
    errorMessage: function (msg, prev) {
      if (msg && msg !== prev) {
        this.triggerErrorShake()
      }
    }
  },
  beforeDestroy: function () {
    document.removeEventListener('keydown', this.onDocumentKeydown)
    if (this.errorShakeTimer) {
      clearTimeout(this.errorShakeTimer)
      this.errorShakeTimer = null
    }
  },
  methods: {
    onDocumentKeydown: function (e) {
      if (!this.visible || this.submitting) return
      if (e.key === 'Escape') {
        e.preventDefault()
        this.close()
      }
    },
    focusInput: function () {
      var self = this
      this.$nextTick(function () {
        if (self.$refs.retroInput) {
          self.$refs.retroInput.focus()
        }
      })
    },
    onBackdropClick: function () {
      if (this.closeOnBackdrop) {
        this.close()
      }
    },
    close: function () {
      if (this.submitting) return
      this.dialogVisible = false
      this.passwordInput = ''
      this.$emit('close')
    },
    triggerErrorShake: function () {
      var self = this
      if (this.errorShakeTimer) {
        clearTimeout(this.errorShakeTimer)
      }
      this.errorShaking = false
      this.$nextTick(function () {
        self.errorShaking = true
        self.errorShakeTimer = setTimeout(function () {
          self.errorShaking = false
          self.errorShakeTimer = null
        }, 420)
      })
    },
    onSubmit: function () {
      if (this.submitting) return
      this.$emit('submit', (this.passwordInput || '').trim())
    },
    clearPassword: function () {
      this.passwordInput = ''
    }
  }
}
</script>

<style scoped>
.dp-retro-pw-gate {
  position: fixed;
  inset: 0;
  z-index: 9350;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(0, 0, 0, 0.55);
}
.dp-retro-pw-gate-enter-active,
.dp-retro-pw-gate-leave-active {
  transition: opacity 0.15s ease;
}
.dp-retro-pw-gate-enter,
.dp-retro-pw-gate-leave-to {
  opacity: 0;
}
.dp-retro-pw-gate__shell {
  position: relative;
  width: min(440px, 100%);
  background: rgba(8, 10, 12, 0.96);
  border: 2px solid rgba(74, 246, 38, 0.34);
  box-shadow: 0 0 0 1px #000, 0 12px 40px rgba(0, 0, 0, 0.65);
  font-family: 'Courier New', ui-monospace, 'PingFang SC', monospace;
  overflow: hidden;
}
.dp-retro-pw-gate__shell--shake {
  animation: dp-retro-pw-gate-shake 0.42s ease-in-out;
}
@keyframes dp-retro-pw-gate-shake {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-5px); }
  40% { transform: translateX(5px); }
  60% { transform: translateX(-3px); }
  80% { transform: translateX(3px); }
}
.dp-retro-pw-gate__scanlines {
  position: absolute;
  inset: 0;
  z-index: 2;
  pointer-events: none;
  background: repeating-linear-gradient(to bottom, transparent 0 2px, rgba(0, 0, 0, 0.12) 2px 3px);
  background-size: 100% 3px;
  opacity: 0.16;
}
.dp-retro-pw-gate__head {
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
.dp-retro-pw-gate__title {
  font-family: 'Press Start 2P', monospace;
  font-size: 9px;
  color: #4af626;
  text-shadow: 0 0 6px rgba(74, 246, 38, 0.55);
  letter-spacing: 0.04em;
}
.dp-retro-pw-gate__close {
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
.dp-retro-pw-gate__close:hover {
  background: #4af626;
  color: #080a0c;
}
.dp-retro-pw-gate__body {
  position: relative;
  z-index: 1;
  padding: 14px 16px 10px;
  font-size: 12px;
  line-height: 1.55;
}
.dp-retro-pw-gate__prompt {
  margin: 0 0 6px;
  color: #ffe066;
  text-shadow: 0 0 4px rgba(255, 224, 102, 0.35);
}
.dp-retro-pw-gate__log {
  margin: 0 0 4px;
  color: #72f052;
  text-shadow: 0 0 3px rgba(114, 240, 82, 0.3);
}
.dp-retro-pw-gate__input-line {
  position: relative;
  display: flex;
  align-items: center;
  margin-top: 12px;
  padding: 8px 0 4px;
  border-top: 1px solid rgba(74, 246, 38, 0.14);
}
.dp-retro-pw-gate__preinput {
  color: #e0f0d8;
  letter-spacing: 0.08em;
}
.dp-retro-pw-gate__cursor {
  color: #4af626;
  text-shadow: 0 0 10px rgba(74, 246, 38, 0.8);
  animation: dp-retro-pw-gate-blink 0.8s step-end infinite;
}
@keyframes dp-retro-pw-gate-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
.dp-retro-pw-gate__hidden-input {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: text;
}
.dp-retro-pw-gate__err {
  margin: 10px 0 0;
  color: #ff6666;
  text-shadow: 0 0 4px rgba(255, 102, 102, 0.35);
}
.dp-retro-pw-gate__actions {
  position: relative;
  z-index: 3;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 10px 16px 14px;
  border-top: 1px solid rgba(74, 246, 38, 0.1);
}
.dp-retro-pw-gate__btn {
  min-width: 88px;
  padding: 8px 12px;
  border: 1px solid rgba(74, 246, 38, 0.28);
  background: #0a0c0e;
  color: #4af626;
  font-family: 'Press Start 2P', monospace;
  font-size: 8px;
  cursor: pointer;
}
.dp-retro-pw-gate__btn:hover:not(:disabled) {
  background: rgba(74, 246, 38, 0.12);
}
.dp-retro-pw-gate__btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.dp-retro-pw-gate__btn--primary {
  border-color: #4af626;
  box-shadow: 0 0 8px rgba(74, 246, 38, 0.25);
}
.dp-retro-pw-gate__btn--primary:hover:not(:disabled) {
  background: #4af626;
  color: #080a0c;
}
</style>
