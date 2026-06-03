/**
 * retro8bit 进房终端 boot 文案（逐行 OK 序列）
 * @typedef {{ text: string, ok: boolean }} RetroBootLine
 */

/** @type {RetroBootLine[]} */
export const RETRO_BOOT_LINES_CREATE = [
  { text: 'ROOM-CRT v1.0 BOOT', ok: false },
  { text: '[  OK  ]  Validating table parameters...', ok: true },
  { text: '[  OK  ]  Allocating seat map...', ok: true },
  { text: '[  OK  ]  Opening room channel...', ok: true },
  { text: '[  OK  ]  Starting first hand...', ok: true }
]

/** @type {RetroBootLine[]} */
export const RETRO_BOOT_LINES_JOIN = [
  { text: 'POKER-SYS v2.4.1 NETLINK', ok: false },
  { text: '[  OK  ]  Resolving room endpoint...', ok: true },
  { text: '[  OK  ]  Connecting to room...', ok: true },
  { text: '[  OK  ]  Syncing seat map...', ok: true },
  { text: '[  OK  ]  Entering table. Stand by.', ok: true }
]

/** @type {RetroBootLine[]} */
export const RETRO_BOOT_LINES_QUICK_MATCH = [
  { text: 'MATCH-CRT v1.0 LINK', ok: false },
  { text: '[  OK  ]  Pairing opponents...', ok: true },
  { text: '[  OK  ]  Match found...', ok: true },
  { text: '[  OK  ]  Opening table channel...', ok: true },
  { text: '[  OK  ]  Dealing first hand...', ok: true }
]

/** @param {'create'|'join'|'quickmatch'|string} [entryPath] */
export function getRetroBootLines(entryPath) {
  if (entryPath === 'create') return RETRO_BOOT_LINES_CREATE.slice()
  if (entryPath === 'quickmatch') return RETRO_BOOT_LINES_QUICK_MATCH.slice()
  return RETRO_BOOT_LINES_JOIN.slice()
}
