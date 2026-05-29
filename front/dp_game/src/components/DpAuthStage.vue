<template>
  <div
    class="dp-auth-stage"
    :class="{
      'dp-auth-stage--booting': phase !== 'idle',
      'dp-auth-stage--transition': phase === 'transition',
      'dp-auth-stage--interactive': contentInteractive
    }"
  >
    <div class="dp-auth-stage__theme-bar dp-game-theme-row">
      <span class="dp-game-theme-row__label">界面主题</span>
      <dp-theme-picker
        :game-ui-theme="gameUiTheme"
        :theme-options="gameThemeOptions"
        :custom-theme-base="customThemeBase"
        :custom-theme-overrides="customThemeOverrides"
        @input-theme="$emit('input-theme', $event)"
        @custom-base="$emit('custom-base', $event)"
        @custom-overrides="$emit('custom-overrides', $event)"
      />
    </div>

    <div class="dp-auth-stage__set" aria-hidden="false">
      <!-- 大电视 -->
      <div class="dp-auth-stage__tv" aria-hidden="true">
        <div class="dp-auth-stage__antenna dp-auth-stage__antenna--l" />
        <div class="dp-auth-stage__antenna dp-auth-stage__antenna--r" />
        <div class="dp-auth-stage__tv-body">
          <div class="dp-auth-stage__bezel">
            <div
              class="dp-auth-stage__screen"
              :class="{ 'dp-auth-stage__screen--on': screenOn }"
            >
              <!-- CRT 花屏：噪点 + 上滚横纹 + 亮带（容器抖动） -->
              <div
                class="dp-auth-stage__snow"
                :class="{ 'dp-auth-stage__snow--active': snowActive }"
                aria-hidden="true"
              >
                <span class="dp-auth-stage__snow-noise" />
                <span class="dp-auth-stage__snow-bars" />
                <span class="dp-auth-stage__snow-bright" />
              </div>
              <div class="dp-auth-stage__scanlines" />
              <div class="dp-auth-stage__vignette" />

              <!-- 开机 / 转场闪白 -->
              <div
                class="dp-auth-stage__flash"
                :class="{ 'dp-auth-stage__flash--pulse': flashPulse }"
              />

              <!-- 屏幕内容 -->
              <div
                class="dp-auth-stage__content"
                :aria-hidden="!contentInteractive"
              >
                <h1 class="dp-auth-stage__title">{{ appAuthTitle }}</h1>

                <nav class="dp-auth-stage__tabs" aria-label="登录或注册">
                  <button
                    type="button"
                    class="dp-auth-stage__tab"
                    :class="{ 'dp-auth-stage__tab--active': authMode === 'login' }"
                    :disabled="!contentInteractive"
                    @click="requestMode('login')"
                  >
                    登录
                  </button>
                  <button
                    type="button"
                    class="dp-auth-stage__tab"
                    :class="{ 'dp-auth-stage__tab--active': authMode === 'register' }"
                    :disabled="!contentInteractive"
                    @click="requestMode('register')"
                  >
                    注册
                  </button>
                </nav>

                <div class="dp-auth-stage__form-panel">
                  <router-view v-if="contentVisible" :key="authMode" />
                </div>
              </div>
            </div>
          </div>
          <div class="dp-auth-stage__controls">
            <span class="dp-auth-stage__knob" />
            <span class="dp-auth-stage__knob dp-auth-stage__knob--sm" />
            <span class="dp-auth-stage__led" :class="{ 'dp-auth-stage__led--on': screenOn }" />
          </div>
        </div>
        <div class="dp-auth-stage__tv-base" aria-hidden="true">
          <div class="dp-auth-stage__tv-feet">
            <span /><span />
          </div>
          <div class="dp-auth-stage__tv-plinth" />
        </div>
      </div>

      <!-- 复古机柜 -->
      <div class="dp-auth-stage__cabinet" aria-hidden="true">
        <div class="dp-auth-stage__cabinet-top" />
        <div class="dp-auth-stage__cabinet-face">
          <div class="dp-auth-stage__drawer">
            <span class="dp-auth-stage__drawer-knob" />
          </div>
          <div class="dp-auth-stage__drawer dp-auth-stage__drawer--narrow">
            <span class="dp-auth-stage__drawer-knob" />
          </div>
          <div class="dp-auth-stage__cabinet-slot" />
        </div>
        <div class="dp-auth-stage__cabinet-legs">
          <span /><span /><span />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { mapState } from 'vuex'
