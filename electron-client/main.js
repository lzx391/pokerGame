const { app, BrowserWindow } = require('electron')
const path = require('path')
const config = require('./config.json')

function createWindow() {
  const win = new BrowserWindow({
    width: config.windowWidth || 1280,
    height: config.windowHeight || 800,
    minWidth: 960,
    minHeight: 600,
    title: config.windowTitle || 'MGDemoPlus 德州扑克',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  })

  const distPath = path.join(__dirname, 'dist-frontend', 'index.html').replace(/\\/g, '/')

  // 监听页面错误并打印到终端
  win.webContents.on('console-message', (_event, _level, message) => {
    console.log('[RENDERER]', message)
  })
  win.webContents.on('did-fail-load', (_event, errorCode, errorDescription, validatedURL) => {
    console.error('[LOAD FAILED]', errorCode, errorDescription, validatedURL)
  })

  // 打包版也开 DevTools 调试
  win.webContents.openDevTools({ mode: 'detach' })

  console.log('[MAIN] Loading:', distPath)
  win.loadURL(`file:///${distPath}`)

  win.setMenuBarVisibility(false)
}

app.whenReady().then(createWindow)

app.on('window-all-closed', () => { app.quit() })
app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) createWindow()
})
