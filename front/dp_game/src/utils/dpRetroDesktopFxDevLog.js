/** Dev logging for retro8bit desktop ambience FX. */
var PREFIX = '[dp-retro-desktop-fx]'

function retroGlitchDebugEnabled() {
  if (typeof localStorage === 'undefined') return false
  return localStorage.getItem('retroGlitchDebug') === '1'
}

export function dpRetroDesktopFxDevLog(message, detail) {
  if (process.env.NODE_ENV !== 'development' && !retroGlitchDebugEnabled()) return
  if (detail !== undefined) {
    console.log(PREFIX + ' ' + message, detail)
  } else {
    console.log(PREFIX + ' ' + message)
  }
}
