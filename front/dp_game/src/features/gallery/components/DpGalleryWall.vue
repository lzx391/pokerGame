<template>
  <section class="dp-gallery-wall" aria-label="画廊作品墙">
    <div v-if="loading" class="dp-gallery-wall__status">
      <i class="el-icon-loading" aria-hidden="true"></i>
      <span>加载作品…</span>
    </div>

    <p v-else-if="!items.length" class="dp-gallery-wall__empty">
      {{ emptyText }}
    </p>

    <div v-else class="dp-gallery-wall__frame">
      <!-- Conveyor belt: seamless CSS marquee in read-only view -->
      <div
        v-if="autoScrollEnabled"
        ref="marqueeViewport"
        class="dp-gallery-wall__viewport"
        :class="{
          'dp-gallery-wall__viewport--paused': scrollPaused || hoverPaused,
          'dp-gallery-wall__viewport--manual': useManualMarqueeTransform
        }"
        @pointerdown="onMarqueePointerDown"
        @pointerup="onMarqueePointerUp"
        @pointercancel="onMarqueePointerUp"
        @pointerleave="onMarqueePointerLeave"
        @mouseenter="onMarqueeMouseEnter"
        @wheel="onMarqueeWheel"
      >
        <div
          ref="marqueeEl"
          class="dp-gallery-wall__marquee"
          :style="marqueeInlineStyle"
          aria-hidden="false"
        >
          <div
            v-for="set in displaySets"
            :key="'set-' + set.key"
            class="dp-gallery-wall__track dp-gallery-wall__track--marquee"
            :aria-hidden="set.duplicate ? 'true' : undefined"
            role="list"
          >
            <div
              v-for="(column, colIndex) in set.columns"
              :key="'col-' + set.key + '-' + colIndex"
              class="dp-gallery-wall__column"
              :class="{ 'dp-gallery-wall__column--stagger': colIndex % 2 === 1 }"
            >
              <article
                v-for="entry in column"
                :key="'piece-' + set.key + '-' + entry.item.id"
                class="dp-gallery-wall__piece"
                :style="pieceStyle(entry.index)"
                role="listitem"
              >
                <button
                  type="button"
                  class="gallery-frame"
                  :class="frameClass(entry.item.id)"
                  :style="frameStyle(entry.item.id)"
                  :aria-label="entry.item.caption ? '查看作品：' + entry.item.caption : '查看画廊作品'"
                  @click="openDetail(entry.item)"
                  @mouseenter="onFrameWarm(entry.item)"
                  @focus="onFrameWarm(entry.item)"
                >
                  <img
                    :src="galleryFileSrc(entry.item.previewUrl || entry.item.imageUrl)"
                    :alt="entry.item.caption || '画廊作品'"
                    loading="lazy"
                    decoding="async"
                    @load="onImageLoad(entry.item.id, $event)"
                  >
                </button>
              </article>
            </div>
          </div>
        </div>
      </div>

      <!-- Static / manual scroll: edit mode, reduced motion, eco mode -->
      <div
        v-else
        ref="track"
        class="dp-gallery-wall__track"
        :class="{ 'dp-gallery-wall__track--few': items.length <= 3 }"
        role="list"
        @scroll="onTrackScroll"
      >
        <div
          v-for="(column, colIndex) in wallColumns"
          :key="'col-' + colIndex"
          class="dp-gallery-wall__column"
          :class="{ 'dp-gallery-wall__column--stagger': colIndex % 2 === 1 }"
        >
          <article
            v-for="entry in column"
            :key="entry.item.id"
            class="dp-gallery-wall__piece"
            :style="pieceStyle(entry.index)"
            role="listitem"
          >
            <button
              type="button"
              class="gallery-frame"
              :class="frameClass(entry.item.id)"
              :style="frameStyle(entry.item.id)"
              :aria-label="entry.item.caption ? '查看作品：' + entry.item.caption : '查看画廊作品'"
              @click="openDetail(entry.item)"
              @mouseenter="onFrameWarm(entry.item)"
              @focus="onFrameWarm(entry.item)"
            >
              <img
                :src="galleryFileSrc(entry.item.previewUrl || entry.item.imageUrl)"
                :alt="entry.item.caption || '画廊作品'"
                loading="lazy"
                decoding="async"
                @load="onImageLoad(entry.item.id, $event)"
              >
            </button>
          </article>
        </div>
      </div>
    </div>

    <dp-gallery-print-reveal
      :visible="detailVisible"
      :item="detailItem"
      @closed="onDetailClosed"
    />
  </section>
