/**
 * Inline pixel SVG sprites for retro8bit table glitch monsters (Space Invaders style).
 * Reference palette: cyan / magenta / yellow / red / blue / purple on dark felt.
 */

import { retroGlitchMonsterBurstAnchors } from './dpRetroTableFxGeometry'

export var RETRO_GLITCH_PALETTE = {
  cyan: '#00e5ff',
  magenta: '#ff4dd8',
  yellow: '#ffe033',
  red: '#ff3344',
  blue: '#4466ff',
  purple: '#9944ff',
  dark: '#0a0c0e',
  white: '#f0f4ff'
}

/** @typedef {{ id: string, name: string, pixels: Array<{ x: number, y: number, fill: string }> }} RetroGlitchSprite */

/** Build horizontally symmetric pixels from left-half offsets (dx from center column). */
function sym(centerX, pairs) {
  var out = []
  for (var i = 0; i < pairs.length; i++) {
    var dx = pairs[i][0]
    var y = pairs[i][1]
    var fill = pairs[i][2]
    if (dx === 0) {
      out.push({ x: centerX, y: y, fill: fill })
    } else {
      out.push({ x: centerX - dx, y: y, fill: fill })
      out.push({ x: centerX + dx, y: y, fill: fill })
    }
  }
  return out
}

function sprite(id, name, pairs) {
  return { id: id, name: name, pixels: sym(7, pairs) }
}

