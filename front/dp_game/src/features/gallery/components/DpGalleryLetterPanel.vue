<template>
  <section class="dp-gallery-letter" :class="letterRootClasses">
    <div
      class="dp-gallery-letter-paper"
      :class="paperClasses"
    >
      <div class="dp-gallery-letter-paper__noise" aria-hidden="true"></div>
      <span
        v-if="showReadOrnaments"
        class="dp-gallery-letter-paper__corner dp-gallery-letter-paper__corner--tl"
        aria-hidden="true"
      ></span>
      <span
        v-if="showReadOrnaments"
        class="dp-gallery-letter-paper__corner dp-gallery-letter-paper__corner--tr"
        aria-hidden="true"
      ></span>
      <span
        v-if="showReadOrnaments"
        class="dp-gallery-letter-paper__corner dp-gallery-letter-paper__corner--bl"
        aria-hidden="true"
      ></span>
      <span
        v-if="showReadOrnaments"
        class="dp-gallery-letter-paper__corner dp-gallery-letter-paper__corner--br"
        aria-hidden="true"
      ></span>
      <span
        v-if="showWaxSeal"
        class="dp-gallery-letter-paper__wax"
        aria-hidden="true"
      ></span>

      <div class="dp-gallery-letter-paper__body">
        <header
          v-if="showPlacard"
          class="dp-gallery-letter__placard"
        >
          <p class="dp-gallery-letter__placard-eyebrow">介绍信</p>
          <p class="dp-gallery-letter__placard-author">
            来自 <span class="dp-gallery-letter__placard-name">{{ letterAuthorName }}</span>
          </p>
          <p v-if="formattedUpdatedAt" class="dp-gallery-letter__placard-date">{{ formattedUpdatedAt }}</p>
        </header>

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
            :rows="6"
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
              <span v-if="!saving">落笔封存</span>
              <span v-else><i class="el-icon-loading"></i> 封存中…</span>
            </button>
            <button
              type="button"
              class="dp-gallery-letter__btn dp-gallery-letter__btn--ghost"
              :disabled="saving || !hasContent"
              aria-label="清空介绍信"
              @click="requestClearLetter"
            >
              撕毁重撰
            </button>
          </div>
        </template>

        <template v-else>
          <p v-if="content" class="dp-gallery-letter__text">{{ content }}</p>
          <p v-else class="dp-gallery-letter__empty">暂无介绍信</p>
        </template>
      </div>
    </div>

    <Transition name="dp-gallery-letter-confirm">
      <div
        v-if="clearConfirmVisible"
        class="dp-gallery-letter__confirm"
        role="alertdialog"
        aria-labelledby="dp-gallery-letter-confirm-title"
        aria-describedby="dp-gallery-letter-confirm-desc"
        @click.self="cancelClear"
      >
        <div class="dp-gallery-letter__confirm-card">
          <p id="dp-gallery-letter-confirm-title" class="dp-gallery-letter__confirm-title">撕毁此信？</p>
          <p id="dp-gallery-letter-confirm-desc" class="dp-gallery-letter__confirm-desc">
            清空后访客将看不到任何介绍文字，此操作不可撤销。
          </p>
          <div class="dp-gallery-letter__confirm-actions">
            <button
              type="button"
              class="dp-gallery-letter__btn dp-gallery-letter__btn--ghost"
              @click="cancelClear"
            >
              保留
            </button>
            <button
              type="button"
              class="dp-gallery-letter__btn dp-gallery-letter__btn--danger"
              :disabled="saving"
              @click="confirmClear"
            >
              确认撕毁
            </button>
          </div>
        </div>
      </div>
    </Transition>
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
    letterAuthorName: {
      type: String,
      default: ''
    },
    updatedAt: {
      type: [String, Number, Date, Array],
      default: null
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
      saving: false,
      clearConfirmVisible: false
    }
  },
  computed: {
    hasContent() {
      return !!(this.savedContent && this.savedContent.trim())
    },
    draftDirty() {
      return (this.draft || '') !== (this.savedContent || '')
    },
    showPlacard() {
      return !this.loading && !this.editable && !!(this.letterAuthorName && String(this.letterAuthorName).trim())
    },
    showReadOrnaments() {
      return !this.loading && !this.editable && this.paperReady
    },
    showWaxSeal() {
      return this.showReadOrnaments && this.hasContent
    },
    formattedUpdatedAt() {
      return this.formatLetterDate(this.updatedAt)
    },
    letterRootClasses() {
      return {
        'dp-gallery-letter--editable': this.editable,
        'dp-gallery-letter--reading': !this.editable && this.paperReady
      }
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
    },
    clearConfirmVisible(v) {
      if (v) {
        document.addEventListener('keydown', this.onConfirmKeydown)
      } else {
        document.removeEventListener('keydown', this.onConfirmKeydown)
      }
    }
  },
  beforeDestroy() {
    document.removeEventListener('keydown', this.onConfirmKeydown)
  },
  methods: {
    formatLetterDate(raw) {
      if (raw == null || raw === '') return ''
      var d = null
      if (Array.isArray(raw) && raw.length >= 3) {
        d = new Date(raw[0], (raw[1] || 1) - 1, raw[2], raw[3] || 0, raw[4] || 0, raw[5] || 0)
      } else if (typeof raw === 'number') {
        d = new Date(raw)
      } else {
        d = new Date(raw)
      }
      if (!d || isNaN(d.getTime())) return ''
      try {
        return new Intl.DateTimeFormat('zh-CN', {
          year: 'numeric',
          month: 'long',
          day: 'numeric'
        }).format(d)
      } catch (e) {
        return d.toLocaleDateString('zh-CN')
      }
    },
    onConfirmKeydown(e) {
      if (!this.clearConfirmVisible) return
      if (e.key === 'Escape') {
        e.preventDefault()
        this.cancelClear()
      }
    },
    requestClearLetter() {
      this.clearConfirmVisible = true
    },
    cancelClear() {
      this.clearConfirmVisible = false
    },
    confirmClear() {
      this.clearConfirmVisible = false
      this.doClearLetter()
    },
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
        this.$emit('saved')
        if (this.$message) this.$message.success('介绍信已保存')
      } catch (e) {
        if (this.$message) this.$message.error(dpAxiosErrorMessage(e, '保存失败'))
      } finally {
        this.saving = false
      }
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
        this.$emit('cleared')
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
  position: relative;
  margin-bottom: 0;
}

