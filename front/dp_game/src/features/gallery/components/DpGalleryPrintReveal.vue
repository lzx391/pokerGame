<template>
  <Transition name="dp-gallery-print-overlay" @after-leave="onOverlayAfterLeave">
    <div
      v-if="visible && item"
      ref="root"
      class="dp-gallery-print"
      :class="printClasses"
      role="dialog"
      aria-modal="true"
      :aria-label="dialogAriaLabel"
      @keydown="onKeydown"
    >
      <button
        type="button"
        class="dp-gallery-print__backdrop"
        aria-label="关闭作品详情"
        tabindex="-1"
        @click="requestClose"
      ></button>

      <div class="dp-gallery-print__stage">
        <div class="dp-gallery-print__slot" aria-hidden="true">
          <span class="dp-gallery-print__slot-mouth"></span>
          <span class="dp-gallery-print__slot-shadow"></span>
        </div>

        <div class="dp-gallery-print__eject-zone">
          <article
            ref="sheet"
            class="dp-gallery-print__sheet"
            :class="{ 'dp-gallery-print__sheet--ejecting': uiPhase === 'ejecting' }"
            @animationend="onSheetAnimationEnd"
          >
            <div class="dp-gallery-print__photo-area">
              <img
                v-if="previewSrc"
                class="dp-gallery-print__img dp-gallery-print__img--preview"
                :class="{ 'dp-gallery-print__img--hidden': fullReady }"
                :src="previewSrc"
                :alt="item.caption || '画廊作品'"
                decoding="async"
              >
              <img
                v-if="fullSrc"
                ref="fullImg"
                class="dp-gallery-print__img dp-gallery-print__img--full"
                :class="{ 'dp-gallery-print__img--visible': fullReady }"
                :src="fullSrc"
                :alt="item.caption || '画廊作品'"
                decoding="async"
                @load="onFullImageLoad"
              >
            </div>
            <p
              id="dp-gallery-print-caption"
              class="dp-gallery-print__caption"
            >
              <span>{{ typewriterText }}</span>
              <span v-if="typewriterActive" class="dp-gallery-print__cursor" aria-hidden="true">|</span>
            </p>
          </article>
        </div>

        <button
          ref="closeBtn"
          type="button"
          class="dp-gallery-print__close"
          aria-label="关闭作品详情"
          @click="requestClose"
        >
          <i class="el-icon-close" aria-hidden="true"></i>
        </button>
      </div>
    </div>
  </Transition>
</template>

<script>
import { galleryFileSrc } from '@features/gallery/utils/dpGalleryUrl'
import { prefetchGalleryFull } from '@features/gallery/utils/dpGalleryPrefetch'

var EJECT_MS = 920
var CLOSE_MS = 420
var TYPEWRITER_MS = 42

