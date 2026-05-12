/**
 * 对局页布局档位：phone / tablet / desktop（供根节点 data-dp-layout-tier 与样式变量联动）。
 * 断点与 front/dp_game/src/styles/dp-game-layout-tiers.css 注释保持一致。
 */
function computeDpLayoutTier() {
  if (typeof window === 'undefined') return 'desktop'
  var w = window.innerWidth
  var h = window.innerHeight
  var shortSide = Math.min(w, h)
  var longSide = Math.max(w, h)
  if (w <= 640) return 'phone'
  /* 横屏手机：宽度可能 >640，以短边识别 */
  if (shortSide <= 480 && longSide <= 1100) return 'phone'
  if (w <= 1024) return 'tablet'
  return 'desktop'
}

/** 与 CSS media orientation / data-dp-orientation 对齐，供竖屏提示与样式分叉 */
function computeDpLayoutOrientation() {
  if (typeof window === 'undefined') return 'landscape'
  return window.innerHeight > window.innerWidth ? 'portrait' : 'landscape'
}

export default {
  data: function () {
    return {
      layoutTier: computeDpLayoutTier(),
      layoutOrientation: computeDpLayoutOrientation(),
      _dpTierOnResize: null,
    }
  },
  mounted: function () {
    var self = this
    this._dpTierOnResize = function () {
      self.layoutTier = computeDpLayoutTier()
      self.layoutOrientation = computeDpLayoutOrientation()
    }
    window.addEventListener('resize', this._dpTierOnResize)
    window.addEventListener('orientationchange', this._dpTierOnResize)
    if (window.visualViewport) {
      window.visualViewport.addEventListener('resize', this._dpTierOnResize)
    }
    this.layoutTier = computeDpLayoutTier()
    this.layoutOrientation = computeDpLayoutOrientation()
    this._dpTierScrollLock()
    this.$nextTick(function () {
      if (typeof self.scheduleTableFitUpdate === 'function') {
        self.scheduleTableFitUpdate()
      }
    })
  },
  beforeDestroy: function () {
    if (this._dpTierOnResize) {
      window.removeEventListener('resize', this._dpTierOnResize)
      window.removeEventListener('orientationchange', this._dpTierOnResize)
      if (window.visualViewport) {
        window.visualViewport.removeEventListener('resize', this._dpTierOnResize)
      }
      this._dpTierOnResize = null
    }
    this._dpTierScrollUnlock()
  },
  watch: {
    layoutTier: function () {
      var self = this
      this.$nextTick(function () {
        if (typeof self.scheduleTableFitUpdate === 'function') {
          self.scheduleTableFitUpdate()
        }
      })
    },
    layoutOrientation: function () {
      var self = this
      this.$nextTick(function () {
        if (typeof self.scheduleTableFitUpdate === 'function') {
          self.scheduleTableFitUpdate()
        }
      })
    },
  },
  methods: {
    _dpTierScrollLock: function () {
      try {
        document.documentElement.classList.add('dp-game-no-scroll')
        document.body.classList.add('dp-game-no-scroll')
      } catch (e) {
        /* ignore */
      }
    },
    _dpTierScrollUnlock: function () {
      try {
        document.documentElement.classList.remove('dp-game-no-scroll')
        document.body.classList.remove('dp-game-no-scroll')
      } catch (e) {
        /* ignore */
      }
    },
  },
}
