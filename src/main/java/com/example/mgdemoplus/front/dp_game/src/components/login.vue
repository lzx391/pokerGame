<template>
  <div class="login-box">
    <h1>登录</h1>

    <!-- 昵称输入框 -->
    <div class="form-item">
      <label>昵称：</label>
      <input v-model="nickname" type="text" placeholder="请输入昵称">
    </div>

    <!-- 密码输入框 -->
    <div class="form-item">
      <label>密码：</label>
      <input v-model="password" type="password" placeholder="请输入密码">
    </div>

    <!-- 登录按钮（预留了跳转空间） -->
    <button class="login-btn" @click="login">
      登录
    </button>
  </div>
</template>

<script>
export default {
  data() {
    return {
      // 绑定输入框的数据
      nickname: "",
      password: ""
    };
  },
  methods: {
    // 登录请求方法
    login() {
      // 简单非空判断
      if (!this.nickname || !this.password) {
        alert("请输入昵称和密码");
        return;
      }
      this.$http.get("/dpUser/loginUser", {
  params: {
    nickname: this.nickname,
    password: this.password
  }
}).then(res => {                  
  console.log("登录结果：", res.data);
  
  // ============== 修复点：必须判断登录成功才允许进主页 ==============
  if (res.data === "登录成功") {
    alert("登录成功！");
    // 保存用户信息
    localStorage.setItem("userInfo", JSON.stringify({
      nickname: this.nickname,
      password: this.password
    }));
    // 只有成功才跳转
    this.$router.push("/home");
  } else {
    // 登录失败：提示 + 不跳转 + 不存信息
    alert("登录失败：" + res.data);
  }
  
}).catch(err => {
  console.error("请求失败", err);
  alert("登录请求异常，请重试");
});
      //   // 向后端发送登录请求（参数：nickname + password，地址 /dpUser/loginUser）
      //   this.$http.get("/dpUser/loginUser", {
      //     nickname: this.nickname,
      //     password: this.password
      //   }).then(res => {
      //     console.log("登录结果：", res.data);
      //     alert(res.data); // 弹出后端返回的 成功/失败 提示


      //     // ====================== 预留空间 ======================
      //     // 你在这里写跳转逻辑：
      //     // 1. 跳组件：this.$router.push("/xxx")
      //     // 2. 跳html：window.location.href = "xxx.html"
      //     // ======================================================

      //   }).catch(err => {
      //     console.error("请求失败", err);
      //     alert("登录请求异常");
      //   });
    }
  }
};
</script>

<!-- 简单样式，让页面好看点 -->
<style scoped>
.login-box {
  width: 300px;
  margin: 100px auto;
  text-align: center;
}

.form-item {
  margin: 15px 0;
}

input {
  width: 200px;
  height: 30px;
  padding: 0 8px;
}

.login-btn {
  width: 218px;
  height: 38px;
  background: #409eff;
  color: #fff;
  border: none;
  border-radius: 4px;
  margin-top: 10px;
  cursor: pointer;
}
</style>