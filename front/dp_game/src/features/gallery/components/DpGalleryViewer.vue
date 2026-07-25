<template>
  <el-dialog
    :visible.sync="dialogVisible"
    :title="dialogTitle"
    width="min(96vw, 920px)"
    custom-class="dp-gallery-viewer-dialog"
    append-to-body
    :close-on-click-modal="true"
    @open="onOpen"
    @closed="onClosed"
  >
    <div v-if="loading" class="dp-gallery-viewer__loading">
      <i class="el-icon-loading"></i>
      <span>加载画廊…</span>
    </div>

    <div v-else-if="!items.length && !letterContent" class="dp-gallery-viewer__empty">
      该玩家还没有上传画廊作品
    </div>

    <template v-else>
      <p v-if="letterContent" class="dp-gallery-viewer__letter">{{ letterContent }}</p>

      <div
        class="dp-gallery-viewer__track"
        :class="{ 'dp-gallery-viewer__track--empty': !items.length }"
      >
        <button
          v-for="item in items"
          :key="item.id"
          type="button"
          class="dp-gallery-viewer__tile"
          @click="openDetail(item)"
        >
          <img
            :src="galleryFileSrc(item.previewUrl || item.imageUrl)"
            :alt="item.caption || '画廊作品'"
            loading="lazy"
            decoding="async"
          >
        </button>
      </div>
    </template>

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
  </el-dialog>
</template>

<script>
import { dpResultSuccess, dpResultData, dpResultMessage, dpAxiosErrorMessage } from '@shared/utils/dpApiResult'
import { galleryFileSrc } from '@features/gallery/utils/dpGalleryUrl'

export default {
  name: 'DpGalleryViewer',
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    userId: {
      type: [Number, String],
      default: null
    },
    subjectName: {
      type: String,
      default: ''
    }
  },
  data() {
    return {
      loading: false,
      items: [],
      letterContent: '',
      detailVisible: false,
      detailItem: null,
      typewriterText: '',
      typewriterActive: false,
      typewriterTimer: null
    }
  },
  computed: {
    dialogVisible: {
      get() {
        return this.visible
      },
      set(v) {
        this.$emit('update:visible', v)
      }
    },
    dialogTitle() {
      var name = (this.subjectName || '').trim()
      return name ? name + ' 的画廊' : '玩家画廊'
    }
  },
  methods: {
    galleryFileSrc,
    onOpen() {
      this.loadGallery()
    },
    onClosed() {
      this.items = []
      this.letterContent = ''
      this.detailVisible = false
      this.detailItem = null
      this.stopTypewriter()
    },
    isGalleryForbidden(err) {
      var st = err && err.response && err.response.status
      return st === 403 || st === 401
    },
    handleGalleryForbidden() {
      if (this.$message) this.$message.warning('无权限查看画廊')
      this.dialogVisible = false
    },
    async loadGallery() {
      var uid = Number(this.userId)
      if (!uid || uid <= 0 || isNaN(uid)) {
        if (this.$message) this.$message.warning('无法打开画廊')
        this.dialogVisible = false
        return
      }
      this.loading = true
      this.items = []
      this.letterContent = ''
      try {
        var itemsRes = await this.$http.get('/dp/gallery/users/' + uid + '/items')
        if (dpResultSuccess(itemsRes.data)) {
          var data = dpResultData(itemsRes.data) || {}
          this.items = Array.isArray(data.items) ? data.items.slice() : []
        } else if (this.$message) {
          this.$message.error(dpResultMessage(itemsRes.data) || '加载画廊失败')
        }
        try {
          var letterRes = await this.$http.get('/dp/gallery/users/' + uid + '/letter')
          if (dpResultSuccess(letterRes.data)) {
            var letterData = dpResultData(letterRes.data) || {}
            var letter = letterData.letter
            this.letterContent = letter && letter.content ? String(letter.content) : ''
          }
        } catch (letterErr) {
          if (this.isGalleryForbidden(letterErr)) {
            this.handleGalleryForbidden()
            return
          }
          /* 手写信可选 */
        }
      } catch (e) {
        if (this.isGalleryForbidden(e)) {
          this.handleGalleryForbidden()
          return
        }
        if (this.$message) {
          this.$message.error(dpAxiosErrorMessage(e, '加载画廊失败'))
        }
      } finally {
        this.loading = false
      }
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
  },
  beforeDestroy() {
    this.stopTypewriter()
  }
}
</script>

<style scoped>
.dp-gallery-viewer__loading,
.dp-gallery-viewer__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 160px;
  color: var(--dp-text-muted);
  font-size: 14px;
}

.dp-gallery-viewer__letter {
  margin: 0 0 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--dp-subpanel-bg);
  border: 1px solid var(--dp-subpanel-border);
  color: var(--dp-text-secondary);
  font-size: 13px;
  line-height: 1.55;
  white-space: pre-wrap;
}

.dp-gallery-viewer__track {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: min(70vh, 640px);
  overflow-y: auto;
  padding: 4px 2px 8px;
}

.dp-gallery-viewer__tile {
  border: none;
  padding: 0;
  margin: 0;
  background: var(--dp-subpanel-bg);
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.12);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.dp-gallery-viewer__tile:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.18);
}
.dp-gallery-viewer__tile img {
  display: block;
  width: 100%;
  height: auto;
  max-height: 280px;
  object-fit: cover;
}

.dp-gallery-detail__img {
  display: block;
  width: 100%;
  max-height: min(60vh, 520px);
  object-fit: contain;
  border-radius: 10px;
  background: var(--dp-subpanel-bg);
}
.dp-gallery-detail__caption {
  margin: 14px 0 0;
  min-height: 1.5em;
  color: var(--dp-text-primary);
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

@media (min-width: 641px) {
  .dp-gallery-viewer__track {
    flex-direction: row;
    flex-wrap: nowrap;
    overflow-x: auto;
    overflow-y: hidden;
    max-height: none;
    padding-bottom: 12px;
    scroll-snap-type: x proximity;
    -webkit-overflow-scrolling: touch;
  }
  .dp-gallery-viewer__tile {
    flex: 0 0 min(320px, 42vw);
    scroll-snap-align: start;
  }
  .dp-gallery-viewer__tile img {
    width: 100%;
    height: 220px;
    max-height: none;
    object-fit: cover;
  }
}
</style>

<style>
.dp-gallery-viewer-dialog .el-dialog__body {
  padding-top: 8px;
  background: var(--dp-panel-bg);
}
.dp-gallery-detail-dialog .el-dialog__body {
  background: var(--dp-panel-bg);
}
</style>
