<template>
  <div
    class="dp-game-root"
    :data-dp-game-theme="effectiveThemeForCss"
  >
    <div class="dp-lobby-inner cr-page" :class="{ 'cr-page--stagger-ready': staggerReady }">
      <header
        class="cr-page__header"
        :class="{ 'cr-stagger-in': staggerReady }"
        :style="staggerReady ? { '--cr-stagger-delay': '0ms' } : null"
      >
        <div class="cr-page__header-main">
          <h1 class="cr-page__title">创建房间</h1>
          <p class="cr-page__hero">自定义小猫小鱼干与人数，开桌后好友可从大厅加入。</p>
        </div>
        <div class="cr-page__header-actions">
          <div class="dp-game-theme-row cr-page__theme-row">
            <span class="dp-game-theme-row__label">界面主题</span>
            <dp-theme-picker
              :game-ui-theme="gameUiTheme"
              :theme-options="gameThemeOptions"
              @input-theme="onLobbyThemeChange($event)"
            />
            <dp-fluidity-toggle />
          </div>
          <button type="button" class="cr-page__back" @click="goHome">返回大厅</button>
        </div>
      </header>

      <section
        class="dp-lobby-panel cr-page__panel"
        v-loading="creating"
        element-loading-text="正在开局…"
        element-loading-background="rgba(0, 0, 0, 0.35)"
      >
        <div
          class="cr-presets"
          role="group"
          aria-label="快捷预设"
          :class="{ 'cr-stagger-in': staggerReady }"
          :style="staggerReady ? { '--cr-stagger-delay': '40ms' } : null"
        >
          <button
            v-for="preset in roomPresets"
            :key="preset.id"
            type="button"
            class="cr-preset"
            :class="{ 'cr-preset--active': isPresetActive(preset) }"
            :aria-pressed="isPresetActive(preset)"
            @click="applyPreset(preset)"
          >
            <span class="cr-preset__label">{{ preset.label }}</span>
            <span v-if="preset.defaultTag" class="cr-preset__tag">默认</span>
          </button>
        </div>

        <div class="cr-form">
          <div
            class="cr-field-group"
            :class="{ 'cr-stagger-in': staggerReady }"
            :style="staggerReady ? { '--cr-stagger-delay': '80ms' } : null"
          >
            <h2 class="cr-field-group__title">盲注</h2>
            <div class="cr-field">
              <label class="cr-field__label" for="cr-small-blind">小猫（SC）</label>
              <el-input-number
                id="cr-small-blind"
                v-model="smallBlind"
                :min="1"
                :precision="0"
                controls-position="right"
                class="cr-field__control"
              />
            </div>
            <div class="cr-field">
              <span class="cr-field__label">大猫（BC）</span>
              <span class="cr-derived" title="始终为小猫的 2 倍">{{ computedBigBlindChips }}</span>
            </div>
          </div>

          <div
            class="cr-field-group"
            :class="{ 'cr-stagger-in': staggerReady }"
            :style="staggerReady ? { '--cr-stagger-delay': '120ms' } : null"
          >
            <h2 class="cr-field-group__title">带入</h2>
            <div class="cr-field cr-field--stack">
              <label class="cr-field__label" for="cr-starting-bb">每人初始（倍）</label>
              <el-slider
                id="cr-starting-bb"
                v-model="startingStackBb"
                :min="5"
                :max="200"
                :show-tooltip="true"
                class="cr-field__control"
              />
              <el-input-number
                v-model="startingStackBb"
                :min="5"
                :max="200"
                :precision="0"
                controls-position="right"
                class="cr-field__control cr-field__control--narrow"
              />
            </div>
            <p class="cr-field__hint">初始小鱼干 = 大猫鱼干数 × 倍数；之后补满也回到该深度。</p>
          </div>

          <div
            class="cr-field-group"
            :class="{ 'cr-stagger-in': staggerReady }"
            :style="staggerReady ? { '--cr-stagger-delay': '160ms' } : null"
          >
            <h2 class="cr-field-group__title">人数</h2>
            <div class="cr-field">
              <label class="cr-field__label" for="cr-max-seats">上限</label>
              <el-input-number
                id="cr-max-seats"
                v-model="maxSeatCount"
                :min="2"
                :max="9"
                :precision="0"
                controls-position="right"
                class="cr-field__control"
                title="一桌最多几名玩家；含已上桌与预约下一局的总人数"
              />
            </div>
            <p class="cr-field__hint">2～9 人；达到上限后无法再进桌或预约下一局。</p>
          </div>

          <div
            class="cr-field-group"
            :class="{ 'cr-stagger-in': staggerReady }"
            :style="staggerReady ? { '--cr-stagger-delay': '200ms' } : null"
          >
            <h2 class="cr-field-group__title">进房（可选）</h2>
            <div class="cr-field cr-field--full">
              <label class="cr-field__label" for="cr-room-password">密码</label>
              <el-input
                id="cr-room-password"
                v-model.trim="roomPassword"
                show-password
                autocomplete="new-password"
                placeholder="不设则任何人可从大厅进入"
                class="cr-field__control"
              />
            </div>
          </div>

          <div
            class="cr-footer"
            :class="{ 'cr-stagger-in': staggerReady }"
            :style="staggerReady ? { '--cr-stagger-delay': '240ms' } : null"
          >
            <details class="cr-more">
              <summary class="cr-more__summary">了解更多</summary>
              <p class="cr-more__body">
                在此设置本桌小猫小鱼干数（大猫自动为其 2 倍）、每人带入倍数（以大猫鱼干数为 1 倍）、一桌最多几名玩家（2～9）与可选进房密码。创建后将直接开桌进入对局页，你可先独自上桌，朋友随时可从大厅加入。
              </p>
            </details>

            <div class="cr-actions">
              <el-button
                type="primary"
                class="cr-actions__btn"
                :disabled="creating"
                @click="submit"
              >
                {{ creating ? '正在开局…' : '创建并开桌' }}
              </el-button>
            </div>
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
import DpFluidityToggle from '@/components/DpFluidityToggle.vue'
import { ensureDpUserIdInStorage } from '@/utils/dpEnsureUserId'
import { exitLobbyQuickMatchSilently } from '@/utils/dpLobbyQuickMatchExit'
import { prefetchGameChunk } from '@/utils/dpPrefetchGameRoute'