/** @type {RetroGlitchSprite[]} */
export var RETRO_GLITCH_MONSTER_SPRITES = [
  sprite('cyan-crab', 'Cyan Crab', [
    [0, 4, RETRO_GLITCH_PALETTE.cyan],
    [1, 4, RETRO_GLITCH_PALETTE.cyan],
    [2, 4, RETRO_GLITCH_PALETTE.cyan],
    [0, 5, RETRO_GLITCH_PALETTE.red],
    [1, 5, RETRO_GLITCH_PALETTE.cyan],
    [2, 5, RETRO_GLITCH_PALETTE.cyan],
    [0, 6, RETRO_GLITCH_PALETTE.cyan],
    [1, 6, RETRO_GLITCH_PALETTE.cyan],
    [2, 6, RETRO_GLITCH_PALETTE.cyan],
    [3, 7, RETRO_GLITCH_PALETTE.cyan],
    [4, 7, RETRO_GLITCH_PALETTE.cyan]
  ]),
  sprite('yellow-beetle', 'Yellow Beetle', [
    [0, 2, RETRO_GLITCH_PALETTE.yellow],
    [1, 2, RETRO_GLITCH_PALETTE.purple],
    [0, 3, RETRO_GLITCH_PALETTE.yellow],
    [1, 3, RETRO_GLITCH_PALETTE.purple],
    [2, 3, RETRO_GLITCH_PALETTE.yellow],
    [0, 4, RETRO_GLITCH_PALETTE.yellow],
    [1, 4, RETRO_GLITCH_PALETTE.red],
    [2, 4, RETRO_GLITCH_PALETTE.yellow],
    [1, 5, RETRO_GLITCH_PALETTE.yellow],
    [2, 5, RETRO_GLITCH_PALETTE.purple],
    [3, 6, RETRO_GLITCH_PALETTE.yellow]
  ]),
  sprite('red-imp', 'Red Imp', [
    [0, 2, RETRO_GLITCH_PALETTE.red],
    [1, 2, RETRO_GLITCH_PALETTE.yellow],
    [0, 3, RETRO_GLITCH_PALETTE.red],
    [1, 3, RETRO_GLITCH_PALETTE.red],
    [0, 4, RETRO_GLITCH_PALETTE.red],
    [1, 4, RETRO_GLITCH_PALETTE.dark],
    [0, 5, RETRO_GLITCH_PALETTE.red],
    [1, 5, RETRO_GLITCH_PALETTE.red],
    [2, 6, RETRO_GLITCH_PALETTE.red]
  ]),
  sprite('blue-stealth', 'Blue Stealth', [
    [0, 3, RETRO_GLITCH_PALETTE.blue],
    [1, 3, RETRO_GLITCH_PALETTE.blue],
    [2, 3, RETRO_GLITCH_PALETTE.blue],
    [0, 4, RETRO_GLITCH_PALETTE.red],
    [1, 4, RETRO_GLITCH_PALETTE.blue],
    [0, 5, RETRO_GLITCH_PALETTE.blue],
    [1, 5, RETRO_GLITCH_PALETTE.blue],
    [2, 5, RETRO_GLITCH_PALETTE.blue]
  ]),
  sprite('purple-saucer', 'Purple Saucer', [
    [1, 3, RETRO_GLITCH_PALETTE.purple],
    [2, 3, RETRO_GLITCH_PALETTE.purple],
    [0, 4, RETRO_GLITCH_PALETTE.purple],
    [1, 4, RETRO_GLITCH_PALETTE.cyan],
    [2, 4, RETRO_GLITCH_PALETTE.purple],
    [1, 5, RETRO_GLITCH_PALETTE.purple],
    [2, 5, RETRO_GLITCH_PALETTE.purple]
  ]),
  sprite('yellow-totem', 'Yellow Totem', [
    [0, 2, RETRO_GLITCH_PALETTE.yellow],
    [1, 2, RETRO_GLITCH_PALETTE.cyan],
    [0, 3, RETRO_GLITCH_PALETTE.yellow],
    [1, 3, RETRO_GLITCH_PALETTE.yellow],
    [0, 4, RETRO_GLITCH_PALETTE.yellow],
    [1, 4, RETRO_GLITCH_PALETTE.cyan],
    [0, 5, RETRO_GLITCH_PALETTE.yellow],
    [1, 5, RETRO_GLITCH_PALETTE.yellow],
    [2, 6, RETRO_GLITCH_PALETTE.yellow],
    [3, 6, RETRO_GLITCH_PALETTE.yellow]
  ]),
  sprite('red-arrow', 'Red Arrowhead', [
    [0, 3, RETRO_GLITCH_PALETTE.red],
    [1, 3, RETRO_GLITCH_PALETTE.yellow],
    [2, 3, RETRO_GLITCH_PALETTE.red],
    [0, 4, RETRO_GLITCH_PALETTE.red],
    [1, 4, RETRO_GLITCH_PALETTE.yellow],
    [0, 5, RETRO_GLITCH_PALETTE.red],
    [1, 5, RETRO_GLITCH_PALETTE.red]
  ]),
  sprite('purple-cyclops', 'Purple Cyclops', [
    [1, 2, RETRO_GLITCH_PALETTE.purple],
    [2, 2, RETRO_GLITCH_PALETTE.purple],
    [0, 3, RETRO_GLITCH_PALETTE.purple],
    [1, 3, RETRO_GLITCH_PALETTE.white],
    [2, 3, RETRO_GLITCH_PALETTE.purple],
    [0, 4, RETRO_GLITCH_PALETTE.purple],
    [1, 4, RETRO_GLITCH_PALETTE.dark],
    [2, 4, RETRO_GLITCH_PALETTE.purple],
    [1, 5, RETRO_GLITCH_PALETTE.purple],
    [2, 6, RETRO_GLITCH_PALETTE.purple],
    [3, 6, RETRO_GLITCH_PALETTE.purple]
  ]),
  sprite('blue-beetle', 'Blue Beetle', [
    [0, 3, RETRO_GLITCH_PALETTE.blue],
    [1, 3, RETRO_GLITCH_PALETTE.yellow],
    [2, 3, RETRO_GLITCH_PALETTE.blue],
    [0, 4, RETRO_GLITCH_PALETTE.blue],
    [1, 4, RETRO_GLITCH_PALETTE.red],
    [2, 4, RETRO_GLITCH_PALETTE.blue],
    [0, 5, RETRO_GLITCH_PALETTE.blue],
    [1, 5, RETRO_GLITCH_PALETTE.blue],
    [2, 6, RETRO_GLITCH_PALETTE.blue]
  ]),
  sprite('cyan-jelly', 'Cyan Jellyfish', [
    [0, 3, RETRO_GLITCH_PALETTE.cyan],
    [1, 3, RETRO_GLITCH_PALETTE.cyan],
    [2, 3, RETRO_GLITCH_PALETTE.cyan],
    [0, 4, RETRO_GLITCH_PALETTE.cyan],
    [1, 4, RETRO_GLITCH_PALETTE.cyan],
    [0, 5, RETRO_GLITCH_PALETTE.cyan],
    [1, 6, RETRO_GLITCH_PALETTE.cyan],
    [2, 6, RETRO_GLITCH_PALETTE.cyan]
  ]),
  sprite('red-robot', 'Red Robot', [
    [0, 2, RETRO_GLITCH_PALETTE.red],
    [1, 2, RETRO_GLITCH_PALETTE.purple],
    [0, 3, RETRO_GLITCH_PALETTE.red],
    [1, 3, RETRO_GLITCH_PALETTE.red],
    [0, 4, RETRO_GLITCH_PALETTE.red],
    [1, 4, RETRO_GLITCH_PALETTE.cyan],
    [0, 5, RETRO_GLITCH_PALETTE.red],
    [1, 5, RETRO_GLITCH_PALETTE.cyan]
  ]),
  sprite('yellow-bug', 'Yellow Bug', [
    [0, 3, RETRO_GLITCH_PALETTE.yellow],
    [1, 3, RETRO_GLITCH_PALETTE.yellow],
    [2, 3, RETRO_GLITCH_PALETTE.purple],
    [0, 4, RETRO_GLITCH_PALETTE.red],
    [1, 4, RETRO_GLITCH_PALETTE.yellow],
    [2, 4, RETRO_GLITCH_PALETTE.purple],
    [1, 5, RETRO_GLITCH_PALETTE.yellow],
    [2, 6, RETRO_GLITCH_PALETTE.purple]
  ]),
  sprite('red-spider', 'Red Spider', [
    [1, 3, RETRO_GLITCH_PALETTE.red],
    [2, 3, RETRO_GLITCH_PALETTE.red],
    [0, 4, RETRO_GLITCH_PALETTE.red],
    [1, 4, RETRO_GLITCH_PALETTE.blue],
    [2, 4, RETRO_GLITCH_PALETTE.red],
    [3, 4, RETRO_GLITCH_PALETTE.red],
    [0, 5, RETRO_GLITCH_PALETTE.red],
    [2, 5, RETRO_GLITCH_PALETTE.red]
  ]),
  sprite('cyan-ghost', 'Cyan Ghost', [
    [0, 3, RETRO_GLITCH_PALETTE.cyan],
    [1, 3, RETRO_GLITCH_PALETTE.cyan],
    [0, 4, RETRO_GLITCH_PALETTE.dark],
    [1, 4, RETRO_GLITCH_PALETTE.cyan],
    [0, 5, RETRO_GLITCH_PALETTE.cyan],
    [1, 5, RETRO_GLITCH_PALETTE.yellow],
    [0, 6, RETRO_GLITCH_PALETTE.cyan],
    [1, 6, RETRO_GLITCH_PALETTE.cyan]
  ]),
  sprite('magenta-bat', 'Magenta Bat', [
    [2, 3, RETRO_GLITCH_PALETTE.red],
    [3, 3, RETRO_GLITCH_PALETTE.red],
    [0, 4, RETRO_GLITCH_PALETTE.magenta],
    [1, 4, RETRO_GLITCH_PALETTE.yellow],
    [2, 4, RETRO_GLITCH_PALETTE.magenta],
    [0, 5, RETRO_GLITCH_PALETTE.magenta],
    [1, 5, RETRO_GLITCH_PALETTE.magenta]
  ]),
  sprite('cyan-invader', 'Cyan Invader', [
    [0, 3, RETRO_GLITCH_PALETTE.cyan],
    [1, 3, RETRO_GLITCH_PALETTE.cyan],
    [2, 3, RETRO_GLITCH_PALETTE.cyan],
    [0, 4, RETRO_GLITCH_PALETTE.yellow],
    [1, 4, RETRO_GLITCH_PALETTE.cyan],
    [2, 4, RETRO_GLITCH_PALETTE.cyan],
    [0, 5, RETRO_GLITCH_PALETTE.cyan],
    [1, 5, RETRO_GLITCH_PALETTE.cyan],
    [2, 6, RETRO_GLITCH_PALETTE.cyan],
    [3, 6, RETRO_GLITCH_PALETTE.cyan]
  ]),
  sprite('yellow-demon', 'Yellow Demon', [
    [0, 2, RETRO_GLITCH_PALETTE.cyan],
    [1, 2, RETRO_GLITCH_PALETTE.cyan],
    [0, 3, RETRO_GLITCH_PALETTE.yellow],
    [1, 3, RETRO_GLITCH_PALETTE.yellow],
    [0, 4, RETRO_GLITCH_PALETTE.red],
    [1, 4, RETRO_GLITCH_PALETTE.yellow],
    [0, 5, RETRO_GLITCH_PALETTE.red],
    [1, 5, RETRO_GLITCH_PALETTE.red]
  ]),
  sprite('red-beetle', 'Red Beetle', [
    [0, 3, RETRO_GLITCH_PALETTE.red],
    [1, 3, RETRO_GLITCH_PALETTE.yellow],
    [2, 3, RETRO_GLITCH_PALETTE.red],
    [0, 4, RETRO_GLITCH_PALETTE.red],
    [1, 4, RETRO_GLITCH_PALETTE.yellow],
    [2, 4, RETRO_GLITCH_PALETTE.red],
    [1, 5, RETRO_GLITCH_PALETTE.red],
    [2, 6, RETRO_GLITCH_PALETTE.red]
  ]),
  sprite('purple-pyramid', 'Purple Pyramid', [
    [0, 3, RETRO_GLITCH_PALETTE.purple],
    [1, 3, RETRO_GLITCH_PALETTE.yellow],
    [2, 3, RETRO_GLITCH_PALETTE.purple],
    [0, 4, RETRO_GLITCH_PALETTE.purple],
    [1, 4, RETRO_GLITCH_PALETTE.purple],
    [1, 5, RETRO_GLITCH_PALETTE.purple],
    [2, 6, RETRO_GLITCH_PALETTE.purple]
  ]),
  sprite('blue-heavy', 'Blue Heavy', [
    [0, 2, RETRO_GLITCH_PALETTE.blue],
    [1, 2, RETRO_GLITCH_PALETTE.yellow],
    [2, 2, RETRO_GLITCH_PALETTE.blue],
    [0, 3, RETRO_GLITCH_PALETTE.blue],
    [1, 3, RETRO_GLITCH_PALETTE.red],
    [2, 3, RETRO_GLITCH_PALETTE.blue],
    [0, 4, RETRO_GLITCH_PALETTE.blue],
    [1, 4, RETRO_GLITCH_PALETTE.blue],
    [2, 4, RETRO_GLITCH_PALETTE.yellow],
    [1, 5, RETRO_GLITCH_PALETTE.blue]
  ]),
  sprite('blue-blinky', 'Blue Blinky', [
    [0, 3, RETRO_GLITCH_PALETTE.blue],
    [1, 3, RETRO_GLITCH_PALETTE.blue],
    [0, 4, RETRO_GLITCH_PALETTE.white],
    [1, 4, RETRO_GLITCH_PALETTE.white],
    [0, 5, RETRO_GLITCH_PALETTE.blue],
    [1, 5, RETRO_GLITCH_PALETTE.blue],
    [0, 6, RETRO_GLITCH_PALETTE.blue],
    [1, 6, RETRO_GLITCH_PALETTE.blue]
  ]),
  sprite('cyan-moth', 'Cyan Moth', [
    [2, 3, RETRO_GLITCH_PALETTE.yellow],
    [3, 3, RETRO_GLITCH_PALETTE.yellow],
    [0, 4, RETRO_GLITCH_PALETTE.cyan],
    [1, 4, RETRO_GLITCH_PALETTE.red],
    [2, 4, RETRO_GLITCH_PALETTE.cyan],
    [0, 5, RETRO_GLITCH_PALETTE.cyan],
    [1, 5, RETRO_GLITCH_PALETTE.cyan]
  ]),
  sprite('red-crawler', 'Red Crawler', [
    [0, 3, RETRO_GLITCH_PALETTE.red],
    [1, 3, RETRO_GLITCH_PALETTE.cyan],
    [2, 3, RETRO_GLITCH_PALETTE.red],
    [0, 4, RETRO_GLITCH_PALETTE.yellow],
    [1, 4, RETRO_GLITCH_PALETTE.red],
    [2, 4, RETRO_GLITCH_PALETTE.yellow],
    [1, 5, RETRO_GLITCH_PALETTE.red],
    [2, 6, RETRO_GLITCH_PALETTE.red]
  ]),
  sprite('purple-wide-eye', 'Purple Wide-Eye', [
    [1, 3, RETRO_GLITCH_PALETTE.purple],
    [2, 3, RETRO_GLITCH_PALETTE.purple],
    [0, 4, RETRO_GLITCH_PALETTE.cyan],
    [1, 4, RETRO_GLITCH_PALETTE.yellow],
    [2, 4, RETRO_GLITCH_PALETTE.cyan],
    [0, 5, RETRO_GLITCH_PALETTE.cyan],
    [1, 5, RETRO_GLITCH_PALETTE.cyan]
  ])
]

