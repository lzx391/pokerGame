/**
 * 对局页全屏：封装各浏览器前缀；部分环境（尤其 iOS Safari）可能始终不可用。
 */

export function getFullscreenElement() {
  return (
    document.fullscreenElement
    || document.webkitFullscreenElement
    || document.mozFullScreenElement
    || document.msFullscreenElement
    || null
  )
}

export function isFullscreenActive() {
  return !!getFullscreenElement()
}

function requestFullscreenOn(el) {
  if (!el) return Promise.reject(new Error('no element'))
  var req =
    el.requestFullscreen
    || el.webkitRequestFullscreen
    || el.mozRequestFullScreen
    || el.msRequestFullscreen
  if (!req) return Promise.reject(new Error('no api'))

  function tryCall(args) {
    try {
      var p = args === undefined ? req.call(el) : req.call(el, args)
      return p && typeof p.then === 'function' ? p : Promise.resolve()
    } catch (e) {
      return Promise.reject(e)
    }
  }

  return tryCall({ navigationUI: 'hide' }).catch(function () {
    return tryCall()
  })
}

export function exitFullscreen() {
  var doc = document
  var exit =
    doc.exitFullscreen
    || doc.webkitExitFullscreen
    || doc.webkitCancelFullScreen
    || doc.mozCancelFullScreen
    || doc.msExitFullscreen
  if (!exit) return Promise.reject(new Error('no exit'))
  try {
    var p = exit.call(doc)
    return p && typeof p.then === 'function' ? p : Promise.resolve()
  } catch (e) {
    return Promise.reject(e)
  }
}

/**
 * 依次尝试：对局根节点 → documentElement（部分安卓 WebView 只认整页）。
 */
export function enterDpGameFullscreen(preferredEl) {
  var chain = [preferredEl, document.documentElement].filter(Boolean)
  var i = 0
  function tryNext(err) {
    if (i >= chain.length) return Promise.reject(err || new Error('fullscreen failed'))
    var el = chain[i++]
    return requestFullscreenOn(el).catch(function (e) {
      return tryNext(e)
    })
  }
  return tryNext()
}

export function bindFullscreenChange(handler) {
  document.addEventListener('fullscreenchange', handler)
  document.addEventListener('webkitfullscreenchange', handler)
  document.addEventListener('mozfullscreenchange', handler)
  document.addEventListener('MSFullscreenChange', handler)
}

export function unbindFullscreenChange(handler) {
  document.removeEventListener('fullscreenchange', handler)
  document.removeEventListener('webkitfullscreenchange', handler)
  document.removeEventListener('mozfullscreenchange', handler)
  document.removeEventListener('MSFullscreenChange', handler)
}
