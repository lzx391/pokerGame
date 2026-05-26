const { contextBridge } = require('electron')

// 读取同目录下的 config.json（Node.js API 在 preload 中可用，sandbox:false）
let serverUrl = 'https://catandppoker.asia'
try {
  const fs = require('fs')
  const cfg = JSON.parse(fs.readFileSync(__dirname + '\\config.json', 'utf-8'))
  serverUrl = cfg.serverUrl || serverUrl
} catch (e) {
  console.error('[preload] config.json load failed, using default:', e.message)
}

contextBridge.exposeInMainWorld('dpElectron', {
  serverUrl: serverUrl,
  isElectron: true,
  platform: process.platform
})
