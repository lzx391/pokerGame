<template>
  <div
      v-show="sceneVisible"
      class="dp-game-hero-hand-hologram"
      :class="phaseClass"
      role="dialog"
      aria-label="我的手牌"
      :aria-hidden="hologramPhase === 'idle' ? 'true' : 'false'"
    >
      <div
        class="dp-game-hero-hand-hologram__scene"
        :style="sceneAnchorStyle"
      >
        <div
          class="dp-game-hero-hand-hologram__volume"
          :style="volumeStyle"
          @animationend="onVolumeAnimEnd"
        >
          <div class="dp-game-hero-hand-hologram__beam-core" aria-hidden="true" />
          <div class="dp-game-hero-hand-hologram__beam-glow" aria-hidden="true" />
          <div class="dp-game-hero-hand-hologram__beam-dust" aria-hidden="true" />

          <div class="dp-game-hero-hand-hologram__projection">
            <span class="dp-game-hero-hand-hologram__ghost-title" aria-hidden="true">我的手牌</span>
            <button
              type="button"
              class="dp-game-hero-hand-hologram__close"
              aria-label="关闭手牌全息"
              @click="requestClose"
            >
              ×
            </button>
            <div class="dp-game-hero-hand-hologram__scan-sweep" aria-hidden="true" />
            <div class="dp-game-hero-hand-hologram__flash" aria-hidden="true" />
            <div
              v-show="showContent"
              class="dp-game-hero-hand-hologram__content"
              @animationend="onContentAnimEnd"
            >
              <div
                class="dp-game-hero-dock dp-game-hero-dock--in-sheet"
                :class="{ 'dp-game-hero-dock--hand-reveal': vm.stage === 'showdown' || vm.stage === 'settled' }"
              >
                <game-player-card
                    v-if="heroDockRowSafe"
                    :player="heroDockRowSafe.player"
                    :seat-index="heroDockRowSafe.seatIndex"
                    :box-style="vm.getPlayerBoxStyle(heroDockRowSafe.player, heroDockRowSafe.seatIndex)"
                    :act-index="vm.actIndex"
                    :stage="vm.stage"
                    :community-cards="vm.communityCards"
                    :community-cards-flip-complete="vm.communityCardsFlipComplete"
                    :is-owner="vm.isOwner"
                    :owner-reveal-all="vm.ownerRevealAll"
                    :my-nickname="vm.user ? vm.user.nickname : ''"
                    :hand-deal-key="vm.currentHandSeed"
                    :hole-deal-seat-order="vm.holeDealOrderFromDealer(heroDockRowSafe.seatIndex)"
                    :hole-deal-player-count="vm.holeDealPlayerCountForAnim"
                    :rival-mini="false"
                    :hero-hand-dock="true"
                    :showdown-hand-leaders="vm.showdownHandLeaderNicknames"
                    :seat-chat-text="vm.seatChatTextFor(heroDockRowSafe.player.nickname)"
                    :skip-hole-deal-animation="true"
                    :deal-reveal-stagger-sec="0.22"
                    @card-click="vm.onPlayerCardClick"
                />
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
</template>

<script>
import { mapState } from 'vuex'
import GamePlayerCard from './GamePlayerCard.vue'
import { dpHandHologramDevLog } from '../utils/dpHandHologramDevLog'

