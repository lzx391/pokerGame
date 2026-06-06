<template>
  <el-dialog
      v-if="!isRetro8bit"
      :visible.sync="dialogVisible"
      title="实验排牌 · 访问验证"
      width="420px"
      top="18vh"
      custom-class="dp-deck-preset-gate"
      append-to-body
      :close-on-click-modal="false"
      @closed="onClosed"
  >
    <p class="dp-deck-preset-gate__hint">
      请输入实验玩法访问密码。验证通过后，本浏览器标签页内可继续使用预设下局牌序。
    </p>
    <el-input
        v-model="passwordInput"
        type="password"
        placeholder="访问密码"
        show-password
        autocomplete="off"
        :disabled="submitting"
        @keyup.enter.native="submit"
    />
    <p v-if="errorMessage" class="dp-deck-preset-gate__error" role="alert">{{ errorMessage }}</p>
    <span slot="footer" class="dialog-footer">
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认</el-button>
    </span>
  </el-dialog>

  <transition v-else name="dp-deck-preset-gate-retro">
    <div v-if="dialogVisible" class="dp-deck-preset-gate-retro" @click.self="dialogVisible = false">
      <div class="dp-deck-preset-gate-retro__shell" role="dialog" aria-labelledby="dp-deck-preset-gate-retro-title">
        <div class="dp-deck-preset-gate-retro__scanlines" aria-hidden="true" />
        <div class="dp-deck-preset-gate-retro__head">
          <span id="dp-deck-preset-gate-retro-title" class="dp-deck-preset-gate-retro__title">&gt; DECK_PRESET // ACCESS</span>
          <button type="button" class="dp-deck-preset-gate-retro__close" aria-label="关闭" @click="dialogVisible = false">[X]</button>
        </div>
        <div class="dp-deck-preset-gate-retro__body">
          <p class="dp-deck-preset-gate-retro__prompt">[ROOT] ACCESS:</p>
          <p class="dp-deck-preset-gate-retro__log">&gt; experimental deck preset requires clearance password</p>
          <p class="dp-deck-preset-gate-retro__log">&gt; session unlock persists until tab close</p>
          <div class="dp-deck-preset-gate-retro__input-line">
            <span class="dp-deck-preset-gate-retro__prompt">&gt;</span>
            <span class="dp-deck-preset-gate-retro__preinput">{{ maskedDisplay }}</span><span class="dp-deck-preset-gate-retro__cursor">█</span>
            <input
                ref="retroInput"
                v-model="passwordInput"
                class="dp-deck-preset-gate-retro__hidden-input"
                type="password"
                autocomplete="off"
                autocorrect="off"
                spellcheck="false"
                :disabled="submitting"
                aria-label="实验排牌访问密码"
                @keydown.enter.prevent="submit"
            />
          </div>
          <p v-if="errorMessage" class="dp-deck-preset-gate-retro__err" role="alert">&gt; ERR: {{ errorMessage }}</p>
        </div>
        <div class="dp-deck-preset-gate-retro__actions">
          <button type="button" class="dp-deck-preset-gate-retro__btn" :disabled="submitting" @click="dialogVisible = false">ABORT</button>
          <button type="button" class="dp-deck-preset-gate-retro__btn dp-deck-preset-gate-retro__btn--primary" :disabled="submitting" @click="submit">
            {{ submitting ? 'VERIFY...' : 'EXECUTE' }}
          </button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
import { dpResultData, dpResultMessage, dpResultSuccess } from '@/utils/dpApiResult'

