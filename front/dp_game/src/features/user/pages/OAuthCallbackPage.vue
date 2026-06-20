<template>
  <div class="oauth-callback">
    <div v-if="loading" class="oauth-callback__status">
      <p>正在验证授权…</p>
    </div>
    <div v-else-if="error" class="oauth-callback__status oauth-callback__status--error">
      <p>{{ error }}</p>
      <button @click="goLogin">返回登录</button>
    </div>
  </div>
</template>

<script>
import { dpResultSuccess, dpResultData, dpResultMessage } from '@shared/utils/dpApiResult'
import { enterLobbyAfterAuth } from '@features/user/utils/dpAuthEnterLobby'
import { bootstrapDpAuthPermissions } from '@features/auth/utils/dpAuthBootstrap'

export default {
  data() {
    return {
      loading: true,
      error: ''
    }
  },
  created() {
    this.handleCallback()
  },
  methods: {
    handleCallback() {
      const query = this.$route.query || {}
      const errorMsg = query.error

      if (errorMsg) {
        this.error = decodeURIComponent(errorMsg)
        this.loading = false
        return
      }

      // 登录模式：拿 oid 换 token
      const oid = query.oid
      if (!oid) {
        this.error = '缺少授权凭证，请重新登录'
        this.loading = false
        return
      }

      this.$http.post('/oauth/exchange-token', { oid })
        .then((res) => {
          const d = res.data
          if (!dpResultSuccess(d)) {
            this.error = dpResultMessage(d) || 'GitHub 授权失败'
            this.loading = false
            return
          }

          const payload = dpResultData(d) || {}

          const row = {
            nickname: payload.nickname,
            password: '',
            userId: payload.userId,
            token: payload.token
          }
          localStorage.setItem('userInfo', JSON.stringify(row))

          if (payload.needSetupNickname) {
            this.$message.info('请先设置一个合规的昵称')
            this.$router.replace('/home')
          } else {
            var self = this
            bootstrapDpAuthPermissions(this).then(function () {
              enterLobbyAfterAuth(self.$router, self, { message: '登录成功' })
            })
          }
        })
        .catch(() => {
          this.error = '授权验证失败，请重试'
          this.loading = false
        })
    },
    goLogin() {
      this.$router.replace('/login')
    }
  }
}
</script>

<style scoped>
.oauth-callback {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: var(--dp-game-bg, #0a0a0a);
  color: var(--dp-text-primary, #e0e0e0);
  font-family: inherit;
}
.oauth-callback__status {
  text-align: center;
  font-size: 15px;
}
.oauth-callback__status--error p {
  margin-bottom: 18px;
  color: #e06c75;
}
.oauth-callback__status--error button {
  padding: 8px 24px;
  font-size: 14px;
  cursor: pointer;
}
</style>
