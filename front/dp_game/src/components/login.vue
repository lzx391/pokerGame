<template>
  <div class="login-box">
    <h1 class="login-box__title">登录</h1>
    <p class="login-box__hint">使用已注册的昵称与密码进入猫咪牌局大厅</p>

    <form class="login-form" @submit.prevent="login">
      <div class="form-item">
        <label for="login-nickname">昵称</label>
        <input
          id="login-nickname"
          v-model="nickname"
          type="text"
          placeholder="请输入昵称"
          autocomplete="username"
        >
      </div>

      <div class="form-item">
        <label for="login-password">密码</label>
        <input
          id="login-password"
          v-model="password"
          type="password"
          placeholder="请输入密码"
          autocomplete="current-password"
        >
      </div>

      <button type="submit" class="login-btn">
        登录
      </button>
    </form>
  </div>
</template>

<script>
import { ensureDpUserIdInStorage } from '@/utils/dpEnsureUserId'
import { dpResultSuccess, dpResultData, dpResultMessage } from '@/utils/dpApiResult'
import { flagCatTutorialAfterLogin } from '@/constants/dpCatThemeCopy'
import { enterLobbyAfterAuth } from '@/utils/dpAuthEnterLobby'

export default {
  inject: {
    dpAuthStage: { default: null }
  },
  data() {
    return {
      nickname: '',
      password: ''
    }
  },
  async created() {
    const raw = localStorage.getItem('userInfo')
    if (!raw) {
      return
    }
    try {
      const user = JSON.parse(raw)
      if (user && user.nickname && user.password) {
        this.nickname = user.nickname
        this.password = user.password
        await ensureDpUserIdInStorage(this.$http)
        enterLobbyAfterAuth(this.$router, this, { showMessage: false })
      }
    } catch (e) {
      console.error('读取本地用户信息失败', e)
      localStorage.removeItem('userInfo')
    }
  },
  methods: {
    showAuthError(message) {
      if (this.dpAuthStage && typeof this.dpAuthStage.showAuthError === 'function') {
        this.dpAuthStage.showAuthError(message)
        return
      }
      if (this.$message) {
        this.$message.error({ message: message, duration: 3000 })
      }
    },
    login() {
      if (this.dpAuthStage && (!this.dpAuthStage.contentInteractive || this.dpAuthStage.showErrorFace)) {
        return
      }
      if (!this.nickname || !this.password) {
        this.showAuthError('请输入昵称和密码')
        return
      }
      this.$http
        .get('/dpUser/loginProfile', {
          params: {
            nickname: this.nickname,
            password: this.password
          }
        })
        .then((res) => {
          console.log('登录结果：', res.data)

          var d = res.data
          if (dpResultSuccess(d)) {
            var payload = dpResultData(d) || {}
            flagCatTutorialAfterLogin()
            var row = {
              nickname: payload.nickname || this.nickname,
              password: this.password,
              userId: payload.userId
            }
            if (payload.token) row.token = payload.token
            localStorage.setItem('userInfo', JSON.stringify(row))
            enterLobbyAfterAuth(this.$router, this, { message: '登录成功' })
          } else {
            this.showAuthError('登录失败：' + dpResultMessage(d))
          }
        })
        .catch((err) => {
          console.error('请求失败', err)
          this.showAuthError('登录请求异常，请重试')
        })
    }
  }
}
</script>

<style scoped>
.login-form {
  margin: 0;
  padding: 0;
}
.login-box {
  width: 100%;
  max-width: 340px;
  margin: 0 auto;
  text-align: center;
}
.login-box__title {
  margin: 0 0 8px;
  font-size: 1.35rem;
  font-weight: 600;
  color: var(--dp-text-primary, inherit);
}
.login-box__hint {
  margin: 0 0 20px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--dp-text-muted, #909399);
}
.form-item {
  margin: 14px 0;
  text-align: left;
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.form-item label {
  flex: 0 0 48px;
  font-size: 14px;
  color: var(--dp-text-secondary, #666);
}
.form-item input {
  flex: 1 1 180px;
  min-width: 0;
  height: 40px;
  padding: 0 12px;
}
.login-btn {
  width: 100%;
  max-width: 280px;
  height: 42px;
  margin-top: 18px;
  font-size: 15px;
}
</style>
