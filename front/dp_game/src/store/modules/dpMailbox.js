import { dpSocialApi } from '@/api/api.dpSocial'
import { dpResultSuccess, dpResultData, dpResultMessage, dpAxiosErrorMessage } from '@/utils/dpApiResult'
import { dpFriendsInviteEligible } from '@/utils/dpFriendsInviteEligible'
import { parseSocialNotifyPayload } from '@/utils/dpSocialStream'

function initialState() {
  return {
    unreadCount: 0,
    friendChatUnreadTotal: 0,
    /** @type {Record<string, number>} peerUserId -> count */
    friendChatUnreadByUserId: {},
    friendRequests: [],
    roomInvites: [],
    friends: [],
    mailboxLoading: false,
    friendsLoading: false,
    actionBusyId: null
  }
}

function normalizePerFriendMap(perFriend) {
  var map = {}
  if (!Array.isArray(perFriend)) return map
  for (var i = 0; i < perFriend.length; i++) {
    var row = perFriend[i]
    var uid = row && row.userId != null ? Number(row.userId) : 0
    var c = row && row.count != null ? Number(row.count) : 0
    if (!isFinite(uid) || uid <= 0) continue
    if (isFinite(c) && c > 0) map[String(uid)] = Math.floor(c)
  }
  return map
}

export default {
  namespaced: true,
  state: initialState(),
  getters: {
    friendUnreadForUser: function (state) {
      return function (userId) {
        var key = userId != null ? String(userId) : ''
        if (!key) return 0
        var n = state.friendChatUnreadByUserId[key]
        return n != null && n > 0 ? n : 0
      }
    }
  },
  mutations: {
    SET_UNREAD(state, n) {
      var v = parseInt(String(n), 10)
      state.unreadCount = isFinite(v) && v > 0 ? v : 0
    },
    SET_FRIEND_CHAT_UNREAD(state, payload) {
      payload = payload || {}
      var tv = parseInt(String(payload.totalUnread), 10)
      state.friendChatUnreadTotal = isFinite(tv) && tv > 0 ? tv : 0
      state.friendChatUnreadByUserId = normalizePerFriendMap(payload.perFriend)
    },
    APPLY_NOTIFY_SUMMARY(state, payload) {
      var parsed = parseSocialNotifyPayload(payload)
      state.unreadCount = parsed.mailboxUnread
      state.friendChatUnreadTotal = parsed.friendChatUnreadTotal
      state.friendChatUnreadByUserId = normalizePerFriendMap(parsed.perFriend)
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
    async fetchFriendChatUnreadSummary({ commit }, { http }) {
      if (!http) return
      var api = dpSocialApi(http)
      try {
        var res = await api.friendChatUnreadSummary()
        var body = res.data
        if (!dpResultSuccess(body)) return
        var d = dpResultData(body) || {}
        commit('SET_FRIEND_CHAT_UNREAD', {
          totalUnread: d.totalUnread,
          perFriend: d.perFriend
        })
      } catch (e) {
        console.error('dpMailbox/fetchFriendChatUnreadSummary', e)
      }
    },
    async fetchNotifySummary({ commit, dispatch }, { http }) {
      if (!http) return
      var api = dpSocialApi(http)
      try {
        var res = await api.notifySummary()
        var body = res.data
        if (!dpResultSuccess(body)) return
        commit('APPLY_NOTIFY_SUMMARY', dpResultData(body))
      } catch (e) {
        /* Agent2 未就绪时回退分别拉取 */
        await Promise.all([
          dispatch('fetchUnreadCount', { http }),
          dispatch('fetchFriendChatUnreadSummary', { http })
        ]).catch(function () {})
      }
    },
    applyNotifyPayload({ commit }, payload) {
      commit('APPLY_NOTIFY_SUMMARY', payload)
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
        // data 通常为 { friends: [...] }；兼容误将数组放在 data 根上的响应
        var raw =
          Array.isArray(d.friends) ? d.friends : Array.isArray(d) ? d : []
        commit('SET_FRIENDS', dpFriendsInviteEligible(raw))
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
    async followFriendToTheirRoom({ dispatch }, { http, friendUserId }) {
      var api = dpSocialApi(http)
      var uid = friendUserId != null ? Number(friendUserId) : 0
      if (!isFinite(uid) || uid <= 0) {
        return { ok: false }
      }
      try {
        var res = await api.followFriendRoom(uid)
        var body = res.data
        var d = dpResultData(body) || {}
        if (!dpResultSuccess(body)) {
          alert(dpResultMessage(body))
          return { ok: false, roomId: d.roomId }
        }
        await dispatch('fetchUnreadCount', { http }).catch(function () {})
        return { ok: true, roomId: d.roomId }
      } catch (e) {
        console.error('followFriendToTheirRoom', e)
        alert('网络错误')
        return { ok: false }
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
