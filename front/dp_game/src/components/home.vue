<template>
  <div
    class="dp-game-root"
    :data-dp-game-theme="effectiveThemeForCss"
    :style="customThemeInlineStyle"
  >
    <div class="dp-lobby-inner home-inner">
      <game-play-guide-modal
        :visible="playGuideVisible"
        :active-tab="playGuideTab"
        :items="handRankReference"
        :first-run="playGuideFirstRun"
        @close="onPlayGuideClose"
        @tab-change="playGuideTab = $event"
        @confirm="onPlayGuideConfirm"
      />

      <header class="home-header">
        <h2 class="home-title">猫咪牌局 · 大厅</h2>
        <div class="home-header__right">
          <div class="dp-game-theme-row home-theme-row">
            <span class="dp-game-theme-row__label">界面主题</span>
            <dp-theme-picker
              :game-ui-theme="gameUiTheme"
              :theme-options="gameThemeOptions"
              :custom-theme-base="customThemeBase"
              :custom-theme-overrides="customThemeOverrides"
              @input-theme="onLobbyThemeChange($event)"
              @custom-base="$store.commit('dpGame/SET_CUSTOM_THEME', { baseId: $event })"
              @custom-overrides="$store.commit('dpGame/SET_CUSTOM_THEME', { overrides: $event })"
            />
          </div>
          <div class="user-info">
            <span v-if="user && user.nickname">当前用户：{{ user.nickname }}</span>
            <button type="button" class="dp-btn dp-btn--ghost logout-btn" @click="openPlayGuide(false)">玩法说明</button>
            <button type="button" class="dp-btn dp-btn--danger logout-btn" @click="logout">退出登录</button>
          </div>
        </div>
      </header>

      <section class="dp-lobby-panel home-actions">
        <h3 class="dp-lobby-panel__title home-actions__title">快捷入口</h3>
        <div class="btns">
          <button
            type="button"
            class="dp-btn dp-btn--success"
            :disabled="quickMatchLoading && !quickMatchPolling"
            @click="onQuickMatchButtonClick"
          >
            {{ quickMatchPolling ? '取消匹配' : quickMatchLoading ? '匹配中…' : '快速匹配' }}
          </button>
          <button type="button" class="dp-btn dp-btn--primary" @click="goCreateRoom">创建房间</button>
          <button type="button" class="dp-btn dp-btn--ghost" @click="goHandHistory">历史对局</button>
          <button type="button" class="dp-btn dp-btn--ghost" @click="goMusicUpload">曲库上传</button>
        </div>
      </section>

      <section class="dp-lobby-panel home-room-list">
        <h3 class="dp-lobby-panel__title">房间列表</h3>
        <div class="home-filters" aria-label="筛选与搜索">
          <div class="home-filters__row">
            <label class="home-filters__item">
              <span class="home-filters__label">房间号</span>
              <input
                v-model.trim="filters.roomId"
                type="text"
                class="home-filters__input"
                placeholder="精确匹配"
                maxlength="32"
                @keyup.enter="applyFilters"
              />
            </label>
            <label class="home-filters__item home-filters__item--num">
              <span class="home-filters__label">大猫鱼干≥</span>
              <input
                v-model="filters.minBigBlind"
                type="number"
                min="0"
                class="home-filters__input"
                placeholder="可选"
              />
            </label>
            <label class="home-filters__item home-filters__item--num">
              <span class="home-filters__label">大猫鱼干≤</span>
              <input
                v-model="filters.maxBigBlind"
                type="number"
                min="0"
                class="home-filters__input"
                placeholder="可选"
              />
            </label>
            <label class="home-filters__item home-filters__item--num">
              <span class="home-filters__label">人数≥</span>
              <input
                v-model="filters.minPlayers"
                type="number"
                min="0"
                class="home-filters__input"
                placeholder="可选"
              />
            </label>
            <label class="home-filters__item home-filters__item--num">
              <span class="home-filters__label">人数≤</span>
              <input
                v-model="filters.maxPlayers"
                type="number"
                min="0"
                class="home-filters__input"
                placeholder="可选"
              />
            </label>
            <label class="home-filters__item">
              <span class="home-filters__label">房间</span>
              <select v-model="filters.password" class="home-filters__select">
                <option value="any">全部</option>
                <option value="locked">仅密码房</option>
                <option value="open">仅公开</option>
              </select>
            </label>
          </div>
          <div class="home-filters__actions">
            <button type="button" class="dp-btn dp-btn--primary" @click="applyFilters">搜索</button>
            <button type="button" class="dp-btn dp-btn--ghost" @click="resetFilters">重置</button>
            <span v-if="useFilterQuery" class="home-filters__mode">当前：条件查询（缓存加速）</span>
            <span v-else class="home-filters__mode">当前：默认列表（缓存加速）</span>
          </div>
        </div>
        <p v-if="roomsLoading" class="room-list__hint">加载中…</p>
        <p v-else-if="roomsError" class="room-list__hint room-list__hint--error">{{ roomsError }}</p>
        <p v-else-if="!roomDtos.length" class="room-list__hint">暂无房间，可先点「创建房间」开一桌。</p>
        <div v-else class="room-list__items">
          <div class="room-item" v-for="roomDto in roomDtos" :key="roomDto.roomId">
            <span class="room-item__text">
              房间 {{ roomDto.roomId }} ({{ roomDto.playerSize }}/{{ roomDto.maxSeatCount != null ? roomDto.maxSeatCount : 9 }}人)
              <span v-if="roomDto.smallBlindChips != null && roomDto.bigBlindChips != null" class="room-item__blinds">
                · 小猫/大猫 {{ roomDto.smallBlindChips }}/{{ roomDto.bigBlindChips }}<template v-if="roomDto.startingStackBb"> · {{ roomDto.startingStackBb }} 倍</template>
              </span>
              <span v-if="roomDto.passwordProtected" class="room-item__lock" title="需要密码">🔒</span>
            </span>
            <button type="button" class="dp-btn dp-btn--primary room-item__join" @click="joinRoom(roomDto)">加入</button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script>
