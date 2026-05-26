<template>
  <el-dialog
    :visible.sync="dialogVisible"
    :width="dialogWidth"
    append-to-body
    custom-class="home-friend-chat-dialog"
    @open="onOpen"
    @close="onClose"
  >
    <template slot="title">
      <div class="friend-chat__header">
        <div class="friend-chat__peer-card" :title="peerDisplayName">
          <dp-user-avatar
            class="friend-chat__peer-avatar"
            :avatar-url="peerAvatarUrl"
            :nickname="peerDisplayName"
            :cache-bust="avatarCacheBustFromUpdatedAt(peerAvatarUpdatedAt)"
            size="md"
          />
          <span
            v-if="showPeerUnreadDot"
            class="friend-chat__peer-unread-dot"
            title="有未读私信"
            aria-label="有未读私信"
          />
        </div>
        <div class="friend-chat__header-text">
          <div class="friend-chat__header-title">{{ dialogTitle }}</div>
          <div class="friend-chat__header-sub">对方在左，你在右</div>
        </div>
      </div>
    </template>
    <div class="friend-chat">
      <p v-if="loading" class="friend-chat__hint">加载中…</p>
      <p v-else-if="loadError" class="friend-chat__hint friend-chat__hint--err">{{ loadError }}</p>
      <div
        ref="scroll"
        class="friend-chat__scroll"
        role="log"
        aria-live="polite"
      >
        <p v-if="!loading && !loadError && !messages.length" class="friend-chat__hint">
          暂无消息，发一句打个招呼吧
        </p>
        <div
          v-for="m in messages"
          :key="'dm-' + m.messageId"
          :class="['friend-chat__row', m.mine ? 'friend-chat__row--mine' : 'friend-chat__row--peer']"
        >
          <div v-if="!m.mine" class="friend-chat__sender">
            <span class="friend-chat__sender-name">{{ peerDisplayName || '对方' }}</span>
            <span
              v-if="showPeerUnreadDot && isLatestPeerMessage(m)"
              class="friend-chat__sender-dot"
              aria-hidden="true"
            />
          </div>
          <div v-else class="friend-chat__sender friend-chat__sender--mine">我</div>
          <div class="friend-chat__bubble">{{ m.body }}</div>
          <div class="friend-chat__time">{{ formatMessageTime(m.createdAt) }}</div>
        </div>
      </div>
      <div class="friend-chat__compose">
        <el-input
          v-model="draft"
          type="textarea"
          :rows="2"
          maxlength="500"
          show-word-limit
          placeholder="输入私信…"
          @keydown.native.ctrl.enter.prevent="sendMessage"
          @keydown.native.meta.enter.prevent="sendMessage"
        />
        <el-button type="primary" size="small" :loading="sending" @click="sendMessage">发送</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script>
import DpUserAvatar from '@/components/DpUserAvatar.vue'
import { avatarCacheBustFromUpdatedAt } from '@/utils/dpAvatarUrl'
import { dpSocialApi } from '@/api/api.dpSocial'
import { dpResultSuccess, dpResultData, dpResultMessage, dpAxiosErrorMessage } from '@/utils/dpApiResult'
import { mapActions, mapState } from 'vuex'

