<template>
  <el-dialog
    :visible.sync="dialogVisible"
    width="min(94vw, 460px)"
    custom-class="home-profile-dialog"
    append-to-body
    :close-on-click-modal="!saving"
    :show-close="false"
    @closed="onClosed"
  >
    <div slot="title" class="prof-title-bar">
      <div class="prof-title-bar__deco">
        <span class="prof-suit prof-suit--spade">♠</span>
        <span class="prof-suit prof-suit--heart">♥</span>
        <span class="prof-title-bar__text">个人资料</span>
        <span class="prof-suit prof-suit--diamond">♦</span>
        <span class="prof-suit prof-suit--club">♣</span>
      </div>
      <button type="button" class="prof-title-bar__close" :disabled="saving" @click="dialogVisible = false" aria-label="关闭">
        <i class="el-icon-close"></i>
      </button>
    </div>

    <div v-if="loading" class="prof-loading">
      <span class="prof-loading__chip"></span>
      <span>加载中…</span>
    </div>

    <div v-else class="prof-body">
      <!-- 头像区 -->
      <div class="prof-avatar-stage">
        <div class="prof-avatar-frame prof-avatar-frame--normal">
          <div class="prof-avatar-frame__bezel">
            <dp-user-avatar
              :avatar-url="form.avatarUrl"
              :nickname="form.nickname"
              :cache-bust="avatarCacheBust || avatarCacheBustFromUpdatedAt(form.avatarUpdatedAt)"
              size="lg"
            />
          </div>
          <span class="prof-avatar-frame__corner prof-avatar-frame__corner--tl"></span>
          <span class="prof-avatar-frame__corner prof-avatar-frame__corner--tr"></span>
          <span class="prof-avatar-frame__corner prof-avatar-frame__corner--bl"></span>
          <span class="prof-avatar-frame__corner prof-avatar-frame__corner--br"></span>
        </div>
        <div class="prof-avatar-name">{{ form.nickname || '未设置昵称' }}</div>
        <div class="prof-avatar-id">ID: {{ form.id }}</div>
        <el-upload
          class="prof-avatar-upload"
          action=""
          accept="image/jpeg,image/png,image/webp,image/gif"
          :show-file-list="false"
          :disabled="avatarUploading"
          :http-request="onAvatarUploadRequest"
        >
          <button type="button" class="prof-btn prof-btn--outline" :disabled="avatarUploading">
            <i class="el-icon-camera"></i> {{ avatarUploading ? '上传中…' : '更换头像' }}
          </button>
        </el-upload>
        <p class="prof-avatar-hint">jpg / png / webp / gif，最大 2MB</p>
      </div>

      <!-- 生涯荣誉勋章墙 -->
      <div v-if="form.totalHandsPlayed != null" class="prof-honor-wall">
        <div class="prof-honor-wall__title">
          <span class="prof-section-deco">♦</span> 生涯荣誉 <span class="prof-section-deco">♦</span>
        </div>
        <div class="prof-honor-wall__medals">
          <div class="prof-medal prof-medal--royal">
            <div class="prof-medal__chip">
              <span class="prof-medal__suit prof-medal__suit--royal">♛</span>
            </div>
            <div class="prof-medal__body">
              <span class="prof-medal__name">皇家同花顺</span>
              <span class="prof-medal__count">{{ form.royalFlushWins || 0 }}<small> 次</small></span>
            </div>
          </div>
          <div class="prof-medal prof-medal--straight">
            <div class="prof-medal__chip">
              <span class="prof-medal__suit prof-medal__suit--straight">♠</span>
            </div>
            <div class="prof-medal__body">
              <span class="prof-medal__name">同花顺</span>
              <span class="prof-medal__count">{{ form.straightFlushWins || 0 }}<small> 次</small></span>
            </div>
          </div>
          <div class="prof-medal prof-medal--four">
            <div class="prof-medal__chip">
              <span class="prof-medal__suit prof-medal__suit--four">4</span>
            </div>
            <div class="prof-medal__body">
              <span class="prof-medal__name">四条</span>
              <span class="prof-medal__count">{{ form.fourOfAKindWins || 0 }}<small> 次</small></span>
            </div>
          </div>
        </div>
        <div class="prof-honor-wall__stats">
          <div class="prof-stat-card">
            <span class="prof-stat-card__label">最高净赢倍数</span>
            <span class="prof-stat-card__value">{{ formatNetWinMultiplier(form.largestPotWon) }}</span>
          </div>
          <div class="prof-stat-card">
            <span class="prof-stat-card__label">单房最高净赢</span>
            <span class="prof-stat-card__value">{{ formatRoomNetMultiplier(form.largestRoomNet) }}</span>
          </div>
          <div class="prof-stat-card">
            <span class="prof-stat-card__label">生涯总局数</span>
            <span class="prof-stat-card__value">{{ form.totalHandsPlayed || 0 }}</span>
          </div>
        </div>
      </div>

      <!-- 编辑区 -->
      <div class="prof-edit-section">
        <div class="prof-edit-section__title">
          <span class="prof-section-deco">♣</span> 编辑资料 <span class="prof-section-deco">♣</span>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          label-position="top"
          class="prof-form"
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
            <div class="prof-pwd-row">
              <span class="prof-pwd-hint">
                {{ form.passwordSet ? '已设置（加密存储，不可查看）' : '未设置' }}
              </span>
              <button
                v-if="!editingPassword"
                type="button"
                class="prof-btn prof-btn--outline prof-btn--sm"
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
                class="prof-field-gap"
              />
              <el-input
                v-model="form.confirmPassword"
                type="password"
                show-password
                autocomplete="new-password"
                placeholder="再次输入新密码"
                class="prof-field-gap"
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

    <span slot="footer" class="prof-footer">
      <button type="button" class="prof-btn prof-btn--ghost" :disabled="saving" @click="dialogVisible = false">
        取消
      </button>
      <button type="button" class="prof-btn prof-btn--gold" :disabled="loading || saving" @click="onSave">
        <span v-if="!saving">保存</span>
        <span v-else class="prof-btn__saving">
          <i class="el-icon-loading"></i> 保存中…
        </span>
      </button>
    </span>
  </el-dialog>
