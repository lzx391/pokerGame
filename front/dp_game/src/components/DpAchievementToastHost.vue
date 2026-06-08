<template>
  <div ref="host" class="dp-ach-toast-host" aria-live="polite">
    <transition-group
      name="dp-ach-toast-slide"
      tag="div"
      class="dp-ach-toast-stack"
      @after-leave="onAfterLeave"
    >
      <div
        v-for="item in visibleToasts"
        :key="item.id"
        class="dp-ach-toast-wrap"
      >
        <dp-achievement-toast
          :title="item.title"
          :description="item.description"
        />
      </div>
    </transition-group>
  </div>
</template>

<script>
import { mapState } from 'vuex'
import DpAchievementToast from '@/components/DpAchievementToast.vue'
import { dpGetOverlayPortalRoot } from '@/utils/dpOverlayPortal'
import { registerDpFullscreenOverlayReparent } from '@/utils/dpFullscreenOverlayBridge'

var DISMISS_MS = 4200
var LEAVE_MS = 420

export default {
  name: 'DpAchievementToastHost',
  components: { DpAchievementToast },
  data: function () {
    return {
      /** @type {Array<{ id: number, code: string, title: string, description: string, leaving: boolean }>} */
      visibleToasts: [],
      /** @type {Record<string, number>} */
      dismissTimers: {},
      /** @type {Record<string, number>} */
      leaveTimers: {},
      seenToastIds: {}
    }
  },
  computed: {
    ...mapState('dpAchievement', ['toasts'])
  },
  watch: {
    toasts: {
      handler: function (list) {
        this.syncFromStore(list || [])
      },
      deep: true,
      immediate: true
    }
  },
  mounted: function () {
    this.reparentHost()
    registerDpFullscreenOverlayReparent(this.reparentHost)
    this._onFsChange = this.reparentHost.bind(this)
    document.addEventListener('fullscreenchange', this._onFsChange)
    document.addEventListener('webkitfullscreenchange', this._onFsChange)
  },
  beforeDestroy: function () {
    registerDpFullscreenOverlayReparent(null)
    if (this._onFsChange) {
      document.removeEventListener('fullscreenchange', this._onFsChange)
      document.removeEventListener('webkitfullscreenchange', this._onFsChange)
      this._onFsChange = null
    }
    this.clearAllTimers()
  },
  methods: {
    reparentHost: function () {
      var el = this.$refs.host
      var root = dpGetOverlayPortalRoot()
      if (!el || !root || el.parentNode === root) return
      root.appendChild(el)
    },
    syncFromStore: function (list) {
      var self = this
      list.forEach(function (row) {
        if (!row || self.seenToastIds[row.id]) return
        self.seenToastIds[row.id] = true
        self.visibleToasts.push({
          id: row.id,
          code: row.code,
          title: row.title,
          description: row.description,
          leaving: false
        })
        self.scheduleDismiss(row.id)
      })
    },
    scheduleDismiss: function (id) {
      var self = this
      if (self.dismissTimers[id] != null) return
      self.dismissTimers[id] = window.setTimeout(function () {
        self.startLeave(id)
      }, DISMISS_MS)
    },
    startLeave: function (id) {
      if (this.dismissTimers[id] != null) {
        clearTimeout(this.dismissTimers[id])
        delete this.dismissTimers[id]
      }
      this.removeVisible(id)
      var self = this
      this.leaveTimers[id] = window.setTimeout(function () {
        self.$store.commit('dpAchievement/DISMISS_TOAST', id)
        delete self.leaveTimers[id]
      }, LEAVE_MS)
    },
    removeVisible: function (id) {
      this.visibleToasts = this.visibleToasts.filter(function (t) {
        return t.id !== id
      })
    },
    onAfterLeave: function () {
      this.reparentHost()
    },
    clearAllTimers: function () {
      var k
      for (k in this.dismissTimers) {
        clearTimeout(this.dismissTimers[k])
      }
      for (k in this.leaveTimers) {
        clearTimeout(this.leaveTimers[k])
      }
      this.dismissTimers = {}
      this.leaveTimers = {}
    }
  }
}
</script>

<style scoped>
.dp-ach-toast-host {
  position: fixed;
  top: clamp(72px, 12vh, 120px);
  right: 16px;
  z-index: 10100;
  pointer-events: none;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.dp-ach-toast-stack {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}

.dp-ach-toast-wrap {
  pointer-events: none;
}

/* Minecraft 式：从右侧滑入 / 滑出 */
.dp-ach-toast-slide-enter-active,
.dp-ach-toast-slide-leave-active {
  transition:
    transform 0.42s cubic-bezier(0.22, 1, 0.36, 1),
    opacity 0.32s ease;
}

.dp-ach-toast-slide-enter,
.dp-ach-toast-slide-leave-to {
  transform: translateX(calc(100% + 28px));
  opacity: 0;
}

.dp-ach-toast-slide-move {
  transition: transform 0.32s ease;
}
</style>
