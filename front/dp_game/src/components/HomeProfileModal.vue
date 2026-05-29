<template>
  <el-dialog
    :visible.sync="dialogVisible"
    width="min(92vw, 520px)"
    custom-class="home-profile-dialog"
    append-to-body
    :close-on-click-modal="!saving"
    :show-close="false"
    @closed="onClosed"
  >
    <div slot="title" class="home-prof-title-bar">
      <div class="home-prof-title-bar__deco">
        <span class="home-prof-suit home-prof-suit--spade" aria-hidden="true">♠</span>
        <span class="home-prof-suit home-prof-suit--heart" aria-hidden="true">♥</span>
        <span class="home-prof-title-bar__text">个人资料</span>
        <span class="home-prof-suit home-prof-suit--diamond" aria-hidden="true">♦</span>
        <span class="home-prof-suit home-prof-suit--club" aria-hidden="true">♣</span>
      </div>
      <button
        type="button"
        class="home-prof-title-bar__close"
        :disabled="saving"
        @click="dialogVisible = false"
        aria-label="关闭"
      >
        <i class="el-icon-close"></i>
      </button>
    </div>

    <div v-if="loading" class="home-prof-loading">
      <span class="home-prof-loading__chip" aria-hidden="true"></span>
      <span>加载中…</span>
    </div>

    <div
      v-else
      class="home-prof-card"
      :class="{
        'home-prof-card--edit': mode === 'edit',
        'home-prof-card--effects-off': shouldSkipEffects()
      }"
    >
      <div
        v-if="showBackdrop"
        class="home-prof-backdrop"
        aria-hidden="true"
      >
        <img
          class="home-prof-backdrop__img"
          :src="backdropSrc"
          alt=""
          decoding="async"
        >
        <div class="home-prof-backdrop__scrim"></div>
      </div>

      <!-- ========== 查看态 ========== -->
      <template v-if="mode === 'view'">
        <div class="home-prof-card__inner home-prof-card__inner--view">
          <div class="home-prof-identity">
            <div class="home-prof-avatar-frame">
              <div class="home-prof-avatar-frame__bezel">
                <dp-user-avatar
                  :avatar-url="form.avatarUrl"
                  :nickname="form.nickname"
                  :cache-bust="avatarCacheBust || avatarCacheBustFromUpdatedAt(form.avatarUpdatedAt)"
                  size="lg"
                />
              </div>
              <span class="home-prof-avatar-frame__corner home-prof-avatar-frame__corner--tl" aria-hidden="true"></span>
              <span class="home-prof-avatar-frame__corner home-prof-avatar-frame__corner--tr" aria-hidden="true"></span>
              <span class="home-prof-avatar-frame__corner home-prof-avatar-frame__corner--bl" aria-hidden="true"></span>
              <span class="home-prof-avatar-frame__corner home-prof-avatar-frame__corner--br" aria-hidden="true"></span>
            </div>
            <div class="home-prof-identity__meta">
              <h2 class="home-prof-name">{{ form.nickname || '未设置昵称' }}</h2>
              <p class="home-prof-id">ID: {{ form.id }}</p>
              <el-upload
                class="home-prof-avatar-upload"
                action=""
                accept="image/jpeg,image/png,image/webp,image/gif"
                :show-file-list="false"
                :disabled="avatarUploading"
                :http-request="onAvatarUploadRequest"
              >
                <button type="button" class="home-prof-btn home-prof-btn--outline" :disabled="avatarUploading">
                  <i class="el-icon-camera"></i>
                  {{ avatarUploading ? '上传中…' : '更换头像' }}
                </button>
              </el-upload>
              <p class="home-prof-avatar-hint">jpg / png / webp / gif，最大 2MB</p>
            </div>
          </div>

          <div
            v-if="form.totalHandsPlayed != null"
            class="home-prof-honor"
            :aria-busy="honorGlitchPhase !== 'revealed'"
          >
            <div class="home-prof-honor__title">
              <span class="home-prof-section-deco" aria-hidden="true">♦</span>
              生涯荣誉
              <span class="home-prof-section-deco" aria-hidden="true">♦</span>
            </div>
            <div class="home-prof-honor__medals">
              <div class="home-prof-medal home-prof-medal--royal">
                <div class="home-prof-medal__body">
                  <span class="home-prof-medal__name">皇家同花顺</span>
                  <span
                    class="home-prof-honor-val"
                    :class="honorValClass"
                    :aria-label="'皇家同花顺 ' + honorDisplayCount('royalFlushWins') + ' 次'"
                  >{{ honorDisplayCount('royalFlushWins') }}<small> 次</small></span>
                </div>
              </div>
              <div class="home-prof-medal home-prof-medal--straight">
                <div class="home-prof-medal__body">
                  <span class="home-prof-medal__name">同花顺</span>
                  <span
                    class="home-prof-honor-val"
                    :class="honorValClass"
                    :aria-label="'同花顺 ' + honorDisplayCount('straightFlushWins') + ' 次'"
                  >{{ honorDisplayCount('straightFlushWins') }}<small> 次</small></span>
                </div>
              </div>
              <div class="home-prof-medal home-prof-medal--four">
                <div class="home-prof-medal__body">
                  <span class="home-prof-medal__name">四条</span>
                  <span
                    class="home-prof-honor-val"
                    :class="honorValClass"
                    :aria-label="'四条 ' + honorDisplayCount('fourOfAKindWins') + ' 次'"
                  >{{ honorDisplayCount('fourOfAKindWins') }}<small> 次</small></span>
                </div>
              </div>
            </div>
            <div class="home-prof-honor__stats">
              <div class="home-prof-stat-card">
                <span class="home-prof-stat-card__label">最高净赢倍数</span>
                <span
                  class="home-prof-honor-val home-prof-stat-card__value"
                  :class="honorValClass"
                  :aria-label="'最高净赢倍数 ' + honorDisplayStat('largestPotWon')"
                >{{ honorDisplayStat('largestPotWon') }}</span>
              </div>
              <div class="home-prof-stat-card">
                <span class="home-prof-stat-card__label">单房最高净赢</span>
                <span
                  class="home-prof-honor-val home-prof-stat-card__value"
                  :class="honorValClass"
                  :aria-label="'单房最高净赢 ' + honorDisplayStat('largestRoomNet')"
                >{{ honorDisplayStat('largestRoomNet') }}</span>
              </div>
              <div class="home-prof-stat-card">
                <span class="home-prof-stat-card__label">生涯总局数</span>
                <span
                  class="home-prof-honor-val home-prof-stat-card__value"
                  :class="honorValClass"
                  :aria-label="'生涯总局数 ' + honorDisplayCount('totalHandsPlayed')"
                >{{ honorDisplayCount('totalHandsPlayed') }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="home-prof-footer home-prof-footer--view">
          <button type="button" class="home-prof-btn home-prof-btn--ghost" @click="dialogVisible = false">
            关闭
          </button>
          <button type="button" class="home-prof-btn home-prof-btn--gold" @click="enterEditMode">
            编辑资料
          </button>
        </div>
      </template>

      <!-- ========== 编辑态 ========== -->
      <template v-else>
        <div class="home-prof-card__inner home-prof-card__inner--edit">
          <div class="home-prof-edit-section">
            <div class="home-prof-edit-section__title">
              <span class="home-prof-section-deco" aria-hidden="true">♣</span>
              编辑资料
              <span class="home-prof-section-deco" aria-hidden="true">♣</span>
            </div>

            <el-form
              ref="formRef"
              :model="form"
              label-position="top"
              class="home-prof-form"
              @submit.native.prevent="onSave"
            >
              <el-form-item label="昵称" required>
                <el-input
                  v-model.trim="form.nickname"
                  maxlength="10"
                  show-word-limit
                  autocomplete="username"
                  placeholder="最多 10 字"
                />
              </el-form-item>

              <el-form-item label="登录密码">
                <div class="home-prof-pwd-row">
                  <span class="home-prof-pwd-hint">
                    {{ form.passwordSet ? '已设置（加密存储，不可查看）' : '未设置' }}
                  </span>
                  <button
                    v-if="!editingPassword"
                    type="button"
                    class="home-prof-btn home-prof-btn--outline home-prof-btn--sm"
                    @click="editingPassword = true"
                  >
                    修改密码
                  </button>
                </div>
                <template v-if="editingPassword">
                  <el-input
                    v-model="form.newPassword"
                    type="password"
                    show-password
                    autocomplete="new-password"
                    placeholder="新密码（至少 6 位，留空不改）"
                    class="home-prof-field-gap"
                  />
                  <el-input
                    v-model="form.confirmPassword"
                    type="password"
                    show-password
                    autocomplete="new-password"
                    placeholder="再次输入新密码"
                    class="home-prof-field-gap"
                  />
                </template>
              </el-form-item>

              <el-form-item label="当前密码" required>
                <el-input
                  v-model="form.oldPassword"
                  type="password"
                  show-password
                  autocomplete="current-password"
                  placeholder="保存前须验证当前密码"
                />
              </el-form-item>
            </el-form>
          </div>
        </div>

        <div class="home-prof-footer home-prof-footer--edit">
          <button type="button" class="home-prof-btn home-prof-btn--ghost" :disabled="saving" @click="leaveEditMode">
            取消
          </button>
          <button type="button" class="home-prof-btn home-prof-btn--gold" :disabled="saving" @click="onSave">
            <span v-if="!saving">保存</span>
            <span v-else class="home-prof-btn__saving">
              <i class="el-icon-loading"></i> 保存中…
            </span>
          </button>
        </div>
      </template>
    </div>
  </el-dialog>
</template>

<script>
import DpUserAvatar from '@/components/DpUserAvatar.vue'
import { dpResultSuccess, dpResultData, dpResultMessage } from '@/utils/dpApiResult'
import { formatNetWinMultiplier, formatRoomNetMultiplier } from '@/utils/dpRoomNetMultiplier'
import { avatarCacheBustFromUpdatedAt, avatarFileSrc } from '@/utils/dpAvatarUrl'

var HONOR_GLITCH_CHARS = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ!@#$%&*'
var HONOR_SCRAMBLE_MS = 320
var HONOR_SCRAMBLE_TICK_MS = 48

export default {
  name: 'HomeProfileModal',
  components: { DpUserAvatar },
  props: {
    visible: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      mode: 'view',
      loading: false,
      saving: false,
      avatarUploading: false,
      avatarCacheBust: '',
      editingPassword: false,
      honorGlitchPhase: 'idle',
      honorGlitchTick: 0,
      honorGlitchTimer: null,
      honorRevealTimer: null,
      form: {
        id: '',
        nickname: '',
        avatarUrl: '',
        avatarUpdatedAt: null,
        passwordSet: true,
        royalFlushWins: null,
        straightFlushWins: null,
        fourOfAKindWins: null,
        largestPotWon: null,
        largestRoomNet: null,
        totalHandsPlayed: null,
        oldPassword: '',
        newPassword: '',
        confirmPassword: ''
      }
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
    showBackdrop() {
      if (this.mode === 'edit' || this.shouldSkipEffects()) return false
      return !!this.backdropSrc
    },
    backdropSrc() {
      if (!this.form.avatarUrl) return ''
      var bust = this.avatarCacheBust || avatarCacheBustFromUpdatedAt(this.form.avatarUpdatedAt)
      return avatarFileSrc(this.form.avatarUrl, bust, { variant: 'full' })
    },
    honorValClass() {
      return {
        'home-prof-honor-val--reveal': this.honorGlitchPhase === 'revealed' && !this.shouldSkipEffects()
      }
    }
  },
  watch: {
    visible(v) {
      if (v) {
        this.mode = 'view'
        this.loadProfile()
      } else {
        this.stopHonorGlitch()
      }
    }
  },
  beforeDestroy() {
    this.stopHonorGlitch()
  },
  methods: {
    avatarCacheBustFromUpdatedAt,
    formatNetWinMultiplier,
    formatRoomNetMultiplier,
    shouldSkipEffects() {
      if (typeof document !== 'undefined' && document.body.getAttribute('data-dp-fluidity') === 'eco') {
        return true
      }
      if (
        typeof window !== 'undefined' &&
        window.matchMedia &&
        window.matchMedia('(prefers-reduced-motion: reduce)').matches
      ) {
        return true
      }
      return false
    },
    enterEditMode() {
      this.mode = 'edit'
      this.form.oldPassword = ''
      this.form.newPassword = ''
      this.form.confirmPassword = ''
      this.editingPassword = false
    },
    leaveEditMode() {
      this.mode = 'view'
      this.editingPassword = false
      this.form.oldPassword = ''
      this.form.newPassword = ''
      this.form.confirmPassword = ''
    },
    onClosed() {
      this.mode = 'view'
      this.editingPassword = false
      this.honorGlitchPhase = 'idle'
      this.form.oldPassword = ''
      this.form.newPassword = ''
      this.form.confirmPassword = ''
      this.stopHonorGlitch()
    },
    stopHonorGlitch() {
      if (this.honorGlitchTimer != null) {
        clearInterval(this.honorGlitchTimer)
        this.honorGlitchTimer = null
      }
      if (this.honorRevealTimer != null) {
        clearTimeout(this.honorRevealTimer)
        this.honorRevealTimer = null
      }
    },
    startHonorGlitch() {
      this.stopHonorGlitch()
      if (this.form.totalHandsPlayed == null) {
        this.honorGlitchPhase = 'idle'
        return
      }
      if (this.shouldSkipEffects()) {
        this.honorGlitchPhase = 'revealed'
        return
      }
      var self = this
      this.honorGlitchPhase = 'scrambling'
      this.honorGlitchTick = 0
      this.honorGlitchTimer = setInterval(function () {
        self.honorGlitchTick++
      }, HONOR_SCRAMBLE_TICK_MS)
      this.honorRevealTimer = setTimeout(function () {
        if (self.honorGlitchTimer != null) {
          clearInterval(self.honorGlitchTimer)
          self.honorGlitchTimer = null
        }
        self.honorGlitchPhase = 'revealed'
      }, HONOR_SCRAMBLE_MS)
    },
    randomGlitchString(len) {
      var s = ''
      var n = len > 0 ? len : 3
      for (var i = 0; i < n; i++) {
        s += HONOR_GLITCH_CHARS.charAt(Math.floor(Math.random() * HONOR_GLITCH_CHARS.length))
      }
      return s
    },
    honorResolvedCount(key) {
      var v = this.form[key]
      return v != null ? String(v) : '0'
    },
    honorResolvedStat(key) {
      if (key === 'largestPotWon') {
        return formatNetWinMultiplier(this.form.largestPotWon)
      }
      if (key === 'largestRoomNet') {
        return formatRoomNetMultiplier(this.form.largestRoomNet)
      }
      return this.honorResolvedCount(key)
    },
    honorDisplayCount(key) {
      var resolved = this.honorResolvedCount(key)
      if (this.honorGlitchPhase === 'revealed' || this.shouldSkipEffects()) {
        return resolved
      }
      if (this.honorGlitchPhase === 'scrambling') {
        void this.honorGlitchTick
        return this.randomGlitchString(resolved.length || 2)
      }
      return '···'
    },
    honorDisplayStat(key) {
      var resolved = this.honorResolvedStat(key)
      if (this.honorGlitchPhase === 'revealed' || this.shouldSkipEffects()) {
        return resolved
      }
      if (this.honorGlitchPhase === 'scrambling') {
        void this.honorGlitchTick
        return this.randomGlitchString(Math.max(resolved.length, 3))
      }
      return '···'
    },
    async loadProfile() {
      this.loading = true
      this.honorGlitchPhase = 'idle'
      this.stopHonorGlitch()
      try {
        const res = await this.$http.get('/dpUser/profile')
        const body = res.data
        if (!dpResultSuccess(body)) {
          this.$message.error(dpResultMessage(body) || '加载资料失败')
          this.dialogVisible = false
          return
        }
        const d = dpResultData(body) || {}
        const profile = d.profile || {}
        this.form.id = profile.id
        this.form.nickname = profile.nickname || ''
        this.form.avatarUrl = profile.avatarUrl || ''
        this.form.avatarUpdatedAt = profile.avatarUpdatedAt != null ? profile.avatarUpdatedAt : null
        this.avatarCacheBust = ''
        this.form.passwordSet = profile.passwordSet !== false
        this.form.royalFlushWins = profile.royalFlushWins
        this.form.straightFlushWins = profile.straightFlushWins
        this.form.fourOfAKindWins = profile.fourOfAKindWins
        this.form.largestPotWon = profile.largestPotWon
        this.form.largestRoomNet = profile.largestRoomNet
        this.form.totalHandsPlayed = profile.totalHandsPlayed
      } catch (e) {
        this.$message.error('加载资料失败')
        this.dialogVisible = false
      } finally {
        this.loading = false
        this.$nextTick(function () {
          this.startHonorGlitch()
        }.bind(this))
      }
    },
    async onAvatarUploadRequest(options) {
      var file = options && options.file
      if (!file) return
      if (file.size > 2 * 1024 * 1024) {
        this.$message.warning('图片不能超过 2MB')
        return
      }
      var fd = new FormData()
      fd.append('file', file)
      this.avatarUploading = true
      try {
        const res = await this.$http.post('/dpUser/avatar', fd, {
          headers: { 'Content-Type': 'multipart/form-data' }
        })
        const body = res.data
        const data = dpResultData(body) || {}
        if (!dpResultSuccess(body)) {
          this.$message.error(dpResultMessage(body) || data.message || '上传失败')
          return
        }
        var url = data.avatarUrl || ''
        this.form.avatarUrl = url
        if (data.avatarUpdatedAt != null) {
          this.form.avatarUpdatedAt = data.avatarUpdatedAt
          this.avatarCacheBust = avatarCacheBustFromUpdatedAt(data.avatarUpdatedAt)
        } else {
          this.avatarCacheBust = Date.now()
        }
        this.$message.success(data.message || '上传成功')
        this.$emit('avatar-updated', {
          avatarUrl: url,
          cacheBust: this.avatarCacheBust,
          avatarUpdatedAt: data.avatarUpdatedAt
        })
      } catch (e) {
        this.$message.error('上传失败')
      } finally {
        this.avatarUploading = false
      }
    },
    async onSave() {
      if (!this.form.oldPassword) {
        this.$message.warning('请填写当前密码')
        return
      }
      if (!this.form.nickname) {
        this.$message.warning('昵称不能为空')
        return
      }
      if (/^\d+$/.test(this.form.nickname)) {
        this.$message.warning('昵称不能为纯数字')
        return
      }
      if (this.editingPassword && this.form.newPassword) {
        if (this.form.newPassword.length < 6) {
          this.$message.warning('新密码至少 6 位')
          return
        }
        if (this.form.newPassword !== this.form.confirmPassword) {
          this.$message.warning('两次输入的新密码不一致')
          return
        }
      }
      const payload = {
        nickname: this.form.nickname,
        oldPassword: this.form.oldPassword
      }
      if (this.editingPassword && this.form.newPassword) {
        payload.newPassword = this.form.newPassword
      }
      this.saving = true
      try {
        const res = await this.$http.put('/dpUser/profile', payload)
        const body = res.data
        if (!dpResultSuccess(body)) {
          this.$message.error(dpResultMessage(body) || '保存失败')
          return
        }
        const saved = dpResultData(body) || {}
        this.$message.success(saved.message || '保存成功')
        this.$emit('saved', {
          nickname: saved.nickname || this.form.nickname,
          token: saved.token,
          nicknameChanged: !!saved.nicknameChanged,
          newPassword: this.editingPassword && this.form.newPassword ? this.form.newPassword : '',
          passwordForStorage: this.form.oldPassword
        })
        this.dialogVisible = false
      } catch (e) {
        const msg =
          (e.response && e.response.data && (e.response.data.message || e.response.data.msg)) ||
          '保存失败'
        this.$message.error(msg)
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style scoped>
/* ============================================
   大厅个人资料卡 — home-prof-*（主题自适应）
   ============================================ */

.home-prof-card {
  --home-prof-card-max: min(92vw, 520px);
  --home-prof-avatar-desktop: clamp(88px, 22vw, 120px);
  --home-prof-glitch-duration: 800ms;
  position: relative;
  overflow: hidden;
  border-radius: 14px;
  min-height: 200px;
}

.home-prof-backdrop {
  display: none;
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
}
.home-prof-backdrop__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: saturate(1.08);
  transform: scale(1.08);
}
.home-prof-backdrop__scrim {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    160deg,
    color-mix(in srgb, var(--dp-panel-bg) 50%, transparent),
    color-mix(in srgb, var(--dp-panel-bg) 80%, #000 16%)
  );
}

.home-prof-card__inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px 0 8px;
}

.home-prof-card--edit {
  background: var(--dp-panel-bg);
}

.home-prof-card--edit .home-prof-card__inner {
  padding: 0;
}

/* ---- 标题栏 ---- */
.home-prof-title-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 4px;
}
.home-prof-title-bar__deco {
  display: flex;
  align-items: center;
  gap: 8px;
}
.home-prof-title-bar__text {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--dp-text-primary);
}
.home-prof-suit {
  font-size: 14px;
  line-height: 1;
}
.home-prof-suit--spade { color: var(--dp-text-primary); }
.home-prof-suit--heart,
.home-prof-suit--diamond { color: var(--dp-danger); }
.home-prof-suit--club { color: var(--dp-text-primary); }
.home-prof-title-bar__close {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid var(--dp-input-border);
  background: var(--dp-panel-bg);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: var(--dp-text-muted);
  transition: all 0.2s;
  flex-shrink: 0;
}
.home-prof-title-bar__close:hover {
  background: color-mix(in srgb, var(--dp-danger) 12%, var(--dp-panel-bg));
  border-color: var(--dp-danger);
  color: var(--dp-danger);
}

/* ---- 加载 ---- */
.home-prof-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 40px 0;
  color: var(--dp-text-muted);
  font-size: 14px;
}
.home-prof-loading__chip {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  border: 3px solid var(--dp-subpanel-border);
  border-top-color: var(--dp-warning);
  animation: home-prof-spin 0.8s linear infinite;
}
@keyframes home-prof-spin {
  to { transform: rotate(360deg); }
}

