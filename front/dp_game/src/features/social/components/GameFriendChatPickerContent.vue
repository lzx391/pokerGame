<template>
  <div class="dp-friend-chat-picker">
    <p class="dp-friend-chat-picker__hint">
      选择好友开始私信对话；未读消息会实时更新。
    </p>
    <p
        v-if="!friendsLoading && !selectableFriends.length"
        class="dp-friend-chat-picker__empty"
    >
      暂无好友可私信（需已成功添加好友）。
    </p>
    <ul
        v-else-if="selectableFriends.length"
        class="dp-friend-chat-picker__list"
    >
      <li
          v-for="f in selectableFriends"
          :key="f.userId"
          :class="['dp-friend-chat-picker__row', friendPresenceClass(f)]"
      >
        <button
            type="button"
            class="dp-friend-chat-picker__left"
            :aria-label="'查看 ' + friendLabel(f) + ' 的资料'"
            @click="onFriendProfileClick(f)"
        >
          <dp-user-avatar
            :avatar-url="f.avatarUrl"
            :nickname="friendLabel(f)"
            :cache-bust="avatarCacheBustFromUpdatedAt(f.avatarUpdatedAt)"
            size="sm"
          />
          <div class="dp-friend-chat-picker__meta">
            <span
                class="dp-friend-chat-picker__name"
                :title="friendLabel(f)"
            >
              {{ friendLabel(f) }}
              <span
                  v-if="friendUnreadFor(f.userId)"
                  class="dp-friend-chat-picker__unread-dot"
                  title="有未读私信"
                  aria-label="有未读私信"
              />
            </span>
            <span
                v-if="friendPresenceLine(f)"
                class="dp-friend-chat-picker__presence"
            >{{ friendPresenceLine(f) }}</span>
          </div>
        </button>
        <el-badge
            :value="friendUnreadFor(f.userId)"
            :hidden="!friendUnreadFor(f.userId)"
            :max="99"
            class="dp-friend-chat-picker__badge"
        >
          <el-button
              type="primary"
              size="small"
              class="dp-friend-chat-picker__dm-btn"
              @click="openChat(f)"
          >
            对话
          </el-button>
        </el-badge>
      </li>
    </ul>
  </div>
</template>

<script>
import DpUserAvatar from '@features/user/components/DpUserAvatar.vue'
import { mapGetters, mapState } from 'vuex'
import { dpFriendPresenceRowClass, dpFriendPresenceStatusText } from '@features/social/utils/dpFriendPresence'
import { dpSocialDisplayNickname } from '@features/social/utils/dpSocialDisplayName'
import { dpAxiosErrorMessage } from '@shared/utils/dpApiResult'
import { avatarCacheBustFromUpdatedAt } from '@features/user/utils/dpAvatarUrl'

export default {
  name: 'GameFriendChatPickerContent',
  components: { DpUserAvatar },
  inject: ['dpGameView'],
  props: {
    active: { type: Boolean, default: false },
    myUserId: { type: Number, default: 0 }
  },
  computed: {
    ...mapState('dpMailbox', ['friends', 'friendsLoading']),
    ...mapGetters('dpMailbox', ['friendUnreadForUser']),
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
    active: {
      immediate: true,
      handler(v) {
        if (v) this.opened()
      }
    }
  },
  methods: {
    avatarCacheBustFromUpdatedAt,
    friendPresenceClass(f) {
      return dpFriendPresenceRowClass(f)
    },
    friendPresenceLine(f) {
      return dpFriendPresenceStatusText(f)
    },
    friendLabel(f) {
      return dpSocialDisplayNickname(f && f.nickname, f && f.userId, '好友')
    },
    friendUnreadFor(userId) {
      return this.friendUnreadForUser(userId)
    },
    async opened() {
      try {
        var r = await this.$store.dispatch('dpMailbox/fetchAllFriendsForInvite', {
          http: this.$http
        })
        if (!r || !r.ok) {
          this.$message.error((r && r.message) || '加载好友列表失败')
        }
      } catch (e) {
        this.$message.error(dpAxiosErrorMessage(e, '加载好友列表失败'))
      }
    },
    onFriendProfileClick(f) {
      if (!f) return
      var vm = this.dpGameView
      if (!vm || typeof vm.openPlayerSocialProfile !== 'function') return
      var nickname = f.nickname
      if (!nickname) nickname = this.friendLabel(f)
      vm.openPlayerSocialProfile({
        nickname: nickname,
        userId: f.userId
      })
    },
    openChat(f) {
      if (!f) return
      this.$emit('open-chat', f)
    }
  }
}
</script>

<style scoped>
.dp-friend-chat-picker {
  padding: 4px 2px 8px;
  max-width: 100%;
  margin: 0 auto;
}

.dp-friend-chat-picker__hint {
  font-size: 13px;
  color: var(--dp-text-muted);
  line-height: 1.45;
  margin: 0 0 14px;
}

.dp-friend-chat-picker__empty {
  font-size: 13px;
  color: var(--dp-text-muted);
  line-height: 1.5;
  margin: 8px 0 0;
  padding: 12px 4px;
  text-align: center;
}

.dp-friend-chat-picker__list {
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

.dp-friend-chat-picker__row {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding: 8px 12px;
  box-sizing: border-box;
  border-bottom: 1px solid var(--dp-panel-border, rgba(255, 255, 255, 0.08));
}

.dp-friend-chat-picker__row:last-child {
  border-bottom: none;
}

.dp-friend-chat-picker__left {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 10px;
  margin: 0;
  padding: 0;
  border: none;
  background: transparent;
  font: inherit;
  color: inherit;
  text-align: left;
  cursor: pointer;
  border-radius: 6px;
  transition: background 0.15s ease;
}

.dp-friend-chat-picker__left:hover,
.dp-friend-chat-picker__left:focus-visible {
  background: var(--dp-input-bg, rgba(255, 255, 255, 0.06));
  outline: none;
}

.dp-friend-chat-picker__meta {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.dp-friend-chat-picker__name {
  width: 100%;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: var(--dp-text-primary, #e8e8e8);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.dp-friend-chat-picker__unread-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f56c6c;
  flex-shrink: 0;
}

.dp-friend-chat-picker__presence {
  font-size: 12px;
  font-weight: 600;
  line-height: 1.35;
  letter-spacing: 0.02em;
}

.dp-friend-chat-picker__row.dp-friend-row--presence-idle .dp-friend-chat-picker__presence {
  color: #529b2e;
}

.dp-friend-chat-picker__row.dp-friend-row--presence-ingame .dp-friend-chat-picker__presence {
  color: #dd6161;
}

.dp-friend-chat-picker__row.dp-friend-row--presence-offline .dp-friend-chat-picker__presence {
  color: #9a9ea4;
}

.dp-friend-chat-picker__badge {
  flex-shrink: 0;
}

.dp-friend-chat-picker__dm-btn {
  min-width: 64px;
  padding-left: 12px;
  padding-right: 12px;
}
</style>
