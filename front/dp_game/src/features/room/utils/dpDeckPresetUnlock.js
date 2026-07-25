/** sessionStorage helpers for experimental deck preset password unlock (per room, per browser tab). */

var UNLOCK_PREFIX = 'dp_deck_preset_unlock_'

export function dpDeckPresetUnlockKey(roomId) {
  return UNLOCK_PREFIX + String(roomId || '')
}

export function dpDeckPresetSessionPassword(roomId) {
  try {
    return sessionStorage.getItem(dpDeckPresetUnlockKey(roomId)) || ''
  } catch (e) {
    return ''
  }
}

export function isDeckPresetUnlocked(roomId) {
  return !!dpDeckPresetSessionPassword(roomId)
}

export function setDeckPresetSessionUnlock(roomId, password) {
  try {
    sessionStorage.setItem(dpDeckPresetUnlockKey(roomId), String(password || ''))
  } catch (e) {
    /* ignore quota / private mode */
  }
}

export function clearDeckPresetSessionUnlock(roomId) {
  try {
    sessionStorage.removeItem(dpDeckPresetUnlockKey(roomId))
  } catch (e) {
    /* ignore */
  }
}
