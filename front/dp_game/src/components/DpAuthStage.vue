<template>
  <div
    class="dp-auth-stage"
    :class="{
      'dp-auth-stage--booting': phase !== 'idle',
      'dp-auth-stage--transition': phase === 'transition',
      'dp-auth-stage--interactive': contentInteractive,
      'dp-auth-stage--rig-descending': rigDescending,
      'dp-auth-stage--rig-landed': rigDescended,
      'dp-auth-stage--auth-error-shake': authErrorShaking
    }"
  >
    <div class="dp-auth-stage__theme-bar dp-game-theme-row">
      <span class="dp-game-theme-row__label">界面主题</span>
      <dp-theme-picker
        :game-ui-theme="gameUiTheme"
        :theme-options="gameThemeOptions"
        @input-theme="$emit('input-theme', $event)"
      />
    </div>

    <!-- 长廊透视背板 -->
    <div class="dp-auth-stage__corridor" aria-hidden="true">
      <div class="dp-auth-stage__corridor-vp" />
      <div class="dp-auth-stage__corridor-ceiling" />
      <div class="dp-auth-stage__corridor-wall dp-auth-stage__corridor-wall--l" />
      <div class="dp-auth-stage__corridor-wall dp-auth-stage__corridor-wall--r" />
      <div class="dp-auth-stage__corridor-floor" />
      <div class="dp-auth-stage__corridor-horizon" />
    </div>

    <!-- 宽屏曲面 clip-path：左右直棱 + 顶凸底凹（objectBoundingBox） -->
    <svg class="dp-auth-stage__clip-defs" aria-hidden="true" width="0" height="0">
      <defs>
        <clipPath id="dp-auth-clip-frame" clipPathUnits="objectBoundingBox">
          <path d="M 0,0.04 Q 0.5,0 1,0.04 L 1,0.96 Q 0.5,1 0,0.96 L 0,0.04 Z" />
        </clipPath>
        <clipPath id="dp-auth-clip-bezel" clipPathUnits="objectBoundingBox">
          <path d="M 0,0.043 Q 0.5,0.003 1,0.043 L 1,0.957 Q 0.5,0.997 0,0.957 L 0,0.043 Z" />
        </clipPath>
        <clipPath id="dp-auth-clip-screen" clipPathUnits="objectBoundingBox">
          <path d="M 0,0.046 Q 0.5,0.006 1,0.046 L 1,0.954 Q 0.5,0.994 0,0.954 L 0,0.046 Z" />
        </clipPath>
        <clipPath id="dp-auth-clip-frame-lg" clipPathUnits="objectBoundingBox">
          <path d="M 0,0.05 Q 0.5,0 1,0.05 L 1,0.95 Q 0.5,1 0,0.95 L 0,0.05 Z" />
        </clipPath>
        <clipPath id="dp-auth-clip-bezel-lg" clipPathUnits="objectBoundingBox">
          <path d="M 0,0.053 Q 0.5,0.003 1,0.053 L 1,0.947 Q 0.5,0.997 0,0.947 L 0,0.053 Z" />
        </clipPath>
        <clipPath id="dp-auth-clip-screen-lg" clipPathUnits="objectBoundingBox">
          <path d="M 0,0.056 Q 0.5,0.006 1,0.056 L 1,0.944 Q 0.5,0.994 0,0.944 L 0,0.056 Z" />
        </clipPath>
      </defs>
    </svg>

    <!-- 线条风电脑：首次进入从顶部下降 -->
    <div
      class="dp-auth-stage__rig"
      :class="{
        'dp-auth-stage__rig--skip-motion': skipRigMotion
      }"
      aria-hidden="false"
    >
      <div class="dp-auth-stage__monitor" aria-hidden="true">
        <div class="dp-auth-stage__monitor-curve">
        <div class="dp-auth-stage__monitor-frame">
          <div class="dp-auth-stage__monitor-body">
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
              <div class="dp-auth-stage__curve-shade" aria-hidden="true" />

              <!-- 开机 / 转场闪白 -->
              <div
                class="dp-auth-stage__flash"
                :class="{ 'dp-auth-stage__flash--pulse': flashPulse }"
              />

              <!-- 认证失败：8bit 哭脸 + 文案 + 重试（z-index 5，低于 flash 6） -->
              <div
                v-if="showErrorFace && authError"
                class="dp-auth-stage__error-face"
                role="alert"
                aria-live="assertive"
              >
                <div class="dp-auth-stage__error-face-art" aria-hidden="true">
                  <!-- 16×16 像素矩阵：八形斜眼（无泪、无眉）+ 厚倒 U 嘴（放大后每格 ≥8px） -->
                  <svg
                    class="dp-auth-stage__error-pixel-face"
                    viewBox="0 0 16 16"
                    aria-hidden="true"
                    focusable="false"
                  >
                    <!-- 八形眼：脸上方偏中，自鼻侧向外下斜 \ /（各 3 阶 2 格粗线，禁止平横条） -->
                    <rect x="0" y="3" width="5" height="1" />
                    <!-- <rect x="3" y="4" width="2" height="1" /> -->
                    <!-- <rect x="2" y="5" width="3" height="1" /> -->
                    <rect x="2" y="5" width="1" height="1" />
                    <rect x="1" y="7" width="3" height="1" />
                    <rect x="11" y="3" width="16" height="1" />
                    <!-- <rect x="11" y="4" width="2" height="1" />
                    <rect x="12" y="5" width="3" height="1" /> -->
                    <rect x="13" y="5" width="1" height="1" />
                    <rect x="12" y="7" width="3" height="1" />
                    <!-- 厚倒 U 嘴：顶拱 + 左右竖边，开口朝下 -->
                    <rect x="4" y="10" width="8" height="1" />
                    <rect x="3" y="11" width="10" height="1" />
                    <rect x="2" y="12" width="2" height="1" />
                    <rect x="12" y="12" width="2" height="1" />
                    <rect x="2" y="13" width="2" height="1" />
                    <rect x="12" y="13" width="2" height="1" />
                  </svg>
                  <span class="dp-auth-stage__error-pixel-label">ERROR</span>
                </div>
                <p class="dp-auth-stage__error-message">{{ authError.message }}</p>
                <button
                  type="button"
                  class="dp-auth-stage__error-retry"
                  @click="clearAuthErrorAndRetry"
                >
                  重试
                </button>
              </div>

              <!-- 屏幕内容 -->
              <div
                class="dp-auth-stage__content"
                :class="{ 'dp-auth-stage__content--hidden': showErrorFace }"
                :aria-hidden="!contentInteractive || showErrorFace"
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
          </div>
        </div>
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

