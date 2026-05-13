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
      <p class="dp-invite-friend-sheet__hint">
        将向好友发送 60 秒内有效的进房邀请；对方在大厅邮箱接受后可免密码以观众身份进入本房间。
      </p>
      <p
          v-if="!friendsLoading && !selectableFriends.length"
          class="dp-invite-friend-sheet__empty"
      >
        暂无好友可邀请（需已成功添加好友）。
      </p>
      <ul
          v-else-if="selectableFriends.length"
          class="dp-invite-friend-sheet__list"
      >
        <li
            v-for="f in selectableFriends"
            :key="f.userId"
            :class="['dp-invite-friend-sheet__row', friendInvitePresenceClass(f)]"
        >
          <div class="dp-invite-friend-sheet__left">
            <span
                class="dp-invite-friend-sheet__name"
                :title="inviteOptionLabel(f)"
            >
              {{ inviteOptionLabel(f) }}
            </span>
            <span
                v-if="friendInvitePresenceLine(f)"
                class="dp-invite-friend-sheet__presence"
            >{{ friendInvitePresenceLine(f) }}</span>
          </div>
          <el-button
              type="primary"
              size="small"
              class="dp-invite-friend-sheet__invite-btn"
              :loading="inviteRowSending(f)"
              :disabled="inviteRowDisabled"
              @click="inviteFriend(f)"
          >
            邀请
          </el-button>
        </li>
      </ul>
    </div>
  </game-bottom-sheet>
</template>

<script>
import GameBottomSheet from './GameBottomSheet.vue'
import { mapState } from 'vuex'
import { dpFriendPresenceRowClass, dpFriendPresenceStatusText } from '@/utils/dpFriendPresence'
import { dpSocialDisplayNickname } from '../utils/dpSocialDisplayName'
import { dpResultSuccess, dpResultMessage, dpAxiosErrorMessage } from '../utils/dpApiResult'

export default {
  name: 'GameInviteFriendSheet',
  components: { GameBottomSheet },
  props: {
    visible: { type: Boolean, default: false },
    roomId: { type: String, required: true },
    myUserId: { type: Number, default: 0 }
  },
  data() {
    return {
      /** 正在进行邀请的好友 userId；有值时禁用所有邀请按钮，当前行展示 loading */
      sendingUserId: null
    }
  },
  computed: {
    ...mapState('dpMailbox', ['friends', 'friendsLoading']),
    selectableFriends() {
      var me = Number(this.myUserId)
      return (this.friends || []).filter(function (f) {
        if (!f) return false
        var id = Number(f.userId)
        if (!id || id <= 0) return false
        if (me > 0 && id === me) return false
        return true
      })
    },
    inviteRowDisabled() {
      return this.friendsLoading || !this.roomId || this.sendingUserId !== null
    }
  },
  watch: {
    visible: {
      immediate: true,
      handler(v) {
        if (v) this.opened()
      }
    }
  },
  methods: {
    friendInvitePresenceClass(f) {
      return dpFriendPresenceRowClass(f)
    },
    friendInvitePresenceLine(f) {
      return dpFriendPresenceStatusText(f)
    },
    inviteOptionLabel(f) {
      return dpSocialDisplayNickname(f && f.nickname, f && f.userId, '好友')
    },
    inviteRowSending(f) {
      var id = f && Number(f.userId)
      return id > 0 && this.sendingUserId === id
    },
    async opened() {
      this.sendingUserId = null
      try {
        var r = await this.$store.dispatch('dpMailbox/fetchFriends', { http: this.$http })
        var n = (this.friends && this.friends.length) || 0
        if (r && r.ok) {
          console.info('[dp][invite] mailbox/fetchFriends OK, friends count=', n, '(same GET /dp/friends as 好友列表)')
        } else {
          console.warn('[dp][invite] fetchFriends failed:', r && r.message)
          this.$message.error((r && r.message) || '加载好友列表失败')
        }
      } catch (e) {
        console.error('[dp][invite] fetchFriends', e)
        this.$message.error(dpAxiosErrorMessage(e, '加载好友列表失败'))
      }
    },
    async inviteFriend(f) {
      if (!f || !this.roomId || this.inviteRowDisabled) return
      var inviteeUserId = Number(f.userId)
      if (!inviteeUserId || inviteeUserId <= 0) return

      this.sendingUserId = inviteeUserId
      try {
        var res = await this.$http.post('/dp/room-invites', {
          roomId: this.roomId,
          inviteeUserId
        })
        if (dpResultSuccess(res.data)) {
          this.$message.success('已发送')
          return
        }
        var msg = dpResultMessage(res.data)
        if (msg.indexOf('好友') !== -1 || msg.indexOf('互为') !== -1) {
          this.$message.warning(msg)
        } else {
          this.$message.error(msg)
        }
      } catch (err) {
        var st = err && err.response && err.response.status
        if (st === 403) {
          var body = err && err.response && err.response.data
          var msg403 = body ? dpResultMessage(body) : ''
          this.$message.warning(
            (msg403 && msg403 !== '请求失败') ? msg403 : '当前无权发起进房邀请（服务端可能仅限房主）；若规则放宽可重试。'
          )
        } else {
          this.$message.error(dpAxiosErrorMessage(err, '仅互为好友可邀请进房'))
        }
      } finally {
        this.sendingUserId = null
      }
    }
  }
}
</script>

<style scoped>
.dp-invite-friend-sheet {
  padding: 4px 2px 8px;
  max-width: 100%;
  margin: 0 auto;
}

.dp-invite-friend-sheet__hint {
  font-size: 13px;
  color: var(--dp-text-muted);
  line-height: 1.45;
  margin: 0 0 14px;
}

.dp-invite-friend-sheet__empty {
  font-size: 13px;
  color: var(--dp-text-muted);
  line-height: 1.5;
  margin: 8px 0 0;
  padding: 12px 4px;
  text-align: center;
}

.dp-invite-friend-sheet__list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: min(42vh, 312px);
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
  border-radius: 10px;
  border: 1px solid var(--dp-panel-border, rgba(255, 255, 255, 0.12));
  background: var(--dp-input-bg, rgba(255, 255, 255, 0.04));
}

.dp-invite-friend-sheet__row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding: 8px 12px;
  box-sizing: border-box;
  border-bottom: 1px solid var(--dp-panel-border, rgba(255, 255, 255, 0.08));
}

.dp-invite-friend-sheet__row:last-child {
  border-bottom: none;
}

.dp-invite-friend-sheet__left {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.dp-invite-friend-sheet__name {
  width: 100%;
  font-size: 14px;
  color: var(--dp-text-primary, #e8e8e8);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dp-invite-friend-sheet__presence {
  font-size: 12px;
  font-weight: 600;
  line-height: 1.35;
  letter-spacing: 0.02em;
}

.dp-invite-friend-sheet__row.dp-friend-row--presence-idle .dp-invite-friend-sheet__presence {
  color: #529b2e;
}

.dp-invite-friend-sheet__row.dp-friend-row--presence-ingame .dp-invite-friend-sheet__presence {
  color: #dd6161;
}

.dp-invite-friend-sheet__row.dp-friend-row--presence-offline .dp-invite-friend-sheet__presence {
  color: #9a9ea4;
}

.dp-invite-friend-sheet__invite-btn {
  flex-shrink: 0;
  min-width: 64px;
  padding-left: 12px;
  padding-right: 12px;
}
</style>
