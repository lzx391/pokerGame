<template>
  <div
    class="dp-game-root gallery-page"
    :data-dp-game-theme="effectiveThemeForCss"
    :data-dp-eco-mode="ecoMode ? 'true' : 'false'"
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

    <div v-if="forbidden && !booting" class="gallery-page__forbidden">
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
        :letter-author="letterAuthorName"
        :eco-mode="ecoMode"
        @update:content="onLetterUpdated"
      />

      <dp-gallery-items-editor
        v-if="editMode"
        @items-changed="onEditorItemsChanged"
      />

      <h2 v-if="editMode" class="gallery-page__section-label">预览</h2>

      <div class="gallery-page__wall-wrap">
        <dp-gallery-wall
          :items="wallItems"
          :loading="itemsLoading && !editMode"
          :empty-text="emptyWallText"
          :edit-mode="editMode"
          :eco-mode="ecoMode"
        />
      </div>
    </template>

    <p v-else class="gallery-page__boot">加载中…</p>
  </div>
</template>

<script>
import '@/styles/dp-game-themes.css'
import '@/styles/dp-lobby-shell.css'
import dpLobbyThemeMixin from '@features/lobby/mixins/dpLobbyThemeMixin'
import { mapGetters, mapState } from 'vuex'
import { DP_PERM_GALLERY_VIEW } from '@features/auth/store/dpAuth'
import { ensureDpUserIdInStorage } from '@features/user/utils/dpEnsureUserId'
import { bootstrapDpAuthPermissions } from '@features/auth/utils/dpAuthBootstrap'
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
    ...mapState('dpGame', ['ecoMode']),
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
    letterAuthorName() {
      if (this.isOwnGallery) {
        return (this.currentUser && this.currentUser.nickname) || '我'
      }
      return this.subjectNickname || '玩家'
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

    await bootstrapDpAuthPermissions(this)

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
  /* Light museum wall — dim ambient base; column spotlights carve lit zones on mat */
  --dp-gallery-wall-base: #c2b6a8;
  --dp-gallery-wall-mid: #b5a89a;
  --dp-gallery-wall-deep: #a6988a;
  --dp-gallery-wall-vignette: rgba(28, 22, 16, 0.42);
  --dp-gallery-wall-spot: rgba(255, 252, 248, 0.14);
  --dp-gallery-wall-texture-a: rgba(255, 255, 255, 0.1);
  --dp-gallery-wall-texture-b: rgba(28, 22, 16, 0.14);
  --dp-gallery-mat-bg: #b5a89a;
  --dp-gallery-mat-edge: rgba(72, 60, 50, 0.28);
  --dp-gallery-baseboard: #9a8e80;
  --dp-gallery-frame-mat: #f7f4ef;
  --dp-gallery-frame-border: rgba(107, 93, 82, 0.22);
  --dp-gallery-spot-core: rgba(255, 248, 235, 0.55);
  --dp-gallery-spot-mid: rgba(255, 248, 235, 0.15);
  --dp-gallery-spot-spread: 80%;
  --dp-gallery-spot-depth: 120%;
  --dp-gallery-ambient-vignette: rgba(28, 22, 16, 0.48);

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

/* Full-viewport museum wall — darker ambient; column spotlights live on mat panel */
.gallery-page::before {
  content: '';
  position: fixed;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  background:
    radial-gradient(ellipse 100% 92% at 50% 46%, transparent 18%, var(--dp-gallery-wall-vignette) 100%),
    radial-gradient(circle at 18% 24%, var(--dp-gallery-wall-texture-a) 0%, transparent 42%),
    radial-gradient(circle at 82% 78%, var(--dp-gallery-wall-texture-b) 0%, transparent 38%),
    linear-gradient(
      175deg,
      var(--dp-gallery-wall-base) 0%,
      var(--dp-gallery-wall-mid) 46%,
      var(--dp-gallery-wall-deep) 100%
    );
}

/* Dark gallery ambiance — gothic & halloween */
.gallery-page[data-dp-game-theme='gothic'],
.gallery-page[data-dp-game-theme='halloween'] {
  --dp-gallery-wall-base: #2a2420;
  --dp-gallery-wall-mid: #252019;
  --dp-gallery-wall-deep: #1f1a17;
  --dp-gallery-wall-vignette: rgba(0, 0, 0, 0.68);
  --dp-gallery-wall-spot: rgba(255, 200, 120, 0.1);
  --dp-gallery-wall-texture-a: rgba(255, 220, 160, 0.04);
  --dp-gallery-wall-texture-b: rgba(0, 0, 0, 0.22);
  --dp-gallery-mat-bg: #2a2420;
  --dp-gallery-mat-edge: rgba(212, 184, 120, 0.14);
  --dp-gallery-baseboard: #3d342e;
  --dp-gallery-frame-mat: #2e2824;
  --dp-gallery-frame-border: rgba(212, 184, 120, 0.24);
  --dp-gallery-spot-core: rgba(255, 200, 120, 0.28);
  --dp-gallery-spot-mid: rgba(255, 180, 80, 0.1);
  --dp-gallery-ambient-vignette: rgba(0, 0, 0, 0.52);
}

.gallery-page[data-dp-game-theme='gothic'] {
  --dp-gallery-spot-core: rgba(224, 201, 117, 0.32);
  --dp-gallery-spot-mid: rgba(224, 201, 117, 0.1);
}

.gallery-page[data-dp-game-theme='halloween'] {
  --dp-gallery-spot-core: rgba(251, 146, 60, 0.3);
  --dp-gallery-spot-mid: rgba(251, 146, 60, 0.11);
  --dp-gallery-wall-texture-a: rgba(251, 146, 60, 0.05);
}

/* retro8bit: dark CRT gallery — softer spot cones, no heavy shadow clash */
.gallery-page[data-dp-game-theme='retro8bit'] {
  --dp-gallery-wall-base: #121510;
  --dp-gallery-wall-mid: #0e120e;
  --dp-gallery-wall-deep: #0a0c0a;
  --dp-gallery-wall-vignette: rgba(0, 0, 0, 0.58);
  --dp-gallery-wall-spot: rgba(74, 246, 38, 0.07);
  --dp-gallery-wall-texture-a: rgba(74, 246, 38, 0.03);
  --dp-gallery-wall-texture-b: rgba(0, 0, 0, 0.22);
  --dp-gallery-mat-bg: #161a14;
  --dp-gallery-mat-edge: rgba(74, 246, 38, 0.12);
  --dp-gallery-baseboard: #1a2018;
  --dp-gallery-frame-mat: #141812;
  --dp-gallery-frame-border: rgba(74, 246, 38, 0.2);
  --dp-gallery-spot-core: rgba(74, 246, 38, 0.14);
  --dp-gallery-spot-mid: rgba(74, 246, 38, 0.05);
  --dp-gallery-spot-spread: 72%;
  --dp-gallery-spot-depth: 105%;
  --dp-gallery-ambient-vignette: rgba(0, 0, 0, 0.4);
}

.gallery-page > *:not(.dp-gallery-envelope):not(.gallery-page__wall-wrap) {
  position: relative;
  z-index: 1;
}

/* No z-index — avoids trapping print reveal (z-index 3000) below the letter layer. */
.gallery-page__wall-wrap {
  position: relative;
  flex: 1 1 auto;
  display: flex;
  flex-direction: column;
  min-height: clamp(60vh, 65vh, 70vh);
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
