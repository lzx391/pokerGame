<template>
  <div>
    <game-bottom-sheet
        v-if="vm.showMobileHandSheet && vm.heroDockRow"
        title="我的手牌"
        aria-label="查看手牌"
        @close="$store.commit('dpGame/SET_MOBILE_SHEETS', { showMobileHandSheet: false })"
    >
      <div
          class="dp-game-hero-dock dp-game-hero-dock--in-sheet"
          :class="{ 'dp-game-hero-dock--hand-reveal': vm.stage === 'showdown' || vm.stage === 'settled' }"
      >
        <game-player-card
            :player="vm.heroDockRow.player"
            :seat-index="vm.heroDockRow.seatIndex"
            :box-style="vm.getPlayerBoxStyle(vm.heroDockRow.player, vm.heroDockRow.seatIndex)"
            :act-index="vm.actIndex"
            :stage="vm.stage"
            :community-cards="vm.communityCards"
            :community-cards-flip-complete="vm.communityCardsFlipComplete"
            :is-owner="vm.isOwner"
            :owner-reveal-all="vm.ownerRevealAll"
            :my-nickname="vm.user ? vm.user.nickname : ''"
            :hand-deal-key="vm.currentHandSeed"
            :hole-deal-seat-order="vm.holeDealOrderFromDealer(vm.heroDockRow.seatIndex)"
            :hole-deal-player-count="vm.holeDealPlayerCountForAnim"
            :rival-mini="false"
            :hero-hand-dock="true"
            :showdown-hand-leaders="vm.showdownHandLeaderNicknames"
            :seat-chat-text="vm.seatChatTextFor(vm.heroDockRow.player.nickname)"
            :skip-hole-deal-animation="true"
            :deal-reveal-stagger-sec="0.22"
            @card-click="vm.onPlayerCardClick"
        />
      </div>
    </game-bottom-sheet>

    <game-bottom-sheet
        v-if="vm.showMobileActionSheet && (vm.isMyTurn || vm.inSettledStage)"
        :title="vm.inSettledStage ? '准备下一局' : '本轮行动'"
        :aria-label="vm.inSettledStage ? '准备下一局' : '下注行动'"
        :wide="true"
        body-modifier="action"
        @close="$store.commit('dpGame/SET_MOBILE_SHEETS', { showMobileActionSheet: false })"
    >
      <game-action-panel
          :settled-prepare="vm.inSettledStage"
          :ready-time-left="vm.readyTimeLeft"
          :my-ready="vm.myReady"
          :time-left="vm.timeLeft"
          :current-bet-to-call="vm.currentBetToCall"
          :my-bet="vm.myBet"
          :call-amount="vm.callAmount"
          :small-blind="vm.smallBlind"
          :big-blind="vm.bigBlind"
          :min-raise="vm.minRaise"
          :min-total-to-raise="vm.minTotalToRaise"
          :last-raise-increment="vm.lastRaiseIncrementEffective"
          :pot="vm.pot"
          :my-chips="vm.myChips"
          :raise-amount="vm.raiseAmount"
          @update:raiseAmount="$store.commit('dpGame/SET_RAISE_AMOUNT', $event)"
          @call="vm.doCall"
          @raise="vm.doRaise"
          @all-in="vm.doAllIn"
          @fold="vm.doFold"
          @toggle-ready="vm.toggleReady"
          @rebuy="vm.rebuy"
      />
    </game-bottom-sheet>

    <game-bottom-sheet
        v-if="vm.showOwnerHubSheet && vm.isOwner"
        title="房主操作"
        aria-label="房主操作"
        :wide="true"
        body-modifier="owner-hub"
        @close="vm.closeOwnerHubPanel"
    >
      <game-owner-panel
          hide-title
          hide-tool-entry
          in-sheet
          :stage="vm.stage"
          :pots="vm.pots"
          :pot="vm.pot"
          :pot-winners="vm.potWinners"
          :selected-winners="vm.selectedWinners"
          :all-pots-have-winners="vm.allPotsHaveWinners"
          @toggle-pot-winner="vm.onTogglePotWinnerPayload"
          @confirm-pot-judge="vm.confirmPotJudge"
          @confirm-judge-win="vm.confirmJudgeWin"
      />
      <game-owner-tool-modal
          :embedded="true"
          :visible="true"
          :owner-reveal-all="vm.ownerRevealAll"
          @update:ownerRevealAll="$store.commit('dpGame/SET_OWNER_REVEAL_ALL', $event)"
          :owner-tool-type="vm.ownerToolType"
          @update:ownerToolType="$store.commit('dpGame/SET_OWNER_TOOL', { ownerToolType: $event })"
          :owner-action-target="vm.ownerActionTarget"
          @update:ownerActionTarget="$store.commit('dpGame/SET_OWNER_TOOL', { ownerActionTarget: $event })"
          :owner-action-players="vm.ownerActionPlayers"
          :demo-bot-adding="vm.demoBotAdding"
          :demo-bot-added-tip="vm.demoBotAddedTip"
          :maniac-bot-adding="vm.maniacBotAdding"
          :maniac-bot-added-tip="vm.maniacBotAddedTip"
          :tag-bot-adding="vm.tagBotAdding"
          :tag-bot-added-tip="vm.tagBotAddedTip"
          :shark-bot-adding="vm.sharkBotAdding"
          :shark-bot-added-tip="vm.sharkBotAddedTip"
          :llm-bot-adding="vm.llmBotAdding"
          :llm-bot-added-tip="vm.llmBotAddedTip"
          @close="vm.closeOwnerHubPanel"
          @add-demo-bot="vm.addDemoBot"
          @add-maniac-bot="vm.addManiacBot"
          @add-tag-bot="vm.addTagBot"
          @add-shark-bot="vm.addSharkBot"
          @add-llm-bot="vm.addLlmBot"
          @transfer-owner="vm.doTransferOwner"
          @kick-player="vm.doKickPlayer"
      />
    </game-bottom-sheet>
  </div>
</template>

<script>
import GameBottomSheet from './GameBottomSheet.vue'
import GamePlayerCard from './GamePlayerCard.vue'
import GameActionPanel from './GameActionPanel.vue'
import GameOwnerPanel from './GameOwnerPanel.vue'
import GameOwnerToolModal from './GameOwnerToolModal.vue'

export default {
  name: 'GameDpGameSheets',
  components: {
    GameBottomSheet,
    GamePlayerCard,
    GameActionPanel,
    GameOwnerPanel,
    GameOwnerToolModal
  },
  inject: ['dpGameView'],
  computed: {
    vm: function () {
      return this.dpGameView
    }
  }
}
</script>
