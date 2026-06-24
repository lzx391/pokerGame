<template>
  <div
    v-if="showEnvelope"
    class="dp-gallery-envelope"
    :class="envelopeClasses"
    role="region"
    aria-label="介绍信"
  >
    <button
      v-if="uiState === 'opening' && !shouldSkipMotion"
      type="button"
      class="dp-gallery-envelope__skip-layer"
      aria-label="跳过开信动画"
      tabindex="-1"
      @click="skipOpening"
    ></button>

    <div
      v-if="uiState === 'opening'"
      class="dp-gallery-envelope__stage"
      aria-hidden="true"
    >
      <div
        class="dp-gallery-envelope__stage-inner"
        :class="openingStageClasses"
      >
        <span class="dp-gallery-envelope__body dp-gallery-envelope__body--stage">
          <span class="dp-gallery-envelope__flap"></span>
          <span class="dp-gallery-envelope__front"></span>
          <span class="dp-gallery-envelope__seal">
            <span class="dp-gallery-envelope__seal-inner"></span>
          </span>
        </span>
        <span class="dp-gallery-envelope__preview-letter" aria-hidden="true"></span>
      </div>
    </div>

    <div
      v-if="showBackdrop"
      class="dp-gallery-envelope__backdrop"
      aria-hidden="true"
      @click="onBackdropClick"
    ></div>

    <Transition
      name="dp-gallery-envelope-sheet"
      @after-leave="onSheetAfterLeave"
    >
      <div
        v-if="showReadingSheet"
        id="dp-gallery-envelope-sheet"
        ref="sheet"
        class="dp-gallery-envelope__sheet"
        role="dialog"
        aria-modal="true"
        aria-label="介绍信"
        tabindex="-1"
        @click.stop
      >
        <dp-gallery-letter-panel
          :content="content"
          :loading="loading"
          :editable="editable"
          :paper-unfolding="paperUnfolding"
          :paper-ready="paperReady"
          @update:content="$emit('update:content', $event)"
        />
      </div>
    </Transition>

    <button
      v-show="showEnvelopeWidget"
      ref="envelopeBtn"
      type="button"
      class="dp-gallery-envelope__widget"
      :class="{ 'dp-gallery-envelope__widget--entering': uiState === 'entering' }"
      :aria-expanded="uiState === 'reading'"
      aria-label="打开介绍信"
      :disabled="uiState === 'entering'"
      @animationend="onEnterAnimationEnd"
      @click="openEnvelope"
      @keydown.enter.prevent="openEnvelope"
      @keydown.space.prevent="openEnvelope"
    >
      <span class="dp-gallery-envelope__body" aria-hidden="true">
        <span class="dp-gallery-envelope__flap"></span>
        <span class="dp-gallery-envelope__front"></span>
        <span class="dp-gallery-envelope__seal">
          <span class="dp-gallery-envelope__seal-inner"></span>
        </span>
      </span>
      <span class="dp-gallery-envelope__hint">一封信</span>
    </button>
  </div>
</template>

<script>
import DpGalleryLetterPanel from '@features/gallery/components/DpGalleryLetterPanel.vue'

var TIMING = {
  flyToCenter: 500,
  flapOpen: 600,
  letterFlyOut: 500,
  scrollUnfold: 400,
  close: 300
}

