// @ts-check
const { test, expect } = require('@playwright/test')
const {
  seedLoggedIn,
  mockGameRoomRoutes,
  gotoGame
} = require('./helpers/rbacMocks')

const DECK_PRESET_PERM = 'game:experimental_deck_preset'

test.describe('RBAC experimental deck preset entry', function () {
  test('non-owner with permission sees deck preset button in top bar', async function ({ page }) {
    await seedLoggedIn(page, [DECK_PRESET_PERM])
    await mockGameRoomRoutes(page, 'room-deck-1', 'other_owner')

    await gotoGame(page, 'room-deck-1', [DECK_PRESET_PERM])
    await expect(page.getByRole('button', { name: '实验排牌' })).toBeVisible()
  })

  test('non-owner without permission does not see deck preset button', async function ({ page }) {
    await seedLoggedIn(page, [])
    await mockGameRoomRoutes(page, 'room-deck-2', 'other_owner')

    await gotoGame(page, 'room-deck-2')
    await expect(page.getByRole('button', { name: '实验排牌' })).toHaveCount(0)
  })

  test('owner without permission does not see deck preset button', async function ({ page }) {
    await seedLoggedIn(page, [])
    await mockGameRoomRoutes(page, 'room-deck-3', 'rbac_tester')

    await gotoGame(page, 'room-deck-3')
    await expect(page.getByRole('button', { name: '实验排牌' })).toHaveCount(0)
  })
})
