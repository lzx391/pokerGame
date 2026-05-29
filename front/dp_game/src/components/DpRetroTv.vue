<template>
  <div
    class="dp-retro-tv"
    aria-hidden="true"
  >
    <div class="dp-retro-tv__antenna dp-retro-tv__antenna--l" />
    <div class="dp-retro-tv__antenna dp-retro-tv__antenna--r" />
    <div class="dp-retro-tv__body">
      <div class="dp-retro-tv__bezel">
        <div class="dp-retro-tv__screen">
          <div class="dp-retro-tv__boot" />
          <div class="dp-retro-tv__snow">
            <span class="dp-retro-tv__snow-noise" />
            <span class="dp-retro-tv__snow-bars" />
            <span class="dp-retro-tv__snow-bright" />
          </div>
          <div class="dp-retro-tv__scanlines" />
          <div class="dp-retro-tv__vignette" />
        </div>
      </div>
      <div class="dp-retro-tv__controls">
        <span class="dp-retro-tv__knob" />
        <span class="dp-retro-tv__knob dp-retro-tv__knob--sm" />
        <span class="dp-retro-tv__led" />
      </div>
    </div>
    <div class="dp-retro-tv__feet">
      <span /><span />
    </div>
  </div>
</template>

<script>
export default {
  name: 'DpRetroTv'
}
</script>