import '@/styles/dp-game-themes.css'
import '@/styles/dp-lobby-shell.css'
import dpLobbyThemeMixin from '@/mixins/dpLobbyThemeMixin'
import { ensureDpUserIdInStorage } from '@/utils/dpEnsureUserId'
import { dpResultSuccess, dpResultData, dpResultMessage } from '@/utils/dpApiResult'
import GamePlayGuideModal from '@/components/GamePlayGuideModal.vue'
import { mapGetters } from 'vuex'
import {
  peekCatTutorialRequested,
  clearCatTutorialSessionFlag,
  isCatTutorialDismissedPermanently,
  setCatTutorialDismissedPermanently
} from '@/constants/dpCatThemeCopy'

export default {
  components: { GamePlayGuideModal },
  mixins: [dpLobbyThemeMixin],
  data() {
    return {
      playGuideVisible: false,
      playGuideTab: 'flow',
      playGuideFirstRun: false,
      user: {},
      roomDtos: [],
      roomsLoading: true,
      roomsError: '',
      /** 表单绑定；是否走 /publicRooms/query 由 applyFilters 写入 useFilterQuery */
      filters: {
        roomId: '',
        minBigBlind: '',
        maxBigBlind: '',
        minPlayers: '',
        maxPlayers: '',
        password: 'any'
      },
      useFilterQuery: false,
      quickMatchLoading: false,
      /** 已入默认快匹队列，靠 /ws/dp-quick-match 推送直至 MATCHED */
      quickMatchPolling: false,
      quickMatchWs: null,
      quickMatchWsSession: 0,
      quickMatchWsNoReconnect: false,
      quickMatchWsReconnectTimer: null,
      quickMatchWsReconnectAttempt: 0
    }
  },
  computed: {
    ...mapGetters('dpGame', ['handRankReference']),
    pageSize() {
      return 20
    }
  },
  async created() {
    try {
      await ensureDpUserIdInStorage(this.$http)
      var raw = localStorage.getItem('userInfo')
      this.user = raw ? JSON.parse(raw) : {}
    } catch (e) {
      this.user = {}
    }
    this.getRooms()
    this.timer = setInterval(() => {
      this.getRooms()
    }, 2000)
  },
  mounted() {
    if (peekCatTutorialRequested()) {
      clearCatTutorialSessionFlag()
      if (!isCatTutorialDismissedPermanently()) {
        this.openPlayGuide(true)
      }
    }
  },
  beforeDestroy() {
    if (this.timer) {
      console.log('正在销毁定时器')
      clearInterval(this.timer)
      this.timer = null
    }
    if (this.quickMatchPolling) {
      this.cancelQuickMatchRemote()
      this.disconnectQuickMatchWs()
      this.quickMatchPolling = false
      this.quickMatchLoading = false
    }
  },
  methods: {
    openPlayGuide(firstRun) {
      this.playGuideFirstRun = !!firstRun
      this.playGuideTab = 'flow'
      this.playGuideVisible = true
    },
    onPlayGuideClose() {
      this.playGuideVisible = false
      this.playGuideFirstRun = false
    },
    onPlayGuideConfirm(payload) {
      if (payload && payload.dontShowAgain) {
        setCatTutorialDismissedPermanently()
      }
      this.onPlayGuideClose()
    },
    logout() {
      localStorage.removeItem('userInfo')
      this.$router.push('/')
    },
    goHandHistory() {
      this.$router.push('/hand-history')
    },
    goMusicUpload() {
      this.$router.push('/music-upload')
    },
    goCreateRoom() {
      this.$router.push('/create-room')
    },
    /** 排队中再点一次：断开快匹 WebSocket 并取消后端队列 */
    async onQuickMatchButtonClick() {
      if (this.quickMatchPolling) {
        this.disconnectQuickMatchWs()
        this.quickMatchPolling = false
        this.quickMatchLoading = false
        try {
          await this.cancelQuickMatchRemote()
        } catch (e) {
          console.error('quickMatchCancel', e)
        }
        return
      }
      await this.quickMatch()
    },
    quickMatchWsBaseUrl() {
      var secure = window.location.protocol === 'https:'
      return (secure ? 'wss:' : 'ws:') + '//' + window.location.host
    },
    disconnectQuickMatchWs() {
      this.quickMatchWsNoReconnect = true
      this.clearQuickMatchWsReconnectTimer()
      this.quickMatchWsReconnectAttempt = 0
      this.quickMatchWsSession++
      var w = this.quickMatchWs
      this.quickMatchWs = null
      if (w) {
        w.onopen = null
        w.onclose = null
        w.onerror = null
        w.onmessage = null
        try {
          w.close()
        } catch (e) { /* ignore */ }
      }
    },
    clearQuickMatchWsReconnectTimer() {
      if (this.quickMatchWsReconnectTimer != null) {
        clearTimeout(this.quickMatchWsReconnectTimer)
        this.quickMatchWsReconnectTimer = null
      }
    },
    /**
     * 排队等待 MATCHED 时 WebSocket 断线：指数退避重连；成功后服务端 {@link pushQuickMatchLobbySnapshot} 会补发状态。
     */
    scheduleQuickMatchWsReconnect(sessionAtClose) {
      var self = this
      if (this.quickMatchWsNoReconnect) return
      if (!this.quickMatchPolling) return
      if (this.quickMatchWsSession !== sessionAtClose) return
      this.clearQuickMatchWsReconnectTimer()
      var exp = Math.min(5, this.quickMatchWsReconnectAttempt)
      var delay = Math.min(30000, 1000 * Math.pow(2, exp))
      var jitter = Math.floor(Math.random() * 400)
      this.quickMatchWsReconnectAttempt++
      this.quickMatchWsReconnectTimer = setTimeout(function () {
        self.quickMatchWsReconnectTimer = null
        if (self.quickMatchWsNoReconnect) return
        if (!self.quickMatchPolling) return
        if (self.quickMatchWsSession !== sessionAtClose) return
        var g = self.quickMatchWs
        if (g && (g.readyState === WebSocket.OPEN || g.readyState === WebSocket.CONNECTING)) return
        self.reconnectQuickMatchWsWhileWaiting(sessionAtClose)
      }, delay + jitter)
    },
    reconnectQuickMatchWsWhileWaiting(prevSession) {
      var self = this
      if (this.quickMatchWsNoReconnect || !this.quickMatchPolling) return
      if (this.quickMatchWsSession !== prevSession) return
      if (!this.user || !this.user.nickname || !this.user.token) return
      this.quickMatchWsSession++
      var sessionAtOpen = this.quickMatchWsSession
      if (this.quickMatchWs) {
        var old = this.quickMatchWs
        this.quickMatchWs = null
        old.onopen = null
        old.onclose = null
        old.onerror = null
        old.onmessage = null
        try {
          old.close()
        } catch (e) { /* ignore */ }
      }
      var path =
        process.env.NODE_ENV === 'development' ? '/dp-ws/dp-quick-match' : '/ws/dp-quick-match'
      var url =
        this.quickMatchWsBaseUrl() +
        path +
        '?nickname=' +
        encodeURIComponent(this.user.nickname) +
        '&token=' +
        encodeURIComponent(String(this.user.token))
      var ws
      try {
        ws = new WebSocket(url)
      } catch (e) {
        this.scheduleQuickMatchWsReconnect(sessionAtOpen)
        return
      }
      this.quickMatchWs = ws
      ws.onopen = function () {
        if (self.quickMatchWsSession !== sessionAtOpen || self.quickMatchWsNoReconnect) return
        self.quickMatchWsReconnectAttempt = 0
      }
      ws.onerror = function () { /* onclose 里统一兜底重连 */ }
      ws.onclose = function () {
        if (self.quickMatchWsSession !== sessionAtOpen) return
        if (self.quickMatchWs === ws) self.quickMatchWs = null
        self.scheduleQuickMatchWsReconnect(sessionAtOpen)
      }
      ws.onmessage = function (ev) {
        self.handleQuickMatchWsMessage(ev, sessionAtOpen)
      }
    },
    connectQuickMatchWs() {
      var self = this
      return new Promise(function (resolve, reject) {
        if (!self.user || !self.user.nickname) {
          reject(new Error('no user'))
          return
        }
        var tok = self.user.token ? String(self.user.token) : ''
        if (!tok) {
          reject(new Error('no token'))
          return
        }
        self.disconnectQuickMatchWs()
        self.quickMatchWsNoReconnect = false
        var sessionAtOpen = ++self.quickMatchWsSession
        var path =
          process.env.NODE_ENV === 'development' ? '/dp-ws/dp-quick-match' : '/ws/dp-quick-match'
        var url =
          self.quickMatchWsBaseUrl() +
          path +
          '?nickname=' +
          encodeURIComponent(self.user.nickname) +
          '&token=' +
          encodeURIComponent(tok)
        var ws
        try {
          ws = new WebSocket(url)
        } catch (e) {
          reject(e)
          return
        }
        self.quickMatchWs = ws
        var settled = false
        ws.onopen = function () {
          if (self.quickMatchWsSession !== sessionAtOpen || self.quickMatchWsNoReconnect) return
          if (settled) return
          settled = true
          self.quickMatchWsReconnectAttempt = 0
          resolve()
        }
        ws.onerror = function () {
          if (self.quickMatchWsSession !== sessionAtOpen) return
          if (!settled) {
            settled = true
            reject(new Error('ws error'))
          }
        }
        ws.onclose = function () {
          if (self.quickMatchWsSession !== sessionAtOpen) return
          if (!settled) {
            settled = true
            reject(new Error('ws closed before open'))
            return
          }
          if (self.quickMatchWs === ws) self.quickMatchWs = null
          self.scheduleQuickMatchWsReconnect(sessionAtOpen)
        }
        ws.onmessage = function (ev) {
          self.handleQuickMatchWsMessage(ev, sessionAtOpen)
        }
      })
    },
    handleQuickMatchWsMessage(ev, sessionAtOpen) {
      if (this.quickMatchWsSession !== sessionAtOpen) return
      try {
        var data = JSON.parse(ev.data)
        if (data._ws !== 'quickMatch') return
        if (data.state === 'MATCHED' && data.roomId) {
          this.quickMatchPolling = false
          this.quickMatchLoading = false
          this.disconnectQuickMatchWs()
          this.$router.push('/game/' + data.roomId)
          return
        }
        if (data.state === 'IDLE') {
          this.quickMatchPolling = false
          this.quickMatchLoading = false
          this.disconnectQuickMatchWs()
          alert(data.message || '已不在匹配队列')
        }
      } catch (e) {
        console.error('quickMatchWs message', e)
      }
    },
    async quickMatch() {
      if (!this.user || !this.user.nickname) {
        alert('请先登录后再快速匹配')
        return
      }
      if (!this.user.token) {
        alert('登录已失效，请重新登录')
        return
      }
      this.quickMatchLoading = true
      try {
        await this.connectQuickMatchWs()
      } catch (e) {
        console.error('quickMatch ws', e)
        alert('匹配通道连接失败，请稍后重试')
        this.quickMatchLoading = false
        return
      }
      try {
        const params = { nickname: this.user.nickname }
        if (this.user.userId != null && this.user.userId !== '') {
          params.userId = this.user.userId
        }
        const res = await this.$http.post('/dpRoom/quickMatch2', null, { params })
        const body = res.data
        if (!dpResultSuccess(body)) {
          this.disconnectQuickMatchWs()
          alert(dpResultMessage(body))
          return
        }
        const data = dpResultData(body) || {}
        if (data.roomId) {
          this.disconnectQuickMatchWs()
          this.$router.push('/game/' + data.roomId)
          return
        }
        if (data.queued && data.state === 'WAITING') {
          this.quickMatchPolling = true
          return
        }
        this.disconnectQuickMatchWs()
        alert('匹配响应异常，请稍后重试')
      } catch (e) {
        console.error('quickMatch', e)
        this.disconnectQuickMatchWs()
        alert('网络错误，请稍后重试')
      } finally {
        if (!this.quickMatchPolling) {
          this.quickMatchLoading = false
        }
      }
    },
    cancelQuickMatchRemote() {
      if (!this.user || !this.user.nickname) return Promise.resolve()
      const params = { nickname: this.user.nickname }
      return this.$http.post('/dpRoom/quickMatchCancel2', null, { params }).catch(() => {})
    },
    /** 与当前 filters 是否应走 MyBatis 查询一致（用于搜索按钮） */
    filtersActiveFromForm() {
      if ((this.filters.roomId || '').length > 0) return true
      if (this.filters.password !== 'any') return true
      const n = (s) => (s === '' || s == null ? NaN : parseInt(String(s), 10))
      if (!isNaN(n(this.filters.minBigBlind))) return true
      if (!isNaN(n(this.filters.maxBigBlind))) return true
      if (!isNaN(n(this.filters.minPlayers))) return true
      if (!isNaN(n(this.filters.maxPlayers))) return true
      return false
    },
    buildFilterQueryParams() {
      const o = { page: 1, pageSize: this.pageSize }
      const rid = (this.filters.roomId || '').trim()
      if (rid) o.roomId = rid
      const n = (s) => (s === '' || s == null ? NaN : parseInt(String(s), 10))
      const minBB = n(this.filters.minBigBlind)
      if (!isNaN(minBB)) o.minBigBlindChips = minBB
      const maxBB = n(this.filters.maxBigBlind)
      if (!isNaN(maxBB)) o.maxBigBlindChips = maxBB
      const minP = n(this.filters.minPlayers)
      if (!isNaN(minP)) o.minPlayerCount = minP
      const maxP = n(this.filters.maxPlayers)
      if (!isNaN(maxP)) o.maxPlayerCount = maxP
      if (this.filters.password === 'locked') o.passwordProtected = true
      if (this.filters.password === 'open') o.passwordProtected = false
      return o
    },
    applyFilters() {
      this.useFilterQuery = this.filtersActiveFromForm()
      this.getRooms()
    },
    resetFilters() {
      this.filters = {
        roomId: '',
        minBigBlind: '',
        maxBigBlind: '',
        minPlayers: '',
        maxPlayers: '',
        password: 'any'
      }
      this.useFilterQuery = false
      this.getRooms()
    },
    async getRooms() {
      try {
        if (this.useFilterQuery && !this.filtersActiveFromForm()) {
          this.useFilterQuery = false
        }
        const useQuery = this.useFilterQuery && this.filtersActiveFromForm()
        if (!this.roomDtos.length) this.roomsLoading = true
        this.roomsError = ''
        const base = { page: 1, pageSize: this.pageSize }
        const url = useQuery ? '/dpRoom/publicRooms/query' : '/dpRoom/publicRooms'
        const params = useQuery ? this.buildFilterQueryParams() : base
        const res = await this.$http.get(url, { params })
        var list = res && res.data ? res.data.list : []
        this.roomDtos = Array.isArray(list) ? list : []
      } catch (e) {
        console.error('getRooms', e)
        this.roomsError = '房间列表加载失败，请确认后端已启动。'
        this.roomDtos = []
      } finally {
        this.roomsLoading = false
      }
    },
    async joinRoom(roomDto) {
      const roomId = typeof roomDto === 'string' ? roomDto : roomDto.roomId
      let roomPassword = ''
      if (roomDto && roomDto.passwordProtected) {
        roomPassword = window.prompt('请输入房间密码') || ''
        if (!roomPassword.trim()) {
          alert('需要输入密码才能加入')
          return
        }
      }
      const params = { roomId, nickname: this.user.nickname }
      if (roomPassword) {
        params.roomPassword = roomPassword.trim()
      }
      if (this.user.userId != null && this.user.userId !== '') {
        params.userId = this.user.userId
      }
      const res = await this.$http.post('/dpRoom/joinRoom2', null, { params })
      const body = res.data
      if (!dpResultSuccess(body)) {
        alert(dpResultMessage(body))
        return
      }
      this.$router.push('/room/' + roomId)
    }
  }
}
</script>

