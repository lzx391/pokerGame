/**
 * Lobby 域 REST 封装（公开房列表、建房、加入房间等）。
 * Wave 3：薄封装，组件仍可直接使用 $http 保持行为不变。
 */

/** @param {import('axios').AxiosInstance} http @param {Record<string, unknown>} [params] */
export function fetchPublicRooms(http, params) {
  return http.get('/dpRoom/publicRooms', { params: params || {} })
}

/** @param {import('axios').AxiosInstance} http @param {Record<string, unknown>} params */
export function queryPublicRooms(http, params) {
  return http.get('/dpRoom/publicRooms/query', { params })
}

/** @param {import('axios').AxiosInstance} http @param {Record<string, unknown>} params */
export function joinRoom2(http, params) {
  return http.post('/dpRoom/joinRoom2', null, { params })
}

/** @param {import('axios').AxiosInstance} http @param {Record<string, unknown>} params */
export function createRoom(http, params) {
  return http.post('/dpRoom/createRoom', null, { params })
}

/** @param {import('axios').AxiosInstance} http @param {{ roomId: string }} params */
export function startGame(http, params) {
  return http.post('/dpRoom/startGame', null, { params })
}

/** @param {import('axios').AxiosInstance} http */
export function quickMatch2(http) {
  return http.post('/dpRoom/quickMatch2', null, { params: {} })
}
