import Vue from 'vue'
import Vuex from 'vuex'
import dpGame from '@features/room/store/dpGame'
import dpMailbox from '@features/social/store/dpMailbox'
import dpAchievement from '@features/achievement/store/dpAchievement'

Vue.use(Vuex)

export default new Vuex.Store({
  modules: {
    dpGame,
    dpMailbox,
    dpAchievement
  }
})
