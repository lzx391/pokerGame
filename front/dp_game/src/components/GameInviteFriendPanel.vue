<template>
  <div
      v-show="sceneVisible"
      class="dp-invite-friend-panel"
      :class="phaseClass"
      role="dialog"
      aria-modal="true"
      aria-label="向好友发送进房邀请"
      :aria-hidden="panelPhase === 'idle' ? 'true' : 'false'"
  >
    <div
        class="dp-invite-friend-panel__shell"
        :style="shellStyle"
        @animationend="onShellAnimEnd"
    >
      <header class="dp-invite-friend-panel__head">
        <span class="dp-invite-friend-panel__title">邀请好友进房</span>
        <button
            type="button"
            class="dp-invite-friend-panel__close"
            aria-label="关闭"
            @click="requestClose"
        >
          ×
        </button>
      </header>
      <div class="dp-invite-friend-panel__body">
        <div
            v-show="showSnowLayer"
            class="dp-invite-friend-panel__snow"
            :class="{ 'dp-invite-friend-panel__snow--active': snowActive }"
            aria-hidden="true"
        >
          <span class="dp-invite-friend-panel__snow-noise" />
          <span class="dp-invite-friend-panel__snow-bars" />
          <span class="dp-invite-friend-panel__snow-bright" />
        </div>
        <div class="dp-invite-friend-panel__scanlines" aria-hidden="true" />
        <div
            v-show="showFlashLayer"
            class="dp-invite-friend-panel__flash"
            aria-hidden="true"
        />
        <div
            class="dp-invite-friend-panel__content"
            :class="{ 'dp-invite-friend-panel__content--ready': showContentReady }"
            :aria-hidden="showContentReady ? 'false' : 'true'"
        >
          <game-invite-friend-content
              :active="contentFetchActive"
              :room-id="roomId"
              :my-user-id="myUserId"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { mapState } from 'vuex'
import GameInviteFriendContent from './GameInviteFriendContent.vue'
import { dpInviteFriendsDevLog } from '../utils/dpInviteFriendsDevLog'

var CONTENT_READY_FALLBACK_MS = 2000
var SNOW_MIN_HOLD_MS = 1000
var FLASH_MS = 100

