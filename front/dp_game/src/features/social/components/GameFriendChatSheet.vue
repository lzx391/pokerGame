<template>
  <game-bottom-sheet
      v-if="visible"
      :title="sheetTitle"
      aria-label="选择好友私信"
      @close="$emit('close')"
  >
    <div
        v-loading="friendsLoading"
        class="dp-friend-chat-sheet"
    >
      <game-friend-chat-picker-content
          :active="visible"
          :my-user-id="myUserId"
          @open-chat="$emit('open-chat', $event)"
      />
    </div>
  </game-bottom-sheet>
</template>

<script>
import GameBottomSheet from '@features/room/components/GameBottomSheet.vue'
import GameFriendChatPickerContent from './GameFriendChatPickerContent.vue'
import { mapState } from 'vuex'

export default {
  name: 'GameFriendChatSheet',
  components: { GameBottomSheet, GameFriendChatPickerContent },
  props: {
    visible: { type: Boolean, default: false },
    myUserId: { type: Number, default: 0 },
    gameUiTheme: { type: String, default: 'default' }
  },
  computed: {
    ...mapState('dpMailbox', ['friendsLoading']),
    sheetTitle() {
      return this.gameUiTheme === 'retro8bit' ? '— FRIEND DM —' : '好友私信'
    }
  }
}
</script>

<style scoped>
.dp-friend-chat-sheet {
  padding: 4px 2px 8px;
  max-width: 100%;
  margin: 0 auto;
}
</style>
