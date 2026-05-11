<template>
  <game-bottom-sheet
      v-if="visible"
      title="邀请好友进房"
      aria-label="向好友发送进房邀请"
      :wide="true"
      @close="$emit('close')"
  >
    <div
        v-loading="friendsLoading"
        class="dp-invite-friend-sheet"
    >
      <p class="dp-invite-friend-sheet__hint">
        将向好友发送 60 秒内有效的进房邀请；对方在大厅邮箱接受后可免密码以观众身份进入本房间。
      </p>
      <el-select
          v-model="selectedUserId"
          class="dp-invite-friend-sheet__select"
          filterable
          placeholder="选择好友"
          :disabled="friendsLoading || !selectableFriends.length"
      >
        <el-option
            v-for="f in selectableFriends"
            :key="f.userId"
            :label="inviteOptionLabel(f)"
            :value="Number(f.userId)"
        />
      </el-select>
      <el-button
          type="primary"
          class="dp-invite-friend-sheet__btn"
          :disabled="!selectedUserId || sending"
          :loading="sending"
          @click="onInvite"
      >
        发送邀请
      </el-button>
    </div>
  </game-bottom-sheet>
</template>

<script>
import GameBottomSheet from './GameBottomSheet.vue'
import { mapState } from 'vuex'
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
      selectedUserId: null,
      sending: false
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
    }
  },
  watch: {
    visible: {
      immediate: true,
      handler(v) {
        if (v) this.opened()
      }
    },
    friends: {
      deep: true,
      handler() {
        if (!this.visible || this.selectedUserId == null) return
        var sel = Number(this.selectedUserId)
        var ok = this.selectableFriends.some(function (f) {
          return Number(f.userId) === sel
        })
        if (!ok) this.selectedUserId = null
      }
    }
  },
  methods: {
    inviteOptionLabel(f) {
      return dpSocialDisplayNickname(f && f.nickname, f && f.userId, '好友')
    },
    async opened() {
      this.selectedUserId = null
      this.sending = false
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
    async onInvite() {
      if (!this.selectedUserId || !this.roomId) return
      this.sending = true
      try {
        var res = await this.$http.post('/dp/room-invites', {
          roomId: this.roomId,
          inviteeUserId: this.selectedUserId
        })
        if (dpResultSuccess(res.data)) {
          this.$message.success('已发送')
          this.$emit('close')
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
        this.sending = false
      }
    }
  }
}
</script>

<style scoped>
.dp-invite-friend-sheet {
  padding: 4px 2px 8px;
}
.dp-invite-friend-sheet__hint {
  font-size: 13px;
  color: var(--dp-text-muted);
  line-height: 1.45;
  margin: 0 0 14px;
}
.dp-invite-friend-sheet__select {
  width: 100%;
  margin-bottom: 14px;
}
.dp-invite-friend-sheet__btn {
  width: 100%;
}
</style>
