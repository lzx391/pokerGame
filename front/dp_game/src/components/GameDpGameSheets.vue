<template>
  <div>
    <transition name="dp-sheet">
    <game-bottom-sheet
        v-if="vm.showMobileHandSheet && vm.heroDockRow && (!vm.useRetroHandHologramWide || !vm.showHeroHandHologram)"
        title="我的手牌"
        aria-label="查看手牌"
        @close="$store.commit('dpGame/SET_MOBILE_SHEETS', { showMobileHandSheet: false })"
    >
      <div
          class="dp-game-hero-dock dp-game-hero-dock--in-sheet"
          :class="{
            'dp-game-hero-dock--hand-reveal':
              vm.cardDisplayStage === 'showdown' || vm.cardDisplayStage === 'settled'
          }"
      >
        <game-player-card
            :player="vm.heroDockRow.player"
            :seat-index="vm.heroDockRow.seatIndex"
            :box-style="vm.getPlayerBoxStyle(vm.heroDockRow.player, vm.heroDockRow.seatIndex)"
            :act-index="vm.actIndex"
            :stage="vm.cardDisplayStage"
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
            :showdown-hand-leaders="vm.tableShowdownHandLeaderNicknames"
            :display-chips="vm.playerEconomyForDisplay(vm.heroDockRow.player).chips"
            :display-bet="vm.playerEconomyForDisplay(vm.heroDockRow.player).bet"
            :actual-stage="vm.stage"
            :retro-showdown-tv-pending="vm.retroShowdownTvPending"
            :seat-chat-text="vm.seatChatTextFor(vm.heroDockRow.player.nickname)"
            :skip-hole-deal-animation="true"
            :deal-reveal-stagger-sec="0.22"
            @card-click="vm.onPlayerCardClick"
        />
      </div>
    </game-bottom-sheet>
    </transition>

    <transition name="dp-sheet">
    <game-bottom-sheet
        v-if="vm.showMobileActionSheet && (vm.isMyTurn || vm.uiInSettledStage)"
        ref="guideActionSheet"
        :title="vm.uiInSettledStage ? '结算阶段' : '本轮行动'"
        :aria-label="vm.uiInSettledStage ? '结算阶段' : '本轮行动'"
        :wide="true"
        body-modifier="action"
        @close="onGuideActionSheetClose"
    >
      <game-settled-prepare-bar
          v-if="vm.uiInSettledStage"
          :my-ready="vm.myReady"
          :ready-time-left="vm.readyTimeLeft"
          :my-chips="vm.displayMyChips"
          :big-blind="vm.bigBlind"
          @toggle-ready="vm.toggleReady"
          @rebuy="vm.rebuy"
      />
      <game-action-panel
          v-else
          ref="guideSheetActionPanel"
          :time-left="vm.timeLeft"
          :my-bet="vm.myBet"
          :call-amount="vm.callAmount"
          :small-blind="vm.smallBlind"
          :big-blind="vm.bigBlind"
          :min-raise="vm.minRaise"
          :min-total-to-raise="vm.minTotalToRaise"
          :last-raise-increment="vm.lastRaiseIncrementEffective"
          :pot="vm.pot"
          :my-chips="vm.displayMyChips"
          :raise-amount="vm.raiseAmount"
          @update:raiseAmount="$store.commit('dpGame/SET_RAISE_AMOUNT', $event)"
          @call="vm.doCall"
          @raise="vm.doRaise"
          @all-in="vm.doAllIn"
          @fold="vm.doFold"
      />
    </game-bottom-sheet>
    </transition>

    <transition name="dp-sheet">
    <game-bottom-sheet
        v-if="vm.showOwnerHubSheet && vm.isOwner && !vm.useRetroOwnerPanelWide"
        title="房主操作"
        aria-label="房主操作"
        :wide="true"
        body-modifier="owner-hub"
        @close="vm.closeOwnerHubPanel"
    >
      <template slot="overlay">
        <custom-npc-style-dialog
            v-if="vm.showCustomNpcStyleDialog"
            :visible="true"
            :pending-count="vm.customNpcPendingCount"
            :submitting="vm.customBotAdding"
            @cancel="vm.closeCustomNpcStyleDialog"
            @confirm="(profile) => vm.submitCustomNpcBatch(profile)"
        />
      </template>
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
          :lag-bot-adding="vm.lagBotAdding"
          :lag-bot-added-tip="vm.lagBotAddedTip"
          :nit-bot-adding="vm.nitBotAdding"
          :nit-bot-added-tip="vm.nitBotAddedTip"
          :call-bot-adding="vm.callBotAdding"
          :call-bot-added-tip="vm.callBotAddedTip"
          :llm-bot-adding="vm.llmBotAdding"
          :llm-bot-added-tip="vm.llmBotAddedTip"
          :llm-global-bot-adding="vm.llmGlobalBotAdding"
          :llm-global-bot-added-tip="vm.llmGlobalBotAddedTip"
          :custom-bot-adding="vm.customBotAdding"
          :custom-bot-added-tip="vm.customBotAddedTip"
          @close="vm.closeOwnerHubPanel"
          @confirm-add-npcs="(p) => vm.confirmAddOwnerNpcs(p)"
          @open-deck-preset="() => vm.openDeckPresetDialog()"
          @transfer-owner="() => vm.doTransferOwner()"
          @kick-players="(nicks) => vm.doKickPlayers(nicks)"
      />
    </game-bottom-sheet>
    </transition>

    <game-owner-touch-panel
        v-if="vm.isOwner && vm.gameUiTheme === 'retro8bit'"
        :open="vm.ownerTouchSheetOpen"
        :game-ui-theme="vm.gameUiTheme"
        :owner-reveal-all="vm.ownerRevealAll"
        :show-custom-npc-style-dialog="vm.showCustomNpcStyleDialog"
        :custom-npc-pending-count="vm.customNpcPendingCount"
        :demo-bot-adding="vm.demoBotAdding"
        :demo-bot-added-tip="vm.demoBotAddedTip"
        :maniac-bot-adding="vm.maniacBotAdding"
        :maniac-bot-added-tip="vm.maniacBotAddedTip"
        :tag-bot-adding="vm.tagBotAdding"
        :tag-bot-added-tip="vm.tagBotAddedTip"
        :lag-bot-adding="vm.lagBotAdding"
        :lag-bot-added-tip="vm.lagBotAddedTip"
        :nit-bot-adding="vm.nitBotAdding"
        :nit-bot-added-tip="vm.nitBotAddedTip"
        :call-bot-adding="vm.callBotAdding"
        :call-bot-added-tip="vm.callBotAddedTip"
        :llm-bot-adding="vm.llmBotAdding"
        :llm-bot-added-tip="vm.llmBotAddedTip"
        :llm-global-bot-adding="vm.llmGlobalBotAdding"
        :llm-global-bot-added-tip="vm.llmGlobalBotAddedTip"
        :custom-bot-adding="vm.customBotAdding"
        :custom-bot-added-tip="vm.customBotAddedTip"
        @close="vm.closeOwnerTouchPanel"
        @close-custom-npc="vm.closeCustomNpcStyleDialog"
        @submit-custom-npc="(profile) => vm.submitCustomNpcBatch(profile)"
        @confirm-add-npcs="(p) => vm.confirmAddOwnerNpcs(p)"
        @confirm-batch-add-npcs="(p) => vm.confirmBatchAddOwnerNpcs(p)"
        @open-deck-preset="() => vm.openDeckPresetDialog()"
        @transfer-owner="() => vm.doTransferOwner({ skipConfirm: true })"
        @kick-players="(nicks) => vm.doKickPlayers(nicks, { skipConfirm: true })"
        @toggle-reveal="vm.onOwnerTouchToggleReveal"
    />

    <transition name="dp-sheet">
    <game-bottom-sheet
        v-if="vm.isOwner && vm.stage === 'showdown' && vm.useRetroOwnerPanelWide && vm.showOwnerPotJudgeSheet"
        title="结算阶段"
        aria-label="结算阶段"
        :wide="true"
        body-modifier="owner-hub"
        @close="vm.closeOwnerPotJudgeSheet"
    >
      <template slot="overlay">
        <custom-npc-style-dialog
            v-if="vm.showCustomNpcStyleDialog"
            :visible="true"
            :pending-count="vm.customNpcPendingCount"
            :submitting="vm.customBotAdding"
            @cancel="vm.closeCustomNpcStyleDialog"
            @confirm="(profile) => vm.submitCustomNpcBatch(profile)"
        />
      </template>
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
    </game-bottom-sheet>
    </transition>

    <custom-npc-style-dialog
        v-if="vm.showCustomNpcStyleDialog && vm.useRetroOwnerPanelWide && vm.ownerTerminalOpen"
        :visible="true"
        :pending-count="vm.customNpcPendingCount"
        :submitting="vm.customBotAdding"
        @cancel="vm.closeCustomNpcStyleDialog"
        @confirm="(profile) => vm.submitCustomNpcBatch(profile)"
    />

    <game-owner-hub-panel
        v-if="vm.useRetroOwnerPanelWide"
        :open="vm.ownerTerminalOpen"
        :owner-reveal-all="vm.ownerRevealAll"
        :demo-bot-adding="vm.demoBotAdding"
        :demo-bot-added-tip="vm.demoBotAddedTip"
        :maniac-bot-adding="vm.maniacBotAdding"
        :maniac-bot-added-tip="vm.maniacBotAddedTip"
        :tag-bot-adding="vm.tagBotAdding"
        :tag-bot-added-tip="vm.tagBotAddedTip"
        :lag-bot-adding="vm.lagBotAdding"
        :lag-bot-added-tip="vm.lagBotAddedTip"
        :nit-bot-adding="vm.nitBotAdding"
        :nit-bot-added-tip="vm.nitBotAddedTip"
        :call-bot-adding="vm.callBotAdding"
        :call-bot-added-tip="vm.callBotAddedTip"
        :llm-bot-adding="vm.llmBotAdding"
        :llm-bot-added-tip="vm.llmBotAddedTip"
        :llm-global-bot-adding="vm.llmGlobalBotAdding"
        :llm-global-bot-added-tip="vm.llmGlobalBotAddedTip"
        :custom-bot-adding="vm.customBotAdding"
        :custom-bot-added-tip="vm.customBotAddedTip"
        @close="vm.closeOwnerTerminal"
        @confirm-add-npcs="(p) => vm.confirmAddOwnerNpcs(p)"
        @open-deck-preset="() => vm.openDeckPresetDialog()"
        @transfer-owner="() => vm.doTransferOwner()"
        @kick-players="(nicks) => vm.doKickPlayers(nicks)"
        @toggle-reveal="vm.onOwnerTerminalToggleReveal"
    />

    <game-deck-preset-password-gate
        :visible.sync="vm.showDeckPresetPasswordGate"
        :room-id="vm.roomId"
        :requester-nickname="vm.user && vm.user.nickname"
        :game-ui-theme="vm.gameUiTheme"
        @verified="(pwd) => vm.onDeckPresetPasswordVerified(pwd)"
    />

    <game-deck-preset-dialog
        :visible.sync="vm.showDeckPresetDialog"
        :player-count="vm.deckPresetPlayerCount"
        :saved-preset-count="vm.deckPresetSavedCount"
        :initial-cards="vm.deckPresetInitialCards"
        :submitting="vm.deckPresetSubmitting"
        :game-ui-theme="vm.gameUiTheme"
        @confirm="(cards) => vm.submitDeckPreset(cards)"
    />

    <game-player-social-sheet
        v-if="vm.playerSocialOpen && vm.playerSocialTarget"
        :visible="true"
        :target="vm.playerSocialTarget"
        @close="() => vm.closePlayerSocialSheet()"
        @view-hand-history-with-opponent="(p) => vm.openOpponentHandHistoryFromSocial(p)"
    />

    <game-hand-history-modal
        v-if="vm.gameUiTheme !== 'retro8bit'"
        :visible="vm.showOpponentHandHistoryModal"
        :game-ui-theme="vm.effectiveThemeForCss"
        list-mode="withOpponent"
        stacked
        :other-user-id="vm.opponentHandHistoryOtherUserId"
        :opponent-display-name="vm.opponentHandHistoryDisplayName"
        @close="$store.commit('dpGame/SET_MODAL', { showOpponentHandHistoryModal: false })"
    />
    <game-invite-friend-panel
        v-if="vm.useRetroInvitePanelWide"
        :open="vm.inviteFriendOpen"
        :room-id="vm.roomId"
        :my-user-id="inviteFriendMyUserId"
        @close="vm.closeInviteFriendSheet"
    />
    <game-invite-friend-sheet
        v-if="vm.inviteFriendOpen && !vm.useRetroInvitePanelWide"
        :visible="true"
        :room-id="vm.roomId"
        :my-user-id="inviteFriendMyUserId"
        @close="vm.closeInviteFriendSheet"
    />

    <game-friend-chat-panel
        v-if="vm.useRetroFriendChatPanelWide"
        :open="vm.friendChatPickerOpen"
        :my-user-id="friendChatMyUserId"
        @close="vm.closeFriendChatPicker"
        @open-chat="(f) => vm.openFriendChatFromPicker(f)"
    />
    <game-friend-chat-sheet
        v-if="vm.friendChatPickerOpen && !vm.useRetroFriendChatPanelWide"
        :visible="true"
        :my-user-id="friendChatMyUserId"
        :game-ui-theme="vm.gameUiTheme"
        @close="vm.closeFriendChatPicker"
        @open-chat="(f) => vm.openFriendChatFromPicker(f)"
    />

    <friend-chat-dialog
        :visible.sync="vm.friendChatVisible"
        :peer-user-id="vm.friendChatPeerId"
        :peer-display-name="vm.friendChatPeerName"
        :peer-avatar-url="vm.friendChatPeerAvatar"
        :peer-avatar-updated-at="vm.friendChatPeerAvatarUpdatedAt"
        :peer-unread-count="vm.friendChatPeerUnread"
        @closed="vm.onFriendChatClosed"
    />

  </div>