</template>

<script>
import DpUserAvatar from '@/components/DpUserAvatar.vue'
import { dpResultSuccess, dpResultData, dpResultMessage } from '@/utils/dpApiResult'
import { formatNetWinMultiplier, formatRoomNetMultiplier } from '@/utils/dpRoomNetMultiplier'
import { avatarCacheBustFromUpdatedAt } from '@/utils/dpAvatarUrl'

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
      loading: false,
      saving: false,
      avatarUploading: false,
      avatarCacheBust: '',
      editingPassword: false,
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
    }
  },
  watch: {
    visible(v) {
      if (v) {
        this.loadProfile()
      }
    }
  },
  methods: {
    avatarCacheBustFromUpdatedAt,
    formatNetWinMultiplier,
    formatRoomNetMultiplier,
    onClosed() {
      this.editingPassword = false
      this.form.oldPassword = ''
      this.form.newPassword = ''
      this.form.confirmPassword = ''
    },
    async loadProfile() {
      this.loading = true
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
   个人资料 — 扑克荣誉勋章墙（主题自适应）
   ============================================ */

/* ---- 标题栏 ---- */
.prof-title-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 4px;
}
.prof-title-bar__deco {
  display: flex;
  align-items: center;
  gap: 8px;
}
.prof-title-bar__text {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--dp-text-primary);
}
.prof-suit {
  font-size: 14px;
  line-height: 1;
}
.prof-suit--spade  { color: var(--dp-text-primary); }
.prof-suit--heart  { color: var(--dp-danger); }
.prof-suit--diamond{ color: var(--dp-danger); }
.prof-suit--club   { color: var(--dp-text-primary); }
.prof-title-bar__close {
  width: 32px; height: 32px;
  border-radius: 50%;
  border: 1px solid var(--dp-input-border);
  background: var(--dp-panel-bg);
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px;
  color: var(--dp-text-muted);
  transition: all 0.2s;
  flex-shrink: 0;
}
.prof-title-bar__close:hover {
  background: color-mix(in srgb, var(--dp-danger) 12%, var(--dp-panel-bg));
  border-color: var(--dp-danger);
  color: var(--dp-danger);
}