/* ---- 身份区 ---- */
.home-prof-identity {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  text-align: center;
}

.home-prof-avatar-frame {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8px;
  border-radius: 12px;
  background: linear-gradient(
    145deg,
    color-mix(in srgb, var(--dp-text-muted) 30%, var(--dp-panel-bg)),
    color-mix(in srgb, var(--dp-text-muted) 50%, var(--dp-panel-bg)) 30%,
    color-mix(in srgb, var(--dp-text-muted) 40%, var(--dp-panel-bg)) 55%,
    color-mix(in srgb, var(--dp-text-muted) 15%, var(--dp-panel-bg)) 85%
  );
  box-shadow:
    0 4px 16px rgba(0, 0, 0, 0.12),
    0 0 0 1px rgba(0, 0, 0, 0.06),
    inset 0 1px 0 rgba(255, 255, 255, 0.4);
}
.home-prof-avatar-frame__bezel {
  position: relative;
  z-index: 2;
  border-radius: 10px;
  overflow: hidden;
  box-shadow:
    inset 0 2px 6px rgba(0, 0, 0, 0.15),
    0 0 0 2px rgba(0, 0, 0, 0.08);
}
.home-prof-avatar-frame__corner {
  position: absolute;
  z-index: 3;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--dp-text-muted) 60%, var(--dp-panel-bg));
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.2),
    inset 0 1px 0 rgba(255, 255, 255, 0.5);
}
.home-prof-avatar-frame__corner--tl { top: 6px; left: 6px; }
.home-prof-avatar-frame__corner--tr { top: 6px; right: 6px; }
.home-prof-avatar-frame__corner--bl { bottom: 6px; left: 6px; }
.home-prof-avatar-frame__corner--br { bottom: 6px; right: 6px; }

