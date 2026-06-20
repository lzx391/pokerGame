/**
 * RBAC 管理 API 封装
 * @param {import('axios').AxiosInstance} http
 */
export function dpAdminApi(http) {
  return {
    verifyPassword: function (password) {
      return http.post('/dp/admin/verifyPassword', { password: password })
    },
    listRoles: function () {
      return http.get('/dp/admin/roles')
    },
    listPermissions: function () {
      return http.get('/dp/admin/permissions')
    },
    updateRolePermissions: function (roleId, permissionIds) {
      return http.put('/dp/admin/roles/' + roleId + '/permissions', { permissionIds: permissionIds })
    },
    listUsers: function (params) {
      return http.get('/dp/admin/users', { params: params || {} })
    },
    updateUserRoles: function (userId, roleIds) {
      return http.put('/dp/admin/users/' + userId + '/roles', { roleIds: roleIds })
    }
  }
}
