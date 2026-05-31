<template>
  <div
    class="dp-game-room-chat-panel"
    :class="{
      'dp-game-room-chat-panel--expanded': panelExpandedClass,
      'dp-game-room-chat-panel--dock': dock,
      'dp-game-room-chat-panel--retro-reveal': useRetroChatReveal
    }"
    aria-label="房间聊天记录"
  >
    <button
      ref="guideChatToggle"
      type="button"
      class="dp-game-room-chat-panel__toggle"
      :aria-expanded="ariaExpanded"
      @click="onToggleClick"
    >
      <span class="dp-game-room-chat-panel__toggle-label">
        聊天
        <span v-if="messageCount" class="dp-game-room-chat-panel__count">({{ messageCount }})</span>
      </span>
      <span v-if="!isVisuallyOpen && lastPreview" class="dp-game-room-chat-panel__preview">{{ lastPreview }}</span>
      <i
        class="el-icon-arrow-up dp-game-room-chat-panel__chevron"
        :class="{ 'dp-game-room-chat-panel__chevron--down': isVisuallyOpen }"
        aria-hidden="true"
      />
    </button>

    <!-- retro8bit + wide viewport + motion: flip board + ECG reveal -->
    <div
      v-if="useRetroChatReveal"
      v-show="sceneVisible"
      class="dp-game-room-chat-panel__board-scene"
    >
      <div
        class="dp-game-room-chat-panel__board"
        :class="boardPhaseClass"
        @animationend="onBoardAnimEnd"
      >
        <div class="dp-game-room-chat-panel__ecg" aria-hidden="true">
          <svg
            class="dp-game-room-chat-panel__ecg-svg"
            viewBox="0 0 200 24"
            preserveAspectRatio="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <polyline
              class="dp-game-room-chat-panel__ecg-line"
              points="0,12 8,12 12,8 16,16 22,12 28,12 32,6 38,18 44,12 50,12 54,10 60,14 66,12 72,4 78,20 84,12 90,12 96,8 102,16 108,12 114,12 120,6 126,18 132,12 138,12 144,10 150,14 156,12 162,8 168,16 174,12 180,12 186,6 192,18 200,12"
              fill="none"
              stroke="currentColor"
              stroke-width="1.5"
              vector-effect="non-scaling-stroke"
              @animationend="onEcgAnimEnd"
            />
          </svg>
        </div>
        <div ref="guideChatListWrap" class="dp-game-room-chat-panel__list-wrap">
          <div
            ref="list"
            class="dp-game-room-chat-panel__list"
            role="log"
            aria-live="polite"
          >
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
    </div>

    <!-- legacy popover (non-retro, narrow viewport, eco / PRM) -->
    <div
      v-else
      ref="guideChatListWrap"
      v-show="expanded"
      class="dp-game-room-chat-panel__list-wrap"
    >
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

/** @typedef {'idle' | 'flipping-up' | 'ecg-sweep' | 'revealed' | 'collapsing-down'} RevealPhase */

