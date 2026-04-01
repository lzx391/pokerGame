<template>
  <div
      class="dp-action-panel"
      :class="{ 'dp-action-panel--settled': settledPrepare }"
  >
    <!-- 结算后准备阶段（原 GameSettledPreparePanel） -->
    <template v-if="settledPrepare">
      <div class="dp-action-panel__settled-title">
        本局已结算，请准备下一局（约30秒后未准备将移到观众席）
      </div>
      <div class="dp-action-panel__settled-count">
        <div class="dp-timer-ring dp-timer-ring--sm">
          <span class="dp-timer-ring__text">{{ readyTimeLeft }}</span>
        </div>
        <span class="dp-action-panel__settled-count-label">准备倒计时</span>
      </div>
      <div class="dp-action-panel__stack-pair dp-action-panel__stack-pair--settled" aria-label="后手与本轮下注">
        <span class="dp-action-panel__stack-pill">后手 <strong>{{ myChips }}</strong></span>
        <span class="dp-action-panel__bet-pill">本轮 {{ myBet }}</span>
      </div>
      <div class="dp-action-panel__settled-actions">
        <button
            type="button"
            class="dp-btn--success dp-action-panel__settled-btn"
            :disabled="myChips < bigBlind"
            :class="{ 'dp-action-panel__settled-btn--dim': myChips < bigBlind }"
            @click="$emit('toggle-ready')"
        >
          {{ myReady ? '取消准备' : (myChips >= bigBlind ? '准备下一局' : ('积分不足大盲(' + bigBlind + ')，无法准备')) }}
        </button>
        <button
            v-if="myChips < bigBlind"
            type="button"
            class="dp-btn--rebuy dp-action-panel__settled-btn"
            @click="$emit('rebuy')"
        >
          补码到初始积分
        </button>
      </div>
    </template>

    <!-- 下注行动 -->
    <template v-else>
      <div class="dp-action-panel__turn">
        轮到你行动了（30秒超时自动弃牌）
      </div>
      <div class="dp-action-panel__stack-pair" aria-label="后手与本轮下注">
        <span class="dp-action-panel__stack-pill">后手 {{ myChips }}</span>
        <span class="dp-action-panel__bet-pill">本轮 {{ myBet }}</span>
      </div>
      <div class="dp-action-panel__meta">
        当前跟注额: {{ currentBetToCall }} | 你已下注: {{ myBet }} | 还需跟: {{ callAmount }}
      </div>
      <!-- 标准最小再加注已临时关闭，避免与 NPC 未同步时误导；恢复时取消注释即可
      <div class="dp-action-panel__raise-hint">
        合法加注至少抬到总注 <strong>{{ minTotalToRaise }}</strong>
        <span class="dp-action-panel__raise-hint-sub">（本圈最小增量 {{ lastRaiseIncrement }}）</span>
      </div>
      -->

      <div class="dp-action-panel__presets" aria-label="按底池比例快捷加注">
        <button type="button" class="dp-btn--pot-preset" @click="setPotFrac(1 / 3)">
          ⅓池
        </button>
        <button type="button" class="dp-btn--pot-preset" @click="setPotFrac(0.5)">
          ½池
        </button>
        <button type="button" class="dp-btn--pot-preset" @click="setPotFrac(0.75)">
          ¾池
        </button>
        <button type="button" class="dp-btn--pot-preset" @click="setPotFrac(1)">
          1×池
        </button>
        <button type="button" class="dp-btn--pot-preset" @click="setPotFrac(1.5)">
          1½池
        </button>
      </div>

      <div class="dp-action-panel__slider-row">
        <label class="dp-raise-slider-label" for="dp-raise-slider">本注筹码</label>
        <input
            id="dp-raise-slider"
            type="range"
            class="dp-raise-slider"
            :min="minRaise"
            :max="sliderMax"
            :step="1"
            :value="sliderValue"
            @input="onSliderInput($event.target.value)"
        >
        <span class="dp-raise-slider-value">{{ raiseAmount }}</span>
      </div>

      <div class="dp-action-panel__controls">
        <div class="dp-action-panel__row dp-action-panel__row--main">
          <div class="dp-timer-ring">
            <span class="dp-timer-ring__text">{{ timeLeft }}</span>
          </div>

          <button type="button" class="dp-btn--call" @click="$emit('call')">
            {{ callAmount > 0 ? '跟注 ' + callAmount : '过牌' }}
          </button>

          <div class="dp-raise-controls">
            <button type="button" class="dp-btn--ghost-sm" @click="bumpRaise(smallBlind)">
              +{{ smallBlind }}
            </button>
            <button type="button" class="dp-btn--ghost-sm" @click="bumpRaise(bigBlind)">
              +{{ bigBlind }}
            </button>
            <input
                type="number"
                class="dp-input--raise"
                :value="raiseAmount"
                :min="minRaise"
                :max="myChips"
                @input="onRaiseInput($event.target.value)"
            >
            <button type="button" class="dp-btn--ghost-sm" @click="bumpRaise(-bigBlind)">
              -{{ bigBlind }}
            </button>
            <button type="button" class="dp-btn--ghost-sm" @click="bumpRaise(-smallBlind)">
              -{{ smallBlind }}
            </button>
          </div>

          <button
              type="button"
              class="dp-btn--raise-go"
              :disabled="raiseAmount < minRaise"
              :style="{ opacity: raiseAmount < minRaise ? 0.45 : 1 }"
              @click="$emit('raise')"
          >
            加注
          </button>
        </div>

        <div class="dp-action-panel__row dp-action-panel__row--commit" aria-label="全下与弃牌">
          <button type="button" class="dp-btn--allin" @click="$emit('all-in')">
            All-In ({{ myChips }})
          </button>
          <button type="button" class="dp-btn--fold" @click="$emit('fold')">
            弃牌
          </button>
        </div>
      </div>
    </template>
  </div>
