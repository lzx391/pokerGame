<template>
  <section class="dp-gallery-items-editor" aria-label="编辑画廊作品">
    <div class="dp-gallery-items-editor__toolbar">
      <el-upload
        ref="upload"
        class="dp-gallery-items-editor__upload"
        action=""
        accept="image/jpeg,image/png,image/webp,image/gif"
        multiple
        :limit="maxBatchFiles"
        :auto-upload="false"
        :show-file-list="false"
        :disabled="busy"
        :on-exceed="onUploadExceed"
        :on-change="onUploadChange"
      >
        <button type="button" class="dp-gallery-items-editor__add" :disabled="busy" aria-label="批量上传作品">
          <i class="el-icon-plus" aria-hidden="true"></i>
          上传作品
          <span class="dp-gallery-items-editor__add-badge">可多选</span>
        </button>
      </el-upload>
      <p class="dp-gallery-items-editor__hint">
        可多选，一次最多 {{ maxBatchFiles }} 张 · jpg / png / webp / gif，最大 15MB
      </p>
    </div>

    <div
      v-if="uploadProgress"
      class="dp-gallery-items-editor__progress-panel"
      role="status"
      aria-live="polite"
    >
      <div class="dp-gallery-items-editor__progress-head">
        <i class="el-icon-loading" aria-hidden="true"></i>
        <span>正在上传 {{ uploadProgress.done }}/{{ uploadProgress.total }}</span>
      </div>
      <div class="dp-gallery-items-editor__progress-track" aria-hidden="true">
        <div
          class="dp-gallery-items-editor__progress-bar"
          :style="{ width: uploadProgressPercent + '%' }"
        ></div>
      </div>
    </div>

    <div v-if="loading" class="dp-gallery-items-editor__status">
      <i class="el-icon-loading" aria-hidden="true"></i>
      <span>加载作品…</span>
    </div>

    <div v-else-if="!rows.length" class="dp-gallery-items-editor__empty">
      <span>点击「上传作品」添加第一张画</span>
      <span class="dp-gallery-items-editor__empty-sub">支持一次多选，最多 {{ maxBatchFiles }} 张</span>
    </div>

    <div v-else class="dp-gallery-items-editor__list">
      <div v-for="row in rows" :key="row.id" class="dp-gallery-items-editor__row">
        <div class="dp-gallery-items-editor__thumb">
          <img
            v-if="row.previewUrl || row.imageUrl"
            :src="galleryFileSrc(row.previewUrl || row.imageUrl)"
            alt=""
            decoding="async"
          >
        </div>
        <div class="dp-gallery-items-editor__fields">
          <el-input
            v-model="row.captionDraft"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="作品说明（可选）"
          />
          <div class="dp-gallery-items-editor__actions">
            <button
              type="button"
              class="dp-gallery-items-editor__btn dp-gallery-items-editor__btn--save"
              :disabled="busy || !row.dirty"
              @click="saveRow(row)"
            >
              保存
            </button>
            <button
              type="button"
              class="dp-gallery-items-editor__btn dp-gallery-items-editor__btn--danger"
              :disabled="busy"
              @click="deleteRow(row)"
            >
              删除
            </button>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script>
import { dpResultSuccess, dpResultData, dpResultMessage } from '@shared/utils/dpApiResult'
import { galleryFileSrc } from '@features/gallery/utils/dpGalleryUrl'

var MAX_BATCH_FILES = 20
var UPLOAD_CONCURRENCY = 2
var MAX_FILE_BYTES = 15 * 1024 * 1024
var ALLOWED_IMAGE_TYPES = {
  'image/jpeg': true,
  'image/png': true,
  'image/webp': true,
  'image/gif': true
}