export default {
  name: 'GameHeroHandHologram',
  components: {
    GamePlayerCard
  },
  inject: ['dpGameView'],
  data() {
    return {
      hologramPhase: 'idle',
      anchorRect: null,
      flashTimer: null,
      phaseTimer: null
    }
  },
  computed: {
    vm() {
      return this.dpGameView
    },
    ...mapState('dpGame', ['showHeroHandHologram', 'gameUiTheme', 'ecoMode']),
    useRetroHandHologramWide() {
      return this.gameUiTheme === 'retro8bit'
        && this.vm.viewportWidth > 600
    },
    useRetroHandHologramAnimated() {
      return this.useRetroHandHologramWide
        && !this.ecoMode
        && !this.vm.prefersReducedMotion
    },
    sceneVisible() {
      return this.hologramPhase !== 'idle'
    },
    heroDockRowSafe() {
      var row = this.vm && this.vm.heroDockRow
      if (!row || !row.player) return null
      return row
    },
    showContent() {
      if (!this.heroDockRowSafe) return false
      if (!this.useRetroHandHologramAnimated) {
        return this.sceneVisible
      }
      return this.hologramPhase === 'materializing'
        || this.hologramPhase === 'flash'
        || this.hologramPhase === 'revealed'
    },
    phaseClass() {
      return {
        'dp-game-hero-hand-hologram--projecting': this.hologramPhase === 'projecting',
        'dp-game-hero-hand-hologram--materializing': this.hologramPhase === 'materializing',
        'dp-game-hero-hand-hologram--flash': this.hologramPhase === 'flash',
        'dp-game-hero-hand-hologram--revealed': this.hologramPhase === 'revealed',
        'dp-game-hero-hand-hologram--collapsing': this.hologramPhase === 'collapsing',
        'dp-game-hero-hand-hologram--instant': !this.useRetroHandHologramAnimated
      }
    },
    sceneAnchorStyle() {
      var anchor = this.anchorOffset()
      if (!anchor) return {}
      return {
        left: anchor.cx + 'px',
        bottom: anchor.bottom + 'px'
      }
    },
    volumeStyle() {
      var anchor = this.anchorOffset()
      if (!anchor) return {}
      var r = this.anchorRect
      var beamWidth = r && r.width ? Math.min(340, Math.max(80, r.width * 2.4)) : 120
      var contentHeight = 128
      var beamHeight = Math.min(420, Math.max(220, contentHeight + 48))
      return {
        width: beamWidth + 'px',
        height: beamHeight + 'px'
      }
    }
  },
  watch: {
    showHeroHandHologram(open) {
      dpHandHologramDevLog('store showHeroHandHologram changed', { open: !!open, phase: this.hologramPhase })
      if (open) {
        this.startOpen()
      } else if (this.hologramPhase !== 'idle') {
        this.startClose()
      }
    },
    'vm.layoutFullscreen'(now) {
      dpHandHologramDevLog('layoutFullscreen changed', {
        layoutFullscreen: !!now,
        isFullscreen: !!(this.vm && this.vm.isFullscreen),
        pseudoFullscreen: !!(this.vm && this.vm.pseudoFullscreen)
      })
      if (this.sceneVisible) this.refreshAnchor()
    },
    useRetroHandHologramWide(now, was) {
      if (now === was) return
      if (!now && this.showHeroHandHologram) {
        this.$store.commit('dpGame/SET_HERO_HAND_HOLOGRAM', false)
      }
      if (!now) {
        this.forceTeardown()
      }
    },
    'vm.viewportWidth'() {
      if (this.sceneVisible) {
        this.refreshAnchor()
      }
    },
    anchorRect() {
      if (this.sceneVisible) {
        var self = this
        this.$nextTick(function () {
          self.logVolumeStyleApplied(self.hologramPhase)
        })
      }
    }
  },
  beforeDestroy() {
    this.forceTeardown()
  },
  methods: {
    forceTeardown() {
      this.clearFlashTimer()
      this.clearPhaseTimer()
      this.hologramPhase = 'idle'
    },
    gameRootZoomFactor(root) {
      if (!root || typeof window === 'undefined') return 1
      var zoom = window.getComputedStyle(root).zoom
      if (!zoom || zoom === 'normal') return 1
      var parsed = parseFloat(zoom)
      return isFinite(parsed) && parsed > 0 ? parsed : 1
    },
    anchorOffset() {
      var r = this.anchorRect
      if (!r) return null
      var root = this.vm && this.vm.$refs && this.vm.$refs.gameRoot
      if (root && typeof window !== 'undefined') {
        var rootRect = root.getBoundingClientRect()
        var zoom = this.gameRootZoomFactor(root)
        return {
          cx: (r.left - rootRect.left + r.width / 2) / zoom,
          bottom: (rootRect.bottom - r.top) / zoom,
          zoom: zoom,
          portal: 'gameRoot'
        }
      }
      if (typeof window === 'undefined') return null
      return {
        cx: r.left + r.width / 2,
        bottom: window.innerHeight - r.top,
        zoom: 1,
        portal: 'viewport'
      }
    },
    logVolumeStyleApplied(phase) {
      var anchor = this.anchorOffset()
      var style = this.volumeStyle
      var volumeEl = this.$el && this.$el.querySelector
        ? this.$el.querySelector('.dp-game-hero-hand-hologram__volume')
        : null
      var projectionEl = this.$el && this.$el.querySelector
        ? this.$el.querySelector('.dp-game-hero-hand-hologram__projection')
        : null
      var computed = null
      if (volumeEl && typeof window !== 'undefined') {
        var cs = window.getComputedStyle(volumeEl)
        computed = {
          position: cs.position,
          left: cs.left,
          bottom: cs.bottom,
          width: cs.width,
          height: cs.height,
          opacity: cs.opacity,
          transform: cs.transform,
          visibility: cs.visibility,
          zIndex: cs.zIndex
        }
      }
      dpHandHologramDevLog('volume style applied', {
        phase: phase || this.hologramPhase,
        volumeWidth: style.width,
        volumeHeight: style.height,
        anchorCx: anchor && anchor.cx,
        anchorBottom: anchor && anchor.bottom,
        portal: anchor && anchor.portal,
        zoom: anchor && anchor.zoom,
        projectionPresent: !!projectionEl,
        computed: computed
      })
    },
    isShowing() {
      return this.hologramPhase !== 'idle'
    },
    refreshAnchor() {
      this.anchorRect = this.findAnchorRect()
    },
    visibleAnchorRect(el) {
      if (!el) return null
      var rect = el.getBoundingClientRect()
      if (rect.width <= 0 || rect.height <= 0) return null
      var style = window.getComputedStyle(el)
      if (style.display === 'none' || style.visibility === 'hidden') return null
      return rect
    },
    defaultAnchorRect() {
      if (typeof window === 'undefined') return null
      var vm = this.vm
      var root = vm && vm.$refs && vm.$refs.gameRoot
      var rootRect = root ? root.getBoundingClientRect() : null
      var footerEl = document.querySelector('.dp-game-mobile-hero-bar')
        || document.querySelector('.dp-game-layout__footer')
      if (footerEl) {
        var footerRect = this.visibleAnchorRect(footerEl)
        if (footerRect) {
          var cx = footerRect.left + footerRect.width / 2
          return {
            left: cx - 44,
            top: footerRect.top,
            width: 88,
            height: 44,
            right: cx + 44,
            bottom: footerRect.top + 44
          }
        }
      }
      var w = rootRect ? rootRect.width : window.innerWidth
      var bottom = rootRect ? rootRect.bottom : window.innerHeight
      var leftBase = rootRect ? rootRect.left : 0
      var cxDefault = leftBase + w / 2
      return {
        left: cxDefault - 44,
        top: bottom - 56,
        width: 88,
        height: 44,
        right: cxDefault + 44,
        bottom: bottom - 12
      }
    },
    findAnchorRect() {
      if (typeof document === 'undefined') {
        var fallbackDoc = this.defaultAnchorRect()
        dpHandHologramDevLog('anchor fallback: document unavailable', { hasRect: !!fallbackDoc })
        return fallbackDoc
      }
      var vm = this.vm
      if (vm && vm.layoutFullscreen) {
        var mobileBtn = document.querySelector('.dp-game-mobile-hero-bar [data-dp-hero-deal-target]')
        var mobileRect = this.visibleAnchorRect(mobileBtn)
        if (mobileRect) {
          dpHandHologramDevLog('anchor found: mobile-hero-bar button', {
            left: mobileRect.left,
            top: mobileRect.top,
            width: mobileRect.width,
            height: mobileRect.height
          })
          return mobileRect
        }
      }
      var buttons = document.querySelectorAll('[data-dp-hero-deal-target]')
      for (var i = buttons.length - 1; i >= 0; i--) {
        var rect = this.visibleAnchorRect(buttons[i])
        if (rect) {
          dpHandHologramDevLog('anchor found: data-dp-hero-deal-target', {
            index: i,
            left: rect.left,
            top: rect.top,
            width: rect.width,
            height: rect.height
          })
          return rect
        }
      }
      var fallback = this.defaultAnchorRect()
      dpHandHologramDevLog('anchor fallback: default footer coords', {
        left: fallback && fallback.left,
        top: fallback && fallback.top,
        width: fallback && fallback.width,
        height: fallback && fallback.height
      })
      return fallback
    },
    clearFlashTimer() {
      if (this.flashTimer) {
        clearTimeout(this.flashTimer)
        this.flashTimer = null
      }
    },
    clearPhaseTimer() {
      if (this.phaseTimer) {
        clearTimeout(this.phaseTimer)
        this.phaseTimer = null
      }
    },
    setPhase(next, reason) {
      if (this.hologramPhase === next) return
      dpHandHologramDevLog('phase transition', {
        from: this.hologramPhase,
        to: next,
        reason: reason || 'unspecified'
      })
      this.hologramPhase = next
      if (next === 'revealed') {
        var self = this
        this.$nextTick(function () {
          self.logVolumeStyleApplied('revealed')
        })
      }
    },
    fallbackToHandSheet(reason) {
      dpHandHologramDevLog('fallback sheet from hologram', { reason: reason || 'unspecified' })
      this.setPhase('idle', reason || 'fallbackToHandSheet')
      this.clearFlashTimer()
      this.clearPhaseTimer()
      this.$store.commit('dpGame/SET_HERO_HAND_HOLOGRAM', false)
      this.$store.commit('dpGame/SET_MOBILE_SHEETS', { showMobileHandSheet: true })
    },
    schedulePhaseRevealFallback() {
      var self = this
      this.clearPhaseTimer()
      this.phaseTimer = setTimeout(function () {
        self.phaseTimer = null
        if (!self.showHeroHandHologram) return
        if (self.hologramPhase === 'projecting'
          || self.hologramPhase === 'materializing'
          || self.hologramPhase === 'flash') {
          self.setPhase('revealed', 'phase reveal fallback timer')
        }
      }, 900)
    },
    startOpen() {
      try {
        dpHandHologramDevLog('startOpen', {
          useRetroHandHologramWide: this.useRetroHandHologramWide,
          heroDockRowPresent: !!this.heroDockRowSafe,
          phase: this.hologramPhase,
          animated: this.useRetroHandHologramAnimated,
          layoutFullscreen: !!(this.vm && this.vm.layoutFullscreen),
          isFullscreen: !!(this.vm && this.vm.isFullscreen)
        })
        if (!this.useRetroHandHologramWide || !this.heroDockRowSafe) {
          this.fallbackToHandSheet(
            !this.useRetroHandHologramWide
              ? 'useRetroHandHologramWide false'
              : 'heroDockRow null'
          )
          return
        }
        if (this.hologramPhase === 'collapsing') {
          dpHandHologramDevLog('startOpen: interrupt collapsing to reopen')
          this.clearFlashTimer()
          this.clearPhaseTimer()
        }
        this.refreshAnchor()
        if (!this.anchorRect) {
          this.fallbackToHandSheet('anchorRect null after refresh')
          return
        }
        this.clearFlashTimer()
        this.clearPhaseTimer()
        if (!this.useRetroHandHologramAnimated) {
          this.setPhase('revealed', 'instant open (eco/reduced-motion)')
          return
        }
        if (this.hologramPhase === 'revealed') {
          dpHandHologramDevLog('startOpen skipped: already revealed')
          return
        }
        this.setPhase('projecting', 'animated open')
        this.schedulePhaseRevealFallback()
        var self = this
        this.$nextTick(function () {
          self.logVolumeStyleApplied('projecting')
        })
      } catch (e) {
        dpHandHologramDevLog('startOpen threw', { error: String(e && e.message ? e.message : e) })
        if (process.env.NODE_ENV !== 'production') {
          console.warn('[dp-game] hologram startOpen failed', e)
        }
        this.fallbackToHandSheet('startOpen exception')
      }
    },
    startClose() {
      dpHandHologramDevLog('startClose', { phase: this.hologramPhase, animated: this.useRetroHandHologramAnimated })
      this.clearFlashTimer()
      this.clearPhaseTimer()
      if (!this.useRetroHandHologramAnimated) {
        this.setPhase('idle', 'instant close')
        return
      }
      if (this.hologramPhase === 'idle' || this.hologramPhase === 'collapsing') return
      this.setPhase('collapsing', 'animated close')
    },
    requestClose() {
      dpHandHologramDevLog('requestClose (close button)')
      this.$store.commit('dpGame/SET_HERO_HAND_HOLOGRAM', false)
    },
    onVolumeAnimEnd(e) {
      if (e.target !== e.currentTarget) return
      var name = e.animationName || ''
      if (this.hologramPhase === 'projecting' && name.indexOf('dp-retro-hand-hologram-beam-expand') !== -1) {
        this.setPhase('materializing', 'beam expand animation ended')
        return
      }
      if (this.hologramPhase === 'collapsing' && name.indexOf('dp-retro-hand-hologram-beam-collapse') !== -1) {
        this.clearPhaseTimer()
        this.setPhase('idle', 'beam collapse animation ended')
      }
    },
    onContentAnimEnd(e) {
      if (e.target !== e.currentTarget) return
      var name = e.animationName || ''
      if (this.hologramPhase === 'materializing' && name.indexOf('dp-retro-hand-hologram-content-scan') !== -1) {
        this.setPhase('flash', 'content scan animation ended')
        var self = this
        this.flashTimer = setTimeout(function () {
          self.flashTimer = null
          if (self.hologramPhase === 'flash') {
            self.clearPhaseTimer()
            self.setPhase('revealed', 'flash timer elapsed')
          }
        }, 100)
      }
    }
  }
}
</script>
