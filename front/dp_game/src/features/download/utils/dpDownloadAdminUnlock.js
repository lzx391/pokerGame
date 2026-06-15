/** sessionStorage helpers for download center admin password unlock (per browser tab). */

var UNLOCK_KEY = 'dp_download_admin_unlock'

export function dpDownloadAdminSessionPassword() {
  try {
    return sessionStorage.getItem(UNLOCK_KEY) || ''
  } catch (e) {
    return ''
  }
}

export function isDownloadAdminUnlocked() {
  return !!dpDownloadAdminSessionPassword()
}

export function setDownloadAdminSessionUnlock(password) {
  try {
    sessionStorage.setItem(UNLOCK_KEY, String(password || ''))
  } catch (e) {
    /* ignore quota / private mode */
  }
}

export function clearDownloadAdminSessionUnlock() {
  try {
    sessionStorage.removeItem(UNLOCK_KEY)
  } catch (e) {
    /* ignore */
  }
}
