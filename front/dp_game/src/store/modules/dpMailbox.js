import { dpSocialApi } from '@/api/api.dpSocial'
import { dpResultSuccess, dpResultData, dpResultMessage, dpAxiosErrorMessage } from '@/utils/dpApiResult'
import { dpFriendsInviteEligible } from '@/utils/dpFriendsInviteEligible'

function initialState() {
  return {
    unreadCount: 0,
    friendRequests: [],
    roomInvites: [],
    friends: [],
    mailboxLoading: false,
    friendsLoading: false,
    actionBusyId: null
  }
}

export default {
  namespaced: true,
  state: initialState(),
  mutations: {
    SET_UNREAD(state, n) {
      var v = parseInt(String(n), 10)
      state.unreadCount = isFinite(v) && v > 0 ? v : 0
    },
    SET_MAILBOX(state, payload) {
      payload = payload || {}
      state.friendRequests = Array.isArray(payload.friendRequests) ? payload.friendRequests : []
      state.roomInvites = Array.isArray(payload.roomInvites) ? payload.roomInvites : []
    },
    SET_FRIENDS(state, list) {
      state.friends = Array.isArray(list) ? list : []
    },
    SET_MAILBOX_LOADING(state, v) {
      state.mailboxLoading = !!v
    },
    SET_FRIENDS_LOADING(state, v) {
      state.friendsLoading = !!v
    },
    SET_ACTION_BUSY(state, id) {
      state.actionBusyId = id != null ? id : null
    }
  },
  actions: {
    /**
     * @param {import('vuex').ActionContext} ctx
     * @param {{ http: import('axios').AxiosInstance }} root0
     */
    async fetchUnreadCount({ commit }, { http }) {
      if (!http) return
      var client = http
      var api = dpSocialApi(client)
      try {
        var res = await api.unreadCount()
        var body = res.data
        if (!dpResultSuccess(body)) return
        var d = dpResultData(body) || {}
        commit('SET_UNREAD', d.count != null ? d.count : 0)
      } catch (e) {
        console.error('dpMailbox/fetchUnreadCount', e)
      }
    },
    async fetchMailbox({ commit }, { http }) {
      var client = http
      if (!client) return
      var api = dpSocialApi(client)
      commit('SET_MAILBOX_LOADING', true)
      try {
        var res = await api.mailbox()
        var body = res.data
        if (!dpResultSuccess(body)) {
          commit('SET_MAILBOX', { friendRequests: [], roomInvites: [] })
          return { ok: false, message: dpResultMessage(body) }
        }
        var d = dpResultData(body) || {}
        commit('SET_MAILBOX', {
          friendRequests: d.friendRequests,
          roomInvites: d.roomInvites
        })
        return { ok: true }
      } catch (e) {
        console.error('dpMailbox/fetchMailbox', e)
        commit('SET_MAILBOX', { friendRequests: [], roomInvites: [] })
        return { ok: false, message: '网络错误' }
      } finally {
        commit('SET_MAILBOX_LOADING', false)
      }
    },
    async fetchFriends({ commit }, { http }) {
      var client = http
      if (!client) return
      var api = dpSocialApi(client)
      commit('SET_FRIENDS_LOADING', true)
      try {
        var res = await api.listFriends()
        var body = res.data
        if (!dpResultSuccess(body)) {
          commit('SET_FRIENDS', [])
          return { ok: false, message: dpResultMessage(body) }
        }
        var d = dpResultData(body) || {}
        commit('SET_FRIENDS', dpFriendsInviteEligible(d.friends))
        return { ok: true }
      } catch (e) {
        console.error('dpMailbox/fetchFriends', e)
        commit('SET_FRIENDS', [])
        return { ok: false, message: '网络错误' }
      } finally {
        commit('SET_FRIENDS_LOADING', false)
      }
    },
    /**
     * @returns {Promise<{ ok: boolean, message?: string }>}
     */
    async removeFriend({ dispatch, commit }, { http, friendUserId }) {
      var uid = parseInt(String(friendUserId), 10)
      if (!http || !isFinite(uid) || uid <= 0) {
        return { ok: false, message: '参数错误' }
      }
      var api = dpSocialApi(http)
      commit('SET_ACTION_BUSY', 'rm:' + uid)
      try {
        var res = await api.deleteFriend(uid)
        var body = res.data
        if (!dpResultSuccess(body)) {
          return { ok: false, message: dpResultMessage(body) }
        }
        var d = dpResultData(body) || {}
        await dispatch('fetchFriends', { http })
        await dispatch('fetchUnreadCount', { http }).catch(() => {})
        return { ok: true, message: d.message != null ? String(d.message) : '已删除好友' }
      } catch (e) {
        console.error('dpMailbox/removeFriend', e)
        return { ok: false, message: dpAxiosErrorMessage(e, '删除好友失败') }
      } finally {
        commit('SET_ACTION_BUSY', null)
      }
    },
    async acceptFriend({ dispatch, commit }, { http, id }) {
      var api = dpSocialApi(http)
      commit('SET_ACTION_BUSY', 'f:' + id)
      try {
        var res = await api.acceptFriendRequest(id)
        var body = res.data
        if (!dpResultSuccess(body)) {
          alert(dpResultMessage(body))
          return false
        }
        await dispatch('fetchMailbox', { http })
        await dispatch('fetchUnreadCount', { http })
        return true
      } catch (e) {
        console.error('acceptFriend', e)
        alert('网络错误')
        return false
      } finally {
        commit('SET_ACTION_BUSY', null)
      }
    },
    async rejectFriend({ dispatch, commit }, { http, id }) {
      var api = dpSocialApi(http)
      commit('SET_ACTION_BUSY', 'f:' + id)
      try {
        var res = await api.rejectFriendRequest(id)
        var body = res.data
        if (!dpResultSuccess(body)) {
          alert(dpResultMessage(body))
          return false
        }
        await dispatch('fetchMailbox', { http })
        await dispatch('fetchUnreadCount', { http })
        return true
      } catch (e) {
        console.error('rejectFriend', e)
        alert('网络错误')
        return false
      } finally {
        commit('SET_ACTION_BUSY', null)
      }
    },
    async acceptRoomInvite({ dispatch, commit }, { http, id }) {
      var api = dpSocialApi(http)
      commit('SET_ACTION_BUSY', 'r:' + id)
      try {
        var res = await api.acceptRoomInvite(id)
        var body = res.data
        var d = dpResultData(body) || {}
        if (!dpResultSuccess(body)) {
          alert(dpResultMessage(body))
          return { ok: false, roomId: d.roomId }
        }
        await dispatch('fetchMailbox', { http })
        await dispatch('fetchUnreadCount', { http })
        return { ok: true, roomId: d.roomId }
      } catch (e) {
        console.error('acceptRoomInvite', e)
        alert('网络错误')
        return { ok: false }
      } finally {
        commit('SET_ACTION_BUSY', null)
      }
    },
    async rejectRoomInvite({ dispatch, commit }, { http, id }) {
      var api = dpSocialApi(http)
      commit('SET_ACTION_BUSY', 'r:' + id)
      try {
        var res = await api.rejectRoomInvite(id)
        var body = res.data
        if (!dpResultSuccess(body)) {
          alert(dpResultMessage(body))
          return false
        }
        await dispatch('fetchMailbox', { http })
        await dispatch('fetchUnreadCount', { http })
        return true
      } catch (e) {
        console.error('rejectRoomInvite', e)
        alert('网络错误')
        return false
      } finally {
        commit('SET_ACTION_BUSY', null)
      }
    }
  }
}
