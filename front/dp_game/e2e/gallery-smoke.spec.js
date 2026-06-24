// @ts-check
const { test, expect } = require('@playwright/test')
const { ok, seedLoggedIn } = require('./helpers/rbacMocks')

const GALLERY_VIEW = 'gallery:view'
const LETTER_TEXT = '你好世界'

var MOCK_GALLERY_ITEMS = [
  { id: 1, caption: '作品一', previewUrl: '/dp/gallery/files/1_sm.webp', imageUrl: '/dp/gallery/files/1.webp' },
  { id: 2, caption: '作品二', previewUrl: '/dp/gallery/files/2_sm.webp', imageUrl: '/dp/gallery/files/2.webp' },
  { id: 3, caption: '作品三', previewUrl: '/dp/gallery/files/3_sm.webp', imageUrl: '/dp/gallery/files/3.webp' },
  { id: 4, caption: '作品四', previewUrl: '/dp/gallery/files/4_sm.webp', imageUrl: '/dp/gallery/files/4.webp' }
]

async function mockGalleryApis(page, options) {
  var opts = options || {}
  var letterContent = opts.letterContent !== undefined ? opts.letterContent : LETTER_TEXT
  var items = opts.items !== undefined ? opts.items : []

  await page.route('**/dev-api/dp/gallery/letter**', function (route) {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({ letter: { content: letterContent } }))
    })
  })
  await page.route('**/dev-api/dp/gallery/items**', function (route) {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({ items: items }))
    })
  })
}

async function mockOtherUserGalleryApis(page, options) {
  var opts = options || {}
  var letterContent = opts.letterContent !== undefined ? opts.letterContent : ''

  await page.route('**/dev-api/dp/gallery/users/1/letter**', function (route) {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({ letter: { content: letterContent } }))
    })
  })
  await page.route('**/dev-api/dp/gallery/users/1/items**', function (route) {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({ items: [] }))
    })
  })
  await page.route('**/dev-api/dp/users/lookup**', function (route) {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({ nickname: 'guest_player' }))
    })
  })
}

async function gotoGallery(page) {
  await page.goto('/#/gallery')
  await page.waitForSelector('.gallery-page', { timeout: 30000 })
}

