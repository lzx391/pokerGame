const path = require('path')
const {defineConfig} = require('@vue/cli-service')

const publicPath = process.env.ELECTRON_BUILD ? './' : '/'

module.exports = defineConfig({
    publicPath,
    transpileDependencies: true,
    configureWebpack: {
        resolve: {
            alias: {
                '@': path.resolve(__dirname, 'src'),
                '@shared': path.resolve(__dirname, 'src/shared'),
                '@features': path.resolve(__dirname, 'src/features')
            }
        }
    },
    pages: {
        index: {
            entry: 'src/main.js',
            title: 'poker_demo',
        },
    },
})
