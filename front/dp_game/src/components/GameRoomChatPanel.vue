<template>
  <div
    class="dp-game-room-chat-panel"
    :class="{
      'dp-game-room-chat-panel--expanded': expanded,
      'dp-game-room-chat-panel--dock': dock
    }"
    aria-label="房间聊天记录"
  >
    <button
      type="button"
      class="dp-game-room-chat-panel__toggle"
      :aria-expanded="expanded ? 'true' : 'false'"
      @click="expanded = !expanded"
    >
      <span class="dp-game-room-chat-panel__toggle-label">
        聊天
        <span v-if="messageCount" class="dp-game-room-chat-panel__count">({{ messageCount }})</span>
      </span>
      <span v-if="!expanded && lastPreview" class="dp-game-room-chat-panel__preview">{{ lastPreview }}</span>
      <i
        class="el-icon-arrow-up dp-game-room-chat-panel__chevron"
        :class="{ 'dp-game-room-chat-panel__chevron--down': expanded }"
        aria-hidden="true"
      />
    </button>
    <div v-show="expanded" class="dp-game-room-chat-panel__list-wrap">
      <div ref="list" class="dp-game-room-chat-panel__list" role="log" aria-live="polite">
        <p v-if="!messageCount" class="dp-game-room-chat-panel__empty">暂无聊天，在下方输入发送</p>
        <div
          v-for="m in messages"
          :key="'rc-' + m.id"
          class="dp-game-room-chat-panel__row"
        >
          <span class="dp-game-room-chat-panel__nick">{{ formatNick(m.nickname) }}：</span>
          <span class="dp-game-room-chat-panel__text">{{ m.text }}</span>
        </div>
        </div>
    </div>
  </div>
</template>

<script>
import { dpDisplayNickname } from '@/utils/dpDisplayNickname'

export default {
  name: 'GameRoomChatPanel',
  props: {
    messages: { type: Array, default: function () { return [] } },
    /** 底栏内联：与输入框、按钮同一行 */
    dock: { type: Boolean, default: false }
  },
  data() {
    return {
      expanded: false
    }
  },
  computed: {
    messageCount() {
      return Array.isArray(this.messages) ? this.messages.length : 0
    },
    lastPreview() {
      if (!this.messageCount) return ''
      var m = this.messages[this.messages.length - 1]
      if (!m) return ''
      var nick = this.formatNick(m.nickname)
      var text = m.text != null ? String(m.text) : ''
      var line = nick + '：' + text
      return line.length > 36 ? line.slice(0, 36) + '…' : line
    }
  },
  watch: {
    messages: {
      handler() {
        if (!this.expanded) return
        var self = this
        this.$nextTick(function () {
          self.scrollToBottom()
        })
      },
      deep: true
    },
    expanded(v) {
      if (!v) return
      var self = this
      this.$nextTick(function () {
        self.scrollToBottom()
      })
    }
  },
  methods: {
    formatNick(name) {
      return dpDisplayNickname(name)
    },
    scrollToBottom() {
      var el = this.$refs.list
      if (!el) return
      el.scrollTop = el.scrollHeight
    }
  }
}
</script>

<style scoped>
.dp-game-room-chat-panel {
  width: 100%;
  margin-bottom: 6px;
}
.dp-game-room-chat-panel__toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 6px 10px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.25);
  color: inherit;
  font-size: 13px;
  cursor: pointer;
  text-align: left;
}
.dp-game-room-chat-panel__toggle-label {
  flex-shrink: 0;
  font-weight: 600;
}
.dp-game-room-chat-panel__count {
  font-weight: 400;
  opacity: 0.85;
}
.dp-game-room-chat-panel__preview {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  opacity: 0.75;
  font-size: 12px;
}
.dp-game-room-chat-panel__chevron {
  margin-left: auto;
  transition: transform 0.2s ease;
}
.dp-game-room-chat-panel__chevron--down {
  transform: rotate(180deg);
}
.dp-game-room-chat-panel__list-wrap {
  margin-top: 6px;
}
.dp-game-room-chat-panel__list {
  max-height: 140px;
  overflow-y: auto;
  padding: 8px;
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.2);
  font-size: 12px;
  line-height: 1.45;
}
.dp-game-room-chat-panel__empty {
  margin: 0;
  text-align: center;
  opacity: 0.7;
}
.dp-game-room-chat-panel__row {
  margin-bottom: 6px;
  word-break: break-word;
}
.dp-game-room-chat-panel__nick {
  font-weight: 600;
  opacity: 0.9;
}
</style>
