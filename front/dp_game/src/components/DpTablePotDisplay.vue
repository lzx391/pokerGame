<template>
  <div class="dp-tpot" :class="{ 'dp-tpot--updating': updating, 'dp-tpot--pulse': pulsing }">
    <!-- CRT 图层 -->
    <div class="dp-tpot__scanlines" />
    <div class="dp-tpot__glow" />
    <!-- 数据包粒子 -->
    <div class="dp-tpot__particles">
      <span
        v-for="p in particles"
        :key="p.id"
        class="dp-tpot__pkt"
        :style="p.style"
        @animationend="onPktEnd(p.id)"
      />
    </div>
    <!-- 数值 -->
    <span class="dp-tpot__label">&gt; POT:</span>
    <span class="dp-tpot__val" :class="{ 'dp-tpot__val--flash': flashVal }">{{ displayVal }}</span>
    <span class="dp-tpot__blink">&#9608;</span>
  </div>
</template>

<script>
var PKT_ID = 0

export default {
  name: 'DpTablePotDisplay',
  inject: { dpGameView: { default: null } },
  props: {
    pot: { type: Number, default: 0 }
  },
  data: function () {
    return {
      displayVal: 0,
      updating: false,
      pulsing: false,
      flashVal: false,
      particles: [],
      animFrame: null,
      rollTimer: null,
      pulseTimer: null
    }
  },
  computed: {
    vm: function () { return this.dpGameView },
    isRetro: function () {
      return this.vm && this.vm.gameUiTheme === 'retro8bit' && this.vm.viewportWidth > 600 && !this.vm.ecoMode && !this.vm.prefersReducedMotion
    }
  },
  watch: {
    pot: function (nv, ov) {
      if (!this.isRetro) { this.displayVal = nv; return }
      var diff = nv - (ov || 0)
      if (diff > 0) {
        this.onPotIncrease(nv, ov || 0, diff)
      } else if (diff < 0) {
        this.displayVal = nv
        this.flashBriefly()
      } else if (ov == null) {
        this.displayVal = nv
      }
    }
  },
  mounted: function () {
    this.displayVal = this.pot
  },
  beforeDestroy: function () {
    if (this.rollTimer) clearTimeout(this.rollTimer)
    if (this.pulseTimer) clearTimeout(this.pulseTimer)
    if (this.animFrame) cancelAnimationFrame(this.animFrame)
  },
  methods: {
    onPotIncrease: function (newVal, oldVal, diff) {
      var self = this
      // 发射数据包粒子
      this.spawnPackets(Math.min(diff, 40))
      this.updating = true

      // 数字滚动
      var steps = Math.min(Math.ceil(diff / 5), 15)
      var stepSize = Math.max(1, Math.ceil(diff / steps))
      var current = oldVal
      var step = 0
      var roll = function () {
        step++
        current = Math.min(newVal, current + stepSize + Math.floor(Math.random() * stepSize))
        self.displayVal = current
        if (current >= newVal || step >= steps) {
          self.displayVal = newVal
          self.flashBriefly()
          self.updating = false
          return
        }
        self.rollTimer = setTimeout(roll, 40 + Math.random() * 30)
      }
      roll()
    },
    flashBriefly: function () {
      var self = this
      this.flashVal = true
      this.pulsing = true
      if (this.pulseTimer) clearTimeout(this.pulseTimer)
      this.pulseTimer = setTimeout(function () {
        self.flashVal = false
        self.pulsing = false
      }, 300)
    },
    spawnPackets: function (count) {
      var self = this
      var pkts = []
      for (var i = 0; i < Math.min(count, 12); i++) {
        var id = ++PKT_ID
        var angle = Math.random() * 360
        var dist = 60 + Math.random() * 40
        var rad = angle * Math.PI / 180
        var sx = Math.cos(rad) * dist
        var sy = Math.sin(rad) * dist
        var scale = 0.4 + Math.random() * 0.6
        var dur = 0.35 + Math.random() * 0.35
        var delay = Math.random() * 0.15
        pkts.push({
          id: id,
          style: {
            '--pkt-from-x': sx + 'px',
            '--pkt-from-y': sy + 'px',
            '--pkt-scale': scale,
            '--pkt-dur': dur + 's',
            '--pkt-delay': delay + 's',
            animationDelay: delay + 's',
            animationDuration: dur + 's'
          }
        })
      }
      this.particles = pkts
      // 清理
      var maxDur = 0
      for (var j = 0; j < pkts.length; j++) {
        var d = parseFloat(pkts[j].style['--pkt-delay']) + parseFloat(pkts[j].style['--pkt-dur'])
        if (d > maxDur) maxDur = d
      }
      setTimeout(function () { self.particles = [] }, (maxDur + 0.1) * 1000)
    },
    onPktEnd: function (id) {
      // 由 CSS animationend 触发，粒子到达时移除
      this.particles = this.particles.filter(function (p) { return p.id !== id })
    }
  }
}
</script>

