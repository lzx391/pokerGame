<template>
  <section class="admin-roles dp-lobby-panel">
    <div class="admin-roles__header">
      <h3 class="admin-roles__title">角色与权限</h3>
      <el-button
        type="primary"
        size="small"
        class="admin-roles__create-btn"
        :disabled="loading"
        @click="openCreateDialog"
      >
        新建角色
      </el-button>
    </div>
    <p v-if="loading" class="admin-roles__hint">加载中…</p>
    <p v-else-if="error" class="admin-roles__hint admin-roles__hint--error">{{ error }}</p>
    <div v-else class="admin-roles__body">
      <aside class="admin-roles__aside">
        <div
          v-for="role in roles"
          :key="'role-' + role.id"
          class="admin-roles__role-item"
          :class="{ 'admin-roles__role-item--active': selectedRoleId === role.id }"
        >
          <button
            type="button"
            class="admin-roles__role-btn"
            @click="selectRole(role)"
          >
            <span class="admin-roles__role-name">{{ role.name }}</span>
            <span class="admin-roles__role-code">{{ role.code }}</span>
          </button>
          <el-button
            v-if="!isBuiltInRole(role)"
            type="text"
            class="admin-roles__delete-btn"
            :loading="deletingRoleId === role.id"
            @click.stop="confirmDeleteRole(role)"
          >
            删除
          </el-button>
        </div>
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

    <el-dialog
      title="新建角色"
      :visible.sync="createDialogVisible"
      width="420px"
      append-to-body
      :close-on-click-modal="false"
      @closed="resetCreateForm"
    >
      <el-form label-width="72px" @submit.native.prevent="submitCreateRole">
        <el-form-item label="编码">
          <el-input
            v-model="createForm.code"
            placeholder="如 MODERATOR"
            maxlength="32"
            :disabled="creating"
            @input="onCreateCodeInput"
          />
        </el-form-item>
        <el-form-item label="名称">
          <el-input
            v-model="createForm.name"
            placeholder="如 版主"
            maxlength="64"
            :disabled="creating"
          />
        </el-form-item>
      </el-form>
      <span slot="footer" class="dialog-footer">
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreateRole">创建</el-button>
      </span>
    </el-dialog>
  </section>
</template>

<script>
import { dpAdminApi } from '@features/admin/api/adminApi'
import { dpResultData, dpResultMessage, dpResultSuccess } from '@shared/utils/dpApiResult'

var BUILT_IN_ROLE_CODES = { PLAYER: true, ADMIN: true }

export default {
  name: 'AdminRolesPage',
  data: function () {
    return {
      loading: true,
      saving: false,
      creating: false,
      deletingRoleId: null,
      error: '',
      roles: [],
      permissions: [],
      selectedRoleId: null,
      checkedPermIds: [],
      createDialogVisible: false,
      createForm: {
        code: '',
        name: ''
      }
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
    isBuiltInRole: function (role) {
      return !!(role && role.code && BUILT_IN_ROLE_CODES[role.code])
    },
    loadData: async function (preferredRoleId) {
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
        this.selectFirstRole(preferredRoleId)
      } catch (e) {
        this.error = '网络错误，请稍后重试'
      } finally {
        this.loading = false
      }
    },
    selectFirstRole: function (preferredRoleId) {
      if (!this.roles.length) {
        this.selectedRoleId = null
        this.checkedPermIds = []
        return
      }
      var target = null
      if (preferredRoleId != null) {
        for (var i = 0; i < this.roles.length; i++) {
          if (this.roles[i].id === preferredRoleId) {
            target = this.roles[i]
            break
          }
        }
      }
      this.selectRole(target || this.roles[0])
    },
    selectRole: function (role) {
      if (!role) return
      this.selectedRoleId = role.id
      var ids = role.permissionIds
      this.checkedPermIds = Array.isArray(ids) ? ids.slice() : []
    },
    openCreateDialog: function () {
      this.createDialogVisible = true
    },
    resetCreateForm: function () {
      this.createForm.code = ''
      this.createForm.name = ''
      this.creating = false
    },
    onCreateCodeInput: function (value) {
      this.createForm.code = String(value || '').toUpperCase()
    },
    submitCreateRole: async function () {
      var code = (this.createForm.code || '').trim()
      var name = (this.createForm.name || '').trim()
      if (!code || !name) {
        this.$message.error('请填写角色编码和名称')
        return
      }
      this.creating = true
      try {
        var res = await dpAdminApi(this.$http).createRole(code, name)
        if (!dpResultSuccess(res.data)) {
          this.$message.error(dpResultMessage(res.data) || '创建失败')
          return
        }
        var roleData = dpResultData(res.data) || {}
        var created = roleData.role
        this.createDialogVisible = false
        this.$message.success('角色已创建')
        await this.loadData(created && created.id != null ? created.id : null)
      } catch (e) {
        this.$message.error('网络错误，请稍后重试')
      } finally {
        this.creating = false
      }
    },
    confirmDeleteRole: function (role) {
      if (!role || this.isBuiltInRole(role)) return
      var self = this
      this.$confirm('确定删除角色「' + role.name + '」？删除后不可恢复。', '删除角色', {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(function () {
        return self.deleteRole(role)
      }).catch(function () {})
    },
    deleteRole: async function (role) {
      this.deletingRoleId = role.id
      try {
        var res = await dpAdminApi(this.$http).deleteRole(role.id)
        if (!dpResultSuccess(res.data)) {
          this.$message.error(dpResultMessage(res.data) || '删除失败')
          return
        }
        this.$message.success('角色已删除')
        var deletedId = role.id
        await this.loadData(this.selectedRoleId === deletedId ? null : this.selectedRoleId)
      } catch (e) {
        this.$message.error('网络错误，请稍后重试')
      } finally {
        this.deletingRoleId = null
      }
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
.admin-roles__header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.admin-roles__title {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  color: var(--dp-text-primary);
}
.admin-roles__create-btn {
  flex-shrink: 0;
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
.admin-roles__role-item {
  display: flex;
  align-items: stretch;
  gap: 4px;
  border: 1px solid var(--dp-subpanel-border);
  border-radius: 8px;
  background: var(--dp-subpanel-bg);
  transition: border-color 0.18s ease;
}
.admin-roles__role-item--active {
  border-color: var(--dp-accent);
  background: var(--dp-panel-bg);
}
.admin-roles__role-btn {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 10px 12px;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
}
.admin-roles__delete-btn {
  align-self: center;
  margin-right: 6px;
  padding: 0 6px;
  color: var(--dp-danger);
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
