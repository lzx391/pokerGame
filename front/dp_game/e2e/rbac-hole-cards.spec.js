// @ts-check
const { test, expect } = require('@playwright/test')

const MOCK_USER = {
  nickname: 'rbac_tester',
  password: 'test1234',
  userId: 1001,
  token: 'mock-jwt-token'
}

function ok(data) {
  return { success: true, data: data }
}

function err(message) {
  return { success: false, message: message }
}

async function seedLoggedIn(page, permissions) {
  await page.addInitScript(function (payload) {
    localStorage.setItem('userInfo', JSON.stringify(payload.user))
    sessionStorage.removeItem('dp_admin_unlock')
  }, { user: MOCK_USER })

  await page.route('**/dp/auth/permissions', function (route) {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({ permissions: permissions || [] }))
    })
  })
}

test.describe('RBAC admin password gate', function () {
  test('wrong password shows error', async function ({ page }) {
    await seedLoggedIn(page, [])
    await page.route('**/dp/admin/verifyPassword', function (route) {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(err('密码错误'))
      })
    })

    await page.goto('/home')
    await page.getByRole('button', { name: '进入管理员模式' }).click()
    await page.getByPlaceholder('访问密码').fill('wrong-pass')
    await page.getByRole('button', { name: '确认' }).click()
    await expect(page.getByText('密码错误')).toBeVisible()
    await expect(page).toHaveURL(/\/home/)
  })

  test('correct password unlocks admin page', async function ({ page }) {
    await seedLoggedIn(page, [])
    await page.route('**/dp/admin/verifyPassword', function (route) {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(ok({ verified: true }))
      })
    })
    await page.route('**/dp/admin/roles', function (route) {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(ok([
          { id: 1, code: 'PLAYER', name: '普通用户', permissionIds: [] },
          { id: 2, code: 'ADMIN', name: '管理员', permissionIds: [] }
        ]))
      })
    })
    await page.route('**/dp/admin/permissions', function (route) {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(ok([
          { id: 10, code: 'game:hole_cards:view', name: '看牌' }
        ]))
      })
    })

    await page.goto('/home')
    await page.getByRole('button', { name: '进入管理员模式' }).click()
    await page.getByPlaceholder('访问密码').fill('correct-pass')
    await page.getByRole('button', { name: '确认' }).click()
    await expect(page).toHaveURL(/\/admin/)
    await expect(page.getByText('管理员控制台')).toBeVisible()
  })
})

test.describe('RBAC hole cards reveal button', function () {
  test('non-owner with permission sees reveal toggle in game top bar', async function ({ page }) {
    await seedLoggedIn(page, ['game:hole_cards:view'])

    await page.route('**/dpRoom/getNowRoom**', function (route) {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          roomId: 'room-rbac-1',
          owner: 'other_owner',
          players: [
            {
              nickname: 'rbac_tester',
              chips: 1000,
              bet: 0,
              holeCards: ['Ah', 'Kd'],
              leftThisHand: false,
              fold: false
            },
            {
              nickname: 'other_owner',
              chips: 1000,
              bet: 0,
              holeCards: ['2c', '3d'],
              leftThisHand: false,
              fold: false
            }
          ],
          spectators: [],
          stage: 'preflop',
          playing: true,
          communityCards: [],
          pot: 0,
          pots: [],
          actIndex: 0,
          currentBetToCall: 0,
          smallBlindChips: 5,
          bigBlindChips: 10
        })
      })
    })

    await page.route('**/dp/room/**/chat**', function (route) {
      route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
    })

    await page.route('**/dpRoom/heartbeat**', function (route) {
      route.fulfill({ status: 200, contentType: 'text/plain', body: 'ok' })
    })

    await page.route('**/dp/music/tracks**', function (route) {
      route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
    })

    await page.goto('/game/room-rbac-1')
    await expect(page.getByRole('button', { name: '看穿底牌' })).toBeVisible()
    await expect(page.getByRole('button', { name: '房主操作' })).toHaveCount(0)
  })

  test('non-owner without permission does not see reveal toggle', async function ({ page }) {
    await seedLoggedIn(page, [])

    await page.route('**/dpRoom/getNowRoom**', function (route) {
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          roomId: 'room-rbac-2',
          owner: 'other_owner',
          players: [
            {
              nickname: 'rbac_tester',
              chips: 1000,
              bet: 0,
              holeCards: ['Ah', 'Kd'],
              leftThisHand: false,
              fold: false
            }
          ],
          spectators: [],
          stage: 'preflop',
          playing: true,
          communityCards: [],
          pot: 0,
          pots: [],
          actIndex: 0,
          currentBetToCall: 0,
          smallBlindChips: 5,
          bigBlindChips: 10
        })
      })
    })

    await page.route('**/dpRoom/heartbeat**', function (route) {
      route.fulfill({ status: 200, contentType: 'text/plain', body: 'ok' })
    })

    await page.goto('/game/room-rbac-2')
    await expect(page.getByRole('button', { name: '看穿底牌' })).toHaveCount(0)
  })
})