export default {
  name: 'DpGalleryPrintReveal',
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    item: {
      type: Object,
      default: null
    }
  },
  data() {
    return {
      uiPhase: 'idle',
      fullReady: false,
      typewriterText: '',
      typewriterActive: false,
      typewriterTimer: null,
      closeTimer: null,
      ejectTimer: null,
      prefersReducedMotion: false,
      pendingClose: false
    }
  },
  computed: {
    printClasses() {
      return {
        'dp-gallery-print--reduced': this.prefersReducedMotion,
        'dp-gallery-print--ejecting': this.uiPhase === 'ejecting',
        'dp-gallery-print--shown': this.uiPhase === 'shown',
        'dp-gallery-print--closing': this.uiPhase === 'closing'
      }
    },
    dialogAriaLabel() {
      var cap = this.item && this.item.caption
      return cap ? '查看作品：' + cap : '查看画廊作品'
    },
    previewSrc() {
      if (!this.item) return ''
      var url = this.item.previewUrl || this.item.imageUrl
      return url ? galleryFileSrc(url) : ''
    },
    fullSrc() {
      if (!this.item) return ''
      var url = this.item.imageUrl || this.item.previewUrl
      return url ? galleryFileSrc(url) : ''
    }
  },
  watch: {
    visible: function (val) {
      if (val && this.item) {
        this.beginReveal()
      } else if (!val) {
        this.resetState()
      }
    },
    item: function (val, oldVal) {
      if (this.visible && val && (!oldVal || val.id !== oldVal.id)) {
        this.beginReveal()
      }
    }
  },
  mounted() {
    this.prefersReducedMotion = this.detectReducedMotion()
    if (this.visible && this.item) {
      this.beginReveal()
    }
  },
  beforeDestroy() {
    this.clearTimers()
    this.stopTypewriter()
    this.unlockScroll()
  },
  methods: {
    detectReducedMotion() {
      return typeof window !== 'undefined' &&
        window.matchMedia &&
        window.matchMedia('(prefers-reduced-motion: reduce)').matches
    },
    beginReveal() {
      this.clearTimers()
      this.stopTypewriter()
      this.fullReady = false
      this.pendingClose = false
      prefetchGalleryFull(this.item)
      var self = this

      if (this.prefersReducedMotion) {
        this.uiPhase = 'shown'
        this.startTypewriter(this.item && this.item.caption ? String(this.item.caption) : '')
        this.$nextTick(function () {
          self.lockScroll()
          self.focusCloseButton()
          self.syncFullReadyFromImage()
        })
        return
      }

      this.uiPhase = 'ejecting'
      this.$nextTick(function () {
        self.lockScroll()
        self.focusCloseButton()
        self.syncFullReadyFromImage()
      })
      this.ejectTimer = setTimeout(function () {
        self.onEjectComplete()
      }, EJECT_MS)
    },
    syncFullReadyFromImage() {
      var img = this.$refs.fullImg
      if (img && img.complete && img.naturalWidth > 0) {
        this.fullReady = true
      }
    },
    onEjectComplete() {
      if (this.uiPhase !== 'ejecting') return
      this.uiPhase = 'shown'
      this.startTypewriter(this.item && this.item.caption ? String(this.item.caption) : '')
    },
    onSheetAnimationEnd(evt) {
      if (!evt) return
      if (evt.animationName === 'dp-gallery-print-eject' && this.uiPhase === 'ejecting') {
        this.onEjectComplete()
        return
      }
      if (evt.animationName === 'dp-gallery-print-tuck' && this.uiPhase === 'closing') {
        this.finishClose()
      }
    },
    onFullImageLoad() {
      this.fullReady = true
    },
    startTypewriter(fullText) {
      this.stopTypewriter()
      var text = fullText || ''
      if (!text) {
        this.typewriterText = '（无说明）'
        this.typewriterActive = false
        return
      }
      if (this.prefersReducedMotion) {
        this.typewriterText = text
        this.typewriterActive = false
        return
      }
      var self = this
      var idx = 0
      this.typewriterText = ''
      this.typewriterActive = true
      this.typewriterTimer = setInterval(function () {
        idx++
        self.typewriterText = text.slice(0, idx)
        if (idx >= text.length) {
          self.stopTypewriter()
        }
      }, TYPEWRITER_MS)
    },
    stopTypewriter() {
      if (this.typewriterTimer != null) {
        clearInterval(this.typewriterTimer)
        this.typewriterTimer = null
      }
      this.typewriterActive = false
    },
    requestClose() {
      if (this.pendingClose) return
      this.pendingClose = true
      this.stopTypewriter()

      if (this.prefersReducedMotion) {
        this.uiPhase = 'closing'
        var selfRm = this
        this.$nextTick(function () {
          selfRm.finishClose()
        })
        return
      }

      this.uiPhase = 'closing'
      var self = this
      this.closeTimer = setTimeout(function () {
        if (self.uiPhase === 'closing') {
          self.finishClose()
        }
      }, CLOSE_MS)
    },
    finishClose() {
      if (!this.pendingClose) return
      this.clearTimers()
      this.unlockScroll()
      this.$emit('closed')
      this.resetState()
    },
    onOverlayAfterLeave() {
      if (this.pendingClose) {
        this.finishClose()
      }
    },
    resetState() {
      this.clearTimers()
      this.stopTypewriter()
      this.uiPhase = 'idle'
      this.fullReady = false
      this.typewriterText = ''
      this.pendingClose = false
    },
    clearTimers() {
      if (this.ejectTimer != null) {
        clearTimeout(this.ejectTimer)
        this.ejectTimer = null
      }
      if (this.closeTimer != null) {
        clearTimeout(this.closeTimer)
        this.closeTimer = null
      }
    },
    lockScroll() {
      if (typeof document === 'undefined') return
      document.body.style.overflow = 'hidden'
    },
    unlockScroll() {
      if (typeof document === 'undefined') return
      document.body.style.overflow = ''
    },
    focusCloseButton() {
      var btn = this.$refs.closeBtn
      if (btn && typeof btn.focus === 'function') {
        btn.focus()
      }
    },
    onKeydown(evt) {
      if (!evt) return
      if (evt.key === 'Escape' || evt.key === 'Esc') {
        evt.preventDefault()
        this.requestClose()
        return
      }
      if (evt.key !== 'Tab') return
      var root = this.$refs.root
      if (!root) return
      var nodes = root.querySelectorAll(
        'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
      )
      var list = []
      for (var i = 0; i < nodes.length; i++) {
        if (nodes[i].tabIndex !== -1) list.push(nodes[i])
      }
      if (!list.length) return
      var first = list[0]
      var last = list[list.length - 1]
      if (evt.shiftKey) {
        if (document.activeElement === first) {
          evt.preventDefault()
          last.focus()
        }
      } else if (document.activeElement === last) {
        evt.preventDefault()
        first.focus()
      }
    }
  }
}
</script>

