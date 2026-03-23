const {defineConfig} = require('@vue/cli-service')
module.exports = defineConfig({
    transpileDependencies: true,
    devServer: {
        // 保持你原有的 client 配置
        client: {
            webSocketURL: 'auto://0.0.0.0:0/ws'
        },
        // 添加下面的代理配置
        proxy: {
            // 这里的 '/dev-api' 要和你在 main.js 中设置的 axios.defaults.baseURL 一致
            '/dev-api': {
                target: 'http://localhost:8088', // 你的后端真实 IP
                changeOrigin: true,              // 允许跨域
                pathRewrite: {                   // 路径重写
                    '^/dev-api': ''                // 转发时去掉 /dev-api 前缀
                }
            },
            // 游戏长连接：必须用独立前缀，不能占用 /ws（上面 client.webSocketURL 已把 HMR 挂在 /ws，会冲突）
            '/dp-ws': {
                target: 'http://127.0.0.1:8088',
                changeOrigin: true,
                ws: true,
                pathRewrite: { '^/dp-ws': '/ws' }
            }
        }
    }
})
