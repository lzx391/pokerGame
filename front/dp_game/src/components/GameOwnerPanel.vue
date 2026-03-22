<template>
  <div style="margin-top:20px; background:#fff; padding:15px; border-radius:10px;">
    <div style="font-size:14px; font-weight:bold; color:#333; margin-bottom:10px; text-align:center;">房主有神器</div>

    <div style="display:flex; justify-content:center; gap:10px; margin-bottom:10px; flex-wrap:wrap;">
      <button
        type="button"
        style="padding:8px 16px; border:none; border-radius:6px; cursor:pointer; font-size:13px; font-weight:bold;
               background: #722ed1; color:#fff; box-shadow:0 2px 6px rgba(114,46,209,0.35);"
        @click="$emit('open-owner-tools')"
      >
        打开房主神器
      </button>
      <button
        type="button"
        style="padding:8px 16px; border:none; border-radius:6px; cursor:pointer; font-size:13px; font-weight:bold;
               background: #fa8c16; color:#fff; box-shadow:0 2px 6px rgba(250,140,22,0.35);"
        @click="$emit('update:ownerRevealAll', !ownerRevealAll)"
      >
        {{ ownerRevealAll ? '关闭看穿底牌' : '一键看穿所有底牌' }}
      </button>
    </div>

    <div style="font-size:12px; color:#999; text-align:center; margin-bottom:6px;">
      「一键看穿」仅你自己可见，NPC 和其他真人不会知道你看到了他们的牌。
    </div>

    <div v-if="stage === 'showdown'" style="text-align:center; margin-bottom:10px;">
      <div style="color:#f5222d; font-weight:bold; margin-bottom:8px;">
        摊牌阶段 - 请为每个池选择赢家
      </div>

      <template v-if="pots.length > 0">
        <div
          v-for="(potItem, pi) in pots"
          :key="'pot' + pi"
          style="background:#fafafa; border:1px solid #e8e8e8; border-radius:8px; padding:10px; margin-bottom:10px; text-align:left;"
        >
          <div style="font-weight:bold; margin-bottom:6px; color:#333;">
            {{ pi === 0 ? '主池' : '边池 ' + pi }} - 金额: <span style="color:#f5222d;">{{ potItem.amount }}</span>
          </div>
          <div style="font-size:12px; color:#999; margin-bottom:6px;">
            有资格的玩家: {{ potItem.eligiblePlayers.join(', ') }}
          </div>
          <div style="display:flex; flex-wrap:wrap; gap:6px;">
            <button
              v-for="name in potItem.eligiblePlayers"
              :key="'pw' + pi + name"
              type="button"
              :style="potWinnerBtnStyle(pi, name)"
              @click="$emit('toggle-pot-winner', { potIndex: pi, nickname: name })"
            >
              {{ name }} {{ isPotWinner(pi, name) ? '(已选)' : '' }}
            </button>
          </div>
        </div>
        <button
          type="button"
          :disabled="!allPotsHaveWinners"
          style="width:100%; padding:12px; background:#52c41a; color:#fff; border:none; border-radius:5px; cursor:pointer; font-weight:bold;"
          :style="{ opacity: allPotsHaveWinners ? 1 : 0.4 }"
          @click="$emit('confirm-pot-judge')"
        >
          确认按池结算
        </button>
      </template>

      <template v-else>
        <div style="font-size:13px; color:#666; margin-bottom:8px;">
          点击上方玩家卡片选择赢家（可多选平分）
        </div>
        <div v-if="selectedWinners.length > 0" style="font-size:13px; color:#333; margin-bottom:8px;">
          已选: {{ selectedWinners.join(', ') }}
        </div>
        <button
          type="button"
          :disabled="selectedWinners.length === 0"
          style="width:100%; padding:12px; background:#52c41a; color:#fff; border:none; border-radius:5px; cursor:pointer; font-weight:bold;"
          :style="{ opacity: selectedWinners.length === 0 ? 0.4 : 1 }"
          @click="$emit('confirm-judge-win')"
        >
          确认结算（底池 {{ pot }} 分给 {{ selectedWinners.length }} 人）
        </button>
      </template>
    </div>

    <div class="actions" style="margin-top: 20px; text-align: center; font-size:12px; color:#999;">
      摊牌后系统会自动结算并在准备阶段结束后开启下一局，无需手动点击“重新发牌”
    </div>
  </div>
</template>

<script>
export default {
  name: 'GameOwnerPanel',
  props: {
    ownerRevealAll: { type: Boolean, default: false },
    stage: { type: String, required: true },
    pots: { type: Array, default: function () { return [] } },
    pot: { type: Number, default: 0 },
    potWinners: { type: Object, default: function () { return {} } },
    selectedWinners: { type: Array, default: function () { return [] } },
    allPotsHaveWinners: { type: Boolean, default: false }
  },
  methods: {
    isPotWinner(potIndex, nickname) {
      var winners = this.potWinners[potIndex] || []
      return winners.indexOf(nickname) > -1
    },
    potWinnerBtnStyle(pi, name) {
      var on = this.isPotWinner(pi, name)
      return {
        padding: '5px 12px',
        borderRadius: '4px',
        cursor: 'pointer',
        border: on ? '2px solid #52c41a' : '1px solid #d9d9d9',
        background: on ? '#f6ffed' : '#fff',
        color: on ? '#52c41a' : '#333',
        fontWeight: on ? 'bold' : 'normal'
      }
    }
  }
}
</script>