export default {
  name: 'DpGalleryLetterEnvelope',
  components: {
    DpGalleryLetterPanel
  },
  props: {
    content: {
      type: String,
      default: ''
    },
    loading: {
      type: Boolean,
      default: false
    },
    editable: {
      type: Boolean,
      default: false
    },
    letterAuthor: {
      type: String,
      default: ''
    },
    ecoMode: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      uiState: 'entering',
      openingPhase: 'fly',
      prefersReducedMotion: false,
      skipAnimations: false,
      paperUnfolding: false,
      paperReady: false,
      enterFallbackTimer: null,
      closeFallbackTimer: null,
      openTimers: []
    }
  },
  computed: {
    hasContent() {
      return !!(this.content && String(this.content).trim())
    },
    showEnvelope() {
      if (this.loading) return false
      return this.editable || this.hasContent
    },
    shouldSkipMotion() {
      return this.prefersReducedMotion || this.ecoMode
    },
    sheetCloseDurationMs() {
      return this.shouldSkipMotion ? 150 : TIMING.close
    },
    showEnvelopeWidget() {
      return this.uiState === 'entering' || this.uiState === 'sealed'
    },
    showReadingSheet() {
      if (this.uiState === 'reading' || this.uiState === 'closing') return true
      return this.uiState === 'opening' && this.openingPhase === 'flyout'
    },
    showBackdrop() {
      if (this.uiState === 'reading' || this.uiState === 'closing') return true
      return this.uiState === 'opening' && this.openingPhase === 'flyout'
    },
    envelopeClasses() {
      return {
        'dp-gallery-envelope--entering': this.uiState === 'entering',
        'dp-gallery-envelope--sealed': this.uiState === 'sealed',
        'dp-gallery-envelope--opening': this.uiState === 'opening',
        'dp-gallery-envelope--reading': this.uiState === 'reading',
        'dp-gallery-envelope--closing': this.uiState === 'closing',
        'dp-gallery-envelope--flyout-sheet': this.uiState === 'opening' && this.openingPhase === 'flyout',
        'dp-gallery-envelope--skip': this.skipAnimations,
        'dp-gallery-envelope--reduced-motion': this.shouldSkipMotion
      }
    },
    openingStageClasses() {
      return {
        'dp-gallery-envelope__stage-inner--fly': this.openingPhase === 'fly' || this.openingPhase === 'flap' || this.openingPhase === 'flyout',
        'dp-gallery-envelope__stage-inner--flap': this.openingPhase === 'flap' || this.openingPhase === 'flyout',
        'dp-gallery-envelope__stage-inner--letter': this.openingPhase === 'flyout'
      }
    }
  },
  mounted() {
    this.prefersReducedMotion = this.detectReducedMotion()
    if (this.prefersReducedMotion) {
      this.uiState = 'sealed'
      return
    }
    this.enterFallbackTimer = window.setTimeout(function () {
      if (this.uiState === 'entering') this.uiState = 'sealed'
    }.bind(this), 1300)
    document.addEventListener('keydown', this.onDocumentKeydown)
  },
  beforeDestroy() {
    if (this.enterFallbackTimer) window.clearTimeout(this.enterFallbackTimer)
    if (this.closeFallbackTimer) window.clearTimeout(this.closeFallbackTimer)
    this.clearOpenTimers()
    document.removeEventListener('keydown', this.onDocumentKeydown)
  },
  methods: {
    detectReducedMotion() {
      try {
        return window.matchMedia('(prefers-reduced-motion: reduce)').matches
      } catch (e) {
        return false
      }
    },
    clearOpenTimers() {
      for (var i = 0; i < this.openTimers.length; i++) {
        window.clearTimeout(this.openTimers[i])
      }
      this.openTimers = []
    },
    onEnterAnimationEnd(e) {
      if (this.uiState !== 'entering') return
      if (e && e.animationName && e.animationName.indexOf('dp-gallery-envelope-float-in') === -1) return
      if (this.enterFallbackTimer) {
        window.clearTimeout(this.enterFallbackTimer)
        this.enterFallbackTimer = null
      }
      this.uiState = 'sealed'
    },
    onDocumentKeydown(e) {
      if (!e || e.key !== 'Escape') return
      if (this.uiState === 'opening') {
        this.skipOpening()
        return
      }
      if (this.uiState === 'reading') this.closeSheet()
    },
    scheduleOpeningPhases() {
      var self = this
      this.clearOpenTimers()
      this.openingPhase = 'fly'
      this.openTimers.push(window.setTimeout(function () {
        if (self.uiState !== 'opening') return
        self.openingPhase = 'flap'
      }, TIMING.flyToCenter))
      this.openTimers.push(window.setTimeout(function () {
        if (self.uiState !== 'opening') return
        self.openingPhase = 'flyout'
        self.resetPaperUnfold(false)
      }, TIMING.flyToCenter + TIMING.flapOpen))
      this.openTimers.push(window.setTimeout(function () {
        if (self.uiState !== 'opening') return
        self.enterReading(false)
      }, TIMING.flyToCenter + TIMING.flapOpen + TIMING.letterFlyOut))
    },
    openEnvelope() {
      if (this.uiState !== 'sealed') return
      if (this.shouldSkipMotion) {
        this.enterReading(true)
        return
      }
      this.skipAnimations = false
      this.uiState = 'opening'
      this.scheduleOpeningPhases()
    },
    skipOpening() {
      if (this.uiState !== 'opening') return
      if (this.openingPhase === 'flyout') {
        this.beginCloseSheet()
        return
      }
      this.enterReading(true)
    },
    beginCloseSheet() {
      this.clearOpenTimers()
      this.uiState = 'closing'
      this.paperUnfolding = false
      this.paperReady = false
      if (this.closeFallbackTimer) window.clearTimeout(this.closeFallbackTimer)
      var durationMs = this.sheetCloseDurationMs
      this.closeFallbackTimer = window.setTimeout(function () {
        this.closeFallbackTimer = null
        this.finishCloseSheet()
      }.bind(this), durationMs + 32)
    },
    enterReading(instant) {
      this.clearOpenTimers()
      this.skipAnimations = !!instant
      this.uiState = 'reading'
      if (instant || !this.paperUnfolding) {
        this.resetPaperUnfold(!!instant)
      }
      this.$nextTick(function () {
        var sheet = this.$refs.sheet
        if (sheet && sheet.focus) sheet.focus()
      }.bind(this))
    },
    onBackdropClick() {
      if (this.uiState === 'reading') {
        this.closeSheet()
        return
      }
      if (this.uiState === 'opening' && this.openingPhase === 'flyout') {
        this.beginCloseSheet()
      }
    },
    resetPaperUnfold(instant) {
      var self = this
      if (instant) {
        this.paperUnfolding = true
        this.paperReady = true
        return
      }
      this.paperUnfolding = true
      this.paperReady = false
      this.$nextTick(function () {
        window.requestAnimationFrame(function () {
          self.paperReady = true
        })
      })
    },
    closeSheet() {
      if (this.uiState !== 'reading') return
      this.beginCloseSheet()
    },
    onSheetAfterLeave() {
      if (this.closeFallbackTimer) {
        window.clearTimeout(this.closeFallbackTimer)
        this.closeFallbackTimer = null
      }
      this.finishCloseSheet()
    },
    finishCloseSheet() {
      if (this.uiState !== 'closing') return
      this.uiState = 'sealed'
      this.skipAnimations = false
      this.$nextTick(function () {
        var btn = this.$refs.envelopeBtn
        if (btn && btn.focus) btn.focus()
      }.bind(this))
    }
  }
}
</script>

