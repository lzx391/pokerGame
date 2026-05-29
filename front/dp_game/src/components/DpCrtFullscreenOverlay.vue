<template>
  <transition name="dp-crt-fs-root">
    <div
      v-if="visible"
      class="dp-crt-fs"
      :class="{
        'dp-crt-fs--snow': phase === 'snow',
        'dp-crt-fs--fade': phase === 'fade-out',
        'dp-crt-fs--lite': !fullSnow
      }"
      aria-hidden="true"
    >
      <div class="dp-crt-fs__snow" :class="{ 'dp-crt-fs__snow--active': snowActive }">
        <span class="dp-crt-fs__snow-noise" />
        <span class="dp-crt-fs__snow-bars" />
        <span class="dp-crt-fs__snow-bright" />
      </div>
      <div class="dp-crt-fs__scanlines" />
      <div class="dp-crt-fs__vignette" />
      <div
        class="dp-crt-fs__flash"
        :class="{ 'dp-crt-fs__flash--pulse': flashPulse }"
      />
    </div>
  </transition>
</template>

<script>
export default {
  name: 'DpCrtFullscreenOverlay',
  data() {
    return {
      visible: false,
      phase: 'idle',
      snowActive: false,
      flashPulse: false,
      fullSnow: true,
      timers: []
    }
  },
  beforeDestroy() {
    this.clearTimers()
  },
  methods: {
    clearTimers() {
      this.timers.forEach(function (id) {
        clearTimeout(id)
      })
      this.timers = []
    },
    schedule(fn, ms) {
      var id = setTimeout(fn, ms)
      this.timers.push(id)
      return id
    },
    /**
     * @param {{ navigateAt: number, fadeDuration: number, total: number, fullSnow: boolean }} timing
     * @param {() => void} onNavigate
     */
    play(timing, onNavigate) {
      this.clearTimers()
      this.fullSnow = timing.fullSnow
      this.visible = true
      this.phase = 'snow'
      this.snowActive = true
      this.flashPulse = false

      if (!timing.fullSnow) {
        this.schedule(() => {
          if (typeof onNavigate === 'function') onNavigate()
        }, timing.navigateAt)
        this.schedule(() => {
          this.phase = 'fade-out'
          this.snowActive = false
        }, timing.navigateAt)
        this.schedule(() => {
          this.visible = false
          this.phase = 'idle'
        }, timing.total)
        return
      }

      this.schedule(() => {
        this.flashPulse = true
        this.schedule(() => {
          this.flashPulse = false
        }, Math.min(120, timing.fadeDuration))
      }, Math.max(0, timing.navigateAt - 80))

      this.schedule(() => {
        if (typeof onNavigate === 'function') onNavigate()
        this.phase = 'fade-out'
        this.snowActive = false
      }, timing.navigateAt)

      this.schedule(() => {
        this.visible = false
        this.phase = 'idle'
        this.snowActive = false
      }, timing.total)
    }
  }
}
</script>

<style scoped>
.dp-crt-fs {
  position: fixed;
  inset: 0;
  z-index: 10050;
  pointer-events: none;
  overflow: hidden;
  background: #0a0c10;
}

.dp-crt-fs-root-enter-active,
.dp-crt-fs-root-leave-active {
  transition: opacity 0.12s ease;
}

.dp-crt-fs-root-enter,
.dp-crt-fs-root-leave-to {
  opacity: 0;
}

.dp-crt-fs--fade {
  animation: dp-crt-fs-fade-out 0.28s ease forwards;
}

.dp-crt-fs--lite.dp-crt-fs--fade {
  animation: dp-crt-fs-fade-out 0.3s ease forwards;
}

@keyframes dp-crt-fs-fade-out {
  0% {
    opacity: 1;
  }
  100% {
    opacity: 0;
  }
}

