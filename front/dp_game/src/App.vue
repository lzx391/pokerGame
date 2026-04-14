<template>
  <div
    id="app"
    :class="{
      'app--lobby': isLobbyRoute,
      'app--dp-game': isGameRoute,
      'app--auth': isAuthPage
    }"
  >
    <!-- 登录 / 注册 页面：带有导航和盒子布局 -->
    <div v-if="isAuthPage" class="app-container">
      <div class="dp-game-theme-row app-auth-theme-bar">
        <span class="dp-game-theme-row__label">界面主题</span>
        <select
          class="dp-game-theme-select"
          aria-label="选择界面主题"
          :value="gameUiTheme"
          @change="onAuthThemeChange($event.target.value)"
        >
          <option v-for="t in gameThemeOptions" :key="t.id" :value="t.id">{{ t.label }}</option>
        </select>
      </div>
      <h1 class="app-title">DP GAME</h1>
      <div class="nav-bar">
        <router-link to="/login" class="nav-link">登录</router-link>
        <router-link to="/register" class="nav-link">注册</router-link>
      </div>
      <div class="content-box">
        <router-view></router-view>
      </div>
    </div>

    <!-- 其他路由：全屏展示，不显示登录 / 注册按钮 -->
    <div v-else class="full-page" :class="{ 'full-page--lobby': isLobbyRoute, 'full-page--dp-game': isGameRoute }">
      <router-view></router-view>
    </div>
  </div>
</template>

<script>
import { mapState } from 'vuex'

export default {
  name: 'App',
  computed: {
    ...mapState('dpGame', ['gameUiTheme', 'gameThemeOptions']),
    isAuthPage() {
      const path = this.$route.path
      return path === '/login' || path === '/register' || path === '/'
    },
    /** 大厅与主题子页：#app 不铺灰底，由 .dp-game-root / body[data-dp-game-theme] 铺色 */
    isLobbyRoute() {
      const p = this.$route.path
      return (
        p === '/home' ||
        p.startsWith('/hand-history') ||
        p === '/music-upload' ||
        p.startsWith('/room/')
      )
    },
    /** 对局页：铺满视口、与 .dp-game-root 组成 flex 链，减少底部露灰/白边 */
    isGameRoute() {
      return this.$route.path.startsWith('/game')
    }
  },
  methods: {
    onAuthThemeChange(themeId) {
      this.$store.commit('dpGame/SET_GAME_UI_THEME', themeId)
    }
  }
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

/* 手机全屏 / 刘海屏：可滚动态视口高度 + iOS Safari 可用高度 */
html {
  height: 100%;
  height: -webkit-fill-available;
  overflow-x: hidden;
  overflow-y: auto;
}

body {
  margin: 0;
  min-height: 100%;
  min-height: 100vh;
  min-height: 100dvh;
  min-height: -webkit-fill-available;
  overflow-x: hidden;
  overflow-y: auto;
  /* 与 #app 一致，避免底部露默认白底 */
  background-color: #f5f7fa;
}

#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
  background-color: #f5f7fa;
  min-height: 100%;
  min-height: 100vh;
  min-height: 100dvh;
  min-height: -webkit-fill-available;
  overflow-x: hidden;
  overflow-y: visible;
}

.app-container {
  max-width: 500px;
  margin: 0 auto;
  padding: 40px 20px;
}

/* 标题样式 */
.app-title {
  font-size: 42px;
  font-weight: bold;
  color: #3a4b5c;
  margin-bottom: 30px;
  letter-spacing: 2px;
}

/* 导航栏 */
.nav-bar {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-bottom: 40px;
}

/* 导航按钮 */
.nav-link {
  display: inline-block;
  padding: 12px 30px;
  background-color: #409eff;
  color: #fff !important;
  border-radius: 8px;
  text-decoration: none;
  font-size: 16px;
  font-weight: 500;
  transition: all 0.3s ease;
  box-shadow: 0 2px 6px rgba(64, 158, 255, 0.2);
}

.nav-link:hover {
  background-color: #338eef;
  transform: translateY(-2px);
  box-shadow: 0 4px 10px rgba(64, 158, 255, 0.3);
}

/* 内容区域 */
.content-box {
  background: #fff;
  padding: 30px;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 非登录/注册页：全屏铺满窗口（对局等路由不再受 #app 外边距挤压） */
.full-page {
  width: 100%;
  padding: 0;
  margin: 0;
  min-height: 100%;
  min-height: 100vh;
  min-height: 100dvh;
  min-height: -webkit-fill-available;
}

/* 大厅布局与 #app.app--lobby / .full-page--lobby：见 dp-lobby-shell.css（组件引入），避免与对局 flex 链冲突 */

/* 对局：#app 与全屏容器拉满动态视口，子级 .dp-game-root flex:1 避免平板/安全区下露浅灰底 */
#app.app--dp-game {
  min-height: 100dvh;
  min-height: -webkit-fill-available;
  display: flex;
  flex-direction: column;
}

.full-page--dp-game {
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;
  min-height: 100dvh;
  min-height: -webkit-fill-available;
}

.full-page--dp-game > .dp-game-root {
  flex: 1 1 auto;
  min-height: 0;
  width: 100%;
}
</style>