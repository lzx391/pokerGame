<template>
  <div
      class="dp-retro-glitch-monster"
      :class="{ 'dp-retro-glitch-monster--debug': debugMode }"
      aria-hidden="true"
  >
    <div
        class="dp-retro-glitch-monster__table-glitch"
        :class="{ 'dp-retro-glitch-monster__table-glitch--burst': tableGlitching }"
        :style="tableGlitchClipStyle"
    >
      <span class="dp-retro-glitch-monster__noise" />
      <span class="dp-retro-glitch-monster__bars" />
    </div>
    <div
        v-show="visible"
        ref="flash"
        class="dp-retro-glitch-monster__flash"
        :class="{ 'dp-retro-glitch-monster__flash--in': fadingIn }"
        :style="{ left: anchor.left, top: anchor.top }"
    >
      <svg
          ref="sprite"
          class="dp-retro-glitch-monster__sprite"
          viewBox="0 0 16 16"
          shape-rendering="crispEdges"
      >
        <g v-if="variant === 'slime'">
          <rect fill="#ff4dd8" x="4" y="10" width="8" height="4" />
          <rect fill="#f0f4ff" x="3" y="7" width="10" height="4" />
          <rect fill="#0a0c0e" x="5" y="8" width="2" height="2" />
          <rect fill="#0a0c0e" x="9" y="8" width="2" height="2" />
        </g>
        <g v-else-if="variant === 'eye'">
          <rect fill="#00e5ff" x="2" y="4" width="12" height="10" />
          <rect fill="#0a0c0e" x="4" y="6" width="4" height="4" />
          <rect fill="#f0f4ff" x="5" y="7" width="2" height="2" />
          <rect fill="#0a0c0e" x="10" y="7" width="2" height="3" />
        </g>
        <g v-else-if="variant === 'block'">
          <rect fill="#ff4dd8" x="3" y="5" width="10" height="9" />
          <rect fill="#f0f4ff" x="5" y="7" width="2" height="2" />
          <rect fill="#f0f4ff" x="9" y="7" width="2" height="2" />
          <rect fill="#0a0c0e" x="6" y="11" width="4" height="1" />
        </g>
        <g v-else>
          <rect fill="#f0f4ff" x="7" y="2" width="2" height="4" />
          <rect fill="#00e5ff" x="4" y="6" width="8" height="7" />
          <rect fill="#0a0c0e" x="5" y="8" width="2" height="2" />
          <rect fill="#0a0c0e" x="9" y="8" width="2" height="2" />
        </g>
      </svg>
    </div>
  </div>
</template>

<script>
import { retroGlitchMonsterFeltAnchor } from '../utils/dpRetroTableFxGeometry'
import {
  dpRetroMonsterGateLog,
  dpRetroMonsterLog,
  retroGlitchDebugEnabled
} from '../utils/dpRetroDesktopFxDevLog'