import { CAT_COPY } from '@/constants/dpCatThemeCopy'

/** @typedef {'boot' | 'idle' | 'transition'} AuthPhase */

const TIMING = {
  bootStatic: 880,
  bootFlash: 480,
  transitionStatic: 520,
  transitionFlash: 120,
  transitionSettle: 180
}

const TIMING_ECO = {
  bootStatic: 0,
  bootFlash: 0,
  transitionStatic: 60,
  transitionFlash: 0,
  transitionSettle: 40
}

export default {
  name: 'DpAuthStage',
  props: {
    appAuthTitle: {
      type: String,
      default: () => CAT_COPY.appAuthTitle
    }
  },
  data() {
    return {
      /** @type {AuthPhase} */
      phase: 'boot',
      authMode: 'login',
      snowActive: true,
      flashPulse: false,
      screenOn: false,
      contentVisible: false,
      contentInteractive: false,
      bootDone: false,
      timers: []
    }
  },
  computed: {
    ...mapState('dpGame', [
      'gameUiTheme',
      'gameThemeOptions',
      'customThemeBase',
      'customThemeOverrides',
      'ecoMode'
    ]),
    timing() {
      return this.ecoMode || this.prefersReducedMotion ? TIMING_ECO : TIMING
    },
    prefersReducedMotion() {
      if (typeof window === 'undefined' || !window.matchMedia) return false
      return window.matchMedia('(prefers-reduced-motion: reduce)').matches
    }
  },
  watch: {
    '$route.path': {
      immediate: true,
      handler(path) {
        const next = path === '/register' ? 'register' : 'login'
        if (!this.bootDone) {
          this.authMode = next
          return
        }
        if (next !== this.authMode) {
          this.runTransition(next, { pushRoute: false })
        }
      }
    }
  },
  mounted() {
    this.authMode = this.$route.path === '/register' ? 'register' : 'login'
    this.runBootSequence()
  },
  beforeDestroy() {
    this.clearTimers()
  },
  methods: {
    clearTimers() {
      this.timers.forEach((id) => clearTimeout(id))
      this.timers = []
    },
    schedule(fn, ms) {
      const id = setTimeout(fn, ms)
      this.timers.push(id)
      return id
    },
    runBootSequence() {
      this.clearTimers()
      const t = this.timing
      this.phase = 'boot'
      this.snowActive = true
      this.flashPulse = false
      this.screenOn = false
      this.contentVisible = false
      this.contentInteractive = false

      if (t.bootStatic + t.bootFlash <= 0) {
        this.finishBootInstant()
        return
      }

      this.schedule(() => {
        this.flashPulse = true
        this.schedule(() => {
          this.flashPulse = false
        }, t.bootFlash)
      }, t.bootStatic)

      this.schedule(() => {
        this.snowActive = false
        this.screenOn = true
        this.contentVisible = true
        this.phase = 'idle'
        this.bootDone = true
        this.schedule(() => {
          this.contentInteractive = true
        }, 80)
      }, t.bootStatic + t.bootFlash)
    },
    finishBootInstant() {
      this.snowActive = false
      this.screenOn = true
      this.contentVisible = true
      this.contentInteractive = true
      this.phase = 'idle'
      this.bootDone = true
    },
    requestMode(mode) {
      if (!this.contentInteractive || mode === this.authMode) return
      const path = mode === 'register' ? '/register' : '/login'
      if (this.$route.path === path) return
      this.runTransition(mode, { pushRoute: true })
    },
    runTransition(nextMode, { pushRoute }) {
      if (this.phase === 'transition') return
      this.clearTimers()
      const t = this.timing
      this.phase = 'transition'
      this.contentInteractive = false
      this.contentVisible = false
      this.snowActive = true
      this.flashPulse = false

      const swapAt = t.transitionStatic
      const endAt = t.transitionStatic + t.transitionFlash + t.transitionSettle

      if (swapAt + endAt <= 0) {
        this.authMode = nextMode
        if (pushRoute) {
          const path = nextMode === 'register' ? '/register' : '/login'
          if (this.$route.path !== path) this.$router.replace(path)
        }
        this.phase = 'idle'
        this.snowActive = false
        this.contentVisible = true
        this.contentInteractive = true
        return
      }

      this.schedule(() => {
        this.authMode = nextMode
        if (pushRoute) {
          const path = nextMode === 'register' ? '/register' : '/login'
          if (this.$route.path !== path) this.$router.replace(path)
        }
        if (t.transitionFlash > 0) {
          this.flashPulse = true
          this.schedule(() => {
            this.flashPulse = false
          }, t.transitionFlash)
        }
      }, swapAt)

      this.schedule(() => {
        this.snowActive = false
        this.contentVisible = true
        this.phase = 'idle'
        this.schedule(() => {
          this.contentInteractive = true
        }, 60)
      }, endAt)
    }
  }
}
</script>

