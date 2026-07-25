/**
 * 房间 REST（与局内 WS 互补）。
 */

export const DP_ROOM_PATHS = {
  recentChat: (roomId) => `/dpRoom/${encodeURIComponent(roomId)}/chat/recent`
}

/**
 * @param {import('axios').AxiosInstance} http
 */
export function dpRoomApi(http) {
  return {
    /**
     * @param {string} roomId
     * @param {{ limit?: number }} [opts]
     */
    recentChat(roomId, opts) {
      var params = {}
      if (opts && opts.limit != null) params.limit = opts.limit
      return http.get(DP_ROOM_PATHS.recentChat(roomId), { params: params })
    }
  }
}