export default {
  name: 'FriendChatDialog',
  components: { DpUserAvatar },
  props: {
    visible: { type: Boolean, default: false },
    peerUserId: { type: [Number, String], default: null },
    peerDisplayName: { type: String, default: '' },
    peerAvatarUrl: { type: String, default: '' },
    peerAvatarUpdatedAt: { type: [Number, String], default: null },
    /** 打开对话时该好友的未读条数（用于头像红点，已读后由父级刷新） */
    peerUnreadCount: { type: Number, default: 0 }
  },
  data() {
    return {
      dialogVisible: false,
      peerUnreadCleared: false,
      messages: [],
      loading: false,
      loadError: '',
      sending: false,
      draft: '',
      /** 用于 SSE 未读递增检测：仅当对端未读变多时才拉消息 */
      ssePeerUnreadBaseline: 0
    }
  },
  computed: {
    ...mapState('dpMailbox', ['friendChatUnreadByUserId']),
    dialogTitle() {
      var name = (this.peerDisplayName || '').trim()
      return name ? '与 ' + name + ' 的对话' : '好友私信'
    },
    peerId() {
      var uid = this.peerUserId != null ? Number(this.peerUserId) : 0
      return isFinite(uid) && uid > 0 ? uid : 0
    },
    peerInitial() {
      var n = (this.peerDisplayName || '').trim()
      if (!n) return '?'
      return n.slice(0, 1).toUpperCase()
    },
    showPeerUnreadDot() {
      if (this.peerUnreadCleared) return false
      var n = Number(this.peerUnreadCount)
      return isFinite(n) && n > 0
    },
    dialogWidth() {
      if (typeof window !== 'undefined' && window.innerWidth < 600) return '100%'
      return '480px'
    },
    peerUnreadFromStore() {
      if (!this.peerId) return 0
      var n = this.friendChatUnreadByUserId[String(this.peerId)]
      return n != null && n > 0 ? n : 0
    }
  },
  created() {
    this.dialogVisible = !!this.visible
  },
  watch: {
    visible(v) {
      this.dialogVisible = !!v
    },
    dialogVisible(v) {
      this.$emit('update:visible', v)
    },
    /** 大厅 SSE notify 更新未读：仅对端未读增加时增量拉取并滚到底 */
    peerUnreadFromStore(n, prev) {
      if (!this.dialogVisible || !this.peerId) return
      var cur = Number(n) || 0
      var base = Number(this.ssePeerUnreadBaseline) || 0
      if (cur <= base) {
        if (cur < base) this.ssePeerUnreadBaseline = cur
        return
      }
      this.ssePeerUnreadBaseline = cur
      this.loadMessages(false, { scrollToBottom: true })
    }
  },
  methods: {
    avatarCacheBustFromUpdatedAt,
    ...mapActions('dpMailbox', [
      'fetchFriendChatUnreadSummary',
      'fetchNotifySummary'
    ]),
    formatMessageTime(raw) {
      if (raw == null || raw === '') return ''
      var s = String(raw)
      var m = s.match(/(\d{2}):(\d{2})/)
      if (m) return m[1] + ':' + m[2]
      return s.length > 16 ? s.slice(0, 16) : s
    },
    isLatestPeerMessage(m) {
      if (!m || m.mine) return false
      for (var i = this.messages.length - 1; i >= 0; i--) {
        var row = this.messages[i]
        if (row && !row.mine) return row.messageId === m.messageId
      }
      return false
    },
    scrollToBottom() {
      var el = this.$refs.scroll
      if (!el) return
      el.scrollTop = el.scrollHeight
    },
    maxMessageId() {
      var max = 0
      for (var i = 0; i < this.messages.length; i++) {
        var id = Number(this.messages[i].messageId)
        if (isFinite(id) && id > max) max = id
      }
      return max
    },
    mergeItems(items) {
      if (!Array.isArray(items) || !items.length) return false
      var prevMax = this.maxMessageId()
      var byId = {}
      for (var i = 0; i < this.messages.length; i++) {
        var m = this.messages[i]
        byId[String(m.messageId)] = m
      }
      for (var j = 0; j < items.length; j++) {
        var row = items[j]
        if (!row || row.messageId == null) continue
        byId[String(row.messageId)] = {
          messageId: row.messageId,
          senderUserId: row.senderUserId,
          recipientUserId: row.recipientUserId,
          body: row.body != null ? String(row.body) : '',
          createdAt: row.createdAt,
          mine: !!row.mine
        }
      }
      var list = Object.keys(byId).map(function (k) {
        return byId[k]
      })
      list.sort(function (a, b) {
        var ia = Number(a.messageId)
        var ib = Number(b.messageId)
        if (isFinite(ia) && isFinite(ib)) return ia - ib
        return String(a.messageId).localeCompare(String(b.messageId))
      })
      this.messages = list
      return this.maxMessageId() > prevMax
    },
    scrollToBottomIfNeeded(shouldScroll) {
      if (!shouldScroll) return
      var self = this
      this.$nextTick(function () {
        self.scrollToBottom()
      })
    },
    async loadMessages(initial, opts) {
      opts = opts || {}
      if (!this.peerId || !this.$http) return
      if (initial) {
        this.loading = true
        this.loadError = ''
      }
      var api = dpSocialApi(this.$http)
      try {
        var res = await api.listFriendMessages(this.peerId, { limit: 50 })
        var body = res.data
        if (!dpResultSuccess(body)) {
          if (initial) this.loadError = dpResultMessage(body)
          return
        }
        var d = dpResultData(body) || {}
        var items = Array.isArray(d.items) ? d.items : []
        if (initial) {
          this.messages = []
        }
        var hadNew = false
        if (items.length) {
          hadNew = this.mergeItems(items)
        }
        var shouldScroll = !!initial || (!!opts.scrollToBottom && hadNew)
        this.scrollToBottomIfNeeded(shouldScroll)
        if (initial || hadNew) {
          await this.markRead()
          this.syncSsePeerUnreadBaseline()
        }
      } catch (e) {
        if (initial) this.loadError = dpAxiosErrorMessage(e, '加载私信失败')
      } finally {
        if (initial) this.loading = false
      }
    },
    syncSsePeerUnreadBaseline() {
      this.ssePeerUnreadBaseline = this.peerUnreadFromStore
    },
    async markRead() {
      var lastId = this.maxMessageId()
      if (!lastId || !this.peerId || !this.$http) return
      var api = dpSocialApi(this.$http)
      try {
        await api.markFriendMessagesRead(this.peerId, lastId)
        this.peerUnreadCleared = true
        this.syncSsePeerUnreadBaseline()
        await this.fetchFriendChatUnreadSummary({ http: this.$http }).catch(function () {})
        await this.fetchNotifySummary({ http: this.$http }).catch(function () {})
      } catch (e) {
        /* ignore */
      }
    },
    async onOpen() {
      this.peerUnreadCleared = false
      this.syncSsePeerUnreadBaseline()
      if (!this.peerId) {
        this.loadError = '无效的好友'
        return
      }
      await this.loadMessages(true)
    },
    onClose() {
      this.ssePeerUnreadBaseline = 0
      this.messages = []
      this.draft = ''
      this.loadError = ''
      this.peerUnreadCleared = false
      this.$emit('closed')
    },
    async sendMessage() {
      var text = (this.draft || '').trim()
      if (!text || !this.peerId || !this.$http) return
      if (text.length > 500) {
        this.$message.warning('最多 500 字')
        return
      }
      this.sending = true
      var api = dpSocialApi(this.$http)
      try {
        var res = await api.sendFriendMessage(this.peerId, text)
        var body = res.data
        if (!dpResultSuccess(body)) {
          this.$message.error(dpResultMessage(body))
          return
        }
        var d = dpResultData(body) || {}
        var mid = d.messageId != null ? d.messageId : Date.now()
        var hadNew = this.mergeItems([
          {
            messageId: mid,
            body: d.body != null && String(d.body).trim() ? String(d.body) : text,
            createdAt: d.createdAt || '',
            mine: true
          }
        ])
        this.scrollToBottomIfNeeded(hadNew)
        this.draft = ''
        await this.markRead()
      } catch (e) {
        this.$message.error(dpAxiosErrorMessage(e, '发送失败'))
      } finally {
        this.sending = false
      }
    }
  }
}
</script>

