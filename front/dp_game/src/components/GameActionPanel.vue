<template>
  <div
    style="margin-top:20px; background:#fff; padding:15px; border-radius:10px; box-shadow:0 -2px 10px rgba(0,0,0,0.05);"
  >
    <div style="text-align:center; color:#faad14; font-weight:bold; margin-bottom:10px;">
      轮到你行动了（30秒超时自动弃牌）
    </div>
    <div style="font-size:13px; color:#666; text-align:center; margin-bottom:10px;">
      当前跟注额: {{ currentBetToCall }} | 你已下注: {{ myBet }} | 还需跟: {{ callAmount }}
    </div>

    <div style="display:flex; gap:10px; flex-wrap:wrap; justify-content:center; align-items:center;">
      <div
        style="display: flex; align-items: center; justify-content: center;
               width: 40px; height: 40px;
               background: #ffffff;
               border: 2px solid #000000;
               border-radius: 50%;
               flex-shrink: 0;
               box-sizing: border-box;"
      >
        <span
          style="color: #ff4d4f; font-size: 18px; font-weight: 900; font-family: 'Arial Black', sans-serif; line-height: 1;"
        >
          {{ timeLeft }}
        </span>
      </div>

      <button
        type="button"
        style="height: 40px; padding: 0 18px; background: #1890ff; color: #fff; border: none; border-radius: 5px; cursor: pointer; font-weight: bold; display: flex; align-items: center;"
        @click="$emit('call')"
      >
        {{ callAmount > 0 ? '跟注 ' + callAmount : '过牌' }}
      </button>

      <div style="display:flex; align-items:center; gap:5px; height: 40px;">
        <button
          type="button"
          style="height: 32px; width: 32px; padding: 0; background: #fff; border: 1px solid #f57f17; color: #f57f17; border-radius: 4px; cursor: pointer; font-weight: bold;"
          @click="bumpRaise(smallBlind)"
        >
          +{{ smallBlind }}
        </button>
        <button
          type="button"
          style="height: 32px; width: 32px; padding: 0; background: #fff; border: 1px solid #f57f17; color: #f57f17; border-radius: 4px; cursor: pointer; font-weight: bold;"
          @click="bumpRaise(bigBlind)"
        >
          +{{ bigBlind }}
        </button>
        <input
          type="number"
          :value="raiseAmount"
          :min="minRaise"
          :max="myChips"
          style="width: 60px; height: 32px; padding: 0; border: 1px solid #d9d9d9; border-radius: 4px; text-align: center;"
          @input="onRaiseInput($event.target.value)"
        />
        <button
          type="button"
          style="height: 32px; width: 32px; padding: 0; background: #fff; border: 1px solid #f57f17; color: #f57f17; border-radius: 4px; cursor: pointer; font-weight: bold;"
          @click="bumpRaise(-bigBlind)"
        >
          -{{ bigBlind }}
        </button>
        <button
          type="button"
          style="height: 32px; width: 32px; padding: 0; background: #fff; border: 1px solid #f57f17; color: #f57f17; border-radius: 4px; cursor: pointer; font-weight: bold;"
          @click="bumpRaise(-smallBlind)"
        >
          -{{ smallBlind }}
        </button>
      </div>

      <button
        type="button"
        :disabled="raiseAmount < minRaise"
        style="height: 40px; padding: 0 14px; background: #f57f17; color: #fff; border: none; border-radius: 5px; cursor: pointer; font-weight: bold;"
        @click="$emit('raise')"
      >
        加注
      </button>

      <button
        type="button"
        style="height: 40px; padding: 0 14px; background: #c62828; color: #fff; border: none; border-radius: 5px; cursor: pointer; font-weight: bold;"
        @click="$emit('all-in')"
      >
        All-In ({{ myChips }})
      </button>

      <button
        type="button"
        style="height: 40px; padding: 0 18px; background: #ff4d4f; color: #fff; border: none; border-radius: 5px; cursor: pointer; font-weight: bold;"
        @click="$emit('fold')"
      >
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
