<template>
  <div>
    <div
        v-if="vm.heroDockRow || vm.isMyTurn || vm.inSettledStage || vm.isOwner"
        class="dp-game-hero-action-row dp-game-hero-action-row--hide-narrow"
        aria-label="本人手牌与操作"
    >
      <div
          v-if="vm.viewerSeatedAtTable"
          class="dp-game-footer-left-bar"
          aria-label="聊天与快捷操作"
      >
        <div class="dp-game-footer-chat-cluster">
          <game-room-chat-panel
              dock
              :messages="vm.roomChatMessages"
          />
          <game-room-chat-bar
              :value="vm.chatInputDraft"
              variant="hero"
              @input="$store.commit('dpGame/SET_CHAT_DRAFT', $event)"
              @send="vm.sendRoomChat"
          />
        </div>
        <div
            v-if="vm.heroDockRow"
            class="dp-game-footer-toolbar dp-game-hero-action-row__owner-cluster"
            aria-label="离座与手牌"
        >
          <button
              type="button"
              class="dp-game-hero-action-row__owner-btn"
              @click="vm.doLeaveSeat"
          >
            主动离座
          </button>
          <button
              v-if="vm.showHeroViewHandButton"
              type="button"
              class="dp-game-hero-action-row__owner-btn dp-game-hero-action-row__owner-btn--hand"
              data-dp-hero-deal-target
              @click="$store.commit('dpGame/SET_MOBILE_SHEETS', { showMobileHandSheet: true })"
          >
            查看手牌
          </button>
        </div>
      </div>
      <div
          v-if="vm.heroDockRow && vm.showBottomHeroDock"
          class="dp-game-hero-dock"
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
            @card-click="vm.onPlayerCardClick"
        />
      </div>
      <div
          class="dp-game-inline-action-slot"
          :class="{
            'dp-game-inline-action-slot--solo': !vm.heroDockRow || !vm.showBottomHeroDock
          }"
      >
        <game-settled-prepare-bar
            v-if="vm.inSettledStage"
            :my-ready="vm.myReady"
            :ready-time-left="vm.readyTimeLeft"
            :my-chips="vm.myChips"
            :big-blind="vm.bigBlind"
            @toggle-ready="vm.toggleReady"
            @rebuy="vm.rebuy"
        />
        <game-action-panel
            v-else-if="vm.isMyTurn"
            :time-left="vm.timeLeft"
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
        />
        <div
            v-else-if="vm.heroDockRow"
            class="dp-game-action-slot-cover"
            aria-hidden="true"
        />
      </div>
    </div>

    <!-- 窄屏 / 全屏：聊天 + 离座/手牌/行动 同一横排靠左 -->
    <div
        v-if="vm.heroDockRow || vm.isMyTurn || vm.inSettledStage || vm.isOwner"
        class="dp-game-mobile-hero-bar"
        aria-label="手牌与行动"
    >
      <div class="dp-game-mobile-hero-bar__inline">
        <div
            class="dp-game-mobile-hero-bar__dock-row"
            :class="{ 'dp-game-mobile-hero-bar__dock-row--no-chat': !vm.viewerSeatedAtTable }"
        >
          <div
              v-if="vm.viewerSeatedAtTable"
              class="dp-game-footer-left-bar"
              aria-label="聊天与快捷操作"
          >
            <div class="dp-game-footer-chat-cluster">
              <game-room-chat-panel
                  dock
                  :messages="vm.roomChatMessages"
              />
              <game-room-chat-bar
                  class="dp-game-mobile-hero-bar__chat"
                  :value="vm.chatInputDraft"
                  variant="mobile"
                  @input="$store.commit('dpGame/SET_CHAT_DRAFT', $event)"
                  @send="vm.sendRoomChat"
              />
            </div>
            <div class="dp-game-footer-toolbar" aria-label="底栏操作">
              <button
                  v-if="vm.heroDockRow"
                  type="button"
                  class="dp-game-mobile-hero-bar__btn dp-game-mobile-hero-bar__btn--toolbar"
                  @click="vm.doLeaveSeat"
              >
                主动离座
              </button>
              <button
                  v-if="vm.heroDockRow && vm.showHeroViewHandButton"
                  type="button"
                  class="dp-game-mobile-hero-bar__btn dp-game-mobile-hero-bar__btn--toolbar"
                  data-dp-hero-deal-target
                  @click="$store.commit('dpGame/SET_MOBILE_SHEETS', { showMobileHandSheet: true })"
              >
                查看手牌
              </button>
              <button
                  v-if="vm.isMyTurn"
                  type="button"
                  class="dp-game-mobile-hero-bar__btn dp-game-mobile-hero-bar__btn--toolbar dp-game-mobile-hero-bar__btn--action"
                  :class="{ 'dp-game-mobile-hero-bar__btn--urgent': vm.timeLeft <= 10 }"
                  :aria-label="'打开行动抽屉，倒计时 ' + vm.timeLeft + ' 秒'"
                  @click="$store.commit('dpGame/SET_MOBILE_SHEETS', { showMobileActionSheet: true })"
              >
                行动（{{ vm.timeLeft }}s）
              </button>
              <button
                  v-if="vm.inSettledStage"
                  type="button"
                  class="dp-game-mobile-hero-bar__btn dp-game-mobile-hero-bar__btn--toolbar dp-game-mobile-hero-bar__btn--action"
                  :class="{ 'dp-game-mobile-hero-bar__btn--urgent': vm.readyTimeLeft <= 8 }"
                  :aria-label="'打开结算准备抽屉，倒计时 ' + vm.readyTimeLeft + ' 秒'"
                  @click="$store.commit('dpGame/SET_MOBILE_SHEETS', { showMobileActionSheet: true })"
              >
                准备（{{ vm.readyTimeLeft }}s）
              </button>
            </div>
          </div>
          <div
              v-else
              class="dp-game-footer-toolbar dp-game-footer-toolbar--solo"
              aria-label="底栏操作"
          >
            <button
                v-if="vm.isMyTurn"
                type="button"
                class="dp-game-mobile-hero-bar__btn dp-game-mobile-hero-bar__btn--toolbar dp-game-mobile-hero-bar__btn--action"
                :class="{ 'dp-game-mobile-hero-bar__btn--urgent': vm.timeLeft <= 10 }"
                @click="$store.commit('dpGame/SET_MOBILE_SHEETS', { showMobileActionSheet: true })"
            >
              行动（{{ vm.timeLeft }}s）
            </button>
            <button
                v-if="vm.inSettledStage"
                type="button"
                class="dp-game-mobile-hero-bar__btn dp-game-mobile-hero-bar__btn--toolbar dp-game-mobile-hero-bar__btn--action"
                :class="{ 'dp-game-mobile-hero-bar__btn--urgent': vm.readyTimeLeft <= 8 }"
                @click="$store.commit('dpGame/SET_MOBILE_SHEETS', { showMobileActionSheet: true })"
            >
              准备（{{ vm.readyTimeLeft }}s）
            </button>
          </div>
        </div>
      </div>
    </div>

    <div
        v-if="vm.viewerSeatedAtTable && !vm.mobileHeroDockActive && !vm.isOwner"
        class="dp-game-action-hud"
        aria-label="房间聊天"
    >
      <div class="dp-game-footer-left-bar">
        <div class="dp-game-footer-chat-cluster">
          <game-room-chat-panel dock :messages="vm.roomChatMessages" />
          <game-room-chat-bar
              :value="vm.chatInputDraft"
              @input="$store.commit('dpGame/SET_CHAT_DRAFT', $event)"
              @send="vm.sendRoomChat"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import GameRoomChatBar from './GameRoomChatBar.vue'
import GameRoomChatPanel from './GameRoomChatPanel.vue'
import GamePlayerCard from './GamePlayerCard.vue'
import GameActionPanel from './GameActionPanel.vue'
import GameSettledPrepareBar from './GameSettledPrepareBar.vue'

export default {
  name: 'GameHeroDockFooter',
  components: { GameRoomChatBar, GameRoomChatPanel, GamePlayerCard, GameActionPanel, GameSettledPrepareBar },
  inject: ['dpGameView'],
  computed: {
    vm: function () {
      return this.dpGameView
    }
  }
}
</script>
