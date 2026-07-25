/**
 * 复制社交场景下的用户 ID（数字字符串）到剪贴板。
 * @param {string|number} raw
 * @param {{ onSuccess?: () => void }} [opts]
 */
export function copySocialId(raw, opts) {
  var s = raw != null ? String(raw).trim() : ''
  if (!s) return
  var onSuccess = opts && opts.onSuccess
  var done = function () {
    if (onSuccess) onSuccess()
  }
  if (typeof navigator !== 'undefined' && navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(s).then(done).catch(function () {})
    return
  }
  try {
    var ta = document.createElement('textarea')
    ta.value = s
    ta.setAttribute('readonly', '')
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    done()
  } catch (e) {
    /* ignore */
  }
}
