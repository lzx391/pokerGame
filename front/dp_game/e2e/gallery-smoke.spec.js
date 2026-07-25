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
  var letterUpdatedAt = opts.letterUpdatedAt !== undefined ? opts.letterUpdatedAt : '2024-06-15T10:30:00'

  await page.route('**/dev-api/dp/gallery/letter**', function (route) {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({
        letter: letterContent
          ? { content: letterContent, updatedAt: letterUpdatedAt }
          : null
      }))
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
  var letterUpdatedAt = opts.letterUpdatedAt !== undefined ? opts.letterUpdatedAt : '2024-05-20T08:00:00'

  await page.route('**/dev-api/dp/gallery/users/1/letter**', function (route) {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({
        letter: letterContent
          ? { content: letterContent, updatedAt: letterUpdatedAt }
          : null
      }))
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
    await expect(page.locator('.dp-gallery-letter__placard-author')).toContainText('来自')
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
    await expect(page.locator('#dp-gallery-letter-input')).toBeVisible()
    await expect(page.getByRole('button', { name: '预览访客视角' })).toBeVisible()
  })

  test('T7b: edit mode preview opens read-only letter with placard', async function ({ page }) {
    await seedLoggedIn(page, [GALLERY_VIEW])
    await mockGalleryApis(page, { letterContent: LETTER_TEXT })
    await page.goto('/#/gallery?mode=edit')
    await waitForPermissions(page)
    await page.waitForSelector('.gallery-page', { timeout: 30000 })
    await expect(page.locator('#dp-gallery-letter-input')).toBeVisible()
    await page.getByRole('button', { name: '预览访客视角' }).click()
    await expect(page.locator('.dp-gallery-envelope__sheet .dp-gallery-letter__text')).toContainText(LETTER_TEXT, { timeout: 2500 })
    await expect(page.locator('.dp-gallery-envelope__sheet .dp-gallery-letter__placard-author')).toBeVisible()
    await expect(page.locator('#dp-gallery-letter-input')).toBeVisible()
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

  test('T11: wheel scroll then click opens print reveal without flicker', async function ({ page }) {
    await seedLoggedIn(page, [GALLERY_VIEW])
    await mockGalleryApis(page, { letterContent: '', items: MOCK_GALLERY_ITEMS })
    await page.goto('/#/gallery')
    await waitForPermissions(page)
    await page.waitForSelector('.dp-gallery-wall__marquee', { timeout: 30000 })

    var viewport = page.locator('.dp-gallery-wall__viewport')
    await viewport.hover()
    await viewport.dispatchEvent('wheel', { deltaY: 240 })
    await viewport.dispatchEvent('wheel', { deltaY: 240 })

    await expect(viewport).toHaveClass(/dp-gallery-wall__viewport--manual/)

    var marquee = page.locator('.dp-gallery-wall__marquee')
    var transforms = []
    for (var i = 0; i < 6; i++) {
      transforms.push(await marquee.evaluate(function (el) {
        return window.getComputedStyle(el).transform
      }))
      await page.waitForTimeout(50)
    }
    var uniqueTransforms = transforms.filter(function (v, idx, arr) { return arr.indexOf(v) === idx })
    expect(uniqueTransforms.length).toBeLessThanOrEqual(2)

    var clickedVisible = await page.evaluate(function () {
      var viewport = document.querySelector('.dp-gallery-wall__viewport')
      if (!viewport) return false
      var vp = viewport.getBoundingClientRect()
      var frames = viewport.querySelectorAll('.gallery-frame')
      for (var i = 0; i < frames.length; i++) {
        var r = frames[i].getBoundingClientRect()
        if (r.width > 0 && r.height > 0 &&
            r.left < vp.right && r.right > vp.left &&
            r.top < vp.bottom && r.bottom > vp.top) {
          frames[i].click()
          return true
        }
      }
      return false
    })
    expect(clickedVisible).toBe(true)
    await expect(page.locator('.dp-gallery-print')).toBeVisible({ timeout: 3000 })
    await expect(viewport).toHaveClass(/dp-gallery-wall__viewport--manual/)
    await expect(viewport).toHaveClass(/dp-gallery-wall__viewport--paused/)

    await page.keyboard.press('Escape')
    await expect(page.locator('.dp-gallery-print')).toHaveCount(0, { timeout: 3000 })
    await expect(viewport).toHaveClass(/dp-gallery-wall__viewport--manual/)
  })

  test('T12: closing detail outside gallery resumes marquee', async function ({ page }) {
    await seedLoggedIn(page, [GALLERY_VIEW])
    await mockGalleryApis(page, { letterContent: '', items: MOCK_GALLERY_ITEMS })
    await page.goto('/#/gallery')
    await waitForPermissions(page)
    await page.waitForSelector('.dp-gallery-wall__marquee', { timeout: 30000 })

    var viewport = page.locator('.dp-gallery-wall__viewport')
    var marquee = page.locator('.dp-gallery-wall__marquee')
    await viewport.hover()
    await page.getByRole('button', { name: '查看作品：作品一' }).first().click()
    await expect(page.locator('.dp-gallery-print')).toBeVisible({ timeout: 3000 })

    var outsidePoint = await page.evaluate(function () {
      var viewport = document.querySelector('.dp-gallery-wall__viewport')
      if (!viewport) return { x: 8, y: 8 }
      var rect = viewport.getBoundingClientRect()
      return { x: Math.max(8, rect.left - 24), y: Math.max(8, rect.top - 24) }
    })
    await page.mouse.move(outsidePoint.x, outsidePoint.y)
    await page.locator('.dp-gallery-print__backdrop').click({ position: { x: 5, y: 5 } })
    await expect(page.locator('.dp-gallery-print')).toHaveCount(0, { timeout: 3000 })

    await expect(viewport).not.toHaveClass(/dp-gallery-wall__viewport--paused/)
    await expect(viewport).not.toHaveClass(/dp-gallery-wall__viewport--manual/)

    var playState = await marquee.evaluate(function (el) {
      return window.getComputedStyle(el).animationPlayState
    })
    expect(playState).toBe('running')

    var t1 = await marquee.evaluate(function (el) {
      return window.getComputedStyle(el).transform
    })
    await page.waitForTimeout(400)
    var t2 = await marquee.evaluate(function (el) {
      return window.getComputedStyle(el).transform
    })
    expect(t2).not.toBe(t1)
  })
})