</template>

<script>
export default {
  name: 'GameActionPanel',
  props: {
    /** true 时显示结算后准备 UI，隐藏下注区 */
    settledPrepare: { type: Boolean, default: false },
    readyTimeLeft: { type: Number, default: 0 },
    myReady: { type: Boolean, default: false },
    timeLeft: { type: Number, default: 0 },
    currentBetToCall: { type: Number, default: 0 },
    myBet: { type: Number, default: 0 },
    callAmount: { type: Number, default: 0 },
    smallBlind: { type: Number, default: 5 },
    bigBlind: { type: Number, default: 10 },
    minRaise: { type: Number, default: 1 },
    minTotalToRaise: { type: Number, default: 0 },
    lastRaiseIncrement: { type: Number, default: 0 },
    pot: { type: Number, default: 0 },
    myChips: { type: Number, default: 0 },
    raiseAmount: { type: Number, default: 0 }
  },
  computed: {
    sliderMax() {
      return Math.max(this.minRaise, this.myChips)
    },
    sliderValue() {
      var v = Number(this.raiseAmount)
      if (isNaN(v)) return this.minRaise
      return Math.min(this.sliderMax, Math.max(this.minRaise, v))
    }
  },
  methods: {
    bumpRaise(delta) {
      var n = Number(this.raiseAmount) + delta
      this.clampEmit(n)
    },
    onRaiseInput(raw) {
      var n = parseFloat(raw)
      if (isNaN(n)) n = 0
      this.clampEmit(n)
    },
    onSliderInput(raw) {
      var n = parseInt(raw, 10)
      if (isNaN(n)) n = this.minRaise
      this.clampEmit(n)
    },
    clampEmit(n) {
      var lo = this.minRaise
      var hi = this.myChips
      if (n < lo) n = lo
      if (n > hi) n = hi
      this.$emit('update:raiseAmount', n)
    },
    setPotFrac(frac) {
      var pot = Number(this.pot) || 0
      var call = Math.max(0, Number(this.callAmount) || 0)
      var base = pot + call
      var extra = Math.round(base * frac)
      var want = call + extra
      this.clampEmit(want)
    }
  }
}
</script>