var SPRITE_BY_ID = {}
for (var si = 0; si < RETRO_GLITCH_MONSTER_SPRITES.length; si++) {
  SPRITE_BY_ID[RETRO_GLITCH_MONSTER_SPRITES[si].id] = RETRO_GLITCH_MONSTER_SPRITES[si]
}

export function getRetroGlitchSprite(id) {
  return SPRITE_BY_ID[id] || RETRO_GLITCH_MONSTER_SPRITES[0]
}

function shuffleInPlace(arr) {
  for (var i = arr.length - 1; i > 0; i--) {
    var j = Math.floor(Math.random() * (i + 1))
    var tmp = arr[i]
    arr[i] = arr[j]
    arr[j] = tmp
  }
  return arr
}

/**
 * Weighted burst size: mostly 1, sometimes 2–3, rarely 4.
 * @returns {number} 1–4
 */
export function pickGlitchMonsterCount() {
  var r = Math.random()
  if (r < 0.52) return 1
  if (r < 0.80) return 2
  if (r < 0.94) return 3
  return 4
}

/**
 * Pick unique sprite ids for a burst (no duplicates within one burst).
 * @param {number} count
 * @returns {string[]}
 */
export function pickGlitchMonsterSpriteIds(count) {
  var pool = RETRO_GLITCH_MONSTER_SPRITES.slice()
  shuffleInPlace(pool)
  var n = Math.max(1, Math.min(count, pool.length))
  var ids = []
  for (var i = 0; i < n; i++) ids.push(pool[i].id)
  return ids
}

