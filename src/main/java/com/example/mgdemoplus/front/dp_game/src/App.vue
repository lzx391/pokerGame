<template>
  <div id="app">
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
    <div v-else class="full-page">
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

#app {
  font-family: Avenir, Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
  background-color: #f5f7fa;
  min-height: 100vh;
  padding: 40px 20px;
}

.app-container {
  max-width: 500px;
  margin: 0 auto;
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

/* 非登录/注册页：全屏铺满窗口 */
.full-page {
  min-height: 100vh;
  padding: 0;
  margin: 0;
}
</style>