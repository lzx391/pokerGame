<template>
  <div class="register-form">
    <h2 class="register-form__title">用户注册</h2>
    <p class="register-form__hint">注册后即可用昵称登录并进入房间。昵称不能为纯数字。</p>

    <form class="register-form__body" @submit.prevent="handleRegister">
      <div class="form-item">
        <label for="reg-nickname">昵称</label>
        <input
          id="reg-nickname"
          v-model="form.nickname"
          type="text"
          placeholder="请设置登录昵称（不能为纯数字）"
          autocomplete="off"
        >
      </div>

      <div class="form-item">
        <label for="reg-password">密码</label>
        <input
          id="reg-password"
          v-model="form.password"
          type="password"
          placeholder="请设置登录密码"
          autocomplete="new-password"
        >
      </div>

      <button type="submit" class="register-btn">
        注册
      </button>
    </form>
  </div>
</template>

<script>
import { dpResultSuccess, dpResultData, dpResultMessage } from '@/utils/dpApiResult'
import { flagCatTutorialAfterLogin } from '@/constants/dpCatThemeCopy'
import { enterLobbyAfterAuth } from '@/utils/dpAuthEnterLobby'

export default {
  inject: {
    dpAuthStage: { default: null }
  },
  data() {
    return {
      form: {
        nickname: '',
        password: ''
      }
    }
  },
  methods: {
    showAuthError(message) {
      if (this.dpAuthStage && typeof this.dpAuthStage.showAuthError === 'function') {
        this.dpAuthStage.showAuthError(message)
        return
      }
      console.warn('[register] auth stage unavailable:', message)
    },
    handleRegister() {
      if (this.dpAuthStage && (!this.dpAuthStage.contentInteractive || this.dpAuthStage.showErrorFace)) {
        return
      }
      var nickname = this.form.nickname.trim()
      if (!nickname) {
        this.showAuthError('请输入昵称！')
        return
      }
      if (/^\d+$/.test(nickname)) {
        this.showAuthError('昵称不能为纯数字')
        return
      }
      if (!this.form.password) {
        this.showAuthError('请输入密码！')
        return
      }

      this.$http
        .post('/dpUser/registerUser', {
          nickname: nickname,
          password: this.form.password
        })
        .then((res) => {
          console.log('注册结果：', res.data)
          var d = res.data
          if (dpResultSuccess(d)) {
            var inner = dpResultData(d) || {}
            var msg = inner.message != null ? String(inner.message) : '注册成功'
            flagCatTutorialAfterLogin()
            var row = {
              nickname: inner.nickname != null ? String(inner.nickname) : this.form.nickname.trim(),
              password: this.form.password,
              userId: inner.userId
            }
            if (inner.token) row.token = String(inner.token)
            localStorage.setItem('userInfo', JSON.stringify(row))
            enterLobbyAfterAuth(this.$router, this, {
              message: msg + '，正在进入大厅'
            })
          } else {
            this.showAuthError(dpResultMessage(d))
          }
        })
        .catch((err) => {
          console.error('注册请求异常：', err)
          this.showAuthError('网络异常，请稍后再试！')
        })
    }
  }
}
</script>

<style scoped>
.register-form__body {
  margin: 0;
  padding: 0;
}
.register-form {
  width: 100%;
  max-width: 360px;
  margin: 0 auto;
  text-align: center;
}
.register-form__title {
  margin: 0 0 8px;
  font-size: 1.35rem;
  font-weight: 600;
  color: var(--dp-text-primary, inherit);
}
.register-form__hint {
  margin: 0 0 22px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--dp-text-muted, #909399);
}
.form-item {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  justify-content: flex-start;
  text-align: left;
  gap: 10px;
}
.form-item label {
  flex: 0 0 48px;
  font-size: 14px;
  color: var(--dp-text-secondary, #666);
}
.form-item input {
  flex: 1 1 auto;
  min-width: 0;
  height: 40px;
  padding: 0 12px;
  border-radius: 8px;
}
.register-btn {
  width: 100%;
  max-width: 280px;
  height: 44px;
  margin-top: 12px;
  font-size: 15px;
  font-weight: 600;
}
</style>
