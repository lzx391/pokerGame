<template>
  <section class="admin-users dp-lobby-panel">
    <h3 class="admin-users__title">用户角色分配</h3>

    <div class="admin-users__toolbar">
      <el-input
        v-model="keyword"
        clearable
        size="small"
        placeholder="搜索昵称"
        prefix-icon="el-icon-search"
        class="admin-users__search"
        @keyup.enter.native="onSearch"
        @clear="onSearch"
      />
      <el-button type="primary" size="small" @click="onSearch">搜索</el-button>
    </div>

    <p v-if="loading" class="admin-users__hint">加载中…</p>
    <p v-else-if="error" class="admin-users__hint admin-users__hint--error">{{ error }}</p>
    <template v-else>
      <el-table
        :data="users"
        stripe
        size="small"
        class="admin-users__table"
        empty-text="暂无用户"
      >
        <el-table-column prop="id" label="ID" width="72" />
        <el-table-column prop="nickname" label="昵称" min-width="120" />
        <el-table-column label="角色" min-width="200">
          <template slot-scope="scope">
            <el-checkbox-group
              v-model="roleSelections[scope.row.id]"
              class="admin-users__role-checks"
              @change="onRoleChange(scope.row)"
            >
              <el-checkbox
                v-for="role in roles"
                :key="'u' + scope.row.id + '-r' + role.id"
                :label="role.id"
              >
                {{ role.name }}
              </el-checkbox>
            </el-checkbox-group>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="total > pageSize"
        class="admin-users__pager"
        layout="prev, pager, next, total"
        :total="total"
        :page-size="pageSize"
        :current-page="page"
        @current-change="onPageChange"
      />
    </template>
  </section>
</template>

<script>
import { dpAdminApi } from '@features/admin/api/adminApi'
import { dpResultData, dpResultMessage, dpResultSuccess } from '@shared/utils/dpApiResult'

export default {
  name: 'AdminUsersPage',
  data: function () {
    return {
      loading: true,
      error: '',
      users: [],
      roles: [],
      roleSelections: {},
      savingUserId: null,
      keyword: '',
      page: 1,
      pageSize: 20,
      total: 0
    }
  },
  created: function () {
    this.loadRoles()
    this.loadUsers()
  },
  methods: {
    loadRoles: async function () {
      try {
        var res = await dpAdminApi(this.$http).listRoles()
        if (dpResultSuccess(res.data)) {
          var data = dpResultData(res.data) || {}
          this.roles = data.list || []
        }
      } catch (e) {
        /* 角色列表失败时用户页仍可展示 */
      }
    },
    loadUsers: async function () {
      this.loading = true
      this.error = ''
      try {
        var res = await dpAdminApi(this.$http).listUsers({
          page: this.page,
          size: this.pageSize,
          keyword: (this.keyword || '').trim()
        })
        if (!dpResultSuccess(res.data)) {
          this.error = dpResultMessage(res.data) || '加载失败'
          return
        }
        var data = dpResultData(res.data) || {}
        this.users = data.list || []
        this.total = data.total != null ? Number(data.total) : 0
        var sel = {}
        for (var i = 0; i < this.users.length; i++) {
          var u = this.users[i]
          sel[u.id] = Array.isArray(u.roleIds) ? u.roleIds.slice() : []
        }
        this.roleSelections = sel
      } catch (e) {
        this.error = '网络错误，请稍后重试'
      } finally {
        this.loading = false
      }
    },
    onSearch: function () {
      this.page = 1
      this.loadUsers()
    },
    onPageChange: function (p) {
      this.page = p
      this.loadUsers()
    },
    onRoleChange: async function (user) {
      if (!user || user.id == null) return
      if (this.savingUserId != null) return
      this.savingUserId = user.id
      var roleIds = this.roleSelections[user.id] || []
      try {
        var res = await dpAdminApi(this.$http).updateUserRoles(user.id, roleIds.slice())
        if (!dpResultSuccess(res.data)) {
          this.$message.error(dpResultMessage(res.data) || '保存失败')
          await this.loadUsers()
          return
        }
        this.$message.success('已更新「' + user.nickname + '」的角色')
        user.roleIds = roleIds.slice()
      } catch (e) {
        this.$message.error('网络错误，请稍后重试')
        await this.loadUsers()
      } finally {
        this.savingUserId = null
      }
    }
  }
}
</script>

<style scoped>
.admin-users {
  padding: clamp(14px, 3vw, 20px);
}
.admin-users__title {
  margin: 0 0 12px;
  font-size: 1rem;
  font-weight: 600;
  color: var(--dp-text-primary);
}
.admin-users__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 14px;
}
.admin-users__search {
  width: min(280px, 100%);
}
.admin-users__hint {
  margin: 12px 0;
  font-size: 14px;
  color: var(--dp-text-muted);
}
.admin-users__hint--error {
  color: var(--dp-danger);
}
.admin-users__role-checks {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
}
.admin-users__pager {
  margin-top: 14px;
  text-align: right;
}
</style>