/* ---- 加载态 ---- */
.prof-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 40px 0;
  color: var(--dp-text-muted);
  font-size: 14px;
}
.prof-loading__chip {
  width: 40px; height: 40px;
  border-radius: 50%;
  border: 3px solid var(--dp-subpanel-border);
  border-top-color: var(--dp-warning);
  animation: prof-spin 0.8s linear infinite;
}
@keyframes prof-spin { to { transform: rotate(360deg); } }

/* ---- Body ---- */
.prof-body {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* ---- 头像区 ---- */
.prof-avatar-stage {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 0 8px;
  position: relative;
}
.prof-avatar-stage::before {
  content: '';
  position: absolute;
  top: 0; left: 20%; right: 20%;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--dp-warning), transparent);
}
/* ---- 头像相框 ---- */
.prof-avatar-frame {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8px;
  border-radius: 50%;
  /* 默认相框：银灰色金属拉丝感 */
  background: linear-gradient(145deg,
    color-mix(in srgb, var(--dp-text-muted) 30%, var(--dp-panel-bg)),
    color-mix(in srgb, var(--dp-text-muted) 50%, var(--dp-panel-bg)) 30%,
    color-mix(in srgb, var(--dp-text-muted) 40%, var(--dp-panel-bg)) 55%,
    color-mix(in srgb, var(--dp-text-muted) 15%, var(--dp-panel-bg)) 85%
  );
  box-shadow:
    0 4px 16px rgba(0,0,0,0.12),
    0 0 0 1px rgba(0,0,0,0.06),
    inset 0 1px 0 rgba(255,255,255,0.4);
}
/* 内圈：像框压在照片上的斜面 */
.prof-avatar-frame__bezel {
  position: relative;
  z-index: 2;
  border-radius: 50%;
  overflow: hidden;
  box-shadow:
    inset 0 2px 6px rgba(0,0,0,0.15),
    0 0 0 2px rgba(0,0,0,0.08);
}
/* 四角铆钉 */
.prof-avatar-frame__corner {
  position: absolute;
  z-index: 3;
  width: 8px; height: 8px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--dp-text-muted) 60%, var(--dp-panel-bg));
  box-shadow:
    0 1px 3px rgba(0,0,0,0.2),
    inset 0 1px 0 rgba(255,255,255,0.5);
}
.prof-avatar-frame__corner--tl { top: 6px;  left: calc(50% - 4px); }
.prof-avatar-frame__corner--tr { top: 6px;  right: calc(50% - 4px); }
.prof-avatar-frame__corner--bl { bottom: 6px; left: calc(50% - 4px); }
.prof-avatar-frame__corner--br { bottom: 6px; right: calc(50% - 4px); }

/* 未来段位相框预留：加 .prof-avatar-frame--bronze / --silver / --gold 等 */
.prof-avatar-frame--normal {
  /* 当前默认样式，后期按段位覆盖 frame 的 background / box-shadow / corner 颜色 */
}
.prof-avatar-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--dp-text-primary);
  letter-spacing: 0.03em;
}
.prof-avatar-id {
  font-size: 12px;
  color: var(--dp-text-muted);
  font-family: 'Courier New', monospace;
  background: var(--dp-subpanel-bg);
  padding: 3px 12px;
  border-radius: 10px;
}
.prof-avatar-hint {
  margin: 0;
  font-size: 11px;
  color: var(--dp-text-muted);
}

