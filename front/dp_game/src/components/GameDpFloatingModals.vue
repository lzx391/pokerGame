<template>
  <div>
    <game-play-guide-modal
        :visible="vm.showPlayGuideModal"
        :active-tab="vm.playGuideTab"
        :items="vm.handRankReference"
        @close="$store.commit('dpGame/SET_MODAL', { showPlayGuideModal: false })"
        @tab-change="$store.commit('dpGame/SET_MODAL', { playGuideTab: $event })"
    />
    <game-spectator-modal
        :visible="vm.showSpectatorModal"
        :spectators="vm.spectators"
        :players="vm.players"
        @close="$store.commit('dpGame/SET_MODAL', { showSpectatorModal: false })"
    />
    <game-wait-next-hand-modal
        :visible="vm.showWaitNextHandModal"
        :wait-next-hand="vm.waitNextHand"
        @close="$store.commit('dpGame/SET_MODAL', { showWaitNextHandModal: false })"
    />
    <game-hand-history-modal
        :visible="vm.showHandHistoryModal"
        :game-ui-theme="vm.effectiveThemeForCss"
        list-mode="mine"
        @close="$store.commit('dpGame/SET_MODAL', { showHandHistoryModal: false })"
    />
    <game-hand-history-modal
        :visible="vm.showOpponentHandHistoryModal"
        :game-ui-theme="vm.effectiveThemeForCss"
        list-mode="withOpponent"
        :other-user-id="vm.opponentHandHistoryOtherUserId"
        :opponent-display-name="vm.opponentHandHistoryDisplayName"
        @close="
          $store.commit('dpGame/SET_MODAL', { showOpponentHandHistoryModal: false })
        "
    />
    <game-music-box-modal
        :visible="vm.showMusicBoxModal"
        :game-ui-theme="vm.effectiveThemeForCss"
        :tracks="vm.musicTracks"
        :list-loading="vm.musicTracksLoading"
        :list-error="vm.musicTracksError"
        :room-music="vm.roomMusicState"
        @close="$store.commit('dpGame/SET_MODAL', { showMusicBoxModal: false })"
        @sync="vm.sendRoomMusicSync"
    />
  </div>
</template>

<script>
import GamePlayGuideModal from './GamePlayGuideModal.vue'
import GameSpectatorModal from './GameSpectatorModal.vue'
import GameWaitNextHandModal from './GameWaitNextHandModal.vue'
import GameHandHistoryModal from './GameHandHistoryModal.vue'
import GameMusicBoxModal from './GameMusicBoxModal.vue'

export default {
  name: 'GameDpFloatingModals',
  components: {
    GamePlayGuideModal,
    GameSpectatorModal,
    GameWaitNextHandModal,
    GameHandHistoryModal,
    GameMusicBoxModal
  },
  inject: ['dpGameView'],
  computed: {
    vm: function () {
      return this.dpGameView
    }
  }
}
</script>
