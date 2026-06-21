<template>
  <section class="dp-gallery-letter">
    <div class="dp-gallery-letter__body">
      <div v-if="loading" class="dp-gallery-letter__status">
        <i class="el-icon-loading" aria-hidden="true"></i>
        <span>加载介绍信…</span>
      </div>

      <template v-else-if="editable">
        <label class="dp-gallery-letter__label" for="dp-gallery-letter-input">写给访客的话</label>
        <el-input
          id="dp-gallery-letter-input"
          v-model="draft"
          type="textarea"
          :rows="5"
          maxlength="2000"
          show-word-limit
          placeholder="写一段介绍，让访客了解你的画廊…"
          :disabled="saving"
        />
        <div class="dp-gallery-letter__actions">
          <button
            type="button"
            class="dp-gallery-letter__btn dp-gallery-letter__btn--primary"
            :disabled="saving || !draftDirty"
            aria-label="保存介绍信"
            @click="saveLetter"
          >
            <span v-if="!saving">保存</span>
            <span v-else><i class="el-icon-loading"></i> 保存中…</span>
          </button>
          <button
            type="button"
            class="dp-gallery-letter__btn dp-gallery-letter__btn--ghost"
            :disabled="saving || !hasContent"
            aria-label="清空介绍信"
            @click="clearLetter"
          >
            清空
          </button>
        </div>
      </template>

      <template v-else>
        <p v-if="content" class="dp-gallery-letter__text">{{ content }}</p>
        <p v-else class="dp-gallery-letter__empty">暂无介绍信</p>
      </template>
    </div>
  </section>
</template>

<script>
import { dpResultSuccess, dpResultData, dpResultMessage, dpAxiosErrorMessage } from '@shared/utils/dpApiResult'

export default {
  name: 'DpGalleryLetterPanel',
  props: {
    /** 只读展示内容 */
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
      draft: '',
      savedContent: '',
      saving: false
    }
  },
  computed: {
    hasContent() {
      return !!(this.savedContent && this.savedContent.trim())
    },
    draftDirty() {
      return (this.draft || '') !== (this.savedContent || '')
    }
  },
  watch: {
    content: {
      immediate: true,
      handler(v) {
        var text = v != null ? String(v) : ''
        this.savedContent = text
        if (!this.draftDirty || !this.editable) {
          this.draft = text
        }
      }
    },
    editable(v) {
      if (v) {
        this.draft = this.savedContent || ''
      }
    }
  },
  methods: {
    async saveLetter() {
      var text = (this.draft || '').trim()
      if (!text) {
        if (this.$message) this.$message.warning('介绍信内容不能为空')
        return
      }
      this.saving = true
      try {
        var params = new URLSearchParams()
        params.append('letter', text)
        var res = await this.$http.post('/dp/gallery/writeLetter', params)
        if (!dpResultSuccess(res.data)) {
          if (this.$message) this.$message.error(dpResultMessage(res.data) || '保存失败')
          return
        }
        this.savedContent = text
        this.draft = text
        this.$emit('update:content', text)
        if (this.$message) this.$message.success('介绍信已保存')
      } catch (e) {
        if (this.$message) this.$message.error(dpAxiosErrorMessage(e, '保存失败'))
      } finally {
        this.saving = false
      }
    },
    clearLetter() {
      var self = this
      if (!this.$confirm) {
        this.doClearLetter()
        return
      }
      this.$confirm('确定清空介绍信吗？', '清空确认', {
        type: 'warning',
        confirmButtonText: '清空',
        cancelButtonText: '取消'
      }).then(function () {
        self.doClearLetter()
      }).catch(function () {})
    },
    async doClearLetter() {
      this.saving = true
      try {
        var res = await this.$http.delete('/dp/gallery/letter')
        if (!dpResultSuccess(res.data)) {
          if (this.$message) this.$message.error(dpResultMessage(res.data) || '清空失败')
          return
        }
        this.savedContent = ''
        this.draft = ''
        this.$emit('update:content', '')
        if (this.$message) this.$message.success('介绍信已清空')
      } catch (e) {
        if (this.$message) this.$message.error(dpAxiosErrorMessage(e, '清空失败'))
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style scoped>
.dp-gallery-letter {
  margin-bottom: clamp(16px, 3vw, 24px);
  border-radius: 14px;
  border: 1px solid var(--dp-subpanel-border, rgba(0, 0, 0, 0.08));
  background: linear-gradient(
    165deg,
    color-mix(in srgb, var(--dp-subpanel-bg, #faf8f5) 92%, var(--dp-accent, #409eff) 8%),
    var(--dp-subpanel-bg, #faf8f5)
  );
  box-shadow: 0 2px 14px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.dp-gallery-letter__body {
  padding: 0 16px 16px;
}

.dp-gallery-letter__label {
  display: block;
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--dp-text-secondary, #606266);
}

.dp-gallery-letter__text {
  margin: 0;
  padding: 12px 14px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--dp-panel-bg, #fff) 88%, transparent);
  border: 1px solid color-mix(in srgb, var(--dp-subpanel-border, #e4e7ed) 70%, transparent);
  color: var(--dp-text-secondary, #606266);
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
}

.dp-gallery-letter__empty {
  margin: 0;
  padding: 10px 0;
  color: var(--dp-text-muted, #909399);
  font-size: 14px;
  font-style: italic;
}

.dp-gallery-letter__status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
  color: var(--dp-text-muted, #909399);
  font-size: 14px;
}

.dp-gallery-letter__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.dp-gallery-letter__btn {
  min-height: 44px;
  min-width: 44px;
  padding: 10px 18px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  font-family: inherit;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}
.dp-gallery-letter__btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.dp-gallery-letter__btn--primary {
  border: none;
  background: var(--dp-warning, #e6a23c);
  color: #fff;
}
.dp-gallery-letter__btn--primary:hover:not(:disabled) {
  filter: brightness(1.05);
}
.dp-gallery-letter__btn--ghost {
  border: 1.5px solid var(--dp-input-border, #dcdfe6);
  background: var(--dp-btn-ghost-bg, transparent);
  color: var(--dp-text-secondary, #606266);
}
.dp-gallery-letter__btn--ghost:hover:not(:disabled) {
  border-color: var(--dp-danger, #f56c6c);
  color: var(--dp-danger, #f56c6c);
}

@media (prefers-reduced-motion: reduce) {
  .dp-gallery-letter__btn {
    transition: none;
  }
}
</style>
