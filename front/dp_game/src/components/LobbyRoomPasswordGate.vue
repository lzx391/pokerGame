<template>
  <transition name="dp-lobby-room-pw-gate">
    <div v-if="dialogVisible" class="dp-lobby-room-pw-gate" @click.self="close">
      <div
          class="dp-lobby-room-pw-gate__shell"
          :class="{ 'dp-lobby-room-pw-gate__shell--shake': errorShaking }"
          role="dialog"
          aria-labelledby="dp-lobby-room-pw-gate-title"
      >
        <div class="dp-lobby-room-pw-gate__scanlines" aria-hidden="true" />
        <div class="dp-lobby-room-pw-gate__head">
          <span id="dp-lobby-room-pw-gate-title" class="dp-lobby-room-pw-gate__title">&gt; ROOM // JOIN</span>
          <button type="button" class="dp-lobby-room-pw-gate__close" aria-label="关闭" @click="close">[X]</button>
        </div>
        <div class="dp-lobby-room-pw-gate__body">
          <p class="dp-lobby-room-pw-gate__prompt">[LOBBY] ACCESS:</p>
          <p class="dp-lobby-room-pw-gate__log">&gt; clearance required to enter locked room</p>
          <p v-if="roomId" class="dp-lobby-room-pw-gate__log">&gt; target: {{ roomId }}</p>
          <div class="dp-lobby-room-pw-gate__input-line">
            <span class="dp-lobby-room-pw-gate__prompt">&gt;</span>
            <span class="dp-lobby-room-pw-gate__preinput">{{ maskedDisplay }}</span><span class="dp-lobby-room-pw-gate__cursor">█</span>
            <input
                ref="retroInput"
                v-model="passwordInput"
                class="dp-lobby-room-pw-gate__hidden-input"
                type="password"
                autocomplete="off"
                autocorrect="off"
                spellcheck="false"
                :disabled="submitting"
                aria-label="房间密码"
                @keydown.enter.prevent="submit"
            />
          </div>
          <p v-if="errorMessage" class="dp-lobby-room-pw-gate__err" role="alert">&gt; ERR: {{ errorMessage }}</p>
        </div>
        <div class="dp-lobby-room-pw-gate__actions">
          <button type="button" class="dp-lobby-room-pw-gate__btn" :disabled="submitting" @click="close">ABORT</button>
          <button
              type="button"
              class="dp-lobby-room-pw-gate__btn dp-lobby-room-pw-gate__btn--primary"
              :disabled="submitting"
              @click="submit"
          >
            {{ submitting ? 'JOIN...' : 'EXECUTE' }}
          </button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
import { dpResultMessage, dpResultSuccess } from '@/utils/dpApiResult'