/* ---- 通用按钮 ---- */
.prof-btn {
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
.prof-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.prof-btn--sm { font-size: 12px; padding: 5px 12px; }
.prof-btn--outline {
  background: transparent;
  border: 1.5px solid var(--dp-warning);
  color: var(--dp-warning);
}
.prof-btn--outline:hover:not(:disabled) {
  background: var(--dp-warning);
  color: #fff;
}
.prof-btn--ghost {
  background: var(--dp-subpanel-bg);
  color: var(--dp-text-secondary);
}
.prof-btn--ghost:hover:not(:disabled) {
  background: var(--dp-input-border);
}
.prof-btn--gold {
  background: linear-gradient(135deg, var(--dp-warning), color-mix(in srgb, var(--dp-warning) 70%, #000));
  color: #fff;
  box-shadow: 0 3px 12px color-mix(in srgb, var(--dp-warning) 35%, transparent);
}
.prof-btn--gold:hover:not(:disabled) {
  box-shadow: 0 5px 18px color-mix(in srgb, var(--dp-warning) 50%, transparent);
  transform: translateY(-1px);
}
.prof-btn--gold:active:not(:disabled) {
  transform: translateY(0);
}
.prof-btn__saving { display: inline-flex; align-items: center; gap: 4px; }

/* ---- 分区装饰 ---- */
.prof-section-deco {
  font-size: 12px;
  opacity: 0.5;
}

/* ======== 生涯荣誉勋章墙 ======== */
.prof-honor-wall {
  background: linear-gradient(160deg, var(--dp-subpanel-bg) 0%, color-mix(in srgb, var(--dp-subpanel-bg) 85%, var(--dp-panel-bg)) 100%);
  border-radius: 14px;
  padding: 18px 16px;
  border: 1px solid var(--dp-subpanel-border);
  position: relative;
  overflow: hidden;
}
/* 背景装饰：透明卡牌花纹 */
.prof-honor-wall::before {
  content: '♠ ♥ ♦ ♣ ♠ ♥ ♦ ♣';
  position: absolute;
  top: -8px; right: -20px;
  font-size: 60px;
  letter-spacing: 8px;
  line-height: 1;
  color: color-mix(in srgb, var(--dp-warning) 4%, transparent);
  pointer-events: none;
  white-space: nowrap;
}
.prof-honor-wall__title {
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
.prof-honor-wall__medals {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
}
.prof-honor-wall__stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

/* ---- 勋章 ---- */
.prof-medal {
  flex: 1 1 0;
  min-width: 0;
  background: var(--dp-panel-bg);
  border-radius: 12px;
  padding: 12px 8px;
  text-align: center;
  border: 1.5px solid;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
  position: relative;
}
.prof-medal:hover {
  transform: translateY(-3px);
}
.prof-medal--royal {
  border-color: var(--dp-warning);
  box-shadow: 0 2px 10px color-mix(in srgb, var(--dp-warning) 12%, transparent);
}
.prof-medal--royal:hover { box-shadow: 0 6px 20px color-mix(in srgb, var(--dp-warning) 25%, transparent); }
.prof-medal--straight {
  border-color: var(--dp-accent);
  box-shadow: 0 2px 10px color-mix(in srgb, var(--dp-accent) 8%, transparent);
}
.prof-medal--straight:hover { box-shadow: 0 6px 20px color-mix(in srgb, var(--dp-accent) 18%, transparent); }
.prof-medal--four {
  border-color: var(--dp-danger);
  box-shadow: 0 2px 10px color-mix(in srgb, var(--dp-danger) 8%, transparent);
}
.prof-medal--four:hover { box-shadow: 0 6px 20px color-mix(in srgb, var(--dp-danger) 18%, transparent); }
.prof-medal__chip {
  width: 42px; height: 42px;
  border-radius: 50%;
  margin: 0 auto 8px;
  display: flex; align-items: center; justify-content: center;
}
.prof-medal--royal .prof-medal__chip {
  background: radial-gradient(circle at 35% 28%, rgba(255,255,255,0.55), transparent 50%),
    conic-gradient(
      color-mix(in srgb, var(--dp-warning) 70%, #fff),
      var(--dp-warning),
      color-mix(in srgb, var(--dp-warning) 70%, #000),
      var(--dp-warning),
      color-mix(in srgb, var(--dp-warning) 70%, #fff)
    );
  box-shadow: 0 0 12px color-mix(in srgb, var(--dp-warning) 30%, transparent);
}
.prof-medal--straight .prof-medal__chip {
  background: radial-gradient(circle at 35% 28%, rgba(255,255,255,0.55), transparent 50%),
    conic-gradient(
      color-mix(in srgb, var(--dp-accent) 70%, #fff),
      var(--dp-accent),
      color-mix(in srgb, var(--dp-accent) 70%, #000),
      var(--dp-accent),
      color-mix(in srgb, var(--dp-accent) 70%, #fff)
    );
  box-shadow: 0 0 12px color-mix(in srgb, var(--dp-accent) 25%, transparent);
}
.prof-medal--four .prof-medal__chip {
  background: radial-gradient(circle at 35% 28%, rgba(255,255,255,0.55), transparent 50%),
    conic-gradient(
      color-mix(in srgb, var(--dp-danger) 70%, #fff),
      var(--dp-danger),
      color-mix(in srgb, var(--dp-danger) 70%, #000),
      var(--dp-danger),
      color-mix(in srgb, var(--dp-danger) 70%, #fff)
    );
  box-shadow: 0 0 12px color-mix(in srgb, var(--dp-danger) 25%, transparent);
}
.prof-medal__suit {
  font-size: 20px;
  font-weight: 900;
  color: #fff;
  text-shadow: 0 1px 3px rgba(0,0,0,0.3);
  line-height: 1;
}
.prof-medal__body {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.prof-medal__name {
  font-size: 11px;
  color: var(--dp-text-muted);
  letter-spacing: 0.03em;
}
.prof-medal__count {
  font-size: 22px;
  font-weight: 800;
  color: var(--dp-text-primary);
  line-height: 1;
}
.prof-medal__count small {
  font-size: 12px;
  font-weight: 500;
  color: var(--dp-text-muted);
}

/* ---- 统计小卡 ---- */
.prof-stat-card {
  background: var(--dp-panel-bg);
  border-radius: 10px;
  padding: 10px 10px;
  text-align: center;
  border: 1px solid var(--dp-subpanel-border);
  transition: transform 0.2s ease;
}
.prof-stat-card:hover { transform: translateY(-2px); }
.prof-stat-card__label {
  display: block;
  font-size: 11px;
  color: var(--dp-text-muted);
  margin-bottom: 4px;
}
.prof-stat-card__value {
  display: block;
  font-size: 18px;
  font-weight: 700;
  color: var(--dp-text-primary);
  line-height: 1.2;
}

/* ======== 编辑资料区 ======== */
.prof-edit-section {
  background: linear-gradient(160deg, var(--dp-subpanel-bg) 0%, color-mix(in srgb, var(--dp-subpanel-bg) 85%, var(--dp-panel-bg)) 100%);
  border-radius: 14px;
  padding: 18px 16px;
  border: 1px solid var(--dp-subpanel-border);
}
.prof-edit-section__title {
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

/* ---- 表单 ---- */
.prof-form >>> .el-form-item__label {
  padding-bottom: 4px;
  font-weight: 600;
  font-size: 13px;
  color: var(--dp-text-primary);
}
.prof-form >>> .el-input__inner {
  border-radius: 8px;
  border-color: var(--dp-input-border);
  font-size: 14px;
  background: var(--dp-input-bg);
  color: var(--dp-text-primary);
  transition: border-color 0.2s, box-shadow 0.2s;
}
.prof-form >>> .el-input__inner:focus {
  border-color: var(--dp-warning);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--dp-warning) 12%, transparent);
}
.prof-form >>> .el-input.is-disabled .el-input__inner {
  background: var(--dp-subpanel-bg);
  color: var(--dp-text-muted);
  border-color: var(--dp-subpanel-border);
}

.prof-pwd-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.prof-pwd-hint {
  flex: 1 1 140px;
  font-size: 13px;
  color: var(--dp-text-muted);
  line-height: 1.4;
}
.prof-field-gap { margin-top: 8px; }

/* ---- Footer ---- */
.prof-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
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
  padding: 8px 20px 16px;
  background: var(--dp-panel-bg);
}
.home-profile-dialog .el-dialog__footer {
  padding: 12px 20px 18px;
  background: var(--dp-panel-bg);
}
</style>
