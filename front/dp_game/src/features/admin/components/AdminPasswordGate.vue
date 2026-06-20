<template>
  <el-dialog
    v-if="!isRetro8bit"
    :visible.sync="dialogVisible"
    title="管理员模式 · 访问验证"
    width="420px"
    top="18vh"
    custom-class="dp-admin-gate"
    append-to-body
    :close-on-click-modal="false"
    @closed="onClosed"
  >
    <p class="dp-admin-gate__hint">
      请输入管理员访问密码。验证通过后，本浏览器标签页内可进入管理页。
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
    <p v-if="errorMessage" class="dp-admin-gate__error" role="alert">{{ errorMessage }}</p>
    <span slot="footer" class="dialog-footer">
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认</el-button>
    </span>
  </el-dialog>

  <transition v-else name="dp-admin-gate-retro">
    <div v-if="dialogVisible" class="dp-admin-gate-retro" @click.self="dialogVisible = false">
      <div class="dp-admin-gate-retro__shell" role="dialog" aria-labelledby="dp-admin-gate-retro-title">
        <div class="dp-admin-gate-retro__scanlines" aria-hidden="true" />
        <div class="dp-admin-gate-retro__head">
          <span id="dp-admin-gate-retro-title" class="dp-admin-gate-retro__title">&gt; ADMIN // ACCESS</span>
          <button type="button" class="dp-admin-gate-retro__close" aria-label="关闭" @click="dialogVisible = false">[X]</button>
        </div>
        <div class="dp-admin-gate-retro__body">
          <p class="dp-admin-gate-retro__prompt">[ROOT] ACCESS:</p>
          <p class="dp-admin-gate-retro__log">&gt; admin console requires clearance password</p>
          <p class="dp-admin-gate-retro__log">&gt; session unlock persists until tab close</p>
          <div class="dp-admin-gate-retro__input-line">
            <span class="dp-admin-gate-retro__prompt">&gt;</span>
            <span class="dp-admin-gate-retro__preinput">{{ maskedDisplay }}</span><span class="dp-admin-gate-retro__cursor">█</span>
            <input
              ref="retroInput"
              v-model="passwordInput"
              class="dp-admin-gate-retro__hidden-input"
              type="password"
              autocomplete="off"
              autocorrect="off"
              spellcheck="false"
              :disabled="submitting"
              aria-label="管理员访问密码"
              @keydown.enter.prevent="submit"
            />
          </div>
          <p v-if="errorMessage" class="dp-admin-gate-retro__err" role="alert">&gt; ERR: {{ errorMessage }}</p>
        </div>
        <div class="dp-admin-gate-retro__actions">
          <button type="button" class="dp-admin-gate-retro__btn" :disabled="submitting" @click="dialogVisible = false">ABORT</button>
          <button type="button" class="dp-admin-gate-retro__btn dp-admin-gate-retro__btn--primary" :disabled="submitting" @click="submit">
            {{ submitting ? 'VERIFY...' : 'EXECUTE' }}
          </button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
import { dpResultData, dpResultMessage, dpResultSuccess } from '@shared/utils/dpApiResult'
import { dpAdminApi } from '@features/admin/api/adminApi'

export default {
  name: 'AdminPasswordGate',
  props: {
    visible: { type: Boolean, default: false },
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
      var n = (this.passwordInput || '').length
      return n > 0 ? '*'.repeat(n) : ''
    }
  },
  watch: {
    visible: function (v) {
      if (v) {
        this.passwordInput = ''
        this.errorMessage = ''
        this.submitting = false
        var self = this
        this.$nextTick(function () {
          if (self.isRetro8bit && self.$refs.retroInput) {
            self.$refs.retroInput.focus()
          }
        })
      }
    }
  },
  methods: {
    onClosed: function () {
      this.passwordInput = ''
      this.errorMessage = ''
      this.submitting = false
    },
    submit: async function () {
      var pwd = (this.passwordInput || '').trim()
      if (!pwd) {
        this.errorMessage = '请输入密码'
        return
      }
      this.submitting = true
      this.errorMessage = ''
      try {
        var res = await dpAdminApi(this.$http).verifyPassword(pwd)
        var body = res.data
        if (dpResultSuccess(body)) {
          var data = dpResultData(body) || {}
          if (data.verified) {
            sessionStorage.setItem('dp_admin_unlock', '1')
            this.dialogVisible = false
            this.$emit('verified')
            return
          }
        }
        this.errorMessage = dpResultMessage(body) || '密码错误'
      } catch (e) {
        this.errorMessage = '验证失败，请稍后重试'
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style scoped>
.dp-admin-gate__hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--dp-text-muted);
  line-height: 1.5;
}
.dp-admin-gate__error {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--dp-danger);
}
</style>

<style>
.dp-admin-gate-retro {
  position: fixed;
  inset: 0;
  z-index: 5000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.72);
}
.dp-admin-gate-retro__shell {
  position: relative;
  width: min(420px, 92vw);
  border: 2px solid #4a9;
  background: #0a0f0a;
  color: #9f9;
  font-family: 'Courier New', monospace;
  box-shadow: 0 0 24px rgba(74, 170, 153, 0.35);
}
.dp-admin-gate-retro__scanlines {
  pointer-events: none;
  position: absolute;
  inset: 0;
  background: repeating-linear-gradient(0deg, transparent 0 2px, rgba(0, 0, 0, 0.15) 2px 4px);
}
.dp-admin-gate-retro__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 1px solid #4a9;
}
.dp-admin-gate-retro__title {
  font-size: 13px;
  letter-spacing: 0.04em;
}
.dp-admin-gate-retro__close {
  border: none;
  background: none;
  color: #9f9;
  cursor: pointer;
  font-family: inherit;
}
.dp-admin-gate-retro__body {
  padding: 14px 12px;
}
.dp-admin-gate-retro__prompt,
.dp-admin-gate-retro__log {
  margin: 0 0 6px;
  font-size: 12px;
}
.dp-admin-gate-retro__input-line {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 10px;
  position: relative;
}
.dp-admin-gate-retro__preinput {
  letter-spacing: 0.12em;
}
.dp-admin-gate-retro__cursor {
  animation: dp-admin-gate-blink 1s step-end infinite;
}
@keyframes dp-admin-gate-blink {
  50% { opacity: 0; }
}
.dp-admin-gate-retro__hidden-input {
  position: absolute;
  inset: 0;
  opacity: 0;
  width: 100%;
  height: 100%;
  cursor: text;
}
.dp-admin-gate-retro__err {
  margin: 8px 0 0;
  color: #f66;
  font-size: 12px;
}
.dp-admin-gate-retro__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 12px;
  border-top: 1px solid #4a9;
}
.dp-admin-gate-retro__btn {
  padding: 6px 12px;
  border: 1px solid #4a9;
  background: #0a0f0a;
  color: #9f9;
  font-family: inherit;
  cursor: pointer;
}
.dp-admin-gate-retro__btn--primary {
  background: #1a3a2a;
}
.dp-admin-gate-retro__btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>
