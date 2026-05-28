<template>
  <div
    class="dp-game-root"
    :data-dp-game-theme="effectiveThemeForCss"
    :style="customThemeInlineStyle"
  >
    <div class="dp-lobby-inner create-room-inner">
      <header class="create-room-header">
        <button type="button" class="dp-btn dp-btn--ghost create-room-header__back" @click="goHome">← 返回大厅</button>
        <div class="dp-game-theme-row create-room-theme-row">
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
      </header>

      <section
        class="dp-lobby-panel create-room-panel"
        v-loading="creating"
        element-loading-text="正在开局…"
        element-loading-background="rgba(0, 0, 0, 0.35)"
      >
        <h1 class="create-room-panel__title">创建房间</h1>
        <p class="create-room-panel__intro">在此设置本桌小猫小鱼干数（大猫自动为其 2 倍）、每人带入倍数（以大猫鱼干数为 1 倍）、一桌最多几名玩家（2～9）与可选进房密码。创建后将直接开桌进入对局页，你可先独自上桌，朋友随时可从大厅加入。</p>

        <div class="create-room-fields">
          <label class="create-room-fields__row">
            <span class="create-room-fields__label">小猫（SC）</span>
            <input v-model.number="smallBlind" type="number" min="1" class="create-room-fields__input" />
          </label>
          <div class="create-room-fields__row">
            <span class="create-room-fields__label">大猫（BC）</span>
            <span class="create-room-fields__derived" title="始终为小猫的 2 倍">{{ computedBigBlindChips }}</span>
          </div>
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
          <label class="create-room-fields__row">
            <span class="create-room-fields__label">人数上限</span>
            <input
              v-model.number="maxSeatCount"
              type="number"
              min="2"
              max="9"
              class="create-room-fields__input"
              title="一桌最多几名玩家；含已上桌与预约下一局的总人数"
            />
          </label>
          <p class="create-room-fields__hint">2～9 人；达到上限后无法再进桌或预约下一局。</p>
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
          <button type="button" class="dp-btn dp-btn--primary" :disabled="creating" @click="submit">
            {{ creating ? '正在开局…' : '创建并开桌' }}
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
import { exitLobbyQuickMatchSilently } from '@/utils/dpLobbyQuickMatchExit'
import { prefetchGameChunk } from '@/utils/dpPrefetchGameRoute'

export default {
  name: 'CreateRoom',
  mixins: [dpLobbyThemeMixin],
  data() {
    return {
      user: {},
      smallBlind: 5,
      startingStackBb: 50,
      maxSeatCount: 9,
      roomPassword: '',
      creating: false
    }
  },
  computed: {
    computedBigBlindChips() {
      var sc = Math.max(1, Number(this.smallBlind) || 5)
      return sc * 2
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
  mounted() {
    prefetchGameChunk().catch(function () {
      /* chunk 预加载失败不阻塞创建页 */
    })
  },
  methods: {
    goHome() {
      this.$router.push('/home')
    },
    async submit() {
      if (this.creating) return
      this.creating = true
      try {
        await exitLobbyQuickMatchSilently(this.$http, this.user, {})
        var sc = Math.max(1, Number(this.smallBlind) || 5)
        var cap = Math.round(Number(this.maxSeatCount) || 9)
        cap = Math.min(9, Math.max(2, cap))
        const params = {
          nickname: this.user.nickname,
          smallBlindChips: sc,
          bigBlindChips: sc * 2,
          startingStackBb: Math.max(5, Number(this.startingStackBb) || 50),
          maxSeatCount: cap
        }
        if (this.roomPassword) {
          params.roomPassword = this.roomPassword
        }
        if (this.user.userId != null && this.user.userId !== '') {
          params.userId = this.user.userId
        }
        const res = await this.$http.post('/dpRoom/createRoom', null, { params })
        const roomId = res.data && res.data.roomId
        if (!roomId) {
          alert('创建失败：未返回房间号')
          this.creating = false
          return
        }
        const startRes = await this.$http.post('/dpRoom/startGame', null, {
          params: {
            roomId: roomId,
            ownerNickname: this.user.nickname
          }
        })
        if (startRes.data !== 'ok') {
          alert('房间已创建但开局未成功，请从大厅进入该房间重试')
          this.$router.replace({ name: 'game', params: { roomId: roomId } })
          return
        }
        this.$router.replace({ name: 'game', params: { roomId: roomId } })
      } catch (e) {
        console.error('createRoom', e)
        alert('创建失败，请检查网络或后端是否已启动')
        this.creating = false
      }
    }
  }
}
</script>

<style scoped>
.create-room-panel {
  position: relative;
  min-height: 280px;
}
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
.create-room-fields__derived {
  flex: 1;
  min-width: 0;
  padding: 8px 10px;
  border-radius: 8px;
  border: 1px dashed var(--dp-subpanel-border);
  background: var(--dp-panel-bg);
  color: var(--dp-text-muted);
  font-size: 14px;
}
.create-room-actions {
  text-align: center;
}
</style>
