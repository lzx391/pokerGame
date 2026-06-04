<template>
  <div
      v-show="sceneVisible"
      class="dp-friend-chat-panel"
      :class="phaseClass"
      role="dialog"
      aria-modal="true"
      aria-label="选择好友私信"
      :aria-hidden="panelPhase === 'idle' ? 'true' : 'false'"
  >
    <div
        class="dp-friend-chat-panel__shell"
        :style="shellStyle"
        @animationend="onShellAnimEnd"
    >
      <header class="dp-friend-chat-panel__head">
        <span class="dp-friend-chat-panel__title">— FRIEND DM —</span>
        <button
            type="button"
            class="dp-friend-chat-panel__close"
            aria-label="关闭"
            @click="requestClose"
        >
          ×
        </button>
      </header>
      <div class="dp-friend-chat-panel__body">
        <div
            v-show="showSnowLayer"
            class="dp-friend-chat-panel__snow"
            :class="{ 'dp-friend-chat-panel__snow--active': snowActive }"
            aria-hidden="true"
        >
          <span class="dp-friend-chat-panel__snow-noise" />
          <span class="dp-friend-chat-panel__snow-bars" />
          <span class="dp-friend-chat-panel__snow-bright" />
        </div>
        <div class="dp-friend-chat-panel__scanlines" aria-hidden="true" />
        <div
            v-show="showFlashLayer"
            class="dp-friend-chat-panel__flash"
            aria-hidden="true"
        />
        <div
            class="dp-friend-chat-panel__content"
            :class="{ 'dp-friend-chat-panel__content--ready': showContentReady }"
            :aria-hidden="showContentReady ? 'false' : 'true'"
        >
          <game-friend-chat-picker-content
              :active="contentFetchActive"
              :my-user-id="myUserId"
              @open-chat="$emit('open-chat', $event)"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { mapState } from 'vuex'
import GameFriendChatPickerContent from './GameFriendChatPickerContent.vue'

var CONTENT_READY_FALLBACK_MS = 2000
var SNOW_MIN_HOLD_MS = 350
var FLASH_MS = 100

export default {
  name: 'GameFriendChatPanel',
  components: { GameFriendChatPickerContent },
  inject: ['dpGameView'],
  props: {
    open: { type: Boolean, default: false },
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
      snowMinHoldTimer: null
    }
  },
  computed: {
    vm() {
      return this.dpGameView
    },
    ...mapState('dpGame', ['gameUiTheme', 'ecoMode']),
    ...mapState('dpMailbox', ['friendsLoading']),
    useRetroFriendChatPanelWide() {
      return this.gameUiTheme === 'retro8bit'
        && this.vm
        && this.vm.viewportWidth > 600
    },
    useRetroFriendChatAnimated() {
      return this.useRetroFriendChatPanelWide
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
      return this.useRetroFriendChatAnimated && this.panelPhase === 'snow'
    },
    showSnowLayer() {
      return this.useRetroFriendChatAnimated && this.panelPhase === 'snow'
    },
    showFlashLayer() {
      return this.useRetroFriendChatAnimated && this.panelPhase === 'reveal-flash'
    },
    showContentReady() {
      return this.panelPhase === 'ready'
        || this.panelPhase === 'reveal-flash'
        || (!this.useRetroFriendChatAnimated && this.sceneVisible && this.panelPhase !== 'retract')
    },
    contentFetchActive() {
      return this.sceneVisible && this.panelPhase !== 'retract'
    },
    phaseClass() {
      return {
        'dp-friend-chat-panel--slide-in': this.panelPhase === 'slide-in',
        'dp-friend-chat-panel--snow': this.panelPhase === 'snow',
        'dp-friend-chat-panel--reveal-flash': this.panelPhase === 'reveal-flash',
        'dp-friend-chat-panel--ready': this.panelPhase === 'ready',
        'dp-friend-chat-panel--retract': this.panelPhase === 'retract',
        'dp-friend-chat-panel--instant': !this.useRetroFriendChatAnimated
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
      if (this.sceneVisible) this.refreshPanelAnchor()
    },
    'vm.viewportWidth'() {
      if (this.sceneVisible) this.refreshPanelAnchor()
    },
    useRetroFriendChatPanelWide(now, was) {
      if (now === was) return
      if (!now && this.open) {
        this.$emit('close')
      }
      if (!now) {
        this.forceTeardown()
      }
    }
  },
  mounted() {
    if (this.open) this.startOpen()
  },
  beforeDestroy() {
    this.forceTeardown()
  },
  methods: {
    forceTeardown() {
      this.resetToIdle()
    },
    setPhase(next) {
      var from = this.panelPhase
      if (from === next) return
      if (next === 'snow') {
        this.snowStartedAt = Date.now()
        this.scheduleSnowMinHoldTimer()
      } else if (from === 'snow') {
        this.clearSnowMinHoldTimer()
      }
      this.panelPhase = next
    },
    startOpen() {
      this.openedAt = Date.now()
      this.nowTick = Date.now()
      this.startContentReadyTickTimer()
      this.refreshPanelAnchor()
      if (this.vm && typeof this.vm.scheduleReparentElementUiLayersIntoFullscreenRoot === 'function') {
        this.vm.scheduleReparentElementUiLayersIntoFullscreenRoot()
      }
      if (this.useRetroFriendChatAnimated) {
        this.setPhase('slide-in')
        return
      }
      this.setPhase('ready')
    },
    startClose() {
      if (this.panelPhase === 'idle' || this.panelPhase === 'retract') return
      if (this.useRetroFriendChatAnimated) {
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
      var snowElapsedMs = Date.now() - this.snowStartedAt
      if (snowElapsedMs < SNOW_MIN_HOLD_MS) return
      if (!this.contentReady) return
      this.advanceFromLoading()
    },
    advanceFromLoading() {
      if (!this.useRetroFriendChatAnimated) {
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
      if (this.panelPhase === 'slide-in' && name.indexOf('dp-friend-chat-panel-slide-in') !== -1) {
        this.setPhase('snow')
        return
      }
      if (this.panelPhase === 'retract' && name.indexOf('dp-friend-chat-panel-retract') !== -1) {
        this.resetToIdle()
      }
    },
    resetToIdle() {
      this.clearContentReadyTickTimer()
      this.clearFlashTimer()
      this.clearSnowMinHoldTimer()
      this.snowStartedAt = null
      this.openedAt = null
      this.panelPhase = 'idle'
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