export default {
  name: 'GameInviteFriendPanel',
  components: { GameInviteFriendContent },
  inject: ['dpGameView'],
  props: {
    open: { type: Boolean, default: false },
    roomId: { type: String, required: true },
    myUserId: { type: Number, default: 0 }
  },
  data() {
    return {
      panelPhase: 'idle',
      openedAt: null,
      nowTick: Date.now(),
      panelTop: 56,
      snowStartedAt: null,
      contentReadyTickTimer: null,
      flashTimer: null,
      phaseTimer: null,
      snowMinHoldTimer: null,
      fallbackLogged: false,
      minHoldLogged: false
    }
  },
  computed: {
    vm() {
      return this.dpGameView
    },
    ...mapState('dpGame', ['gameUiTheme', 'ecoMode']),
    ...mapState('dpMailbox', ['friendsLoading']),
    useRetroInvitePanelWide() {
      return this.gameUiTheme === 'retro8bit'
        && this.vm
        && this.vm.viewportWidth > 600
    },
    useRetroInvitePanelAnimated() {
      return this.useRetroInvitePanelWide
        && !this.ecoMode
        && !(this.vm && this.vm.prefersReducedMotion)
    },
    sceneVisible() {
      return this.panelPhase !== 'idle'
    },
    contentReady() {
      if (!this.friendsLoading) return true
      if (!this.openedAt) return false
      return (this.nowTick - this.openedAt) >= CONTENT_READY_FALLBACK_MS
    },
    snowActive() {
      return this.useRetroInvitePanelAnimated
        && this.panelPhase === 'snow'
    },
    showSnowLayer() {
      return this.useRetroInvitePanelAnimated
        && this.panelPhase === 'snow'
    },
    showFlashLayer() {
      return this.useRetroInvitePanelAnimated && this.panelPhase === 'reveal-flash'
    },
    showContentReady() {
      return this.panelPhase === 'ready'
        || this.panelPhase === 'reveal-flash'
        || (!this.useRetroInvitePanelAnimated && this.sceneVisible && this.panelPhase !== 'retract')
    },
    contentFetchActive() {
      return this.sceneVisible && this.panelPhase !== 'retract'
    },
    portalInGameRoot() {
      return !!(this.useRetroInvitePanelWide && this.vm && this.vm.$refs && this.vm.$refs.gameRoot)
    },
    phaseClass() {
      return {
        'dp-invite-friend-panel--slide-in': this.panelPhase === 'slide-in',
        'dp-invite-friend-panel--snow': this.panelPhase === 'snow',
        'dp-invite-friend-panel--reveal-flash': this.panelPhase === 'reveal-flash',
        'dp-invite-friend-panel--ready': this.panelPhase === 'ready',
        'dp-invite-friend-panel--retract': this.panelPhase === 'retract',
        'dp-invite-friend-panel--instant': !this.useRetroInvitePanelAnimated,
        'dp-invite-friend-panel--portal-root': this.portalInGameRoot
      }
    },
    shellStyle() {
      return {
        top: this.panelTop + 'px'
      }
    }
  },
  watch: {
    open(now) {
      if (now) {
        this.startOpen()
      } else if (this.panelPhase !== 'idle') {
        this.startClose()
      }
    },
    contentReady(now, was) {
      if (!now || was) return
      if (this.openedAt && this.friendsLoading) {
        dpInviteFriendsDevLog('fallback', {
          reason: '2s-cap',
          elapsedMs: this.nowTick - this.openedAt
        })
        this.fallbackLogged = true
      }
      if (this.panelPhase === 'snow') {
        this.tryAdvanceFromSnow()
      }
    },
    nowTick() {
      if (this.panelPhase === 'snow') {
        this.tryAdvanceFromSnow()
      }
    },
    'vm.layoutFullscreen'() {
      this.syncPortalMount()
      if (this.sceneVisible) this.refreshPanelAnchor()
    },
    'vm.viewportWidth'() {
      if (this.sceneVisible) this.refreshPanelAnchor()
    },
    useRetroInvitePanelWide(now, was) {
      if (now === was) return
      if (!now && this.open) {
        this.$emit('close')
      }
      if (!now) {
        this.resetToIdle()
      }
      if (now) {
        this.syncPortalMount()
      }
    }
  },
  mounted() {
    this.syncPortalMount()
    if (this.open) this.startOpen()
  },
  beforeDestroy() {
    this.clearContentReadyTickTimer()
    this.clearFlashTimer()
    this.clearPhaseTimer()
    this.clearSnowMinHoldTimer()
    if (typeof document !== 'undefined' && this.$el && this.$el.parentNode) {
      this.$el.parentNode.removeChild(this.$el)
    }
  },
  methods: {
    setPhase(next) {
      var from = this.panelPhase
      if (from === next) return
      if (next === 'snow') {
        this.snowStartedAt = Date.now()
        this.minHoldLogged = false
        this.scheduleSnowMinHoldTimer()
      } else if (from === 'snow') {
        this.clearSnowMinHoldTimer()
      }
      this.panelPhase = next
      dpInviteFriendsDevLog('phase', {
        from: from,
        to: next,
        animated: this.useRetroInvitePanelAnimated,
        contentReady: this.contentReady,
        friendsLoading: this.friendsLoading,
        elapsedMs: this.openedAt ? (Date.now() - this.openedAt) : null
      })
    },
    startOpen() {
      this.fallbackLogged = false
      this.openedAt = Date.now()
      this.nowTick = Date.now()
      this.startContentReadyTickTimer()
      this.refreshPanelAnchor()
      this.syncPortalMount()
      if (this.vm && typeof this.vm.scheduleReparentElementUiLayersIntoFullscreenRoot === 'function') {
        this.vm.scheduleReparentElementUiLayersIntoFullscreenRoot()
      }
      dpInviteFriendsDevLog('open panel', {
        animated: this.useRetroInvitePanelAnimated,
        ecoMode: this.ecoMode,
        prefersReducedMotion: !!(this.vm && this.vm.prefersReducedMotion)
      })
      if (this.useRetroInvitePanelAnimated) {
        this.setPhase('slide-in')
        return
      }
      dpInviteFriendsDevLog('instant', {
        ecoMode: this.ecoMode,
        prefersReducedMotion: !!(this.vm && this.vm.prefersReducedMotion)
      })
      this.setPhase('ready')
    },
    startClose() {
      if (this.panelPhase === 'idle' || this.panelPhase === 'retract') return
      if (this.useRetroInvitePanelAnimated) {
        this.setPhase('retract')
        return
      }
      this.resetToIdle()
    },
    requestClose() {
      this.$emit('close')
    },
    tryAdvanceFromSnow() {
      if (this.panelPhase !== 'snow' || !this.snowStartedAt) return
      var now = Date.now()
      var snowElapsedMs = now - this.snowStartedAt
      if (snowElapsedMs < SNOW_MIN_HOLD_MS) return
      if (!this.minHoldLogged) {
        this.minHoldLogged = true
        dpInviteFriendsDevLog('snow min-hold elapsed', {
          snowElapsedMs: snowElapsedMs,
          friendsLoading: this.friendsLoading,
          contentReady: this.contentReady
        })
      }
      if (!this.contentReady) return
      var reason = this.friendsLoading ? 'min-hold-and-2s-cap' : 'min-hold-and-loading-done'
      dpInviteFriendsDevLog('snow end reason', {
        reason: reason,
        snowElapsedMs: snowElapsedMs,
        friendsLoading: this.friendsLoading,
        elapsedMs: this.openedAt ? (now - this.openedAt) : null
      })
      this.advanceFromLoading()
    },
    advanceFromLoading() {
      if (!this.useRetroInvitePanelAnimated) {
        this.setPhase('ready')
        return
      }
      this.setPhase('reveal-flash')
      this.clearFlashTimer()
      var self = this
      this.flashTimer = setTimeout(function () {
        self.setPhase('ready')
      }, FLASH_MS)
    },
    onShellAnimEnd(event) {
      if (!event || !event.animationName) return
      var name = event.animationName
      if (this.panelPhase === 'slide-in' && name.indexOf('dp-invite-friend-panel-slide-in') !== -1) {
        dpInviteFriendsDevLog('slide complete', {
          contentReady: this.contentReady,
          friendsLoading: this.friendsLoading,
          elapsedMs: this.openedAt ? (Date.now() - this.openedAt) : null
        })
        dpInviteFriendsDevLog('snow start', {
          friendsLoading: this.friendsLoading,
          contentReady: this.contentReady,
          minHoldMs: SNOW_MIN_HOLD_MS,
          elapsedMs: this.openedAt ? (Date.now() - this.openedAt) : null
        })
        this.setPhase('snow')
        return
      }
      if (this.panelPhase === 'retract' && name.indexOf('dp-invite-friend-panel-retract') !== -1) {
        this.resetToIdle()
      }
    },
    resetToIdle() {
      this.clearContentReadyTickTimer()
      this.clearFlashTimer()
      this.clearPhaseTimer()
      this.clearSnowMinHoldTimer()
      this.snowStartedAt = null
      this.minHoldLogged = false
      this.openedAt = null
      this.setPhase('idle')
    },
    scheduleSnowMinHoldTimer() {
      this.clearSnowMinHoldTimer()
      var self = this
      this.snowMinHoldTimer = setTimeout(function () {
        self.tryAdvanceFromSnow()
      }, SNOW_MIN_HOLD_MS)
    },
    clearSnowMinHoldTimer() {
      if (this.snowMinHoldTimer) {
        clearTimeout(this.snowMinHoldTimer)
        this.snowMinHoldTimer = null
      }
    },
    startContentReadyTickTimer() {
      this.clearContentReadyTickTimer()
      var self = this
      this.contentReadyTickTimer = setInterval(function () {
        self.nowTick = Date.now()
      }, 100)
    },
    clearContentReadyTickTimer() {
      if (this.contentReadyTickTimer) {
        clearInterval(this.contentReadyTickTimer)
        this.contentReadyTickTimer = null
      }
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
    getPortalTarget() {
      if (!this.useRetroInvitePanelWide) return null
      var vm = this.vm
      if (vm && vm.$refs && vm.$refs.gameRoot) {
        return vm.$refs.gameRoot
      }
      return null
    },
    portalTargetLabel() {
      var target = this.getPortalTarget()
      if (!target) return 'none'
      var vm = this.vm
      if (vm && vm.$refs && vm.$refs.gameRoot && target === vm.$refs.gameRoot) return 'gameRoot'
      return 'body'
    },
    syncPortalMount() {
      if (!this.$el) return
      var target = this.getPortalTarget()
      if (!target) return
      if (this.$el.parentNode !== target || target.lastElementChild !== this.$el) {
        try {
          target.appendChild(this.$el)
          dpInviteFriendsDevLog('Teleport', {
            portalTarget: this.portalTargetLabel()
          })
        } catch (e) {
          if (process.env.NODE_ENV !== 'production') {
            console.warn('[dp-game] invite panel portal mount failed', e)
          }
        }
      }
    },
    gameRootZoomFactor(root) {
      if (!root || typeof window === 'undefined') return 1
      var zoom = window.getComputedStyle(root).zoom
      if (!zoom || zoom === 'normal') return 1
      var parsed = parseFloat(zoom)
      return isFinite(parsed) && parsed > 0 ? parsed : 1
    },
    refreshPanelAnchor() {
      var root = this.vm && this.vm.$refs && this.vm.$refs.gameRoot
      if (!root || typeof window === 'undefined') {
        this.panelTop = 56
        return
      }
      var header = root.querySelector('.dp-game-layout__header')
      if (!header) {
        this.panelTop = 56
        return
      }
      var rect = header.getBoundingClientRect()
      var rootRect = root.getBoundingClientRect()
      var zoom = this.gameRootZoomFactor(root)
      this.panelTop = (rect.bottom - rootRect.top) / zoom + 8
    }
  }
}
</script>