export default {
  name: 'DpGalleryItemsEditor',
  data() {
    return {
      loading: false,
      busy: false,
      rows: [],
      maxBatchFiles: MAX_BATCH_FILES,
      uploadProgress: null,
      uploadBatchTimer: null
    }
  },
  beforeDestroy() {
    if (this.uploadBatchTimer) {
      clearTimeout(this.uploadBatchTimer)
      this.uploadBatchTimer = null
    }
  },
  computed: {
    uploadProgressPercent() {
      if (!this.uploadProgress || !this.uploadProgress.total) return 0
      return Math.round((this.uploadProgress.done / this.uploadProgress.total) * 100)
    }
  },
  created() {
    this.loadItems()
  },
  methods: {
    galleryFileSrc,
    mapRow(item) {
      return {
        id: item.id,
        imageUrl: item.imageUrl,
        previewUrl: item.previewUrl,
        caption: item.caption || '',
        captionDraft: item.caption || '',
        sortOrder: item.sortOrder != null ? item.sortOrder : 0,
        dirty: false
      }
    },
    emitItems() {
      this.$emit('items-changed', this.rows.map(function (r) {
        return {
          id: r.id,
          imageUrl: r.imageUrl,
          previewUrl: r.previewUrl,
          caption: r.caption,
          sortOrder: r.sortOrder
        }
      }))
    },
    async loadItems() {
      this.loading = true
      try {
        var res = await this.$http.get('/dp/gallery/items')
        if (!dpResultSuccess(res.data)) {
          if (this.$message) this.$message.error(dpResultMessage(res.data) || '加载失败')
          return
        }
        var data = dpResultData(res.data) || {}
        var items = Array.isArray(data.items) ? data.items : []
        this.rows = items.map(this.mapRow)
        this.emitItems()
      } catch (e) {
        if (this.$message) this.$message.error('加载失败')
      } finally {
        this.loading = false
      }
    },
    onUploadExceed() {
      if (this.$message) this.$message.warning('单次最多选择 ' + MAX_BATCH_FILES + ' 张')
    },
    onUploadChange(file, fileList) {
      if (this.busy || !fileList || !fileList.length) return
      if (this.uploadBatchTimer) clearTimeout(this.uploadBatchTimer)
      var self = this
      this.uploadBatchTimer = setTimeout(function () {
        self.uploadBatchTimer = null
        var files = fileList.map(function (entry) {
          return entry.raw
        }).filter(Boolean)
        if (self.$refs.upload) self.$refs.upload.clearFiles()
        if (files.length) self.startBatchUpload(files)
      }, 0)
    },
    isAllowedImageType(file) {
      return !!(file && ALLOWED_IMAGE_TYPES[file.type])
    },
    async uploadSingleFile(file) {
      if (!file) return { ok: false }
      if (file.size > MAX_FILE_BYTES) {
        if (this.$message) this.$message.warning('图片不能超过 15MB')
        return { ok: false }
      }
      if (!this.isAllowedImageType(file)) {
        if (this.$message) this.$message.warning('仅支持 jpg / png / webp / gif')
        return { ok: false }
      }
      var fd = new FormData()
      fd.append('file', file)
      try {
        var res = await this.$http.post('/dp/gallery/items', fd, {
          headers: { 'Content-Type': 'multipart/form-data' }
        })
        if (!dpResultSuccess(res.data)) {
          return { ok: false }
        }
        var data = dpResultData(res.data) || {}
        if (data.item) {
          this.rows.push(this.mapRow(data.item))
          this.emitItems()
          return { ok: true }
        }
      } catch (e) {
        return { ok: false }
      }
      return { ok: false }
    },
    async runUploadPool(files) {
      var self = this
      var index = 0
      var success = 0
      var fail = 0

      async function worker() {
        while (index < files.length) {
          var current = index
          index += 1
          var result = await self.uploadSingleFile(files[current])
          if (result.ok) success += 1
          else fail += 1
          self.uploadProgress = {
            done: success + fail,
            total: files.length
          }
        }
      }

      var workers = []
      var poolSize = Math.min(UPLOAD_CONCURRENCY, files.length)
      for (var i = 0; i < poolSize; i++) {
        workers.push(worker())
      }
      await Promise.all(workers)
      return { success: success, fail: fail }
    },
    async startBatchUpload(files) {
      if (!files || !files.length || this.busy) return
      this.busy = true
      this.uploadProgress = { done: 0, total: files.length }
      try {
        var result = await this.runUploadPool(files)
        if (this.$message) {
          this.$message({
            type: result.fail ? (result.success ? 'warning' : 'error') : 'success',
            message: '上传完成：成功 ' + result.success + ' 张，失败 ' + result.fail + ' 张'
          })
        }
      } finally {
        this.uploadProgress = null
        this.busy = false
      }
    },
    markDirty(row) {
      row.dirty = (row.captionDraft || '') !== (row.caption || '')
    },
    async saveRow(row) {
      this.markDirty(row)
      if (!row.dirty) return
      this.busy = true
      try {
        var params = new URLSearchParams()
        params.append('caption', row.captionDraft || '')
        var res = await this.$http.put('/dp/gallery/items/' + row.id, params)
        if (!dpResultSuccess(res.data)) {
          if (this.$message) this.$message.error(dpResultMessage(res.data) || '保存失败')
          return
        }
        row.caption = row.captionDraft || ''
        row.dirty = false
        this.emitItems()
        if (this.$message) this.$message.success('保存成功')
      } catch (e) {
        if (this.$message) this.$message.error('保存失败')
      } finally {
        this.busy = false
      }
    },
    deleteRow(row) {
      var self = this
      this.$confirm('确定删除这张作品吗？', '删除确认', {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
      }).then(function () {
        self.doDeleteRow(row)
      }).catch(function () {})
    },
    async doDeleteRow(row) {
      this.busy = true
      try {
        var res = await this.$http.delete('/dp/gallery/items/' + row.id)
        if (!dpResultSuccess(res.data)) {
          if (this.$message) this.$message.error(dpResultMessage(res.data) || '删除失败')
          return
        }
        this.rows = this.rows.filter(function (r) {
          return r.id !== row.id
        })
        this.emitItems()
        if (this.$message) this.$message.success('已删除')
      } catch (e) {
        if (this.$message) this.$message.error('删除失败')
      } finally {
        this.busy = false
      }
    }
  },
  watch: {
    rows: {
      deep: true,
      handler() {
        this.rows.forEach(function (row) {
          row.dirty = (row.captionDraft || '') !== (row.caption || '')
        })
      }
    }
  }
}
</script>