<style scoped>
.dp-gallery-envelope {
  position: fixed;
  z-index: 2000;
  bottom: clamp(16px, 4vw, 32px);
  right: clamp(16px, 4vw, 32px);
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
  pointer-events: none;
  max-width: min(420px, calc(100vw - 32px));
}

.dp-gallery-envelope--reading,
.dp-gallery-envelope--closing,
.dp-gallery-envelope--opening.dp-gallery-envelope--flyout-sheet {
  inset: 0;
  align-items: center;
  justify-content: center;
  max-width: none;
  padding: clamp(12px, 3vw, 24px);
  box-sizing: border-box;
}

.dp-gallery-envelope > * {
  pointer-events: auto;
}

.dp-gallery-envelope__skip-layer {
  position: fixed;
  inset: 0;
  z-index: 2001;
  border: none;
  padding: 0;
  margin: 0;
  background: transparent;
  cursor: default;
  pointer-events: auto;
}

/* ---------- Opening stage (CSS 3D) ---------- */
.dp-gallery-envelope__stage {
  position: fixed;
  inset: 0;
  z-index: 2002;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
}

.dp-gallery-envelope__stage-inner {
  position: relative;
  perspective: 800px;
  transform-style: preserve-3d;
  transform: translate(38vw, 32vh) scale(0.72);
  opacity: 0.88;
}

.dp-gallery-envelope__stage-inner--fly {
  animation: dp-gallery-env-fly-center 500ms cubic-bezier(0.22, 1, 0.36, 1) forwards;
  will-change: transform, opacity;
}

.dp-gallery-envelope__body--stage {
  position: relative;
  display: block;
  width: 96px;
  height: 64px;
  transform-style: preserve-3d;
  filter: drop-shadow(0 8px 18px rgba(58, 51, 44, 0.28));
}

.dp-gallery-envelope__body--stage .dp-gallery-envelope__flap {
  height: 34px;
}

.dp-gallery-envelope__stage-inner--flap .dp-gallery-envelope__flap {
  animation: dp-gallery-env-flap-open 600ms ease-in-out forwards;
  will-change: transform;
}

