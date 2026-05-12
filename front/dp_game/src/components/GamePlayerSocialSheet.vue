<template>
  <game-bottom-sheet
      v-if="visible && target"
      title="玩家信息"
      aria-label="查看玩家并发送好友申请"
      @close="$emit('close')"
  >
    <div class="dp-player-social-sheet">
      <div class="dp-player-social-sheet__name">{{ displayName }}</div>
      <p class="dp-player-social-sheet__placeholder">游戏玩家</p>
      <el-button
          type="primary"
          class="dp-player-social-sheet__btn"
          :disabled="primaryDisabled"
          :loading="sending"
          @click="onSendRequest"
      >
        {{ primaryLabel }}
      </el-button>
      <p v-if="tip" class="dp-player-social-sheet__tip">{{ tip }}</p>
    </div>
  </game-bottom-sheet>
</template>

<script>
import GameBottomSheet from './GameBottomSheet.vue'
import { mapState } from 'vuex'
import { dpDisplayNickname } from '../utils/dpDisplayNickname'
import { dpResultSuccess, dpResultMessage, dpAxiosErrorMessage } from '../utils/dpApiResult'

export default {
  name: 'GamePlayerSocialSheet',
  components: { GameBottomSheet },
  props: {
    visible: { type: Boolean, default: false },
    /** @type {{ nickname: string, userId: number }} */
    target: {
      type: Object,
      default: null
    }
  },
  data() {
    return {
      sending: false,
      sentOk: false,
      tip: ''
    }
  },
  computed: {
    ...mapState('dpMailbox', ['friends', 'friendsLoading']),
    displayName() {
      if (!this.target || !this.target.nickname) return ''
      return dpDisplayNickname(this.target)
    },
    friendIds() {
      return (this.friends || []).map(function (f) {
        return Number(f.userId)
      }).filter(function (n) {
        return n > 0
      })
    },
    isFriend() {
      if (!this.target) return false
      return this.friendIds.indexOf(Number(this.target.userId)) !== -1
    },
    primaryLabel() {
      if (this.isFriend) return '已是好友'
      if (this.sentOk) return '申请已发送'
      return '发送好友申请'
    },
    primaryDisabled() {
      return this.friendsLoading || this.isFriend || this.sentOk || !!this.tip
    }
  },
  watch: {
    visible: {
      immediate: true,
      handler(v) {
        if (v && this.target) this.refresh()
      }
    },
    target() {
      if (this.visible && this.target) this.refresh()
    }
  },
  methods: {
    refresh() {
      this.tip = ''
      this.sentOk = false
      this.loadFriends()
    },
    async loadFriends() {
      try {
        await this.$store.dispatch('dpMailbox/fetchFriends', { http: this.$http })
      } catch (e) {
        /* 静默：仍可尝试发申请 */
      }
    },
    async onSendRequest() {
      if (!this.target || this.primaryDisabled) return
      var uid = Number(this.target.userId)
      if (!uid || uid <= 0) return
      this.sending = true
      this.tip = ''
      try {
        var res = await this.$http.post('/dp/friends/requests', { toUserId: uid })
        if (dpResultSuccess(res.data)) {
          this.sentOk = true
          this.$message.success((res.data.data && res.data.data.message) || '已发送')
          return
        }
        var msg = dpResultMessage(res.data)
        if (msg.indexOf('对方已向您发来申请') !== -1) {
          this.tip = msg + '，请在大厅邮箱中处理。'
          return
        }
        if (msg.indexOf('已是好友') !== -1) {
          await this.loadFriends()
          this.$message.info(msg)
          return
        }
        if (msg.indexOf('申请已存在') !== -1) {
          this.sentOk = true
          this.$message.success(msg)
          return
        }
        this.$message.error(msg)
      } catch (err) {
        this.$message.error(dpAxiosErrorMessage(err, '无法发送好友申请，请稍后重试'))
      } finally {
        this.sending = false
      }
    }
  }
}
</script>

<style scoped>
.dp-player-social-sheet {
  padding: 4px 2px 8px;
}
.dp-player-social-sheet__name {
  font-size: 17px;
  font-weight: 600;
  color: var(--dp-text-strong, #1a1a1a);
  margin-bottom: 6px;
}
.dp-player-social-sheet__placeholder {
  font-size: 13px;
  color: var(--dp-text-muted, #888);
  margin: 0 0 16px;
}
.dp-player-social-sheet__btn {
  width: 100%;
}
.dp-player-social-sheet__tip {
  margin: 12px 0 0;
  font-size: 13px;
  color: var(--dp-text-muted, #888);
  line-height: 1.45;
}
</style>
