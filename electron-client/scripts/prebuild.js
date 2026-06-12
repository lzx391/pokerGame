const { execSync } = require('child_process')
const fs = require('fs')
const path = require('path')

const repoRoot = path.join(__dirname, '..', '..')
const frontendDir = path.join(repoRoot, 'front', 'dp_game')
const distSrc = path.join(frontendDir, 'dist')
const distDst = path.join(__dirname, '..', 'dist-frontend')

console.log('[prebuild] Building frontend for Electron (relative publicPath)...')
execSync('npm run build:electron', { cwd: frontendDir, stdio: 'inherit' })

fs.rmSync(distDst, { recursive: true, force: true })
fs.cpSync(distSrc, distDst, { recursive: true })
console.log('[prebuild] Copied dist -> dist-frontend')
