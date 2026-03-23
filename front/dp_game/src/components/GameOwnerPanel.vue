<template>
  <div class="dp-owner-panel">
    <div class="dp-owner-panel__title">房主有神器</div>

    <div class="dp-owner-panel__row">
      <button type="button" class="dp-btn--owner-purple" @click="$emit('open-owner-tools')">
        打开房主神器
      </button>
      <button type="button" class="dp-btn--owner-orange" @click="$emit('update:ownerRevealAll', !ownerRevealAll)">
        {{ ownerRevealAll ? '关闭看穿底牌' : '一键看穿所有底牌' }}
      </button>
    </div>

    <div class="dp-owner-panel__hint">
      「一键看穿」仅你自己可见，NPC 和其他真人不会知道你看到了他们的牌。
    </div>

    <div v-if="stage === 'showdown'">
      <div class="dp-owner-panel__showdown-title">
        摊牌阶段 - 请为每个池选择赢家
      </div>

      <template v-if="pots.length > 0">
        <div
          v-for="(potItem, pi) in pots"
          :key="'pot' + pi"
          class="dp-pot-block"
        >
          <div class="dp-pot-block__title">
            {{ pi === 0 ? '主池' : '边池 ' + pi }} - 金额: <span class="dp-top-bar__pot">{{ potItem.amount }}</span>
          </div>
          <div class="dp-pot-block__eligible">
            有资格的玩家: {{ formatNickList(potItem.eligiblePlayers) }}
          </div>
          <div class="dp-pot-block__btns">
            <button
              v-for="name in potItem.eligiblePlayers"
              :key="'pw' + pi + name"
              type="button"
              :style="potWinnerBtnStyle(pi, name)"
              @click="$emit('toggle-pot-winner', { potIndex: pi, nickname: name })"
            >
              {{ displayNick(name) }} {{ isPotWinner(pi, name) ? '(已选)' : '' }}
            </button>
          </div>
        </div>
        <button
          type="button"
          class="dp-btn--success-wide"
          :disabled="!allPotsHaveWinners"
          :style="{ opacity: allPotsHaveWinners ? 1 : 0.4 }"
          @click="$emit('confirm-pot-judge')"
        >
          确认按池结算
        </button>
      </template>

      <template v-else>
        <div class="dp-owner-panel__muted" style="text-align:center;">
          点击上方玩家卡片选择赢家（可多选平分）
        </div>
        <div v-if="selectedWinners.length > 0" class="dp-owner-panel__muted" style="text-align:center;">
          已选: {{ formatNickList(selectedWinners) }}
        </div>
        <button
          type="button"
          class="dp-btn--success-wide"
          :disabled="selectedWinners.length === 0"
          :style="{ opacity: selectedWinners.length === 0 ? 0.4 : 1 }"
          @click="$emit('confirm-judge-win')"
        >
          确认结算（底池 {{ pot }} 分给 {{ selectedWinners.length }} 人）
        </button>
      </template>
    </div>

    <div class="dp-owner-panel__footer">
      摊牌后系统会自动结算并在准备阶段结束后开启下一局，无需手动点击“重新发牌”
    </div>
  </div>
</template>

<script>
import { dpDisplayNickname } from '../utils/dpDisplayNickname'

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
    displayNick(nickname) {
      return dpDisplayNickname(nickname)
    },
    formatNickList(arr) {
      if (!arr || arr.length === 0) return ''
      return arr.map(dpDisplayNickname).join(', ')
    },
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
        border: on ? '2px solid var(--dp-success)' : '1px solid var(--dp-subpanel-border)',
        background: on ? 'var(--dp-player-card-winner-bg)' : 'var(--dp-btn-ghost-bg)',
        color: on ? 'var(--dp-success)' : 'var(--dp-text-primary)',
        fontWeight: on ? 'bold' : 'normal'
      }
    }
  }
}
</script>
