/** Dev-only: retro8bit 正多边形桌几何与座位映射。生产构建不输出。 */
var PREFIX = '[dp-table-layout]'

export function dpTableLayoutDevLog(message, detail) {
  if (process.env.NODE_ENV !== 'development') return
  if (detail !== undefined) {
    console.info(PREFIX + ' ' + message, detail)
  } else {
    console.info(PREFIX + ' ' + message)
  }
}
