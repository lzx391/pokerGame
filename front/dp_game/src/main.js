import Vue from 'vue'
import App from './App.vue'
import axios from 'axios'
import router from './router'
import store from './store'
import { getRoomNodeContext, dpRoomAxiosPrefixFromWsRoute, getDpDevLobbyApiBases } from '@/utils/dpRoomNodeContext'
import { syncDpBodyGameTheme } from './utils/dpBodyGameTheme'
/* 主题变量需先于 lobby-shell（body 背景用 var(--dp-game-bg)） */
import './styles/dp-game-themes.css'
/* 尽早加载：大厅 #app.app--lobby 与 .dp-game-root 布局 */
import './styles/dp-lobby-shell.css'
import './styles/dp-auth-shell.css'
import './styles/dp-game-element-ui.css'
import ElementUI, { Message } from 'element-ui'
import 'element-ui/lib/theme-chalk/index.css';
Vue.config.productionTip = false
Vue.use(ElementUI);
// 开发经 Nginx 同域 /dev-api（见 docker/nginx/README-dp-dev-two-jvm.md）；生产为站点根路径
axios.defaults.baseURL = process.env.NODE_ENV === 'production' ? '' : '/dev-api'

/** 仅大厅列表：开发环境随机选实例。显式 VUE_APP_LOBBY_API_BASES 优先；未配则从 VUE_APP_DP_DEV_WS_HTTP_MAP（含 /c-api）推导 */
function parseLobbyApiBases () {
  var raw = process.env.VUE_APP_LOBBY_API_BASES
  if (raw && typeof raw === 'string' && raw.trim()) {
    return raw.split(',').map(function (s) { return s.trim() }).filter(Boolean)
  }
  return getDpDevLobbyApiBases()
}
var lobbyApiBases = parseLobbyApiBases()

axios.interceptors.request.use(function (config) {
  var url = config.url || ''
  if (url.indexOf('/dpUser/loginProfile') !== -1 || url.indexOf('/dpUser/registerUser') !== -1) {
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

// 仅 GET /dpRoom/publicRooms（含分页 query）：在配置 VUE_APP_LOBBY_API_BASES 时随机选 baseURL；须在「房间节点」拦截器之前执行
axios.interceptors.request.use(function (config) {
  var url = config.url || ''
  if (url.indexOf('/dpRoom/publicRooms') === 0 && lobbyApiBases.length > 0) {
    config.baseURL = lobbyApiBases[Math.floor(Math.random() * lobbyApiBases.length)]
  }
  return config
})

// 进房后：按 lookup 的 wsRoute 选 Nginx 前缀（见 VUE_APP_DP_DEV_WS_HTTP_MAP；未配时内置 dp-ws-b/8089→/b-api）
axios.interceptors.request.use(function (config) {
  var url = config.url || ''
  if (url.indexOf('/dpRoom/') === 0) {
    if (url.indexOf('/dpRoom/publicRooms') === 0
        || url.indexOf('/dpRoom/lookupRoom') === 0
        || url.indexOf('/dpRoom/createRoom') === 0) {
      return config
    }
    var ctx = getRoomNodeContext()
    if (ctx && ctx.wsRoute) {
      config.baseURL = dpRoomAxiosPrefixFromWsRoute(ctx.wsRoute)
      config.url = url
    }
  }
  return config
})

// 与后端 Spring Security 401（JwtAuthenticationEntryPoint / JwtAuthenticationFilter）对齐：全局提示并回登录页
var handling401 = false
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

Vue.prototype.$http =axios

router.afterEach(function () {
  syncDpBodyGameTheme(store, router)
})
router.onReady(function () {
  syncDpBodyGameTheme(store, router)
})
store.subscribe(function (mutation) {
  if (mutation.type === 'dpGame/SET_GAME_UI_THEME') {
    syncDpBodyGameTheme(store, router)
  }
})

new Vue({
  store,
  render: h => h(App),
  router:router
}).$mount('#app')
