<template>
  <!-- Scheduler only: table-local glitch + monster live on GameRoundTable -->
  <span class="dp-retro-ambient-scheduler" aria-hidden="true" />
</template>

<script>
import { dpRetroDesktopFxDevLog } from '../utils/dpRetroDesktopFxDevLog'

/** Fixed gap between cycle starts (PM: 10s rhythm). */
var CYCLE_INTERVAL_MS = 10000
/** retroGlitchDebug=1: faster manual QA without changing phase timings. */
var DEBUG_CYCLE_INTERVAL_MS = 3000

function retroGlitchDebugEnabled() {
  if (typeof localStorage === 'undefined') return false
  return localStorage.getItem('retroGlitchDebug') === '1'
}

export default {
  name: 'DpRetroAmbientOverlay',
  inject: { dpGameView: { default: null } },
  props: {
    animated: { type: Boolean, default: false }
  },
  data: function () {
    return {
      _glitchTimer: null,
      _hiddenListener: null
    }
  },
  watch: {
    animated: function (on) {
      if (on) this.scheduleNextGlitch()
      else this.clearGlitchSchedule()
    }
  },
  mounted: function () {
    var self = this
    if (typeof document !== 'undefined') {
      this._hiddenListener = function () {
        if (document.hidden) self.clearGlitchSchedule()
        else if (self.animated) self.scheduleNextGlitch()
      }
      document.addEventListener('visibilitychange', this._hiddenListener)
    }
    dpRetroDesktopFxDevLog('scheduler-mounted', {
      animated: this.animated,
      hasGameView: !!this.dpGameView
    })
    if (this.animated) this.scheduleNextGlitch()
  },
  beforeDestroy: function () {
    this.clearGlitchSchedule()
    if (this._hiddenListener && typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', this._hiddenListener)
    }
  },
  methods: {
    clearGlitchSchedule: function () {
      if (this._glitchTimer) {
        clearTimeout(this._glitchTimer)
        this._glitchTimer = null
      }
    },
    nextGlitchDelayMs: function () {
      return retroGlitchDebugEnabled() ? DEBUG_CYCLE_INTERVAL_MS : CYCLE_INTERVAL_MS
    },
    scheduleNextGlitch: function () {
      var self = this
      this.clearGlitchSchedule()
      if (!this.animated || (typeof document !== 'undefined' && document.hidden)) return
      var delay = this.nextGlitchDelayMs()
      if (retroGlitchDebugEnabled()) {
        dpRetroDesktopFxDevLog('glitch-debug-interval', { delayMs: delay })
      }
      this._glitchTimer = setTimeout(function () {
        self.fireGlitchBurst()
        self.scheduleNextGlitch()
      }, delay)
    },
    isGlitchBlocked: function () {
      var vm = this.dpGameView
      if (!vm) return true
      if (vm.showHeroHandHologram) return true
      var cli = vm.$refs && vm.$refs.terminalCli
      if (cli && cli.open) return true
      var popup = vm.$refs && vm.$refs.crtEventPopup
      if (popup && popup.active) return true
      return false
    },
    glitchBlockReason: function () {
      var vm = this.dpGameView
      if (!vm) return 'no-dpGameView'
      if (vm.showHeroHandHologram) return 'hero-hologram'
      var cli = vm.$refs && vm.$refs.terminalCli
      if (cli && cli.open) return 'terminal-cli'
      var popup = vm.$refs && vm.$refs.crtEventPopup
      if (popup && popup.active) return 'crt-event-popup'
      return null
    },
    fireGlitchBurst: function () {
      if (!this.animated) {
        dpRetroDesktopFxDevLog('glitch-skipped', { reason: 'not-animated' })
        return
      }
      var blockReason = this.glitchBlockReason()
      if (blockReason) {
        dpRetroDesktopFxDevLog('glitch-skipped', { reason: blockReason })
        return
      }
      dpRetroDesktopFxDevLog('glitch-cycle-start', { nextInMs: this.nextGlitchDelayMs() })
      this.$emit('glitch-burst')
    }
  }
}
</script>
