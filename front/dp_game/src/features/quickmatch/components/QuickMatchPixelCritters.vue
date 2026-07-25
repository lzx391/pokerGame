<template>
  <div
    class="qm-critters"
    :class="{
      'qm-critters--retro': retro,
      'qm-critters--simple': !retro,
      'qm-critters--static': staticOnly
    }"
    aria-hidden="true"
  >
    <div
      v-if="showMonster"
      class="qm-critters__stage"
    >
      <div
        class="qm-critters__monster"
        :class="{ 'qm-critters__monster--in': animActive }"
        :style="monsterPosStyle"
      >
        <div
          :key="'qm-anim-' + critterKey"
          class="qm-critters__anim"
          :class="monsterAnimClass"
          :style="monsterAnimStyle"
        >
          <svg
            v-if="retro"
            class="qm-critters__sprite"
            viewBox="0 0 16 16"
            shape-rendering="crispEdges"
          >
            <rect
              v-for="(px, pi) in spritePixels"
              :key="'px-' + pi"
              :x="px.x"
              :y="px.y"
              width="1"
              height="1"
              :fill="px.fill"
            />
          </svg>
          <span
            v-else
            class="qm-critters__dot"
            :style="{ background: dotColor }"
          />
        </div>
      </div>
    </div>

    <div
      v-if="glitchVisible"
      :key="glitchKey"
      class="qm-critters__glitch"
    >
      <span class="qm-critters__glitch-noise" />
      <span class="qm-critters__glitch-bars" />
      <span class="qm-critters__glitch-flash" />
      <span class="qm-critters__glitch-chroma" />
    </div>
  </div>
</template>

<script>
import '@/styles/dp-quick-match-critters.css'
import {
  getRetroGlitchSprite,
  pickGlitchMonsterSpriteIds,
  pickQmCritterAnim,
  pickQmCritterPosLeft,
  RETRO_GLITCH_PALETTE
} from '@shared/utils/dpRetroGlitchMonsterSprites'

var GLITCH_MS = 450
var HOLD_MS = 2000

var SIMPLE_COLORS = [
  RETRO_GLITCH_PALETTE.cyan,
  RETRO_GLITCH_PALETTE.magenta,
  RETRO_GLITCH_PALETTE.yellow,
  RETRO_GLITCH_PALETTE.red,
  RETRO_GLITCH_PALETTE.blue,
  RETRO_GLITCH_PALETTE.purple
]

function pickSimpleColor(exclude) {
  var pool = SIMPLE_COLORS.filter(function (c) { return c !== exclude })
  if (!pool.length) pool = SIMPLE_COLORS.slice()
  return pool[Math.floor(Math.random() * pool.length)]
}

function pickMonsterId(excludeId) {
  var ids = pickGlitchMonsterSpriteIds(2)
  if (excludeId && ids.length > 1 && ids[0] === excludeId) return ids[1]
  return ids[0]
}

