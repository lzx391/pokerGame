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
        :class="{ 'dp-gallery-wall__track--wide': isWide }"
        role="list"
        @scroll.passive="onTrackScroll"
      >
        <article
          v-for="(item, index) in items"
          :key="item.id"
          class="dp-gallery-wall__piece"
          :style="pieceStyle(index)"
          role="listitem"
        >
          <button
            type="button"
            class="gallery-frame"
            :class="frameClass(item.id)"
            :aria-label="item.caption ? '查看作品：' + item.caption : '查看画廊作品'"
            @click="openDetail(item)"
            @mouseenter="onFrameWarm(item)"
            @focus="onFrameWarm(item)"
          >
            <div class="gallery-frame__mat">
              <img
                :src="galleryFileSrc(item.previewUrl || item.imageUrl)"
                :alt="item.caption || '画廊作品'"
                loading="lazy"
                decoding="async"
                @load="onImageLoad(item.id, $event)"
              >
            </div>
          </button>
          <p v-if="item.caption" class="dp-gallery-wall__caption">{{ item.caption }}</p>
        </article>
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
import { dpGalleryFrameClass } from '@features/gallery/utils/dpGalleryFrame'
import DpGalleryPrintReveal from '@features/gallery/components/DpGalleryPrintReveal.vue'
import {
  prefetchGalleryAhead,
  prefetchGalleryFull,
  prefetchGalleryUrls
} from '@features/gallery/utils/dpGalleryPrefetch'

