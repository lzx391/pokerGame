<template>
  <div
    class="dp-game-root gallery-page"
    :data-dp-game-theme="effectiveThemeForCss"
  >
    <header class="gallery-page__header">
      <button
        type="button"
        class="gallery-page__back"
        aria-label="返回"
        @click="goBack"
      >
        返回
      </button>
      <h1 class="gallery-page__title">{{ pageTitle }}</h1>
      <div class="gallery-page__header-actions">
        <div class="dp-game-theme-row gallery-page__theme-row">
          <span class="dp-game-theme-row__label">界面主题</span>
          <dp-theme-picker
            :game-ui-theme="gameUiTheme"
            :theme-options="gameThemeOptions"
            @input-theme="onLobbyThemeChange($event)"
          />
        </div>
      </div>
    </header>

    <div v-if="forbidden" class="gallery-page__forbidden">
      <p class="gallery-page__forbidden-text">无权限查看画廊</p>
      <button type="button" class="gallery-page__back gallery-page__back--center" @click="goBack">
        返回
      </button>
    </div>

    <template v-else-if="!booting">
      <dp-gallery-letter-envelope
        :content="letterContent"
        :loading="letterLoading"
        :editable="canEditLetter"
        @update:content="onLetterUpdated"
      />

      <dp-gallery-items-editor
        v-if="editMode"
        @items-changed="onEditorItemsChanged"
      />

      <h2 v-if="editMode" class="gallery-page__section-label">预览</h2>

      <dp-gallery-wall
        :items="wallItems"
        :loading="itemsLoading && !editMode"
        :empty-text="emptyWallText"
      />
    </template>

    <p v-else class="gallery-page__boot">加载中…</p>
  </div>
</template>

<script>
import '@/styles/dp-game-themes.css'
import '@/styles/dp-lobby-shell.css'
import dpLobbyThemeMixin from '@features/lobby/mixins/dpLobbyThemeMixin'
import { mapGetters } from 'vuex'
import { DP_PERM_GALLERY_VIEW } from '@features/auth/store/dpAuth'
import { ensureDpUserIdInStorage } from '@features/user/utils/dpEnsureUserId'
import { refreshDpAuthPermissions } from '@features/auth/utils/dpAuthBootstrap'
import { dpResultSuccess, dpResultData, dpResultMessage, dpAxiosErrorMessage } from '@shared/utils/dpApiResult'
import { dpSocialApi } from '@features/social/api/socialApi'
import { dpDisplayNickname } from '@shared/utils/dpDisplayNickname'
import DpGalleryLetterEnvelope from '@features/gallery/components/DpGalleryLetterEnvelope.vue'
import DpGalleryWall from '@features/gallery/components/DpGalleryWall.vue'
import DpGalleryItemsEditor from '@features/gallery/components/DpGalleryItemsEditor.vue'