</template>

<script>
import GameBottomSheet from './GameBottomSheet.vue'
import GamePlayerCard from './GamePlayerCard.vue'
import GameActionPanel from './GameActionPanel.vue'
import GameSettledPrepareBar from './GameSettledPrepareBar.vue'
import GameOwnerPanel from './GameOwnerPanel.vue'
import GameOwnerToolModal from './GameOwnerToolModal.vue'
import GamePlayerSocialSheet from './GamePlayerSocialSheet.vue'
import GameHandHistoryModal from './GameHandHistoryModal.vue'
import GameInviteFriendSheet from './GameInviteFriendSheet.vue'
import GameInviteFriendPanel from './GameInviteFriendPanel.vue'
import GameFriendChatSheet from './GameFriendChatSheet.vue'
import GameFriendChatPanel from './GameFriendChatPanel.vue'
import FriendChatDialog from './FriendChatDialog.vue'
import GameOwnerHubPanel from './GameOwnerHubPanel.vue'
import GameOwnerTouchPanel from './GameOwnerTouchPanel.vue'
import CustomNpcStyleDialog from './CustomNpcStyleDialog.vue'
import GameDeckPresetDialog from './GameDeckPresetDialog.vue'
import GameDeckPresetPasswordGate from './GameDeckPresetPasswordGate.vue'