.dp-crt-fs__snow {
  position: absolute;
  inset: -4%;
  z-index: 2;
  opacity: 0;
  pointer-events: none;
  overflow: hidden;
  transition: opacity 0.1s ease;
  background: color-mix(in srgb, #1a1e24 88%, #3a4048);
}

.dp-crt-fs__snow--active {
  opacity: 0.94;
  animation: dp-crt-fs-jitter 0.07s steps(2) infinite;
}

.dp-crt-fs--lite .dp-crt-fs__snow--active {
  opacity: 0.42;
  inset: 0;
  transform: none;
  animation: none;
  background: color-mix(in srgb, #909399 48%, #1c1f24);
}

.dp-crt-fs__snow-noise,
.dp-crt-fs__snow-bars,
.dp-crt-fs__snow-bright {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.dp-crt-fs--lite .dp-crt-fs__snow-noise,
.dp-crt-fs--lite .dp-crt-fs__snow-bars,
.dp-crt-fs--lite .dp-crt-fs__snow-bright {
  display: none;
}

.dp-crt-fs__snow-noise {
  opacity: 0.88;
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
  background-size: 3px 3px, 4px 4px, 2px 2px, 5px 100%;
  animation: dp-crt-fs-noise 0.05s steps(3) infinite;
}

@keyframes dp-crt-fs-noise {
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

.dp-crt-fs__snow-bars {
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
  animation: dp-crt-fs-bars 0.28s linear infinite;
}

@keyframes dp-crt-fs-bars {
  0% {
    background-position: 0 0;
  }
  100% {
    background-position: 0 -6px;
  }
}

.dp-crt-fs__snow-bright {
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
  animation: dp-crt-fs-bright 1.05s linear infinite;
  mix-blend-mode: screen;
}

@keyframes dp-crt-fs-bright {
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

@keyframes dp-crt-fs-jitter {
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

.dp-crt-fs__scanlines {
  position: absolute;
  inset: 0;
  z-index: 3;
  pointer-events: none;
  background: repeating-linear-gradient(
    to bottom,
    transparent 0,
    transparent 2px,
    rgba(0, 0, 0, 0.14) 2px,
    rgba(0, 0, 0, 0.14) 3px
  );
  background-size: 100% 3px;
  opacity: 0.4;
  animation: dp-crt-fs-scan 0.42s linear infinite;
}

.dp-crt-fs--lite .dp-crt-fs__scanlines {
  opacity: 0.16;
  animation: none;
}

@keyframes dp-crt-fs-scan {
  0% {
    background-position: 0 0;
  }
  100% {
    background-position: 0 -3px;
  }
}

.dp-crt-fs__vignette {
  position: absolute;
  inset: 0;
  z-index: 4;
  pointer-events: none;
  background: radial-gradient(
    ellipse 88% 78% at 50% 48%,
    transparent 32%,
    rgba(0, 0, 0, 0.48) 100%
  );
}

.dp-crt-fs__flash {
  position: absolute;
  inset: 0;
  z-index: 5;
  pointer-events: none;
  opacity: 0;
  background: transparent;
}

.dp-crt-fs__flash--pulse {
  animation: dp-crt-fs-flash 0.12s ease-out forwards;
}

@keyframes dp-crt-fs-flash {
  0% {
    opacity: 1;
    background: #f8fafc;
  }
  100% {
    opacity: 0;
    background: transparent;
  }
}
</style>

<style>
body[data-dp-fluidity='eco'] .dp-crt-fs__snow--active,
body[data-dp-fluidity='eco'] .dp-crt-fs__snow-noise,
body[data-dp-fluidity='eco'] .dp-crt-fs__snow-bars,
body[data-dp-fluidity='eco'] .dp-crt-fs__snow-bright,
body[data-dp-fluidity='eco'] .dp-crt-fs__scanlines {
  animation: none !important;
}

body[data-dp-fluidity='eco'] .dp-crt-fs__snow--active {
  opacity: 0.38;
  inset: 0;
  transform: none;
}

body[data-dp-fluidity='eco'] .dp-crt-fs__snow-noise,
body[data-dp-fluidity='eco'] .dp-crt-fs__snow-bars,
body[data-dp-fluidity='eco'] .dp-crt-fs__snow-bright {
  display: none;
}

@media (prefers-reduced-motion: reduce) {
  .dp-crt-fs__snow,
  .dp-crt-fs__snow--active,
  .dp-crt-fs__snow-noise,
  .dp-crt-fs__snow-bars,
  .dp-crt-fs__snow-bright,
  .dp-crt-fs__scanlines,
  .dp-crt-fs__flash--pulse,
  .dp-crt-fs--fade {
    animation: none !important;
  }

  .dp-crt-fs__snow--active {
    opacity: 0.36;
    inset: 0;
    transform: none;
  }

  .dp-crt-fs__snow-noise,
  .dp-crt-fs__snow-bars,
  .dp-crt-fs__snow-bright {
    display: none;
  }
}
</style>
