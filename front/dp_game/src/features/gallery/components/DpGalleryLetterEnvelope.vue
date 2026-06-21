<template>
  <div
    class="dp-gallery-envelope"
    :class="envelopeClasses"
    role="region"
    aria-label="介绍信"
  >
    <!-- Letter sheet (visible when open) -->
    <Transition
      name="dp-gallery-envelope-sheet"
      @after-leave="onSheetAfterLeave"
    >
      <div
        v-if="uiState === 'open'"
        id="dp-gallery-envelope-sheet"
        class="dp-gallery-envelope__sheet"
        role="dialog"
        aria-modal="false"
        aria-labelledby="dp-gallery-envelope-sheet-title"
      >
        <header class="dp-gallery-envelope__sheet-head">
          <h2 id="dp-gallery-envelope-sheet-title" class="dp-gallery-envelope__sheet-title">
            介绍信
          </h2>
          <button
            type="button"
            class="dp-gallery-envelope__dismiss"
            aria-label="收起介绍信"
            @click="closeSheet"
          >
            收起
          </button>
        </header>

        <dp-gallery-letter-panel
          :content="content"
          :loading="loading"
          :editable="editable"
          @update:content="$emit('update:content', $event)"
        />
      </div>
    </Transition>

    <!-- Envelope widget (hidden while sheet is open or closing) -->
    <button
      v-show="showEnvelopeWidget"
      ref="envelopeBtn"
      type="button"
      class="dp-gallery-envelope__widget"
      :class="{ 'dp-gallery-envelope__widget--entering': uiState === 'entering' }"
      :aria-expanded="uiState === 'open'"
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
      <span class="dp-gallery-envelope__hint">介绍信</span>
    </button>

  </div>
</template>

<script>
import DpGalleryLetterPanel from '@features/gallery/components/DpGalleryLetterPanel.vue'

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
    }
  },
  data() {
    return {
      uiState: 'entering',
      prefersReducedMotion: false,
      enterFallbackTimer: null,
      closeFallbackTimer: null
    }
  },
  computed: {
    sheetCloseDurationMs() {
      return this.prefersReducedMotion ? 150 : 300
    },
    showEnvelopeWidget() {
      return this.uiState === 'entering' || this.uiState === 'sealed'
    },
    envelopeClasses() {
      return {
        'dp-gallery-envelope--entering': this.uiState === 'entering',
        'dp-gallery-envelope--sealed': this.uiState === 'sealed',
        'dp-gallery-envelope--open': this.uiState === 'open',
        'dp-gallery-envelope--reduced-motion': this.prefersReducedMotion
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
      if (this.uiState === 'open') this.closeSheet()
    },
    openEnvelope() {
      if (this.uiState !== 'sealed') return
      this.uiState = 'open'
      this.$nextTick(function () {
        var sheet = this.$el && this.$el.querySelector('.dp-gallery-envelope__dismiss')
        if (sheet && sheet.focus) sheet.focus()
      }.bind(this))
    },
    closeSheet() {
      if (this.uiState !== 'open') return
      this.uiState = 'closing'
      if (this.closeFallbackTimer) window.clearTimeout(this.closeFallbackTimer)
      var durationMs = this.sheetCloseDurationMs
      this.closeFallbackTimer = window.setTimeout(function () {
        this.closeFallbackTimer = null
        this.finishCloseSheet()
      }.bind(this), durationMs + 32)
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
  z-index: 120;
  bottom: clamp(16px, 4vw, 32px);
  right: clamp(16px, 4vw, 32px);
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
  pointer-events: none;
  max-width: min(420px, calc(100vw - 32px));
}

.dp-gallery-envelope > * {
  pointer-events: auto;
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

/* ---------- Letter sheet ---------- */
.dp-gallery-envelope__sheet {
  width: min(380px, calc(100vw - 32px));
  max-height: min(70vh, 520px);
  overflow-y: auto;
  padding: 14px 16px 16px;
  border-radius: 14px;
  border: 1px solid color-mix(in srgb, var(--dp-accent, #6b5d52) 18%, transparent);
  background: linear-gradient(
    175deg,
    color-mix(in srgb, var(--dp-panel-bg, #faf8f5) 94%, var(--dp-warning, #c9a962) 6%),
    color-mix(in srgb, var(--dp-subpanel-bg, #f5f3f0) 96%, var(--dp-accent, #6b5d52) 4%)
  );
  box-shadow:
    0 12px 40px rgba(58, 51, 44, 0.18),
    0 2px 8px rgba(58, 51, 44, 0.08);
  scrollbar-width: thin;
}

.dp-gallery-envelope__sheet-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.dp-gallery-envelope__sheet-title {
  margin: 0;
  font-size: 1rem;
  font-weight: 650;
  letter-spacing: 0.04em;
  color: var(--dp-text-primary, #303133);
}

.dp-gallery-envelope__dismiss {
  flex-shrink: 0;
  min-height: 44px;
  min-width: 44px;
  padding: 8px 14px;
  border: 1px solid var(--dp-input-border, #dcdfe6);
  border-radius: 10px;
  background: var(--dp-btn-ghost-bg, rgba(255, 255, 255, 0.6));
  color: var(--dp-text-secondary, #606266);
  font-size: 14px;
  font-weight: 600;
  font-family: inherit;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background 0.2s ease;
}
.dp-gallery-envelope__dismiss:hover {
  border-color: var(--dp-accent, #6b5d52);
  color: var(--dp-accent, #6b5d52);
}
.dp-gallery-envelope__dismiss:focus-visible {
  outline: 2px solid var(--dp-accent, #6b5d52);
  outline-offset: 2px;
}

/* Panel inside sheet: remove outer card chrome */
.dp-gallery-envelope__sheet ::v-deep .dp-gallery-letter {
  margin-bottom: 0;
  border: none;
  background: transparent;
  box-shadow: none;
  border-radius: 0;
}

.dp-gallery-envelope__sheet ::v-deep .dp-gallery-letter__body {
  padding: 0;
}

/* Sheet transition – duration must match sheetCloseDurationMs (300ms / 150ms reduced) */
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

@media (prefers-reduced-motion: reduce) {
  .dp-gallery-envelope__widget--entering {
    animation: none;
  }
  .dp-gallery-envelope__body,
  .dp-gallery-envelope__dismiss {
    transition: none;
  }
  .dp-gallery-envelope-sheet-enter-active,
  .dp-gallery-envelope-sheet-leave-active {
    transition: opacity 0.15s ease;
  }
  .dp-gallery-envelope-sheet-enter,
  .dp-gallery-envelope-sheet-leave-to {
    transform: none;
  }
}

@media (max-width: 480px) {
  .dp-gallery-envelope {
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
