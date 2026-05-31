<template>
  <game-bottom-sheet
      v-if="visible"
      title="邀请好友进房"
      aria-label="向好友发送进房邀请"
      @close="$emit('close')"
  >
    <div
        v-loading="friendsLoading"
        class="dp-invite-friend-sheet"
    >
      <game-invite-friend-content
          :active="visible"
          :room-id="roomId"
          :my-user-id="myUserId"
      />
    </div>
  </game-bottom-sheet>
</template>

<script>
import GameBottomSheet from './GameBottomSheet.vue'
import GameInviteFriendContent from './GameInviteFriendContent.vue'
import { mapState } from 'vuex'

export default {
  name: 'GameInviteFriendSheet',
  components: { GameBottomSheet, GameInviteFriendContent },
  props: {
    visible: { type: Boolean, default: false },
    roomId: { type: String, required: true },
    myUserId: { type: Number, default: 0 }
  },
  computed: {
    ...mapState('dpMailbox', ['friendsLoading'])
  }
}
</script>

<style scoped>
.dp-invite-friend-sheet {
  padding: 4px 2px 8px;
  max-width: 100%;
  margin: 0 auto;
}
</style>
