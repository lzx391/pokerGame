<template>
  <game-bottom-sheet
      v-if="visible && target"
      title="玩家信息"
      aria-label="查看玩家并发送好友申请"
      @close="$emit('close')"
  >
    <div class="dp-player-social-sheet">
      <div class="dp-player-social-sheet__name">{{ displayName }}</div>
      <p class="dp-player-social-sheet__subtitle">游戏玩家</p>

      <!-- 生涯荣誉战绩 -->
      <div v-if="honor" class="dp-player-social-sheet__honor">
        <div class="dp-player-honor__title">生涯战绩</div>
        <div class="dp-player-honor__badges">
          <div class="honor-chip honor-chip--royal">
            <span class="honor-chip__icon">RF</span>
            <span>{{ honor.royalFlushWins || 0 }}</span>
          </div>
          <div class="honor-chip honor-chip--straight">
            <span class="honor-chip__icon">SF</span>
            <span>{{ honor.straightFlushWins || 0 }}</span>
          </div>
          <div class="honor-chip honor-chip--four">
            <span class="honor-chip__icon">4K</span>
            <span>{{ honor.fourOfAKindWins || 0 }}</span>
          </div>
        </div>
        <div class="dp-player-honor__stats">
          <div class="honor-line">
            <span>单局最高净赢</span>
            <strong>{{ honor.largestPotWon || 0 }} BC</strong>
          </div>
          <div class="honor-line">
            <span>单房间最高净赢</span>
            <strong>{{ honor.largestRoomNet || 0 }} BC</strong>
          </div>
          <div class="honor-line">
            <span>生涯总局数</span>
            <strong>{{ honor.totalHandsPlayed || 0 }}</strong>
          </div>
        </div>
      </div>

      <div
          v-if="socialPrimaryIsStaticHint"
          class="dp-player-social-sheet__hint"
      >
        {{ primaryLabel }}
      </div>
      <el-button
          v-else
          type="primary"
          class="dp-player-social-sheet__btn"
          :disabled="primaryDisabled"
          :loading="sending"
          @click="onSendRequest"
      >
        {{ primaryLabel }}
      </el-button>
      <el-button
          type="default"
          plain
          class="dp-player-social-sheet__btn dp-player-social-sheet__btn--secondary"
          @click="onViewHandHistoryWithOpponent"
      >
        查看与该玩家的历史对局
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
      tip: '',
      honor: null
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
    /** 禁用态主按钮在 Element 里会非常浅，改用普通文本块以保持可读 */
    socialPrimaryIsStaticHint() {
      return this.isFriend || this.sentOk
    },
    primaryDisabled() {
      return this.friendsLoading || this.socialPrimaryIsStaticHint || !!this.tip
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
      this.honor = null
      this.loadFriends()
      this.loadHonor()
    },
    async loadHonor() {
      if (!this.target) return
      var uid = Number(this.target.userId)
      if (!uid || uid <= 0 || isNaN(uid)) return
      try {
        var res = await this.$http.get('/dpUser/stats/' + uid)
        if (dpResultSuccess(res.data)) {
          this.honor = (res.data.data && res.data.data.honor) || null
        }
      } catch (e) {
        // 静默：战绩加载不影响主要功能
      }
    },
    async loadFriends() {
      try {
        await this.$store.dispatch('dpMailbox/fetchFriends', { http: this.$http })
      } catch (e) {
        /* 静默：仍可尝试发申请 */
      }
    },
    onViewHandHistoryWithOpponent() {
      if (!this.target) return
      var uid = Number(this.target.userId)
      if (!uid || uid <= 0 || isNaN(uid)) return
      this.$emit('view-hand-history-with-opponent', {
        userId: uid,
        displayName: this.displayName || this.target.nickname
      })
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
.dp-player-social-sheet__subtitle {
  font-size: 13px;
  color: var(--dp-text-secondary, #5a5248);
  margin: 0 0 12px;
}
.dp-player-social-sheet__hint {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 12px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 600;
  text-align: center;
  color: var(--dp-text-primary, #1a1a1a);
  background: rgba(136, 136, 136, 0.12);
  border: 1px solid rgba(136, 136, 136, 0.28);
  margin-bottom: 2px;
}

@supports (color: color-mix(in srgb, red, blue)) {
  .dp-player-social-sheet__hint {
    background: color-mix(in srgb, var(--dp-text-muted, #888) 14%, transparent);
    border: 1px solid color-mix(in srgb, var(--dp-text-muted, #888) 38%, transparent);
  }
}
.dp-player-social-sheet__btn {
  width: 100%;
}
.dp-player-social-sheet__btn--secondary {
  margin-top: 10px;
}
.dp-player-social-sheet__tip {
  margin: 12px 0 0;
  font-size: 13px;
  color: var(--dp-text-muted, #888);
  line-height: 1.45;
}

/* ---- 生涯荣誉 ---- */
.dp-player-social-sheet__honor {
  background: var(--dp-subpanel-bg, #fafafa);
  border-radius: 8px;
  padding: 10px 12px;
  border: 1px solid var(--dp-subpanel-border, #e8e8e8);
  margin-bottom: 12px;
}
.dp-player-honor__title {
  font-size: 12px;
  color: var(--dp-text-muted, #999);
  font-weight: 600;
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.dp-player-honor__badges {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
}
.honor-chip {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding: 6px 8px;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 700;
  border: 1px solid;
}
.honor-chip--royal {
  background: color-mix(in srgb, var(--dp-warning, #faad14) 12%, transparent);
  border-color: var(--dp-warning, #faad14);
  color: var(--dp-warning, #b45309);
}
.honor-chip--straight {
  background: color-mix(in srgb, var(--dp-accent, #1890ff) 12%, transparent);
  border-color: var(--dp-accent, #1890ff);
  color: var(--dp-accent, #1565c0);
}
.honor-chip--four {
  background: color-mix(in srgb, var(--dp-danger, #ff4d4f) 12%, transparent);
  border-color: var(--dp-danger, #ff4d4f);
  color: var(--dp-danger, #c62828);
}
.honor-chip__icon {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.5px;
}
.dp-player-honor__stats {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.honor-line {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--dp-text-muted, #999);
}
.honor-line strong {
  color: var(--dp-text-primary, #333);
  font-weight: 600;
}
</style>