export default {
  name: 'GameDpGameSheets',
  components: {
    GameBottomSheet,
    GamePlayerCard,
    GameActionPanel,
    GameSettledPrepareBar,
    GameOwnerPanel,
    GameOwnerToolModal,
    GamePlayerSocialSheet,
    GameHandHistoryModal,
    GameInviteFriendSheet,
    GameInviteFriendPanel,
    GameFriendChatSheet,
    GameFriendChatPanel,
    FriendChatDialog,
    GameOwnerHubPanel,
    GameOwnerTouchPanel,
    CustomNpcStyleDialog,
    GameDeckPresetDialog,
    GameDeckPresetPasswordGate
  },
  inject: ['dpGameView'],
  computed: {
    vm: function () {
      return this.dpGameView
    },
    inviteFriendMyUserId: function () {
      var u = this.vm && this.vm.user
      var n = u && u.userId != null && u.userId !== '' ? Number(u.userId) : 0
      return isNaN(n) || n <= 0 ? 0 : n
    },
    friendChatMyUserId: function () {
      return this.vm && this.vm.friendChatMyUserId != null ? this.vm.friendChatMyUserId : 0
    }
  },
  methods: {
    onGuideActionSheetClose: function () {
      if (this.vm && this.vm.dpGuideMode && this.vm.isGuideActionPanelStep && this.vm.isGuideActionPanelStep()) {
        return
      }
      this.$store.commit('dpGame/SET_MOBILE_SHEETS', { showMobileActionSheet: false })
    }
  }
}
</script>