<style scoped>
.home-inner {
  padding-bottom: 24px;
}
.home-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: clamp(10px, 2.5vw, 16px);
  margin-bottom: clamp(12px, 3vw, 20px);
  flex-wrap: wrap;
}
.home-title {
  margin: 0;
  font-size: clamp(1.12rem, 3.8vw, 1.4rem);
  font-weight: 600;
  color: var(--dp-text-primary);
  line-height: 1.25;
}
.home-header__right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}
.home-theme-row {
  justify-content: flex-end;
}
.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: var(--dp-text-secondary);
  flex-wrap: wrap;
}
.logout-btn {
  padding: 6px 10px;
  font-size: 13px;
}
.home-actions__title {
  margin-bottom: 14px;
}
.btns {
  text-align: center;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  align-items: center;
}
.home-room-list .dp-lobby-panel__title {
  margin-bottom: 8px;
}
.home-filters {
  margin-bottom: 12px;
  padding: 12px;
  border: 1px solid var(--dp-subpanel-border);
  border-radius: 8px;
  background: var(--dp-subpanel-bg, rgba(0, 0, 0, 0.04));
}
.home-filters__row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 12px;
  align-items: flex-end;
}
.home-filters__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.home-filters__item--num {
  max-width: 110px;
}
.home-filters__label {
  font-size: 12px;
  color: var(--dp-text-muted);
}
.home-filters__input,
.home-filters__select {
  padding: 6px 8px;
  font-size: 14px;
  border: 1px solid var(--dp-subpanel-border);
  border-radius: 6px;
  background: var(--dp-panel-bg, #fff);
  color: var(--dp-text-primary);
  min-width: 0;
}
.home-filters__input:focus,
.home-filters__select:focus {
  outline: 2px solid var(--dp-accent, #409eff);
  outline-offset: 0;
}
.home-filters__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}
.home-filters__mode {
  font-size: 12px;
  color: var(--dp-text-muted);
  margin-left: 4px;
}
.room-list__hint {
  margin: 12px 0;
  padding: 8px 0;
  color: var(--dp-text-muted);
  font-size: 14px;
  line-height: 1.5;
}
.room-list__hint--error {
  color: var(--dp-danger);
}
.room-list__items {
  margin-top: 8px;
}
.room-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--dp-subpanel-border);
}
.room-item:last-child {
  border-bottom: none;
}
.room-item__text {
  color: var(--dp-text-primary);
  font-size: 14px;
  line-height: 1.4;
}
.room-item__blinds {
  color: var(--dp-text-muted);
  font-size: 13px;
}
.room-item__lock {
  margin-left: 4px;
  font-size: 13px;
}
.room-item__join {
  flex-shrink: 0;
}
</style>
