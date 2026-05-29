const sharp = require('sharp')
const path = require('path')
const fs = require('fs')

const srcDir = path.resolve(__dirname, '..', 'src', 'assets')
const files = ['RF.png', 'SF.png', '4K.png']

async function run() {
  for (const name of files) {
    const input = path.join(srcDir, name)
    const outWebp = path.join(srcDir, name.replace('.png', '.webp'))

    const srcSize = (fs.statSync(input).size / 1024).toFixed(0)

    await sharp(input)
      .resize(512)
      .webp({ quality: 80 })
      .toFile(outWebp)

    const outSize = (fs.statSync(outWebp).size / 1024).toFixed(0)
    console.log(`${name}: ${srcSize}KB → ${name.replace('.png', '.webp')}: ${outSize}KB`)
  }
  console.log('done')
}

run().catch(e => { console.error(e); process.exit(1) })
