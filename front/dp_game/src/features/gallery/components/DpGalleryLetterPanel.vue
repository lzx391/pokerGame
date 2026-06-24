<template>
  <section class="dp-gallery-letter">
    <div
      class="dp-gallery-letter-paper"
      :class="paperClasses"
    >
      <div class="dp-gallery-letter-paper__body">
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
            class="dp-gallery-letter__textarea"
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
    </div>
  </section>
</template>

<script>
import { dpResultSuccess, dpResultData, dpResultMessage, dpAxiosErrorMessage } from '@shared/utils/dpApiResult'

export default {
  name: 'DpGalleryLetterPanel',
  props: {
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
    },
    paperUnfolding: {
      type: Boolean,
      default: false
    },
    paperReady: {
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
    },
    paperClasses() {
      if (this.editable) {
        return {
          'dp-gallery-letter-paper--unfolding': true,
          'dp-gallery-letter-paper--ready': true
        }
      }
      return {
        'dp-gallery-letter-paper--unfolding': this.paperUnfolding,
        'dp-gallery-letter-paper--ready': this.paperReady
      }
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
  margin-bottom: 0;
}

.dp-gallery-letter-paper {
  --dp-letter-line-height: 32px;
  --dp-letter-scroll-roll: 18px;
  --dp-letter-scroll-body-offset: calc(var(--dp-letter-scroll-roll) + 10px);
  position: relative;
  max-height: 0;
  overflow: hidden;
  border-radius: 2px;
  border: 1px solid transparent;
  background-color: #e8d4a0;
  background-image:
    linear-gradient(
      90deg,
      rgba(90, 68, 38, 0.1) 0%,
      transparent 5%,
      transparent 95%,
      rgba(90, 68, 38, 0.1) 100%
    ),
    repeating-linear-gradient(
      0deg,
      transparent,
      transparent calc(var(--dp-letter-line-height) - 1px),
      var(--dp-letter-line, rgba(107, 82, 52, 0.14)) calc(var(--dp-letter-line-height) - 1px),
      var(--dp-letter-line, rgba(107, 82, 52, 0.14)) var(--dp-letter-line-height)
    ),
    repeating-radial-gradient(
      circle at 23% 41%,
      rgba(90, 68, 38, 0.04) 0 1px,
      transparent 1px 3px
    ),
    repeating-radial-gradient(
      circle at 71% 63%,
      rgba(255, 248, 220, 0.06) 0 1px,
      transparent 1px 4px
    ),
    var(--dp-letter-paper, linear-gradient(168deg, #f8edd4 0%, #e8d4a0 100%));
  background-size: 100% 100%, 100% var(--dp-letter-line-height), auto, auto, 100% 100%;
  background-position: 0 0, 0 var(--dp-letter-scroll-body-offset), 0 0, 0 0, 0 0;
  background-clip: padding-box;
  font-family: var(--dp-letter-font, 'Ma Shan Zheng', 'STKaiti', 'KaiTi', cursive, serif);
  color: var(--dp-letter-ink, #3a2e22);
  box-shadow: none;
  transition:
    max-height var(--dp-letter-unfold-duration, 400ms) ease-out,
    border-color 0.25s ease,
    box-shadow 0.25s ease;
}

/* Top scroll rod — rolled parchment cylinder (pure CSS gradients) */
.dp-gallery-letter-paper::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 3;
  height: var(--dp-letter-scroll-roll);
  pointer-events: none;
  border-top: 2px solid #5a4528;
  border-radius: 0 0 42% 42% / 0 0 72% 72%;
  background:
    linear-gradient(
      180deg,
      #5a4528 0%,
      #6b5430 6%,
      #8b6f3a 22%,
      #a8864a 42%,
      #c4a574 62%,
      #d8c090 78%,
      #e8d4a0 92%,
      #f0ddb0 100%
    );
  box-shadow:
    inset 0 -4px 8px rgba(60, 45, 25, 0.38),
    inset 0 2px 4px rgba(255, 248, 220, 0.22),
    0 3px 6px rgba(42, 32, 20, 0.18);
}

/* Bottom scroll rod — mirror of top with heavier drop shadow */
.dp-gallery-letter-paper::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 3;
  height: var(--dp-letter-scroll-roll);
  pointer-events: none;
  border-bottom: 2px solid #5a4528;
  border-radius: 42% 42% 0 0 / 72% 72% 0 0;
  background:
    linear-gradient(
      0deg,
      #5a4528 0%,
      #6b5430 6%,
      #8b6f3a 22%,
      #a8864a 42%,
      #c4a574 62%,
      #d8c090 78%,
      #e8d4a0 92%,
      #f0ddb0 100%
    );
  box-shadow:
    inset 0 4px 8px rgba(60, 45, 25, 0.38),
    inset 0 -2px 4px rgba(255, 248, 220, 0.18),
    0 6px 14px rgba(42, 32, 20, 0.28),
    0 2px 4px rgba(42, 32, 20, 0.16);
}

.dp-gallery-letter-paper--unfolding {
  min-height: calc(48px + var(--dp-letter-scroll-roll) * 2);
  border-color: var(--dp-letter-border, rgba(139, 115, 75, 0.32));
  box-shadow:
    inset 0 1px 0 rgba(255, 248, 230, 0.35),
    inset 0 -2px 8px rgba(90, 68, 38, 0.06),
    0 22px 52px rgba(42, 32, 20, 0.3),
    0 8px 20px rgba(42, 32, 20, 0.16);
}

.dp-gallery-letter-paper--ready {
  max-height: min(72vh, 560px);
  overflow-x: hidden;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
}

.dp-gallery-letter-paper__body {
  position: relative;
  z-index: 1;
  padding:
    calc(var(--dp-letter-scroll-roll) + 12px)
    clamp(24px, 6vw, 36px)
    calc(var(--dp-letter-scroll-roll) + 14px);
  min-height: 160px;
}

.dp-gallery-letter__label {
  display: block;
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-family: var(--dp-font-ui, inherit);
  color: var(--dp-text-muted, #909399);
}

.dp-gallery-letter__text {
  margin: 0;
  font-size: 20px;
  line-height: var(--dp-letter-line-height, 32px);
  white-space: pre-wrap;
  font-family: inherit;
  color: var(--dp-letter-ink, #3a2e22);
  letter-spacing: 0.02em;
}

.dp-gallery-letter__empty {
  margin: 0;
  padding: 4px 0;
  color: color-mix(in srgb, var(--dp-letter-ink, #3a2e22) 45%, transparent);
  font-size: 20px;
  line-height: var(--dp-letter-line-height, 32px);
  font-style: italic;
}

.dp-gallery-letter__status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 0;
  color: var(--dp-text-muted, #909399);
  font-size: 14px;
}

.dp-gallery-letter__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid var(--dp-letter-border, color-mix(in srgb, var(--dp-accent, #6b5d52) 16%, transparent));
}

.dp-gallery-letter__textarea ::v-deep .el-textarea__inner {
  background: color-mix(in srgb, var(--dp-letter-paper-tint, #f0e0b8) 72%, transparent);
  border-color: var(--dp-letter-border, rgba(139, 115, 75, 0.32));
  font-family: inherit;
  font-size: 20px;
  line-height: var(--dp-letter-line-height, 32px);
  color: var(--dp-letter-ink, #3a2e22);
  letter-spacing: 0.02em;
  resize: vertical;
  box-shadow: inset 0 1px 3px rgba(90, 68, 38, 0.06);
}

.dp-gallery-letter__btn {
  min-height: 40px;
  min-width: 44px;
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.04em;
  cursor: pointer;
  font-family: var(--dp-font-ui, inherit);
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

.dp-game-root[data-dp-eco-mode='true'] .dp-gallery-letter-paper {
  transition: max-height 0.15s ease-out, border-color 0.15s ease;
}

@media (prefers-reduced-motion: reduce) {
  .dp-gallery-letter-paper {
    transition: max-height 0.15s ease-out, border-color 0.15s ease;
  }
  .dp-gallery-letter-paper::before,
  .dp-gallery-letter-paper::after {
    box-shadow:
      inset 0 -3px 6px rgba(60, 45, 25, 0.32),
      0 2px 4px rgba(42, 32, 20, 0.14);
  }
  .dp-gallery-letter-paper::after {
    box-shadow:
      inset 0 3px 6px rgba(60, 45, 25, 0.32),
      0 4px 10px rgba(42, 32, 20, 0.2);
  }
  .dp-gallery-letter__btn {
    transition: none;
  }
}

@media (max-width: 480px) {
  .dp-gallery-letter-paper {
    --dp-letter-scroll-roll: 16px;
  }
  .dp-gallery-letter-paper__body {
    padding-left: clamp(18px, 5vw, 28px);
    padding-right: clamp(18px, 5vw, 28px);
  }
}
</style>
