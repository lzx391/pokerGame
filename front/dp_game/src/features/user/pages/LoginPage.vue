<template>
  <div
    class="login-box"
    :class="{
      'login-box--retro8bit': isRetro8bit
    }"
  >
    <div
      class="login-box__main"
      :class="{ 'login-box__main--dimmed': isRetro8bit && retroOAuthSceneVisible }"
    >
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
            :disabled="isRetro8bit && retroOAuthBusy"
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
            :disabled="isRetro8bit && retroOAuthBusy"
          >
        </div>

        <div class="login-actions" :class="{ 'login-actions--retro8bit': isRetro8bit }">
          <button
            type="submit"
            class="login-btn"
            :disabled="isRetro8bit && retroOAuthBusy"
          >
            登录
          </button>
          <button
            v-if="isRetro8bit"
            type="button"
            class="login-retro-alt-btn retro-pixel-btn"
            :disabled="retroOAuthBusy"
            @click="openRetroOAuth"
          >
            其他方式登录
          </button>
        </div>
      </form>
    </div>

    <div
      v-if="isRetro8bit && retroOAuthSceneVisible"
      ref="retroOAuthOverlay"
      class="login-retro-oauth-overlay"
      :class="{
        'login-retro-oauth-overlay--open': retroOAuthPhase === 'ready',
        'login-retro-oauth-overlay--busy': retroOAuthBusy
      }"
    >
      <div
        class="login-retro-oauth-overlay__dim"
        aria-hidden="true"
        @click="backRetroOAuth"
      />

      <div
        class="login-retro-oauth-sheet"
        :class="retroOAuthSheetPhaseClass"
        role="dialog"
        aria-label="第三方登录"
        aria-modal="true"
      >
        <div
          class="login-retro-oauth-sheet__shell"
          @animationend="onOAuthSheetAnimEnd"
        >
          <div class="login-retro-oauth-sheet__edge" aria-hidden="true" />
          <div class="login-retro-oauth-sheet__scanlines" aria-hidden="true" />
          <div class="login-retro-oauth-sheet__vignette" aria-hidden="true" />

          <div
            v-show="retroOAuthUseGlitch && retroOAuthPhase === 'snow'"
            class="login-retro-oauth-sheet__snow"
            :class="{ 'login-retro-oauth-sheet__snow--active': retroOAuthPhase === 'snow' }"
            aria-hidden="true"
          >
            <span class="login-retro-oauth-sheet__snow-noise" />
            <span class="login-retro-oauth-sheet__snow-bars" />
          </div>
          <div
            v-show="retroOAuthUseGlitch && retroOAuthPhase === 'reveal-flash'"
            class="login-retro-oauth-sheet__flash"
            aria-hidden="true"
          />

          <div
            ref="retroOAuthPanelInner"
            class="login-retro-oauth-sheet__inner"
            :class="{ 'login-retro-oauth-sheet__inner--ready': retroOAuthContentReady }"
            tabindex="-1"
          >
            <h2 class="login-retro-oauth__title">&gt; OAUTH_LINK</h2>
            <p class="login-retro-oauth__hint">选择授权平台继续</p>
            <ul
              class="login-retro-oauth__menu"
              role="menu"
              aria-label="第三方登录选项"
            >
              <li
                v-for="(item, idx) in retroOAuthMenuItems"
                :key="item.id"
                role="menuitem"
                class="login-retro-oauth__row"
                :class="{ 'login-retro-oauth__row--selected': oauthMenuIndex === idx }"
                :aria-selected="oauthMenuIndex === idx ? 'true' : 'false'"
                @click="onRetroOAuthRowClick(idx)"
              >
                <div class="login-retro-oauth__row-left">
                  <span class="login-retro-oauth__row-cursor" aria-hidden="true">{{ oauthMenuIndex === idx ? '>' : ' ' }}</span>
                  <span class="login-retro-oauth__row-label">{{ item.label }}</span>
                </div>
              </li>
            </ul>
            <p class="login-retro-oauth__kbd-hint" aria-hidden="true">↑↓ select · ENTER confirm · ESC back</p>
          </div>
        </div>
      </div>
    </div>

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
        <button type="button" class="dingding-login-btn" @click="loginWithDingding">
          钉钉登录
        </button>
      </div>
    </template>
  </div>
