// @ts-check
const { test, expect } = require('@playwright/test')
const {
  ok,
  seedLoggedIn: seedLoggedInBase,
  mockGameRoomRoutes,
  gotoGame
} = require('./helpers/rbacMocks')

function err(message) {
  return { success: false, message: message }
}

async function seedLoggedIn(page, permissions) {
  await seedLoggedInBase(page, permissions)
  await page.addInitScript(function () {
    sessionStorage.removeItem('dp_admin_unlock')
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

    await page.goto('/#/home')
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

    await page.goto('/#/home')
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
    await mockGameRoomRoutes(page, 'room-rbac-1', 'other_owner')

    await gotoGame(page, 'room-rbac-1', ['game:hole_cards:view'])
    await expect(page.getByRole('button', { name: '看穿底牌' })).toBeVisible()
    await expect(page.getByRole('button', { name: '房主操作' })).toHaveCount(0)
  })

  test('non-owner without permission does not see reveal toggle', async function ({ page }) {
    await seedLoggedIn(page, [])
    await mockGameRoomRoutes(page, 'room-rbac-2', 'other_owner')

    await gotoGame(page, 'room-rbac-2')
    await expect(page.getByRole('button', { name: '看穿底牌' })).toHaveCount(0)
  })
})
