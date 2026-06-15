import axios from 'axios'
import { Message } from 'element-ui'
import { disconnectDpSocialStream } from '@features/social/sse/dpSocialStreamClient'

function resolveBaseURL() {
  // 开发：走 vue 代理 /dev-api；生产（含 Docker 同域静态资源）：直接请求当前站点根路径
  // Electron 桌面客户端：连到 config.json 配置的服务器地址
  if (typeof window !== 'undefined' && window.dpElectron && window.dpElectron.serverUrl) {
    return window.dpElectron.serverUrl
  }
  return process.env.NODE_ENV === 'production' ? '' : '/dev-api'
}

var handling401 = false

/**
 * 配置 axios 实例：baseURL、JWT Bearer 请求头、401 全局处理。
 * @param {import('vue-router').default} router
 * @returns {typeof axios}
 */
export function setupHttpClient(router) {
  axios.defaults.baseURL = resolveBaseURL()

  axios.interceptors.request.use(function (config) {
    var url = config.url || ''
    if (url.indexOf('/dpUser/loginProfile') !== -1 || url.indexOf('/dpUser/registerUser') !== -1 || url.indexOf('/oauth/') !== -1) {
      return config
    }
    try {
      var raw = localStorage.getItem('userInfo')
      if (raw) {
        var u = JSON.parse(raw)
        if (u && u.token) {
          config.headers = config.headers || {}
          config.headers.Authorization = 'Bearer ' + u.token
        }
      }
    } catch (e) {
      /* ignore */
    }
    return config
  })

  // 与后端 Spring Security 401（JwtAuthenticationEntryPoint / JwtAuthenticationFilter）对齐：全局提示并回登录页
  axios.interceptors.response.use(
    function (response) {
      return response
    },
    function (error) {
      var status = error.response && error.response.status
      if (status === 401) {
        if (!handling401) {
          handling401 = true
          try {
            localStorage.removeItem('userInfo')
          } catch (e) {
            /* ignore */
          }
          disconnectDpSocialStream()
          var data = error.response && error.response.data
          var msg = (data && (data.message || data.msg)) || '未登录或登录已失效，请重新登录'
          Message.error(msg)
          if (router.currentRoute.path !== '/login') {
            router.replace('/login')
          }
          setTimeout(function () {
            handling401 = false
          }, 800)
        }
      }
      return Promise.reject(error)
    }
  )

  return axios
}

export default axios
