/**
 * 对局主区：不纵向滚动，按视口将牌桌整块等比缩小（transform: scale + 裁剪框）。
 * inner 的宽高/scale 直接写 DOM，避免与 Vue :style 合并导致测量不准。
 *
 * 注意：flex + align-items:center 下若 clip 不按主区拉满宽，width:100% 的牌桌会退化成极窄
 * min-content，scrollWidth 过小，出现「中间一条」——测量前须用主区 clientWidth 撑开 inner。
 *
 * 可用宽高须用主区**内容盒**（client 减 padding）：否则会把 padding 算进「能放牌桌」的高，
 * 缩放比偏大，flex 居中 + overflow:hidden 时易裁掉 12 点方向（平板/窄窗 ≤900px 无大屏 padding 时更明显）。
 * .dp-game-table-fit 的 padding-top 须在扣 mh/mw 时一并减掉（与 margin 同理）。
 *
 * 缩放原点须为「顶水平居中」（50% 0）：若用 top left，等比缩小后绘制贴在布局框左侧，
 * 而裁剪框按 flex 水平居中切「中间窗口」，会裁掉牌桌左侧，表现为整桌挤到左边且不对称。
 */
export default {
  data: function () {
    return {
      tableFitClipStyleObj: { width: "100%", maxWidth: "100%", overflow: "visible" },
      _tableFitRaf: null,
      _tableFitRoMain: null,
      _tableFitRoInner: null,
      _tableFitOnWin: null,
    }
  },
  watch: {
    layoutFullscreen: function () {
      var self = this
      this.scheduleTableFitUpdate()
      this.$nextTick(function () {
        self.scheduleTableFitUpdate()
        if (typeof requestAnimationFrame === "function") {
          requestAnimationFrame(function () {
            self.scheduleTableFitUpdate()
          })
        }
      })
    },
    stage: function () {
      var self = this
      this.$nextTick(function () {
        self.$nextTick(function () {
          self.scheduleTableFitUpdate()
        })
      })
    },
    "players.length": function () {
      this.scheduleTableFitUpdate()
    },
    loading: function (v) {
      if (!v) this.scheduleTableFitUpdate()
    },
  },
  mounted: function () {
    var self = this
    this._tableFitOnWin = function () {
      self.scheduleTableFitUpdate()
    }
    window.addEventListener("resize", this._tableFitOnWin)
    window.addEventListener("orientationchange", this._tableFitOnWin)
    if (window.visualViewport) {
      window.visualViewport.addEventListener("resize", this._tableFitOnWin)
    }
    this.$nextTick(function () {
      self._setupTableFitObservers()
      self.scheduleTableFitUpdate()
    })
  },
  beforeDestroy: function () {
    if (this._tableFitOnWin) {
      window.removeEventListener("resize", this._tableFitOnWin)
      window.removeEventListener("orientationchange", this._tableFitOnWin)
      if (window.visualViewport) {
        window.visualViewport.removeEventListener("resize", this._tableFitOnWin)
      }
      this._tableFitOnWin = null
    }
    if (this._tableFitRoMain) {
      try {
        this._tableFitRoMain.disconnect()
      } catch (e) {
        /* ignore */
      }
      this._tableFitRoMain = null
    }
    if (this._tableFitRoInner) {
      try {
        this._tableFitRoInner.disconnect()
      } catch (e) {
        /* ignore */
      }
      this._tableFitRoInner = null
    }
    var inner = this.$refs.tableFitInner
    if (inner && inner.style) {
      inner.style.removeProperty("transform")
      inner.style.removeProperty("transform-origin")
      inner.style.removeProperty("width")
      inner.style.removeProperty("height")
    }
  },
  methods: {
    scheduleTableFitUpdate: function () {
      var self = this
      if (this._tableFitRaf) cancelAnimationFrame(this._tableFitRaf)
      this._tableFitRaf = requestAnimationFrame(function () {
        self._tableFitRaf = null
        self.updateTableFit()
      })
    },
    _setupTableFitObservers: function () {
      var self = this
      var RO = window.ResizeObserver
      if (!RO) return
      var main = this.$refs.gameMain
      var inner = this.$refs.tableFitInner
      if (main) {
        this._tableFitRoMain = new RO(function () {
          self.scheduleTableFitUpdate()
        })
        this._tableFitRoMain.observe(main)
      }
      if (inner) {
        var table = inner.querySelector(".dp-game-table")
        if (table) {
          this._tableFitRoInner = new RO(function () {
            self.scheduleTableFitUpdate()
          })
          this._tableFitRoInner.observe(table)
        }
      }
    },
    updateTableFit: function () {
      var main = this.$refs.gameMain
      var inner = this.$refs.tableFitInner
      if (!main || !inner) return

      var st = inner.style
      st.boxSizing = "border-box"
      st.removeProperty("transform")
      st.removeProperty("transform-origin")

      var settlement = this.stage === "showdown" || this.stage === "settled"

      /*
       * 摊牌/结算：展示面更大、绝对定位座位易超出 inner 的 scrollWidth，硬缩放易左右裁切。
       * 此段不套 scale，由主区 overflow:auto 平移查看（与旧版「主区可滚」一致）。
       */
      if (settlement) {
        st.removeProperty("width")
        st.removeProperty("height")
        this.tableFitClipStyleObj = {
          width: "100%",
          maxWidth: "100%",
          overflow: "visible",
        }
        return
      }

      var cs = window.getComputedStyle(main)
      var pl = parseFloat(cs.paddingLeft) || 0
      var pr = parseFloat(cs.paddingRight) || 0
      var pt = parseFloat(cs.paddingTop) || 0
      var pb = parseFloat(cs.paddingBottom) || 0
      var mw = main.clientWidth - pl - pr
      var mh = main.clientHeight - pt - pb
      /* dp-game-shell：.dp-game-table-fit 外 margin-top 下移桌面时必须扣掉，否则会按过高 mh 缩放再裁切 */
      var outer = inner.parentElement
      if (outer && outer.classList && outer.classList.contains("dp-game-table-fit")) {
        var fm = window.getComputedStyle(outer)
        mw -= (parseFloat(fm.marginLeft) || 0) + (parseFloat(fm.marginRight) || 0)
        mh -= (parseFloat(fm.marginTop) || 0) + (parseFloat(fm.marginBottom) || 0)
        mw -= (parseFloat(fm.paddingLeft) || 0) + (parseFloat(fm.paddingRight) || 0)
        mh -= (parseFloat(fm.paddingTop) || 0) + (parseFloat(fm.paddingBottom) || 0)
      }
      if (mw < 4 || mh < 4) return

      /* 先按主区宽度撑开，再量自然宽高（避免 width:100% 在窄父级下塌成一条） */
      st.width = mw + "px"
      st.removeProperty("height")
      void inner.offsetHeight

      var nw = inner.scrollWidth
      var nh = inner.scrollHeight
      var layoutEl = inner.querySelector(".dp-game-table__layout")
      if (layoutEl) {
        var lw = layoutEl.scrollWidth
        var lh = layoutEl.scrollHeight
        if (lw > nw) nw = lw
        if (lh > nh) nh = lh
      }
      if (nw < 4 || nh < 4) return

      var pad = 16
      var sx = (mw - pad) / nw
      var sy = (mh - pad) / nh
      var s = sx < sy ? sx : sy
      if (s > 1) s = 1
      if (!isFinite(s) || s <= 0) s = 1

      if (s >= 0.999) {
        st.removeProperty("width")
        st.removeProperty("height")
        this.tableFitClipStyleObj = {
          width: "100%",
          maxWidth: "100%",
          overflow: "visible",
        }
        return
      }

      /* 竖向余量：上牌 translate(-50%,-50%) + clip 舍入 */
      var wClip = Math.max(1, Math.ceil(nw * s) + 2)
      var hClip = Math.max(1, Math.ceil(nh * s) + 44)
      st.transform = "scale(" + s + ")"
      st.transformOrigin = "50% 0"
      st.width = nw + "px"
      st.height = nh + "px"
      this.tableFitClipStyleObj = {
        width: wClip + "px",
        height: hClip + "px",
        overflow: "hidden",
        maxWidth: "100%",
      }
    },
  },
}