</template>

<script>
import { galleryFileSrc } from '@features/gallery/utils/dpGalleryUrl'
import { dpGalleryFrameClass, dpGalleryAspectRatio } from '@features/gallery/utils/dpGalleryFrame'
import DpGalleryPrintReveal from '@features/gallery/components/DpGalleryPrintReveal.vue'
import {
  prefetchGalleryAhead,
  prefetchGalleryFull,
  prefetchGalleryUrls
} from '@features/gallery/utils/dpGalleryPrefetch'

var PREFETCH_AHEAD = 3
var PREFETCH_THROTTLE_MS = 200

export default {
  name: 'DpGalleryWall',
  components: {
    DpGalleryPrintReveal
  },
  props: {
    items: {
      type: Array,
      default: function () { return [] }
    },
    loading: {
      type: Boolean,
      default: false
    },
    emptyText: {
      type: String,
      default: '还没有上传画廊作品'
    },
    editMode: {
      type: Boolean,
      default: false
    },
    ecoMode: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      frameClasses: {},
      frameAspectRatios: {},
      detailVisible: false,
      detailItem: null,
      prefetchTimer: null,
      touchPaused: false,
      hoverPaused: false,
      pointerOverViewport: false,
      lastPointerX: 0,
      lastPointerY: 0,
      manualMarqueeOffset: 0,
      useManualMarqueeTransform: false,
      ecoModeFromDom: false,
      ecoObserver: null
    }
  },
  computed: {
    prefersReducedMotion() {
      return typeof window !== 'undefined' &&
        window.matchMedia &&
        window.matchMedia('(prefers-reduced-motion: reduce)').matches
    },
    ecoModeActive() {
      return this.ecoMode || this.ecoModeFromDom
    },
    autoScrollEnabled() {
      return !this.editMode &&
        !this.prefersReducedMotion &&
        this.items.length > 0
    },
    scrollPaused() {
      return this.detailVisible || this.touchPaused || this.ecoModeActive
    },
    marqueeInlineStyle() {
      if (!this.useManualMarqueeTransform) return {}
      var axis = this.isMarqueeVertical() ? 'Y' : 'X'
      return { transform: 'translate' + axis + '(' + this.manualMarqueeOffset + 'px)' }
    },
    wallColumns() {
      var items = this.items
      if (!items.length) return []

      var columns = []
      var itemIndex = 0
      var colIndex = 0

      while (itemIndex < items.length) {
        var capacity = colIndex % 2 === 0 ? 2 : 1
        var column = []

        for (var i = 0; i < capacity && itemIndex < items.length; i++) {
          column.push({ item: items[itemIndex], index: itemIndex })
          itemIndex++
        }

        if (column.length) {
          columns.push(column)
        }
        colIndex++
      }

      return columns
    },
    displaySets() {
      var columns = this.wallColumns
      return [
        { key: 'a', columns: columns, duplicate: false },
        { key: 'b', columns: columns, duplicate: true }
      ]
    }
  },
  watch: {
    items: {
      deep: true,
      handler: function () {
        this.frameClasses = {}
        this.frameAspectRatios = {}
        var self = this
        this.$nextTick(function () {
          self.schedulePrefetch(true)
        })
      }
    },
    loading: function (val) {
      if (!val && this.items.length) {
        var self = this
        this.$nextTick(function () {
          self.schedulePrefetch(true)
        })
      }
    },
    autoScrollEnabled: function () {
      var self = this
      this.$nextTick(function () {
        self.schedulePrefetch(true)
      })
    }
  },
  mounted() {
    if (typeof window !== 'undefined') {
      window.addEventListener('scroll', this.onPageScroll, { passive: true })
    }
    if (typeof document !== 'undefined') {
      document.addEventListener('pointermove', this.onDocumentPointerMove, { passive: true })
    }
    this.observeEcoMode()
    var self = this
    this.$nextTick(function () {
      if (self.items.length) {
        self.schedulePrefetch(true)
      }
    })
  },
  beforeDestroy() {
    if (typeof window !== 'undefined') {
      window.removeEventListener('scroll', this.onPageScroll)
    }
    if (typeof document !== 'undefined') {
      document.removeEventListener('pointermove', this.onDocumentPointerMove)
    }
    if (this.prefetchTimer != null) {
      clearTimeout(this.prefetchTimer)
      this.prefetchTimer = null
    }
    if (this.ecoObserver) {
      this.ecoObserver.disconnect()
      this.ecoObserver = null
    }
  },
  methods: {
    galleryFileSrc,
    observeEcoMode() {
      if (typeof document === 'undefined') return
      var root = this.$el && this.$el.closest('[data-dp-eco-mode]')
      if (!root) {
        root = document.querySelector('[data-dp-eco-mode]')
      }
      if (!root) return

      var self = this
      var readEco = function () {
        self.ecoModeFromDom = root.getAttribute('data-dp-eco-mode') === 'true'
      }
      readEco()

      if (typeof MutationObserver !== 'undefined') {
        this.ecoObserver = new MutationObserver(readEco)
        this.ecoObserver.observe(root, { attributes: true, attributeFilter: ['data-dp-eco-mode'] })
      }
    },
    isMarqueeVertical() {
      return typeof window !== 'undefined' &&
        window.matchMedia &&
        window.matchMedia('(max-width: 767px)').matches
    },
    getMarqueeDurationSec() {
      var marquee = this.$refs.marqueeEl
      if (!marquee || typeof window === 'undefined') return 50
      var raw = window.getComputedStyle(marquee).getPropertyValue('--dp-gallery-marquee-duration').trim()
      if (!raw) return 50
      if (raw.endsWith('ms')) return parseFloat(raw) / 1000
      if (raw.endsWith('s')) return parseFloat(raw)
      var parsed = parseFloat(raw)
      return isNaN(parsed) ? 50 : parsed
    },
    readMarqueeTranslate(el) {
      if (!el || typeof window === 'undefined') return 0
      var matrix = new DOMMatrixReadOnly(window.getComputedStyle(el).transform)
      return this.isMarqueeVertical() ? matrix.m42 : matrix.m41
    },
    getMarqueeLoopDistance(el) {
      if (!el) return 0
      return this.isMarqueeVertical() ? el.offsetHeight / 2 : el.offsetWidth / 2
    },
    freezeMarqueeAtCurrentPosition() {
      var marquee = this.$refs.marqueeEl
      if (!marquee) return
      this.manualMarqueeOffset = this.readMarqueeTranslate(marquee)
      this.useManualMarqueeTransform = true
    },
    resumeMarqueeAnimation() {
      var marquee = this.$refs.marqueeEl
      if (!marquee) {
        this.useManualMarqueeTransform = false
        return
      }

      var loopDistance = this.getMarqueeLoopDistance(marquee)
      var duration = this.getMarqueeDurationSec()
      var progress = 0

      if (loopDistance > 0) {
        progress = ((-this.manualMarqueeOffset % loopDistance) + loopDistance) % loopDistance / loopDistance
      }

      this.manualMarqueeOffset = -progress * loopDistance
      this.useManualMarqueeTransform = false

      marquee.style.animation = 'none'
      marquee.style.transform = ''
      void marquee.offsetWidth
      marquee.style.removeProperty('animation')
      marquee.style.animationDelay = '-' + (progress * duration) + 's'
    },
    onMarqueePointerDown() {
      this.touchPaused = true
    },
    onMarqueePointerUp() {
      this.touchPaused = false
    },
    onDocumentPointerMove(evt) {
      if (!evt) return
      this.lastPointerX = evt.clientX
      this.lastPointerY = evt.clientY
    },
    isPointerOverMarqueeViewport() {
      var viewport = this.$refs.marqueeViewport
      if (!viewport || typeof document === 'undefined') return this.pointerOverViewport
      var el = document.elementFromPoint(this.lastPointerX, this.lastPointerY)
      return !!(el && viewport.contains(el))
    },
    onMarqueePointerLeave() {
      this.touchPaused = false
      this.pointerOverViewport = false
      if (!this.hoverPaused || this.detailVisible) return
      this.resumeMarqueeAnimation()
      this.hoverPaused = false
    },
    onMarqueeMouseEnter() {
      this.pointerOverViewport = true
      if (this.scrollPaused) return
      this.hoverPaused = true
      this.freezeMarqueeAtCurrentPosition()
    },
    onMarqueeWheel(evt) {
      if (!this.hoverPaused || this.scrollPaused || !evt) return

      var delta = evt.deltaY
      if (evt.deltaMode === 1) delta *= 16
      else if (evt.deltaMode === 2) {
        var viewport = this.$refs.marqueeViewport
        delta *= viewport ? viewport.clientHeight : 400
      }

      if (evt.deltaMode === 0 && Math.abs(evt.deltaY) < Math.abs(evt.deltaX)) {
        delta = evt.deltaX
      }

      if (!delta) return
      evt.preventDefault()

      var marquee = this.$refs.marqueeEl
      if (!marquee) return

      this.manualMarqueeOffset -= delta

      var loopDistance = this.getMarqueeLoopDistance(marquee)
      if (loopDistance > 0) {
        var wrapped = ((-this.manualMarqueeOffset % loopDistance) + loopDistance) % loopDistance
        this.manualMarqueeOffset = -wrapped
      }
    },
    onImageLoad(id, evt) {
      var img = evt && evt.target
      if (!img || !id) return
      this.$set(this.frameClasses, id, dpGalleryFrameClass(img.naturalWidth, img.naturalHeight))
      this.$set(this.frameAspectRatios, id, dpGalleryAspectRatio(img.naturalWidth, img.naturalHeight))
    },
    frameClass(id) {
      return this.frameClasses[id] || 'gallery-frame--square'
    },
    frameStyle(id) {
      var ratio = this.frameAspectRatios[id]
      if (!ratio) return {}
      return { '--dp-gallery-frame-ratio': ratio }
    },
    pieceStyle(index) {
      if (this.prefersReducedMotion) {
        return {}
      }
      return { animationDelay: (index * 0.06) + 's' }
    },
    getCurrentItemIndex() {
      var track = this.$refs.track
      if (!track || !this.items.length) return 0
      var pieces = track.querySelectorAll('.dp-gallery-wall__piece')
      if (!pieces.length) return 0

      var trackRect = track.getBoundingClientRect()
      var viewportCenter = track.scrollLeft + track.clientWidth / 2

      for (var i = 0; i < pieces.length; i++) {
        var rect = pieces[i].getBoundingClientRect()
        var pieceCenter = rect.left - trackRect.left + track.scrollLeft + rect.width / 2
        if (Math.abs(pieceCenter - viewportCenter) <= rect.width * 0.6) return i
      }

      for (var j = 0; j < pieces.length; j++) {
        var r = pieces[j].getBoundingClientRect()
        if (r.right > trackRect.left && r.left < trackRect.right) return j
      }
      return 0
    },
    schedulePrefetch(immediate) {
      var self = this
      if (this.prefetchTimer != null) {
        clearTimeout(this.prefetchTimer)
        this.prefetchTimer = null
      }
      if (immediate) {
        if (this.autoScrollEnabled) {
          prefetchGalleryUrls(this.items)
        } else {
          prefetchGalleryAhead(this.items, this.getCurrentItemIndex(), PREFETCH_AHEAD)
          prefetchGalleryUrls(this.items.slice(0, Math.min(PREFETCH_AHEAD, this.items.length)))
        }
        return
      }
      this.prefetchTimer = setTimeout(function () {
        self.prefetchTimer = null
        if (self.autoScrollEnabled) {
          prefetchGalleryUrls(self.items)
        } else {
          prefetchGalleryAhead(self.items, self.getCurrentItemIndex(), PREFETCH_AHEAD)
        }
      }, PREFETCH_THROTTLE_MS)
    },
    onTrackScroll() {
      this.schedulePrefetch()
    },
    onPageScroll() {
      this.schedulePrefetch()
    },
    onFrameWarm(item) {
      prefetchGalleryFull(item)
    },
    openDetail(item) {
      if (this.autoScrollEnabled) {
        this.freezeMarqueeAtCurrentPosition()
      }
      prefetchGalleryFull(item)
      this.detailItem = item
      this.detailVisible = true
    },
    onDetailClosed() {
      this.detailVisible = false
      this.detailItem = null
      var self = this
      this.$nextTick(function () {
        if (!self.autoScrollEnabled || !self.$refs.marqueeEl) return
        if (self.isPointerOverMarqueeViewport()) {
          self.hoverPaused = true
          self.freezeMarqueeAtCurrentPosition()
        } else {
          self.hoverPaused = false
          if (self.useManualMarqueeTransform) {
            self.resumeMarqueeAnimation()
          }
        }
      })
    }
  }
}
</script>

