import { dpResultData, dpResultSuccess } from '@shared/utils/dpApiResult'

export const DP_PERM_HOLE_CARDS_VIEW = 'game:hole_cards:view'

export default {
  namespaced: true,
  state: {
    permissions: []
  },
  getters: {
    hasPerm: function (state) {
      return function (code) {
        if (!code) return false
        return state.permissions.indexOf(code) !== -1
      }
    },
    canViewHoleCards: function (state, getters) {
      return getters.hasPerm(DP_PERM_HOLE_CARDS_VIEW)
    }
  },
  mutations: {
    SET_PERMISSIONS: function (state, list) {
      state.permissions = Array.isArray(list) ? list.slice() : []
    },
    CLEAR_PERMISSIONS: function (state) {
      state.permissions = []
    }
  },
  actions: {
    fetchPermissions: async function ({ commit }, payload) {
      var http = payload && payload.http
      if (!http) return
      try {
        var res = await http.get('/dp/auth/permissions')
        if (dpResultSuccess(res.data)) {
          var data = dpResultData(res.data) || {}
          commit('SET_PERMISSIONS', data.permissions || [])
        }
      } catch (e) {
        /* 静默：无权限时后端可能返回空列表或 401 由全局拦截处理 */
      }
    },
    clearPermissions: function ({ commit }) {
      commit('CLEAR_PERMISSIONS')
    }
  }
}
