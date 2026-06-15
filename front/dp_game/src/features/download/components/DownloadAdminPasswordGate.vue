<template>
  <div>
    <el-dialog
      v-if="!isRetro8bit"
      :visible.sync="dialogVisible"
      title="下载中心 · 管理验证"
      width="420px"
      top="18vh"
      custom-class="dp-download-admin-gate"
      append-to-body
      :close-on-click-modal="false"
      @closed="onClosed"
    >
      <p class="dp-download-admin-gate__hint">
        请输入管理密码。验证通过后，本浏览器标签页内可上传或下架安装包。
      </p>
      <el-input
        v-model="passwordInput"
        type="password"
        placeholder="管理密码"
        show-password
        autocomplete="off"
        :disabled="submitting"
        @keyup.enter.native="submit"
      />
      <p v-if="errorMessage" class="dp-download-admin-gate__error" role="alert">{{ errorMessage }}</p>
      <span slot="footer" class="dialog-footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确认</el-button>
      </span>
    </el-dialog>

    <dp-retro-password-gate-shell
      v-else
      :visible.sync="dialogVisible"
      title="> DOWNLOAD // ADMIN"
      prompt="[ADMIN] ACCESS:"
      :logs="retroLogs"
      :error-message="errorMessage"
      :submitting="submitting"
      primary-label="EXECUTE"
      primary-loading-label="VERIFY..."
      input-aria-label="下载中心管理密码"
      @submit="submit"
      @close="onClosed"
    />
  </div>
</template>

<script>
import { dpResultData, dpResultMessage, dpResultSuccess } from '@shared/utils/dpApiResult'
import DpRetroPasswordGateShell from '@shared/components/DpRetroPasswordGateShell.vue'

export default {
  name: 'DownloadAdminPasswordGate',
  components: { DpRetroPasswordGateShell },
  props: {
    visible: { type: Boolean, default: false },
    gameUiTheme: { type: String, default: 'default' }
  },
  data: function () {
    return {
      passwordInput: '',
      errorMessage: '',
      submitting: false
    }
  },
  computed: {
    dialogVisible: {
      get: function () {
        return this.visible
      },
      set: function (v) {
        this.$emit('update:visible', v)
      }
    },
    isRetro8bit: function () {
      return this.gameUiTheme === 'retro8bit'
    },
    retroLogs: function () {
      return [
        '> clearance required for upload / delete',
        '> session unlock persists until tab close'
      ]
    }
  },
  watch: {
    visible: function (v) {
      if (v) {
        this.passwordInput = ''
        this.errorMessage = ''
      }
    }
  },
  methods: {
    onClosed: function () {
      this.passwordInput = ''
      this.errorMessage = ''
      this.$emit('dismiss')
    },
    submit: async function (retroPassword) {
      if (this.submitting) return
      var pwd = this.isRetro8bit
        ? (retroPassword || '').trim()
        : (this.passwordInput || '').trim()
      if (!pwd) {
        this.errorMessage = this.isRetro8bit ? 'PASSWORD REQUIRED' : '请输入管理密码'
        return
      }
      this.submitting = true
      this.errorMessage = ''
      try {
        var res = await this.$http.post('/dpDownload/verifyAdminPassword', { adminPassword: pwd })
        var body = res.data
        if (!dpResultSuccess(body)) {
          this.errorMessage = dpResultMessage(body) || (this.isRetro8bit ? 'ACCESS DENIED' : '密码验证失败')
          return
        }
        var d = dpResultData(body) || {}
        this.$emit('verified', pwd)
        this.dialogVisible = false
        if (d.message && !this.isRetro8bit) {
          this.$message.success(d.message)
        }
      } catch (err) {
        this.errorMessage = this.isRetro8bit
          ? 'NETWORK ERROR'
          : ('网络错误: ' + (err && err.message ? err.message : '请稍后再试'))
      } finally {
        this.submitting = false
      }
    }
  }
}
</script>

<style scoped>
.dp-download-admin-gate__hint {
  margin: 0 0 12px;
  font-size: 13px;
  line-height: 1.5;
  color: #595959;
}
.dp-download-admin-gate__error {
  margin: 10px 0 0;
  font-size: 13px;
  color: #cf1322;
}
</style>