var MONSTER_IDLE_ANIMS = ['peek', 'walk-x', 'bob-y', 'spin']

function pickMonsterIdleAnim() {
  return MONSTER_IDLE_ANIMS[Math.floor(Math.random() * MONSTER_IDLE_ANIMS.length)]
}

function parseAnchorPct(anchor) {
  return {
    x: parseFloat(anchor && anchor.left) || 50,
    y: parseFloat(anchor && anchor.top) || 91
  }
}

function anchorDistSq(a, b) {
  var pa = parseAnchorPct(a)
  var pb = parseAnchorPct(b)
  var dx = pa.x - pb.x
  var dy = pa.y - pb.y
  return dx * dx + dy * dy
}

/**
 * Assign per-monster idle animation; closest pair fights when count >= 2.
 * @param {Array<{ id: string, anchor: { left: string, top: string } }>} monsters
 */
export function assignMonsterAnimations(monsters) {
  if (!monsters || !monsters.length) return monsters

  var used = {}
  if (monsters.length >= 2) {
    var bestI = 0
    var bestJ = 1
    var bestDist = anchorDistSq(monsters[0].anchor, monsters[1].anchor)
    for (var i = 0; i < monsters.length; i++) {
      for (var j = i + 1; j < monsters.length; j++) {
        var d = anchorDistSq(monsters[i].anchor, monsters[j].anchor)
        if (d < bestDist) {
          bestDist = d
          bestI = i
          bestJ = j
        }
      }
    }
    var leftX = parseAnchorPct(monsters[bestI].anchor).x
    var rightX = parseAnchorPct(monsters[bestJ].anchor).x
    var leftIdx = leftX <= rightX ? bestI : bestJ
    var rightIdx = leftX <= rightX ? bestJ : bestI
    monsters[leftIdx].anim = 'fight'
    monsters[leftIdx].fightDir = 1
    monsters[rightIdx].anim = 'fight'
    monsters[rightIdx].fightDir = -1
    used[leftIdx] = true
    used[rightIdx] = true
  }

  for (var k = 0; k < monsters.length; k++) {
    if (!used[k]) monsters[k].anim = pickMonsterIdleAnim()
    monsters[k].animDur = (0.65 + Math.random() * 0.55).toFixed(2)
    monsters[k].animDelay = (0.22 + Math.random() * 0.18).toFixed(2)
  }
  return monsters
}

/**
 * Full burst plan: count + unique sprites + spread anchors on lower felt.
 * @param {object} layout
 * @returns {{ count: number, monsters: Array<{ id: string, anchor: { left: string, top: string }, anim: string }> }}
 */
export function planGlitchMonsterBurst(layout) {
  var count = pickGlitchMonsterCount()
  var ids = pickGlitchMonsterSpriteIds(count)
  var anchors = retroGlitchMonsterBurstAnchors(layout, ids.length)
  var monsters = []
  for (var i = 0; i < ids.length; i++) {
    monsters.push({ id: ids[i], anchor: anchors[i] })
  }
  assignMonsterAnimations(monsters)
  return { count: ids.length, monsters: monsters }
}
