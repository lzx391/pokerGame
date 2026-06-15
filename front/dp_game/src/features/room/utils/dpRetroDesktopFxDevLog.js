/** Dev logging for retro8bit desktop ambience FX. */
var PREFIX = '[dp-retro-desktop-fx]'
var MONSTER_PREFIX = '[dp-retro-monster]'
var _startupHintLogged = false

/** Optional QA flag: 3s glitch cycles + 2x monster sprite (CSS). Logs are always on. */
export function retroGlitchDebugEnabled() {
  if (typeof localStorage === 'undefined') return false
  return localStorage.getItem('retroGlitchDebug') === '1'
}

function shouldLogVerbose() {
  return process.env.NODE_ENV === 'development' || retroGlitchDebugEnabled()
}

export function dpRetroDesktopFxDevLog(message, detail) {
  if (!shouldLogVerbose()) return
  if (detail !== undefined) {
    console.log(PREFIX + ' ' + message, detail)
  } else {
    console.log(PREFIX + ' ' + message)
  }
}

/** Monster glitch pipeline tracing — always prints in all builds. */
export function dpRetroMonsterLog(message, detail) {
  if (detail !== undefined) {
    console.log(MONSTER_PREFIX + ' ' + message, detail)
  } else {
    console.log(MONSTER_PREFIX + ' ' + message)
  }
}

/** Log once per page load on first retro desktop game mount. */
export function dpRetroMonsterLogStartupHint() {
  if (_startupHintLogged) return
  _startupHintLogged = true
  console.info(
    MONSTER_PREFIX + " logs always on. Optional: localStorage.setItem('retroGlitchDebug','1') → 3s cycles + 2x sprite"
  )
}

/**
 * Always-on gate diagnostic (any build). Call when retro8bit game mounts or gates change.
 */
export function dpRetroMonsterGateLog(detail) {
  if (detail !== undefined) {
    console.info(MONSTER_PREFIX + ' gate', detail)
  } else {
    console.info(MONSTER_PREFIX + ' gate')
  }
}
