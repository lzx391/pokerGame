/** Dev-only console logging for retro8bit hand hologram click path. Stripped in production builds. */
var PREFIX = '[dp-hand-hologram]'

export function dpHandHologramDevLog(message, detail) {
  if (process.env.NODE_ENV !== 'development') return
  if (detail !== undefined) {
    console.log(PREFIX + ' ' + message, detail)
  } else {
    console.log(PREFIX + ' ' + message)
  }
}
