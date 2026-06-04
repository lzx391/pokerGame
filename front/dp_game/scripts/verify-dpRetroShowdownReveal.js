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

console.log('verify-dpRetroShowdownReveal: ok')