export default {
  name: 'GameDeckPresetPasswordGate',
  props: {
    visible: { type: Boolean, default: false },
    roomId: { type: String, default: '' },
    requesterNickname: { type: String, default: '' },
    gameUiTheme: { type: String, default: 'default' }
  },
  data: function () {
    return {
      passwordInput: '',
      errorMessage: '',
      submitting: false
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
    isRetro8bit: function () {
      return this.gameUiTheme === 'retro8bit'
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
      }
    }
  },
  methods: {
    focusInput: function () {
      var self = this
      this.$nextTick(function () {
        if (self.isRetro8bit && self.$refs.retroInput) {
          self.$refs.retroInput.focus()
        }
      })
    },
    onClosed: function () {
      this.passwordInput = ''
      this.errorMessage = ''
    },
    submit: async function () {
      if (this.submitting) return
      var pwd = (this.passwordInput || '').trim()
      if (!pwd) {
        this.errorMessage = this.isRetro8bit ? 'PASSWORD REQUIRED' : '请输入访问密码'
        return
      }
      if (!this.roomId || !this.requesterNickname) {
        this.errorMessage = this.isRetro8bit ? 'ROOM CONTEXT LOST' : '房间信息缺失，请刷新后重试'
        return
      }
      this.submitting = true
      this.errorMessage = ''
      try {
        var res = await this.$http.post('/dpRoom/verifyExperimentalDeckPassword', {
          roomId: this.roomId,
          requesterNickname: this.requesterNickname,
          experimentalPassword: pwd
        })
        var body = res.data
        if (!dpResultSuccess(body)) {
          this.errorMessage = dpResultMessage(body) || (this.isRetro8bit ? 'ACCESS DENIED' : '密码验证失败')
          return
        }
        var d = dpResultData(body) || {}
        this.$emit('verified', pwd)
        this.dialogVisible = false
        if (d.message && !this.isRetro8bit) {
          this.$message.success(d.message)
        }
      } catch (err) {
        this.errorMessage = this.isRetro8bit ? 'NETWORK ERROR' : ('网络错误: ' + err.message)
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style scoped>
.dp-deck-preset-gate__hint {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.5;
  color: #595959;
}
.dp-deck-preset-gate__error {
  margin: 10px 0 0;
  font-size: 13px;
  color: #cf1322;
}

/* retro8bit CRT gate */
.dp-deck-preset-gate-retro {
  position: fixed;
  inset: 0;
  z-index: 9350;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(0, 0, 0, 0.55);
}
.dp-deck-preset-gate-retro-enter-active,
.dp-deck-preset-gate-retro-leave-active {
  transition: opacity 0.15s ease;
}
.dp-deck-preset-gate-retro-enter,
.dp-deck-preset-gate-retro-leave-to {
  opacity: 0;
}
.dp-deck-preset-gate-retro__shell {
  position: relative;
  width: min(440px, 100%);
  background: rgba(8, 10, 12, 0.96);
  border: 2px solid rgba(74, 246, 38, 0.34);
  box-shadow: 0 0 0 1px #000, 0 12px 40px rgba(0, 0, 0, 0.65);
  font-family: 'Courier New', ui-monospace, 'PingFang SC', monospace;
  overflow: hidden;
}
.dp-deck-preset-gate-retro__scanlines {
  position: absolute;
  inset: 0;
  z-index: 2;
  pointer-events: none;
  background: repeating-linear-gradient(to bottom, transparent 0 2px, rgba(0, 0, 0, 0.12) 2px 3px);
  background-size: 100% 3px;
  opacity: 0.16;
}
.dp-deck-preset-gate-retro__head {
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
.dp-deck-preset-gate-retro__title {
  font-family: 'Press Start 2P', monospace;
  font-size: 9px;
  color: #4af626;
  text-shadow: 0 0 6px rgba(74, 246, 38, 0.55);
  letter-spacing: 0.04em;
}
.dp-deck-preset-gate-retro__close {
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
.dp-deck-preset-gate-retro__close:hover {
  background: #4af626;
  color: #080a0c;
}
.dp-deck-preset-gate-retro__body {
  position: relative;
  z-index: 1;
  padding: 14px 16px 10px;
  font-size: 12px;
  line-height: 1.55;
}
.dp-deck-preset-gate-retro__prompt {
  margin: 0 0 6px;
  color: #ffe066;
  text-shadow: 0 0 4px rgba(255, 224, 102, 0.35);
}
.dp-deck-preset-gate-retro__log {
  margin: 0 0 4px;
  color: #72f052;
  text-shadow: 0 0 3px rgba(114, 240, 82, 0.3);
}
.dp-deck-preset-gate-retro__input-line {
  position: relative;
  display: flex;
  align-items: center;
  margin-top: 12px;
  padding: 8px 0 4px;
  border-top: 1px solid rgba(74, 246, 38, 0.14);
}
.dp-deck-preset-gate-retro__preinput {
  color: #e0f0d8;
  letter-spacing: 0.08em;
}
.dp-deck-preset-gate-retro__cursor {
  color: #4af626;
  text-shadow: 0 0 10px rgba(74, 246, 38, 0.8);
  animation: dp-deck-preset-gate-blink 0.8s step-end infinite;
}
@keyframes dp-deck-preset-gate-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
.dp-deck-preset-gate-retro__hidden-input {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: text;
}
.dp-deck-preset-gate-retro__err {
  margin: 10px 0 0;
  color: #ff6666;
  text-shadow: 0 0 4px rgba(255, 102, 102, 0.35);
}
.dp-deck-preset-gate-retro__actions {
  position: relative;
  z-index: 3;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 10px 16px 14px;
  border-top: 1px solid rgba(74, 246, 38, 0.1);
}
.dp-deck-preset-gate-retro__btn {
  min-width: 88px;
  padding: 8px 12px;
  border: 1px solid rgba(74, 246, 38, 0.28);
  background: #0a0c0e;
  color: #4af626;
  font-family: 'Press Start 2P', monospace;
  font-size: 8px;
  cursor: pointer;
}
.dp-deck-preset-gate-retro__btn:hover:not(:disabled) {
  background: rgba(74, 246, 38, 0.12);
}
.dp-deck-preset-gate-retro__btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.dp-deck-preset-gate-retro__btn--primary {
  border-color: #4af626;
  box-shadow: 0 0 8px rgba(74, 246, 38, 0.25);
}
.dp-deck-preset-gate-retro__btn--primary:hover:not(:disabled) {
  background: #4af626;
  color: #080a0c;
}
</style>
