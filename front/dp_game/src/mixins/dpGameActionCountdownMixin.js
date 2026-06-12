/**
 * 行动思考倒计时（圆环 timeLeft，总长来自房间 thinkTimeSeconds + lastActionTime）；
 * 结算准备倒计时仍硬 30s（readyTimeLeft → GameSettledPrepareBar）。
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
    actionThinkTimeTotalSec: function () {
      var s = this.$store.state.dpGame
      var v = Number(s.thinkTimeSeconds)
      if (!isFinite(v) || v < 1) return 30
      return Math.floor(v)
    },
    computeActionRemainingSec: function () {
      var s = this.$store.state.dpGame
      var total = this.actionThinkTimeTotalSec()
      var last = Number(s.lastActionTime)
      if (!isFinite(last) || last <= 0) return total
      var deadline = last + total * 1000
      var remaining = Math.ceil((deadline - Date.now()) / 1000)
      if (remaining < 0) remaining = 0
      if (remaining > total) remaining = total
      return remaining
    },
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
      var remaining = this.computeActionRemainingSec()
      if (this._actionCountdownKey === key) {
        this.timeLeft = remaining
        if (remaining > 0 && !this.actionTimer) {
          this.startCountdownTick()
        }
        if (remaining <= 0) {
          this.stopCountdown()
        }
        return
      }
      this._actionCountdownKey = key
      this.startCountdown()
    },
    startCountdownTick: function () {
      if (this.actionTimer) return
      var self = this
      this.actionTimer = setInterval(function () {
        if (self.timeLeft > 0) {
          self.timeLeft--
        } else {
          self.stopCountdown()
        }
      }, 1000)
    },
    startCountdown: function () {
      this.stopCountdown()
      this.timeLeft = this.computeActionRemainingSec()
      if (this.timeLeft <= 0) return
      this.startCountdownTick()
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
