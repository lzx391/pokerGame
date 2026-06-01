<template>
  <div
      v-show="sceneVisible"
      class="dp-owner-terminal"
      :class="phaseClass"
      role="dialog"
      aria-modal="true"
      aria-label="房主终端"
      :aria-hidden="panelPhase === 'idle' ? 'true' : 'false'"
  >
    <div
        class="dp-owner-terminal__shell"
        :style="shellStyle"
        @animationend="onShellAnimEnd"
    >
      <header class="dp-owner-terminal__head">
        <span class="dp-owner-terminal__title">房主终端</span>
        <button
            type="button"
            class="dp-owner-terminal__close"
            aria-label="关闭"
            @click="requestClose"
        >
          ×
        </button>
      </header>
      <div
          ref="focusRoot"
          class="dp-owner-terminal__body"
          tabindex="-1"
          :class="{ 'dp-owner-terminal__body--focused': terminalFocused }"
          @click="focusTerminal"
          @keydown="onKeydown"
      >
        <div
            v-show="showSnowLayer"
            class="dp-owner-terminal__snow"
            :class="{ 'dp-owner-terminal__snow--active': snowActive }"
            aria-hidden="true"
        >
          <span class="dp-owner-terminal__snow-noise" />
          <span class="dp-owner-terminal__snow-bars" />
          <span class="dp-owner-terminal__snow-bright" />
        </div>
        <div class="dp-owner-terminal__scanlines" aria-hidden="true" />
        <div
            v-show="showFlashLayer"
            class="dp-owner-terminal__flash"
            aria-hidden="true"
        />
        <div
            class="dp-owner-terminal__content"
            :class="{ 'dp-owner-terminal__content--ready': showContentReady }"
            :aria-hidden="showContentReady ? 'false' : 'true'"
        >
          <game-owner-hub-content
              ref="hubContent"
              :active="contentActive"
              :terminal-focused="terminalFocused"
              :owner-reveal-all="ownerRevealAll"
              :demo-bot-adding="demoBotAdding"
              :demo-bot-added-tip="demoBotAddedTip"
              :maniac-bot-adding="maniacBotAdding"
              :maniac-bot-added-tip="maniacBotAddedTip"
              :tag-bot-adding="tagBotAdding"
              :tag-bot-added-tip="tagBotAddedTip"
              :lag-bot-adding="lagBotAdding"
              :lag-bot-added-tip="lagBotAddedTip"
              :nit-bot-adding="nitBotAdding"
              :nit-bot-added-tip="nitBotAddedTip"
              :call-bot-adding="callBotAdding"
              :call-bot-added-tip="callBotAddedTip"
              :llm-bot-adding="llmBotAdding"
              :llm-bot-added-tip="llmBotAddedTip"
              :llm-global-bot-adding="llmGlobalBotAdding"
              :llm-global-bot-added-tip="llmGlobalBotAddedTip"
              :custom-bot-adding="customBotAdding"
              :custom-bot-added-tip="customBotAddedTip"
              @confirm-add-npcs="$emit('confirm-add-npcs', $event)"
              @transfer-owner="$emit('transfer-owner')"
              @kick-players="$emit('kick-players', $event)"
              @toggle-reveal="$emit('toggle-reveal')"
              @request-close="requestClose"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { mapState } from 'vuex'
import GameOwnerHubContent from './GameOwnerHubContent.vue'
import { dpOwnerTerminalDevLog } from '../utils/dpOwnerTerminalDevLog'

var SNOW_MIN_HOLD_MS = 1000
var FLASH_MS = 100

