const { defineConfig } = require('@vue/cli-service')
module.exports = defineConfig({
  transpileDependencies: true,
    devServer: {
    client: {
      // 让 WebSocket URL 自动跟随页面协议和主机变化
      webSocketURL: 'auto://0.0.0.0:0/ws'
    }
  }
})