var WIDE_BP = 768
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
      isWide: typeof window !== 'undefined' ? window.innerWidth >= WIDE_BP : true,
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
    }
  },
  watch: {
    items: {
      deep: true,
      handler: function () {
        this.frameClasses = {}
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
    this.syncWide()
    if (typeof window !== 'undefined') {
      window.addEventListener('resize', this.syncWide, { passive: true })
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
      window.removeEventListener('resize', this.syncWide)
    }
    if (this.prefetchTimer != null) {
      clearTimeout(this.prefetchTimer)
      this.prefetchTimer = null
    }
  },
  methods: {
    galleryFileSrc,
    syncWide() {
      var wide = typeof window !== 'undefined' && window.innerWidth >= WIDE_BP
      if (wide !== this.isWide) {
        this.isWide = wide
      }
    },
    onImageLoad(id, evt) {
      var img = evt && evt.target
      if (!img || !id) return
      var cls = dpGalleryFrameClass(img.naturalWidth, img.naturalHeight)
      this.$set(this.frameClasses, id, cls)
    },
    frameClass(id) {
      return this.frameClasses[id] || 'gallery-frame--square'
    },
    pieceStyle(index) {
      if (this.prefersReducedMotion) {
        return {}
      }
      return { animationDelay: (index * 0.08) + 's' }
    },
    getCurrentItemIndex() {
      var track = this.$refs.track
      if (!track || !this.items.length) return 0
      var pieces = track.querySelectorAll('.dp-gallery-wall__piece')
      if (!pieces.length) return 0

      var scrollPos = this.isWide ? track.scrollLeft : track.scrollTop
      var viewport = this.isWide ? track.clientWidth : track.clientHeight
      var center = scrollPos + viewport / 2

      for (var i = 0; i < pieces.length; i++) {
        var el = pieces[i]
        var start = this.isWide ? el.offsetLeft : el.offsetTop
        var end = start + (this.isWide ? el.offsetWidth : el.offsetHeight)
        if (center >= start && center < end) return i
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
  --dp-gallery-mat-bg: color-mix(in srgb, var(--dp-panel-bg, #fff) 38%, var(--dp-subpanel-bg, #f5f3f0));
  --dp-gallery-mat-edge: color-mix(in srgb, var(--dp-panel-border, rgba(107, 93, 82, 0.16)) 85%, transparent);
  --dp-gallery-baseboard: color-mix(in srgb, var(--dp-accent, #6b5d52) 18%, var(--dp-subpanel-bg, #f5f3f0));
  --dp-gallery-frame-mat: color-mix(in srgb, var(--dp-gallery-mat-bg) 55%, #f7f4ef);
  --dp-gallery-frame-border: color-mix(in srgb, var(--dp-accent, #6b5d52) 22%, transparent);

  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  margin-top: clamp(8px, 1.6vw, 14px);
  padding: clamp(10px, 2vw, 16px) clamp(6px, 1.4vw, 12px) clamp(14px, 2.4vw, 20px);
  border-radius: clamp(12px, 2.4vw, 18px);
}

/* Mat runway — differentiated from wall / header zone */
.dp-gallery-wall::before {
  content: '';
  position: absolute;
  inset: 0;
  z-index: 0;
  border-radius: inherit;
  pointer-events: none;
  background:
    radial-gradient(ellipse 85% 70% at 50% 0%, color-mix(in srgb, #fff 14%, transparent) 0%, transparent 58%),
    repeating-linear-gradient(
      90deg,
      transparent 0,
      transparent 52px,
      color-mix(in srgb, var(--dp-gallery-mat-edge) 28%, transparent) 52px,
      color-mix(in srgb, var(--dp-gallery-mat-edge) 28%, transparent) 53px
    ),
    linear-gradient(
      180deg,
      color-mix(in srgb, var(--dp-gallery-mat-bg) 94%, #fff) 0%,
      var(--dp-gallery-mat-bg) 52%,
      color-mix(in srgb, var(--dp-gallery-mat-bg) 88%, var(--dp-text-primary, #3a332c)) 100%
    );
  box-shadow:
    inset 0 3px 0 color-mix(in srgb, var(--dp-accent, #6b5d52) 20%, transparent),
    inset 0 0 0 1px color-mix(in srgb, #fff 36%, transparent),
    inset 0 -2px 0 var(--dp-gallery-mat-edge),
    0 2px 8px color-mix(in srgb, var(--dp-text-primary, #3a332c) 5%, transparent);
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
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.dp-gallery-wall__track {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: clamp(20px, 4vw, 32px);
  overflow-x: hidden;
  overflow-y: auto;
  scroll-snap-type: y mandatory;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
  scrollbar-color: color-mix(in srgb, var(--dp-accent, #6b5d52) 35%, transparent) transparent;
  padding: 8px 4px clamp(20px, 3vw, 28px);
}
.dp-gallery-wall__track::-webkit-scrollbar {
  width: 6px;
}
.dp-gallery-wall__track::-webkit-scrollbar-thumb {
  border-radius: 3px;
  background: color-mix(in srgb, var(--dp-accent, #6b5d52) 32%, transparent);
}
.dp-gallery-wall__track::-webkit-scrollbar-track {
  background: transparent;
}

.dp-gallery-wall__track--wide {
  flex-direction: row;
  align-items: stretch;
  justify-content: flex-start;
  gap: clamp(24px, 3vw, 40px);
  overflow-x: auto;
  overflow-y: hidden;
  scroll-snap-type: x mandatory;
  padding: 12px 8px 28px;
}
.dp-gallery-wall__track--wide::-webkit-scrollbar {
  height: 6px;
  width: auto;
}

.dp-gallery-wall__piece {
  flex: 0 0 auto;
  scroll-snap-align: center;
  animation: dp-gallery-piece-in 0.55s ease both;
}

@keyframes dp-gallery-piece-in {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.dp-gallery-wall__track--wide .dp-gallery-wall__piece {
  animation-name: dp-gallery-piece-in-x;
}

@keyframes dp-gallery-piece-in-x {
  from {
    opacity: 0;
    transform: translateX(16px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.gallery-frame {
  display: block;
  border: none;
  padding: 0;
  margin: 0;
  cursor: pointer;
  background: transparent;
  font-family: inherit;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}
.gallery-frame:focus-visible {
  outline: 2px solid var(--dp-accent, #409eff);
  outline-offset: 4px;
}
.gallery-frame:hover {
  transform: translateY(-3px);
}

.gallery-frame__mat {
  background: linear-gradient(
    145deg,
    color-mix(in srgb, var(--dp-gallery-frame-mat, #f7f4ef) 88%, #fff) 0%,
    var(--dp-gallery-frame-mat, #ebe6dc) 100%
  );
  padding: clamp(10px, 2vw, 16px);
  border: 1px solid var(--dp-gallery-frame-border, color-mix(in srgb, #8b7355 28%, transparent));
  box-shadow:
    inset 0 0 0 1px color-mix(in srgb, #fff 52%, transparent),
    0 4px 18px color-mix(in srgb, var(--dp-text-primary, #3a332c) 12%, transparent),
    0 1px 3px color-mix(in srgb, var(--dp-text-primary, #3a332c) 6%, transparent);
}

.gallery-frame img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
  background: color-mix(in srgb, var(--dp-gallery-frame-mat, #faf9f7) 70%, var(--dp-panel-bg, #fff));
}

.gallery-frame--portrait {
  width: min(240px, 72vw);
}
.gallery-frame--portrait .gallery-frame__mat {
  height: min(360px, 58vh);
}
.gallery-frame--portrait img {
  max-height: calc(min(360px, 58vh) - 32px);
  margin: 0 auto;
}

.gallery-frame--square {
  width: min(280px, 78vw);
}
.gallery-frame--square .gallery-frame__mat {
  height: min(280px, 48vh);
  display: flex;
  align-items: center;
  justify-content: center;
}
.gallery-frame--square img {
  max-height: calc(min(280px, 48vh) - 32px);
  max-width: 100%;
}

.gallery-frame--landscape {
  width: min(420px, 88vw);
}
.gallery-frame--landscape .gallery-frame__mat {
  height: min(240px, 40vh);
  display: flex;
  align-items: center;
  justify-content: center;
}
.gallery-frame--landscape img {
  max-height: calc(min(240px, 40vh) - 32px);
  max-width: 100%;
}

.dp-gallery-wall__track--wide .gallery-frame--portrait {
  width: min(220px, 28vw);
}
.dp-gallery-wall__track--wide .gallery-frame--square {
  width: min(260px, 32vw);
}
.dp-gallery-wall__track--wide .gallery-frame--landscape {
  width: min(400px, 46vw);
}

.dp-gallery-wall__caption {
  margin: 10px 4px 0;
  max-width: min(420px, 88vw);
  font-size: 13px;
  line-height: 1.5;
  color: var(--dp-text-secondary, #606266);
  text-align: center;
  white-space: pre-wrap;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

@media (prefers-reduced-motion: reduce) {
  .dp-gallery-wall__piece,
  .gallery-frame {
    animation: none;
    transition: none;
  }
  .gallery-frame:hover {
    transform: none;
  }
}
</style>
