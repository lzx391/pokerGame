/**
 * 对局根全屏、伪全屏与 Element UI 弹层挪入 gameRoot（供 game.vue 使用）。
 */
export default {
  data: function () {
    return {
      isFullscreen: false,
      pseudoFullscreen: false,
      _dpMessageFullscreenWrapDone: false
    }
  },
  computed: {
    dpFullscreenApiSupported: function () {
      var d = document
      return !!(
        d.fullscreenEnabled ||
        d.webkitFullscreenEnabled ||
        d.mozFullScreenEnabled ||
        d.msFullscreenEnabled
      )
    },
    layoutFullscreen: function () {
      return this.isFullscreen || this.pseudoFullscreen
    }
  },
  mounted: function () {
    this._dpFsChange = this.syncDpFullscreenState.bind(this)
    document.addEventListener('fullscreenchange', this._dpFsChange)
    document.addEventListener('webkitfullscreenchange', this._dpFsChange)
    this.syncDpFullscreenState()
    this.wrapDpMessageForFullscreenOverlays()
    var self = this
    this.$nextTick(function () {
      self.tryEnterDpFullscreen()
      self.scheduleReparentElementUiLayersIntoFullscreenRoot()
    })
  },
  beforeDestroy: function () {
    if (this._dpFsChange) {
      document.removeEventListener('fullscreenchange', this._dpFsChange)
      document.removeEventListener('webkitfullscreenchange', this._dpFsChange)
      this._dpFsChange = null
    }
    this.exitDpFullscreenIfActive()
    this.setPseudoFullscreen(false)
  },
  methods: {
    syncDpFullscreenState: function () {
      var root = this.$refs.gameRoot
      var active = document.fullscreenElement || document.webkitFullscreenElement
      this.isFullscreen = !!(root && active === root)
      if (this.isFullscreen && this.pseudoFullscreen) {
        this.setPseudoFullscreen(false)
      }
      if (this.isFullscreen) {
        this.scheduleReparentElementUiLayersIntoFullscreenRoot()
      }
    },
    exitDpFullscreenIfActive: function () {
      var root = this.$refs.gameRoot
      var active = document.fullscreenElement || document.webkitFullscreenElement
      if (!this.isFullscreen && !(root && active === root)) return
      if (!active || (root && active !== root)) return
      var swallow = function (p) {
        if (p && typeof p.then === 'function') {
          p.catch(function () {})
        }
      }
      try {
        if (document.exitFullscreen) swallow(document.exitFullscreen())
        else if (document.webkitExitFullscreen) swallow(document.webkitExitFullscreen())
      } catch (e) { /* ignore */ }
    },
    setPseudoFullscreen: function (on) {
      this.pseudoFullscreen = !!on
      try {
        document.body.style.overflow = ''
      } catch (e) { /* ignore */ }
    },
    tryEnterDpFullscreen: function () {
      var root = this.$refs.gameRoot
      if (!root || this.layoutFullscreen) return
      var self = this
      if (!this.dpFullscreenApiSupported) {
        this.setPseudoFullscreen(true)
        return
      }
      var req =
        root.requestFullscreen ||
        root.webkitRequestFullscreen ||
        root.mozRequestFullScreen ||
        root.msRequestFullscreen
      if (!req) {
        this.setPseudoFullscreen(true)
        return
      }
      Promise.resolve(req.call(root)).catch(function (e) {
        console.error('进入全屏失败', e)
        self.setPseudoFullscreen(true)
      })
    },
    toggleDpFullscreen: function () {
      var root = this.$refs.gameRoot
      if (!root) return
      var self = this
      if (this.layoutFullscreen) {
        if (this.isFullscreen) {
          Promise.resolve(
            document.exitFullscreen
              ? document.exitFullscreen()
              : document.webkitExitFullscreen
                ? document.webkitExitFullscreen()
                : null
          ).catch(function (e) {
            console.error('退出全屏失败', e)
            self.$message.warning('无法退出全屏')
          })
        }
        if (this.pseudoFullscreen) this.setPseudoFullscreen(false)
        return
      }
      this.tryEnterDpFullscreen()
    },
    reparentElementUiLayersIntoFullscreenRoot: function () {
      var root = this.$refs.gameRoot
      if (!root || !this.isFullscreen) return
      var moveIfOutside = function (node) {
        if (!node || !node.parentNode || root.contains(node)) return
        root.appendChild(node)
      }
      var moveAll = function (selector) {
        var nodes = document.querySelectorAll(selector)
        var i = 0
        for (i = 0; i < nodes.length; i++) {
          moveIfOutside(nodes[i])
        }
      }
      var w = 0
      moveAll('.el-message-box__wrapper')
      moveAll('.el-dialog__wrapper')
      moveAll('.el-drawer__wrapper')
      /* el-select / 部分下拉挂在 body，全屏时必须在 gameRoot 内才能看见 */
      moveAll('.el-select-dropdown')
      var modals = document.getElementsByClassName('v-modal')
      for (w = 0; w < modals.length; w++) {
        moveIfOutside(modals[w])
      }
      var msgs = document.querySelectorAll('.el-message')
      for (w = 0; w < msgs.length; w++) {
        moveIfOutside(msgs[w])
      }
    },
    scheduleReparentElementUiLayersIntoFullscreenRoot: function () {
      var self = this
      if (!self.isFullscreen) return
      var run = function () {
        self.reparentElementUiLayersIntoFullscreenRoot()
      }
      self.$nextTick(function () {
        run()
        if (typeof requestAnimationFrame === 'function') {
          requestAnimationFrame(run)
        }
        setTimeout(run, 0)
        setTimeout(run, 50)
      })
    },
    wrapDpMessageForFullscreenOverlays: function () {
      if (this._dpMessageFullscreenWrapDone) return
      var raw = this.$message
      if (!raw || typeof raw !== 'function') return
      var self = this
      var wrapped = function () {
        var ret = raw.apply(raw, arguments)
        self.scheduleReparentElementUiLayersIntoFullscreenRoot()
        return ret
      }
      var k
      for (k in raw) {
        if (!Object.prototype.hasOwnProperty.call(raw, k) || typeof raw[k] !== 'function') continue
        wrapped[k] = (function (methodName) {
          return function () {
            var ret = raw[methodName].apply(raw, arguments)
            self.scheduleReparentElementUiLayersIntoFullscreenRoot()
            return ret
          }
        })(k)
      }
      this.$message = wrapped
      this._dpMessageFullscreenWrapDone = true
    },
    dpConfirm: function (text, title, options) {
      var o = Object.assign({
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
        closeOnClickModal: false
      }, options || {})
      var p = this.$confirm(text, title || '请确认', o)
      this.scheduleReparentElementUiLayersIntoFullscreenRoot()
      return p
    }
  }
}