/** 电脑从顶部下降（仅首次 mount） */
const RIG_DESCENT_MS = 1000

/** 认证失败演出时序（ms） */
const AUTH_ERROR_TIMING = {
  shake: 420,
  flash: 280,
  faceDelay: 400,
  retryFlash: 120
}

const AUTH_ERROR_TIMING_ECO = {
  shake: 80,
  flash: 0,
  faceDelay: 60,
  retryFlash: 0
}

export default {
  name: 'DpAuthStage',
  provide() {
    return { dpAuthStage: this }
  },
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
      snowActive: false,
      flashPulse: false,
      screenOn: false,
      contentVisible: false,
      contentInteractive: false,
      bootDone: false,
      rigDescending: false,
      rigDescended: false,
      /** @type {{ message: string } | null} */
      authError: null,
      showErrorFace: false,
      authErrorShaking: false,
      timers: []
    }
  },
  computed: {
    ...mapState('dpGame', [
      'gameUiTheme',
      'gameThemeOptions',
      'ecoMode'
    ]),
    timing() {
      return this.ecoMode || this.prefersReducedMotion ? TIMING_ECO : TIMING
    },
    prefersReducedMotion() {
      if (typeof window === 'undefined' || !window.matchMedia) return false
      return window.matchMedia('(prefers-reduced-motion: reduce)').matches
    },
    skipRigMotion() {
      return this.ecoMode || this.prefersReducedMotion
    },
    rigDescentMs() {
      return this.skipRigMotion ? 0 : RIG_DESCENT_MS
    },
    authErrorTiming() {
      return this.ecoMode || this.prefersReducedMotion ? AUTH_ERROR_TIMING_ECO : AUTH_ERROR_TIMING
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
    this.startRigDescentThenBoot()
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
    startRigDescentThenBoot() {
      const ms = this.rigDescentMs
      if (ms <= 0) {
        this.rigDescended = true
        this.rigDescending = false
        this.runBootSequence()
        return
      }
      this.rigDescending = true
      this.rigDescended = false
      this.schedule(() => {
        this.rigDescending = false
        this.rigDescended = true
        this.runBootSequence()
      }, ms)
    },
    runBootSequence() {
      this.clearTimers()
      const t = this.timing
      this.phase = 'boot'
      this.authError = null
      this.showErrorFace = false
      this.authErrorShaking = false
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
    },
    /** 登录/注册失败：震颤 → 闪白 → 8bit 哭脸（子组件 inject dpAuthStage 调用） */
    showAuthError(message) {
      const text = message != null ? String(message).trim() : '操作失败，请重试'
      this.clearTimers()
      this.authError = { message: text || '操作失败，请重试' }
      this.showErrorFace = false
      this.authErrorShaking = true
      this.contentInteractive = false

      const t = this.authErrorTiming
      const flashAt = t.shake > 0 ? Math.round(t.shake * 0.45) : 0

      if (t.flash > 0) {
        this.schedule(() => {
          this.flashPulse = true
          this.schedule(() => {
            this.flashPulse = false
          }, t.flash)
        }, flashAt)
      }

      this.schedule(() => {
        this.authErrorShaking = false
        this.showErrorFace = true
      }, t.faceDelay)
    },
    /** 点「重试」：短闪 → 花屏开机 → 回到当前 Tab 表单 */
    clearAuthErrorAndRetry() {
      this.authError = null
      this.showErrorFace = false
      this.authErrorShaking = false
      this.contentInteractive = false

      const t = this.authErrorTiming
      if (t.retryFlash > 0) {
        this.flashPulse = true
        this.schedule(() => {
          this.flashPulse = false
          this.runBootSequence()
        }, t.retryFlash)
      } else {
        this.runBootSequence()
      }
    }
  }
}
</script>

