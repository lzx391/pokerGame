<template>
  <div ref="guideActionPanelHost" class="dp-action-panel">
      <div class="dp-action-panel__turn">
        轮到你行动了（30秒超时自动盖牌）
      </div>
      <div class="dp-action-panel__raise-hint">
        <!-- 合法加投至少抬到总投入 <strong>{{ minTotalToRaise }}</strong> -->
        <span class="dp-action-panel__raise-hint-sub">（本圈最小增量 {{ lastRaiseIncrement }}）</span>
      </div>

      <div ref="guidePotPresets" class="dp-action-panel__presets" aria-label="按小鱼干池比例快捷加投">
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
        <button
            type="button"
            class="dp-btn--pot-preset dp-btn--mini-raise"
            :disabled="myChips < 1"
            :title="'本笔至少 ' + minRaise + '（总投入到 ' + minTotalToRaise + '）'"
            @click="applyMiniRaise"
        >
          最小加投 {{ minRaise }}
        </button>
      </div>

      <div ref="guideRaiseSlider" class="dp-action-panel__slider-row">
        <label class="dp-raise-slider-label" for="dp-raise-slider">本笔投入</label>
        <input
            id="dp-raise-slider"
            type="range"
            class="dp-raise-slider"
            :min="minRaise"
            :max="sliderMax"
            :step="snapChipUnit"
            :value="sliderValue"
            @input="onSliderInput($event.target.value)"
        >
        <span class="dp-raise-slider-value">{{ raiseAmount }}</span>
      </div>

      <div class="dp-action-panel__controls">
        <div class="dp-action-panel__row dp-action-panel__row--main">
          <div ref="guideActionTimer" class="dp-timer-ring">
            <span class="dp-timer-ring__text">{{ timeLeft }}</span>
          </div>

          <button ref="guideCall" type="button" class="dp-btn--call" @click="$emit('call')">
            {{ callAmount > 0 ? '跟投 ' + callAmount : '观望' }}
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
                :step="snapChipUnit"
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
              ref="guideRaise"
              type="button"
              class="dp-btn--raise-go"
              :disabled="raiseAmount < minRaise"
              :style="{ opacity: raiseAmount < minRaise ? 0.45 : 1 }"
              @click="$emit('raise')"
          >
            加投
          </button>
        </div>

        <div class="dp-action-panel__row dp-action-panel__row--commit" aria-label="全投与盖牌">
          <button ref="guideAllin" type="button" class="dp-btn--allin" @click="$emit('all-in')">
            全投 ({{ myChips }})
          </button>
          <button ref="guideFold" type="button" class="dp-btn--fold" @click="$emit('fold')">
            盖牌
          </button>
        </div>
      </div>
  </div>
</template>

<script>
export default {
  name: 'GameActionPanel',
  props: {
    timeLeft: { type: Number, default: 0 },
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
    /** 对齐用的小底分单位：prop 无效时用大底分/2，避免退化成 1 导致 23 等无法吸到 5 的倍数 */
    snapChipUnit() {
      var sb = Number(this.smallBlind)
      if (isFinite(sb) && sb >= 1) return Math.floor(sb)
      var bb = Number(this.bigBlind)
      if (isFinite(bb) && bb >= 2) return Math.max(1, Math.floor(bb / 2))
      return 1
    },
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
      n = this.snapBetToSmallBlindMultiple(n)
      if (n < lo) n = lo
      if (n > hi) n = hi
      this.$emit('update:raiseAmount', n)
    },
    /**
     * 总投入对齐到小底分倍数：先向下取整档；若低于合法最小加投则取「≥minRaise 的最小小底分倍数」。
     */
    snapBetToSmallBlindMultiple(total) {
      var sb = this.snapChipUnit
      var t = Number(total)
      if (!isFinite(t)) return this.minRaise
      var down = Math.floor(t / sb) * sb
      var lo = Number(this.minRaise)
      if (!isFinite(lo) || lo < 1) lo = 1
      if (down >= lo) return down
      return Math.ceil(lo / sb) * sb
    },
    setPotFrac(frac) {
      var pot = Number(this.pot) || 0
      var call = Math.max(0, Number(this.callAmount) || 0)
      var base = pot + call
      // 与「向下吸到小底分倍数」一致：比例部分先向下取整，再交给 clamp 对齐
      var extra = Math.floor(base * frac)
      var want = call + extra
      this.clampEmit(want)
    },
    applyMiniRaise() {
      this.clampEmit(this.minRaise)
    }
  }
}
</script>
