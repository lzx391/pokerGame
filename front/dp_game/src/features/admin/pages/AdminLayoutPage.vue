<template>
  <div
    class="dp-game-root"
    :data-dp-game-theme="effectiveThemeForCss"
  >
    <div class="dp-lobby-inner dp-lobby-inner--wide admin-layout">
      <div class="admin-layout__toolbar">
        <div class="dp-game-theme-row admin-layout__theme-row">
          <span class="dp-game-theme-row__label">界面主题</span>
          <dp-theme-picker
            :game-ui-theme="gameUiTheme"
            :theme-options="gameThemeOptions"
            @input-theme="onLobbyThemeChange($event)"
          />
        </div>
      </div>

      <header class="admin-layout__header">
        <h2 class="admin-layout__title">管理员控制台</h2>
        <div class="admin-layout__actions">
          <el-button type="text" class="admin-layout__home-link" @click="goHome">返回大厅</el-button>
          <el-button type="text" class="admin-layout__lock-link" @click="lockAdmin">锁定管理页</el-button>
        </div>
      </header>

      <nav class="admin-layout__nav dp-lobby-panel" aria-label="管理导航">
        <router-link
          to="/admin/roles"
          class="admin-layout__nav-link"
          active-class="admin-layout__nav-link--active"
        >
          角色权限
        </router-link>
        <router-link
          to="/admin/users"
          class="admin-layout__nav-link"
          active-class="admin-layout__nav-link--active"
        >
          用户角色
        </router-link>
      </nav>

      <main class="admin-layout__main">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script>
import '@/styles/dp-lobby-shell.css'
import dpLobbyThemeMixin from '@features/lobby/mixins/dpLobbyThemeMixin'

export default {
  name: 'AdminLayoutPage',
  mixins: [dpLobbyThemeMixin],
  methods: {
    goHome: function () {
      this.$router.push('/home')
    },
    lockAdmin: function () {
      sessionStorage.removeItem('dp_admin_unlock')
      this.$message.success('已锁定管理页，再次进入需验证密码')
      this.$router.push('/home')
    }
  }
}
</script>

<style scoped>
.admin-layout {
  padding-bottom: clamp(20px, 4vw, 32px);
}
.admin-layout__toolbar {
  margin-bottom: 12px;
}
.admin-layout__header {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 14px;
}
.admin-layout__title {
  margin: 0;
  font-size: clamp(1.1rem, 3.2vw, 1.35rem);
  font-weight: 700;
  color: var(--dp-text-primary);
}
.admin-layout__actions {
  display: flex;
  align-items: center;
  gap: 4px;
}
.admin-layout__nav {
  display: flex;
  gap: 8px;
  padding: 10px 14px;
  margin-bottom: 14px;
}
.admin-layout__nav-link {
  padding: 8px 16px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--dp-text-secondary);
  text-decoration: none;
  border: 1px solid transparent;
  transition: all 0.18s ease;
}
.admin-layout__nav-link:hover {
  color: var(--dp-accent);
  background: var(--dp-subpanel-bg);
}
.admin-layout__nav-link--active {
  color: var(--dp-accent);
  border-color: var(--dp-accent);
  background: var(--dp-subpanel-bg);
}
.admin-layout__main {
  min-height: 200px;
}
</style>
