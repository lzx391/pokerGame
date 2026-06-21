// @ts-check
const { test, expect } = require('@playwright/test')
const { ok, seedLoggedIn } = require('./helpers/rbacMocks')

const GALLERY_VIEW = 'gallery:view'

async function mockGalleryApis(page) {
  await page.route('**/dev-api/dp/gallery/letter**', function (route) {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({ letter: { content: 'Welcome to my gallery.' } }))
    })
  })
  await page.route('**/dev-api/dp/gallery/items**', function (route) {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({ items: [] }))
    })
  })
}

async function gotoGallery(page) {
  await page.goto('/#/gallery')
  await page.waitForSelector('.gallery-page', { timeout: 30000 })
}

test.describe('Gallery page smoke', function () {
  test('user without gallery:view sees forbidden state', async function ({ page }) {
    await seedLoggedIn(page, [])
    await mockGalleryApis(page)
    await gotoGallery(page)
    await expect(page.getByText('无权限查看画廊')).toBeVisible()
  })

  test('user with gallery:view loads own gallery wall after permissions warm-up', async function ({ page }) {
    await seedLoggedIn(page, [GALLERY_VIEW])
    await mockGalleryApis(page)
    var permWait = page.waitForResponse(function (res) {
      return res.url().includes('/dpUser/permissions') && res.status() === 200
    })
    await page.goto('/#/home')
    await permWait
    await page.goto('/#/gallery')
    await page.waitForSelector('.gallery-page', { timeout: 30000 })
    await expect(page.getByText('无权限查看画廊')).toHaveCount(0)
    await expect(page.locator('.dp-gallery-wall__empty')).toContainText('该玩家还没有上传画廊作品')
  })

  test('direct /gallery entry loads gallery after permissions bootstrap', async function ({ page }) {
    await seedLoggedIn(page, [GALLERY_VIEW])
    await mockGalleryApis(page)
    var permWait = page.waitForResponse(function (res) {
      return res.url().includes('/dpUser/permissions') && res.status() === 200
    })
    await page.goto('/#/gallery')
    await permWait
    await page.waitForSelector('.gallery-page', { timeout: 30000 })
    await expect(page.getByText('无权限查看画廊')).toHaveCount(0)
    await expect(page.locator('.dp-gallery-wall__empty')).toContainText('该玩家还没有上传画廊作品')
  })
})