.home-prof-identity__meta {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: 100%;
}
.home-prof-name {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--dp-text-primary);
  letter-spacing: 0.03em;
  line-height: 1.25;
}
.home-prof-id {
  margin: 0;
  font-size: 12px;
  color: var(--dp-text-muted);
  font-family: 'Courier New', monospace;
  background: color-mix(in srgb, var(--dp-subpanel-bg) 88%, transparent);
  padding: 3px 12px;
  border-radius: 10px;
}
.home-prof-avatar-hint {
  margin: 0;
  font-size: 11px;
  color: var(--dp-text-muted);
}

/* ---- 按钮 ---- */
.home-prof-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  border-radius: 8px;
  font-family: inherit;
  cursor: pointer;
  font-size: 14px;
  padding: 9px 20px;
  font-weight: 600;
  transition: all 0.2s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}
.home-prof-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.home-prof-btn--sm {
  font-size: 12px;
  padding: 5px 12px;
}
.home-prof-btn--outline {
  background: transparent;
  border: 1.5px solid var(--dp-warning);
  color: var(--dp-warning);
}
.home-prof-btn--outline:hover:not(:disabled) {
  background: var(--dp-warning);
  color: #fff;
}
.home-prof-btn--ghost {
  background: var(--dp-subpanel-bg);
  color: var(--dp-text-secondary);
}
.home-prof-btn--ghost:hover:not(:disabled) {
  background: var(--dp-input-border);
}
.home-prof-btn--gold {
  background: linear-gradient(135deg, var(--dp-warning), color-mix(in srgb, var(--dp-warning) 70%, #000));
  color: #fff;
  box-shadow: 0 3px 12px color-mix(in srgb, var(--dp-warning) 35%, transparent);
}
.home-prof-btn--gold:hover:not(:disabled) {
  box-shadow: 0 5px 18px color-mix(in srgb, var(--dp-warning) 50%, transparent);
  transform: translateY(-1px);
}
.home-prof-btn--gold:active:not(:disabled) {
  transform: translateY(0);
}
.home-prof-btn__saving {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.home-prof-section-deco {
  font-size: 12px;
  opacity: 0.5;
}

/* ---- 荣誉墙 ---- */
.home-prof-honor {
  background: color-mix(in srgb, var(--dp-subpanel-bg) 72%, transparent);
  border-radius: 14px;
  padding: 14px 12px;
  border: 1px solid color-mix(in srgb, var(--dp-subpanel-border) 80%, transparent);
  position: relative;
  overflow: hidden;
}
.home-prof-honor__title {
  text-align: center;
  font-size: 14px;
  font-weight: 700;
  color: var(--dp-text-primary);
  margin-bottom: 12px;
  letter-spacing: 0.06em;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.home-prof-honor__medals {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
}
.home-prof-honor__stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.home-prof-medal {
  flex: 1 1 0;
  min-width: 88px;
  min-height: 96px;
  border-radius: 12px;
  padding: 10px 6px;
  text-align: center;
  border: 1.5px solid;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}
.home-prof-medal::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: linear-gradient(
    to top,
    rgba(0, 0, 0, 0.78) 0%,
    rgba(0, 0, 0, 0.48) 42%,
    rgba(0, 0, 0, 0.12) 100%
  );
  pointer-events: none;
}
.home-prof-medal--royal {
  border-color: color-mix(in srgb, var(--dp-warning) 65%, transparent);
  background-image: url('~@/assets/RF.webp');
}
.home-prof-medal--straight {
  border-color: color-mix(in srgb, var(--dp-accent) 65%, transparent);
  background-image: url('~@/assets/SF.webp');
}
.home-prof-medal--four {
  border-color: color-mix(in srgb, var(--dp-danger) 65%, transparent);
  background-image: url('~@/assets/4K.webp');
}
.home-prof-medal__body {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.home-prof-medal__name {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.82);
}
.home-prof-medal .home-prof-honor-val {
  color: #fff;
}
.home-prof-medal .home-prof-honor-val small {
  color: rgba(255, 255, 255, 0.72);
}
.home-prof-honor-val {
  font-size: 20px;
  font-weight: 800;
  color: var(--dp-text-primary);
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.home-prof-honor-val small {
  font-size: 11px;
  font-weight: 500;
  color: var(--dp-text-muted);
}

@keyframes home-prof-honor-reveal {
  0% {
    opacity: 0.35;
    filter: brightness(2);
    transform: scale(1.06);
  }
  40% {
    opacity: 1;
    filter: brightness(1.35);
  }
  100% {
    opacity: 1;
    filter: none;
    transform: none;
  }
}
.home-prof-honor-val--reveal {
  animation: home-prof-honor-reveal var(--home-prof-glitch-duration) cubic-bezier(0.22, 1, 0.36, 1) both;
}

.home-prof-stat-card {
  background: color-mix(in srgb, var(--dp-panel-bg) 88%, transparent);
  border-radius: 10px;
  padding: 8px 6px;
  text-align: center;
  border: 1px solid color-mix(in srgb, var(--dp-subpanel-border) 75%, transparent);
}
.home-prof-stat-card__label {
  display: block;
  font-size: 10px;
  color: var(--dp-text-muted);
  margin-bottom: 4px;
  line-height: 1.2;
}
.home-prof-stat-card__value {
  display: block;
  font-size: 16px;
}

/* ---- 编辑区 ---- */
.home-prof-edit-section {
  background: var(--dp-subpanel-bg);
  border-radius: 14px;
  padding: 18px 16px;
  border: 1px solid var(--dp-subpanel-border);
}
.home-prof-edit-section__title {
  text-align: center;
  font-size: 15px;
  font-weight: 700;
  color: var(--dp-text-primary);
  margin-bottom: 14px;
  letter-spacing: 0.06em;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.home-prof-form >>> .el-form-item__label {
  padding-bottom: 4px;
  font-weight: 600;
  font-size: 13px;
  color: var(--dp-text-primary);
}
.home-prof-form >>> .el-input__inner {
  border-radius: 8px;
  border-color: var(--dp-input-border);
  font-size: 14px;
  background: var(--dp-input-bg);
  color: var(--dp-text-primary);
  transition: border-color 0.2s, box-shadow 0.2s;
}
.home-prof-form >>> .el-input__inner:focus {
  border-color: var(--dp-warning);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--dp-warning) 12%, transparent);
}
.home-prof-pwd-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.home-prof-pwd-hint {
  flex: 1 1 140px;
  font-size: 13px;
  color: var(--dp-text-muted);
  line-height: 1.4;
}
.home-prof-field-gap {
  margin-top: 8px;
}

/* ---- Footer ---- */
.home-prof-footer {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
  padding-top: 4px;
}

/* ---- 桌面方卡 ≥641px ---- */
@media (min-width: 641px) {
  .home-prof-card:not(.home-prof-card--edit) {
    aspect-ratio: 1 / 1;
    display: flex;
    flex-direction: column;
    background: var(--dp-panel-bg);
  }

  .home-prof-card:not(.home-prof-card--edit) .home-prof-backdrop {
    display: block;
  }

  .home-prof-card--effects-off:not(.home-prof-card--edit) .home-prof-backdrop {
    display: none;
  }

  .home-prof-card__inner--view {
    flex: 1;
    min-height: 0;
    padding: 12px 14px 0;
    justify-content: space-between;
  }

  .home-prof-identity {
    flex-direction: row;
    align-items: flex-start;
    text-align: left;
    gap: 14px;
  }

  .home-prof-identity__meta {
    align-items: flex-start;
    flex: 1;
    min-width: 0;
    padding-top: 4px;
  }

  .home-prof-avatar-frame {
    flex-shrink: 0;
    width: var(--home-prof-avatar-desktop);
    height: var(--home-prof-avatar-desktop);
    padding: 6px;
    box-sizing: border-box;
  }

  .home-prof-avatar-frame__bezel {
    width: 100%;
    height: 100%;
  }

  .home-prof-avatar-frame__bezel >>> .dp-user-avatar--lg {
    width: 100% !important;
    height: 100% !important;
    min-width: 0;
    min-height: 0;
  }

  .home-prof-honor {
    flex-shrink: 0;
  }

  .home-prof-footer--view {
    padding: 0 14px 12px;
    margin-top: auto;
  }

  .home-prof-name {
    font-size: clamp(17px, 2.5vw, 20px);
  }
}

/* ---- 手机竖卡 ≤640px ---- */
@media (max-width: 640px) {
  .home-prof-card:not(.home-prof-card--edit) {
    background: var(--dp-panel-bg);
  }

  .home-prof-honor__medals {
    padding-bottom: 2px;
  }

  .home-prof-stat-card__value {
    font-size: 14px;
  }

  .home-prof-honor-val {
    font-size: 18px;
  }
}
</style>

<style>
.home-profile-dialog {
  max-width: calc(100vw - 16px);
  border-radius: 16px !important;
  overflow: hidden;
}
.home-profile-dialog .el-dialog__header {
  padding: 18px 20px 8px;
  background: var(--dp-panel-bg);
}
.home-profile-dialog .el-dialog__body {
  padding: 8px 16px 12px;
  background: var(--dp-panel-bg);
}
.home-profile-dialog .el-dialog__footer {
  display: none;
}

/* eco / PRM：跳过头像 blur 与 honor 揭示动画 */
body[data-dp-fluidity='eco'] .home-prof-backdrop {
  display: none !important;
}
body[data-dp-fluidity='eco'] .home-prof-honor-val--reveal {
  animation: none !important;
  filter: none !important;
  transform: none !important;
}
body[data-dp-fluidity='eco'] .home-prof-medal:hover,
body[data-dp-fluidity='eco'] .home-prof-stat-card:hover {
  transform: none;
}

@media (prefers-reduced-motion: reduce) {
  .home-prof-backdrop {
    display: none !important;
  }
  .home-prof-honor-val--reveal {
    animation: none !important;
    filter: none !important;
    transform: none !important;
  }
  .home-prof-loading__chip {
    animation: none !important;
  }
}
</style>