.dp-gallery-envelope__preview-letter {
  position: absolute;
  left: 50%;
  bottom: 8px;
  width: 72px;
  height: 52px;
  margin-left: -36px;
  border-radius: 4px;
  background: var(--dp-letter-paper, var(--dp-panel-bg, #faf8f5));
  border: 1px solid var(--dp-letter-border, rgba(107, 93, 82, 0.22));
  box-shadow: 0 6px 16px rgba(58, 51, 44, 0.16);
  transform-origin: bottom center;
  transform: translateY(12px) scale(0.35) rotateX(18deg);
  opacity: 0;
  pointer-events: none;
}

.dp-gallery-envelope__stage-inner--letter .dp-gallery-envelope__preview-letter {
  animation: dp-gallery-env-letter-fly 500ms cubic-bezier(0.22, 1, 0.36, 1) forwards;
  will-change: transform, opacity;
}

@keyframes dp-gallery-env-fly-center {
  0% {
    opacity: 0.88;
    transform: translate(38vw, 32vh) scale(0.72);
  }
  100% {
    opacity: 1;
    transform: translate(0, 0) scale(1.12);
  }
}

@keyframes dp-gallery-env-flap-open {
  0% {
    transform: rotateX(0deg);
  }
  100% {
    transform: rotateX(-160deg);
  }
}

@keyframes dp-gallery-env-letter-fly {
  0% {
    opacity: 0;
    transform: translateY(12px) scale(0.35) rotateX(18deg);
  }
  100% {
    opacity: 1;
    transform: translateY(-72px) scale(1) rotateX(0deg);
  }
}

/* ---------- Envelope widget ---------- */
.dp-gallery-envelope__widget {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  min-width: 44px;
  min-height: 44px;
  padding: 4px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-family: inherit;
  -webkit-tap-highlight-color: transparent;
}
.dp-gallery-envelope__widget:disabled {
  cursor: default;
}
.dp-gallery-envelope__widget:focus-visible {
  outline: 2px solid var(--dp-accent, #6b5d52);
  outline-offset: 4px;
  border-radius: 12px;
}

.dp-gallery-envelope__widget--entering {
  animation: dp-gallery-envelope-float-in 1.1s cubic-bezier(0.22, 1, 0.36, 1) forwards;
}

.dp-gallery-envelope--reduced-motion .dp-gallery-envelope__widget--entering {
  animation: none;
}

@keyframes dp-gallery-envelope-float-in {
  0% {
    opacity: 0;
    transform: translate(120%, 120%) rotate(8deg) scale(0.72);
  }
  55% {
    opacity: 1;
    transform: translate(-6%, -4%) rotate(-2deg) scale(1.04);
  }
  78% {
    transform: translate(2%, 1%) rotate(1deg) scale(0.98);
  }
  100% {
    opacity: 1;
    transform: translate(0, 0) rotate(0deg) scale(1);
  }
}

.dp-gallery-envelope__body {
  position: relative;
  width: 72px;
  height: 48px;
  filter: drop-shadow(0 6px 14px rgba(58, 51, 44, 0.22));
  transition: transform 0.35s cubic-bezier(0.22, 1, 0.36, 1);
}

.dp-gallery-envelope__widget:hover .dp-gallery-envelope__body {
  transform: translateY(-3px) rotate(-1deg);
}

.dp-gallery-envelope__front {
  position: absolute;
  inset: 0;
  border-radius: 3px 3px 5px 5px;
  background: linear-gradient(
    168deg,
    color-mix(in srgb, var(--dp-panel-bg, #faf8f5) 88%, var(--dp-warning, #c9a962) 12%),
    color-mix(in srgb, var(--dp-subpanel-bg, #ebe6df) 90%, var(--dp-accent, #6b5d52) 10%)
  );
  border: 1px solid color-mix(in srgb, var(--dp-accent, #6b5d52) 28%, transparent);
  box-shadow: inset 0 -8px 12px rgba(58, 51, 44, 0.06);
}

.dp-gallery-envelope__flap {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 26px;
  clip-path: polygon(0 0, 50% 100%, 100% 0);
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--dp-panel-bg, #fff) 70%, var(--dp-warning, #d4b978) 30%),
    color-mix(in srgb, var(--dp-subpanel-bg, #e8e2d9) 85%, var(--dp-accent, #6b5d52) 15%)
  );
  border-top: 1px solid color-mix(in srgb, var(--dp-accent, #6b5d52) 22%, transparent);
  transform-origin: top center;
  z-index: 2;
}

.dp-gallery-envelope__seal {
  position: absolute;
  left: 50%;
  top: 52%;
  transform: translate(-50%, -50%);
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: radial-gradient(
    circle at 35% 30%,
    color-mix(in srgb, var(--dp-warning, #d4a853) 80%, #fff 20%),
    color-mix(in srgb, var(--dp-warning, #b8860b) 70%, var(--dp-accent, #6b5d52) 30%)
  );
  box-shadow:
    0 2px 6px rgba(58, 51, 44, 0.28),
    inset 0 1px 2px rgba(255, 255, 255, 0.35);
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
}

.dp-gallery-envelope__seal-inner {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--dp-accent, #6b5d52) 55%, #3a332c);
  opacity: 0.55;
}

.dp-gallery-envelope__hint {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--dp-text-muted, #909399);
  opacity: 0.85;
}

/* ---------- Reading backdrop + letter sheet ---------- */
.dp-gallery-envelope__backdrop {
  position: fixed;
  inset: 0;
  z-index: 2002;
  border: none;
  padding: 0;
  margin: 0;
  background: rgba(36, 26, 18, 0.62);
  cursor: default;
  pointer-events: auto;
  animation: dp-gallery-envelope-backdrop-in 0.28s ease forwards;
}

@keyframes dp-gallery-envelope-backdrop-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

.dp-gallery-envelope__sheet {
  position: relative;
  z-index: 2003;
  width: min(420px, calc(100vw - 32px));
  max-height: min(72vh, 560px);
  outline: none;
}

.dp-gallery-envelope__sheet:focus-visible {
  outline: none;
}

.dp-gallery-envelope__sheet ::v-deep .dp-gallery-letter {
  margin-bottom: 0;
}

.dp-gallery-envelope__sheet ::v-deep .dp-gallery-letter-paper {
  width: 100%;
  max-height: min(72vh, 560px);
}

.dp-gallery-envelope-sheet-enter-active,
.dp-gallery-envelope-sheet-leave-active {
  transition:
    opacity 0.3s ease,
    transform 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}
.dp-gallery-envelope-sheet-enter,
.dp-gallery-envelope-sheet-leave-to {
  opacity: 0;
  transform: translateY(16px) scale(0.96);
}

.dp-gallery-envelope--skip .dp-gallery-envelope__stage-inner,
.dp-gallery-envelope--skip .dp-gallery-envelope__stage-inner--fly,
.dp-gallery-envelope--skip .dp-gallery-envelope__stage-inner--flap .dp-gallery-envelope__flap,
.dp-gallery-envelope--skip .dp-gallery-envelope__stage-inner--letter .dp-gallery-envelope__preview-letter,
.dp-gallery-envelope--reduced-motion .dp-gallery-envelope__stage-inner,
.dp-gallery-envelope--reduced-motion .dp-gallery-envelope__stage-inner--fly,
.dp-gallery-envelope--reduced-motion .dp-gallery-envelope__stage-inner--flap .dp-gallery-envelope__flap,
.dp-gallery-envelope--reduced-motion .dp-gallery-envelope__stage-inner--letter .dp-gallery-envelope__preview-letter {
  animation: none !important;
}

.dp-game-root[data-dp-eco-mode='true'] .dp-gallery-envelope__stage-inner--fly,
.dp-game-root[data-dp-eco-mode='true'] .dp-gallery-envelope__stage-inner--flap .dp-gallery-envelope__flap,
.dp-game-root[data-dp-eco-mode='true'] .dp-gallery-envelope__stage-inner--letter .dp-gallery-envelope__preview-letter {
  animation: none !important;
}

@media (prefers-reduced-motion: reduce) {
  .dp-gallery-envelope__widget--entering {
    animation: none;
  }
  .dp-gallery-envelope__body {
    transition: none;
  }
  .dp-gallery-envelope__backdrop {
    animation: none;
  }
  .dp-gallery-envelope-sheet-enter-active,
  .dp-gallery-envelope-sheet-leave-active {
    transition: opacity 0.15s ease;
  }
  .dp-gallery-envelope-sheet-enter,
  .dp-gallery-envelope-sheet-leave-to {
    transform: none;
  }
  .dp-gallery-envelope__stage-inner--fly,
  .dp-gallery-envelope__stage-inner--flap .dp-gallery-envelope__flap,
  .dp-gallery-envelope__stage-inner--letter .dp-gallery-envelope__preview-letter {
    animation: none !important;
  }
}

@media (max-width: 480px) {
  .dp-gallery-envelope--sealed,
  .dp-gallery-envelope--entering {
    left: clamp(12px, 3vw, 20px);
    right: clamp(12px, 3vw, 20px);
    align-items: stretch;
    max-width: none;
  }
  .dp-gallery-envelope__widget {
    align-self: flex-end;
  }
  .dp-gallery-envelope__sheet {
    width: 100%;
  }
}
</style>
