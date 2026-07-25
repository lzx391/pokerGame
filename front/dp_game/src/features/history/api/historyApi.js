/**
 * History 域 REST 封装（牌谱列表、详情、成就回放）。
 * Wave 6a：薄封装，供后续从 $http 直调迁移；组件仍可直接使用 $http 保持行为不变。
 */

/**
 * @param {import('axios').AxiosInstance} http
 * @param {{ page: number, pageSize: number }} params
 */
export function fetchHandHistoryList(http, params) {
  return http.get('/dpHandHistory/list', { params })
}

/**
 * @param {import('axios').AxiosInstance} http
 * @param {{ page: number, pageSize: number, otherUserId: number }} params
 */
export function fetchHandHistoryListWithOpponent(http, params) {
  return http.get('/dpHandHistory/checkUserAndOtherPlayerHandHistoryList', { params })
}

/**
 * @param {import('axios').AxiosInstance} http
 * @param {{ handHistoryId: number }} params
 */
export function fetchHandHistoryDetail(http, params) {
  return http.get('/dpHandHistory/detail', { params })
}

/**
 * @param {import('axios').AxiosInstance} http
 * @param {{ handHistoryId: number, userId: number }} params
 */
export function fetchAchievementHandHistoryDetail(http, params) {
  return http.get('/dpHandHistory/checkUserAchievementDetail', { params })
}
