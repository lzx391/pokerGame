<template>
  <section class="admin-roles dp-lobby-panel">
    <h3 class="admin-roles__title">角色与权限</h3>
    <p v-if="loading" class="admin-roles__hint">加载中…</p>
    <p v-else-if="error" class="admin-roles__hint admin-roles__hint--error">{{ error }}</p>
    <div v-else class="admin-roles__body">
      <aside class="admin-roles__aside">
        <button
          v-for="role in roles"
          :key="'role-' + role.id"
          type="button"
          class="admin-roles__role-btn"
          :class="{ 'admin-roles__role-btn--active': selectedRoleId === role.id }"
          @click="selectRole(role)"
        >
          <span class="admin-roles__role-name">{{ role.name }}</span>
          <span class="admin-roles__role-code">{{ role.code }}</span>
        </button>
      </aside>
      <div v-if="selectedRole" class="admin-roles__detail">
        <h4 class="admin-roles__detail-title">{{ selectedRole.name }} · 权限勾选</h4>
        <el-checkbox-group v-model="checkedPermIds" class="admin-roles__checks">
          <el-checkbox
            v-for="perm in permissions"
            :key="'perm-' + perm.id"
            :label="perm.id"
            class="admin-roles__check"
          >
            <span class="admin-roles__perm-name">{{ perm.name }}</span>
            <span class="admin-roles__perm-code">{{ perm.code }}</span>
          </el-checkbox>
        </el-checkbox-group>
        <div class="admin-roles__footer">
          <el-button type="primary" :loading="saving" @click="saveRolePermissions">保存权限</el-button>
        </div>
      </div>
      <p v-else class="admin-roles__hint">请选择左侧角色</p>
    </div>
  </section>
</template>

<script>
import { dpAdminApi } from '@features/admin/api/adminApi'
import { dpResultData, dpResultMessage, dpResultSuccess } from '@shared/utils/dpApiResult'

export default {
  name: 'AdminRolesPage',
  data: function () {
    return {
      loading: true,
      saving: false,
      error: '',
      roles: [],
      permissions: [],
      selectedRoleId: null,
      checkedPermIds: []
    }
  },
  computed: {
    selectedRole: function () {
      var id = this.selectedRoleId
      if (id == null) return null
      for (var i = 0; i < this.roles.length; i++) {
        if (this.roles[i].id === id) return this.roles[i]
      }
      return null
    }
  },
  created: function () {
    this.loadData()
  },
  methods: {
    loadData: async function () {
      this.loading = true
      this.error = ''
      try {
        var api = dpAdminApi(this.$http)
        var results = await Promise.all([api.listRoles(), api.listPermissions()])
        var rolesBody = results[0].data
        var permsBody = results[1].data
        if (!dpResultSuccess(rolesBody) || !dpResultSuccess(permsBody)) {
          this.error = dpResultMessage(rolesBody) || dpResultMessage(permsBody) || '加载失败'
          return
        }
        var rolesData = dpResultData(rolesBody) || {}
        var permsData = dpResultData(permsBody) || {}
        this.roles = rolesData.list || []
        this.permissions = permsData.list || []
        if (this.roles.length > 0) {
          this.selectRole(this.roles[0])
        }
      } catch (e) {
        this.error = '网络错误，请稍后重试'
      } finally {
        this.loading = false
      }
    },
    selectRole: function (role) {
      if (!role) return
      this.selectedRoleId = role.id
      var ids = role.permissionIds
      this.checkedPermIds = Array.isArray(ids) ? ids.slice() : []
    },
    saveRolePermissions: async function () {
      if (!this.selectedRole) return
      this.saving = true
      try {
        var res = await dpAdminApi(this.$http).updateRolePermissions(
          this.selectedRole.id,
          this.checkedPermIds.slice()
        )
        if (!dpResultSuccess(res.data)) {
          this.$message.error(dpResultMessage(res.data) || '保存失败')
          return
        }
        this.$message.success('角色权限已更新')
        this.selectedRole.permissionIds = this.checkedPermIds.slice()
        for (var i = 0; i < this.roles.length; i++) {
          if (this.roles[i].id === this.selectedRole.id) {
            this.$set(this.roles[i], 'permissionIds', this.checkedPermIds.slice())
            break
          }
        }
      } catch (e) {
        this.$message.error('网络错误，请稍后重试')
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style scoped>
.admin-roles {
  padding: clamp(14px, 3vw, 20px);
}
.admin-roles__title {
  margin: 0 0 12px;
  font-size: 1rem;
  font-weight: 600;
  color: var(--dp-text-primary);
}
.admin-roles__hint {
  margin: 12px 0;
  font-size: 14px;
  color: var(--dp-text-muted);
}
.admin-roles__hint--error {
  color: var(--dp-danger);
}
.admin-roles__body {
  display: grid;
  grid-template-columns: minmax(160px, 220px) 1fr;
  gap: 16px;
}
@media (max-width: 640px) {
  .admin-roles__body {
    grid-template-columns: 1fr;
  }
}
.admin-roles__aside {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.admin-roles__role-btn {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 10px 12px;
  border: 1px solid var(--dp-subpanel-border);
  border-radius: 8px;
  background: var(--dp-subpanel-bg);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.18s ease;
}
.admin-roles__role-btn--active {
  border-color: var(--dp-accent);
  background: var(--dp-panel-bg);
}
.admin-roles__role-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--dp-text-primary);
}
.admin-roles__role-code {
  font-size: 11px;
  color: var(--dp-text-muted);
  font-family: 'Courier New', monospace;
}
.admin-roles__detail-title {
  margin: 0 0 12px;
  font-size: 14px;
  font-weight: 600;
  color: var(--dp-text-primary);
}
.admin-roles__checks {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.admin-roles__check {
  display: flex;
  align-items: flex-start;
}
.admin-roles__perm-name {
  display: block;
  font-size: 14px;
  color: var(--dp-text-primary);
}
.admin-roles__perm-code {
  display: block;
  font-size: 11px;
  color: var(--dp-text-muted);
  font-family: 'Courier New', monospace;
}
.admin-roles__footer {
  margin-top: 16px;
}
</style>
