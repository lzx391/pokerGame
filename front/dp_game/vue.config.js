const {defineConfig} = require('@vue/cli-service')
// Electron 桌面客户端：用相对路径（file:// 协议下绝对路径会解析到磁盘根目录）
const publicPath = process.env.ELECTRON_BUILD ? './' : '/'
module.exports = defineConfig({
    publicPath,
    transpileDependencies: true,
    /** 浏览器标签页标题；不设时默认用 package.json 的 name（即 dp_game） */
    pages: {
        index: {
            entry: 'src/main.js',
            title: 'poker_demo',
        },
    },
    devServer: {
        // 保持你原有的 client 配置
        client: {
            webSocketURL: 'auto://0.0.0.0:0/ws'
        },
        // 添加下面的代理配置
        proxy: {
            // SSE 必须单独配置且排在 /dev-api 之前，否则 dev-server 会缓冲 text/event-stream，浏览器收不到 notify
            '/dev-api/dp/social/stream': {
                target: 'http://localhost:8088',
                changeOrigin: true,
                pathRewrite: { '^/dev-api': '' },
                onProxyRes(proxyRes) {
                    proxyRes.headers['cache-control'] = 'no-cache, no-transform'
                    proxyRes.headers['x-accel-buffering'] = 'no'
                    proxyRes.headers['connection'] = 'keep-alive'
                }
            },
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
