<template>
  <div
      v-if="hasLeader"
      class="dp-retro-stack-leader"
      :class="{ 'dp-retro-stack-leader--fade': animated && leaderKey }"
      :key="animated ? leaderKey : 'static'"
      aria-live="polite"
  >
    <span class="dp-retro-stack-leader__prefix">STACK_LEADER &gt;&gt;</span>
    <span class="dp-retro-stack-leader__body">{{ lineBody }}</span>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import { dpDisplayNickname } from '@shared/utils/dpDisplayNickname'

export default {
  name: 'DpRetroStackLeaderTicker',
  props: {
    animated: { type: Boolean, default: false }
  },
  computed: {
    ...mapGetters('dpGame', ['liveTableChipLeaderNicks', 'liveTableChipLeaderMaxChips']),
    hasLeader: function () {
      return this.liveTableChipLeaderNicks && this.liveTableChipLeaderNicks.length > 0
    },
    leaderKey: function () {
      return (this.liveTableChipLeaderNicks || []).join('|') + ':' + this.liveTableChipLeaderMaxChips
    },
    lineBody: function () {
      var nicks = this.liveTableChipLeaderNicks || []
      var labels = nicks.map(function (n) {
        return dpDisplayNickname(n)
      })
      var chips = this.liveTableChipLeaderMaxChips
      return labels.join(' | ') + ' (' + chips + ')'
    }
  }
}
</script>