export default {
  name: 'LobbyRoomPasswordGate',
  props: {
    visible: { type: Boolean, default: false },
    roomId: { type: String, default: '' },
    nickname: { type: String, default: '' },
    userId: { type: [String, Number], default: null }
  },
  data: function () {
    return {
      passwordInput: '',
      errorMessage: '',
      submitting: false,
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
        this.errorMessage = ''
        this.focusInput()
        document.addEventListener('keydown', this.onDocumentKeydown)
      } else {
        document.removeEventListener('keydown', this.onDocumentKeydown)
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
    close: function () {
      if (this.submitting) return
      this.dialogVisible = false
      this.passwordInput = ''
      this.errorMessage = ''
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
    setError: function (msg) {
      this.errorMessage = msg
      this.triggerErrorShake()
    },
    submit: async function () {
      if (this.submitting) return
      var pwd = (this.passwordInput || '').trim()
      if (!pwd) {
        this.setError('PASSWORD REQUIRED')
        return
      }
      if (!this.roomId || !this.nickname) {
        this.setError('ROOM CONTEXT LOST')
        return
      }
      this.submitting = true
      this.errorMessage = ''
      try {
        var params = {
          roomId: this.roomId,
          nickname: this.nickname,
          roomPassword: pwd
        }
        if (this.userId != null && this.userId !== '') {
          params.userId = this.userId
        }
        var res = await this.$http.post('/dpRoom/joinRoom2', null, { params: params })
        var body = res.data
        if (!dpResultSuccess(body)) {
          this.setError(dpResultMessage(body) || 'ACCESS DENIED')
          return
        }
        this.$emit('joined', this.roomId)
        this.dialogVisible = false
        this.passwordInput = ''
        this.errorMessage = ''
      } catch (err) {
        this.setError('NETWORK ERROR')
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style scoped>
.dp-lobby-room-pw-gate {
  position: fixed;
  inset: 0;
  z-index: 9350;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(0, 0, 0, 0.55);
}
.dp-lobby-room-pw-gate-enter-active,
.dp-lobby-room-pw-gate-leave-active {
  transition: opacity 0.15s ease;
}
.dp-lobby-room-pw-gate-enter,
.dp-lobby-room-pw-gate-leave-to {
  opacity: 0;
}
.dp-lobby-room-pw-gate__shell {
  position: relative;
  width: min(440px, 100%);
  background: rgba(8, 10, 12, 0.96);
  border: 2px solid rgba(74, 246, 38, 0.34);
  box-shadow: 0 0 0 1px #000, 0 12px 40px rgba(0, 0, 0, 0.65);
  font-family: 'Courier New', ui-monospace, 'PingFang SC', monospace;
  overflow: hidden;
}
.dp-lobby-room-pw-gate__shell--shake {
  animation: dp-lobby-room-pw-gate-shake 0.42s ease-in-out;
}
@keyframes dp-lobby-room-pw-gate-shake {
  0%, 100% { transform: translateX(0); }
  20% { transform: translateX(-5px); }
  40% { transform: translateX(5px); }
  60% { transform: translateX(-3px); }
  80% { transform: translateX(3px); }
}
.dp-lobby-room-pw-gate__scanlines {
  position: absolute;
  inset: 0;
  z-index: 2;
  pointer-events: none;
  background: repeating-linear-gradient(to bottom, transparent 0 2px, rgba(0, 0, 0, 0.12) 2px 3px);
  background-size: 100% 3px;
  opacity: 0.16;
}
.dp-lobby-room-pw-gate__head {
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
.dp-lobby-room-pw-gate__title {
  font-family: 'Press Start 2P', monospace;
  font-size: 9px;
  color: #4af626;
  text-shadow: 0 0 6px rgba(74, 246, 38, 0.55);
  letter-spacing: 0.04em;
}
.dp-lobby-room-pw-gate__close {
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
.dp-lobby-room-pw-gate__close:hover {
  background: #4af626;
  color: #080a0c;
}
.dp-lobby-room-pw-gate__body {
  position: relative;
  z-index: 1;
  padding: 14px 16px 10px;
  font-size: 12px;
  line-height: 1.55;
}
.dp-lobby-room-pw-gate__prompt {
  margin: 0 0 6px;
  color: #ffe066;
  text-shadow: 0 0 4px rgba(255, 224, 102, 0.35);
}
.dp-lobby-room-pw-gate__log {
  margin: 0 0 4px;
  color: #72f052;
  text-shadow: 0 0 3px rgba(114, 240, 82, 0.3);
}
.dp-lobby-room-pw-gate__input-line {
  position: relative;
  display: flex;
  align-items: center;
  margin-top: 12px;
  padding: 8px 0 4px;
  border-top: 1px solid rgba(74, 246, 38, 0.14);
}
.dp-lobby-room-pw-gate__preinput {
  color: #e0f0d8;
  letter-spacing: 0.08em;
}
.dp-lobby-room-pw-gate__cursor {
  color: #4af626;
  text-shadow: 0 0 10px rgba(74, 246, 38, 0.8);
  animation: dp-lobby-room-pw-gate-blink 0.8s step-end infinite;
}
@keyframes dp-lobby-room-pw-gate-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
.dp-lobby-room-pw-gate__hidden-input {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: text;
}
.dp-lobby-room-pw-gate__err {
  margin: 10px 0 0;
  color: #ff6666;
  text-shadow: 0 0 4px rgba(255, 102, 102, 0.35);
}
.dp-lobby-room-pw-gate__actions {
  position: relative;
  z-index: 3;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 10px 16px 14px;
  border-top: 1px solid rgba(74, 246, 38, 0.1);
}
.dp-lobby-room-pw-gate__btn {
  min-width: 88px;
  padding: 8px 12px;
  border: 1px solid rgba(74, 246, 38, 0.28);
  background: #0a0c0e;
  color: #4af626;
  font-family: 'Press Start 2P', monospace;
  font-size: 8px;
  cursor: pointer;
}
.dp-lobby-room-pw-gate__btn:hover:not(:disabled) {
  background: rgba(74, 246, 38, 0.12);
}
.dp-lobby-room-pw-gate__btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.dp-lobby-room-pw-gate__btn--primary {
  border-color: #4af626;
  box-shadow: 0 0 8px rgba(74, 246, 38, 0.25);
}
.dp-lobby-room-pw-gate__btn--primary:hover:not(:disabled) {
  background: #4af626;
  color: #080a0c;
}
</style>