</template>

<script>
import { mapState } from 'vuex'
import { ensureDpUserIdInStorage } from '@features/user/utils/dpEnsureUserId'
import { dpResultSuccess, dpResultData, dpResultMessage } from '@shared/utils/dpApiResult'
import { flagCatTutorialAfterLogin } from '@shared/constants/dpCatThemeCopy'
import { enterLobbyAfterAuth } from '@features/user/utils/dpAuthEnterLobby'
import { dpPortalOverlayToBody, dpRestoreOverlayFromPortal } from '@shared/utils/dpOverlayPortal'

/** @typedef {'idle' | 'preparing' | 'slide-in' | 'snow' | 'reveal-flash' | 'ready' | 'retract'} RetroOAuthPhase */

const RETRO_OAUTH_SLIDE_MS = 280
const RETRO_OAUTH_SLIDE_MS_ECO = 80
const RETRO_OAUTH_GLITCH_MS = 280
const RETRO_OAUTH_FLASH_MS = 100

export default {
  inject: {
    dpAuthStage: { default: null }
  },
  data() {
    return {
      nickname: '',
      password: '',
      /** @type {RetroOAuthPhase} */
      retroOAuthPhase: 'idle',
      oauthMenuIndex: 0,
      retroOAuthTimers: [],
      _retroOAuthPortalAnchor: null,
      _retroOAuthKeyBound: null
    }
  },
  computed: {
    ...mapState('dpGame', ['gameUiTheme', 'ecoMode']),
    isRetro8bit() {
      return this.gameUiTheme === 'retro8bit'
    },
    prefersReducedMotion() {
      if (typeof window === 'undefined' || !window.matchMedia) return false
      return window.matchMedia('(prefers-reduced-motion: reduce)').matches
    },
    retroOAuthSlideMs() {
      if (this.ecoMode || this.prefersReducedMotion) return RETRO_OAUTH_SLIDE_MS_ECO
      return RETRO_OAUTH_SLIDE_MS
    },
    retroOAuthUseGlitch() {
      return !this.ecoMode && !this.prefersReducedMotion
    },
    retroOAuthSceneVisible() {
      return this.retroOAuthPhase !== 'idle'
    },
    retroOAuthOpen() {
      return this.retroOAuthPhase === 'ready'
    },
    retroOAuthBusy() {
      return this.retroOAuthPhase === 'preparing'
        || this.retroOAuthPhase === 'slide-in'
        || this.retroOAuthPhase === 'snow'
        || this.retroOAuthPhase === 'reveal-flash'
        || this.retroOAuthPhase === 'retract'
    },
    retroOAuthContentReady() {
      return this.retroOAuthPhase === 'ready'
        || this.retroOAuthPhase === 'reveal-flash'
        || (!this.retroOAuthUseGlitch
          && this.retroOAuthSceneVisible
          && this.retroOAuthPhase !== 'retract')
    },
    retroOAuthSheetPhaseClass() {
      return {
        'login-retro-oauth-sheet--slide-in': this.retroOAuthPhase === 'slide-in',
        'login-retro-oauth-sheet--snow': this.retroOAuthPhase === 'snow',
        'login-retro-oauth-sheet--reveal-flash': this.retroOAuthPhase === 'reveal-flash',
        'login-retro-oauth-sheet--ready': this.retroOAuthPhase === 'ready',
        'login-retro-oauth-sheet--retract': this.retroOAuthPhase === 'retract',
        'login-retro-oauth-sheet--instant': !this.retroOAuthUseGlitch
          && this.retroOAuthSceneVisible
          && this.retroOAuthPhase !== 'retract'
      }
    },
    retroOAuthMenuItems() {
      return [
        { id: 'github', label: 'GITHUB' },
        { id: 'gitee', label: 'GITEE' },
        { id: 'ding', label: 'DINGTALK // LOGIN' },
        { id: 'back', label: '< BACK' }
      ]
    }
  },
  beforeDestroy() {
    this.clearRetroOAuthTimers()
    this.unmountRetroOAuthKeydown()
    this.teardownRetroOAuthPortal()
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
    clearRetroOAuthTimers() {
      this.retroOAuthTimers.forEach((id) => clearTimeout(id))
      this.retroOAuthTimers = []
    },
    scheduleRetroOAuthTimer(fn, ms) {
      const id = setTimeout(fn, ms)
      this.retroOAuthTimers.push(id)
      return id
    },
    armRetroOAuthFallback(phase) {
      this.scheduleRetroOAuthTimer(() => {
        if (phase === 'slide-in') this.advanceFromOAuthSlideIn()
        else if (phase === 'retract') this.teardownOAuthPanel()
      }, this.retroOAuthSlideMs + 40)
    },
    setOAuthPhase(next) {
      if (this.retroOAuthPhase === next) return
      this.retroOAuthPhase = next
      this.syncRetroOAuthBodyClass()
    },
    portalRetroOAuthOverlay() {
      var el = this.$refs.retroOAuthOverlay
      if (!el) return
      if (!this._retroOAuthPortalAnchor) {
        this._retroOAuthPortalAnchor = { parent: null, next: null }
      }
      dpPortalOverlayToBody(el, this._retroOAuthPortalAnchor)
      this.syncRetroOAuthBodyClass()
    },
    teardownRetroOAuthPortal() {
      var el = this.$refs.retroOAuthOverlay
      dpRestoreOverlayFromPortal(el, this._retroOAuthPortalAnchor)
      this._retroOAuthPortalAnchor = null
      this.syncRetroOAuthBodyClass(true)
    },
    syncRetroOAuthBodyClass(forceRemove) {
      if (typeof document === 'undefined') return
      if (!forceRemove && this.retroOAuthSceneVisible) {
        document.body.classList.add('login-retro-oauth-active')
        return
      }
      document.body.classList.remove('login-retro-oauth-active')
    },
    mountRetroOAuthKeydown() {
      if (this._retroOAuthKeyBound) return
      this._retroOAuthKeyBound = this.onRetroOAuthKeydown.bind(this)
      window.addEventListener('keydown', this._retroOAuthKeyBound)
    },
    unmountRetroOAuthKeydown() {
      if (!this._retroOAuthKeyBound) return
      window.removeEventListener('keydown', this._retroOAuthKeyBound)
      this._retroOAuthKeyBound = null
    },
    onRetroOAuthKeydown(e) {
      if (this.retroOAuthPhase !== 'ready') return
      var key = e.key
      var len = this.retroOAuthMenuItems.length
      if (key === 'Escape') {
        e.preventDefault()
        this.backRetroOAuth()
        return
      }
      if (key === 'ArrowUp' && len > 0) {
        e.preventDefault()
        this.oauthMenuIndex = (this.oauthMenuIndex - 1 + len) % len
        return
      }
      if (key === 'ArrowDown' && len > 0) {
        e.preventDefault()
        this.oauthMenuIndex = (this.oauthMenuIndex + 1) % len
        return
      }
      if (key === 'Enter') {
        e.preventDefault()
        this.activateRetroOAuthMenuItem()
      }
    },
    activateRetroOAuthMenuItem() {
      var item = this.retroOAuthMenuItems[this.oauthMenuIndex]
      if (!item) return
      if (item.id === 'github') this.loginWithGitHub()
      else if (item.id === 'gitee') this.loginWithGitee()
      else if (item.id === 'ding') this.loginWithDingding()
      else if (item.id === 'back') this.backRetroOAuth()
    },
    onRetroOAuthRowClick(idx) {
      if (this.retroOAuthBusy || !this.retroOAuthOpen) return
      this.oauthMenuIndex = idx
      this.activateRetroOAuthMenuItem()
    },
    focusRetroOAuthPanel() {
      var el = this.$refs.retroOAuthPanelInner
      if (el && typeof el.focus === 'function') el.focus()
    },
    openRetroOAuth() {
      if (!this.isRetro8bit || this.retroOAuthSceneVisible) return
      this.clearRetroOAuthTimers()
      this.unmountRetroOAuthKeydown()
      this.oauthMenuIndex = 0
      this.setOAuthPhase('preparing')
      var self = this
      this.$nextTick(function () {
        self.portalRetroOAuthOverlay()
        self.$nextTick(function () {
          if (!self.retroOAuthUseGlitch) {
            self.clearRetroOAuthTimers()
            self.setOAuthPhase('ready')
            self.mountRetroOAuthKeydown()
            self.$nextTick(function () { self.focusRetroOAuthPanel() })
            return
          }
          self.setOAuthPhase('slide-in')
          self.armRetroOAuthFallback('slide-in')
        })
      })
    },
    backRetroOAuth() {
      if (!this.isRetro8bit || this.retroOAuthPhase !== 'ready') return
      this.clearRetroOAuthTimers()
      this.unmountRetroOAuthKeydown()
      if (this.retroOAuthUseGlitch) {
        this.setOAuthPhase('retract')
        this.armRetroOAuthFallback('retract')
        return
      }
      this.teardownOAuthPanel()
    },
    advanceFromOAuthSlideIn() {
      if (this.retroOAuthPhase !== 'slide-in') return
      this.clearRetroOAuthTimers()
      if (!this.retroOAuthUseGlitch) {
        this.setOAuthPhase('ready')
        this.mountRetroOAuthKeydown()
        this.$nextTick(() => this.focusRetroOAuthPanel())
        return
      }
      this.setOAuthPhase('snow')
      this.scheduleRetroOAuthTimer(() => this.advanceFromOAuthSnow(), RETRO_OAUTH_GLITCH_MS)
    },
    advanceFromOAuthSnow() {
      if (this.retroOAuthPhase !== 'snow') return
      this.setOAuthPhase('reveal-flash')
      this.scheduleRetroOAuthTimer(() => this.advanceFromOAuthFlash(), RETRO_OAUTH_FLASH_MS)
    },
    advanceFromOAuthFlash() {
      if (this.retroOAuthPhase !== 'reveal-flash') return
      this.setOAuthPhase('ready')
      this.mountRetroOAuthKeydown()
      this.$nextTick(() => this.focusRetroOAuthPanel())
    },
    teardownOAuthPanel() {
      this.clearRetroOAuthTimers()
      this.unmountRetroOAuthKeydown()
      this.setOAuthPhase('idle')
      this.teardownRetroOAuthPortal()
    },
    onOAuthSheetAnimEnd(e) {
      if (e.target !== e.currentTarget) return
      var name = e.animationName || ''
      if (this.retroOAuthPhase === 'slide-in' && name.indexOf('login-oauth-sheet-slide-in') !== -1) {
        this.advanceFromOAuthSlideIn()
        return
      }
      if (this.retroOAuthPhase === 'retract' && name.indexOf('login-oauth-sheet-slide-out') !== -1) {
        this.teardownOAuthPanel()
      }
    },
    login() {
      if (this.isRetro8bit && this.retroOAuthBusy) return
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
      if (this.isRetro8bit && this.retroOAuthBusy) return
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
      if (this.isRetro8bit && this.retroOAuthBusy) return
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
    },
    loginWithDingding() {
      if (this.isRetro8bit && this.retroOAuthBusy) return
      this.$http.get('/oauth/ding/authorize-url')
        .then((res) => {
          const d = res.data
          if (dpResultSuccess(d)) {
            const payload = dpResultData(d) || {}
            window.location.href = payload.url
          } else {
            this.showAuthError(dpResultMessage(d) || '获取钉钉授权链接失败')
          }
        })
        .catch((err) => {
          console.error('获取钉钉授权链接失败', err)
          this.showAuthError('获取钉钉授权链接失败')
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
.login-box__main {
  position: relative;
  z-index: 1;
  transition: opacity 0.18s ease-out;
}
.login-box__main--dimmed {
  opacity: 0.42;
  pointer-events: none;
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
.login-actions--retro8bit .login-retro-alt-btn {
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
.dingding-login-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  max-width: 280px;
  height: 42px;
  font-size: 14px;
  font-weight: 500;
  color: #fff;
  background: #0089ff;
  border: 1px solid #0070d9;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}
.dingding-login-btn:hover {
  background: #1a96ff;
}
</style>
