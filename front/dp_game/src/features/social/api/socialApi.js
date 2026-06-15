/**
 * DP 好友 / 邮箱（REST MVP）路径与请求封装。
 * 鉴权由全局 axios 拦截器注入 Bearer（localStorage userInfo.token）。
 */

export const DP_SOCIAL_PATHS = {
  sendFriendRequest: '/dp/friends/requests',
  pendingFriendInbound: '/dp/friends/requests/pending',
  acceptFriendRequest: (id) => `/dp/friends/requests/${id}/accept`,
  rejectFriendRequest: (id) => `/dp/friends/requests/${id}/reject`,
  friends: '/dp/friends',
  deleteFriend: (friendUserId) => `/dp/friends/${friendUserId}`,
  mailbox: '/dp/mailbox',
  unreadCount: '/dp/mailbox/unread-count',
  friendChatUnreadSummary: '/dp/friends/chat-unread-summary',
  friendMessages: (peerUserId) => `/dp/friends/${peerUserId}/messages`,
  sendFriendMessage: (peerUserId) => `/dp/friends/${peerUserId}/messages`,
  markFriendMessagesRead: (peerUserId) => `/dp/friends/${peerUserId}/messages/read`,
  /** SSE 推送 + 重连补拉（Agent2） */
  socialStream: '/dp/social/stream',
  notifySummary: '/dp/social/notify-summary',
  createRoomInvite: '/dp/room-invites',
  acceptRoomInvite: (id) => `/dp/room-invites/${id}/accept`,
  rejectRoomInvite: (id) => `/dp/room-invites/${id}/reject`,
  /** 互为好友、对方在房内时观众跟随进房（服务端走与接受进房邀请相同的 joinRoomInviteAsSpectator） */
  followFriendRoom: '/dp/friends/follow-room',
  /** 加好友前精确查人（数字→id，否则昵称全等） */
  lookupUser: '/dp/users/lookup'
}

/**
 * @param {import('axios').AxiosInstance} http
 */
export function dpSocialApi(http) {
  return {
    sendFriendRequest(toUserId) {
      return http.post(DP_SOCIAL_PATHS.sendFriendRequest, { toUserId })
    },
    pendingFriendInbound() {
      return http.get(DP_SOCIAL_PATHS.pendingFriendInbound)
    },
    acceptFriendRequest(id) {
      return http.post(DP_SOCIAL_PATHS.acceptFriendRequest(id))
    },
    rejectFriendRequest(id) {
      return http.post(DP_SOCIAL_PATHS.rejectFriendRequest(id))
    },
    /**
     * @param {{ page?: number, pageSize?: number, q?: string }} [opts]
     */
    listFriends(opts) {
      var params = {}
      opts = opts || {}
      if (opts.page != null) params.page = opts.page
      if (opts.pageSize != null) params.pageSize = opts.pageSize
      if (opts.q != null && String(opts.q).trim() !== '') params.q = String(opts.q).trim()
      return http.get(DP_SOCIAL_PATHS.friends, { params: params })
    },
    lookupUser(q) {
      return http.get(DP_SOCIAL_PATHS.lookupUser, { params: { q: q } })
    },
    deleteFriend(friendUserId) {
      return http.delete(DP_SOCIAL_PATHS.deleteFriend(friendUserId))
    },
    mailbox() {
      return http.get(DP_SOCIAL_PATHS.mailbox)
    },
    unreadCount() {
      return http.get(DP_SOCIAL_PATHS.unreadCount)
    },
    friendChatUnreadSummary() {
      return http.get(DP_SOCIAL_PATHS.friendChatUnreadSummary)
    },
    /**
     * @param {number} peerUserId
     * @param {{ beforeId?: number|string, limit?: number }} [opts]
     */
    listFriendMessages(peerUserId, opts) {
      var params = {}
      if (opts && opts.beforeId != null) params.beforeId = opts.beforeId
      if (opts && opts.limit != null) params.limit = opts.limit
      return http.get(DP_SOCIAL_PATHS.friendMessages(peerUserId), { params: params })
    },
    sendFriendMessage(peerUserId, body) {
      return http.post(DP_SOCIAL_PATHS.sendFriendMessage(peerUserId), { body: body })
    },
    markFriendMessagesRead(peerUserId, lastReadMessageId) {
      return http.post(DP_SOCIAL_PATHS.markFriendMessagesRead(peerUserId), {
        lastReadMessageId: lastReadMessageId
      })
    },
    notifySummary() {
      return http.get(DP_SOCIAL_PATHS.notifySummary)
    },
    createRoomInvite(roomId, inviteeUserId) {
      return http.post(DP_SOCIAL_PATHS.createRoomInvite, { roomId, inviteeUserId })
    },
    acceptRoomInvite(id) {
      return http.post(DP_SOCIAL_PATHS.acceptRoomInvite(id))
    },
    rejectRoomInvite(id) {
      return http.post(DP_SOCIAL_PATHS.rejectRoomInvite(id))
    },
    followFriendRoom(friendUserId) {
      return http.post(DP_SOCIAL_PATHS.followFriendRoom, { friendUserId })
    }
  }
}
