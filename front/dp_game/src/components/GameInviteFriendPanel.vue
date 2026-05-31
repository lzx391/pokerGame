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
      contentReadyTickTimer: null,
      flashTimer: null,
      phaseTimer: null,
      fallbackLogged: false
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
        && (this.panelPhase === 'snow' || (this.panelPhase === 'slide-in' && !this.contentReady))
        && !this.contentReady
    },
    showSnowLayer() {
      return this.useRetroInvitePanelAnimated && !this.contentReady
        && (this.panelPhase === 'slide-in' || this.panelPhase === 'snow')
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
      if (this.panelPhase === 'snow' || this.panelPhase === 'slide-in') {
        this.advanceFromLoading()
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
    if (typeof document !== 'undefined' && this.$el && this.$el.parentNode) {
      this.$el.parentNode.removeChild(this.$el)
    }
  },
  methods: {
    setPhase(next) {
      var from = this.panelPhase
      if (from === next) return
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
        if (this.contentReady) {
          this.advanceFromLoading()
        } else {
          this.setPhase('snow')
        }
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
      this.openedAt = null
      this.setPhase('idle')
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
