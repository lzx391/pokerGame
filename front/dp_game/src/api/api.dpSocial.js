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
  createRoomInvite: '/dp/room-invites',
  acceptRoomInvite: (id) => `/dp/room-invites/${id}/accept`,
  rejectRoomInvite: (id) => `/dp/room-invites/${id}/reject`
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
    listFriends() {
      return http.get(DP_SOCIAL_PATHS.friends)
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
    createRoomInvite(roomId, inviteeUserId) {
      return http.post(DP_SOCIAL_PATHS.createRoomInvite, { roomId, inviteeUserId })
    },
    acceptRoomInvite(id) {
      return http.post(DP_SOCIAL_PATHS.acceptRoomInvite(id))
    },
    rejectRoomInvite(id) {
      return http.post(DP_SOCIAL_PATHS.rejectRoomInvite(id))
    }
  }
}
