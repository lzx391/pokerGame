import { mapState, mapGetters } from 'vuex'

/**
 * 大厅子页：仅映射主题状态与提交变更。
 * body[data-dp-game-theme] 由 main.js 中 router.afterEach + store.subscribe 统一维护，
 * 勿在组件 beforeDestroy 里移除，否则 SPA 跳转会出现半屏灰底、滚动失效。
 */
export default {
  computed: {
    ...mapState('dpGame', [
      'gameUiTheme',
      'gameThemeOptions',
      'customThemeBase',
      'customAccent'
    ]),
    ...mapGetters('dpGame', ['effectiveThemeForCss', 'customThemeInlineStyle'])
  },
  methods: {
    onLobbyThemeChange(themeId) {
      this.$store.commit('dpGame/SET_GAME_UI_THEME', themeId)
    }
  }
}