var VARIANTS = ['slime', 'eye', 'block', 'spike']
/** Phase A: table felt glitch burst. */
var PHASE_A_MS = 0
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
      sequenceTimers: [],
      debugRafId: null,
      cycleStartMs: 0
    }
  },
  computed: {
    debugMode: function () {
      return retroGlitchDebugEnabled()
    },
    tableGlitchClipStyle: function () {
      if (!this.layout || !this.layout.clipPath) return {}
      return { clipPath: this.layout.clipPath, '--dp-table-polygon': this.layout.clipPath }
    }
  },
  watch: {
    glitchSeq: function (seq, prev) {
      dpRetroMonsterLog('seq-watcher', { seq: seq, prev: prev, animated: this.animated })
      if (seq > 0 && seq !== prev) this.runGlitchSequence()
    }
  },
  mounted: function () {
    dpRetroMonsterGateLog({
      event: 'monster-mounted',
      animated: this.animated,
      glitchSeq: this.glitchSeq,
      hasLayoutCenter: !!(this.layout && this.layout.center)
    })
    dpRetroMonsterLog('monster-mounted', {
      glitchSeq: this.glitchSeq,
      animated: this.animated,
      hasLayout: !!(this.layout && this.layout.center),
      clipPath: this.layout && this.layout.clipPath
    })
    if (this.glitchSeq > 0 && this.animated) this.runGlitchSequence()
  },
  beforeDestroy: function () {
    this.clearSequenceTimers()
    this.stopDebugVisibilityTrace()
  },
  methods: {
    clearSequenceTimers: function () {
      var timers = this.sequenceTimers
      if (!timers || !timers.length) {
        this.sequenceTimers = []
        return
      }
      for (var i = 0; i < timers.length; i++) clearTimeout(timers[i])
      this.sequenceTimers = []
    },
    schedule: function (fn, ms) {
      var self = this
      if (!this.sequenceTimers) this.sequenceTimers = []
      var id = setTimeout(function () {
        var list = self.sequenceTimers
        if (!list) return
        var idx = list.indexOf(id)
        if (idx !== -1) list.splice(idx, 1)
        fn()
      }, ms)
      this.sequenceTimers.push(id)
      return id
    },
    logRenderState: function (phase, extra) {
      var root = this.$el
      var glitchEl = root && root.querySelector('.dp-retro-glitch-monster__table-glitch')
      var flashEl = this.$refs.flash
      var spriteEl = this.$refs.sprite
      var payload = {
        phase: phase,
        tableGlitching: this.tableGlitching,
        visible: this.visible,
        fadingIn: this.fadingIn,
        variant: this.variant,
        anchor: this.anchor,
        glitchLayer: glitchEl ? this.measureEl(glitchEl) : null,
        flashLayer: flashEl ? this.measureEl(flashEl) : null,
        spriteMounted: !!(spriteEl && spriteEl.isConnected),
        spriteChildCount: spriteEl ? spriteEl.childNodes.length : 0
      }
      if (extra) {
        for (var k in extra) payload[k] = extra[k]
      }
      dpRetroMonsterLog('render-state', payload)
    },
    measureEl: function (el) {
      if (!el || typeof window === 'undefined') return null
      var cs = window.getComputedStyle(el)
      var rect = el.getBoundingClientRect()
      return {
        w: Math.round(rect.width),
        h: Math.round(rect.height),
        opacity: cs.opacity,
        zIndex: cs.zIndex,
        clipPath: cs.clipPath && cs.clipPath !== 'none' ? cs.clipPath : null,
        display: cs.display,
        visibility: cs.visibility
      }
    },
    stopDebugVisibilityTrace: function () {
      if (this.debugRafId) {
        cancelAnimationFrame(this.debugRafId)
        this.debugRafId = null
      }
    },
    startDebugVisibilityTrace: function (untilMs) {
      if (!this.debugMode || typeof window === 'undefined') return
      var self = this
      var start = performance.now()
      var tick = function () {
        if (performance.now() - start > untilMs) {
          self.debugRafId = null
          return
        }
        self.logRenderState('debug-frame', { elapsedMs: Math.round(performance.now() - self.cycleStartMs) })
        self.debugRafId = requestAnimationFrame(tick)
      }
      this.stopDebugVisibilityTrace()
      this.debugRafId = requestAnimationFrame(tick)
    },
    runGlitchSequence: function () {
      if (!this.animated) {
        dpRetroMonsterLog('abort', { reason: 'not-animated', glitchSeq: this.glitchSeq })
        return
      }
      if (!this.layout || !this.layout.center) {
        dpRetroMonsterLog('abort', {
          reason: 'missing-layout',
          animated: this.animated,
          hasLayout: !!this.layout,
          hasCenter: !!(this.layout && this.layout.center),
          clipPath: this.layout && this.layout.clipPath
        })
        return
      }
      var self = this
      this.clearSequenceTimers()
      this.stopDebugVisibilityTrace()
      this.cycleStartMs = typeof performance !== 'undefined' ? performance.now() : Date.now()
      this.tableGlitching = true
      this.visible = false
      this.fadingIn = false
      dpRetroMonsterLog('phase-a', {
        glitchSeq: this.glitchSeq,
        atMs: PHASE_A_MS,
        recoveryMs: CYCLE_RECOVERY_MS
      })
      this.$nextTick(function () {
        self.logRenderState('phase-a-dom')
      })
      this.schedule(function () {
        self.variant = VARIANTS[Math.floor(Math.random() * VARIANTS.length)]
        self.anchor = retroGlitchMonsterFeltAnchor(self.layout)
        self.visible = true
        self.fadingIn = false
        self.$nextTick(function () {
          self.fadingIn = true
          dpRetroMonsterLog('phase-b', {
            glitchSeq: self.glitchSeq,
            atMs: MONSTER_APPEAR_MS,
            variant: self.variant,
            anchor: self.anchor,
            visible: self.visible,
            fadingIn: self.fadingIn
          })
          self.$nextTick(function () {
            self.logRenderState('phase-b-dom')
            self.startDebugVisibilityTrace(MONSTER_HOLD_MS)
          })
        })
      }, MONSTER_APPEAR_MS)
      this.schedule(function () {
        self.fadingIn = false
        dpRetroMonsterLog('phase-c', {
          glitchSeq: self.glitchSeq,
          atMs: MONSTER_APPEAR_MS + MONSTER_HOLD_MS,
          holdMs: MONSTER_HOLD_MS
        })
        self.logRenderState('phase-c-dom')
        self.stopDebugVisibilityTrace()
      }, MONSTER_APPEAR_MS + MONSTER_HOLD_MS)
      this.schedule(function () {
        self.tableGlitching = false
        self.visible = false
        self.fadingIn = false
        dpRetroMonsterLog('phase-d', {
          glitchSeq: self.glitchSeq,
          atMs: CYCLE_RECOVERY_MS
        })
        self.$nextTick(function () {
          self.logRenderState('phase-d-dom')
        })
      }, CYCLE_RECOVERY_MS)
    }
  }
}
</script>