<style scoped>
.dp-auth-stage {
  position: relative;
  width: 100%;
  max-width: min(100%, 42rem);
  margin: 0 auto;
  min-height: clamp(420px, 72vh, 640px);
  isolation: isolate;
}

.dp-auth-stage__theme-bar {
  justify-content: flex-end;
  margin-bottom: clamp(10px, 2.5vw, 16px);
  position: relative;
  z-index: 12;
}

/* —— 长廊透视背板 —— */
.dp-auth-stage__corridor {
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
  background: var(--dp-auth-corridor-bg);
}

.dp-auth-stage__corridor-vp {
  position: absolute;
  left: 50%;
  top: 28%;
  width: clamp(80px, 18vw, 140px);
  height: clamp(80px, 18vw, 140px);
  transform: translate(-50%, -50%);
  border-radius: 50%;
  background: radial-gradient(
    circle,
    color-mix(in srgb, var(--dp-auth-corridor-vp-glow) 85%, transparent) 0%,
    transparent 72%
  );
  opacity: 0.55;
}

.dp-auth-stage__corridor-ceiling {
  position: absolute;
  inset: 0 0 52% 0;
  background:
    linear-gradient(180deg, var(--dp-auth-corridor-ceiling-tint) 0%, transparent 88%),
    repeating-linear-gradient(
      90deg,
      transparent 0,
      transparent 47px,
      color-mix(in srgb, var(--dp-auth-corridor-line) 55%, transparent) 47px,
      color-mix(in srgb, var(--dp-auth-corridor-line) 55%, transparent) 48px
    );
  opacity: 0.7;
}

.dp-auth-stage__corridor-wall {
  position: absolute;
  top: 18%;
  bottom: 28%;
  width: 38%;
  border: 1px solid color-mix(in srgb, var(--dp-auth-corridor-line) 70%, transparent);
  background: linear-gradient(
    105deg,
    color-mix(in srgb, var(--dp-auth-corridor-bg) 92%, var(--dp-auth-corridor-wall-tint)),
    transparent 75%
  );
}

.dp-auth-stage__corridor-wall--l {
  left: 0;
  transform: perspective(520px) rotateY(14deg);
  transform-origin: left center;
  border-left: none;
}

.dp-auth-stage__corridor-wall--r {
  right: 0;
  transform: perspective(520px) rotateY(-14deg);
  transform-origin: right center;
  border-right: none;
  background: linear-gradient(
    -105deg,
    color-mix(in srgb, var(--dp-auth-corridor-bg) 92%, var(--dp-auth-corridor-wall-tint)),
    transparent 75%
  );
}

.dp-auth-stage__corridor-horizon {
  position: absolute;
  left: 8%;
  right: 8%;
  top: 42%;
  height: 1px;
  background: color-mix(in srgb, var(--dp-auth-phosphor) 22%, var(--dp-auth-corridor-line));
  box-shadow: 0 0 12px var(--dp-auth-screen-glow);
  opacity: 0.65;
}

