<template>
  <div class="dp-game-root" :data-dp-game-theme="gameUiTheme">
    <div class="dp-lobby-inner room-inner">
      <div class="dp-game-theme-row room-theme-row">
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

      <section class="dp-lobby-panel room-panel">
        <h1 class="room-panel__title">房间内</h1>
        <p class="room-panel__line">房间号：<strong>{{ roomId }}</strong></p>
        <p class="room-panel__line">房主：{{ displayNickname(room?.owner) }}</p>

        <h3 class="room-panel__subheading">玩家列表</h3>
        <div class="player" v-for="p in room?.players" :key="p.nickname">
          <span>{{ displayNickname(p.nickname) }}</span>
          <span :class="p.ready ? 'player__ready' : 'player__wait'">{{ p.ready ? '已准备' : '未准备' }}</span>
        </div>

        <div class="btns">
          <button type="button" class="dp-btn dp-btn--success" @click="ready">准备/取消</button>
          <button
            v-if="user.nickname === room?.owner"
            type="button"
            class="dp-btn dp-btn--primary"
            @click="start"
            :disabled="!allReady"
          >
            开始游戏
          </button>
          <button type="button" class="dp-btn dp-btn--danger" @click="exit">退出房间</button>
        </div>
      </section>
    </div>
  </div>
</template>

<script>
import '@/styles/dp-game-themes.css'
import '@/styles/dp-lobby-shell.css'
import dpLobbyThemeMixin from '@/mixins/dpLobbyThemeMixin'
import { dpDisplayNickname } from '../utils/dpDisplayNickname'

export default {
  name: 'Room',
  mixins: [dpLobbyThemeMixin],
  data() {
    return {
      roomId: '',
      room: null,
      user: {},
      allReady: false,
      timer: null,
      heartbeatTimer: null
    }
  },
  created() {
    this.roomId = this.$route.params.roomId
    this.user = JSON.parse(localStorage.getItem('userInfo'))
    this.fetchRoomInfo()

    this.timer = setInterval(this.fetchRoomInfo, 2000)

    this.heartbeatTimer = setInterval(() => {
      this.$http.post('/dpRoom/heartbeat', null, {
        params: {
          roomId: this.roomId,
          nickname: this.user.nickname
        }
      })
    }, 5000)
  },
  beforeDestroy() {
    clearInterval(this.timer)
    clearInterval(this.heartbeatTimer)
  },
  methods: {
    displayNickname: dpDisplayNickname,
    async ready() {
      try {
        await this.$http.post('/dpRoom/toggleReady', null, {
          params: {
            roomId: this.roomId,
            nickname: this.user.nickname
          }
        })
        this.fetchRoomInfo()
      } catch (e) {
        console.error('准备失败', e)
      }
    },

    async start() {
      const res = await this.$http.post('/dpRoom/startGame', null, {
        params: {
          roomId: this.roomId,
          ownerNickname: this.user.nickname
        }
      })
      alert(res.data)
      this.$router.push(`/game/${this.roomId}`)
    },

    async exit() {
      try {
        await this.$http.post('/dpRoom/exitRoom', null, {
          params: {
            roomId: this.roomId,
            nickname: this.user.nickname
          }
        })
        this.$router.push('/home')
      } catch (e) {
        console.error('退出房间失败', e)
        alert('退出房间失败')
      }
    },

    async fetchRoomInfo() {
      try {
        const res = await this.$http.get('/dpRoom/getNowRoom', {
          params: { roomId: this.roomId }
        })
        const room = res.data
        if (!room) {
          this.$router.push('/home')
          return
        }

        this.room = room
        this.allReady = room.players.every((p) => p.ready)

        if (room.playing) {
          this.$router.push('/game/' + this.roomId)
        }
      } catch (e) {
        console.error('获取房间信息失败', e)
      }
    }
  }
}
</script>

<style scoped>
.room-inner {
  padding: 16px 0 32px;
}
.room-theme-row {
  justify-content: center;
  margin-bottom: 16px;
}
.room-panel__title {
  margin: 0 0 12px;
  font-size: 1.25rem;
  font-weight: 600;
  color: var(--dp-text-primary);
}
.room-panel__line {
  margin: 8px 0;
  color: var(--dp-text-secondary);
  font-size: 15px;
}
.room-panel__subheading {
  margin: 20px 0 10px;
  font-size: 1rem;
  font-weight: 600;
  color: var(--dp-text-primary);
}
.player {
  display: flex;
  justify-content: space-between;
  max-width: 320px;
  margin: 10px auto;
  padding: 8px 10px;
  border-bottom: 1px solid var(--dp-subpanel-border);
  color: var(--dp-text-primary);
  font-size: 14px;
}
.player__ready {
  color: var(--dp-success);
  font-weight: 600;
}
.player__wait {
  color: var(--dp-danger);
}
.btns {
  margin-top: 24px;
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}
</style>
