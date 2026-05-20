<template>
  <el-dialog
    title="个人资料"
    :visible.sync="dialogVisible"
    width="min(92vw, 420px)"
    custom-class="home-profile-dialog"
    append-to-body
    :close-on-click-modal="!saving"
    @closed="onClosed"
  >
    <div v-if="loading" class="home-profile-modal__loading">加载中…</div>
    <el-form
      v-else
      ref="formRef"
      :model="form"
      label-position="top"
      class="home-profile-modal__form"
      @submit.native.prevent="onSave"
    >
      <el-form-item label="用户 ID">
        <el-input :value="String(form.id)" disabled />
      </el-form-item>

      <!-- 生涯荣誉战绩 -->
      <el-form-item v-if="form.totalHandsPlayed != null" label="生涯战绩">
        <div class="home-profile-modal__honor">
          <div class="home-profile-modal__honor-row">
            <div class="honor-badge honor-badge--royal">
              <span class="honor-badge__icon">RF</span>
              <span class="honor-badge__label">皇家同花顺</span>
              <span class="honor-badge__count">{{ form.royalFlushWins || 0 }}</span>
            </div>
            <div class="honor-badge honor-badge--straight">
              <span class="honor-badge__icon">SF</span>
              <span class="honor-badge__label">同花顺</span>
              <span class="honor-badge__count">{{ form.straightFlushWins || 0 }}</span>
            </div>
            <div class="honor-badge honor-badge--four">
              <span class="honor-badge__icon">4K</span>
              <span class="honor-badge__label">四条</span>
              <span class="honor-badge__count">{{ form.fourOfAKindWins || 0 }}</span>
            </div>
          </div>
          <div class="home-profile-modal__honor-stats">
            <div class="honor-stat">
              <span class="honor-stat__label">单局最高净赢</span>
              <span class="honor-stat__value">{{ form.largestPotWon || 0 }} BC</span>
            </div>
            <div class="honor-stat">
              <span class="honor-stat__label">单房间最高净赢</span>
              <span class="honor-stat__value">{{ form.largestRoomNet || 0 }} BC</span>
            </div>
            <div class="honor-stat">
              <span class="honor-stat__label">生涯总局数</span>
              <span class="honor-stat__value">{{ form.totalHandsPlayed || 0 }}</span>
            </div>
          </div>
        </div>
      </el-form-item>

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
        <div class="home-profile-modal__pwd-row">
          <span class="home-profile-modal__pwd-hint">
            {{ form.passwordSet ? '已设置（服务器仅存加密摘要，无法展示原文）' : '未设置' }}
          </span>
          <button
            v-if="!editingPassword"
            type="button"
            class="dp-btn dp-btn--ghost home-profile-modal__pwd-toggle"
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
            placeholder="新密码（至少 6 位，留空表示不改）"
            class="home-profile-modal__field-gap"
          />
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="再次输入新密码"
            class="home-profile-modal__field-gap"
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
    <span slot="footer" class="home-profile-modal__footer">
      <button type="button" class="dp-btn dp-btn--ghost" :disabled="saving" @click="dialogVisible = false">
        取消
      </button>
      <button type="button" class="dp-btn dp-btn--primary" :disabled="loading || saving" @click="onSave">
        {{ saving ? '保存中…' : '保存' }}
      </button>
    </span>
  </el-dialog>
</template>

<script>
import { dpResultSuccess, dpResultData, dpResultMessage } from '@/utils/dpApiResult'

export default {
  name: 'HomeProfileModal',
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
      editingPassword: false,
      form: {
        id: '',
        nickname: '',
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
    async onSave() {
      if (!this.form.oldPassword) {
        this.$message.warning('请填写当前密码')
        return
      }
      if (!this.form.nickname) {
        this.$message.warning('昵称不能为空')
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
.home-profile-modal__loading {
  text-align: center;
  padding: 24px 0;
  color: var(--dp-text-secondary, #666);
}
.home-profile-modal__form >>> .el-form-item__label {
  padding-bottom: 4px;
  line-height: 1.3;
}
.home-profile-modal__pwd-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.home-profile-modal__pwd-hint {
  flex: 1 1 160px;
  font-size: 13px;
  color: var(--dp-text-secondary, #666);
  line-height: 1.4;
}
.home-profile-modal__pwd-toggle {
  flex-shrink: 0;
  padding: 4px 10px;
  font-size: 13px;
}
.home-profile-modal__field-gap {
  margin-top: 8px;
}
.home-profile-modal__footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

/* ---- 荣誉战绩 ---- */
.home-profile-modal__honor {
  background: var(--dp-subpanel-bg, #fafafa);
  border-radius: 8px;
  padding: 12px 14px;
  border: 1px solid var(--dp-subpanel-border, #e8e8e8);
}
.home-profile-modal__honor-row {
  display: flex;
  gap: 8px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}
.honor-badge {
  flex: 1 1 90px;
  min-width: 80px;
  border-radius: 8px;
  padding: 10px 8px;
  text-align: center;
  border: 1px solid;
}
.honor-badge--royal {
  background: color-mix(in srgb, var(--dp-warning, #faad14) 10%, transparent);
  border-color: var(--dp-warning, #faad14);
  color: var(--dp-warning, #b45309);
}
.honor-badge--straight {
  background: color-mix(in srgb, var(--dp-accent, #1890ff) 10%, transparent);
  border-color: var(--dp-accent, #1890ff);
  color: var(--dp-accent, #1565c0);
}
.honor-badge--four {
  background: color-mix(in srgb, var(--dp-danger, #ff4d4f) 10%, transparent);
  border-color: var(--dp-danger, #ff4d4f);
  color: var(--dp-danger, #c62828);
}
.honor-badge__icon {
  display: block;
  font-size: 16px;
  font-weight: 800;
  margin-bottom: 2px;
  letter-spacing: 1px;
}
.honor-badge__label {
  display: block;
  font-size: 11px;
  color: var(--dp-text-muted, #999);
  margin-bottom: 3px;
}
.honor-badge__count {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: var(--dp-text-primary, #333);
}
.home-profile-modal__honor-stats {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.honor-stat {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}
.honor-stat__label {
  color: var(--dp-text-muted, #999);
}
.honor-stat__value {
  font-weight: 600;
  color: var(--dp-text-primary, #333);
}
</style>

<style>
/* 窄屏：对话框贴边留白 */
.home-profile-dialog {
  max-width: calc(100vw - 16px);
}
.home-profile-dialog .el-dialog__body {
  padding-top: 8px;
  padding-bottom: 8px;
}
</style>