<style scoped>
.dp-gallery-wall {
  --dp-gallery-frame-brown-dark: #2c2118;
  --dp-gallery-frame-brown-mid: #3d2e24;
  --dp-gallery-frame-brown-body: #4a3729;
  --dp-gallery-frame-brown-highlight: #6b5344;
  --dp-gallery-frame-brown-rim: #1f1812;
  --dp-gallery-frame-shadow: rgba(44, 33, 24, 0.25);
  --dp-gallery-frame-border-w: clamp(5px, 0.7vw, 8px);
  --dp-gallery-frame-w: clamp(120px, 13vw, 160px);
  --dp-gallery-frame-radius: clamp(6px, 1vw, 10px);
  --dp-gallery-marquee-duration: 50s;
  --dp-gallery-track-gap: clamp(14px, 2.4vw, 22px);
  /* Per-column track spotlight — overridden by gallery-page theme */
  --dp-gallery-spot-core: rgba(255, 248, 235, 0.55);
  --dp-gallery-spot-mid: rgba(255, 248, 235, 0.15);
  --dp-gallery-spot-spread: 80%;
  --dp-gallery-spot-depth: 120%;
  --dp-gallery-ambient-vignette: rgba(32, 26, 20, 0.42);

  position: relative;
  flex: 1 1 auto;
  min-height: clamp(60vh, 65vh, 70vh);
  display: flex;
  flex-direction: column;
  margin-top: clamp(8px, 1.6vw, 14px);
  padding: clamp(10px, 2vw, 16px) clamp(6px, 1.4vw, 12px) clamp(14px, 2.4vw, 20px);
  border-radius: clamp(12px, 2.4vw, 18px);
}

