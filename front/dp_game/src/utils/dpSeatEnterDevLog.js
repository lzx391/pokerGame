/** Dev-only console logging for retro8bit seat enter reveal. Stripped in production builds. */
var PREFIX = '[dp-seat-enter]'

export function dpSeatEnterDevLog(message, detail) {
  if (process.env.NODE_ENV !== 'development') return
  if (detail !== undefined) {
    console.log(PREFIX + ' ' + message, detail)
  } else {
    console.log(PREFIX + ' ' + message)
  }
}
