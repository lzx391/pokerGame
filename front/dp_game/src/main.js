import Vue from 'vue'
import App from './App.vue'
import axios from 'axios'
import router from './router'
import store from './store'
import ElementUI from 'element-ui'
import 'element-ui/lib/theme-chalk/index.css';
Vue.config.productionTip = false
Vue.use(ElementUI);
// 开发：走 vue 代理 /dev-api；生产（含 Docker 同域静态资源）：直接请求当前站点根路径
axios.defaults.baseURL = process.env.NODE_ENV === 'production' ? '' : '/dev-api'
Vue.prototype.$http =axios
new Vue({
  store,
  render: h => h(App),
  router:router
}).$mount('#app')
