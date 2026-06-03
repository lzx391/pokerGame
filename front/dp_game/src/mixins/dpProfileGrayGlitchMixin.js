/**
 * retro8bit profile: gray static burst per region, then content reveal.
 * Regions: avatar | medal-1 | medal-2 | medal-3 (each honor medal card separately).
 */
import { isRetro8bitTheme, shouldSkipRetroEnterEffects } from '@/utils/dpRetroEnterGameHandoff'

export var DP_PROF_GLITCH_BURST_MS = 300
export var DP_PROF_GLITCH_REVEAL_MS = 520
export var DP_PROF_GLITCH_REGION_STAGGER_MS = 90

var REGIONS = ['avatar', 'medal-1', 'medal-2', 'medal-3']

function defaultBurstState() {
  return { avatar: false, 'medal-1': false, 'medal-2': false, 'medal-3': false }
}

function defaultRevealedState(revealed) {
  return { avatar: revealed, 'medal-1': revealed, 'medal-2': revealed, 'medal-3': revealed }
}

export function shouldSkipProfileGrayGlitch() {
  return shouldSkipRetroEnterEffects()
}

export default {
  data() {
    return {
      profGlitchBurst: defaultBurstState(),
      profGlitchRevealed: defaultRevealedState(true),
      _profGlitchTimers: []
    }
  },
  computed: {
    retroProfFx() {
      return isRetro8bitTheme() && !shouldSkipProfileGrayGlitch()
    }
  },
  beforeDestroy() {
    this.clearProfGrayGlitchTimers()
  },
  methods: {
    clearProfGrayGlitchTimers() {
      if (!this._profGlitchTimers || !this._profGlitchTimers.length) return
      this._profGlitchTimers.forEach(function (id) {
        clearTimeout(id)
      })
      this._profGlitchTimers = []
    },
    resetProfGrayGlitch() {
      this.clearProfGrayGlitchTimers()
      var revealed = !this.retroProfFx
      this.profGlitchBurst = defaultBurstState()
      this.profGlitchRevealed = defaultRevealedState(revealed)
    },
    profGlitchRegionClass(region) {
      if (!this.retroProfFx) return {}
      return {
        'dp-prof-glitch': true,
        'dp-prof-glitch--burst': !!this.profGlitchBurst[region],
        'dp-prof-glitch--revealed': !!this.profGlitchRevealed[region]
      }
    },
    _pushProfGlitchTimer(id) {
      if (!this._profGlitchTimers) this._profGlitchTimers = []
      this._profGlitchTimers.push(id)
    },
    /**
     * @param {string[]} regions subset of avatar|medal-1|medal-2|medal-3
     * @param {{ reset?: boolean }} [opts] reset=false keeps revealed state for regions not listed
     */
    scheduleProfGrayGlitch(regions, opts) {
      var reset = !opts || opts.reset !== false
      if (reset) {
        this.resetProfGrayGlitch()
      } else {
        this.clearProfGrayGlitchTimers()
        if (this.retroProfFx && regions && regions.length) {
          var selfReset = this
          regions.forEach(function (region) {
            if (REGIONS.indexOf(region) === -1) return
            selfReset.$set(selfReset.profGlitchRevealed, region, false)
            selfReset.$set(selfReset.profGlitchBurst, region, false)
          })
        }
      }
      if (!this.retroProfFx) return
      var list = (regions && regions.length) ? regions : REGIONS.slice()
      var self = this
      list.forEach(function (region, idx) {
        if (REGIONS.indexOf(region) === -1) return
        var startDelay = idx * DP_PROF_GLITCH_REGION_STAGGER_MS
        self._pushProfGlitchTimer(setTimeout(function () {
          self.$set(self.profGlitchRevealed, region, false)
          self.$set(self.profGlitchBurst, region, true)
          self._pushProfGlitchTimer(setTimeout(function () {
            self.$set(self.profGlitchBurst, region, false)
            self.$set(self.profGlitchRevealed, region, true)
          }, DP_PROF_GLITCH_BURST_MS))
        }, startDelay))
      })
    }
  }
}
