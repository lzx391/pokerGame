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
      <div
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
    }
  },
  data() {
    return {
      frameClasses: {},
      frameAspectRatios: {},
      detailVisible: false,
      detailItem: null,
      prefetchTimer: null
    }
  },
  computed: {
    prefersReducedMotion() {
      return typeof window !== 'undefined' &&
        window.matchMedia &&
        window.matchMedia('(prefers-reduced-motion: reduce)').matches
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
          prefetchGalleryUrls(self.items.slice(0, Math.min(PREFETCH_AHEAD, self.items.length)))
        })
      }
    },
    loading: function (val) {
      if (!val && this.items.length) {
        var self = this
        this.$nextTick(function () {
          self.schedulePrefetch(true)
          prefetchGalleryUrls(self.items.slice(0, Math.min(PREFETCH_AHEAD, self.items.length)))
        })
      }
    }
  },
  mounted() {
    if (typeof window !== 'undefined') {
      window.addEventListener('scroll', this.onPageScroll, { passive: true })
    }
    var self = this
    this.$nextTick(function () {
      if (self.items.length) {
        self.schedulePrefetch(true)
        prefetchGalleryUrls(self.items.slice(0, Math.min(PREFETCH_AHEAD, self.items.length)))
      }
    })
  },
  beforeDestroy() {
    if (typeof window !== 'undefined') {
      window.removeEventListener('scroll', this.onPageScroll)
    }
    if (this.prefetchTimer != null) {
      clearTimeout(this.prefetchTimer)
      this.prefetchTimer = null
    }
  },
  methods: {
    galleryFileSrc,
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
        prefetchGalleryAhead(this.items, this.getCurrentItemIndex(), PREFETCH_AHEAD)
        return
      }
      this.prefetchTimer = setTimeout(function () {
        self.prefetchTimer = null
        prefetchGalleryAhead(self.items, self.getCurrentItemIndex(), PREFETCH_AHEAD)
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
      prefetchGalleryFull(item)
      this.detailItem = item
      this.detailVisible = true
    },
    onDetailClosed() {
      this.detailVisible = false
      this.detailItem = null
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

  position: relative;
  flex: 1 1 auto;
  min-height: clamp(60vh, 65vh, 70vh);
  display: flex;
  flex-direction: column;
  margin-top: clamp(8px, 1.6vw, 14px);
  padding: clamp(10px, 2vw, 16px) clamp(6px, 1.4vw, 12px) clamp(14px, 2.4vw, 20px);
  border-radius: clamp(12px, 2.4vw, 18px);
}

/* Mat runway — museum display plinth, subtle vertical guides only */
.dp-gallery-wall::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  border-radius: inherit;
  pointer-events: none;
  background:
    radial-gradient(ellipse 85% 70% at 50% 0%, color-mix(in srgb, #fff 12%, transparent) 0%, transparent 58%),
    repeating-linear-gradient(
      90deg,
      transparent 0,
      transparent 72px,
      color-mix(in srgb, var(--dp-gallery-mat-edge, rgba(107, 93, 82, 0.12)) 18%, transparent) 72px,
      color-mix(in srgb, var(--dp-gallery-mat-edge, rgba(107, 93, 82, 0.12)) 18%, transparent) 73px
    ),
    linear-gradient(
      180deg,
      color-mix(in srgb, var(--dp-gallery-mat-bg, #f8f5f0) 96%, #fff) 0%,
      var(--dp-gallery-mat-bg, #f8f5f0) 54%,
      color-mix(in srgb, var(--dp-gallery-mat-bg, #f8f5f0) 92%, var(--dp-text-primary, #3a332c)) 100%
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

.dp-gallery-wall__track {
  display: flex;
  flex-direction: row;
  flex-wrap: nowrap;
  align-items: flex-start;
  gap: clamp(14px, 2.4vw, 22px);
  width: 100%;
  overflow-x: auto;
  overflow-y: hidden;
  padding: clamp(8px, 1.6vw, 14px) clamp(4px, 1vw, 10px) clamp(20px, 3vw, 28px);
  scroll-behavior: smooth;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
  scrollbar-color: color-mix(in srgb, var(--dp-accent, #6b5d52) 35%, transparent) transparent;
}

.dp-gallery-wall__track--few {
  justify-content: center;
}

.dp-gallery-wall__column {
  display: flex;
  flex-direction: column;
  flex: 0 0 auto;
  /* ~one frame height between stacked pieces in capacity-2 columns */
  gap: var(--dp-gallery-frame-w);
}

.dp-gallery-wall__column--stagger {
  /* offset single-image columns to sit in the vertical slot of adjacent pairs */
  padding-top: calc(var(--dp-gallery-frame-w) * 1.15);
}

.dp-gallery-wall__piece {
  flex: 0 0 auto;
  animation: dp-gallery-piece-in 0.5s ease both;
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

@media (prefers-reduced-motion: reduce) {
  .dp-gallery-wall__piece,
  .gallery-frame {
    animation: none;
    transition: none;
  }
  .dp-gallery-wall__track {
    scroll-behavior: auto;
  }
  .gallery-frame:hover {
    transform: none;
  }
}
</style>
