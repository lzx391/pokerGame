/** 对局页全屏边界调试描边开关（默认关闭）。 */
var STORAGE_KEY = 'dpDebugBounds'

export var DP_DEBUG_BOUNDS_BODY_CLASS = 'dp-debug-bounds-active'

export function isDpDebugBoundsEnabled() {
  try {
    if (typeof window === 'undefined') return false
    var params = new URLSearchParams(window.location.search)
    if (params.get('dpDebugBounds') === '1') return true
    var stored = localStorage.getItem(STORAGE_KEY)
    return stored === '1' || stored === 'true'
  } catch (e) {
    return false
  }
}
