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

      <div class="login-actions" :class="{ 'login-actions--retro8bit': isRetro8bit }">
        <button type="submit" class="login-btn">
          登录
        </button>
        <button
          v-if="isRetro8bit"
          type="button"
          class="github-login-btn github-login-btn--pixel"
          @click="loginWithGitHub"
        >
          GITHUB登录
        </button>
      </div>
    </form>

    <template v-if="!isRetro8bit">
      <div class="oauth-divider">
        <span>或</span>
      </div>

      <div class="oauth-buttons">
        <button type="button" class="github-login-btn" @click="loginWithGitHub">
          <span class="github-login-btn__icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor">
              <path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0 0 24 12c0-6.63-5.37-12-12-12z"/>
            </svg>
          </span>
          GitHub 登录
        </button>
        <button type="button" class="gitee-login-btn" @click="loginWithGitee">
          Gitee 登录
        </button>
      </div>
    </template>
  </div>
</template>

<script>
import { mapState } from 'vuex'
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
  computed: {
    ...mapState('dpGame', ['gameUiTheme']),
    isRetro8bit() {
      return this.gameUiTheme === 'retro8bit'
    }
  },
  async created() {
    if (this.$route.path !== '/login') {
      return
    }
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
    },
    loginWithGitHub() {
      this.$http.get('/oauth/github/authorize-url')
        .then((res) => {
          const d = res.data
          if (dpResultSuccess(d)) {
            const payload = dpResultData(d) || {}
            window.location.href = payload.url
          } else {
            this.showAuthError(dpResultMessage(d) || '获取 GitHub 授权链接失败')
          }
        })
        .catch((err) => {
          console.error('获取 GitHub 授权链接失败', err)
          this.showAuthError('获取 GitHub 授权链接失败')
        })
    },
    loginWithGitee() {
      this.$http.get('/oauth/gitee/authorize-url')
        .then((res) => {
          const d = res.data
          if (dpResultSuccess(d)) {
            const payload = dpResultData(d) || {}
            window.location.href = payload.url
          } else {
            this.showAuthError(dpResultMessage(d) || '获取 Gitee 授权链接失败')
          }
        })
        .catch((err) => {
          console.error('获取 Gitee 授权链接失败', err)
          this.showAuthError('获取 Gitee 授权链接失败')
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
.login-actions--retro8bit {
  display: flex;
  align-items: stretch;
  gap: 10px;
  width: 100%;
  max-width: 280px;
  margin: 18px auto 0;
}
.login-actions--retro8bit .login-btn,
.login-actions--retro8bit .github-login-btn--pixel {
  flex: 1 1 0;
  min-width: 0;
  width: auto;
  max-width: none;
  margin-top: 0;
}
.login-btn {
  width: 100%;
  max-width: 280px;
  height: 42px;
  margin-top: 18px;
  font-size: 15px;
}
.oauth-divider {
  display: flex;
  align-items: center;
  max-width: 280px;
  margin: 16px auto 12px;
  color: var(--dp-text-muted, #909399);
  font-size: 12px;
}
.oauth-divider::before,
.oauth-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--dp-text-muted, #909399);
  opacity: 0.35;
}
.oauth-divider span {
  padding: 0 12px;
}
.oauth-buttons {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  max-width: 280px;
  margin: 0 auto;
}
.github-login-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: 100%;
  max-width: 280px;
  height: 42px;
  font-size: 14px;
  font-weight: 500;
  color: #fff;
  background: #24292e;
  border: 1px solid #444d56;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}
.github-login-btn:hover {
  background: #2f363d;
}
.github-login-btn__icon {
  display: flex;
  align-items: center;
}
.gitee-login-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  max-width: 280px;
  height: 42px;
  font-size: 14px;
  font-weight: 500;
  color: #fff;
  background: #c71d23;
  border: 1px solid #a8181d;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}
.gitee-login-btn:hover {
  background: #d42a30;
}
</style>
