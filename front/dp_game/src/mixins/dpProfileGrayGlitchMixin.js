/**
 * retro8bit profile: gray static burst per region, then content reveal.
 * Avatar: loading transition — glitch starts immediately on open, reveals when image loads.
 * Medals: optional timed burst (avatar-only flow uses startProfAvatarGlitch / revealProfAvatar).
 */
import { mapState } from 'vuex'
import { shouldSkipRetroEnterEffects } from '@/utils/dpRetroEnterGameHandoff'

export var DP_PROF_GLITCH_BURST_MS = 300
export var DP_PROF_GLITCH_REVEAL_MS = 520
export var DP_PROF_GLITCH_REGION_STAGGER_MS = 90
export var DP_PROF_AVATAR_BURST_LOOP_GAP_MS = 140

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
      _profGlitchTimers: {},
      _profAvatarGlitchActive: false
    }
  },
  computed: {
    ...mapState('dpGame', ['gameUiTheme']),
    retroProfFx() {
      return this.gameUiTheme === 'retro8bit' && !shouldSkipProfileGrayGlitch()
    }
  },
  beforeDestroy() {
    this.clearProfGrayGlitchTimers()
  },
  methods: {
    clearProfGrayGlitchTimers(regions) {
      if (!this._profGlitchTimers) this._profGlitchTimers = {}
      var toClear = regions && regions.length ? regions : REGIONS.slice()
      var self = this
      toClear.forEach(function (region) {
        if (region === 'avatar') {
          self._profAvatarGlitchActive = false
        }
        var ids = self._profGlitchTimers[region] || []
        ids.forEach(function (id) {
          clearTimeout(id)
        })
        self._profGlitchTimers[region] = []
      })
    },
    resetProfGrayGlitch() {
      this._profAvatarGlitchActive = false
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
    _pushProfGlitchTimer(region, id) {
      if (!this._profGlitchTimers) this._profGlitchTimers = {}
      if (!this._profGlitchTimers[region]) this._profGlitchTimers[region] = []
      this._profGlitchTimers[region].push(id)
    },
    /** Immediate avatar gray glitch; loops until revealProfAvatar / revealProfAvatarAfterBurst. */
    startProfAvatarGlitch() {
      if (!this.retroProfFx) {
        this.$set(this.profGlitchRevealed, 'avatar', true)
        this.$set(this.profGlitchBurst, 'avatar', false)
        return
      }
      this._profAvatarGlitchActive = true
      this.clearProfGrayGlitchTimers(['avatar'])
      this.$set(this.profGlitchRevealed, 'avatar', false)
      this.$set(this.profGlitchBurst, 'avatar', true)
      var self = this
      this._pushProfGlitchTimer('avatar', setTimeout(function () {
        if (!self._profAvatarGlitchActive) return
        self.$set(self.profGlitchBurst, 'avatar', false)
        self._scheduleProfAvatarBurstLoop()
      }, DP_PROF_GLITCH_BURST_MS))
    },
    _scheduleProfAvatarBurstLoop() {
      var self = this
      if (!this._profAvatarGlitchActive || !this.retroProfFx || this.profGlitchRevealed.avatar) {
        return
      }
      this.$set(this.profGlitchBurst, 'avatar', true)
      this._pushProfGlitchTimer('avatar', setTimeout(function () {
        if (!self._profAvatarGlitchActive) return
        self.$set(self.profGlitchBurst, 'avatar', false)
        if (!self.profGlitchRevealed.avatar) {
          self._pushProfGlitchTimer('avatar', setTimeout(function () {
            self._scheduleProfAvatarBurstLoop()
          }, DP_PROF_AVATAR_BURST_LOOP_GAP_MS))
        }
      }, DP_PROF_GLITCH_BURST_MS))
    },
    /** Reveal avatar immediately (image ready). */
    revealProfAvatar() {
      this._profAvatarGlitchActive = false
      this.clearProfGrayGlitchTimers(['avatar'])
      if (!this.retroProfFx) {
        this.$set(this.profGlitchRevealed, 'avatar', true)
        this.$set(this.profGlitchBurst, 'avatar', false)
        return
      }
      this.$set(this.profGlitchBurst, 'avatar', false)
      this.$set(this.profGlitchRevealed, 'avatar', true)
    },
    /** Letter avatar / no URL: one burst then reveal (~300ms). */
    revealProfAvatarAfterBurst() {
      if (!this.retroProfFx || this.profGlitchRevealed.avatar) {
        this.revealProfAvatar()
        return
      }
      this._profAvatarGlitchActive = false
      this.clearProfGrayGlitchTimers(['avatar'])
      this.$set(this.profGlitchRevealed, 'avatar', false)
      this.$set(this.profGlitchBurst, 'avatar', true)
      var self = this
      this._pushProfGlitchTimer('avatar', setTimeout(function () {
        self.$set(self.profGlitchBurst, 'avatar', false)
        self.$set(self.profGlitchRevealed, 'avatar', true)
      }, DP_PROF_GLITCH_BURST_MS))
    },
    /**
     * @param {string[]} regions subset of avatar|medal-1|medal-2|medal-3
     * @param {{ reset?: boolean }} [opts] reset=false keeps revealed state for regions not listed
     */
    scheduleProfGrayGlitch(regions, opts) {
      var reset = !opts || opts.reset !== false
      var list = (regions && regions.length) ? regions : REGIONS.slice()
      if (reset) {
        this.resetProfGrayGlitch()
      } else {
        this.clearProfGrayGlitchTimers(list)
        if (this.retroProfFx && list.length) {
          var selfReset = this
          list.forEach(function (region) {
            if (REGIONS.indexOf(region) === -1) return
            if (region === 'avatar' && selfReset._profAvatarGlitchActive) return
            selfReset.$set(selfReset.profGlitchRevealed, region, false)
            selfReset.$set(selfReset.profGlitchBurst, region, false)
          })
        }
      }
      if (!this.retroProfFx) return
      var self = this
      list.forEach(function (region, idx) {
        if (REGIONS.indexOf(region) === -1) return
        if (region === 'avatar' && self._profAvatarGlitchActive) return
        var startDelay = idx * DP_PROF_GLITCH_REGION_STAGGER_MS
        self._pushProfGlitchTimer(region, setTimeout(function () {
          self.$set(self.profGlitchRevealed, region, false)
          self.$set(self.profGlitchBurst, region, true)
          self._pushProfGlitchTimer(region, setTimeout(function () {
            self.$set(self.profGlitchBurst, region, false)
            self.$set(self.profGlitchRevealed, region, true)
          }, DP_PROF_GLITCH_BURST_MS))
        }, startDelay))
      })
    }
  }
}