async function waitForPermissions(page) {
  var permWait = page.waitForResponse(function (res) {
    return res.url().includes('/dpUser/permissions') && res.status() === 200
  })
  await permWait
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
    await page.goto('/#/gallery')
    await waitForPermissions(page)
    await page.waitForSelector('.gallery-page', { timeout: 30000 })
    await expect(page.getByText('无权限查看画廊')).toHaveCount(0)
    await expect(page.locator('.dp-gallery-wall__empty')).toContainText('该玩家还没有上传画廊作品')
  })

  test('T1: envelope hint visible when letter has content', async function ({ page }) {
    await seedLoggedIn(page, [GALLERY_VIEW])
    await mockGalleryApis(page, { letterContent: LETTER_TEXT })
    await page.goto('/#/gallery')
    await waitForPermissions(page)
    await page.waitForSelector('.gallery-page', { timeout: 30000 })
    await expect(page.getByRole('button', { name: '打开介绍信' })).toBeVisible()
    await expect(page.getByText('一封信')).toBeVisible()
  })

  test('T2: clicking envelope shows letter body within 2.5s', async function ({ page }) {
    await seedLoggedIn(page, [GALLERY_VIEW])
    await mockGalleryApis(page, { letterContent: LETTER_TEXT })
    await page.goto('/#/gallery')
    await waitForPermissions(page)
    await page.waitForSelector('.gallery-page', { timeout: 30000 })
    await page.getByRole('button', { name: '打开介绍信' }).click()
    await expect(page.locator('.dp-gallery-letter__text')).toContainText(LETTER_TEXT, { timeout: 2500 })
  })

  test('T3: Escape closes letter and returns sealed envelope', async function ({ page }) {
    await seedLoggedIn(page, [GALLERY_VIEW])
    await mockGalleryApis(page, { letterContent: LETTER_TEXT })
    await page.goto('/#/gallery')
    await waitForPermissions(page)
    await page.waitForSelector('.gallery-page', { timeout: 30000 })
    await page.getByRole('button', { name: '打开介绍信' }).click()
    await expect(page.locator('.dp-gallery-letter__text')).toContainText(LETTER_TEXT, { timeout: 2500 })
    await page.keyboard.press('Escape')
    await expect(page.locator('.dp-gallery-letter__text')).toHaveCount(0, { timeout: 2000 })
    await expect(page.getByRole('button', { name: '打开介绍信' })).toBeVisible()
  })

  test('T3b: backdrop click closes letter and returns sealed envelope', async function ({ page }) {
    await seedLoggedIn(page, [GALLERY_VIEW])
    await mockGalleryApis(page, { letterContent: LETTER_TEXT })
    await page.goto('/#/gallery')
    await waitForPermissions(page)
    await page.waitForSelector('.gallery-page', { timeout: 30000 })
    await page.getByRole('button', { name: '打开介绍信' }).click()
    await expect(page.locator('.dp-gallery-letter__text')).toContainText(LETTER_TEXT, { timeout: 2500 })
    await page.locator('.dp-gallery-envelope__backdrop').click({ position: { x: 8, y: 8 } })
    await expect(page.locator('.dp-gallery-letter__text')).toHaveCount(0, { timeout: 2000 })
    await expect(page.getByRole('button', { name: '打开介绍信' })).toBeVisible()
  })

  test('T4: other user empty letter hides envelope', async function ({ page }) {
    await seedLoggedIn(page, [GALLERY_VIEW])
    await mockOtherUserGalleryApis(page, { letterContent: '' })
    await page.goto('/#/gallery/users/1')
    await waitForPermissions(page)
    await page.waitForSelector('.gallery-page', { timeout: 30000 })
    await expect(page.getByRole('button', { name: '打开介绍信' })).toHaveCount(0)
    await expect(page.getByText('一封信')).toHaveCount(0)
  })

  test('T5: reduced motion shows letter body immediately', async function ({ browser }) {
    var context = await browser.newContext({ reducedMotion: 'reduce' })
    var page = await context.newPage()
    try {
      await seedLoggedIn(page, [GALLERY_VIEW])
      await mockGalleryApis(page, { letterContent: LETTER_TEXT })
      await page.goto('/#/gallery')
      await waitForPermissions(page)
      await page.waitForSelector('.gallery-page', { timeout: 30000 })
      var start = Date.now()
      await page.getByRole('button', { name: '打开介绍信' }).click()
      await expect(page.locator('.dp-gallery-letter__text')).toContainText(LETTER_TEXT, { timeout: 500 })
      expect(Date.now() - start).toBeLessThan(800)
    } finally {
      await context.close()
    }
  })

  test('T6: read-only gallery with items enables conveyor marquee', async function ({ page }) {
    await seedLoggedIn(page, [GALLERY_VIEW])
    await mockGalleryApis(page, { letterContent: '', items: MOCK_GALLERY_ITEMS })
    await page.goto('/#/gallery')
    await waitForPermissions(page)
    await page.waitForSelector('.dp-gallery-wall__marquee', { timeout: 30000 })
    await expect(page.locator('.dp-gallery-wall__marquee')).toHaveCount(1)
    await expect(page.locator('.dp-gallery-wall__track--marquee')).toHaveCount(2)
  })

  test('T7: edit mode disables conveyor marquee', async function ({ page }) {
    await seedLoggedIn(page, [GALLERY_VIEW])
    await mockGalleryApis(page, { letterContent: '', items: MOCK_GALLERY_ITEMS })
    await page.goto('/#/gallery?mode=edit')
    await waitForPermissions(page)
    await page.waitForSelector('.gallery-page', { timeout: 30000 })
    await expect(page.locator('.dp-gallery-wall__marquee')).toHaveCount(0)
    await expect(page.locator('.dp-gallery-wall__track')).toHaveCount(1)
  })

  test('T8: reduced motion disables conveyor marquee', async function ({ browser }) {
    var context = await browser.newContext({ reducedMotion: 'reduce' })
    var page = await context.newPage()
    try {
      await seedLoggedIn(page, [GALLERY_VIEW])
      await mockGalleryApis(page, { letterContent: '', items: MOCK_GALLERY_ITEMS })
      await page.goto('/#/gallery')
      await waitForPermissions(page)
      await page.waitForSelector('.gallery-page', { timeout: 30000 })
      await expect(page.locator('.dp-gallery-wall__marquee')).toHaveCount(0)
      await expect(page.locator('.dp-gallery-wall__track')).toHaveCount(1)
    } finally {
      await context.close()
    }
  })

  test('T9: clicking a piece opens print reveal and pauses marquee', async function ({ page }) {
    await seedLoggedIn(page, [GALLERY_VIEW])
    await mockGalleryApis(page, { letterContent: '', items: MOCK_GALLERY_ITEMS })
    await page.goto('/#/gallery')
    await waitForPermissions(page)
    await page.waitForSelector('.dp-gallery-wall__marquee', { timeout: 30000 })
    await page.locator('.dp-gallery-wall__viewport').hover()
    await page.getByRole('button', { name: '查看作品：作品一' }).first().click()
    await expect(page.locator('.dp-gallery-print')).toBeVisible({ timeout: 3000 })
    var paused = await page.locator('.dp-gallery-wall__viewport--paused').count()
    expect(paused).toBe(1)
  })

  test('T10: wheel scroll while hovered moves marquee manually', async function ({ page }) {
    await seedLoggedIn(page, [GALLERY_VIEW])
    await mockGalleryApis(page, { letterContent: '', items: MOCK_GALLERY_ITEMS })
    await page.goto('/#/gallery')
    await waitForPermissions(page)
    await page.waitForSelector('.dp-gallery-wall__marquee', { timeout: 30000 })

    var viewport = page.locator('.dp-gallery-wall__viewport')
    await viewport.hover()

    var before = await page.locator('.dp-gallery-wall__marquee').evaluate(function (el) {
      var matrix = new DOMMatrixReadOnly(window.getComputedStyle(el).transform)
      return matrix.m41
    })

    await viewport.dispatchEvent('wheel', { deltaY: 120 })

    var after = await page.locator('.dp-gallery-wall__marquee').evaluate(function (el) {
      var matrix = new DOMMatrixReadOnly(window.getComputedStyle(el).transform)
      return matrix.m41
    })

    expect(after).not.toBe(before)
    await expect(viewport).toHaveClass(/dp-gallery-wall__viewport--manual/)
  })
})