export default {
  name: 'GalleryPage',
  components: {
    DpGalleryLetterEnvelope,
    DpGalleryWall,
    DpGalleryItemsEditor
  },
  mixins: [dpLobbyThemeMixin],
  props: {
    userId: {
      type: [Number, String],
      default: null
    }
  },
  data() {
    return {
      booting: true,
      forbidden: false,
      currentUser: null,
      subjectNickname: '',
      letterContent: '',
      letterLoading: false,
      items: [],
      editorItems: null,
      itemsLoading: false
    }
  },
  computed: {
    ...mapGetters('dpAuth', ['hasPerm']),
    isOwnGallery() {
      return !this.routeUserId
    },
    routeUserId() {
      var uid = Number(this.userId)
      if (!uid || uid <= 0 || isNaN(uid)) return null
      return uid
    },
    editMode() {
      if (!this.isOwnGallery) return false
      return this.$route.query.mode === 'edit' && this.hasPerm(DP_PERM_GALLERY_VIEW)
    },
    canEditLetter() {
      return this.editMode
    },
    targetUserId() {
      if (this.routeUserId) return this.routeUserId
      var uid = Number(this.currentUser && this.currentUser.userId)
      if (!uid || uid <= 0 || isNaN(uid)) return null
      return uid
    },
    pageTitle() {
      if (this.isOwnGallery) {
        var nick = (this.currentUser && this.currentUser.nickname) || '我'
        return this.editMode ? '编辑我的画廊' : nick + ' 的画廊'
      }
      var name = this.subjectNickname || '玩家'
      return name + ' 的画廊'
    },
    wallItems() {
      if (this.editMode && Array.isArray(this.editorItems)) {
        return this.editorItems
      }
      return this.items
    },
    emptyWallText() {
      if (this.editMode) return '上传作品后在此预览'
      return '该玩家还没有上传画廊作品'
    }
  },
  async created() {
    refreshDpAuthPermissions(this)
    try {
      var raw = localStorage.getItem('userInfo')
      this.currentUser = raw ? JSON.parse(raw) : null
    } catch (e) {
      this.currentUser = null
    }
    if (!this.currentUser || !this.currentUser.nickname) {
      this.$router.replace('/login')
      return
    }
    this.currentUser = (await ensureDpUserIdInStorage(this.$http)) || this.currentUser
    var uid = Number(this.currentUser && this.currentUser.userId)
    if (!this.currentUser || isNaN(uid) || uid <= 0) {
      this.$router.replace('/login')
      return
    }
    this.currentUser.userId = uid

    if (!this.hasPerm(DP_PERM_GALLERY_VIEW)) {
      this.forbidden = true
      this.booting = false
      return
    }

    if (this.routeUserId) {
      await this.loadSubjectNickname(this.routeUserId)
    }

    await this.loadGalleryData()
    this.booting = false
  },
  methods: {
    goBack() {
      if (window.history.length > 1) {
        this.$router.back()
      } else {
        this.$router.push('/home')
      }
    },
    onLetterUpdated(text) {
      this.letterContent = text != null ? String(text) : ''
    },
    onEditorItemsChanged(items) {
      this.editorItems = Array.isArray(items) ? items.slice() : []
    },
    isGalleryForbidden(err) {
      var st = err && err.response && err.response.status
      return st === 403 || st === 401
    },
    async loadSubjectNickname(userId) {
      try {
        var res = await dpSocialApi(this.$http).lookupUser(String(userId))
        if (dpResultSuccess(res.data)) {
          var d = dpResultData(res.data) || {}
          var nick = d.nickname && String(d.nickname).trim()
          this.subjectNickname = nick ? dpDisplayNickname(nick) : '玩家'
          return
        }
      } catch (e) {
        /* fallback */
      }
      this.subjectNickname = '玩家'
    },
    async loadGalleryData() {
      if (this.editMode) {
        await this.loadOwnLetter()
        return
      }
      var uid = this.targetUserId
      if (!uid) return
      this.itemsLoading = true
      this.letterLoading = true
      try {
        if (this.isOwnGallery) {
          await Promise.all([this.loadOwnLetter(), this.loadOwnItems()])
        } else {
          await Promise.all([this.loadUserLetter(uid), this.loadUserItems(uid)])
        }
      } finally {
        this.itemsLoading = false
        this.letterLoading = false
      }
    },
    async loadOwnLetter() {
      this.letterLoading = true
      try {
        var res = await this.$http.get('/dp/gallery/letter')
        if (dpResultSuccess(res.data)) {
          var data = dpResultData(res.data) || {}
          var letter = data.letter
          this.letterContent = letter && letter.content ? String(letter.content) : ''
        } else if (this.$message) {
          this.$message.error(dpResultMessage(res.data) || '加载介绍信失败')
        }
      } catch (e) {
        if (this.isGalleryForbidden(e)) {
          this.forbidden = true
          return
        }
        if (this.$message) this.$message.error(dpAxiosErrorMessage(e, '加载介绍信失败'))
      } finally {
        this.letterLoading = false
      }
    },
    async loadOwnItems() {
      try {
        var res = await this.$http.get('/dp/gallery/items')
        if (dpResultSuccess(res.data)) {
          var data = dpResultData(res.data) || {}
          this.items = Array.isArray(data.items) ? data.items.slice() : []
        }
      } catch (e) {
        if (this.isGalleryForbidden(e)) {
          this.forbidden = true
        }
      }
    },
    async loadUserLetter(uid) {
      try {
        var res = await this.$http.get('/dp/gallery/users/' + uid + '/letter')
        if (dpResultSuccess(res.data)) {
          var data = dpResultData(res.data) || {}
          var letter = data.letter
          this.letterContent = letter && letter.content ? String(letter.content) : ''
        }
      } catch (e) {
        if (this.isGalleryForbidden(e)) {
          this.forbidden = true
        }
      }
    },
    async loadUserItems(uid) {
      try {
        var res = await this.$http.get('/dp/gallery/users/' + uid + '/items')
        if (dpResultSuccess(res.data)) {
          var data = dpResultData(res.data) || {}
          this.items = Array.isArray(data.items) ? data.items.slice() : []
        } else if (this.$message) {
          this.$message.error(dpResultMessage(res.data) || '加载画廊失败')
        }
      } catch (e) {
        if (this.isGalleryForbidden(e)) {
          this.forbidden = true
          return
        }
        if (this.$message) this.$message.error(dpAxiosErrorMessage(e, '加载画廊失败'))
      }
    }
  }
}
</script>