/* Mat runway — darker museum wall; column spotlights provide illumination */
.dp-gallery-wall::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  border-radius: inherit;
  pointer-events: none;
  background:
    repeating-linear-gradient(
      90deg,
      transparent 0,
      transparent 72px,
      color-mix(in srgb, var(--dp-gallery-mat-edge, rgba(107, 93, 82, 0.12)) 18%, transparent) 72px,
      color-mix(in srgb, var(--dp-gallery-mat-edge, rgba(107, 93, 82, 0.12)) 18%, transparent) 73px
    ),
    linear-gradient(
      180deg,
      color-mix(in srgb, var(--dp-gallery-mat-bg, #b8aca0) 82%, #000) 0%,
      color-mix(in srgb, var(--dp-gallery-mat-bg, #b8aca0) 92%, #000) 54%,
      color-mix(in srgb, var(--dp-gallery-mat-bg, #b8aca0) 78%, #000) 100%
    );
  box-shadow:
    inset 0 3px 0 color-mix(in srgb, var(--dp-gallery-mat-edge, rgba(107, 93, 82, 0.12)) 65%, transparent),
    inset 0 0 0 1px color-mix(in srgb, #fff 28%, transparent),
    inset 0 -2px 0 var(--dp-gallery-mat-edge, rgba(107, 93, 82, 0.12)),
    0 2px 8px rgba(58, 51, 44, 0.06);
}

/* Baseboard trim along mat bottom edge */
.dp-gallery-wall::after {
  content: '';
  position: absolute;
  left: clamp(8px, 2vw, 14px);
  right: clamp(8px, 2vw, 14px);
  bottom: clamp(4px, 0.8vw, 7px);
  height: clamp(5px, 1vw, 8px);
  z-index: 0;
  border-radius: 2px;
  pointer-events: none;
  background:
    repeating-linear-gradient(
      90deg,
      color-mix(in srgb, var(--dp-gallery-baseboard) 88%, #fff) 0,
      color-mix(in srgb, var(--dp-gallery-baseboard) 88%, #fff) 4px,
      color-mix(in srgb, var(--dp-accent, #6b5d52) 26%, var(--dp-subpanel-bg)) 4px,
      color-mix(in srgb, var(--dp-accent, #6b5d52) 26%, var(--dp-subpanel-bg)) 8px
    );
  box-shadow: 0 1px 0 color-mix(in srgb, #fff 18%, transparent);
}

.dp-gallery-wall__status,
.dp-gallery-wall__empty {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 180px;
  color: var(--dp-text-muted, #909399);
  font-size: 14px;
  margin: 0;
}

.dp-gallery-wall__frame {
  position: relative;
  z-index: 1;
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  width: 100%;
  min-height: 0;
}

/* Ambient vignette between column spotlights */
.dp-gallery-wall__frame::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  border-radius: inherit;
  background:
    repeating-linear-gradient(
      90deg,
      transparent 0,
      transparent calc(var(--dp-gallery-frame-w) * 0.35 + var(--dp-gallery-track-gap) * 0.25),
      color-mix(in srgb, var(--dp-gallery-ambient-vignette) 55%, transparent) calc(var(--dp-gallery-frame-w) * 0.55 + var(--dp-gallery-track-gap) * 0.45),
      transparent calc(var(--dp-gallery-frame-w) + var(--dp-gallery-track-gap))
    ),
    radial-gradient(
      ellipse 92% 88% at 50% 40%,
      transparent 12%,
      color-mix(in srgb, var(--dp-gallery-ambient-vignette) 72%, transparent) 58%,
      var(--dp-gallery-ambient-vignette) 100%
    );
}

/* Marquee viewport — clips conveyor belt */
.dp-gallery-wall__viewport {
  position: relative;
  z-index: 1;
  flex: 1 1 auto;
  width: 100%;
  min-height: 0;
  overflow: hidden;
  padding: clamp(8px, 1.6vw, 14px) clamp(4px, 1vw, 10px) clamp(20px, 3vw, 28px);
  touch-action: pan-y pinch-zoom;
}

.dp-gallery-wall__marquee {
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap;
  align-items: flex-start;
  width: max-content;
  animation: dp-gallery-marquee-x var(--dp-gallery-marquee-duration) linear infinite;
  will-change: transform;
}

.dp-gallery-wall__viewport--paused .dp-gallery-wall__marquee {
  animation-play-state: paused;
}

.dp-gallery-wall__viewport--manual .dp-gallery-wall__marquee {
  animation: none !important;
}

.dp-gallery-wall__viewport--manual {
  cursor: grab;
}

.dp-gallery-wall__viewport--manual:active {
  cursor: grabbing;
}

.dp-gallery-wall__track {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap;
  align-items: flex-start;
  gap: var(--dp-gallery-track-gap);
  width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  padding: clamp(8px, 1.6vw, 14px) clamp(4px, 1vw, 10px) clamp(20px, 3vw, 28px);
  scroll-behavior: smooth;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
  scrollbar-color: color-mix(in srgb, var(--dp-accent, #6b5d52) 35%, transparent) transparent;
}

.dp-gallery-wall__track--marquee {
  flex: 0 0 auto;
  width: auto;
  overflow: visible;
  padding: 0;
  /* Trailing gap on each duplicate half keeps loop seam spacing consistent */
  padding-right: var(--dp-gallery-track-gap);
  box-sizing: content-box;
}

.dp-gallery-wall__track--few {
  justify-content: center;
}

.dp-gallery-wall__column {
  position: relative;
  display: flex;
  flex-direction: column;
  flex: 0 0 auto;
  /* ~one frame height between stacked pieces in capacity-2 columns */
  gap: var(--dp-gallery-frame-w);
}

/* Overhead track light — arch/cone wash per column */
.dp-gallery-wall__column::before {
  content: '';
  position: absolute;
  top: calc(-1 * clamp(8px, 1.6vw, 14px));
  left: 50%;
  transform: translateX(-50%);
  width: calc(100% + var(--dp-gallery-track-gap) * 0.85);
  height: calc(100% + clamp(8px, 1.6vw, 14px) + clamp(20px, 3vw, 28px));
  pointer-events: none;
  z-index: 0;
  background: radial-gradient(
    ellipse var(--dp-gallery-spot-spread) var(--dp-gallery-spot-depth) at 50% 0%,
    var(--dp-gallery-spot-core) 0%,
    var(--dp-gallery-spot-mid) 35%,
    transparent 70%
  );
}

.dp-gallery-wall__column--stagger {
  /* offset single-image columns to sit in the vertical slot of adjacent pairs */
  padding-top: calc(var(--dp-gallery-frame-w) * 1.15);
}

.dp-gallery-wall__piece {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
  animation: dp-gallery-piece-in 0.5s ease both;
}

@keyframes dp-gallery-marquee-x {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(-50%);
  }
}

@keyframes dp-gallery-marquee-y {
  from {
    transform: translateY(0);
  }
  to {
    transform: translateY(-50%);
  }
}

@keyframes dp-gallery-piece-in {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.gallery-frame {
  position: relative;
  display: block;
  width: var(--dp-gallery-frame-w);
  aspect-ratio: var(--dp-gallery-frame-ratio, 1 / 1);
  box-sizing: border-box;
  padding: var(--dp-gallery-frame-border-w);
  border: 1px solid var(--dp-gallery-frame-brown-rim);
  border-radius: var(--dp-gallery-frame-radius);
  margin: 0;
  cursor: pointer;
  background: linear-gradient(
    155deg,
    var(--dp-gallery-frame-brown-body) 0%,
    var(--dp-gallery-frame-brown-mid) 42%,
    var(--dp-gallery-frame-brown-dark) 100%
  );
  overflow: hidden;
  font-family: inherit;
  box-shadow:
    0 4px 12px var(--dp-gallery-frame-shadow),
    inset 0 1px 0 rgba(255, 255, 255, 0.08),
    inset 0 -1px 0 rgba(44, 33, 24, 0.22),
    inset 0 0 0 1px var(--dp-gallery-frame-brown-highlight);
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}
.gallery-frame:focus-visible {
  outline: 2px solid var(--dp-accent, #409eff);
  outline-offset: 4px;
}
.gallery-frame:hover {
  transform: translateY(-3px);
  box-shadow:
    0 8px 22px rgba(44, 33, 24, 0.32),
    0 4px 12px var(--dp-gallery-frame-shadow),
    inset 0 1px 0 rgba(255, 255, 255, 0.1),
    inset 0 -1px 0 rgba(44, 33, 24, 0.26),
    inset 0 0 0 1px color-mix(in srgb, var(--dp-gallery-frame-brown-highlight) 88%, #fff);
}

.gallery-frame img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: max(2px, calc(var(--dp-gallery-frame-radius) - var(--dp-gallery-frame-border-w) * 0.45));
}

/* Subtle lift in lit zone — warm highlight on frame rim */
.gallery-frame::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  pointer-events: none;
  opacity: 0.45;
  background: radial-gradient(
    ellipse 90% 75% at 50% 8%,
    color-mix(in srgb, var(--dp-gallery-spot-core) 28%, transparent) 0%,
    transparent 62%
  );
}

/* Mobile: vertical conveyor belt */
@media (max-width: 767px) {
  .dp-gallery-wall__viewport {
    touch-action: pan-x pinch-zoom;
  }

  .dp-gallery-wall__marquee {
    flex-direction: column;
    width: 100%;
    height: max-content;
    animation-name: dp-gallery-marquee-y;
  }

  .dp-gallery-wall__track--marquee {
    flex-direction: column;
    align-items: center;
    width: 100%;
    padding-right: 0;
    padding-bottom: var(--dp-gallery-track-gap);
  }

  .dp-gallery-wall__column--stagger {
    padding-top: 0;
    padding-left: calc(var(--dp-gallery-frame-w) * 0.575);
  }

  /* Vertical belt: spotlight still falls from column top */
  .dp-gallery-wall__column::before {
    top: calc(-1 * clamp(8px, 1.6vw, 14px));
    left: 50%;
    width: calc(100% + var(--dp-gallery-track-gap) * 0.6);
    height: calc(100% + clamp(8px, 1.6vw, 14px) + clamp(20px, 3vw, 28px));
  }
}

@media (prefers-reduced-motion: reduce) {
  .dp-gallery-wall__piece,
  .gallery-frame {
    animation: none;
    transition: none;
  }
  .dp-gallery-wall__track {
    scroll-behavior: auto;
  }
  .dp-gallery-wall__marquee {
    animation: none;
  }
  .gallery-frame:hover {
    transform: none;
  }
}
</style>
