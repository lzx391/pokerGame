/**
 * 登录/注册/OAuth 成功后或 App 初始化时拉取当前用户权限列表。
 * @param {import('vue').default} vm 含 $http、$store 的组件实例
 */
export function bootstrapDpAuthPermissions(vm) {
  if (!vm || !vm.$store || !vm.$http) return Promise.resolve()
  try {
    var raw = localStorage.getItem('userInfo')
    if (!raw) {
      vm.$store.dispatch('dpAuth/clearPermissions')
      return Promise.resolve()
    }
    var user = JSON.parse(raw)
    if (!user || !user.token) {
      vm.$store.dispatch('dpAuth/clearPermissions')
      return Promise.resolve()
    }
    return vm.$store.dispatch('dpAuth/fetchPermissions', { http: vm.$http })
  } catch (e) {
    return Promise.resolve()
  }
}
