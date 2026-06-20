import Vue from 'vue'
import App from './App.vue'
import axios, { setupHttpClient } from '@/shared/api/http'
import router from './router'
import store from './store'
import { syncDpBodyGameTheme } from '@shared/utils/dpBodyGameTheme'
import { syncDpBodyFluidity } from '@shared/utils/dpBodyFluidity'
import { syncDpBodyRouteTransitionFlag } from '@shared/utils/dpRouteTransitionFlag'
import { syncDpSiteHeartbeat } from '@features/presence/utils/dpSiteHeartbeat'
import {
  initDpSocialStreamClient,
  syncDpSocialStreamConnection
} from '@features/social/sse/dpSocialStreamClient'
import DpThemePicker from '@shared/components/DpThemePicker.vue'

Vue.component('DpThemePicker', DpThemePicker)
/* 主题变量需先于 lobby-shell（body 背景用 var(--dp-game-bg)） */
import './styles/dp-game-themes.css'
import './styles/dp-nickname-font.css'
import './styles/dp-depth-tokens.css'
/* 尽早加载：大厅 #app.app--lobby 与 .dp-game-root 布局 */
import './styles/dp-lobby-shell.css'
import './styles/dp-auth-shell.css'
import './styles/dp-motion-tokens.css'
import './styles/dp-profile-gray-glitch.css'
import './styles/dp-route-transition.css'
import './styles/dp-interactive-hover.css'
import './styles/dp-crt-card-chips.css'
import './styles/dp-game-modals.css'
import './styles/dp-overlay-layers.css'
import './styles/dp-game-responsive-type.css'
import './styles/dp-game-layout-tiers.css'
import './styles/dp-game-element-ui.css'
import './styles/dp-social-lists.css'
import hasPermi from '@/directives/hasPermi'
import {
  Badge,
  Button,
  Checkbox,
  CheckboxGroup,
  Dialog,
  Drawer,
  Form,
  FormItem,
  Input,
  InputNumber,
  Pagination,
  Message,
  MessageBox,
  Slider,
  Table,
  TableColumn,
  Tooltip,
  Upload
} from 'element-ui'
import Loading from 'element-ui/lib/loading'
import 'element-ui/lib/theme-chalk/loading.css'
import 'element-ui/lib/theme-chalk/icon.css'

Vue.config.productionTip = false

Vue.directive('hasPermi', hasPermi)

Vue.use(Badge)
Vue.use(Button)
Vue.use(Checkbox)
Vue.use(CheckboxGroup)
Vue.use(Dialog)
Vue.use(Drawer)
Vue.use(Form)
Vue.use(FormItem)
Vue.use(Input)
Vue.use(InputNumber)
Vue.use(Pagination)
Vue.use(Slider)
Vue.use(Table)
Vue.use(TableColumn)
Vue.use(Tooltip)
Vue.use(Upload)
Vue.use(Loading.directive)

Vue.prototype.$message = Message
Vue.prototype.$confirm = MessageBox.confirm
Vue.prototype.$alert = MessageBox.alert

setupHttpClient(router)
Vue.prototype.$http = axios

initDpSocialStreamClient(store, axios)

router.afterEach(function () {
  syncDpBodyGameTheme(store, router)
  syncDpBodyFluidity(store)
  syncDpBodyRouteTransitionFlag()
  syncDpSiteHeartbeat(axios, router)
  syncDpSocialStreamConnection()
})
router.onReady(function () {
  syncDpBodyGameTheme(store, router)
  syncDpBodyFluidity(store)
  syncDpBodyRouteTransitionFlag()
  syncDpSiteHeartbeat(axios, router)
  syncDpSocialStreamConnection()
})
store.subscribe(function (mutation) {
  if (mutation.type === 'dpGame/SET_GAME_UI_THEME') {
    syncDpBodyGameTheme(store, router)
  }
  if (mutation.type === 'dpGame/SET_ECO_MODE') {
    syncDpBodyFluidity(store)
  }
})

new Vue({
  store,
  render: h => h(App),
  router:router
}).$mount('#app')