<style scoped>
.dp-auth-stage {
  --dp-auth-tv-shell: color-mix(in srgb, var(--dp-subpanel-bg, #e8ecf0) 68%, var(--dp-panel-border, #c5cdd6));
  --dp-auth-tv-bezel: color-mix(in srgb, var(--dp-text-secondary, #5a6578) 58%, #1a1f28);
  --dp-auth-tv-screen-off: color-mix(in srgb, var(--dp-text-muted, #909399) 38%, #14181e);
  --dp-auth-cabinet-wood: color-mix(in srgb, var(--dp-text-secondary, #6b5a48) 35%, #3d3228);
  --dp-auth-tv-border: clamp(5px, 1.25vw, 7px);
  --dp-auth-tv-shell-pad: clamp(12px, 3vw, 17px);
  --dp-auth-tv-bezel-pad: clamp(10px, 2.6vw, 14px);
  --dp-auth-tv-bezel-radius: clamp(14px, 3.6vw, 18px);
  --dp-auth-tv-shell-radius: clamp(18px, 4.5vw, 24px);
  width: 100%;
  max-width: min(100%, 38rem);
  margin: 0 auto;
}

.dp-auth-stage__theme-bar {
  justify-content: flex-end;
  margin-bottom: clamp(10px, 2.5vw, 16px);
  position: relative;
  z-index: 2;
}

.dp-auth-stage__set {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0;
}

/* —— 大电视 —— */
.dp-auth-stage__tv {
  width: 100%;
  max-width: 100%;
  position: relative;
  filter: drop-shadow(
    0 14px 28px color-mix(in srgb, var(--dp-text-primary, #2c3e50) 22%, transparent)
  );
}

.dp-auth-stage__antenna {
  position: absolute;
  bottom: calc(100% - 8px);
  width: 2px;
  height: clamp(20px, 5.5vw, 30px);
  opacity: 0.72;
  background: linear-gradient(
    to top,
    var(--dp-auth-tv-bezel),
    color-mix(in srgb, var(--dp-accent, #409eff) 45%, var(--dp-auth-tv-bezel))
  );
  border-radius: 2px;
  transform-origin: bottom center;
}

.dp-auth-stage__antenna--l {
  left: 22%;
  transform: rotate(-20deg);
}

.dp-auth-stage__antenna--r {
  right: 22%;
  transform: rotate(20deg);
}

.dp-auth-stage__tv-body {
  position: relative;
  background: linear-gradient(
    168deg,
    color-mix(in srgb, var(--dp-panel-bg, #fff) 42%, var(--dp-auth-tv-shell)),
    var(--dp-auth-tv-shell)
  );
  border: var(--dp-auth-tv-border) solid
    color-mix(in srgb, var(--dp-panel-border, #dcdfe6) 62%, var(--dp-auth-tv-bezel));
  border-radius: var(--dp-auth-tv-shell-radius) var(--dp-auth-tv-shell-radius)
    clamp(10px, 2.5vw, 12px) clamp(10px, 2.5vw, 12px);
  padding: var(--dp-auth-tv-shell-pad) clamp(14px, 3.2vw, 18px) clamp(10px, 2.4vw, 12px);
  box-shadow:
    inset 0 2px 0 color-mix(in srgb, var(--dp-panel-bg, #fff) 55%, transparent),
    inset 0 -3px 0 color-mix(in srgb, var(--dp-auth-tv-bezel) 22%, transparent),
    0 6px 0 color-mix(in srgb, var(--dp-auth-tv-bezel) 32%, transparent),
    0 10px 24px color-mix(in srgb, var(--dp-text-primary, #2c3e50) 14%, transparent);
}

/* 侧面散热栅（纯装饰） */
.dp-auth-stage__tv-body::before,
.dp-auth-stage__tv-body::after {
  content: '';
  position: absolute;
  top: 28%;
  width: clamp(5px, 1.2vw, 7px);
  height: 34%;
  border-radius: 2px;
  background: repeating-linear-gradient(
    to bottom,
    color-mix(in srgb, var(--dp-auth-tv-bezel) 55%, transparent) 0 2px,
    transparent 2px 5px
  );
  opacity: 0.45;
  pointer-events: none;
}

.dp-auth-stage__tv-body::before {
  left: clamp(4px, 1vw, 6px);
}

.dp-auth-stage__tv-body::after {
  right: clamp(4px, 1vw, 6px);
}

.dp-auth-stage__bezel {
  background: linear-gradient(
    175deg,
    color-mix(in srgb, var(--dp-auth-tv-bezel) 88%, var(--dp-panel-bg, #fff)),
    var(--dp-auth-tv-bezel)
  );
  border-radius: var(--dp-auth-tv-bezel-radius);
  padding: var(--dp-auth-tv-bezel-pad);
  box-shadow:
    inset 0 5px 16px color-mix(in srgb, #000 48%, transparent),
    inset 0 0 0 clamp(2px, 0.55vw, 3px) color-mix(in srgb, var(--dp-auth-tv-bezel) 75%, #000),
    0 1px 0 color-mix(in srgb, var(--dp-panel-bg, #fff) 18%, transparent);
}

.dp-auth-stage__screen {
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  aspect-ratio: 16 / 9;
  min-height: clamp(248px, 47vw, 308px);
  border-radius: clamp(4px, 1vw, 6px);
  overflow: hidden;
  background: var(--dp-auth-tv-screen-off);
  box-shadow:
    inset 0 3px 12px color-mix(in srgb, #000 55%, transparent),
    inset 0 0 0 1px color-mix(in srgb, var(--dp-auth-tv-bezel) 70%, #000);
  transition: background-color 0.35s ease;
}

.dp-auth-stage__screen--on {
  background: color-mix(in srgb, var(--dp-panel-bg, #f0f2f5) 92%, #1a1f24);
}

/* CRT 花屏容器：整体颤抖，子层分别负责噪点 / 横纹 / 亮带 */
.dp-auth-stage__snow {
  position: absolute;
  inset: -8%;
  z-index: 5;
  opacity: 0;
  pointer-events: none;
  overflow: hidden;
  transition: opacity 0.12s ease;
  background: color-mix(in srgb, #1a1e24 88%, #3a4048);
}

.dp-auth-stage__snow--active {
  opacity: 0.92;
  animation: dp-auth-crt-jitter 0.07s steps(2) infinite;
}

.dp-auth-stage__snow-noise,
.dp-auth-stage__snow-bars,
.dp-auth-stage__snow-bright {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

/* 层 1：细颗粒噪点 + 快速错位 */
.dp-auth-stage__snow-noise {
  opacity: 0.88;
  background-color: transparent;
  background-image:
    repeating-radial-gradient(
      circle at 12% 18%,
      rgba(255, 255, 255, 0.62) 0 0.45px,
      transparent 0.55px
    ),
    repeating-radial-gradient(
      circle at 78% 62%,
      rgba(210, 218, 228, 0.5) 0 0.4px,
      transparent 0.5px
    ),
    repeating-radial-gradient(
      circle at 44% 81%,
      rgba(240, 245, 252, 0.42) 0 0.35px,
      transparent 0.45px
    ),
    repeating-linear-gradient(
      92deg,
      rgba(255, 255, 255, 0.06) 0,
      rgba(255, 255, 255, 0.06) 1px,
      transparent 1px,
      transparent 2px
    );
  background-size:
    3px 3px,
    4px 4px,
    2px 2px,
    5px 100%;
  animation: dp-auth-noise-flicker 0.05s steps(3) infinite;
}

@keyframes dp-auth-noise-flicker {
  0% {
    background-position: 0 0, 1px 0, 0 1px, 0 0;
    opacity: 0.82;
  }
  33% {
    background-position: 2px 1px, -1px 2px, 1px -1px, 3px 0;
    opacity: 0.95;
  }
  66% {
    background-position: -1px 2px, 2px -1px, -2px 1px, -2px 0;
    opacity: 0.78;
  }
  100% {
    background-position: 1px -2px, 0 2px, 2px 0, 1px 0;
    opacity: 0.9;
  }
}

/* 层 2：横纹亮带自下往上滚 */
.dp-auth-stage__snow-bars {
  opacity: 0.75;
  mix-blend-mode: screen;
  background: repeating-linear-gradient(
    to bottom,
    rgba(0, 0, 0, 0.14) 0,
    rgba(0, 0, 0, 0.14) 1px,
    rgba(235, 240, 248, 0.16) 1px,
    rgba(235, 240, 248, 0.16) 2px,
    rgba(255, 255, 255, 0.08) 2px,
    rgba(255, 255, 255, 0.08) 3px,
    transparent 3px,
    transparent 6px
  );
  background-size: 100% 6px;
  animation: dp-auth-roll-bars 0.28s linear infinite;
}

@keyframes dp-auth-roll-bars {
  0% {
    background-position: 0 0;
  }
  100% {
    background-position: 0 -6px;
  }
}

/* 层 3：偶尔扫过的更亮横条 */
.dp-auth-stage__snow-bright {
  inset: auto -4% -35%;
  height: 22%;
  opacity: 0;
  background: linear-gradient(
    to bottom,
    transparent 0%,
    rgba(255, 255, 255, 0.22) 28%,
    rgba(255, 255, 255, 0.48) 50%,
    rgba(255, 255, 255, 0.22) 72%,
    transparent 100%
  );
  animation: dp-auth-bright-bar 1.05s linear infinite;
  mix-blend-mode: screen;
}

@keyframes dp-auth-bright-bar {
  0% {
    transform: translateY(0);
    opacity: 0;
  }
  8% {
    opacity: 0.55;
  }
  92% {
    opacity: 0.45;
  }
  100% {
    transform: translateY(-520%);
    opacity: 0;
  }
}

@keyframes dp-auth-crt-jitter {
  0% {
    transform: translate(0, 0);
  }
  25% {
    transform: translate(-1.5px, 0.5px);
  }
  50% {
    transform: translate(1px, -1px);
  }
  75% {
    transform: translate(-0.5px, 1px);
  }
  100% {
    transform: translate(1.5px, -0.5px);
  }
}

.dp-auth-stage__scanlines {
  position: absolute;
  inset: 0;
  z-index: 2;
  pointer-events: none;
  background: repeating-linear-gradient(
    to bottom,
    transparent 0,
    transparent 2px,
    rgba(0, 0, 0, 0.14) 2px,
    rgba(0, 0, 0, 0.14) 3px
  );
  background-size: 100% 3px;
  opacity: 0.38;
  animation: dp-auth-scan-roll 2.8s linear infinite;
}

.dp-auth-stage--booting .dp-auth-stage__scanlines,
.dp-auth-stage--transition .dp-auth-stage__scanlines {
  opacity: 0.52;
  animation: dp-auth-scan-roll-fast 0.42s linear infinite;
}

@keyframes dp-auth-scan-roll {
  0% {
    background-position: 0 0;
  }
  100% {
    background-position: 0 12px;
  }
}

@keyframes dp-auth-scan-roll-fast {
  0% {
    background-position: 0 0;
  }
  100% {
    background-position: 0 -3px;
  }
}

.dp-auth-stage__vignette {
  position: absolute;
  inset: 0;
  z-index: 3;
  pointer-events: none;
  background: radial-gradient(
    ellipse 88% 78% at 50% 48%,
    transparent 32%,
    rgba(0, 0, 0, 0.42) 100%
  );
}

.dp-auth-stage__flash {
  position: absolute;
  inset: 0;
  z-index: 6;
  pointer-events: none;
  opacity: 0;
  background: transparent;
}

.dp-auth-stage__flash--pulse {
  animation: dp-auth-flash-pulse 0.48s ease-out forwards;
}

@keyframes dp-auth-flash-pulse {
  0% {
    opacity: 1;
    background: #f8fafc;
  }
  22% {
    opacity: 1;
    background: color-mix(in srgb, var(--dp-accent, #409eff) 14%, #fff);
  }
  55% {
    opacity: 0.7;
    background: color-mix(in srgb, var(--dp-text-muted, #909399) 35%, #e8eaed);
  }
  100% {
    opacity: 0;
    background: transparent;
  }
}

.dp-auth-stage__content {
  position: relative;
  z-index: 4;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  flex: 1 1 auto;
  min-height: 0;
  max-height: 100%;
  width: 100%;
  padding: clamp(8px, 2vw, 12px) clamp(10px, 2.5vw, 14px) clamp(8px, 2vw, 12px);
  box-sizing: border-box;
  overflow-x: hidden;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
  scrollbar-color: color-mix(in srgb, var(--dp-accent, #409eff) 35%, transparent) transparent;
  opacity: 0;
  transform: scale(0.98);
  transition:
    opacity 0.28s ease,
    transform 0.32s ease;
  pointer-events: none;
}

.dp-auth-stage__content::-webkit-scrollbar {
  width: 4px;
}

.dp-auth-stage__content::-webkit-scrollbar-thumb {
  border-radius: 4px;
  background: color-mix(in srgb, var(--dp-accent, #409eff) 40%, var(--dp-text-muted, #909399));
}

.dp-auth-stage--interactive .dp-auth-stage__content {
  opacity: 1;
  transform: scale(1);
  pointer-events: auto;
}

.dp-auth-stage__title {
  flex-shrink: 0;
  margin: 0 0 clamp(4px, 1.2vw, 8px);
  font-size: clamp(1.05rem, 3.8vw, 1.4rem);
  font-weight: 700;
  letter-spacing: 0.1em;
  line-height: 1.2;
  color: var(--dp-text-primary, #2c3e50);
  text-shadow: 0 1px 0 color-mix(in srgb, var(--dp-panel-bg, #fff) 40%, transparent);
}

.dp-auth-stage__tabs {
  display: flex;
  flex-shrink: 0;
  justify-content: center;
  gap: clamp(6px, 1.5vw, 10px);
  margin-bottom: clamp(6px, 1.5vw, 10px);
}

.dp-auth-stage__tab {
  flex: 1 1 auto;
  max-width: 8.5rem;
  padding: clamp(6px, 1.5vw, 8px) clamp(10px, 2.5vw, 14px);
  font-size: clamp(12px, 3.2vw, 13px);
  font-weight: 600;
  font-family: inherit;
  border-radius: 8px;
  border: 1px solid var(--dp-panel-border, #dcdfe6);
  background: color-mix(in srgb, var(--dp-subpanel-bg, #eef1f4) 80%, var(--dp-panel-bg, #fff));
  color: var(--dp-text-secondary, #5a6578);
  cursor: pointer;
  transition:
    background 0.15s ease,
    color 0.15s ease,
    box-shadow 0.15s ease;
}

.dp-auth-stage__tab--active {
  background: var(--dp-btn-primary-bg, #409eff);
  color: var(--dp-btn-primary-fg, #fff);
  border-color: color-mix(in srgb, var(--dp-accent, #409eff) 60%, var(--dp-panel-border));
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--dp-accent, #409eff) 35%, transparent);
}

.dp-auth-stage__tab:disabled {
  cursor: wait;
  opacity: 0.65;
}

.dp-auth-stage__form-panel {
  flex: 1 1 auto;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  width: 100%;
  min-width: 0;
  min-height: 0;
  overflow-x: hidden;
}

.dp-auth-stage__controls {
  display: flex;
  align-items: center;
  gap: clamp(8px, 2vw, 10px);
  margin-top: clamp(10px, 2.5vw, 12px);
  padding: 0 clamp(6px, 1.5vw, 8px);
}

.dp-auth-stage__knob {
  width: clamp(18px, 4.5vw, 22px);
  height: clamp(18px, 4.5vw, 22px);
  border-radius: 50%;
  background: radial-gradient(
    circle at 35% 30%,
    color-mix(in srgb, var(--dp-panel-bg, #fff) 50%, var(--dp-auth-tv-shell)),
    var(--dp-auth-tv-bezel)
  );
  border: 1px solid color-mix(in srgb, var(--dp-auth-tv-bezel) 70%, #000);
  box-shadow: inset 0 -2px 4px rgba(0, 0, 0, 0.28);
}

.dp-auth-stage__knob--sm {
  width: clamp(13px, 3.2vw, 16px);
  height: clamp(13px, 3.2vw, 16px);
}

.dp-auth-stage__led {
  margin-left: auto;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--dp-text-muted, #909399) 80%, #444);
  opacity: 0.5;
  transition:
    background 0.25s ease,
    box-shadow 0.25s ease,
    opacity 0.25s ease;
}

.dp-auth-stage__led--on {
  background: var(--dp-accent, #409eff);
  opacity: 0.85;
  box-shadow: 0 0 6px color-mix(in srgb, var(--dp-accent, #409eff) 55%, transparent);
  animation: dp-auth-led-pulse 2.2s ease-in-out infinite;
}

@keyframes dp-auth-led-pulse {
  0%,
  100% {
    opacity: 0.6;
  }
  50% {
    opacity: 1;
  }
}

.dp-auth-stage__tv-base {
  width: 100%;
  margin-top: clamp(2px, 0.6vw, 4px);
}

.dp-auth-stage__tv-feet {
  display: flex;
  justify-content: space-between;
  padding: 0 clamp(20px, 7vw, 44px);
}

.dp-auth-stage__tv-feet span {
  width: clamp(34px, 9.5vw, 48px);
  height: clamp(8px, 2vw, 10px);
  border-radius: 0 0 clamp(4px, 1vw, 6px) clamp(4px, 1vw, 6px);
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--dp-auth-tv-bezel) 90%, var(--dp-panel-bg, #fff)),
    var(--dp-auth-tv-bezel)
  );
  box-shadow: 0 2px 4px color-mix(in srgb, var(--dp-text-primary, #2c3e50) 18%, transparent);
}

.dp-auth-stage__tv-plinth {
  height: clamp(6px, 1.6vw, 8px);
  margin: 2px clamp(12px, 4vw, 28px) 0;
  border-radius: 0 0 clamp(6px, 1.5vw, 8px) clamp(6px, 1.5vw, 8px);
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--dp-auth-tv-bezel) 75%, var(--dp-auth-tv-shell)),
    color-mix(in srgb, var(--dp-auth-tv-bezel) 95%, #000)
  );
  box-shadow: 0 4px 10px color-mix(in srgb, var(--dp-text-primary, #2c3e50) 16%, transparent);
}

/* —— 机柜 —— */
.dp-auth-stage__cabinet {
  width: 100%;
  max-width: 100%;
  margin-top: -2px;
  position: relative;
  z-index: 0;
}

.dp-auth-stage__cabinet-top {
  height: 8px;
  margin: 0 5%;
  border-radius: 4px 4px 0 0;
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--dp-auth-cabinet-wood) 70%, #5c4a3a),
    var(--dp-auth-cabinet-wood)
  );
  box-shadow: 0 -2px 0 color-mix(in srgb, #000 15%, transparent);
}

.dp-auth-stage__cabinet-face {
  display: grid;
  grid-template-columns: 1fr 0.72fr 0.35fr;
  gap: clamp(6px, 1.5vw, 10px);
  padding: clamp(8px, 2vw, 12px) clamp(12px, 3vw, 16px) clamp(10px, 2.5vw, 14px);
  background: linear-gradient(
    175deg,
    color-mix(in srgb, var(--dp-auth-cabinet-wood) 85%, #6d5848),
    color-mix(in srgb, var(--dp-auth-cabinet-wood) 95%, #2a221c)
  );
  border: 2px solid color-mix(in srgb, #000 35%, var(--dp-auth-cabinet-wood));
  border-radius: 0 0 6px 6px;
  box-shadow:
    inset 0 2px 0 color-mix(in srgb, #fff 12%, transparent),
    0 8px 20px color-mix(in srgb, var(--dp-text-primary, #2c3e50) 18%, transparent);
}

.dp-auth-stage__drawer {
  min-height: clamp(36px, 9vw, 48px);
  border-radius: 4px;
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--dp-auth-cabinet-wood) 60%, #8a7260),
    color-mix(in srgb, var(--dp-auth-cabinet-wood) 90%, #2e2520)
  );
  border: 1px solid color-mix(in srgb, #000 40%, transparent);
  box-shadow:
    inset 0 1px 0 color-mix(in srgb, #fff 10%, transparent),
    inset 0 -3px 6px rgba(0, 0, 0, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
}

.dp-auth-stage__drawer-knob {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: radial-gradient(circle at 30% 28%, #c9b89a, #6b5a48);
  border: 1px solid color-mix(in srgb, #000 45%, transparent);
  box-shadow: 0 2px 3px rgba(0, 0, 0, 0.35);
}

.dp-auth-stage__cabinet-slot {
  min-height: clamp(36px, 9vw, 48px);
  border-radius: 3px;
  background: #0a0c10;
  border: 2px inset color-mix(in srgb, #000 55%, #333);
  box-shadow: inset 0 4px 12px rgba(0, 0, 0, 0.65);
}

.dp-auth-stage__cabinet-legs {
  display: flex;
  justify-content: space-between;
  padding: 0 12% 0;
  margin-top: 2px;
}

.dp-auth-stage__cabinet-legs span {
  width: clamp(10px, 2.5vw, 14px);
  height: clamp(10px, 2.5vw, 14px);
  background: linear-gradient(180deg, #4a3d32, #2a221c);
  border-radius: 0 0 2px 2px;
}

@media (max-width: 380px) {
  .dp-auth-stage {
    --dp-auth-tv-border: 5px;
    --dp-auth-tv-shell-pad: 10px;
    --dp-auth-tv-bezel-pad: 9px;
    --dp-auth-tv-bezel-radius: 14px;
    --dp-auth-tv-shell-radius: 18px;
  }

  .dp-auth-stage__screen {
    min-height: clamp(244px, 61vw, 288px);
  }

  .dp-auth-stage__tv-body::before,
  .dp-auth-stage__tv-body::after {
    opacity: 0.32;
    height: 30%;
  }

  .dp-auth-stage__content {
    padding: 8px 10px;
  }

  .dp-auth-stage__title {
    font-size: 1.05rem;
    letter-spacing: 0.06em;
  }

  .dp-auth-stage__tab {
    padding: 6px 10px;
    font-size: 12px;
  }

  .dp-auth-stage__cabinet-face {
    grid-template-columns: 1fr 1fr;
    padding-left: 10px;
    padding-right: 10px;
  }

  .dp-auth-stage__cabinet-slot {
    grid-column: 1 / -1;
    min-height: 28px;
  }

  .dp-auth-stage__drawer {
    min-height: 32px;
  }
}
</style>

<!-- eco / reduced-motion：需命中 body -->
<style>
body[data-dp-fluidity='eco'] .dp-auth-stage__snow,
body[data-dp-fluidity='eco'] .dp-auth-stage__snow--active,
body[data-dp-fluidity='eco'] .dp-auth-stage__snow-noise,
body[data-dp-fluidity='eco'] .dp-auth-stage__snow-bars,
body[data-dp-fluidity='eco'] .dp-auth-stage__snow-bright,
body[data-dp-fluidity='eco'] .dp-auth-stage__scanlines {
  animation: none !important;
}

body[data-dp-fluidity='eco'] .dp-auth-stage__snow--active {
  opacity: 0.4;
  inset: 0;
  transform: none;
  background: color-mix(in srgb, var(--dp-text-muted, #909399) 55%, #1c1f24);
}

body[data-dp-fluidity='eco'] .dp-auth-stage__snow-noise,
body[data-dp-fluidity='eco'] .dp-auth-stage__snow-bars,
body[data-dp-fluidity='eco'] .dp-auth-stage__snow-bright {
  display: none;
}

body[data-dp-fluidity='eco'] .dp-auth-stage__scanlines {
  opacity: 0.16;
}

body[data-dp-fluidity='eco'] .dp-auth-stage__led--on {
  animation: none;
}

@media (prefers-reduced-motion: reduce) {
  .dp-auth-stage__snow,
  .dp-auth-stage__snow--active,
  .dp-auth-stage__snow-noise,
  .dp-auth-stage__snow-bars,
  .dp-auth-stage__snow-bright,
  .dp-auth-stage__scanlines,
  .dp-auth-stage__led--on,
  .dp-auth-stage__flash--pulse {
    animation: none !important;
  }

  .dp-auth-stage__snow--active {
    opacity: 0.38;
    inset: 0;
    transform: none;
    background: color-mix(in srgb, var(--dp-text-muted, #909399) 55%, #1c1f24);
  }

  .dp-auth-stage__snow-noise,
  .dp-auth-stage__snow-bars,
  .dp-auth-stage__snow-bright {
    display: none;
  }

  .dp-auth-stage__scanlines {
    opacity: 0.14;
  }

  .dp-auth-stage__content {
    transition-duration: 0.05s;
  }
}
</style>
