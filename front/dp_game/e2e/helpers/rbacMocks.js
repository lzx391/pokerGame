// @ts-check
const { expect } = require('@playwright/test')

const MOCK_USER = {
  nickname: 'rbac_tester',
  password: 'test1234',
  userId: 1001,
  token: 'mock-jwt-token'
}

function ok(data) {
  return { success: true, code: 20000, data: data }
}

async function seedLoggedIn(page, permissions) {
  await page.addInitScript(function (payload) {
    localStorage.setItem('userInfo', JSON.stringify(payload.user))
    var origRemove = Storage.prototype.removeItem
    Storage.prototype.removeItem = function (key) {
      if (key === 'userInfo') return
      return origRemove.apply(this, arguments)
    }
  }, { user: MOCK_USER })

  await page.route('**/dev-api/**', function (route) {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({}))
    })
  })

  await page.route('**/dp/auth/permissions**', function (route) {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({ permissions: permissions || [] }))
    })
  })

  await page.route('**/dpUser/loginProfile**', function (route) {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({
        userId: MOCK_USER.userId,
        nickname: MOCK_USER.nickname,
        token: MOCK_USER.token
      }))
    })
  })

  await page.route('**/dp/presence/**', function (route) {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({}))
    })
  })

  await page.route('**/dp/social/**', function (route) {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({}))
    })
  })

  await page.route('**/dp/mailbox**', function (route) {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({}))
    })
  })

  await page.route('**/dp/friends/**', function (route) {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(ok({}))
    })
  })
}

async function mockGameRoomRoutes(page, roomId, ownerNickname) {
  await page.route('**/dpRoom/getNowRoom**', function (route) {
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        roomId: roomId,
        owner: ownerNickname,
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
            nickname: ownerNickname,
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

  await page.route('**/dpMusic/list**', function (route) {
    route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
  })
}

async function gotoGame(page, roomId, permissions) {
  await page.goto('/#/game/' + roomId)
  await page.waitForSelector('.dp-game-root', { timeout: 30000 })
  if (permissions && permissions.length) {
    await page.evaluate(function (perms) {
      var root = document.getElementById('app')
      var vm = root && root.__vue__
      if (vm && vm.$store) {
        vm.$store.commit('dpAuth/SET_PERMISSIONS', perms)
      }
    }, permissions)
  }
  await expect(page).toHaveURL(new RegExp('/game/' + roomId), { timeout: 15000 })
}

module.exports = {
  MOCK_USER,
  ok,
  seedLoggedIn,
  mockGameRoomRoutes,
  gotoGame
}