var ROOM_PRESETS = [
  { id: 'casual', label: '休闲桌', smallBlind: 2, startingStackBb: 40, maxSeatCount: 6, roomPassword: '' },
  { id: 'standard', label: '标准桌', smallBlind: 5, startingStackBb: 50, maxSeatCount: 9, roomPassword: '', defaultTag: true },
  { id: 'deep', label: '深鱼干桌', smallBlind: 10, startingStackBb: 100, maxSeatCount: 6, roomPassword: '' }
]

export default {
  name: 'CreateRoom',
  components: { DpFluidityToggle },
  mixins: [dpLobbyThemeMixin],
  data() {
    return {
      user: {},
      smallBlind: 5,
      startingStackBb: 50,
      maxSeatCount: 9,
      roomPassword: '',
      creating: false,
      staggerReady: false,
      roomPresets: ROOM_PRESETS
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
    if (this.shouldSkipStagger()) {
      this.staggerReady = true
    }
  },
  mounted() {
    prefetchGameChunk().catch(function () {
      /* chunk 预加载失败不阻塞创建页 */
    })
    if (this.staggerReady) return
    var self = this
    this.$nextTick(function () {
      requestAnimationFrame(function () {
        self.staggerReady = true
      })
    })
  },
  methods: {
    shouldSkipStagger() {
      if (typeof document !== 'undefined' && document.body.getAttribute('data-dp-fluidity') === 'eco') {
        return true
      }
      if (
        typeof window !== 'undefined' &&
        window.matchMedia &&
        window.matchMedia('(prefers-reduced-motion: reduce)').matches
      ) {
        return true
      }
      return false
    },
    goHome() {
      this.$router.push('/home')
    },
    isPresetActive(preset) {
      return (
        Number(this.smallBlind) === preset.smallBlind &&
        Number(this.startingStackBb) === preset.startingStackBb &&
        Number(this.maxSeatCount) === preset.maxSeatCount &&
        (this.roomPassword || '') === (preset.roomPassword || '')
      )
    },
    applyPreset(preset) {
      this.smallBlind = preset.smallBlind
      this.startingStackBb = preset.startingStackBb
      this.maxSeatCount = preset.maxSeatCount
      this.roomPassword = preset.roomPassword || ''
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
          this.$message.error('创建失败：未返回房间号')
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
          this.$message.warning('房间已创建但开局未成功，请从大厅进入该房间重试')
          this.$router.replace({ name: 'game', params: { roomId: roomId } })
          return
        }
        this.$router.replace({ name: 'game', params: { roomId: roomId } })
      } catch (e) {
        console.error('createRoom', e)
        this.$message.error('创建失败，请检查网络或后端是否已启动')
        this.creating = false
      }
    }
  }
}
</script>

