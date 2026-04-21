<template>
  <div class="dp-game-root" :data-dp-game-theme="gameUiTheme">
    <div class="dp-lobby-inner create-room-inner">
      <header class="create-room-header">
        <button type="button" class="dp-btn dp-btn--ghost create-room-header__back" @click="goHome">← 返回大厅</button>
        <div class="dp-game-theme-row create-room-theme-row">
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
      </header>

      <section class="dp-lobby-panel create-room-panel">
        <h1 class="create-room-panel__title">创建房间</h1>
        <p class="create-room-panel__intro">在此设置本桌开局小猫/大猫小鱼干数、每人带入倍数（以大猫鱼干数为 1 倍）与可选进房密码，再进入房间等人。</p>

        <div class="create-room-fields">
          <label class="create-room-fields__row">
            <span class="create-room-fields__label">小猫（SC）</span>
            <input v-model.number="smallBlind" type="number" min="1" class="create-room-fields__input" />
          </label>
          <label class="create-room-fields__row">
            <span class="create-room-fields__label">大猫（BC）</span>
            <input v-model.number="bigBlind" type="number" min="2" class="create-room-fields__input" />
          </label>
          <label class="create-room-fields__row">
            <span class="create-room-fields__label">每人初始（倍）</span>
            <input
              v-model.number="startingStackBb"
              type="number"
              min="5"
              class="create-room-fields__input"
              title="初始小鱼干 = 大猫鱼干数 × 倍数，局深看的是这个"
            />
          </label>
          <p class="create-room-fields__hint">初始小鱼干 = 大猫鱼干数 × 倍数；之后补满也回到该深度。</p>
          <label class="create-room-fields__row create-room-fields__row--full">
            <span class="create-room-fields__label">房间密码（可选）</span>
            <input
              v-model.trim="roomPassword"
              type="password"
              autocomplete="new-password"
              class="create-room-fields__input"
              placeholder="不设则任何人可从大厅进入"
            />
          </label>
        </div>

        <div class="create-room-actions">
          <button type="button" class="dp-btn dp-btn--primary" :disabled="submitting" @click="submit">
            {{ submitting ? '创建中…' : '创建并进入房间' }}
          </button>
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

export default {
  name: 'CreateRoom',
  mixins: [dpLobbyThemeMixin],
  data() {
    return {
      user: {},
      smallBlind: 5,
      bigBlind: 10,
      startingStackBb: 50,
      roomPassword: '',
      submitting: false
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
    if (!this.user.nickname) {
      this.$router.replace('/login')
    }
  },
  methods: {
    goHome() {
      this.$router.push('/home')
    },
    async submit() {
      if (this.submitting) return
      this.submitting = true
      try {
        const params = {
          nickname: this.user.nickname,
          smallBlindChips: Math.max(1, Number(this.smallBlind) || 5),
          bigBlindChips: Math.max(2, Number(this.bigBlind) || 10),
          startingStackBb: Math.max(5, Number(this.startingStackBb) || 50)
        }
        if (this.roomPassword) {
          params.roomPassword = this.roomPassword
        }
        if (this.user.userId != null && this.user.userId !== '') {
          params.userId = this.user.userId
        }
        const res = await this.$http.post('/dpRoom/createRoom', null, { params })
        this.$router.replace('/room/' + res.data.roomId)
      } catch (e) {
        console.error('createRoom', e)
        alert('创建失败，请检查网络或后端是否已启动')
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style scoped>
.create-room-inner {
  padding: 16px 0 32px;
}
.create-room-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}
.create-room-header__back {
  font-size: 14px;
}
.create-room-theme-row {
  margin-left: auto;
}
.create-room-panel__title {
  margin: 0 0 8px;
  font-size: 1.35rem;
  font-weight: 600;
  color: var(--dp-text-primary);
}
.create-room-panel__intro {
  margin: 0 0 20px;
  font-size: 14px;
  color: var(--dp-text-secondary);
  line-height: 1.5;
}
.create-room-fields {
  max-width: 400px;
  margin: 0 auto 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.create-room-fields__row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  color: var(--dp-text-secondary);
}
.create-room-fields__row--full {
  flex-wrap: wrap;
}
.create-room-fields__hint {
  margin: 0;
  font-size: 12px;
  color: var(--dp-text-muted);
  line-height: 1.4;
}
.create-room-fields__label {
  flex: 0 0 8em;
}
.create-room-fields__input {
  flex: 1;
  min-width: 0;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px solid var(--dp-subpanel-border);
  background: var(--dp-panel-bg);
  color: var(--dp-text-primary);
  font-size: 14px;
}
.create-room-actions {
  text-align: center;
}
</style>
