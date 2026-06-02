<template>
  <div class="dp-retro-glitch-monster" aria-hidden="true">
    <div
        class="dp-retro-glitch-monster__table-glitch"
        :class="{ 'dp-retro-glitch-monster__table-glitch--burst': tableGlitching }"
        :style="tableGlitchClipStyle"
    >
      <span class="dp-retro-glitch-monster__noise" />
      <span class="dp-retro-glitch-monster__bars" />
    </div>
    <div
        v-if="visible"
        class="dp-retro-glitch-monster__flash"
        :class="{ 'dp-retro-glitch-monster__flash--in': fadingIn }"
        :style="{ left: anchor.left, top: anchor.top }"
    >
      <svg
          class="dp-retro-glitch-monster__sprite"
          viewBox="0 0 16 16"
          width="80"
          height="80"
          shape-rendering="crispEdges"
      >
        <template v-if="variant === 'slime'">
          <rect fill="#ff4dd8" x="4" y="10" width="8" height="4" />
          <rect fill="#f0f4ff" x="3" y="7" width="10" height="4" />
          <rect fill="#0a0c0e" x="5" y="8" width="2" height="2" />
          <rect fill="#0a0c0e" x="9" y="8" width="2" height="2" />
        </template>
        <template v-else-if="variant === 'eye'">
          <rect fill="#00e5ff" x="2" y="4" width="12" height="10" />
          <rect fill="#0a0c0e" x="4" y="6" width="4" height="4" />
          <rect fill="#f0f4ff" x="5" y="7" width="2" height="2" />
          <rect fill="#0a0c0e" x="10" y="7" width="2" height="3" />
        </template>
        <template v-else-if="variant === 'block'">
          <rect fill="#ff4dd8" x="3" y="5" width="10" height="9" />
          <rect fill="#f0f4ff" x="5" y="7" width="2" height="2" />
          <rect fill="#f0f4ff" x="9" y="7" width="2" height="2" />
          <rect fill="#0a0c0e" x="6" y="11" width="4" height="1" />
        </template>
        <template v-else>
          <rect fill="#f0f4ff" x="7" y="2" width="2" height="4" />
          <rect fill="#00e5ff" x="4" y="6" width="8" height="7" />
          <rect fill="#0a0c0e" x="5" y="8" width="2" height="2" />
          <rect fill="#0a0c0e" x="9" y="8" width="2" height="2" />
        </template>
      </svg>
    </div>
  </div>
</template>

<script>
import { retroGlitchMonsterFeltAnchor } from '../utils/dpRetroTableFxGeometry'
import { dpRetroDesktopFxDevLog } from '../utils/dpRetroDesktopFxDevLog'

var VARIANTS = ['slime', 'eye', 'block', 'spike']
/** Phase B: monster cuts in after flash begins. */
var MONSTER_APPEAR_MS = 200
/** Phase C: hold ~2.5s from monster appear. */
var MONSTER_HOLD_MS = 2500
/** Phase D: glitch clears + monster hides (recovery). */
var CYCLE_RECOVERY_MS = 2800

export default {
  name: 'DpRetroGlitchMonster',
  props: {
    layout: { type: Object, default: null },
    animated: { type: Boolean, default: false },
    glitchSeq: { type: Number, default: 0 }
  },
  data: function () {
    return {
      tableGlitching: false,
      visible: false,
      fadingIn: false,
      variant: 'slime',
      anchor: { left: '50%', top: '62%' },
      _timers: []
    }
  },
  watch: {
    glitchSeq: function (seq, prev) {
      if (seq > 0 && seq !== prev) this.runGlitchSequence()
    }
  },
  computed: {
    tableGlitchClipStyle: function () {
      if (!this.layout || !this.layout.clipPath) return {}
      return { clipPath: this.layout.clipPath, '--dp-table-polygon': this.layout.clipPath }
    }
  },
  mounted: function () {
    if (this.glitchSeq > 0 && this.animated) this.runGlitchSequence()
  },
  beforeDestroy: function () {
    this.clearSequenceTimers()
  },
  methods: {
    clearSequenceTimers: function () {
      var timers = this._timers
      for (var i = 0; i < timers.length; i++) clearTimeout(timers[i])
      this._timers = []
    },
    schedule: function (fn, ms) {
      var self = this
      var id = setTimeout(function () {
        var idx = self._timers.indexOf(id)
        if (idx !== -1) self._timers.splice(idx, 1)
        fn()
      }, ms)
      this._timers.push(id)
      return id
    },
    runGlitchSequence: function () {
      if (!this.animated || !this.layout || !this.layout.center) {
        dpRetroDesktopFxDevLog('table-glitch-abort', {
          animated: this.animated,
          hasLayout: !!(this.layout && this.layout.center)
        })
        return
      }
      var self = this
      this.clearSequenceTimers()
      this.tableGlitching = true
      this.visible = false
      this.fadingIn = false
      dpRetroDesktopFxDevLog('table-glitch-start', { recoveryMs: CYCLE_RECOVERY_MS })
      this.schedule(function () {
        self.variant = VARIANTS[Math.floor(Math.random() * VARIANTS.length)]
        self.anchor = retroGlitchMonsterFeltAnchor(self.layout)
        self.visible = true
        self.fadingIn = false
        self.$nextTick(function () {
          self.fadingIn = true
        })
        dpRetroDesktopFxDevLog('glitch-monster-reveal', {
          variant: self.variant,
          anchor: self.anchor,
          atMs: MONSTER_APPEAR_MS
        })
      }, MONSTER_APPEAR_MS)
      this.schedule(function () {
        self.fadingIn = false
      }, MONSTER_APPEAR_MS + MONSTER_HOLD_MS)
      this.schedule(function () {
        self.tableGlitching = false
        self.visible = false
        self.fadingIn = false
        dpRetroDesktopFxDevLog('table-glitch-recovery', { atMs: CYCLE_RECOVERY_MS })
      }, CYCLE_RECOVERY_MS)
    }
  }
}
</script>
