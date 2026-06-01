<template>
  <transition name="dp-popup-root">
    <div v-if="active" class="dp-popup" aria-live="assertive">
      <!-- 电视机外壳 -->
      <div class="dp-popup__tv">
        <div class="dp-popup__antenna dp-popup__antenna--l" />
        <div class="dp-popup__antenna dp-popup__antenna--r" />
        <div class="dp-popup__body">
          <div class="dp-popup__bezel">
            <div class="dp-popup__screen" :class="screenClass">
              <!-- 雪花层 -->
              <div class="dp-popup__snow" :class="{ 'dp-popup__snow--active': phase === 'snow' || phase === 'glitch-out' }">
                <span class="dp-popup__snow-noise" />
                <span class="dp-popup__snow-bars" />
              </div>
              <!-- 扫描线 -->
              <div class="dp-popup__scanlines" />
              <!-- 暗角 -->
              <div class="dp-popup__vignette" />
              <!-- 闪白 -->
              <div class="dp-popup__flash" :class="{ 'dp-popup__flash--pulse': phase === 'focus' || phase === 'glitch-out' }" />
              <!-- 内容文字 -->
              <div v-if="phase === 'display' || phase === 'focus' || phase === 'glitch-out'" class="dp-popup__content" :class="contentClass">
                <p class="dp-popup__title">{{ eventTitle }}</p>
                <p v-if="eventSubtitle" class="dp-popup__subtitle">{{ eventSubtitle }}</p>
                <p v-if="eventDetail" class="dp-popup__detail">{{ eventDetail }}</p>
              </div>
            </div>
          </div>
          <!-- 底部旋钮 -->
          <div class="dp-popup__controls">
            <span class="dp-popup__knob" />
            <span class="dp-popup__knob dp-popup__knob--sm" />
            <span class="dp-popup__led" :class="ledClass" />
          </div>
        </div>
        <div class="dp-popup__feet"><span /><span /></div>
      </div>
    </div>
  </transition>
</template>

<script>
export default {
  name: 'DpCrtEventPopup',
  data: function () {
    return {
      active: false,
      phase: 'idle',    // idle | entering | snow | focus | display | glitch-out | exiting
      eventType: 'info',
      eventTitle: '',
      eventSubtitle: '',
      eventDetail: '',
      queue: [],
      timers: []
    }
  },
  computed: {
    screenClass: function () {
      return {
        'dp-popup__screen--snow-phase': this.phase === 'snow',
        'dp-popup__screen--glitching': this.phase === 'glitch-out'
      }
    },
    contentClass: function () {
      return {
        'dp-popup__content--danger': this.eventType === 'danger',
        'dp-popup__content--win': this.eventType === 'win',
        'dp-popup__content--badbeat': this.eventType === 'badbeat',
        'dp-popup__content--info': this.eventType === 'info'
      }
    },
    ledClass: function () {
      return {
        'dp-popup__led--red': this.eventType === 'danger' || this.eventType === 'badbeat',
        'dp-popup__led--amber': this.eventType === 'win',
        'dp-popup__led--green': this.eventType === 'info'
      }
    }
  },
  beforeDestroy: function () {
    this.clearAllTimers()
  },
  methods: {
    clearAllTimers: function () {
      var t = this.timers
      for (var i = 0; i < t.length; i++) clearTimeout(t[i])
      this.timers = []
    },
    schedule: function (fn, ms) {
      var id = setTimeout(fn, ms)
      this.timers.push(id)
      return id
    },

    /**
     * 触发弹窗事件
     * @param {'danger'|'win'|'badbeat'|'info'} type
     * @param {string} title 主标题
     * @param {string} subtitle 副标题
     * @param {string} detail 详情
     */
    trigger: function (type, title, subtitle, detail) {
      this.queue.push({ type: type, title: title, subtitle: subtitle || '', detail: detail || '' })
      if (!this.active) this.playNext()
    },

    playNext: function () {
      if (!this.queue.length) return
      var evt = this.queue.shift()
      this.eventType = evt.type
      this.eventTitle = evt.title
      this.eventSubtitle = evt.subtitle
      this.eventDetail = evt.detail
      this.play()
    },

    play: function () {
      var self = this
      this.clearAllTimers()
      this.active = true
      this.phase = 'entering'

      // 0ms: 进入 → 300ms spring 弹入
      this.schedule(function () {
        self.phase = 'snow'
        // 300ms 雪花
        self.schedule(function () {
          self.phase = 'focus'
          // 100ms 对焦
          self.schedule(function () {
            self.phase = 'display'
            // 展示 1500ms
            self.schedule(function () {
              self.phase = 'glitch-out'
              // 200ms 花屏
              self.schedule(function () {
                self.phase = 'exiting'
                // 250ms 缩小消失
                self.schedule(function () {
                  self.active = false
                  self.phase = 'idle'
                  // 继续队列
                  self.$nextTick(function () { self.playNext() })
                }, 280)
              }, 200)
            }, 1500)
          }, 100)
        }, 300)
      }, 20)
    }
  }
}
</script>