<style scoped>
.dp-tpot {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  font-family: 'Courier New', ui-monospace, 'PingFang SC', monospace;
  font-size: 12px;
  color: #4af626;
  background: rgba(4, 6, 8, 0.85);
  border: 1px solid rgba(74, 246, 38, 0.25);
  border-radius: 2px;
  box-shadow: 0 0 0 1px #000, 0 2px 8px rgba(0,0,0,0.4);
  overflow: hidden;
  user-select: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.dp-tpot--updating {
  border-color: rgba(74, 246, 38, 0.5);
}
.dp-tpot--pulse {
  box-shadow: 0 0 0 1px #000, 0 0 16px rgba(74, 246, 38, 0.2);
}

/* CRT 图层 */
.dp-tpot__scanlines {
  position: absolute; inset: 0; z-index: 2; pointer-events: none;
  background: repeating-linear-gradient(to bottom, transparent 0 1px, rgba(0,0,0,0.08) 1px 2px);
  background-size: 100% 2px;
}
.dp-tpot__glow {
  position: absolute; inset: 0; z-index: 1; pointer-events: none;
  background: radial-gradient(ellipse at center, rgba(74,246,38,0.04) 0%, transparent 70%);
  transition: opacity 0.15s;
}
.dp-tpot--pulse .dp-tpot__glow {
  background: radial-gradient(ellipse at center, rgba(74,246,38,0.12) 0%, transparent 70%);
}

/* 标签 */
.dp-tpot__label {
  position: relative; z-index: 3;
  color: rgba(74, 246, 38, 0.55);
  font-size: 10px;
  letter-spacing: 0.04em;
}

/* 数值 */
.dp-tpot__val {
  position: relative; z-index: 3;
  font-weight: bold;
  font-size: 14px;
  text-shadow: 0 0 6px rgba(74, 246, 38, 0.4);
  transition: text-shadow 0.15s;
}
.dp-tpot__val--flash {
  text-shadow: 0 0 0 rgba(74,246,38,0), 0 0 16px rgba(74,246,38,0.9);
  color: #b8ffa0;
}

/* 闪烁光标 */
.dp-tpot__blink {
  position: relative; z-index: 3;
  font-size: 12px;
  animation: dp-tpot-cursor-blink 1.2s step-end infinite;
}
@keyframes dp-tpot-cursor-blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

/* ====== 数据包粒子 ====== */
.dp-tpot__particles {
  position: absolute; inset: 0; z-index: 5; pointer-events: none;
}
.dp-tpot__pkt {
  position: absolute;
  top: 50%; left: 50%;
  width: 3px; height: 3px;
  background: #4af626;
  box-shadow: 0 0 4px rgba(74, 246, 38, 0.8), 0 0 8px rgba(74, 246, 38, 0.4);
  border-radius: 1px;
  animation: dp-tpot-pkt-fly var(--pkt-dur, 0.4s) ease-in var(--pkt-delay, 0s) both;
  transform: translate(var(--pkt-from-x, -30px), var(--pkt-from-y, -30px)) scale(var(--pkt-scale, 1));
}
@keyframes dp-tpot-pkt-fly {
  0% {
    transform: translate(var(--pkt-from-x, -30px), var(--pkt-from-y, -30px)) scale(0.3);
    opacity: 0;
  }
  20% {
    opacity: 1;
    transform: translate(
      calc(var(--pkt-from-x, -30px) * 0.4),
      calc(var(--pkt-from-y, -30px) * 0.4)
    ) scale(calc(var(--pkt-scale, 1) * 1.3));
  }
  100% {
    transform: translate(0, 0) scale(0.1);
    opacity: 0.6;
  }
}
</style>
