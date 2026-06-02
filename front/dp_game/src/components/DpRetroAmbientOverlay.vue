<template>
  <!-- Scheduler only: table-local glitch + monster live on GameRoundTable -->
  <span class="dp-retro-ambient-scheduler" aria-hidden="true" />
</template>

<script>
import {
  dpRetroMonsterGateLog,
  dpRetroMonsterLog,
  retroGlitchDebugEnabled
} from '../utils/dpRetroDesktopFxDevLog'

/** Fixed gap between cycle starts (PM: 10s rhythm). */
var CYCLE_INTERVAL_MS = 10000
/** retroGlitchDebug=1: faster manual QA without changing phase timings. */
var DEBUG_CYCLE_INTERVAL_MS = 3000

export default {
  name: 'DpRetroAmbientOverlay',
  inject: { dpGameView: { default: null } },
  props: {
    animated: { type: Boolean, default: false }
  },
  data: function () {
    return {
      glitchTimer: null,
      hiddenListener: null,
      tickCount: 0
    }
  },
  watch: {
    animated: function (on) {
      dpRetroMonsterLog('scheduler-animated', { on: on })
      if (on) this.scheduleNextGlitch()
      else this.clearGlitchSchedule()
    }
  },
  mounted: function () {
    var self = this
    if (typeof document !== 'undefined') {
      this.hiddenListener = function () {
        if (document.hidden) {
          dpRetroMonsterLog('scheduler-pause', { reason: 'document-hidden' })
          self.clearGlitchSchedule()
        } else if (self.animated) {
          dpRetroMonsterLog('scheduler-resume', { reason: 'document-visible' })
          self.scheduleNextGlitch()
        }
      }
      document.addEventListener('visibilitychange', this.hiddenListener)
    }
    dpRetroMonsterGateLog({
      event: 'scheduler-mounted',
      animated: this.animated,
      hasGameView: !!this.dpGameView,
      intervalMs: this.nextGlitchDelayMs()
    })
    dpRetroMonsterLog('scheduler-mounted', {
      animated: this.animated,
      hasGameView: !!this.dpGameView,
      intervalMs: this.nextGlitchDelayMs()
    })
    if (this.animated) this.scheduleNextGlitch()
  },
  beforeDestroy: function () {
    this.clearGlitchSchedule()
    if (this.hiddenListener && typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', this.hiddenListener)
    }
  },
  methods: {
    clearGlitchSchedule: function () {
      if (this.glitchTimer) {
        clearTimeout(this.glitchTimer)
        this.glitchTimer = null
      }
    },
    nextGlitchDelayMs: function () {
      return retroGlitchDebugEnabled() ? DEBUG_CYCLE_INTERVAL_MS : CYCLE_INTERVAL_MS
    },
    scheduleNextGlitch: function () {
      var self = this
      this.clearGlitchSchedule()
      if (!this.animated) {
        dpRetroMonsterLog('scheduler-tick-skip', { reason: 'not-animated' })
        return
      }
      if (typeof document !== 'undefined' && document.hidden) {
        dpRetroMonsterLog('scheduler-tick-skip', { reason: 'document-hidden' })
        return
      }
      var delay = this.nextGlitchDelayMs()
      if (typeof this.tickCount !== 'number' || isNaN(this.tickCount)) {
        this.tickCount = 0
      }
      this.tickCount++
      dpRetroMonsterLog('scheduler-tick', {
        tick: this.tickCount,
        delayMs: delay,
        debugInterval: retroGlitchDebugEnabled()
      })
      this.glitchTimer = setTimeout(function () {
        self.fireGlitchBurst()
        self.scheduleNextGlitch()
      }, delay)
    },
    glitchBlockReason: function () {
      var vm = this.dpGameView
      if (!vm) return 'no-dpGameView'
      if (vm.ecoMode) return 'eco-mode'
      if (vm.prefersReducedMotion) return 'prefers-reduced-motion'
      if (vm.showHeroHandHologram) return 'hero-hologram'
      var cli = vm.$refs && vm.$refs.terminalCli
      if (cli && cli.open) return 'terminal-cli'
      var popup = vm.$refs && vm.$refs.crtEventPopup
      if (popup && popup.active) return 'crt-event-popup'
      return null
    },
    fireGlitchBurst: function () {
      if (!this.animated) {
        dpRetroMonsterLog('glitch-skipped', { reason: 'not-animated' })
        return
      }
      var blockReason = this.glitchBlockReason()
      if (blockReason) {
        dpRetroMonsterLog('glitch-skipped', { reason: blockReason })
        if (blockReason === 'eco-mode') {
          dpRetroMonsterGateLog({ event: 'glitch-skipped', reason: blockReason, hint: 'disable eco mode in top bar' })
        }
        return
      }
      dpRetroMonsterLog('glitch-cycle-start', { nextInMs: this.nextGlitchDelayMs() })
      dpRetroMonsterLog('glitch-burst-emit', { nextInMs: this.nextGlitchDelayMs() })
      this.$emit('glitch-burst')
    }
  }
}
</script>
