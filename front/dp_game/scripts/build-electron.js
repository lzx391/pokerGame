const { execSync } = require('child_process')

execSync('npx vue-cli-service build', {
  stdio: 'inherit',
  env: { ...process.env, ELECTRON_BUILD: 'true' }
})