export default {
  name: 'GameRoomChatPanel',
  inject: {
    dpGameView: { default: null }
  },
  props: {
    messages: { type: Array, default: function () { return [] } },
    /** 底栏内联：与输入框、按钮同一行 */
    dock: { type: Boolean, default: false }
  },
  data() {
    return {
      expanded: false,
      /** @type {RevealPhase} */
      revealPhase: 'idle',
      viewportWidth: typeof window !== 'undefined' ? window.innerWidth : 1024,
      prefersReducedMotion: false,
      resizeTimer: null,
      prmMedia: null
    }
  },
  computed: {
    gameUiTheme() {
      if (this.dpGameView && this.dpGameView.gameUiTheme) {
        return this.dpGameView.gameUiTheme
      }
      return 'default'
    },
    ecoMode() {
      return !!(this.dpGameView && this.dpGameView.ecoMode)
    },
    useRetroChatReveal() {
      return this.dock
        && this.gameUiTheme === 'retro8bit'
        && this.viewportWidth > 600
        && !this.ecoMode
        && !this.prefersReducedMotion
    },
    sceneVisible() {
      return this.revealPhase !== 'idle'
    },
    boardPhaseClass() {
      return {
        'dp-game-room-chat-panel__board--flipping-up': this.revealPhase === 'flipping-up',
        'dp-game-room-chat-panel__board--ecg-sweep': this.revealPhase === 'ecg-sweep',
        'dp-game-room-chat-panel__board--revealed': this.revealPhase === 'revealed',
        'dp-game-room-chat-panel__board--collapsing-down': this.revealPhase === 'collapsing-down'
      }
    },
    panelExpandedClass() {
      return this.expanded || (this.useRetroChatReveal && this.revealPhase !== 'idle')
    },
    isVisuallyOpen() {
      if (this.useRetroChatReveal) {
        return this.revealPhase === 'flipping-up'
          || this.revealPhase === 'ecg-sweep'
          || this.revealPhase === 'revealed'
      }
      return this.expanded
    },
    ariaExpanded() {
      return this.isVisuallyOpen ? 'true' : 'false'
    },
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
        if (!this.isVisuallyOpen) return
        var self = this
        this.$nextTick(function () {
          self.scrollToBottom()
        })
      },
      deep: true
    },
    expanded(v) {
      if (!v) return
      if (this.useRetroChatReveal && this.revealPhase !== 'revealed') return
      var self = this
      this.$nextTick(function () {
        self.scrollToBottom()
      })
    },
    revealPhase(phase) {
      if (phase !== 'revealed') return
      var self = this
      this.$nextTick(function () {
        self.scrollToBottom()
      })
    },
    useRetroChatReveal(now, was) {
      if (now === was) return
      this.syncRevealModeOnGateChange()
    },
    viewportWidth() {
      if (this.expanded) {
        this.syncRevealModeOnGateChange()
      }
    }
  },
  mounted() {
    this.prefersReducedMotion = this.readPrefersReducedMotion()
    if (typeof window !== 'undefined' && window.matchMedia) {
      this.prmMedia = window.matchMedia('(prefers-reduced-motion: reduce)')
      if (this.prmMedia.addEventListener) {
        this.prmMedia.addEventListener('change', this.onPrmChange)
      } else if (this.prmMedia.addListener) {
        this.prmMedia.addListener(this.onPrmChange)
      }
    }
    window.addEventListener('resize', this.onResize)
  },
  beforeDestroy() {
    window.removeEventListener('resize', this.onResize)
    if (this.resizeTimer) {
      clearTimeout(this.resizeTimer)
      this.resizeTimer = null
    }
    if (this.prmMedia) {
      if (this.prmMedia.removeEventListener) {
        this.prmMedia.removeEventListener('change', this.onPrmChange)
      } else if (this.prmMedia.removeListener) {
        this.prmMedia.removeListener(this.onPrmChange)
      }
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
    },
    readPrefersReducedMotion() {
      if (typeof window === 'undefined' || !window.matchMedia) return false
      return window.matchMedia('(prefers-reduced-motion: reduce)').matches
    },
    onPrmChange() {
      this.prefersReducedMotion = this.readPrefersReducedMotion()
      this.syncRevealModeOnGateChange()
    },
    onResize() {
      var self = this
      if (this.resizeTimer) clearTimeout(this.resizeTimer)
      this.resizeTimer = setTimeout(function () {
        self.viewportWidth = window.innerWidth
      }, 150)
    },
    syncRevealModeOnGateChange() {
      if (!this.expanded) {
        this.revealPhase = 'idle'
        return
      }
      if (this.useRetroChatReveal) {
        this.revealPhase = 'revealed'
      } else {
        this.revealPhase = 'idle'
      }
    },
    onToggleClick() {
      if (!this.useRetroChatReveal) {
        this.expanded = !this.expanded
        if (!this.expanded) {
          this.revealPhase = 'idle'
        }
        return
      }
      if (this.revealPhase === 'idle') {
        this.expanded = true
        this.revealPhase = 'flipping-up'
        return
      }
      if (this.revealPhase === 'collapsing-down') {
        return
      }
      this.expanded = false
      this.revealPhase = 'collapsing-down'
    },
    onBoardAnimEnd(e) {
      if (e.target !== e.currentTarget) return
      var name = e.animationName || ''
      if (this.revealPhase === 'flipping-up' && name.indexOf('dp-retro-chat-flip-up') !== -1) {
        this.revealPhase = 'ecg-sweep'
        return
      }
      if (this.revealPhase === 'collapsing-down' && name.indexOf('dp-retro-chat-flip-down') !== -1) {
        this.revealPhase = 'idle'
      }
    },
    onEcgAnimEnd(e) {
      if (this.revealPhase !== 'ecg-sweep') return
      if (!e.target.classList.contains('dp-game-room-chat-panel__ecg-line')) return
      this.revealPhase = 'revealed'
    },
    openForGuide() {
      this.expanded = true
      if (this.useRetroChatReveal) {
        this.revealPhase = 'revealed'
      } else {
        this.revealPhase = 'idle'
      }
    },
    closeForGuide() {
      this.expanded = false
      this.revealPhase = 'idle'
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
