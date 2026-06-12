/** Dev-only console logging for retro8bit table seat rays. Stripped in production builds. */
var PREFIX = '[dp-seat-ray]'

export function dpSeatRayDevLog(message, detail) {
  if (process.env.NODE_ENV !== 'development') return
  if (detail !== undefined) {
    console.info(PREFIX + ' ' + message, detail)
  } else {
    console.info(PREFIX + ' ' + message)
  }
}