<style scoped>
/* ====== 弹窗容器 ====== */
.dp-popup {
  position: fixed;
  inset: 0;
  z-index: 10055;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.dp-popup-root-enter-active {
  transition: opacity 0.1s ease;
}
.dp-popup-root-leave-active {
  transition: opacity 0.25s ease;
}
.dp-popup-root-enter,
.dp-popup-root-leave-to {
  opacity: 0;
}

/* ====== 电视机外壳 ====== */
.dp-popup__tv {
  --tv-w: 280px;
  width: var(--tv-w);
  position: relative;
  pointer-events: auto;
  filter: drop-shadow(0 8px 24px rgba(0, 0, 0, 0.65));
  animation: dp-popup-enter 0.3s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
}

.dp-popup__tv.dp-popup--exiting {
  animation: dp-popup-exit 0.25s ease-in forwards;
}

@keyframes dp-popup-enter {
  from { transform: scale(0.3); opacity: 0; }
  to   { transform: scale(1); opacity: 1; }
}

@keyframes dp-popup-exit {
  from { transform: scale(1); opacity: 1; }
  to   { transform: scale(0.2); opacity: 0; }
}

/* 天线 */
.dp-popup__antenna {
  position: absolute;
  bottom: calc(100% - 6px);
  width: 3px;
  height: 38px;
  background: linear-gradient(to top, #3a4048, #4af626);
  border-radius: 2px;
  transform-origin: bottom center;
}
.dp-popup__antenna--l { left: 30%; transform: rotate(-28deg); }
.dp-popup__antenna--r { right: 30%; transform: rotate(28deg); }

/* 机身 */
.dp-popup__body {
  background: linear-gradient(165deg, #1a1e24, #11141a);
  border: 2px solid #3a4048;
  border-radius: 8px 8px 4px 4px;
  padding: 8px 8px 6px;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.04), 0 3px 0 rgba(0,0,0,0.3);
}

/* 边框 */
.dp-popup__bezel {
  background: #1a1e24;
  border-radius: 4px;
  padding: 5px;
  box-shadow: inset 0 2px 8px rgba(0,0,0,0.5);
}

/* 屏幕 */
.dp-popup__screen {
  position: relative;
  aspect-ratio: 4 / 3;
  border-radius: 2px;
  overflow: hidden;
  background: #0a0c0e;
}

/* 雪花层 */
.dp-popup__snow {
  position: absolute;
  inset: -4%;
  z-index: 1;
  opacity: 0;
  pointer-events: none;
  overflow: hidden;
  transition: opacity 0.08s ease;
  background: #0e1012;
}
.dp-popup__snow--active {
  opacity: 0.92;
  animation: dp-popup-jitter 0.06s steps(2) infinite;
}

@keyframes dp-popup-jitter {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(-1.5px, 0.8px); }
}

.dp-popup__snow-noise,
.dp-popup__snow-bars {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.dp-popup__snow-noise {
  opacity: 0.85;
  background-image:
    repeating-radial-gradient(circle at 12% 18%, rgba(255,255,255,0.6) 0 0.45px, transparent 0.55px),
    repeating-radial-gradient(circle at 78% 62%, rgba(210,218,228,0.5) 0 0.4px, transparent 0.5px),
    repeating-radial-gradient(circle at 44% 81%, rgba(240,245,252,0.42) 0 0.35px, transparent 0.45px),
    repeating-linear-gradient(92deg, rgba(255,255,255,0.06) 0 1px, transparent 1px 2px);
  background-size: 3px 3px, 4px 4px, 2.5px 2.5px, 5px 100%;
  animation: dp-popup-noise 0.04s steps(3) infinite;
}

@keyframes dp-popup-noise {
  0% { background-position: 0 0, 1px 0, 0 1px, 0 0; opacity: 0.78; }
  50% { background-position: 2px 1px, -1px 2px, 1px -1px, 3px 0; opacity: 0.92; }
  100% { background-position: -1px 2px, 2px -1px, -2px 1px, -2px 0; opacity: 0.75; }
}

.dp-popup__snow-bars {
  opacity: 0.7;
  mix-blend-mode: screen;
  background: repeating-linear-gradient(
    to bottom,
    rgba(0,0,0,0.14) 0 1px,
    rgba(235,240,248,0.16) 1px 2px,
    rgba(255,255,255,0.08) 2px 3px,
    transparent 3px 6px
  );
  background-size: 100% 6px;
  animation: dp-popup-bars 0.25s linear infinite;
}

@keyframes dp-popup-bars {
  to { background-position: 0 -6px; }
}

/* 扫描线 */
.dp-popup__scanlines {
  position: absolute;
  inset: 0;
  z-index: 2;
  pointer-events: none;
  background: repeating-linear-gradient(
    to bottom,
    transparent 0 2px,
    rgba(0,0,0,0.15) 2px 3px
  );
  background-size: 100% 3px;
  opacity: 0.45;
}

/* 暗角 */
.dp-popup__vignette {
  position: absolute;
  inset: 0;
  z-index: 3;
  background: radial-gradient(ellipse 85% 75% at 50% 50%, transparent 35%, rgba(0,0,0,0.5) 100%);
  pointer-events: none;
}

/* 闪白 */
.dp-popup__flash {
  position: absolute;
  inset: 0;
  z-index: 5;
  pointer-events: none;
  opacity: 0;
}
.dp-popup__flash--pulse {
  animation: dp-popup-flash 0.12s ease-out;
}

@keyframes dp-popup-flash {
  0% { opacity: 1; background: #f8fafc; }
  100% { opacity: 0; background: transparent; }
}

/* 内容文字 */
.dp-popup__content {
  position: absolute;
  inset: 0;
  z-index: 4;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 16px;
  text-align: center;
  font-family: 'Press Start 2P', monospace;
  text-shadow: 0 0 8px rgba(74, 246, 38, 0.7);
}

.dp-popup__title {
  font-size: 14px;
  color: #4af626;
  margin: 0 0 10px;
  line-height: 1.5;
  letter-spacing: 0.04em;
}

.dp-popup__subtitle {
  font-size: 11px;
  color: #72f052;
  margin: 0 0 6px;
  line-height: 1.4;
}

.dp-popup__detail {
  font-size: 10px;
  color: #ffe066;
  margin: 0;
  line-height: 1.4;
}

/* 事件类型配色 */
.dp-popup__content--danger .dp-popup__title {
  color: #ff4444;
  text-shadow: 0 0 10px rgba(255, 68, 68, 0.7);
  animation: dp-popup-danger-blink 0.5s step-end infinite;
}
@keyframes dp-popup-danger-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

.dp-popup__content--win .dp-popup__title {
  color: #ffe066;
  text-shadow: 0 0 12px rgba(255, 224, 102, 0.8);
}

.dp-popup__content--badbeat .dp-popup__screen {
  background: #0000aa;
}
.dp-popup__content--badbeat .dp-popup__title {
  color: #ffffff;
  text-shadow: none;
  font-family: 'Courier New', monospace;
  font-size: 13px;
}

/* 底部旋钮和 LED */
.dp-popup__controls {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 6px;
  padding: 0 2px;
}
.dp-popup__knob {
  width: 14px; height: 14px;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, #2a303a, #1a1e24);
  border: 1px solid #3a4048;
  box-shadow: inset 0 -2px 3px rgba(0,0,0,0.3);
}
.dp-popup__knob--sm { width: 10px; height: 10px; }
.dp-popup__led {
  margin-left: auto;
  width: 7px; height: 7px;
  border-radius: 50%;
  background: #4af626;
  box-shadow: 0 0 6px rgba(74,246,38,0.6);
}
.dp-popup__led--amber { background: #ffe066; box-shadow: 0 0 6px rgba(255,224,102,0.6); }
.dp-popup__led--red   { background: #ff4444; box-shadow: 0 0 6px rgba(255,68,68,0.6); }

.dp-popup__feet {
  display: flex;
  justify-content: space-between;
  padding: 0 16px;
  margin-top: 2px;
}
.dp-popup__feet span {
  width: 24px; height: 4px;
  border-radius: 0 0 4px 4px;
  background: #2a3038;
}

@media (prefers-reduced-motion: reduce) {
  .dp-popup__snow--active,
  .dp-popup__snow-noise,
  .dp-popup__snow-bars,
  .dp-popup__flash--pulse,
  .dp-popup__content--danger .dp-popup__title,
  .dp-popup__tv {
    animation: none !important;
  }
  .dp-popup__tv { transform: scale(1); opacity: 1; }
}
</style>
