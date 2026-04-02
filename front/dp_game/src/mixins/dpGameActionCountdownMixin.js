/**
 * 行动 30s 与结算准备倒计时（与 GameActionPanel / 桌面圆环共用 timeLeft）。
 */
export default {
  data: function () {
    return {
      actionTimer: null,
      _actionCountdownKey: null,
      timeLeft: 30,
      readyTimer: null,
      readyTimeLeft: 30
    }
  },
  methods: {
    actionCountdownShouldRun: function () {
      var s = this.$store.state.dpGame
      if (!s.playing) return false
      var st = s.stage
      if (st === 'showdown' || st === 'settled') return false
      var i = s.actIndex
      var list = s.players
      if (i < 0 || !list || i >= list.length) return false
      var p = list[i]
      if (!p || p.leftThisHand || p.fold) return false
      return true
    },
    actionCountdownSessionKey: function () {
      var s = this.$store.state.dpGame
      return String(s.playing) + '|' + s.stage + '|' + s.actIndex + '|' + s.currentHandSeed
    },
    syncActionCountdown: function () {
      if (!this.actionCountdownShouldRun()) {
        this.stopCountdown()
        this._actionCountdownKey = null
        return
      }
      var key = this.actionCountdownSessionKey()
      if (this._actionCountdownKey === key) return
      this._actionCountdownKey = key
      this.startCountdown()
    },
    startCountdown: function () {
      this.stopCountdown()
      this.timeLeft = 30
      var self = this
      this.actionTimer = setInterval(function () {
        if (self.timeLeft > 0) {
          self.timeLeft--
        } else {
          self.stopCountdown()
        }
      }, 1000)
    },
    stopCountdown: function () {
      if (this.actionTimer) {
        clearInterval(this.actionTimer)
        this.actionTimer = null
      }
    },
    startReadyCountdown: function () {
      this.stopReadyCountdown()
      this.readyTimeLeft = 30
      var self = this
      this.readyTimer = setInterval(function () {
        if (self.readyTimeLeft > 0) {
          self.readyTimeLeft--
        } else {
          self.stopReadyCountdown()
        }
      }, 1000)
    },
    stopReadyCountdown: function () {
      if (this.readyTimer) {
        clearInterval(this.readyTimer)
        this.readyTimer = null
      }
    }
  }
}
