<template>
  <div
      v-show="open"
      class="dp-friend-chat-panel"
      role="dialog"
      aria-modal="true"
      aria-label="选择好友私信"
  >
    <div
        class="dp-friend-chat-panel__shell"
        :style="shellStyle"
    >
      <header class="dp-friend-chat-panel__head">
        <span class="dp-friend-chat-panel__title">— FRIEND DM —</span>
        <button
            type="button"
            class="dp-friend-chat-panel__close"
            aria-label="关闭"
            @click="$emit('close')"
        >
          ×
        </button>
      </header>
      <div class="dp-friend-chat-panel__body">
        <div class="dp-friend-chat-panel__scanlines" aria-hidden="true" />
        <div class="dp-friend-chat-panel__content">
          <game-friend-chat-picker-content
              :active="open"
              :my-user-id="myUserId"
              @open-chat="$emit('open-chat', $event)"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import GameFriendChatPickerContent from './GameFriendChatPickerContent.vue'

export default {
  name: 'GameFriendChatPanel',
  components: { GameFriendChatPickerContent },
  inject: ['dpGameView'],
  props: {
    open: { type: Boolean, default: false },
    myUserId: { type: Number, default: 0 }
  },
  computed: {
    vm() {
      return this.dpGameView
    },
    shellStyle() {
      return {
        top: this.panelTop + 'px'
      }
    }
  },
  data() {
    return {
      panelTop: 56
    }
  },
  watch: {
    open(now) {
      if (now) {
        this.refreshPanelAnchor()
        if (this.vm && typeof this.vm.scheduleReparentElementUiLayersIntoFullscreenRoot === 'function') {
          this.vm.scheduleReparentElementUiLayersIntoFullscreenRoot()
        }
      }
    },
    'vm.layoutFullscreen'() {
      if (this.open) this.refreshPanelAnchor()
    },
    'vm.viewportWidth'() {
      if (this.open) this.refreshPanelAnchor()
    }
  },
  mounted() {
    if (this.open) this.refreshPanelAnchor()
  },
  methods: {
    gameRootZoomFactor(root) {
      if (!root || typeof window === 'undefined') return 1
      var zoom = window.getComputedStyle(root).zoom
      if (!zoom || zoom === 'normal') return 1
      var parsed = parseFloat(zoom)
      return isFinite(parsed) && parsed > 0 ? parsed : 1
    },
    refreshPanelAnchor() {
      var root = this.vm && this.vm.$refs && this.vm.$refs.gameRoot
      if (!root || typeof window === 'undefined') {
        this.panelTop = 56
        return
      }
      var header = root.querySelector('.dp-game-layout__header')
      if (!header) {
        this.panelTop = 56
        return
      }
      var rect = header.getBoundingClientRect()
      var rootRect = root.getBoundingClientRect()
      var zoom = this.gameRootZoomFactor(root)
      this.panelTop = (rect.bottom - rootRect.top) / zoom + 8
    }
  }
}
</script>