.dp-auth-stage__corridor-floor {
  position: absolute;
  left: -15%;
  right: -15%;
  bottom: 0;
  height: 58%;
  transform-origin: 50% 100%;
  transform: perspective(340px) rotateX(62deg);
  background:
    linear-gradient(
      180deg,
      transparent 0%,
      color-mix(in srgb, var(--dp-auth-corridor-bg) 40%, var(--dp-auth-corridor-floor-tint)) 18%,
      var(--dp-auth-corridor-bg) 100%
    ),
    repeating-linear-gradient(
      90deg,
      transparent 0,
      transparent 38px,
      color-mix(in srgb, var(--dp-auth-corridor-line) 80%, transparent) 38px,
      color-mix(in srgb, var(--dp-auth-corridor-line) 80%, transparent) 39px
    ),
    repeating-linear-gradient(
      0deg,
      transparent 0,
      transparent 22px,
      color-mix(in srgb, var(--dp-auth-corridor-line) 45%, transparent) 22px,
      color-mix(in srgb, var(--dp-auth-corridor-line) 45%, transparent) 23px
    );
  mask-image: linear-gradient(to top, #000 55%, transparent 100%);
}

/* —— 电脑 rig + 下降 —— */
.dp-auth-stage__rig {
  position: relative;
  z-index: 4;
  width: 100%;
  display: flex;
  justify-content: center;
  padding-top: clamp(8px, 2vw, 16px);
  transform: translateY(calc(-105vh - 12%)) scale(0.52);
  opacity: 0.35;
  will-change: transform, opacity;
}

.dp-auth-stage__rig--skip-motion,
.dp-auth-stage--rig-landed .dp-auth-stage__rig {
  transform: translateY(0) scale(1);
  opacity: 1;
}

.dp-auth-stage--rig-descending:not(.dp-auth-stage--rig-landed) .dp-auth-stage__rig:not(.dp-auth-stage__rig--skip-motion) {
  animation: dp-auth-rig-descend 1s cubic-bezier(0.22, 1, 0.36, 1) forwards;
}

@keyframes dp-auth-rig-descend {
  0% {
    transform: translateY(calc(-105vh - 12%)) scale(0.52);
    opacity: 0.35;
  }
  72% {
    transform: translateY(2%) scale(1.02);
    opacity: 1;
  }
  100% {
    transform: translateY(0) scale(1);
    opacity: 1;
  }
}

/* —— 线条风显示器 —— */
.dp-auth-stage__monitor {
  width: 100%;
  max-width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  filter: drop-shadow(
    0 18px 36px color-mix(in srgb, #000 42%, transparent)
  );
}

.dp-auth-stage__clip-defs {
  position: absolute;
  width: 0;
  height: 0;
  overflow: hidden;
  pointer-events: none;
}

/* 顶凸底凹宽屏曲面：以 clip 为准，极小 rotateX 仅作纵深感 */
.dp-auth-stage__monitor-curve {
  width: 100%;
  perspective: 1400px;
  perspective-origin: 50% 42%;
  transform: rotateX(1deg);
  transform-origin: 50% 52%;
}

.dp-auth-stage__monitor-frame {
  position: relative;
  width: 100%;
}

.dp-auth-stage__monitor-body {
  position: relative;
  width: 100%;
  border: 2px solid var(--dp-auth-bezel-stroke);
  padding: clamp(8px, 2vw, 12px);
  background: var(--dp-auth-rig-fill);
  clip-path: url(#dp-auth-clip-frame);
  overflow: hidden;
  box-shadow:
    0 0 0 1px var(--dp-auth-screen-glow),
    inset 0 0 24px color-mix(in srgb, var(--dp-auth-phosphor) 4%, transparent),
    inset 0 -10px 22px -14px color-mix(in srgb, #fff 8%, transparent),
    inset 0 28px 44px -18px color-mix(in srgb, #000 58%, transparent);
}

.dp-auth-stage__bezel {
  position: relative;
  border: 2px solid color-mix(in srgb, var(--dp-auth-bezel-stroke) 90%, var(--dp-auth-phosphor));
  padding: clamp(6px, 1.5vw, 10px);
  background: var(--dp-auth-bezel-inner);
  clip-path: url(#dp-auth-clip-bezel);
  overflow: hidden;
  box-shadow:
    inset 0 0 12px color-mix(in srgb, #000 75%, transparent),
    inset 0 -6px 18px -10px color-mix(in srgb, #fff 5%, transparent),
    inset 0 22px 36px -12px color-mix(in srgb, #000 48%, transparent);
}

.dp-auth-stage__bezel::before {
  content: '';
  position: absolute;
  inset: clamp(4px, 1vw, 8px);
  pointer-events: none;
  box-shadow:
    inset 0 0 0 1px color-mix(in srgb, var(--dp-auth-phosphor-dim) 18%, transparent);
  z-index: 1;
}

.dp-auth-stage__bezel::after {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 2;
  background: linear-gradient(
    180deg,
    rgba(255, 255, 255, 0.1) 0%,
    transparent 14%,
    transparent 78%,
    rgba(0, 0, 0, 0.22) 92%,
    rgba(0, 0, 0, 0.42) 100%
  );
}

.dp-auth-stage__screen {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  aspect-ratio: 16 / 9;
  min-height: clamp(256px, 48vw, 320px);
  overflow: hidden;
  background: var(--dp-auth-screen-off);
  clip-path: url(#dp-auth-clip-screen);
  box-shadow:
    inset 0 0 0 1px color-mix(in srgb, var(--dp-auth-phosphor-dim) 35%, #000),
    inset 0 -8px 20px -10px color-mix(in srgb, #fff 6%, transparent),
    inset 0 24px 38px -8px color-mix(in srgb, #000 48%, transparent);
  transition:
    background-color 0.35s ease,
    box-shadow 0.35s ease;
  z-index: 0;
}

.dp-auth-stage__screen--on {
  background: var(--dp-auth-screen-bg);
  box-shadow:
    inset 0 0 32px var(--dp-auth-screen-glow),
    inset 0 0 56px color-mix(in srgb, var(--dp-auth-screen-glow) 48%, transparent),
    inset 0 0 0 1px color-mix(in srgb, var(--dp-auth-phosphor-dim) 52%, #000),
    inset 0 -6px 18px -10px color-mix(in srgb, #fff 5%, transparent),
    inset 0 22px 42px -8px color-mix(in srgb, #000 38%, transparent);
}

/* CRT 花屏容器：整体颤抖，子层分别负责噪点 / 横纹 / 亮带 */
.dp-auth-stage__snow {
  position: absolute;
  inset: -6%;
  z-index: 5;
  opacity: 0;
  pointer-events: none;
  overflow: hidden;
  transition: opacity 0.12s ease;
  background: color-mix(in srgb, var(--dp-auth-snow-base) 88%, var(--dp-auth-snow-base-alt));
}

.dp-auth-stage__snow--active {
  opacity: 0.92;
  /* 仅纵向抖动 + 横向不外扩，避免 -6% inset 配合横移产生 CRT 凸泡掐边 */
  inset: -5% 0;
  animation: dp-auth-snow-jitter-y 0.07s steps(2) infinite;
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
.dp-auth-stage__snow--active .dp-auth-stage__snow-bright {
  inset: auto 0 -35%;
}

.dp-auth-stage__snow-bright {
  inset: auto 0 -35%;
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

/* 花屏专用：仅垂直微抖，保留 dp-auth-crt-jitter 契约供其它引用 */
@keyframes dp-auth-snow-jitter-y {
  0% {
    transform: translateY(0);
  }
  25% {
    transform: translateY(0.5px);
  }
  50% {
    transform: translateY(-0.5px);
  }
  75% {
    transform: translateY(0.35px);
  }
  100% {
    transform: translateY(-0.35px);
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
    rgba(0, 0, 0, 0.1) 2px,
    rgba(0, 0, 0, 0.1) 3px
  );
  background-size: 100% 3px;
  opacity: 0.28;
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
    ellipse 88% 76% at 50% 50%,
    transparent 36%,
    rgba(0, 0, 0, var(--dp-auth-vignette-strength, 0.26)) 100%
  );
}

/* 顶凸底凹纵深感：顶侧略亮迎光、底侧略深；左右不掐边 */
.dp-auth-stage__curve-shade {
  position: absolute;
  inset: 0;
  z-index: 3;
  pointer-events: none;
  background:
    linear-gradient(
      180deg,
      rgba(255, 255, 255, 0.1) 0%,
      rgba(255, 255, 255, 0.04) 6%,
      transparent 20%,
      transparent 72%,
      rgba(0, 0, 0, 0.1) 88%,
      rgba(0, 0, 0, 0.32) 96%,
      rgba(0, 0, 0, 0.52) 100%
    ),
    radial-gradient(
      ellipse 100% 82% at 50% 36%,
      rgba(255, 255, 255, 0.06) 0%,
      transparent 40%,
      rgba(0, 0, 0, 0.12) 100%
    );
  opacity: 0.92;
}

/*
 * 花屏 / 灭屏：与顶凸底凹一致（顶亮底深），禁止左右 pincushion。
 * snow--active 覆盖 Tab 转场；:not(--on) 覆盖 boot 灭屏期。
 */
.dp-auth-stage__snow--active ~ .dp-auth-stage__curve-shade,
.dp-auth-stage__screen:not(.dp-auth-stage__screen--on) .dp-auth-stage__curve-shade {
  background:
    linear-gradient(
      180deg,
      rgba(255, 255, 255, 0.14) 0%,
      rgba(255, 255, 255, 0.06) 6%,
      transparent 20%,
      transparent 72%,
      rgba(0, 0, 0, 0.12) 88%,
      rgba(0, 0, 0, 0.36) 96%,
      rgba(0, 0, 0, 0.55) 100%
    ),
    radial-gradient(
      ellipse 100% 82% at 50% 38%,
      rgba(255, 255, 255, 0.05) 0%,
      transparent 38%,
      rgba(0, 0, 0, 0.2) 100%
    );
  opacity: 0.86;
}

.dp-auth-stage__snow--active ~ .dp-auth-stage__vignette,
.dp-auth-stage__screen:not(.dp-auth-stage__screen--on) .dp-auth-stage__vignette {
  background: radial-gradient(
    ellipse 92% 78% at 50% 40%,
    rgba(255, 255, 255, 0.06) 0%,
    transparent 38%,
    rgba(0, 0, 0, calc(var(--dp-auth-vignette-strength, 0.26) * 0.65)) 100%
  );
}

.dp-auth-stage__snow--active ~ .dp-auth-stage__scanlines,
.dp-auth-stage__screen:not(.dp-auth-stage__screen--on) .dp-auth-stage__scanlines {
  -webkit-mask-image: linear-gradient(
    180deg,
    rgba(0, 0, 0, 0.94) 0%,
    rgba(0, 0, 0, 0.72) 10%,
    rgba(0, 0, 0, 0.42) 50%,
    rgba(0, 0, 0, 0.72) 90%,
    rgba(0, 0, 0, 0.94) 100%
  );
  mask-image: linear-gradient(
    180deg,
    rgba(0, 0, 0, 0.94) 0%,
    rgba(0, 0, 0, 0.72) 10%,
    rgba(0, 0, 0, 0.42) 50%,
    rgba(0, 0, 0, 0.72) 90%,
    rgba(0, 0, 0, 0.94) 100%
  );
}

.dp-auth-stage__screen:not(.dp-auth-stage__screen--on) {
  box-shadow:
    inset 0 0 0 1px color-mix(in srgb, var(--dp-auth-phosphor-dim) 35%, #000),
    inset 0 -6px 16px -12px color-mix(in srgb, #fff 5%, transparent),
    inset 0 18px 26px -14px color-mix(in srgb, #000 34%, transparent);
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
    background: var(--dp-auth-flash-tint);
  }
  22% {
    opacity: 1;
    background: color-mix(in srgb, var(--dp-auth-phosphor) 28%, var(--dp-auth-flash-tint));
  }
  55% {
    opacity: 0.7;
    background: color-mix(in srgb, var(--dp-auth-phosphor-dim) 35%, var(--dp-auth-screen-off));
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
  align-items: center;
  justify-content: center;
  flex: 0 1 auto;
  min-height: 0;
  max-height: 100%;
  width: 100%;
  margin: auto 0;
  padding: clamp(6px, 1.5vw, 10px) clamp(10px, 2.5vw, 14px);
  box-sizing: border-box;
  overflow-x: hidden;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
  scrollbar-color: color-mix(in srgb, var(--dp-auth-phosphor) 35%, transparent) transparent;
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
  background: color-mix(in srgb, var(--dp-auth-phosphor) 40%, var(--dp-auth-phosphor-dim));
}

.dp-auth-stage--interactive .dp-auth-stage__content {
  opacity: 1;
  transform: scale(1);
  pointer-events: auto;
}

.dp-auth-stage__content--hidden {
  visibility: hidden;
  pointer-events: none;
}

/* —— 认证失败：整机震颤 —— */
.dp-auth-stage--auth-error-shake .dp-auth-stage__monitor {
  animation: dp-auth-error-shake 0.42s ease-in-out;
}

@keyframes dp-auth-error-shake {
  0%,
  100% {
    transform: translate(0, 0);
  }
  12% {
    transform: translate(-5px, 2px);
  }
  24% {
    transform: translate(5px, -2px);
  }
  36% {
    transform: translate(-4px, -1px);
  }
  48% {
    transform: translate(4px, 1px);
  }
  60% {
    transform: translate(-3px, 2px);
  }
  72% {
    transform: translate(3px, -1px);
  }
  84% {
    transform: translate(-2px, 0);
  }
}

/* —— 认证失败：8bit 哭脸层（z-index 5，低于 flash 6） —— */
.dp-auth-stage__error-face {
  position: absolute;
  inset: 0;
  z-index: 5;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: clamp(10px, 2.5vw, 14px);
  padding: clamp(12px, 3vw, 20px) clamp(14px, 3.5vw, 22px);
  box-sizing: border-box;
  pointer-events: auto;
  background: color-mix(in srgb, var(--dp-auth-screen-bg) 92%, #000);
}

.dp-auth-stage__error-face-art {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: clamp(6px, 1.6vw, 10px);
  flex-shrink: 0;
}

.dp-auth-stage__error-pixel-face {
  display: block;
  width: clamp(128px, 32%, 224px);
  height: auto;
  aspect-ratio: 1;
  flex-shrink: 0;
  color: var(--dp-auth-phosphor);
  fill: currentColor;
  image-rendering: pixelated;
  image-rendering: crisp-edges;
  filter:
    drop-shadow(0 0 2px var(--dp-auth-phosphor))
    drop-shadow(0 0 10px var(--dp-auth-screen-glow))
    drop-shadow(0 0 22px color-mix(in srgb, var(--dp-auth-phosphor) 52%, transparent));
}

.dp-auth-stage__error-pixel-label {
  font-family: ui-monospace, 'Cascadia Code', 'Consolas', monospace;
  font-size: clamp(11px, 2.8vw, 14px);
  font-weight: 700;
  letter-spacing: 0.38em;
  text-indent: 0.38em;
  line-height: 1;
  color: var(--dp-auth-phosphor);
  text-shadow:
    0 0 6px var(--dp-auth-screen-glow),
    0 0 14px color-mix(in srgb, var(--dp-auth-phosphor) 45%, transparent);
  opacity: 0.92;
}

.dp-auth-stage__error-message {
  margin: 0;
  max-width: min(100%, 22rem);
  text-align: center;
  font-family: ui-monospace, 'Cascadia Code', 'Consolas', monospace;
  font-size: clamp(13px, 3.6vw, 15px);
  line-height: 1.45;
  letter-spacing: 0.04em;
  color: var(--dp-auth-phosphor);
  text-shadow: var(--dp-auth-text-shadow);
  word-break: break-word;
}

.dp-auth-stage__error-retry {
  margin-top: clamp(4px, 1vw, 8px);
  padding: clamp(8px, 2vw, 10px) clamp(20px, 5vw, 28px);
  font-family: ui-monospace, 'Cascadia Code', 'Consolas', monospace;
  font-size: clamp(13px, 3.6vw, 15px);
  font-weight: 600;
  letter-spacing: 0.08em;
  color: var(--dp-auth-phosphor);
  background: color-mix(in srgb, var(--dp-auth-phosphor) 22%, var(--dp-auth-screen-bg));
  border: 1px solid var(--dp-auth-phosphor);
  border-radius: 4px;
  cursor: pointer;
  text-shadow: var(--dp-auth-text-shadow);
  box-shadow:
    0 0 0 1px color-mix(in srgb, var(--dp-auth-phosphor) 42%, transparent),
    0 0 12px var(--dp-auth-screen-glow);
  transition:
    background 0.15s ease,
    box-shadow 0.15s ease;
}

@media (hover: hover) and (pointer: fine) {
  .dp-auth-stage__error-retry:hover {
    background: color-mix(in srgb, var(--dp-auth-phosphor) 32%, var(--dp-auth-screen-bg));
    box-shadow: 0 0 16px color-mix(in srgb, var(--dp-auth-phosphor) 28%, transparent);
  }
}

.dp-auth-stage__title {
  flex-shrink: 0;
  width: 100%;
  text-align: center;
  margin: 0 0 clamp(6px, 1.4vw, 10px);
  font-size: clamp(1.2rem, 4.4vw, 1.55rem);
  font-weight: 700;
  letter-spacing: 0.12em;
  line-height: 1.2;
  color: var(--dp-auth-phosphor);
  text-shadow: var(--dp-auth-text-shadow-title, var(--dp-auth-text-shadow));
  font-family: ui-monospace, 'Cascadia Code', 'Consolas', monospace;
}

.dp-auth-stage__tabs {
  display: flex;
  flex-shrink: 0;
  justify-content: center;
  width: 100%;
  gap: clamp(8px, 2vw, 12px);
  margin-bottom: clamp(8px, 2vw, 12px);
}

.dp-auth-stage__tab {
  flex: 1 1 auto;
  max-width: 9.5rem;
  padding: clamp(8px, 2vw, 10px) clamp(12px, 3vw, 16px);
  font-size: clamp(13px, 3.6vw, 15px);
  font-weight: 600;
  font-family: ui-monospace, 'Cascadia Code', 'Consolas', monospace;
  border-radius: 4px;
  border: 1px solid color-mix(in srgb, var(--dp-auth-phosphor-dim) 72%, transparent);
  background: color-mix(in srgb, var(--dp-auth-screen-bg) 88%, #000);
  color: var(--dp-auth-phosphor-dim);
  cursor: pointer;
  transition:
    background 0.15s ease,
    color 0.15s ease,
    box-shadow 0.15s ease;
}

.dp-auth-stage__tab--active {
  background: color-mix(in srgb, var(--dp-auth-phosphor) 24%, var(--dp-auth-screen-bg));
  color: var(--dp-auth-phosphor);
  border-color: var(--dp-auth-phosphor);
  box-shadow:
    0 0 0 1px color-mix(in srgb, var(--dp-auth-phosphor) 42%, transparent),
    0 0 16px var(--dp-auth-screen-glow),
    inset 0 0 10px color-mix(in srgb, var(--dp-auth-screen-glow) 55%, transparent);
}

.dp-auth-stage__tab:disabled {
  cursor: wait;
  opacity: 0.65;
}

.dp-auth-stage__form-panel {
  flex: 0 1 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-width: 0;
  overflow-x: hidden;
}

/* 桌面大屏：舞台加宽、显示器占比更大、曲面更明显 */
@media (min-width: 1280px) {
  .dp-auth-stage {
    max-width: min(100%, 58rem);
    min-height: clamp(520px, 80vh, 860px);
  }

  .dp-auth-stage__monitor {
    width: min(100%, 85%);
    max-width: 52rem;
  }

  .dp-auth-stage__monitor-curve {
    transform: rotateX(1.5deg);
  }

  .dp-auth-stage__monitor-body {
    clip-path: url(#dp-auth-clip-frame-lg);
    padding: clamp(12px, 1.4vw, 18px);
    box-shadow:
      0 0 0 1px var(--dp-auth-screen-glow),
      inset 0 0 32px color-mix(in srgb, var(--dp-auth-phosphor) 5%, transparent),
      inset 0 -12px 26px -14px color-mix(in srgb, #fff 9%, transparent),
      inset 0 32px 56px -14px color-mix(in srgb, #000 62%, transparent),
      0 14px 48px color-mix(in srgb, #000 28%, transparent);
  }

  .dp-auth-stage__bezel {
    clip-path: url(#dp-auth-clip-bezel-lg);
    padding: clamp(10px, 1.1vw, 14px);
    box-shadow:
      inset 0 0 16px color-mix(in srgb, #000 78%, transparent),
      inset 0 -8px 20px -8px color-mix(in srgb, #fff 6%, transparent),
      inset 0 26px 46px -8px color-mix(in srgb, #000 52%, transparent);
  }

  .dp-auth-stage__screen {
    clip-path: url(#dp-auth-clip-screen-lg);
    min-height: clamp(352px, 37vw, 520px);
    box-shadow:
      inset 0 0 0 1px color-mix(in srgb, var(--dp-auth-phosphor-dim) 35%, #000),
      inset 0 -8px 22px -10px color-mix(in srgb, #fff 7%, transparent),
      inset 0 28px 52px -4px color-mix(in srgb, #000 50%, transparent);
  }

  .dp-auth-stage__screen--on {
    box-shadow:
      inset 0 0 40px var(--dp-auth-screen-glow),
      inset 0 0 68px color-mix(in srgb, var(--dp-auth-screen-glow) 48%, transparent),
      inset 0 0 0 1px color-mix(in srgb, var(--dp-auth-phosphor-dim) 52%, #000),
      inset 0 -8px 20px -10px color-mix(in srgb, #fff 6%, transparent),
      inset 0 26px 52px -4px color-mix(in srgb, #000 40%, transparent);
  }

  .dp-auth-stage__snow {
    inset: -7%;
  }

  .dp-auth-stage__snow--active {
    inset: -6% 0;
  }

  .dp-auth-stage__curve-shade {
    opacity: 1;
    background:
      linear-gradient(
        180deg,
        rgba(255, 255, 255, 0.12) 0%,
        rgba(255, 255, 255, 0.05) 5%,
        transparent 18%,
        transparent 70%,
        rgba(0, 0, 0, 0.12) 86%,
        rgba(0, 0, 0, 0.38) 95%,
        rgba(0, 0, 0, 0.58) 100%
      ),
      radial-gradient(
        ellipse 100% 80% at 50% 34%,
        rgba(255, 255, 255, 0.08) 0%,
        transparent 36%,
        rgba(0, 0, 0, 0.16) 100%
      );
  }

  .dp-auth-stage__snow--active ~ .dp-auth-stage__curve-shade,
  .dp-auth-stage__screen:not(.dp-auth-stage__screen--on) .dp-auth-stage__curve-shade {
    background:
      linear-gradient(
        180deg,
        rgba(255, 255, 255, 0.16) 0%,
        rgba(255, 255, 255, 0.07) 5%,
        transparent 18%,
        transparent 70%,
        rgba(0, 0, 0, 0.14) 86%,
        rgba(0, 0, 0, 0.42) 95%,
        rgba(0, 0, 0, 0.6) 100%
      ),
      radial-gradient(
        ellipse 100% 80% at 50% 36%,
        rgba(255, 255, 255, 0.06) 0%,
        transparent 34%,
        rgba(0, 0, 0, 0.22) 100%
      );
    opacity: 0.9;
  }

  .dp-auth-stage__screen:not(.dp-auth-stage__screen--on) {
    box-shadow:
      inset 0 0 0 1px color-mix(in srgb, var(--dp-auth-phosphor-dim) 35%, #000),
      inset 0 -8px 18px -12px color-mix(in srgb, #fff 6%, transparent),
      inset 0 22px 34px -12px color-mix(in srgb, #000 32%, transparent);
  }

  .dp-auth-stage__vignette {
    background: radial-gradient(
      ellipse 84% 74% at 50% 50%,
      transparent 32%,
      rgba(0, 0, 0, var(--dp-auth-vignette-strength, 0.26)) 100%
    );
  }

  .dp-auth-stage__content {
    padding: clamp(10px, 1.2vw, 16px) clamp(18px, 2.2vw, 26px);
  }

  .dp-auth-stage__title {
    margin-bottom: clamp(8px, 1vw, 12px);
    font-size: clamp(1.35rem, 1.9vw, 1.75rem);
  }

  .dp-auth-stage__tabs {
    gap: 12px;
    margin-bottom: 14px;
  }

  .dp-auth-stage__tab {
    max-width: 11rem;
    padding: 10px 18px;
    font-size: 15px;
  }
}

@media (max-width: 380px) {
  .dp-auth-stage {
    max-width: 100%;
    min-height: 380px;
  }

  .dp-auth-stage__screen {
    min-height: clamp(244px, 61vw, 288px);
  }

  .dp-auth-stage__content {
    padding: 8px 10px;
  }

  .dp-auth-stage__title {
    font-size: 1.05rem;
    letter-spacing: 0.08em;
  }

  .dp-auth-stage__tab {
    padding: 6px 10px;
    font-size: 12px;
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
body[data-dp-fluidity='eco'] .dp-auth-stage__scanlines,
body[data-dp-fluidity='eco'] .dp-auth-stage__rig {
  animation: none !important;
}

body[data-dp-fluidity='eco'] .dp-auth-stage__snow--active {
  opacity: 0.4;
  inset: 0;
  transform: none;
  background: color-mix(in srgb, var(--dp-text-muted) 55%, var(--dp-auth-snow-base));
}

body[data-dp-fluidity='eco'] .dp-auth-stage__snow-noise,
body[data-dp-fluidity='eco'] .dp-auth-stage__snow-bars,
body[data-dp-fluidity='eco'] .dp-auth-stage__snow-bright {
  display: none;
}

body[data-dp-fluidity='eco'] .dp-auth-stage__scanlines {
  opacity: 0.16;
}

body[data-dp-fluidity='eco'] .dp-auth-stage__rig {
  transform: translateY(0) scale(1);
  opacity: 1;
}

body[data-dp-fluidity='eco'] .dp-auth-stage--auth-error-shake .dp-auth-stage__monitor {
  animation-duration: 0.08s;
}

@media (prefers-reduced-motion: reduce) {
  .dp-auth-stage__snow,
  .dp-auth-stage__snow--active,
  .dp-auth-stage__snow-noise,
  .dp-auth-stage__snow-bars,
  .dp-auth-stage__snow-bright,
  .dp-auth-stage__scanlines,
  .dp-auth-stage__flash--pulse,
  .dp-auth-stage__rig {
    animation: none !important;
  }

  .dp-auth-stage__rig {
    transform: translateY(0) scale(1);
    opacity: 1;
  }

  .dp-auth-stage__snow--active {
    opacity: 0.38;
    inset: 0;
    transform: none;
    background: color-mix(in srgb, var(--dp-text-muted) 55%, var(--dp-auth-snow-base));
  }

  .dp-auth-stage__snow-noise,
  .dp-auth-stage__snow-bars,
  .dp-auth-stage__snow-bright {
    display: none;
  }

  .dp-auth-stage__scanlines {
    opacity: 0.12;
  }

  .dp-auth-stage__content {
    transition-duration: 0.05s;
  }

  .dp-auth-stage--auth-error-shake .dp-auth-stage__monitor {
    animation-duration: 0.08s;
  }
}
</style>