<style scoped>
.cr-page {
  max-width: 720px;
  margin: 0 auto;
  padding: clamp(12px, 3vw, 24px) clamp(12px, 4vw, 20px) clamp(32px, 8vw, 48px);
  box-sizing: border-box;
  font-family: var(--dp-font-ui);
}

.cr-page:not(.cr-page--stagger-ready) .cr-page__header,
.cr-page:not(.cr-page--stagger-ready) .cr-presets,
.cr-page:not(.cr-page--stagger-ready) .cr-field-group,
.cr-page:not(.cr-page--stagger-ready) .cr-footer {
  opacity: 0;
  transform: translateY(8px);
}

.cr-page__header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.cr-page__title {
  font-size: clamp(20px, 4.5vw, 26px);
  margin: 0 0 6px;
  font-weight: 700;
  color: var(--dp-text-primary);
}

.cr-page__hero {
  margin: 0;
  font-size: 15px;
  line-height: 1.5;
  color: var(--dp-text-secondary);
  max-width: 36em;
}

.cr-page__header-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.cr-page__theme-row {
  font-size: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.cr-page__back {
  padding: 8px 14px;
  border: 1px solid var(--dp-input-border);
  border-radius: 6px;
  background: var(--dp-btn-ghost-bg);
  color: var(--dp-text-primary);
  cursor: pointer;
  font-size: 14px;
  font-family: var(--dp-font-ui);
}

.cr-page__back:hover {
  border-color: var(--dp-accent);
  color: var(--dp-accent);
}

.cr-page__panel {
  position: relative;
  min-height: 280px;
}

/* mirror home-quick-card */
.cr-presets {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
}

.cr-preset {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 44px;
  padding: 10px 16px;
  border: 1px solid var(--dp-subpanel-border);
  border-radius: clamp(10px, 2vw, 14px);
  background: var(--dp-subpanel-bg);
  cursor: pointer;
  font-family: var(--dp-font-ui);
  font-size: 14px;
  font-weight: 600;
  color: var(--dp-text-primary);
  position: relative;
  overflow: hidden;
  transition:
    border-color 0.22s cubic-bezier(0.25, 0.46, 0.45, 0.94),
    transform 0.22s cubic-bezier(0.25, 0.46, 0.45, 0.94),
    box-shadow 0.22s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}

.cr-preset::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: var(--dp-accent);
  opacity: 0;
  transition: opacity 0.22s ease;
  pointer-events: none;
}

.cr-preset:hover {
  transform: translateY(-2px);
  box-shadow: var(--dp-depth-elev-2);
  border-color: var(--dp-accent);
}

.cr-preset:hover::after {
  opacity: 0.03;
}

