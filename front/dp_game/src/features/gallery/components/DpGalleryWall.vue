<template>
  <section class="dp-gallery-wall" aria-label="画廊作品墙">
    <div v-if="loading" class="dp-gallery-wall__status">
      <i class="el-icon-loading" aria-hidden="true"></i>
      <span>加载作品…</span>
    </div>

    <p v-else-if="!items.length" class="dp-gallery-wall__empty">
      {{ emptyText }}
    </p>

    <div
      v-else
      class="dp-gallery-wall__track"
      :class="{ 'dp-gallery-wall__track--wide': isWide }"
      role="list"
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

    <el-dialog
      :visible.sync="detailVisible"
      width="min(94vw, 720px)"
      custom-class="dp-gallery-detail-dialog"
      append-to-body
      :close-on-click-modal="true"
      @closed="stopTypewriter"
    >
      <div v-if="detailItem" class="dp-gallery-detail">
        <img
          class="dp-gallery-detail__img"
          :src="galleryFileSrc(detailItem.imageUrl)"
          :alt="detailItem.caption || '画廊作品'"
          decoding="async"
        >
        <p class="dp-gallery-detail__caption">
          <span>{{ typewriterText }}</span>
          <span v-if="typewriterActive" class="dp-gallery-detail__cursor" aria-hidden="true">|</span>
        </p>
      </div>
    </el-dialog>
  </section>
</template>

<script>
import { galleryFileSrc } from '@features/gallery/utils/dpGalleryUrl'
import { dpGalleryFrameClass } from '@features/gallery/utils/dpGalleryFrame'

var WIDE_BP = 768

export default {
  name: 'DpGalleryWall',
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
      typewriterText: '',
      typewriterActive: false,
      typewriterTimer: null,
      resizeObserver: null
    }
  },
  watch: {
    items: {
      deep: true,
      handler() {
        this.frameClasses = {}
      }
    }
  },
  mounted() {
    this.syncWide()
    if (typeof window !== 'undefined') {
      window.addEventListener('resize', this.syncWide, { passive: true })
    }
  },
  beforeDestroy() {
    if (typeof window !== 'undefined') {
      window.removeEventListener('resize', this.syncWide)
    }
    this.stopTypewriter()
  },
  methods: {
    galleryFileSrc,
    syncWide() {
      this.isWide = typeof window !== 'undefined' && window.innerWidth >= WIDE_BP
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
      if (typeof window !== 'undefined' &&
        window.matchMedia &&
        window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        return {}
      }
      return { animationDelay: (index * 0.08) + 's' }
    },
    openDetail(item) {
      this.detailItem = item
      this.detailVisible = true
      this.startTypewriter(item && item.caption ? String(item.caption) : '')
    },
    startTypewriter(fullText) {
      this.stopTypewriter()
      var text = fullText || ''
      if (!text) {
        this.typewriterText = '（无说明）'
        this.typewriterActive = false
        return
      }
      if (
        typeof window !== 'undefined' &&
        window.matchMedia &&
        window.matchMedia('(prefers-reduced-motion: reduce)').matches
      ) {
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
      }, 42)
    },
    stopTypewriter() {
      if (this.typewriterTimer != null) {
        clearInterval(this.typewriterTimer)
        this.typewriterTimer = null
      }
      this.typewriterActive = false
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
    inset 0 1px 0 color-mix(in srgb, #fff 36%, transparent),
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

.dp-gallery-wall__track {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: clamp(20px, 4vw, 32px);
  overflow-y: auto;
  overflow-x: hidden;
  scroll-snap-type: y mandatory;
  -webkit-overflow-scrolling: touch;
  padding: 8px 4px clamp(20px, 3vw, 28px);
  flex: 1;
  min-height: 0;
}

.dp-gallery-wall__track--wide {
  flex-direction: row;
  overflow-x: auto;
  overflow-y: hidden;
  scroll-snap-type: x mandatory;
  gap: clamp(24px, 3vw, 40px);
  padding: 12px 8px 28px;
  align-items: stretch;
}

.dp-gallery-wall__piece {
  flex: 0 0 auto;
  scroll-snap-align: center;
  scroll-snap-stop: always;
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

.dp-gallery-detail__img {
  display: block;
  width: 100%;
  max-height: min(60vh, 520px);
  object-fit: contain;
  border-radius: 10px;
  background: var(--dp-subpanel-bg, #faf8f5);
}
.dp-gallery-detail__caption {
  margin: 14px 0 0;
  min-height: 1.5em;
  color: var(--dp-text-primary, #303133);
  font-size: 15px;
  line-height: 1.6;
  white-space: pre-wrap;
}
.dp-gallery-detail__cursor {
  display: inline-block;
  margin-left: 2px;
  animation: dp-gallery-cursor-blink 0.8s step-end infinite;
}
@keyframes dp-gallery-cursor-blink {
  50% { opacity: 0; }
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
  .dp-gallery-detail__cursor {
    animation: none;
  }
}
</style>

<style>
.dp-gallery-detail-dialog .el-dialog__body {
  background: var(--dp-panel-bg, #fff);
}
</style>