.dp-gallery-letter-paper {
  --dp-letter-line-height: 32px;
  --dp-letter-edge-accent: 5px;
  --dp-letter-body-inset-y: 20px;
  --dp-letter-line-start: calc(var(--dp-letter-body-inset-y) + 2px);
  position: relative;
  max-height: 0;
  overflow: hidden;
  border-radius: 3px;
  border: 1px solid transparent;
  background-color: var(--dp-letter-paper-tint, #e8d4a0);
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
    var(--dp-letter-paper, linear-gradient(168deg, #f8edd4 0%, #e8d4a0 100%));
  background-size: 100% 100%, 100% var(--dp-letter-line-height), 100% 100%;
  background-position: 0 0, 0 var(--dp-letter-line-start), 0 0;
  background-clip: padding-box;
  font-family: var(--dp-letter-font, 'Ma Shan Zheng', 'STKaiti', 'KaiTi', cursive, serif);
  color: var(--dp-letter-ink, #3a2e22);
  box-shadow: none;
  transition:
    max-height var(--dp-letter-unfold-duration, 400ms) ease-out,
    border-color 0.25s ease,
    box-shadow 0.25s ease;
}

.dp-gallery-letter-paper__noise {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  opacity: var(--dp-letter-noise-opacity, 0.045);
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.85' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
  background-size: 180px 180px;
  mix-blend-mode: multiply;
}

.dp-gallery-letter-paper__corner {
  position: absolute;
  z-index: 2;
  width: 22px;
  height: 22px;
  pointer-events: none;
  opacity: 0.42;
  border-color: color-mix(in srgb, var(--dp-letter-ink, #3a2e22) 28%, transparent);
}
.dp-gallery-letter-paper__corner--tl {
  top: calc(var(--dp-letter-edge-accent) + 10px);
  left: 14px;
  border-top: 1.5px solid;
  border-left: 1.5px solid;
}
.dp-gallery-letter-paper__corner--tr {
  top: calc(var(--dp-letter-edge-accent) + 10px);
  right: 14px;
  border-top: 1.5px solid;
  border-right: 1.5px solid;
}
.dp-gallery-letter-paper__corner--bl {
  bottom: calc(var(--dp-letter-edge-accent) + 10px);
  left: 14px;
  border-bottom: 1.5px solid;
  border-left: 1.5px solid;
}
.dp-gallery-letter-paper__corner--br {
  bottom: calc(var(--dp-letter-edge-accent) + 10px);
  right: 14px;
  border-bottom: 1.5px solid;
  border-right: 1.5px solid;
}

.dp-gallery-letter-paper__wax {
  position: absolute;
  z-index: 4;
  right: clamp(28px, 8vw, 44px);
  bottom: calc(var(--dp-letter-edge-accent) + 16px);
  width: 38px;
  height: 38px;
  border-radius: 50%;
  pointer-events: none;
  background:
    radial-gradient(circle at 32% 28%, color-mix(in srgb, var(--dp-letter-wax, #a83232) 55%, #fff 45%), transparent 58%),
    radial-gradient(circle at 50% 55%, var(--dp-letter-wax, #a83232), color-mix(in srgb, var(--dp-letter-wax, #a83232) 70%, #000 30%));
  box-shadow:
    inset 0 2px 4px rgba(255, 255, 255, 0.22),
    inset 0 -3px 6px rgba(0, 0, 0, 0.28),
    0 4px 12px rgba(42, 32, 20, 0.32);
  transform: rotate(-12deg);
}

.dp-gallery-letter-paper__wax::after {
  content: '';
  position: absolute;
  inset: 22%;
  border-radius: 50%;
  border: 1.5px solid color-mix(in srgb, var(--dp-letter-ink, #3a2e22) 35%, transparent);
  opacity: 0.5;
}

/* Top — flat wood-tone accent strip (no 3D scroll curl) */
.dp-gallery-letter-paper::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  z-index: 3;
  height: var(--dp-letter-edge-accent);
  pointer-events: none;
  background: linear-gradient(
    180deg,
    color-mix(in srgb, var(--dp-letter-scroll-rod-mid, #a8864a) 48%, var(--dp-letter-scroll-rod-light, #f0ddb0) 52%),
    color-mix(in srgb, var(--dp-letter-scroll-rod-light, #f0ddb0) 55%, transparent)
  );
  border-bottom: 1px solid color-mix(in srgb, var(--dp-letter-border, rgba(139, 115, 75, 0.32)) 65%, transparent);
  opacity: 0.72;
}

/* Bottom — soft deckle fade + thin accent */
.dp-gallery-letter-paper::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 3;
  height: calc(var(--dp-letter-edge-accent) + 2px);
  pointer-events: none;
  background: linear-gradient(
    0deg,
    color-mix(in srgb, var(--dp-letter-scroll-rod-mid, #a8864a) 22%, transparent),
    transparent 70%
  );
  opacity: 0.55;
}

.dp-gallery-letter-paper--unfolding {
  min-height: 140px;
  border-color: var(--dp-letter-border, rgba(139, 115, 75, 0.32));
  box-shadow:
    inset 0 0 52px color-mix(in srgb, var(--dp-letter-ink, #3a2e22) 5%, transparent),
    inset 0 1px 0 rgba(255, 248, 230, 0.42),
    inset 0 -1px 0 color-mix(in srgb, var(--dp-letter-border, rgba(139, 115, 75, 0.32)) 45%, transparent),
    inset 1px 0 0 color-mix(in srgb, var(--dp-letter-border, rgba(139, 115, 75, 0.32)) 22%, transparent),
    inset -1px 0 0 color-mix(in srgb, var(--dp-letter-border, rgba(139, 115, 75, 0.32)) 22%, transparent),
    0 22px 52px rgba(42, 32, 20, 0.28),
    0 8px 20px rgba(42, 32, 20, 0.14);
}

.dp-gallery-letter-paper--ready {
  max-height: min(72vh, 560px);
  overflow-x: hidden;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
}

.dp-gallery-letter--editable .dp-gallery-letter-paper--ready {
  max-height: none;
}

.dp-gallery-letter-paper__body {
  position: relative;
  z-index: 1;
  padding:
    calc(var(--dp-letter-edge-accent) + var(--dp-letter-body-inset-y))
    clamp(24px, 6vw, 36px)
    calc(var(--dp-letter-edge-accent) + var(--dp-letter-body-inset-y) + 4px);
  min-height: 160px;
}

.dp-gallery-letter__placard {
  margin: 0 0 18px;
  padding: 0 0 14px;
  border-bottom: 1px solid color-mix(in srgb, var(--dp-letter-border, rgba(139, 115, 75, 0.32)) 80%, transparent);
  font-family: var(--dp-font-ui, inherit);
}

.dp-gallery-letter__placard-eyebrow {
  margin: 0 0 6px;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: color-mix(in srgb, var(--dp-letter-ink, #3a2e22) 52%, transparent);
}

.dp-gallery-letter__placard-author {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  letter-spacing: 0.06em;
  color: color-mix(in srgb, var(--dp-letter-ink, #3a2e22) 72%, transparent);
}

.dp-gallery-letter__placard-name {
  font-size: 17px;
  font-weight: 650;
  letter-spacing: 0.04em;
  color: var(--dp-letter-ink, #3a2e22);
}

.dp-gallery-letter__placard-date {
  margin: 6px 0 0;
  font-size: 11px;
  letter-spacing: 0.08em;
  color: color-mix(in srgb, var(--dp-letter-ink, #3a2e22) 48%, transparent);
}

.dp-gallery-letter__label {
  display: block;
  margin-bottom: 10px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  font-family: var(--dp-font-ui, inherit);
  color: color-mix(in srgb, var(--dp-letter-ink, #3a2e22) 55%, transparent);
}

.dp-gallery-letter__text {
  margin: 0;
  padding-right: 48px;
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
  font-family: var(--dp-font-ui, inherit);
}

.dp-gallery-letter__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px dashed color-mix(in srgb, var(--dp-letter-border, rgba(139, 115, 75, 0.32)) 70%, transparent);
}

.dp-gallery-letter__textarea ::v-deep .el-textarea__inner {
  background: transparent;
  border: none;
  border-radius: 0;
  padding: 0;
  font-family: inherit;
  font-size: 20px;
  line-height: var(--dp-letter-line-height, 32px);
  color: var(--dp-letter-ink, #3a2e22);
  letter-spacing: 0.02em;
  resize: none;
  min-height: calc(var(--dp-letter-line-height, 32px) * 5);
  box-shadow: none;
}

.dp-gallery-letter__textarea ::v-deep .el-textarea__inner:focus {
  outline: none;
  box-shadow: none;
}

.dp-gallery-letter__textarea ::v-deep .el-textarea__inner::placeholder {
  color: color-mix(in srgb, var(--dp-letter-ink, #3a2e22) 38%, transparent);
  font-style: italic;
}

.dp-gallery-letter__textarea ::v-deep .el-input__count {
  background: transparent;
  border: none;
  border-radius: 0;
  padding: 0;
  font-family: var(--dp-font-ui, inherit);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.08em;
  color: color-mix(in srgb, var(--dp-letter-ink, #3a2e22) 42%, transparent);
  bottom: 0;
  right: 2px;
  line-height: var(--dp-letter-line-height, 32px);
  pointer-events: none;
}

.dp-gallery-letter__btn {
  min-height: 44px;
  min-width: 44px;
  padding: 8px 18px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.08em;
  cursor: pointer;
  font-family: var(--dp-font-ui, inherit);
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease, box-shadow 0.2s ease;
}
.dp-gallery-letter__btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.dp-gallery-letter__btn--primary {
  border: 1.5px solid color-mix(in srgb, var(--dp-letter-envelope-seal-dark, #9a7030) 65%, transparent);
  background: linear-gradient(
    168deg,
    var(--dp-letter-envelope-seal-light, #d4a853),
    var(--dp-letter-envelope-seal-dark, #9a7030)
  );
  color: #fffaf2;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.28),
    0 3px 10px rgba(42, 32, 20, 0.22);
}
.dp-gallery-letter__btn--primary:hover:not(:disabled) {
  filter: brightness(1.06);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.32),
    0 5px 14px rgba(42, 32, 20, 0.28);
}
.dp-gallery-letter__btn--ghost {
  border: 1.5px solid color-mix(in srgb, var(--dp-letter-border, rgba(139, 115, 75, 0.32)) 90%, transparent);
  background: color-mix(in srgb, var(--dp-letter-paper-tint, #f0e0b8) 55%, transparent);
  color: color-mix(in srgb, var(--dp-letter-ink, #3a2e22) 75%, transparent);
}
.dp-gallery-letter__btn--ghost:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--dp-letter-wax, #a83232) 55%, transparent);
  color: var(--dp-letter-wax, #a83232);
}
.dp-gallery-letter__btn--danger {
  border: 1.5px solid color-mix(in srgb, var(--dp-letter-wax, #a83232) 70%, transparent);
  background: color-mix(in srgb, var(--dp-letter-wax, #a83232) 88%, #000 12%);
  color: #fff8f4;
}
.dp-gallery-letter__btn--danger:hover:not(:disabled) {
  filter: brightness(1.08);
}

/* Inline clear confirm */
.dp-gallery-letter__confirm {
  position: absolute;
  inset: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: color-mix(in srgb, var(--dp-letter-paper-tint, #f0e0b8) 35%, rgba(36, 26, 18, 0.55));
  border-radius: 2px;
}

.dp-gallery-letter__confirm-card {
  width: min(320px, 100%);
  padding: 18px 20px 16px;
  border: 1.5px solid var(--dp-letter-border, rgba(139, 115, 75, 0.32));
  border-radius: 4px;
  background: var(--dp-letter-paper-tint, #f0e0b8);
  box-shadow: 0 16px 40px rgba(42, 32, 20, 0.28);
  font-family: var(--dp-font-ui, inherit);
}

.dp-gallery-letter__confirm-title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 650;
  color: var(--dp-letter-ink, #3a2e22);
}

.dp-gallery-letter__confirm-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.55;
  color: color-mix(in srgb, var(--dp-letter-ink, #3a2e22) 68%, transparent);
}

.dp-gallery-letter__confirm-actions {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.dp-gallery-letter-confirm-enter-active,
.dp-gallery-letter-confirm-leave-active {
  transition: opacity 0.2s ease;
}
.dp-gallery-letter-confirm-enter,
.dp-gallery-letter-confirm-leave-to {
  opacity: 0;
}

.dp-game-root[data-dp-eco-mode='true'] .dp-gallery-letter-paper {
  transition: max-height 0.15s ease-out, border-color 0.15s ease;
}

@media (prefers-reduced-motion: reduce) {
  .dp-gallery-letter-paper {
    transition: max-height 0.15s ease-out, border-color 0.15s ease;
  }
  .dp-gallery-letter__btn {
    transition: none;
  }
  .dp-gallery-letter-confirm-enter-active,
  .dp-gallery-letter-confirm-leave-active {
    transition: none;
  }
}

@media (max-width: 480px) {
  .dp-gallery-letter-paper {
    --dp-letter-body-inset-y: 16px;
  }
  .dp-gallery-letter-paper__body {
    padding-left: clamp(18px, 5vw, 28px);
    padding-right: clamp(18px, 5vw, 28px);
  }
  .dp-gallery-letter__text {
    padding-right: 0;
  }
  .dp-gallery-letter-paper__wax {
    width: 30px;
    height: 30px;
    right: 22px;
  }
}
</style>