<style scoped>
.friend-chat__header {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding-right: 28px;
}
.friend-chat__peer-card {
  position: relative;
  flex-shrink: 0;
}
.friend-chat__peer-avatar {
  display: block;
}
.friend-chat__peer-unread-dot {
  position: absolute;
  top: -2px;
  right: -2px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #f56c6c;
  border: 2px solid var(--dp-panel-bg, #fff);
  box-shadow: 0 0 0 1px rgba(245, 108, 108, 0.35);
}
.friend-chat__header-text {
  min-width: 0;
}
.friend-chat__header-title {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--dp-text-primary, #1e2330);
}
.friend-chat__header-sub {
  margin-top: 2px;
  font-size: 12px;
  color: var(--dp-text-muted, #888);
}
.friend-chat {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: min(52dvh, 320px);
}
.friend-chat__hint {
  margin: 0;
  font-size: 13px;
  color: var(--dp-game-muted, #888);
  text-align: center;
}
.friend-chat__hint--err {
  color: #e6a23c;
}
.friend-chat__scroll {
  flex: 1;
  min-height: min(40dvh, 220px);
  max-height: min(50dvh, 360px);
  overflow-y: auto;
  padding: 8px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--dp-subpanel-bg, #f0f2f5) 65%, transparent);
  border: 1px solid var(--dp-subpanel-border, rgba(0, 0, 0, 0.06));
}
.friend-chat__sender {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--dp-text-muted, #666);
}
.friend-chat__sender--mine {
  justify-content: flex-end;
  width: 100%;
  color: color-mix(in srgb, #409eff 70%, var(--dp-text-muted, #888));
}
.friend-chat__sender-name {
  max-width: 12rem;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.friend-chat__sender-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f56c6c;
  flex-shrink: 0;
}
.friend-chat__row {
  display: flex;
  flex-direction: column;
  margin-bottom: 10px;
  max-width: 85%;
}
.friend-chat__row--mine {
  margin-left: auto;
  align-items: flex-end;
}
.friend-chat__row--peer {
  align-items: flex-start;
}
.friend-chat__bubble {
  padding: 8px 12px;
  border-radius: 10px;
  font-size: 14px;
  line-height: 1.4;
  word-break: break-word;
}
.friend-chat__row--mine .friend-chat__bubble {
  background: #409eff;
  color: #fff;
}
.friend-chat__row--peer .friend-chat__bubble {
  background: var(--dp-subpanel-bg, #f4f6f9);
  color: var(--dp-text-primary, #1e2330);
  border: 1px solid var(--dp-subpanel-border, rgba(0, 0, 0, 0.12));
}
.friend-chat__time {
  margin-top: 2px;
  font-size: 11px;
  color: var(--dp-text-muted, #999);
}
.friend-chat__compose {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
</style>