export default {
  name: 'GameOwnerHubPanel',
  components: { GameOwnerHubContent },
  inject: ['dpGameView'],
  props: {
    open: { type: Boolean, default: false },
    ownerRevealAll: { type: Boolean, default: false },
    demoBotAdding: { type: Boolean, default: false },
    demoBotAddedTip: { type: String, default: '' },
    maniacBotAdding: { type: Boolean, default: false },
    maniacBotAddedTip: { type: String, default: '' },
    tagBotAdding: { type: Boolean, default: false },
    tagBotAddedTip: { type: String, default: '' },
    lagBotAdding: { type: Boolean, default: false },
    lagBotAddedTip: { type: String, default: '' },
    nitBotAdding: { type: Boolean, default: false },
    nitBotAddedTip: { type: String, default: '' },
    callBotAdding: { type: Boolean, default: false },
    callBotAddedTip: { type: String, default: '' },
    llmBotAdding: { type: Boolean, default: false },
    llmBotAddedTip: { type: String, default: '' },
    llmGlobalBotAdding: { type: Boolean, default: false },
    llmGlobalBotAddedTip: { type: String, default: '' },
    customBotAdding: { type: Boolean, default: false },
    customBotAddedTip: { type: String, default: '' }
  },
  data() {
    return {
      panelPhase: 'idle',
      openedAt: null,
      panelTop: 56,
      snowStartedAt: null,
      flashTimer: null,
      snowMinHoldTimer: null,
      snowTickTimer: null,
      terminalFocused: false,
      minHoldLogged: false
    }
  },
  computed: {
    vm() {
      return this.dpGameView
    },
    ...mapState('dpGame', ['gameUiTheme', 'ecoMode']),
    useRetroOwnerPanelWide() {
      return this.gameUiTheme === 'retro8bit'
        && this.vm
        && this.vm.viewportWidth > 600
    },
    useRetroOwnerPanelAnimated() {
      return this.useRetroOwnerPanelWide
        && !this.ecoMode
        && !(this.vm && this.vm.prefersReducedMotion)
    },
    sceneVisible() {
      return this.panelPhase !== 'idle'
    },
    snowActive() {
      return this.useRetroOwnerPanelAnimated && this.panelPhase === 'snow'
    },
    showSnowLayer() {
      return this.useRetroOwnerPanelAnimated && this.panelPhase === 'snow'
    },
    showFlashLayer() {
      return this.useRetroOwnerPanelAnimated && this.panelPhase === 'reveal-flash'
    },
    showContentReady() {
      return this.panelPhase === 'ready'
        || this.panelPhase === 'reveal-flash'
        || (!this.useRetroOwnerPanelAnimated && this.sceneVisible && this.panelPhase !== 'retract')
    },
    contentActive() {
      return this.sceneVisible && this.panelPhase !== 'retract' && this.showContentReady
    },
    phaseClass() {
      return {
        'dp-owner-terminal--slide-in': this.panelPhase === 'slide-in',
        'dp-owner-terminal--snow': this.panelPhase === 'snow',
        'dp-owner-terminal--reveal-flash': this.panelPhase === 'reveal-flash',
        'dp-owner-terminal--ready': this.panelPhase === 'ready',
        'dp-owner-terminal--retract': this.panelPhase === 'retract',
        'dp-owner-terminal--instant': !this.useRetroOwnerPanelAnimated
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
    'vm.layoutFullscreen'() {
      if (this.sceneVisible) this.refreshPanelAnchor()
    },
    'vm.viewportWidth'() {
      if (this.sceneVisible) this.refreshPanelAnchor()
    },
    useRetroOwnerPanelWide(now, was) {
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
        this.minHoldLogged = false
        this.scheduleSnowMinHoldTimer()
        this.startSnowTickTimer()
      } else if (from === 'snow') {
        this.clearSnowMinHoldTimer()
        this.clearSnowTickTimer()
      }
      this.panelPhase = next
      dpOwnerTerminalDevLog('phase', {
        from: from,
        to: next,
        animated: this.useRetroOwnerPanelAnimated,
        snowElapsedMs: this.snowStartedAt ? (Date.now() - this.snowStartedAt) : null
      })
    },
    startOpen() {
      this.openedAt = Date.now()
      this.terminalFocused = false
      this.refreshPanelAnchor()
      if (this.vm && typeof this.vm.scheduleReparentElementUiLayersIntoFullscreenRoot === 'function') {
        this.vm.scheduleReparentElementUiLayersIntoFullscreenRoot()
      }
      dpOwnerTerminalDevLog('open panel', {
        animated: this.useRetroOwnerPanelAnimated,
        ecoMode: this.ecoMode,
        prefersReducedMotion: !!(this.vm && this.vm.prefersReducedMotion)
      })
      if (this.useRetroOwnerPanelAnimated) {
        this.setPhase('slide-in')
        return
      }
      dpOwnerTerminalDevLog('instant', {
        ecoMode: this.ecoMode,
        prefersReducedMotion: !!(this.vm && this.vm.prefersReducedMotion)
      })
      this.setPhase('ready')
    },
    startClose() {
      if (this.panelPhase === 'idle' || this.panelPhase === 'retract') return
      this.terminalFocused = false
      var content = this.$refs.hubContent
      if (content && typeof content.resetStack === 'function') {
        content.resetStack()
      }
      if (this.useRetroOwnerPanelAnimated) {
        this.setPhase('retract')
        return
      }
      this.resetToIdle()
    },
    requestClose() {
      this.$emit('close')
    },
    focusTerminal() {
      this.terminalFocused = true
      if (this.$refs.focusRoot && typeof this.$refs.focusRoot.focus === 'function') {
        this.$refs.focusRoot.focus()
      }
    },
    onKeydown(event) {
      if (this.panelPhase === 'retract' || this.panelPhase === 'slide-in') return
      if (!this.terminalFocused && event.key !== 'Escape') return
      var content = this.$refs.hubContent
      if (event.key === 'Escape') {
        if (content && typeof content.handleRootEsc === 'function' && content.handleRootEsc()) {
          event.preventDefault()
          return
        }
        if (this.panelPhase === 'ready' || this.panelPhase === 'reveal-flash') {
          event.preventDefault()
          this.requestClose()
        }
        return
      }
      if (content && typeof content.handleKeydown === 'function') {
        content.handleKeydown(event)
      }
    },
    tryAdvanceFromSnow() {
      if (this.panelPhase !== 'snow' || !this.snowStartedAt) return
      var snowElapsedMs = Date.now() - this.snowStartedAt
      if (snowElapsedMs < SNOW_MIN_HOLD_MS) return
      if (!this.minHoldLogged) {
        this.minHoldLogged = true
        dpOwnerTerminalDevLog('snow min-hold elapsed', { snowElapsedMs: snowElapsedMs })
      }
      dpOwnerTerminalDevLog('snow end reason', {
        reason: 'min-hold',
        snowElapsedMs: snowElapsedMs,
        elapsedMs: this.openedAt ? (Date.now() - this.openedAt) : null
      })
      this.advanceFromLoading()
    },
    advanceFromLoading() {
      if (!this.useRetroOwnerPanelAnimated) {
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
      if (this.panelPhase === 'slide-in' && name.indexOf('dp-owner-terminal-slide-in') !== -1) {
        dpOwnerTerminalDevLog('slide complete', {
          elapsedMs: this.openedAt ? (Date.now() - this.openedAt) : null
        })
        dpOwnerTerminalDevLog('snow start', {
          minHoldMs: SNOW_MIN_HOLD_MS,
          elapsedMs: this.openedAt ? (Date.now() - this.openedAt) : null
        })
        this.setPhase('snow')
        return
      }
      if (this.panelPhase === 'retract' && name.indexOf('dp-owner-terminal-retract') !== -1) {
        this.resetToIdle()
      }
    },
    resetToIdle() {
      this.clearFlashTimer()
      this.clearSnowMinHoldTimer()
      this.clearSnowTickTimer()
      this.snowStartedAt = null
      this.minHoldLogged = false
      this.openedAt = null
      this.terminalFocused = false
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
    startSnowTickTimer() {
      this.clearSnowTickTimer()
      var self = this
      this.snowTickTimer = setInterval(function () {
        self.tryAdvanceFromSnow()
      }, 100)
    },
    clearSnowTickTimer() {
      if (this.snowTickTimer) {
        clearInterval(this.snowTickTimer)
        this.snowTickTimer = null
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
