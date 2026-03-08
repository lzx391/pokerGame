import Vue from 'vue'
import App from './App.vue'
import axios from 'axios'
import router from './router'
import ElementUI from 'element-ui'
import 'element-ui/lib/theme-chalk/index.css';
Vue.config.productionTip = false
Vue.use(ElementUI);
axios.defaults.baseURL ="/dev-api"
Vue.prototype.$http =axios
new Vue({
  render: h => h(App),
  router:router
}).$mount('#app')
