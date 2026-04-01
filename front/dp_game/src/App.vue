<template>
  <div id="app" :class="{ 'app--lobby': isLobbyRoute }">
    <!-- 登录 / 注册 页面：带有导航和盒子布局 -->
    <div v-if="isAuthPage" class="app-container">
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
    <div v-else class="full-page" :class="{ 'full-page--lobby': isLobbyRoute }">
      <router-view></router-view>
    </div>
  </div>
</template>

<script>
export default {
  name: 'App',
  computed: {
    isAuthPage() {
      const path = this.$route.path
      return path === '/login' || path === '/register' || path === '/'
    },
    /** 大厅只需内容高度，避免整页强制 100vh 造成「下面一大块空白」的观感 */
    isLobbyRoute() {
      const p = this.$route.path
      return p === '/home' || p.startsWith('/hand-history') || p === '/music-upload'
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
}

body {
  margin: 0;
  min-height: 100%;
  min-height: 100vh;
  min-height: 100dvh;
  min-height: -webkit-fill-available;
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

/* 游戏大厅：不要与 #app 双重撑满视口，高度随内容 */
#app.app--lobby {
  min-height: 0;
}

.full-page--lobby {
  min-height: 0;
}
</style>