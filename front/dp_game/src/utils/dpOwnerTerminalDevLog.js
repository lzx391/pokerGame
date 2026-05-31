/** Dev-only console logging for retro8bit owner terminal panel. Stripped in production builds. */
var PREFIX = '[dp-owner-terminal]'

export function dpOwnerTerminalDevLog(message, detail) {
  if (process.env.NODE_ENV !== 'development') return
  if (detail !== undefined) {
    console.info(PREFIX + ' ' + message, detail)
  } else {
    console.info(PREFIX + ' ' + message)
  }
}
