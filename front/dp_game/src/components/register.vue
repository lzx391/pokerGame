<template>
  <div class="register-form">
    <h2 class="register-form__title">用户注册</h2>
    <p class="register-form__hint">注册后即可用昵称登录并进入房间</p>

    <div class="form-item">
      <label for="reg-nickname">昵称</label>
      <input
        id="reg-nickname"
        v-model="form.nickname"
        type="text"
        placeholder="请设置登录昵称"
        autocomplete="off"
      >
    </div>

    <div class="form-item">
      <label for="reg-password">密码</label>
      <input
        id="reg-password"
        v-model="form.password"
        type="password"
        placeholder="请设置登录密码"
        autocomplete="new-password"
      >
    </div>

    <button type="button" class="register-btn" @click="handleRegister">
      注册
    </button>
  </div>
</template>

<script>
import { dpResultSuccess, dpResultData, dpResultMessage } from '@/utils/dpApiResult'

export default {
  data() {
    return {
      form: {
        nickname: '',
        password: ''
      }
    }
  },
  methods: {
    handleRegister() {
      if (!this.form.nickname.trim()) {
        alert('请输入昵称！')
        return
      }
      if (!this.form.password) {
        alert('请输入密码！')
        return
      }

      this.$http
        .post('/dpUser/registerUser', this.form)
        .then((res) => {
          console.log('注册结果：', res.data)
          var d = res.data
          if (dpResultSuccess(d)) {
            var inner = dpResultData(d) || {}
            var msg = inner.message != null ? String(inner.message) : '注册成功'
            alert(msg)
            this.$router.push('/login')
          } else {
            alert(dpResultMessage(d))
          }
        })
        .catch((err) => {
          console.error('注册请求异常：', err)
          alert('网络异常，请稍后再试！')
        })
    }
  }
}
</script>

<style scoped>
.register-form {
  width: 100%;
  max-width: 360px;
  margin: 0 auto;
  text-align: center;
}
.register-form__title {
  margin: 0 0 8px;
  font-size: 1.35rem;
  font-weight: 600;
  color: var(--dp-text-primary, inherit);
}
.register-form__hint {
  margin: 0 0 22px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--dp-text-muted, #909399);
}
.form-item {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  justify-content: flex-start;
  text-align: left;
  gap: 10px;
}
.form-item label {
  flex: 0 0 48px;
  font-size: 14px;
  color: var(--dp-text-secondary, #666);
}
.form-item input {
  flex: 1 1 auto;
  min-width: 0;
  height: 40px;
  padding: 0 12px;
  border-radius: 8px;
}
.register-btn {
  width: 100%;
  max-width: 280px;
  height: 44px;
  margin-top: 12px;
  font-size: 15px;
  font-weight: 600;
}
</style>
