<template>
  <el-dialog
    :visible.sync="dialogVisible"
    title="编辑我的画廊"
    width="min(96vw, 720px)"
    custom-class="dp-gallery-editor-dialog"
    append-to-body
    :close-on-click-modal="!busy"
    @open="onOpen"
    @closed="onClosed"
  >
    <div class="dp-gallery-editor__toolbar">
      <el-upload
        class="dp-gallery-editor__upload"
        action=""
        accept="image/jpeg,image/png,image/webp,image/gif"
        :show-file-list="false"
        :disabled="busy"
        :http-request="onUploadRequest"
      >
        <button type="button" class="dp-gallery-editor__add" :disabled="busy">
          <i class="el-icon-plus"></i>
          上传作品
        </button>
      </el-upload>
      <button
        type="button"
        class="dp-gallery-editor__preview"
        :disabled="!ownerUserId || busy"
        @click="openPreview"
      >
        预览（他人视角）
      </button>
    </div>
    <p class="dp-gallery-editor__hint">jpg / png / webp / gif，最大 15MB</p>

    <div v-if="loading" class="dp-gallery-editor__loading">
      <i class="el-icon-loading"></i>
      <span>加载中…</span>
    </div>

    <div v-else-if="!rows.length" class="dp-gallery-editor__empty">
      点击「上传作品」添加第一张画
    </div>

    <div v-else class="dp-gallery-editor__list">
      <div v-for="row in rows" :key="row.id" class="dp-gallery-editor__row">
        <div class="dp-gallery-editor__thumb">
          <img
            v-if="row.previewUrl || row.imageUrl"
            :src="galleryFileSrc(row.previewUrl || row.imageUrl)"
            alt=""
            decoding="async"
          >
        </div>
        <div class="dp-gallery-editor__fields">
          <el-input
            v-model="row.captionDraft"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="作品说明（可选）"
          />
          <div class="dp-gallery-editor__actions">
            <button
              type="button"
              class="dp-gallery-editor__btn dp-gallery-editor__btn--save"
              :disabled="busy || !row.dirty"
              @click="saveRow(row)"
            >
              保存
            </button>
            <button
              type="button"
              class="dp-gallery-editor__btn dp-gallery-editor__btn--danger"
              :disabled="busy"
              @click="deleteRow(row)"
            >
              删除
            </button>
          </div>
        </div>
      </div>
    </div>

    <dp-gallery-viewer
      :visible.sync="previewVisible"
      :user-id="ownerUserId"
      subject-name="我"
    />
  </el-dialog>
</template>

<script>
import DpGalleryViewer from '@features/gallery/components/DpGalleryViewer.vue'
import { dpResultSuccess, dpResultData, dpResultMessage } from '@shared/utils/dpApiResult'
import { galleryFileSrc } from '@features/gallery/utils/dpGalleryUrl'

export default {
  name: 'DpGalleryEditor',
  components: { DpGalleryViewer },
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    userId: {
      type: [Number, String],
      default: null
    }
  },
  data() {
    return {
      loading: false,
      busy: false,
      rows: [],
      previewVisible: false
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
    ownerUserId() {
      var uid = Number(this.userId)
      if (!uid || uid <= 0 || isNaN(uid)) return null
      return uid
    }
  },
  methods: {
    galleryFileSrc,
    onOpen() {
      this.loadItems()
    },
    onClosed() {
      this.rows = []
      this.previewVisible = false
    },
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
        if (this.$message) this.$message.success('已删除')
      } catch (e) {
        if (this.$message) this.$message.error('删除失败')
      } finally {
        this.busy = false
      }
    },
    openPreview() {
      if (!this.ownerUserId) return
      this.previewVisible = true
    }
  },
  watch: {
    rows: {
      deep: true,
      handler() {
        var self = this
        this.rows.forEach(function (row) {
          row.dirty = (row.captionDraft || '') !== (row.caption || '')
        })
      }
    }
  }
}
</script>

<style scoped>
.dp-gallery-editor__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}
.dp-gallery-editor__upload {
  display: inline-block;
}
.dp-gallery-editor__add,
.dp-gallery-editor__preview {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 8px;
  border: 1.5px solid var(--dp-warning);
  background: transparent;
  color: var(--dp-warning);
  font-size: 14px;
  font-weight: 600;
  padding: 8px 16px;
  cursor: pointer;
  font-family: inherit;
}
.dp-gallery-editor__add:hover:not(:disabled),
.dp-gallery-editor__preview:hover:not(:disabled) {
  background: var(--dp-warning);
  color: #fff;
}
.dp-gallery-editor__add:disabled,
.dp-gallery-editor__preview:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.dp-gallery-editor__hint {
  margin: 8px 0 12px;
  font-size: 12px;
  color: var(--dp-text-muted);
}
.dp-gallery-editor__loading,
.dp-gallery-editor__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 120px;
  color: var(--dp-text-muted);
}
.dp-gallery-editor__list {
  display: flex;
  flex-direction: column;
  gap: 14px;
  max-height: min(60vh, 520px);
  overflow-y: auto;
}
.dp-gallery-editor__row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  padding: 10px;
  border-radius: 12px;
  border: 1px solid var(--dp-subpanel-border);
  background: var(--dp-subpanel-bg);
}
.dp-gallery-editor__thumb {
  flex: 0 0 96px;
  width: 96px;
  height: 96px;
  border-radius: 10px;
  overflow: hidden;
  background: var(--dp-panel-bg);
}
.dp-gallery-editor__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.dp-gallery-editor__fields {
  flex: 1;
  min-width: 0;
}
.dp-gallery-editor__actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
}
.dp-gallery-editor__btn {
  border: none;
  border-radius: 8px;
  padding: 7px 14px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
}
.dp-gallery-editor__btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.dp-gallery-editor__btn--save {
  background: var(--dp-warning);
  color: #fff;
}
.dp-gallery-editor__btn--danger {
  background: color-mix(in srgb, var(--dp-danger) 14%, var(--dp-panel-bg));
  color: var(--dp-danger);
  border: 1px solid color-mix(in srgb, var(--dp-danger) 35%, transparent);
}
</style>

<style>
.dp-gallery-editor-dialog .el-dialog__body {
  background: var(--dp-panel-bg);
}
</style>