export default {
  name: 'QuickMatchPixelCritters',
  props: {
    active: { type: Boolean, default: false },
    retro: { type: Boolean, default: false },
    ecoMode: { type: Boolean, default: false }
  },
  data: function () {
    return {
      monsterId: '',
      dotColor: SIMPLE_COLORS[0],
      anim: 'bob-y',
      animDur: '0.9',
      animDelay: '0.25',
      fightDir: 1,
      posLeft: '50%',
      critterKey: 0,
      animActive: false,
      showMonster: false,
      glitchVisible: false,
      glitchKey: 0,
      reducedMotion: false,
      glitchTimer: null,
      holdTimer: null
    }
  },
  computed: {
    staticOnly: function () {
      return this.ecoMode || this.reducedMotion
    },
    spritePixels: function () {
      var sprite = getRetroGlitchSprite(this.monsterId)
      return sprite && sprite.pixels ? sprite.pixels : []
    },
    monsterPosStyle: function () {
      return { '--qm-pos-left': this.posLeft || '50%' }
    },
    monsterAnimClass: function () {
      if (this.staticOnly || !this.animActive) return ''
      return 'qm-critters__anim--' + (this.anim || 'bob-y')
    },
    monsterAnimStyle: function () {
      var style = {
        '--anim-dur': (this.animDur || '0.9') + 's',
        '--anim-delay': (this.animDelay || '0.25') + 's'
      }
      if (this.anim === 'fight' && this.fightDir) {
        style['--fight-dir'] = String(this.fightDir)
      }
      return style
    }
  },
  watch: {
    active: function (on) {
      if (on) this.startCycle()
      else this.stopCycle()
    },
    retro: function () {
      if (this.active) this.startCycle()
    },
    staticOnly: function () {
      if (this.active) this.startCycle()
    }
  },
  mounted: function () {
    this.syncReducedMotion()
    if (typeof window !== 'undefined' && window.matchMedia) {
      var mq = window.matchMedia('(prefers-reduced-motion: reduce)')
      var self = this
      this._mqHandler = function () { self.syncReducedMotion() }
      if (mq.addEventListener) mq.addEventListener('change', this._mqHandler)
      else if (mq.addListener) mq.addListener(this._mqHandler)
    }
    if (this.active) this.startCycle()
  },
  beforeDestroy: function () {
    this.stopCycle()
    if (this._mqHandler && typeof window !== 'undefined' && window.matchMedia) {
      var mq = window.matchMedia('(prefers-reduced-motion: reduce)')
      if (mq.removeEventListener) mq.removeEventListener('change', this._mqHandler)
      else if (mq.removeListener) mq.removeListener(this._mqHandler)
    }
    this._mqHandler = null
  },
  methods: {
    syncReducedMotion: function () {
      if (typeof window === 'undefined' || !window.matchMedia) {
        this.reducedMotion = false
        return
      }
      this.reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    },
    clearTimers: function () {
      if (this.glitchTimer) {
        clearTimeout(this.glitchTimer)
        this.glitchTimer = null
      }
      if (this.holdTimer) {
        clearTimeout(this.holdTimer)
        this.holdTimer = null
      }
    },
    stopCycle: function () {
      this.clearTimers()
      this.showMonster = false
      this.animActive = false
      this.glitchVisible = false
      this.monsterId = ''
    },
    pickNextMonster: function () {
      var prevId = this.monsterId
      var prevAnim = this.anim
      if (this.retro) {
        this.monsterId = pickMonsterId(prevId)
      } else {
        this.dotColor = pickSimpleColor(this.dotColor)
      }
      var animPack = pickQmCritterAnim(prevAnim)
      this.anim = animPack.anim
      this.animDur = animPack.animDur
      this.animDelay = animPack.animDelay
      this.fightDir = animPack.fightDir || 1
      this.posLeft = pickQmCritterPosLeft()
      this.critterKey += 1
    },
    revealMonster: function () {
      var self = this
      this.animActive = false
      this.showMonster = true
      this.$nextTick(function () {
        if (!self.active || self.staticOnly || !self.showMonster) return
        self.animActive = true
      })
    },
    startCycle: function () {
      this.stopCycle()
      if (!this.active) return
      if (this.staticOnly) {
        this.pickNextMonster()
        this.revealMonster()
        this.scheduleStaticSwap()
        return
      }
      this.runGlitchPhase()
    },
    scheduleStaticSwap: function () {
      var self = this
      this.holdTimer = setTimeout(function () {
        self.holdTimer = null
        if (!self.active || !self.staticOnly) return
        self.pickNextMonster()
        self.scheduleStaticSwap()
      }, HOLD_MS)
    },
    runGlitchPhase: function () {
      var self = this
      this.showMonster = false
      this.animActive = false
      this.glitchVisible = true
      this.glitchKey += 1
      this.glitchTimer = setTimeout(function () {
        self.glitchTimer = null
        if (!self.active || self.staticOnly) return
        self.glitchVisible = false
        self.pickNextMonster()
        self.revealMonster()
        self.scheduleNextGlitch()
      }, GLITCH_MS)
    },
    scheduleNextGlitch: function () {
      var self = this
      this.holdTimer = setTimeout(function () {
        self.holdTimer = null
        if (!self.active || self.staticOnly) return
        self.runGlitchPhase()
      }, HOLD_MS)
    }
  }
}
</script>
