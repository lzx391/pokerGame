/** Dev-only console logging for retro8bit invite-friends panel. Stripped in production builds. */
var PREFIX = '[dp-invite-friends]'

export function dpInviteFriendsDevLog(message, detail) {
  if (process.env.NODE_ENV !== 'development') return
  if (detail !== undefined) {
    console.info(PREFIX + ' ' + message, detail)
  } else {
    console.info(PREFIX + ' ' + message)
  }
}
