/**
 * Pure-function smoke test for retro8bit showdown reveal helpers.
 * Run: node scripts/verify-dpRetroShowdownReveal.js
 */
/* eslint-disable no-console */
var assert = require('assert')

function isRetroBettingStage(stage) {
  return ['preflop', 'flop', 'turn', 'river'].indexOf(stage) !== -1
}

function isRetroRevealStage(stage) {
  return ['showdown', 'settled'].indexOf(stage) !== -1
}

function resolveCardDisplayStage(actualStage, tvPending, bettingStageBeforeShowdown) {
  if (!tvPending || !isRetroRevealStage(actualStage)) return actualStage
  if (bettingStageBeforeShowdown && isRetroBettingStage(bettingStageBeforeShowdown)) {
    return bettingStageBeforeShowdown
  }
  return 'river'
}

function shouldRevealHoleCardsAtShowdown(actualStage, tvPending) {
  if (tvPending) return false
  return isRetroRevealStage(actualStage)
}

assert.strictEqual(resolveCardDisplayStage('settled', true, 'river'), 'river')
assert.strictEqual(resolveCardDisplayStage('settled', false, 'river'), 'settled')
assert.strictEqual(shouldRevealHoleCardsAtShowdown('settled', true), false)
assert.strictEqual(shouldRevealHoleCardsAtShowdown('settled', false), true)
assert.strictEqual(shouldRevealHoleCardsAtShowdown('river', false), false)

function isSettlePresentationFrozen(tvPending, actualStage) {
  return !!tvPending && isRetroRevealStage(actualStage)
}

function captureBettingEconomySnapshot(room) {
  var players = room.players || []
  var byNick = {}
  for (var i = 0; i < players.length; i++) {
    var p = players[i]
    if (!p || !p.nickname) continue
    byNick[p.nickname] = { chips: p.chips, bet: p.bet }
  }
  return { byNick: byNick, pot: room.pot, currentBetToCall: room.currentBetToCall, chipLeaderNicknames: [] }
}

function resolvePlayerEconomyDisplay(player, snapshot, frozen) {
  if (!frozen || !snapshot || !snapshot.byNick) return { chips: player.chips, bet: player.bet }
  var entry = snapshot.byNick[player.nickname]
  return entry ? { chips: entry.chips, bet: entry.bet } : { chips: player.chips, bet: player.bet }
}

var snap = captureBettingEconomySnapshot({
  pot: 120,
  currentBetToCall: 40,
  players: [{ nickname: 'A', chips: 500, bet: 40 }]
})
assert.strictEqual(isSettlePresentationFrozen(true, 'settled'), true)
assert.strictEqual(isSettlePresentationFrozen(false, 'settled'), false)
assert.strictEqual(resolvePlayerEconomyDisplay({ nickname: 'A', chips: 900, bet: 0 }, snap, true).chips, 500)

console.log('verify-dpRetroShowdownReveal: ok')
