<template>
  <div v-if="visible" class="hand-rank-modal-mask" @click="$emit('close')">
    <div
        class="hand-rank-modal hand-rank-modal--hand-history"
        :data-dp-game-theme="gameUiTheme"
        role="dialog"
        aria-modal="true"
        aria-labelledby="dp-hand-history-modal-title"
        @click.stop
    >
      <div class="dp-game-dialog__head">
        <span id="dp-hand-history-modal-title" class="dp-game-dialog__title">{{ modalTitle }}</span>
        <button
            type="button"
            class="dp-game-dialog__close"
            aria-label="关闭"
            @click="$emit('close')"
        >
          ×
        </button>
      </div>
      <div class="dp-game-dialog__body dp-game-dialog__body--hand-history">
        <hand-history
            v-if="view === 'list'"
            :key="'hh-' + listMode + '-' + (otherUserId || 'me')"
            embedded
            :list-mode="listMode"
            :other-user-id="otherUserId"
            @close="$emit('close')"
            @view-detail="onViewDetail"
        />
        <hand-history-detail
            v-else
            :hand-history-id="detailId"
            embedded
            @back="view = 'list'"
        />
      </div>
    </div>
  </div>
</template>

<script>
import HandHistory from './HandHistory.vue'
import HandHistoryDetail from './HandHistoryDetail.vue'

export default {
  name: 'GameHandHistoryModal',
  components: { HandHistory, HandHistoryDetail },
  props: {
    visible: { type: Boolean, default: false },
    /** 与对局页 `game.vue` 的 `gameUiTheme` 一致，供弹层内列表/详情跟随当前界面主题 */
    gameUiTheme: { type: String, default: 'default' },
    /** mine：GET /dpHandHistory/list；withOpponent：与同局真人双方的共同列表 */
    listMode: {
      type: String,
      default: 'mine',
      validator(v) {
        return v === 'mine' || v === 'withOpponent'
      }
    },
    otherUserId: { type: Number, default: null },
    /** 列表态弹层标题用，须与卡片/dpDisplayNickname 一致 */
    opponentDisplayName: { type: String, default: '' }
  },
  data() {
    return {
      view: 'list',
      detailId: null
    }
  },
  computed: {
    modalTitle() {
      if (this.view === 'detail') return '牌谱详情'
      if (
        this.listMode === 'withOpponent'
        && this.opponentDisplayName
      ) {
        return '与 ' + this.opponentDisplayName + ' 的历史对局'
      }
      return '历史对局'
    }
  },
  watch: {
    visible(v) {
      if (v) {
        this.view = 'list'
        this.detailId = null
      }
    }
  },
  methods: {
    onViewDetail(id) {
      this.detailId = id
      this.view = 'detail'
    }
  }
}
</script>

<style src="../styles/dp-game-modals.css"></style>
<style scoped>
.hand-rank-modal--hand-history {
  max-width: min(920px, 96vw);
  max-height: min(92vh, 900px);
  width: 100%;
}

.dp-game-dialog__body--hand-history {
  max-height: calc(92vh - 56px);
  overflow: auto;
  -webkit-overflow-scrolling: touch;
  background: var(--dp-game-bg, #f0f2f5);
  color: var(--dp-text-primary, #1a2332);
  font-family: var(--dp-font-ui, inherit);
  padding: 0;
}
</style>
