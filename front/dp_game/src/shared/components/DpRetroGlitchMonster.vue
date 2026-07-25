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
        v-for="(monster, idx) in monsters"
        v-show="visible"
        :key="'glitch-monster-' + idx + '-' + monster.id"
        class="dp-retro-glitch-monster__flash"
        :class="{ 'dp-retro-glitch-monster__flash--in': fadingIn }"
        :style="monsterFlashStyle(monster, idx)"
    >
      <div
          class="dp-retro-glitch-monster__anim"
          :class="monsterAnimClass(monster)"
          :style="monsterAnimStyle(monster, idx)"
      >
        <svg
            class="dp-retro-glitch-monster__sprite"
            viewBox="0 0 16 16"
            shape-rendering="crispEdges"
        >
          <rect
              v-for="(px, pi) in spritePixels(monster.id)"
              :key="monster.id + '-px-' + pi"
              :x="px.x"
              :y="px.y"
              width="1"
              height="1"
              :fill="px.fill"
          />
        </svg>
      </div>
    </div>
  </div>
</template>

<script>
import {
  getRetroGlitchSprite,
  planGlitchMonsterBurst
} from '@shared/utils/dpRetroGlitchMonsterSprites'
import {
  dpRetroMonsterGateLog,
  dpRetroMonsterLog,
  retroGlitchDebugEnabled
} from '@features/room/utils/dpRetroDesktopFxDevLog'

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
      monsters: [],
      burstCount: 0,
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
    },
    tableGlitching: function (active) {
      this.$emit('table-edge-alert', !!active)
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
    this.$emit('table-edge-alert', false)
  },
  methods: {
    spritePixels: function (id) {
      var sprite = getRetroGlitchSprite(id)
      return sprite && sprite.pixels ? sprite.pixels : []
    },
    monsterFlashStyle: function (monster, idx) {
      var anchor = monster.anchor || { left: '50%', top: '91%' }
      return {
        left: anchor.left,
        top: anchor.top,
        '--monster-i': String(idx)
      }
    },
    monsterAnimClass: function (monster) {
      var anim = monster.anim || 'bob-y'
      return 'dp-retro-glitch-monster__anim--' + anim
    },
    monsterAnimStyle: function (monster, idx) {
      var style = {
        '--anim-dur': (monster.animDur || '0.9') + 's',
        '--anim-delay': (monster.animDelay || '0.25') + 's'
      }
      if (monster.anim === 'fight' && monster.fightDir) {
        style['--fight-dir'] = String(monster.fightDir)
      }
      style['--monster-i'] = String(idx)
      return style
    },
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
      var flashEls = root ? root.querySelectorAll('.dp-retro-glitch-monster__flash') : []
      var payload = {
        phase: phase,
        tableGlitching: this.tableGlitching,
        visible: this.visible,
        fadingIn: this.fadingIn,
        burstCount: this.burstCount,
        monsters: this.monsters,
        glitchLayer: glitchEl ? this.measureEl(glitchEl) : null,
        flashCount: flashEls ? flashEls.length : 0
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
      this.monsters = []
      this.burstCount = 0
      dpRetroMonsterLog('phase-a', {
        glitchSeq: this.glitchSeq,
        atMs: PHASE_A_MS,
        recoveryMs: CYCLE_RECOVERY_MS
      })
      this.$nextTick(function () {
        self.logRenderState('phase-a-dom')
      })
      this.schedule(function () {
        var burst = planGlitchMonsterBurst(self.layout)
        self.burstCount = burst.count
        self.monsters = burst.monsters
        self.visible = true
        self.fadingIn = false
        self.$nextTick(function () {
          self.fadingIn = true
          dpRetroMonsterLog('phase-b', {
            glitchSeq: self.glitchSeq,
            atMs: MONSTER_APPEAR_MS,
            burstCount: self.burstCount,
            monsters: self.monsters,
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
        self.monsters = []
        self.burstCount = 0
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
