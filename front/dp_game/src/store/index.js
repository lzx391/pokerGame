import Vue from 'vue'
import Vuex from 'vuex'
import dpGame from './modules/dpGame'

Vue.use(Vuex)

export default new Vuex.Store({
  modules: {
    dpGame
  }
})
