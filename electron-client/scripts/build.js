const { execSync } = require('child_process')
const fs = require('fs')
const path = require('path')

// 1. prebuild: 复制 dist
const src = path.join(__dirname, '..', '..', 'front', 'dp_game', 'dist')
const dst = path.join(__dirname, '..', 'dist-frontend')
fs.rmSync(dst, { recursive: true, force: true })
fs.cpSync(src, dst, { recursive: true })
console.log('Copied dist -> dist-frontend')

// 2. 时间戳输出目录
const now = new Date()
const ts = `${now.getFullYear()}${pad(now.getMonth()+1)}${pad(now.getDate())}-${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
const outputDir = `MGDemoPlus-${ts}`
function pad(n) { return String(n).padStart(2, '0') }

// 3. 打包
const mirror = process.env.ELECTRON_MIRROR || ''
const cmd = `npx electron-builder --win dir --c.directories.output=${outputDir}`
const env = { ...process.env }
if (mirror) env.ELECTRON_MIRROR = mirror

console.log(`Building to: ${outputDir}`)
execSync(cmd, { env, stdio: 'inherit', cwd: path.join(__dirname, '..') })
console.log(`\nDone! Output: ${outputDir}\\win-unpacked\\MGDemoPlus.exe`)