.cr-preset--active {
  border-color: var(--dp-accent);
  background: color-mix(in srgb, var(--dp-accent) 12%, var(--dp-subpanel-bg));
}

.cr-preset__tag {
  font-size: 11px;
  font-weight: 500;
  color: var(--dp-text-muted);
}

.cr-form {
  max-width: 480px;
  margin: 0 auto;
}

.cr-field-group + .cr-field-group {
  margin-top: 20px;
}

.cr-field-group__title {
  margin: 0 0 12px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.02em;
  color: var(--dp-text-primary);
}

.cr-field {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.cr-field--stack {
  flex-direction: column;
  align-items: stretch;
}

.cr-field--full {
  flex-wrap: wrap;
}

.cr-field__label {
  flex: 0 0 8em;
  font-size: 14px;
  color: var(--dp-text-secondary);
}

.cr-field--stack .cr-field__label {
  flex: none;
}

.cr-field__control {
  flex: 1;
  min-width: 0;
  width: 100%;
}

.cr-field__control--narrow {
  max-width: 140px;
  align-self: flex-end;
}

.cr-field__hint {
  margin: 0;
  font-size: 12px;
  color: var(--dp-text-muted);
  line-height: 1.4;
}

.cr-derived {
  flex: 1;
  min-width: 0;
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px dashed var(--dp-subpanel-border);
  background: var(--dp-input-bg, var(--dp-panel-bg));
  color: var(--dp-text-muted);
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  opacity: 0.92;
}

.cr-footer {
  margin-top: 24px;
}

.cr-more {
  margin-bottom: 20px;
}

.cr-more__summary {
  font-size: 14px;
  color: var(--dp-text-secondary);
  cursor: pointer;
  list-style: none;
  user-select: none;
}

.cr-more__summary::-webkit-details-marker {
  display: none;
}

.cr-more__summary::before {
  content: '▶ ';
  font-size: 10px;
  margin-right: 4px;
}

.cr-more[open] .cr-more__summary::before {
  content: '▼ ';
}

.cr-more__body {
  margin: 10px 0 0;
  font-size: 14px;
  color: var(--dp-text-secondary);
  line-height: 1.5;
}

.cr-actions {
  text-align: center;
}

.cr-actions__btn {
  width: 100%;
  max-width: 320px;
}

.cr-page >>> .el-input-number,
.cr-page >>> .el-slider {
  width: 100%;
}

@keyframes cr-stagger-rise {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.cr-stagger-in {
  animation: cr-stagger-rise 280ms cubic-bezier(0.22, 1, 0.36, 1) both;
  animation-delay: var(--cr-stagger-delay, 0ms);
}

@media (max-width: 640px) {
  .cr-page__header-actions {
    width: 100%;
    align-items: stretch;
  }

  .cr-page__theme-row {
    justify-content: flex-start;
  }

  .cr-page__back {
    align-self: flex-end;
  }
}
</style>

<style>
/* eco / reduced-motion: unscoped for body attribute selectors */
body[data-dp-fluidity='eco'] .cr-page:not(.cr-page--stagger-ready) .cr-page__header,
body[data-dp-fluidity='eco'] .cr-page:not(.cr-page--stagger-ready) .cr-presets,
body[data-dp-fluidity='eco'] .cr-page:not(.cr-page--stagger-ready) .cr-field-group,
body[data-dp-fluidity='eco'] .cr-page:not(.cr-page--stagger-ready) .cr-footer,
body[data-dp-fluidity='eco'] .cr-stagger-in {
  animation: none !important;
  opacity: 1 !important;
  transform: none !important;
}

body[data-dp-fluidity='eco'] .cr-preset:hover {
  transform: none;
}

@media (prefers-reduced-motion: reduce) {
  .cr-stagger-in {
    animation: none !important;
    opacity: 1 !important;
    transform: none !important;
  }

  .cr-preset {
    transition: none !important;
  }

  .cr-preset:hover {
    transform: none !important;
  }
}
</style>