<style scoped>
.dp-retro-tv {
  --dp-tv-w: 128px;
  --dp-tv-shell: color-mix(in srgb, var(--dp-subpanel-bg, #e8ecf0) 72%, var(--dp-panel-border, #c5cdd6));
  --dp-tv-bezel: color-mix(in srgb, var(--dp-text-secondary, #5a6578) 55%, #1a1f28);
  --dp-tv-screen-off: color-mix(in srgb, var(--dp-text-muted, #909399) 35%, #1c1f24);
  pointer-events: none;
  user-select: none;
  width: var(--dp-tv-w);
  position: relative;
  filter: drop-shadow(0 6px 14px color-mix(in srgb, var(--dp-text-primary, #2c3e50) 18%, transparent));
}

.dp-retro-tv__antenna {
  position: absolute;
  bottom: calc(100% - 6px);
  width: 2px;
  height: 28px;
  background: linear-gradient(
    to top,
    var(--dp-tv-bezel),
    color-mix(in srgb, var(--dp-accent, #409eff) 40%, var(--dp-tv-bezel))
  );
  border-radius: 2px;
  transform-origin: bottom center;
}

.dp-retro-tv__antenna--l {
  left: 28%;
  transform: rotate(-22deg);
}

.dp-retro-tv__antenna--r {
  right: 28%;
  transform: rotate(22deg);
}

.dp-retro-tv__body {
  background: linear-gradient(
    165deg,
    color-mix(in srgb, var(--dp-panel-bg, #fff) 40%, var(--dp-tv-shell)),
    var(--dp-tv-shell)
  );
  border: 2px solid color-mix(in srgb, var(--dp-panel-border, #dcdfe6) 80%, var(--dp-tv-bezel));
  border-radius: 10px 10px 6px 6px;
  padding: 8px 8px 6px;
  box-shadow:
    inset 0 1px 0 color-mix(in srgb, var(--dp-panel-bg, #fff) 55%, transparent),
    0 2px 0 color-mix(in srgb, var(--dp-tv-bezel) 25%, transparent);
}

.dp-retro-tv__bezel {
  background: var(--dp-tv-bezel);
  border-radius: 6px;
  padding: 5px;
  box-shadow: inset 0 2px 6px rgba(0, 0, 0, 0.45);
}

.dp-retro-tv__screen {
  position: relative;
  aspect-ratio: 16 / 9;
  border-radius: 3px;
  overflow: hidden;
  background: var(--dp-tv-screen-off);
}

/* 开机闪一下 → 露出花屏 */
.dp-retro-tv__boot {
  position: absolute;
  inset: 0;
  z-index: 4;
  background: color-mix(in srgb, var(--dp-panel-bg, #f5f7fa) 88%, #fff);
  opacity: 1;
  animation: dp-tv-boot-flash 0.52s ease-out forwards;
}

@keyframes dp-tv-boot-flash {
  0% {
    opacity: 1;
    background: #f8fafc;
  }
  18% {
    opacity: 1;
    background: color-mix(in srgb, var(--dp-accent, #409eff) 12%, #fff);
  }
  42% {
    opacity: 0.75;
    background: color-mix(in srgb, var(--dp-text-muted, #909399) 40%, #e8eaed);
  }
  100% {
    opacity: 0;
    background: transparent;
  }
}

/* CRT 花屏：与 DpAuthStage 同套视觉 */
.dp-retro-tv__snow {
  position: absolute;
  inset: -8%;
  z-index: 1;
  opacity: 0.88;
  overflow: hidden;
  pointer-events: none;
  background: color-mix(in srgb, #1a1e24 88%, #3a4048);
  animation: dp-tv-crt-jitter 0.07s steps(2) infinite;
}

.dp-retro-tv__snow-noise,
.dp-retro-tv__snow-bars,
.dp-retro-tv__snow-bright {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.dp-retro-tv__snow-noise {
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
  animation: dp-tv-noise-flicker 0.05s steps(3) infinite;
}

@keyframes dp-tv-noise-flicker {
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

.dp-retro-tv__snow-bars {
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
  animation: dp-tv-roll-bars 0.28s linear infinite;
}

@keyframes dp-tv-roll-bars {
  0% {
    background-position: 0 0;
  }
  100% {
    background-position: 0 -6px;
  }
}

.dp-retro-tv__snow-bright {
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
  animation: dp-tv-bright-bar 1.05s linear infinite;
  mix-blend-mode: screen;
}

@keyframes dp-tv-bright-bar {
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

@keyframes dp-tv-crt-jitter {
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

.dp-retro-tv__scanlines {
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
  opacity: 0.52;
  animation: dp-tv-scan-roll-fast 0.42s linear infinite;
}

@keyframes dp-tv-scan-roll-fast {
  0% {
    background-position: 0 0;
  }
  100% {
    background-position: 0 -3px;
  }
}

.dp-retro-tv__vignette {
  position: absolute;
  inset: 0;
  z-index: 3;
  background: radial-gradient(
    ellipse 85% 75% at 50% 50%,
    transparent 35%,
    rgba(0, 0, 0, 0.45) 100%
  );
  pointer-events: none;
}

.dp-retro-tv__controls {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  padding: 0 2px;
}

.dp-retro-tv__knob {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: radial-gradient(
    circle at 35% 30%,
    color-mix(in srgb, var(--dp-panel-bg, #fff) 50%, var(--dp-tv-shell)),
    var(--dp-tv-bezel)
  );
  border: 1px solid color-mix(in srgb, var(--dp-tv-bezel) 70%, #000);
  box-shadow: inset 0 -2px 3px rgba(0, 0, 0, 0.25);
}

.dp-retro-tv__knob--sm {
  width: 10px;
  height: 10px;
}

.dp-retro-tv__led {
  margin-left: auto;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--dp-accent, #409eff);
  box-shadow: 0 0 6px color-mix(in srgb, var(--dp-accent, #409eff) 65%, transparent);
  animation: dp-tv-led-pulse 2.4s ease-in-out infinite;
}

@keyframes dp-tv-led-pulse {
  0%,
  100% {
    opacity: 0.55;
  }
  50% {
    opacity: 1;
  }
}

.dp-retro-tv__feet {
  display: flex;
  justify-content: space-between;
  padding: 0 14px;
  margin-top: 2px;
}

.dp-retro-tv__feet span {
  width: 22px;
  height: 4px;
  border-radius: 0 0 3px 3px;
  background: var(--dp-tv-bezel);
}
</style>

<!-- eco / reduced-motion 需命中 body，不能 scoped -->
<style>
body[data-dp-fluidity='eco'] .dp-retro-tv__boot,
body[data-dp-fluidity='eco'] .dp-retro-tv__snow,
body[data-dp-fluidity='eco'] .dp-retro-tv__snow-noise,
body[data-dp-fluidity='eco'] .dp-retro-tv__snow-bars,
body[data-dp-fluidity='eco'] .dp-retro-tv__snow-bright,
body[data-dp-fluidity='eco'] .dp-retro-tv__scanlines {
  animation: none !important;
}

body[data-dp-fluidity='eco'] .dp-retro-tv__boot {
  opacity: 0;
  display: none;
}

body[data-dp-fluidity='eco'] .dp-retro-tv__snow {
  inset: 0;
  opacity: 0.35;
  transform: none;
  background: color-mix(in srgb, var(--dp-text-muted, #909399) 55%, #1c1f24);
}

body[data-dp-fluidity='eco'] .dp-retro-tv__snow-noise,
body[data-dp-fluidity='eco'] .dp-retro-tv__snow-bars,
body[data-dp-fluidity='eco'] .dp-retro-tv__snow-bright {
  display: none;
}

body[data-dp-fluidity='eco'] .dp-retro-tv__scanlines {
  opacity: 0.2;
  background: repeating-linear-gradient(
    to bottom,
    transparent 0,
    transparent 3px,
    rgba(0, 0, 0, 0.08) 3px,
    rgba(0, 0, 0, 0.08) 4px
  );
}

body[data-dp-fluidity='eco'] .dp-retro-tv__led {
  animation: none;
  opacity: 0.7;
}

@media (prefers-reduced-motion: reduce) {
  .dp-retro-tv__boot,
  .dp-retro-tv__snow,
  .dp-retro-tv__snow-noise,
  .dp-retro-tv__snow-bars,
  .dp-retro-tv__snow-bright,
  .dp-retro-tv__scanlines,
  .dp-retro-tv__led {
    animation: none !important;
  }

  .dp-retro-tv__boot {
    opacity: 0;
    display: none;
  }

  .dp-retro-tv__snow {
    inset: 0;
    opacity: 0.35;
    transform: none;
    background: color-mix(in srgb, var(--dp-text-muted, #909399) 55%, #1c1f24);
  }

  .dp-retro-tv__snow-noise,
  .dp-retro-tv__snow-bars,
  .dp-retro-tv__snow-bright {
    display: none;
  }

  .dp-retro-tv__scanlines {
    opacity: 0.18;
  }

  .dp-retro-tv__led {
    opacity: 0.65;
  }
}
</style>
