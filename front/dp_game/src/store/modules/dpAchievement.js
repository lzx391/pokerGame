import { parseAchievementUnlockPayload } from '@/utils/dpSocialStream'

export default {
  namespaced: true,
  state: {
    /** 每次 SSE 解锁 +1，供成就墙等订阅刷新 */
    revision: 0,
    /** @type {{ code: string, title: string, description: string } | null} */
    lastUnlock: null,
    toastSeq: 0,
    /** @type {Array<{ id: number, code: string, title: string, description: string }>} */
    toasts: []
  },
  mutations: {
    APPLY_UNLOCK(state, payload) {
      state.revision += 1
      state.lastUnlock = payload
    },
    ENQUEUE_TOAST(state, payload) {
      state.toastSeq += 1
      state.toasts.push({
        id: state.toastSeq,
        code: payload.code,
        title: payload.title,
        description: payload.description
      })
      if (state.toasts.length > 8) {
        state.toasts.splice(0, state.toasts.length - 8)
      }
    },
    DISMISS_TOAST(state, id) {
      state.toasts = state.toasts.filter(function (t) {
        return t.id !== id
      })
    }
  },
  actions: {
    /**
     * @param {import('vuex').ActionContext} ctx
     * @param {any} raw SSE JSON 或已解析对象
     */
    handleAchievementUnlock({ commit }, raw) {
      var parsed = parseAchievementUnlockPayload(raw)
      if (!parsed) return
      commit('APPLY_UNLOCK', parsed)
      commit('ENQUEUE_TOAST', parsed)
    }
  }
}
