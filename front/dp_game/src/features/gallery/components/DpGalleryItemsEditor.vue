<template>
  <section class="dp-gallery-items-editor" aria-label="编辑画廊作品">
    <div class="dp-gallery-items-editor__toolbar">
      <el-upload
        class="dp-gallery-items-editor__upload"
        action=""
        accept="image/jpeg,image/png,image/webp,image/gif"
        :show-file-list="false"
        :disabled="busy"
        :http-request="onUploadRequest"
      >
        <button type="button" class="dp-gallery-items-editor__add" :disabled="busy" aria-label="上传作品">
          <i class="el-icon-plus" aria-hidden="true"></i>
          上传作品
        </button>
      </el-upload>
      <p class="dp-gallery-items-editor__hint">jpg / png / webp / gif，最大 15MB</p>
    </div>

    <div v-if="loading" class="dp-gallery-items-editor__status">
      <i class="el-icon-loading" aria-hidden="true"></i>
      <span>加载作品…</span>
    </div>

    <div v-else-if="!rows.length" class="dp-gallery-items-editor__empty">
      点击「上传作品」添加第一张画
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

export default {
  name: 'DpGalleryItemsEditor',
  data() {
    return {
      loading: false,
      busy: false,
      rows: []
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
    async onUploadRequest(options) {
      var file = options && options.file
      if (!file) return
      if (file.size > 15 * 1024 * 1024) {
        if (this.$message) this.$message.warning('图片不能超过 15MB')
        return
      }
      var fd = new FormData()
      fd.append('file', file)
      this.busy = true
      try {
        var res = await this.$http.post('/dp/gallery/items', fd, {
          headers: { 'Content-Type': 'multipart/form-data' }
        })
        if (!dpResultSuccess(res.data)) {
          if (this.$message) this.$message.error(dpResultMessage(res.data) || '上传失败')
          return
        }
        var data = dpResultData(res.data) || {}
        if (data.item) {
          this.rows.push(this.mapRow(data.item))
          this.emitItems()
          if (this.$message) this.$message.success('上传成功')
        }
      } catch (e) {
        if (this.$message) this.$message.error('上传失败')
      } finally {
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

.dp-gallery-items-editor__hint {
  margin: 0;
  font-size: 12px;
  color: var(--dp-text-muted, #909399);
}

.dp-gallery-items-editor__status,
.dp-gallery-items-editor__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 80px;
  color: var(--dp-text-muted, #909399);
  font-size: 14px;
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
