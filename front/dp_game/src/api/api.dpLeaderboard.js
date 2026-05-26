/**
 * 周榜 REST（permitAll；登录时 axios 拦截器自动带 Bearer，用于 myRank / myMultiplier）。
 */

export const DP_LEADERBOARD_PATHS = {
  weeklyHand: '/dp/leaderboard/weekly/hand',
  weeklyRoom: '/dp/leaderboard/weekly/room'
}

/**
 * @param {import('axios').AxiosInstance} http
 * @param {{ limit?: number }} [opts]
 */
export function getWeeklyHandLeaderboard(http, opts) {
  var params = { limit: 50 }
  if (opts && opts.limit != null) params.limit = opts.limit
  return http.get(DP_LEADERBOARD_PATHS.weeklyHand, { params: params })
}

/**
 * @param {import('axios').AxiosInstance} http
 * @param {{ limit?: number }} [opts]
 */
export function getWeeklyRoomLeaderboard(http, opts) {
  var params = { limit: 50 }
  if (opts && opts.limit != null) params.limit = opts.limit
  return http.get(DP_LEADERBOARD_PATHS.weeklyRoom, { params: params })
}
