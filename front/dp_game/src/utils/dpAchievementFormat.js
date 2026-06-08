/**
 * 成就解锁时间展示：YYYY-MM-DD HH:mm:ss（本地时区）。
 * @param {string|number|Date|null|undefined} raw
 * @returns {string} 格式化结果；无效输入返回空字符串
 */
export function formatAchievementUnlockedAt(raw) {
  if (raw == null || raw === '') return ''
  var d = new Date(raw)
  if (isNaN(d.getTime())) return ''
  var y = d.getFullYear()
  var m = String(d.getMonth() + 1).padStart(2, '0')
  var day = String(d.getDate()).padStart(2, '0')
  var h = String(d.getHours()).padStart(2, '0')
  var min = String(d.getMinutes()).padStart(2, '0')
  var s = String(d.getSeconds()).padStart(2, '0')
  return y + '-' + m + '-' + day + ' ' + h + ':' + min + ':' + s
}

/**
 * 已解锁成就展示用：有 unlockedAt 则格式化，旧数据缺失时返回 em dash。
 * @param {string|number|Date|null|undefined} raw
 * @returns {string}
 */
export function displayAchievementUnlockedAt(raw) {
  var formatted = formatAchievementUnlockedAt(raw)
  return formatted || '\u2014'
}
