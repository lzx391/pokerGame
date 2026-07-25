// @ts-check
const { test, expect } = require('@playwright/test')
const {
  seedLoggedIn,
  mockGameRoomRoutes,
  gotoGame
} = require('./helpers/rbacMocks')

const DECK_PRESET_PERM = 'game:experimental_deck_preset'
const NPC_DECISION_TRACE_PERM = 'game:npc_decision_trace'

test.describe('RBAC NPC decision trace entry', function () {
  test('non-owner with npc decision trace permission sees decision trace button', async function ({ page }) {
    await seedLoggedIn(page, [NPC_DECISION_TRACE_PERM])
    await mockGameRoomRoutes(page, 'room-trace-1', 'other_owner')

    await gotoGame(page, 'room-trace-1', [NPC_DECISION_TRACE_PERM])
    await expect(page.getByRole('button', { name: '决策追踪' })).toBeVisible()
  })

  test('non-owner without permission does not see decision trace button', async function ({ page }) {
    await seedLoggedIn(page, [])
    await mockGameRoomRoutes(page, 'room-trace-2', 'other_owner')

    await gotoGame(page, 'room-trace-2')
    await expect(page.getByRole('button', { name: '决策追踪' })).toHaveCount(0)
  })

  test('deck preset and decision trace permissions show respective buttons', async function ({ page }) {
    await seedLoggedIn(page, [DECK_PRESET_PERM, NPC_DECISION_TRACE_PERM])
    await mockGameRoomRoutes(page, 'room-trace-3', 'other_owner')

    await gotoGame(page, 'room-trace-3', [DECK_PRESET_PERM, NPC_DECISION_TRACE_PERM])
    await expect(page.getByRole('button', { name: '实验排牌' })).toBeVisible()
    await expect(page.getByRole('button', { name: '决策追踪' })).toBeVisible()
  })
})
