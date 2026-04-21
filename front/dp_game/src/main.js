import Vue from 'vue'
import App from './App.vue'
import axios from 'axios'
import router from './router'
import store from './store'
import { syncDpBodyGameTheme } from './utils/dpBodyGameTheme'
import DpThemePicker from './components/DpThemePicker.vue'

Vue.component('DpThemePicker', DpThemePicker)
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
// 开发：走 vue 代理 /dev-api；生产（含 Docker 同域静态资源）：直接请求当前站点根路径
axios.defaults.baseURL = process.env.NODE_ENV === 'production' ? '' : '/dev-api'

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
  if (
    mutation.type === 'dpGame/SET_GAME_UI_THEME' ||
    mutation.type === 'dpGame/SET_CUSTOM_THEME'
  ) {
    syncDpBodyGameTheme(store, router)
  }
})

new Vue({
  store,
  render: h => h(App),
  router:router
}).$mount('#app')