<style scoped>
.dp-gallery-print {
  --dp-print-paper: #faf8f5;
  --dp-print-border: color-mix(in srgb, var(--dp-panel-border, rgba(107, 93, 82, 0.22)) 90%, transparent);
  --dp-print-slot: color-mix(in srgb, var(--dp-text-primary, #3a332c) 88%, #1a1612);
  --dp-print-caption: var(--dp-text-primary, #303133);

  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: clamp(12px, 3vw, 28px);
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.dp-gallery-print__backdrop {
  position: fixed;
  inset: 0;
  margin: 0;
  padding: 0;
  border: none;
  background: color-mix(in srgb, var(--dp-text-primary, #3a332c) 52%, transparent);
  backdrop-filter: blur(3px);
  cursor: pointer;
}

.dp-gallery-print__stage {
  position: relative;
  z-index: 1;
  width: min(94vw, 420px);
  margin-top: clamp(24px, 8vh, 72px);
  display: flex;
  flex-direction: column;
  align-items: center;
  pointer-events: none;
}

.dp-gallery-print__slot {
  position: relative;
  width: min(88%, 340px);
  height: clamp(18px, 3.2vw, 24px);
  margin-bottom: -2px;
  pointer-events: none;
}

.dp-gallery-print__slot-mouth {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: 6px 6px 2px 2px;
  background:
    linear-gradient(180deg, color-mix(in srgb, #fff 8%, transparent) 0%, transparent 42%),
    linear-gradient(180deg, var(--dp-print-slot) 0%, color-mix(in srgb, var(--dp-print-slot) 82%, #000) 100%);
  box-shadow:
    inset 0 2px 0 color-mix(in srgb, #fff 14%, transparent),
    inset 0 -3px 6px rgba(0, 0, 0, 0.45),
    0 6px 16px rgba(0, 0, 0, 0.28);
}

.dp-gallery-print__slot-shadow {
  position: absolute;
  left: 8%;
  right: 8%;
  bottom: -6px;
  height: 8px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.22);
  filter: blur(4px);
}

.dp-gallery-print__eject-zone {
  width: 100%;
  overflow: hidden;
  padding-bottom: 8px;
  pointer-events: auto;
}

.dp-gallery-print__sheet {
  width: min(100%, 380px);
  margin: 0 auto;
  padding: clamp(14px, 3vw, 18px) clamp(14px, 3vw, 18px) clamp(22px, 4vw, 28px);
  background: var(--dp-print-paper);
  border: 1px solid var(--dp-print-border);
  box-shadow:
    inset 0 0 0 1px color-mix(in srgb, #fff 68%, transparent),
    0 10px 28px rgba(0, 0, 0, 0.22),
    0 2px 6px rgba(0, 0, 0, 0.12);
  transform: translateY(calc(-100% - 28px));
  opacity: 0;
}

.dp-gallery-print__sheet--ejecting,
.dp-gallery-print--shown .dp-gallery-print__sheet,
.dp-gallery-print--reduced.dp-gallery-print--shown .dp-gallery-print__sheet {
  animation: dp-gallery-print-eject 0.92s cubic-bezier(0.22, 0.85, 0.28, 1) forwards;
}

.dp-gallery-print--shown .dp-gallery-print__sheet {
  transform: translateY(0);
  opacity: 1;
  animation: none;
}

.dp-gallery-print--closing .dp-gallery-print__sheet {
  animation: dp-gallery-print-tuck 0.42s cubic-bezier(0.55, 0.08, 0.68, 0.53) forwards;
}

@keyframes dp-gallery-print-eject {
  0% {
    transform: translateY(calc(-100% - 28px)) rotate(-1.2deg);
    opacity: 0.35;
  }
  18% {
    opacity: 1;
  }
  72% {
    transform: translateY(6px) rotate(0.6deg);
  }
  88% {
    transform: translateY(-3px) rotate(-0.25deg);
  }
  100% {
    transform: translateY(0) rotate(0deg);
    opacity: 1;
  }
}

@keyframes dp-gallery-print-tuck {
  0% {
    transform: translateY(0) rotate(0deg);
    opacity: 1;
  }
  100% {
    transform: translateY(calc(-108% - 20px)) rotate(-1deg);
    opacity: 0;
  }
}

.dp-gallery-print__photo-area {
  position: relative;
  width: 100%;
  aspect-ratio: 4 / 5;
  max-height: min(52vh, 420px);
  background: color-mix(in srgb, var(--dp-subpanel-bg, #f0ece6) 80%, #fff);
  overflow: hidden;
  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--dp-text-primary, #3a332c) 6%, transparent);
}

.dp-gallery-print__img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
  transition: opacity 0.28s ease;
}

.dp-gallery-print__img--preview {
  opacity: 1;
}

.dp-gallery-print__img--preview.dp-gallery-print__img--hidden {
  opacity: 0;
}

.dp-gallery-print__img--full {
  opacity: 0;
}

.dp-gallery-print__img--full.dp-gallery-print__img--visible {
  opacity: 1;
}

.dp-gallery-print__caption {
  margin: clamp(12px, 2.4vw, 16px) 0 0;
  min-height: 1.5em;
  color: var(--dp-print-caption);
  font-size: clamp(13px, 2.6vw, 15px);
  line-height: 1.55;
  white-space: pre-wrap;
  text-align: left;
  font-family: 'Courier New', Courier, monospace;
}

.dp-gallery-print__cursor {
  display: inline-block;
  margin-left: 2px;
  animation: dp-gallery-print-cursor-blink 0.8s step-end infinite;
}

@keyframes dp-gallery-print-cursor-blink {
  50% { opacity: 0; }
}

.dp-gallery-print__close {
  pointer-events: auto;
  margin-top: clamp(14px, 3vw, 20px);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 44px;
  min-height: 44px;
  padding: 0;
  border: 1px solid color-mix(in srgb, #fff 24%, transparent);
  border-radius: 50%;
  background: color-mix(in srgb, var(--dp-panel-bg, #fff) 18%, transparent);
  color: #fff;
  cursor: pointer;
  font-size: 20px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.22);
  transition: background 0.2s ease, transform 0.2s ease;
}

.dp-gallery-print__close:hover {
  background: color-mix(in srgb, var(--dp-panel-bg, #fff) 32%, transparent);
  transform: scale(1.04);
}

.dp-gallery-print__close:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 3px;
}

.dp-gallery-print-overlay-enter-active,
.dp-gallery-print-overlay-leave-active {
  transition: opacity 0.32s ease;
}

.dp-gallery-print-overlay-enter,
.dp-gallery-print-overlay-leave-to {
  opacity: 0;
}

.dp-gallery-print--reduced .dp-gallery-print__sheet {
  transform: translateY(0);
  opacity: 1;
  animation: none;
}

.dp-gallery-print--reduced .dp-gallery-print__sheet--ejecting {
  animation: none;
}

@media (prefers-reduced-motion: reduce) {
  .dp-gallery-print__sheet,
  .dp-gallery-print__sheet--ejecting,
  .dp-gallery-print--shown .dp-gallery-print__sheet,
  .dp-gallery-print--closing .dp-gallery-print__sheet {
    animation: none !important;
    transform: translateY(0) !important;
    opacity: 1 !important;
    transition: opacity 0.2s ease;
  }

  .dp-gallery-print--closing .dp-gallery-print__sheet {
    opacity: 0 !important;
  }

  .dp-gallery-print__img {
    transition: none;
  }

  .dp-gallery-print__cursor {
    animation: none;
  }

  .dp-gallery-print__close {
    transition: none;
  }
}
</style>
