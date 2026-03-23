<template>
  <div class="dp-action-panel">
    <div class="dp-action-panel__turn">
      轮到你行动了（30秒超时自动弃牌）
    </div>
    <div class="dp-action-panel__meta">
      当前跟注额: {{ currentBetToCall }} | 你已下注: {{ myBet }} | 还需跟: {{ callAmount }}
    </div>

    <div class="dp-action-panel__row">
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
        />
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

      <button type="button" class="dp-btn--allin" @click="$emit('all-in')">
        All-In ({{ myChips }})
      </button>

      <button type="button" class="dp-btn--fold" @click="$emit('fold')">
        弃牌
      </button>
    </div>
  </div>
</template>

<script>
export default {
  name: 'GameActionPanel',
  props: {
    timeLeft: { type: Number, required: true },
    currentBetToCall: { type: Number, required: true },
    myBet: { type: Number, required: true },
    callAmount: { type: Number, required: true },
    smallBlind: { type: Number, required: true },
    bigBlind: { type: Number, required: true },
    minRaise: { type: Number, required: true },
    myChips: { type: Number, required: true },
    raiseAmount: { type: Number, required: true }
  },
  methods: {
    bumpRaise(delta) {
      var n = Number(this.raiseAmount) + delta
      this.$emit('update:raiseAmount', n)
    },
    onRaiseInput(raw) {
      var n = parseFloat(raw)
      if (isNaN(n)) n = 0
      this.$emit('update:raiseAmount', n)
    }
  }
}
</script>