<style scoped>
.dp-gallery-items-editor {
  margin-bottom: clamp(16px, 3vw, 24px);
  padding: clamp(12px, 2.5vw, 18px);
  border-radius: 14px;
  border: 1px dashed color-mix(in srgb, var(--dp-warning, #e6a23c) 45%, var(--dp-subpanel-border, #dcdfe6));
  background: color-mix(in srgb, var(--dp-subpanel-bg, #faf8f5) 90%, var(--dp-warning, #e6a23c) 10%);
}

.dp-gallery-items-editor__toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.dp-gallery-items-editor__upload {
  display: inline-block;
}

.dp-gallery-items-editor__add {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 44px;
  border-radius: 10px;
  border: 1.5px solid var(--dp-warning, #e6a23c);
  background: transparent;
  color: var(--dp-warning, #e6a23c);
  font-size: 14px;
  font-weight: 600;
  padding: 8px 16px;
  cursor: pointer;
  font-family: inherit;
}
.dp-gallery-items-editor__add:hover:not(:disabled) {
  background: var(--dp-warning, #e6a23c);
  color: #fff;
}
.dp-gallery-items-editor__add:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.dp-gallery-items-editor__add-badge {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 6px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--dp-warning, #e6a23c) 18%, var(--dp-panel-bg, #fff));
  color: var(--dp-warning, #e6a23c);
  line-height: 1.2;
}
.dp-gallery-items-editor__add:hover:not(:disabled) .dp-gallery-items-editor__add-badge {
  background: rgba(255, 255, 255, 0.22);
  color: #fff;
}

.dp-gallery-items-editor__hint {
  margin: 0;
  font-size: 12px;
  color: var(--dp-text-muted, #909399);
}

.dp-gallery-items-editor__progress-panel {
  margin-bottom: 12px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid color-mix(in srgb, var(--dp-warning, #e6a23c) 35%, var(--dp-subpanel-border, #e4e7ed));
  background: color-mix(in srgb, var(--dp-warning, #e6a23c) 8%, var(--dp-panel-bg, #fff));
}

.dp-gallery-items-editor__progress-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--dp-warning, #e6a23c);
}

.dp-gallery-items-editor__progress-track {
  height: 8px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--dp-warning, #e6a23c) 12%, var(--dp-subpanel-bg, #f5f7fa));
  overflow: hidden;
}

.dp-gallery-items-editor__progress-bar {
  height: 100%;
  border-radius: inherit;
  background: var(--dp-warning, #e6a23c);
  transition: width 0.2s ease;
}

.dp-gallery-items-editor__status,
.dp-gallery-items-editor__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 80px;
  color: var(--dp-text-muted, #909399);
  font-size: 14px;
  text-align: center;
}

.dp-gallery-items-editor__empty-sub {
  font-size: 12px;
  color: color-mix(in srgb, var(--dp-warning, #e6a23c) 70%, var(--dp-text-muted, #909399));
}

.dp-gallery-items-editor__list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-height: min(40vh, 360px);
  overflow-y: auto;
}

.dp-gallery-items-editor__row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 10px;
  border-radius: 12px;
  border: 1px solid var(--dp-subpanel-border, #e4e7ed);
  background: var(--dp-panel-bg, #fff);
}

.dp-gallery-items-editor__thumb {
  flex: 0 0 72px;
  width: 72px;
  height: 72px;
  border-radius: 8px;
  overflow: hidden;
  background: var(--dp-subpanel-bg, #f5f7fa);
}
.dp-gallery-items-editor__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.dp-gallery-items-editor__fields {
  flex: 1;
  min-width: 0;
}

.dp-gallery-items-editor__actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}

.dp-gallery-items-editor__btn {
  min-height: 44px;
  border: none;
  border-radius: 8px;
  padding: 7px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
}
.dp-gallery-items-editor__btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.dp-gallery-items-editor__btn--save {
  background: var(--dp-warning, #e6a23c);
  color: #fff;
}
.dp-gallery-items-editor__btn--danger {
  background: color-mix(in srgb, var(--dp-danger, #f56c6c) 14%, var(--dp-panel-bg, #fff));
  color: var(--dp-danger, #f56c6c);
  border: 1px solid color-mix(in srgb, var(--dp-danger, #f56c6c) 35%, transparent);
}
</style>
