const {defineConfig} = require('@vue/cli-service')
module.exports = defineConfig({
    transpileDependencies: true,
    devServer: {
        // 开发访问 http://127.0.0.1:8880（docker/nginx 同上域转发），勿直连本端口依赖代理
        client: {
            webSocketURL: 'auto://0.0.0.0:0/ws'
        }
    }
})
