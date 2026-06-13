/**
 * Overlay 层级常量与动态递增（同层多次打开时 +10）。
 * 与 styles/dp-overlay-layers.css 中 --dp-z-* 保持一致。
 */

export var DP_Z_LAYER = {
  friendList: 8000,
  profileDialog: 8500,
  playerSheet: 9000,
  achievement: 9100,
  handHistory: 9200,
  deckPreset: 9300,
  decisionTrace: 9310
}

var stackBump = 0

/** @param {'friendList'|'profileDialog'|'playerSheet'|'achievement'|'handHistory'|'deckPreset'|'decisionTrace'} layer */
export function dpLayerZIndex(layer) {
  var base = DP_Z_LAYER[layer]
  if (base == null) base = DP_Z_LAYER.playerSheet
  return base
}

/** 打开叠层弹窗时调用，保证高于同层上一次打开 */
export function dpNextZIndex(layer) {
  stackBump += 10
  if (stackBump > 90) stackBump = 10
  return dpLayerZIndex(layer) + stackBump
}

export function dpResetZIndexStack() {
  stackBump = 0
}
