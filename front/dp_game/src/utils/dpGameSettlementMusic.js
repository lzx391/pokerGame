/**
 * 结算阶段 BGM：单例 Audio，与对局阶段（摊牌/准备下一局）同步播放与停止。
 * 资源放在 public/music/，文件名含中文或特殊字符时用 encodeURIComponent 拼 URL。
 */
const SETTLEMENT_MUSIC_FILENAME = 'Jcdaql-Sinos_De_Natal（圣诞快乐）-Lopy..mp3'

let audio = null

function getPublicBase() {
  if (typeof process !== 'undefined' && process.env && process.env.BASE_URL) {
    var b = process.env.BASE_URL
    return b.endsWith('/') ? b : b + '/'
  }
  return '/'
}

function getSettlementMusicUrl() {
  return getPublicBase() + 'music/' + encodeURIComponent(SETTLEMENT_MUSIC_FILENAME)
}

export function playSettlementMusic() {
  try {
    if (!audio) {
      audio = new Audio()
    }
    var url = getSettlementMusicUrl()
    if (audio.src !== url) {
      audio.src = url
    }
    audio.loop = false
    var p = audio.play()
    if (p && typeof p.then === 'function') {
      p.catch(function () { /* 未交互时浏览器可能拒绝自动播放 */ })
    }
  } catch (e) { /* ignore */ }
}

export function stopSettlementMusic() {
  try {
    if (!audio) return
    audio.pause()
    audio.currentTime = 0
  } catch (e) { /* ignore */ }
}
