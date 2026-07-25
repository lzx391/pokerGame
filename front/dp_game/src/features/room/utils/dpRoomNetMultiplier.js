/** 净赢倍数展示（单局/单房间）：保留两位小数并加「倍」后缀。 */
export function formatNetWinMultiplier(value) {
  const n = Number(value)
  if (value == null || value === '' || Number.isNaN(n)) {
    return '0.00 倍'
  }
  return n.toFixed(2) + ' 倍'
}

/** @deprecated 请用 formatNetWinMultiplier；保留别名兼容旧引用。 */
export function formatRoomNetMultiplier(value) {
  return formatNetWinMultiplier(value)
}
