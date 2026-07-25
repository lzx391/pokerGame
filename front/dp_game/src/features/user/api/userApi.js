/**
 * User 域 REST 封装（loginProfile、registerUser、资料/OAuth）。
 * Wave 2：薄封装，供后续从 $http 直调迁移；组件仍可直接使用 $http 保持行为不变。
 */

/**
 * @param {import('axios').AxiosInstance} http
 * @param {{ nickname: string, password: string }} params
 */
export function loginProfile(http, params) {
  return http.get('/dpUser/loginProfile', { params })
}

/**
 * @param {import('axios').AxiosInstance} http
 * @param {{ nickname: string, password: string }} body
 */
export function registerUser(http, body) {
  return http.post('/dpUser/registerUser', body)
}

/** @param {import('axios').AxiosInstance} http */
export function fetchUserProfile(http) {
  return http.get('/dpUser/profile')
}

/**
 * @param {import('axios').AxiosInstance} http
 * @param {FormData} formData
 */
export function uploadUserAvatar(http, formData) {
  return http.post('/dpUser/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/**
 * @param {import('axios').AxiosInstance} http
 * @param {Record<string, unknown>} body
 */
export function updateUserProfile(http, body) {
  return http.put('/dpUser/profile', body)
}

/**
 * @param {import('axios').AxiosInstance} http
 * @param {Record<string, unknown>} body
 */
export function updateUserPassword(http, body) {
  return http.put('/dpUser/password', body)
}

/**
 * @param {import('axios').AxiosInstance} http
 * @param {{ oid: string }} body
 */
export function exchangeOAuthToken(http, body) {
  return http.post('/oauth/exchange-token', body)
}

/** @param {import('axios').AxiosInstance} http @param {'github'|'gitee'|'ding'} provider */
export function getOAuthAuthorizeUrl(http, provider) {
  return http.get('/oauth/' + provider + '/authorize-url')
}