<style scoped>
.gallery-page {
  --dp-gallery-wall-base: color-mix(in srgb, var(--dp-subpanel-bg, #f5f3f0) 68%, var(--dp-game-bg, #fafafa));
  --dp-gallery-wall-warm: color-mix(in srgb, var(--dp-warning, #b8860b) 7%, var(--dp-gallery-wall-base));
  --dp-gallery-wall-deep: color-mix(in srgb, var(--dp-text-primary, #3a332c) 10%, var(--dp-gallery-wall-warm));
  --dp-gallery-wall-line: color-mix(in srgb, var(--dp-panel-border, rgba(107, 93, 82, 0.16)) 72%, transparent);
  --dp-gallery-wall-vignette: color-mix(in srgb, var(--dp-text-primary, #3a332c) 14%, transparent);
  --dp-gallery-wall-spot: color-mix(in srgb, var(--dp-accent, #6b5d52) 6%, transparent);
  --dp-gallery-mat-bg: color-mix(in srgb, var(--dp-panel-bg, #fff) 38%, var(--dp-subpanel-bg, #f5f3f0));
  --dp-gallery-mat-edge: color-mix(in srgb, var(--dp-panel-border, rgba(107, 93, 82, 0.16)) 85%, transparent);
  --dp-gallery-baseboard: color-mix(in srgb, var(--dp-accent, #6b5d52) 18%, var(--dp-subpanel-bg, #f5f3f0));
  --dp-gallery-frame-mat: color-mix(in srgb, var(--dp-gallery-mat-bg) 55%, #f7f4ef);
  --dp-gallery-frame-border: color-mix(in srgb, var(--dp-accent, #6b5d52) 22%, transparent);

  position: relative;
  isolation: isolate;
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  min-height: 100dvh;
  max-width: min(1200px, 100%);
  width: 100%;
  margin: 0 auto;
  padding: clamp(12px, 3vw, 20px) clamp(12px, 3vw, 24px) clamp(20px, 4vw, 32px);
  box-sizing: border-box;
  font-family: var(--dp-font-ui, inherit);
  color: var(--dp-text-primary, #303133);
  background: transparent;
}

/* Full-viewport museum wall — CSS only, no image assets */
.gallery-page::before {
  content: '';
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background:
    radial-gradient(ellipse 108% 96% at 50% 48%, transparent 40%, var(--dp-gallery-wall-vignette) 100%),
    radial-gradient(ellipse 88% 52% at 50% -6%, var(--dp-gallery-wall-spot) 0%, transparent 64%),
    repeating-linear-gradient(
      180deg,
      transparent 0,
      transparent calc(128px - 1px),
      var(--dp-gallery-wall-line) calc(128px - 1px),
      var(--dp-gallery-wall-line) 128px
    ),
    linear-gradient(
      175deg,
      color-mix(in srgb, var(--dp-gallery-wall-warm) 92%, #fff) 0%,
      var(--dp-gallery-wall-warm) 42%,
      var(--dp-gallery-wall-deep) 100%
    );
}

.gallery-page > * {
  position: relative;
  z-index: 1;
}

.gallery-page__header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: clamp(16px, 3vw, 24px);
  flex-shrink: 0;
}

.gallery-page__title {
  flex: 1 1 100%;
  order: -1;
  margin: 0;
  font-size: clamp(1.2rem, 3.5vw, 1.5rem);
  font-weight: 650;
  letter-spacing: 0.02em;
  color: var(--dp-text-primary, #303133);
}

.gallery-page__header-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
  margin-left: auto;
}

.gallery-page__theme-row {
  justify-content: flex-end;
}

.gallery-page__back {
  min-height: 44px;
  padding: 8px 16px;
  border: 1px solid var(--dp-input-border, #dcdfe6);
  border-radius: 8px;
  background: var(--dp-btn-ghost-bg, #fff);
  color: var(--dp-text-primary, #303133);
  cursor: pointer;
  font-size: 14px;
  font-family: inherit;
  transition: border-color 0.2s ease, color 0.2s ease;
}
.gallery-page__back:hover {
  border-color: var(--dp-accent, #409eff);
  color: var(--dp-accent, #409eff);
}
.gallery-page__back--center {
  margin-top: 12px;
}

.gallery-page__section-label {
  margin: 0 0 10px;
  font-size: 13px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--dp-text-muted, #909399);
}

.gallery-page__boot {
  color: var(--dp-text-muted, #909399);
  text-align: center;
  padding: 48px 16px;
}

.gallery-page__forbidden {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 48px 16px;
}

.gallery-page__forbidden-text {
  margin: 0;
  font-size: 16px;
  color: var(--dp-text-secondary, #606266);
  line-height: 1.6;
}

@media (min-width: 640px) {
  .gallery-page__title {
    flex: 1 1 auto;
    order: 0;
  }
}
</style>
