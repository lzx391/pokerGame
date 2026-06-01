<template>
  <transition name="dp-boot-root">
    <div v-if="visible" class="dp-boot" aria-hidden="true">
      <!-- 第一阶段：雪花 -->
      <div v-if="phase === 'snow'" class="dp-boot__snow">
        <span class="dp-boot__snow-noise" />
        <span class="dp-boot__snow-bars" />
      </div>

      <!-- 第二阶段：黑屏 + 逐行打印 -->
      <div v-if="phase === 'typing' || phase === 'fade-out'" class="dp-boot__terminal">
        <pre class="dp-boot__lines"><span
  v-for="(line, i) in visibleLines"
  :key="i"
  class="dp-boot__line"
  :class="{ 'dp-boot__line--ok': line.ok, 'dp-boot__line--cursor': i === visibleLines.length - 1 && phase === 'typing' }"
>{{ line.text }}<span v-if="i === visibleLines.length - 1 && phase === 'typing'" class="dp-boot__cursor">█</span></span></pre>
      </div>
    </div>
  </transition>
</template>

<script>
var BOOT_LINES = [
  { text: 'POKER-SYS v2.4.1 BIOS', ok: false },
  { text: '[  OK  ]  Initializing deck subsystem...', ok: true },
  { text: '[  OK  ]  Establishing player seat map...', ok: true },
  { text: '[  OK  ]  Loading community card pool...', ok: true },
  { text: '[  OK  ]  Game ready. Press any key.', ok: true }
]

export default {
  name: 'DpCrtBootSequence',
  data: function () {
    return {
      visible: false,
      phase: 'snow',
      visibleLines: [],
      timers: []
    }
  },
  beforeDestroy: function () {
    this.clearTimers()
  },
  methods: {
    clearTimers: function () {
      var t = this.timers
      for (var i = 0; i < t.length; i++) {
        clearTimeout(t[i])
      }
      this.timers = []
    },
    schedule: function (fn, ms) {
      var id = setTimeout(fn, ms)
      this.timers.push(id)
      return id
    },
    play: function (onDone) {
      var self = this
      this.clearTimers()
      this.visible = true
      this.phase = 'snow'
      this.visibleLines = []

      // 600ms 雪花 → 黑屏 + 逐行
      this.schedule(function () {
        self.phase = 'typing'

        // 逐行打字
        BOOT_LINES.forEach(function (line, idx) {
          self.schedule(function () {
            self.visibleLines.push(line)
            // 最后一行打印完后等 350ms 再淡出
            if (idx === BOOT_LINES.length - 1) {
              self.schedule(function () {
                self.phase = 'fade-out'
                self.schedule(function () {
                  self.visible = false
                  self.phase = 'snow'
                  self.visibleLines = []
                  if (typeof onDone === 'function') onDone()
                }, 400)
              }, 450)
            }
          }, 350 + idx * 280)
        })
      }, 600)
    }
  }
}
</script>

<style scoped>
.dp-boot {
  position: fixed;
  inset: 0;
  z-index: 10060;
  pointer-events: none;
}

.dp-boot-root-enter-active {
  transition: opacity 0.08s ease;
}

.dp-boot-root-leave-active {
  transition: opacity 0.35s ease;
}

.dp-boot-root-enter,
.dp-boot-root-leave-to {
  opacity: 0;
}

/* 雪花阶段 */
.dp-boot__snow {
  position: absolute;
  inset: 0;
  background: #080a0c;
  overflow: hidden;
}

.dp-boot__snow-noise,
.dp-boot__snow-bars {
  position: absolute;
  inset: 0;
}

.dp-boot__snow-noise {
  opacity: 0.7;
  background-image:
    repeating-radial-gradient(circle at 15% 20%, rgba(255,255,255,0.55) 0 0.4px, transparent 0.5px),
    repeating-radial-gradient(circle at 72% 58%, rgba(210,218,228,0.45) 0 0.35px, transparent 0.45px),
    repeating-radial-gradient(circle at 40% 76%, rgba(240,245,252,0.38) 0 0.3px, transparent 0.4px);
  background-size: 3px 3px, 4px 4px, 2.5px 2.5px;
  animation: dp-boot-noise 0.04s steps(3) infinite;
}

@keyframes dp-boot-noise {
  0% { background-position: 0 0, 1px 0, 0 1px; opacity: 0.65; }
  50% { background-position: 2px 1px, -1px 2px, 1px -1px; opacity: 0.88; }
  100% { background-position: -1px 2px, 2px -1px, -2px 1px; opacity: 0.62; }
}

.dp-boot__snow-bars {
  opacity: 0.55;
  mix-blend-mode: screen;
  background: repeating-linear-gradient(
    to bottom,
    rgba(0,0,0,0.12) 0 1px,
    rgba(235,240,248,0.14) 1px 2px,
    rgba(255,255,255,0.06) 2px 3px,
    transparent 3px 6px
  );
  background-size: 100% 6px;
  animation: dp-boot-bars 0.22s linear infinite;
}

@keyframes dp-boot-bars {
  to { background-position: 0 -6px; }
}

/* 终端打字阶段 */
.dp-boot__terminal {
  position: absolute;
  inset: 0;
  background: #080a0c;
  display: flex;
  align-items: center;
  justify-content: center;
}

.dp-boot__lines {
  margin: 0;
  font-family: 'Courier New', ui-monospace, 'PingFang SC', 'Microsoft YaHei', monospace;
  font-size: 14px;
  line-height: 1.8;
  color: #4af626;
  white-space: pre;
  text-shadow: 0 0 6px rgba(74, 246, 38, 0.55);
}

.dp-boot__line {
  display: block;
}

.dp-boot__line--ok {
  color: #72f052;
}

.dp-boot__cursor {
  animation: dp-boot-cursor-blink 0.8s step-end infinite;
  color: #4af626;
  text-shadow: 0 0 10px rgba(74, 246, 38, 0.8);
}

@keyframes dp-boot-cursor-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

@media (prefers-reduced-motion: reduce) {
  .dp-boot__snow-noise,
  .dp-boot__snow-bars,
  .dp-boot__cursor {
    animation: none !important;
  }
  .dp-boot__cursor {
    opacity: 1;
  }
}
</style>
