<template>
  <div class="register-form">
    <h2>用户注册</h2>

    <!-- 昵称输入框 -->
    <div class="form-item">
      <label>昵称：</label>
      <input
        v-model="form.nickname"
        type="text"
        placeholder="请设置登录昵称"
        autocomplete="off"
      >
    </div>

    <!-- 密码输入框 -->
    <div class="form-item">
      <label>密码：</label>
      <input
        v-model="form.password"
        type="password"
        placeholder="请设置登录密码"
      >
    </div>

    <!-- 注册按钮 -->
    <button class="register-btn" @click="handleRegister">
      注册
    </button>
  </div>
</template>

<script>
import { dpResultSuccess, dpResultData, dpResultMessage } from '@/utils/dpApiResult'

export default {
  data() {
    return {
      // 表单数据，与后端DpUser实体类字段一致
      form: {
        nickname: "",
        password: ""
      }
    };
  },
  methods: {
    // 注册核心逻辑
    handleRegister() {
      // 1. 前端非空校验
      if (!this.form.nickname.trim()) {
        alert("请输入昵称！");
        return;
      }
      if (!this.form.password) {
        alert("请输入密码！");
        return;
      }

      // 2. 调用后端注册接口
      this.$http.post("/dpUser/registerUser", this.form)
        .then(res => {
          console.log("注册结果：", res.data);
          var d = res.data;
          if (dpResultSuccess(d)) {
            var inner = dpResultData(d) || {};
            var msg = inner.message != null ? String(inner.message) : '注册成功';
            alert(msg);
            this.$router.push("/login");
          } else {
            alert(dpResultMessage(d));
          }
        })
        .catch(err => {
          console.error("注册请求异常：", err);
          alert("网络异常，请稍后再试！");
        });
    }
  }
};
</script>

<style scoped>
.register-form {
  width: 100%;
  max-width: 350px;
}

.register-form h2 {
  color: #3a4b5c;
  margin-bottom: 25px;
  font-size: 22px;
}

.form-item {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
  justify-content: flex-start;
  text-align: left;
}

.form-item label {
  width: 70px;
  font-size: 16px;
  color: #3a4b5c;
}

.form-item input {
  flex: 1;
  height: 38px;
  padding: 0 12px;
  border: 1px solid #e6e6e6;
  border-radius: 6px;
  font-size: 15px;
  transition: border 0.3s;
}

.form-item input:focus {
  outline: none;
  border-color: #409eff;
}

.register-btn {
  width: 100%;
  height: 42px;
  background-color: #409eff;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-top: 10px;
}

.register-btn:hover {
  background-color: #338eef;
  transform: translateY(-2px);
}
</style>