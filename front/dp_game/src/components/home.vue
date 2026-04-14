<template>
  <div class="dp-game-root" :data-dp-game-theme="gameUiTheme">
    <div class="dp-lobby-inner home-inner">
      <header class="home-header">
        <h2 class="home-title">游戏大厅</h2>
        <div class="home-header__right">
          <div class="dp-game-theme-row home-theme-row">
            <span class="dp-game-theme-row__label">界面主题</span>
            <select
              class="dp-game-theme-select"
              aria-label="选择界面主题"
              :value="gameUiTheme"
              @change="onLobbyThemeChange($event.target.value)"
            >
              <option v-for="t in gameThemeOptions" :key="t.id" :value="t.id">{{ t.label }}</option>
            </select>
          </div>
          <div class="user-info">
            <span v-if="user && user.nickname">当前用户：{{ user.nickname }}</span>
            <button type="button" class="dp-btn dp-btn--danger logout-btn" @click="logout">退出登录</button>
          </div>
        </div>
      </header>

      <section class="dp-lobby-panel home-actions">
        <h3 class="dp-lobby-panel__title home-actions__title">快捷入口</h3>
        <div class="btns">
          <button type="button" class="dp-btn dp-btn--primary" @click="createRoom">创建房间</button>
          <button type="button" class="dp-btn dp-btn--ghost" @click="goHandHistory">历史对局</button>
          <button type="button" class="dp-btn dp-btn--ghost" @click="goMusicUpload">曲库上传</button>
        </div>
      </section>

      <section class="dp-lobby-panel home-room-list">
        <h3 class="dp-lobby-panel__title">房间列表</h3>
        <p v-if="roomsLoading" class="room-list__hint">加载中…</p>
        <p v-else-if="roomsError" class="room-list__hint room-list__hint--error">{{ roomsError }}</p>
        <p v-else-if="!roomDtos.length" class="room-list__hint">暂无房间，可先点「创建房间」开一桌。</p>
        <div v-else class="room-list__items">
          <div class="room-item" v-for="roomDto in roomDtos" :key="roomDto.roomId">
            <span class="room-item__text">房间 {{ roomDto.roomId }} ({{ roomDto.playerSize }}人)</span>
            <button type="button" class="dp-btn dp-btn--primary room-item__join" @click="joinRoom(roomDto.roomId)">加入</button>
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
import { dpResultSuccess, dpResultMessage } from '@/utils/dpApiResult'

export default {
  mixins: [dpLobbyThemeMixin],
  data() {
    return {
      user: {},
      roomDtos: [],
      roomsLoading: true,
      roomsError: ''
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
  beforeDestroy() {
    if (this.timer) {
      console.log('正在销毁定时器')
      clearInterval(this.timer)
      this.timer = null
    }
  },
  methods: {
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
    async getRooms() {
      try {
        if (!this.roomDtos.length) this.roomsLoading = true
        this.roomsError = ''
        const res = await this.$http.get('/dpRoom/getAllRooms2')
        var list = res.data
        this.roomDtos = Array.isArray(list) ? list : []
      } catch (e) {
        console.error('getRooms', e)
        this.roomsError = '房间列表加载失败，请确认后端已启动。'
        this.roomDtos = []
      } finally {
        this.roomsLoading = false
      }
    },
    async createRoom() {
      const params = { nickname: this.user.nickname }
      if (this.user.userId != null && this.user.userId !== '') {
        params.userId = this.user.userId
      }
      const res = await this.$http.post('/dpRoom/createRoom', null, { params })
      this.$router.push('/room/' + res.data.roomId)
    },
    async joinRoom(roomId) {
      const params = { roomId, nickname: this.user.nickname }
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
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.home-title {
  margin: 0;
  font-size: 1.35rem;
  font-weight: 600;
  color: var(--dp-text-primary);
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
}
.room-item__join {
  flex-shrink: 0;
}
</style>
