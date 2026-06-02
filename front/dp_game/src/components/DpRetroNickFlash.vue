<template>
  <div
      v-if="visible && displayNick"
      class="dp-retro-nick-flash"
      :class="{ 'dp-retro-nick-flash--in': fadingIn }"
      aria-hidden="true"
  >
    <span class="dp-retro-nick-flash__tag">[SCAN]</span>
    <span class="dp-retro-nick-flash__nick">{{ displayNick }}</span>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import { dpDisplayNickname } from '../utils/dpDisplayNickname'
import { dpRetroDesktopFxDevLog } from '../utils/dpRetroDesktopFxDevLog'

var FLASH_MS = 3000
var MIN_GAP_MS = 8000
var MAX_GAP_MS = 15000

export default {
  name: 'DpRetroNickFlash',
  props: {
    animated: { type: Boolean, default: false }
  },
  data: function () {
    return {
      visible: false,
      fadingIn: false,
      currentNick: '',
      _cycleTimer: null,
      _hideTimer: null
    }
  },
  computed: {
    ...mapGetters('dpGame', ['retroNickReservoir']),
    displayNick: function () {
      return this.currentNick ? dpDisplayNickname(this.currentNick) : ''
    }
  },
  watch: {
    animated: function (on) {
      if (on) this.scheduleCycle()
      else this.teardownCycle()
    }
  },
  mounted: function () {
    if (this.animated) this.scheduleCycle()
  },
  beforeDestroy: function () {
    this.teardownCycle()
  },
  methods: {
    teardownCycle: function () {
      if (this._cycleTimer) {
        clearTimeout(this._cycleTimer)
        this._cycleTimer = null
      }
      if (this._hideTimer) {
        clearTimeout(this._hideTimer)
        this._hideTimer = null
      }
      this.visible = false
      this.fadingIn = false
    },
    scheduleCycle: function () {
      var self = this
      if (this._cycleTimer) {
        clearTimeout(this._cycleTimer)
        this._cycleTimer = null
      }
      if (!this.animated) return
      var gap = MIN_GAP_MS + Math.floor(Math.random() * (MAX_GAP_MS - MIN_GAP_MS))
      this._cycleTimer = setTimeout(function () {
        self.showRandomNick()
        self.scheduleCycle()
      }, gap)
    },
    pickRandomNick: function () {
      var pool = this.retroNickReservoir || []
      if (!pool.length) return ''
      return pool[Math.floor(Math.random() * pool.length)]
    },
    showRandomNick: function () {
      var nick = this.pickRandomNick()
      if (!nick) return
      var self = this
      this.currentNick = nick
      this.visible = true
      this.fadingIn = false
      this.$nextTick(function () {
        self.fadingIn = true
      })
      dpRetroDesktopFxDevLog('nick-flash', { nick: nick })
      if (this._hideTimer) clearTimeout(this._hideTimer)
      this._hideTimer = setTimeout(function () {
        self.fadingIn = false
        self._hideTimer = setTimeout(function () {
          self.visible = false
        }, 320)
      }, FLASH_MS)
    }
  }
}
</script>
