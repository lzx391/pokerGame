<template>
  <div class="dp-trace-dock-mount">
    <!-- 宽屏：右侧常驻 dock -->
    <aside
        v-if="layoutMode === 'dock' && pinned"
        class="dp-trace-dock"
        :class="{ 'dp-trace-dock--retro8bit': uiVariant === 'retro8bit' }"
        :style="dockAsideStyle"
        role="complementary"
        aria-label="NPC 决策追踪"
    >
      <header class="dp-trace-dock__head">
        <h2 class="dp-trace-dock__title">决策追踪</h2>
        <!-- <span class="dp-trace-dock__tag">TAG</span> -->
      </header>
      <div class="dp-trace-dock__toolbar">
        <button
            v-if="bodyCanGoBack"
            type="button"
            class="dp-btn dp-btn--ghost dp-trace-dock__btn"
            @click="onBack"
        >
          返回
        </button>
        <button
            type="button"
            class="dp-btn dp-btn--primary dp-trace-dock__btn"
            :disabled="refreshing || loading"
            @click="onRefresh"
        >
          {{ refreshing || loading ? '刷新中…' : '刷新' }}
        </button>
        <span class="dp-trace-dock__hint">点刷新查看最新决策</span>
        <span class="dp-trace-dock__crumb">{{ bodyBreadcrumb }}</span>
      </div>
      <game-npc-decision-trace-body
          ref="traceBody"
          class="dp-trace-dock__body"
          :ui-variant="uiVariant"
          :room-id="roomId"
          :hands="hands"
          :loading="loading"
          :load-error="loadError"
          :refreshing="refreshing"
          :on-auth-failure="onAuthFailure"
          @nav-change="onBodyNavChange"
      />
    </aside>

    <!-- 窄屏：底栏 sheet -->
    <transition name="dp-sheet">
      <game-bottom-sheet
          v-if="layoutMode === 'sheet' && sheetOpen"
          title="决策追踪"
          aria-label="NPC 决策追踪"
          wide
          @close="onSheetClose"
      >
        <div class="dp-trace-dock__sheet-toolbar">
          <button
              v-if="bodyCanGoBack"
              type="button"
              class="dp-btn dp-btn--ghost dp-trace-dock__btn"
              @click="onBack"
          >
            返回
          </button>
          <button
              type="button"
              class="dp-btn dp-btn--primary dp-trace-dock__btn"
              :disabled="refreshing || loading"
              @click="onRefresh"
          >
            {{ refreshing || loading ? '刷新中…' : '刷新' }}
          </button>
          <span class="dp-trace-dock__hint">点刷新查看最新决策</span>
          <span class="dp-trace-dock__crumb">{{ bodyBreadcrumb }}</span>
        </div>
        <game-npc-decision-trace-body
            ref="traceBodySheet"
            class="dp-trace-dock__body"
            :ui-variant="uiVariant"
            :room-id="roomId"
            :hands="hands"
            :loading="loading"
            :load-error="loadError"
            :refreshing="refreshing"
            :on-auth-failure="onAuthFailure"
            @nav-change="onBodyNavChange"
        />
      </game-bottom-sheet>
    </transition>
  </div>
</template>

<script>
import GameBottomSheet from './GameBottomSheet.vue'
import GameNpcDecisionTraceBody from './GameNpcDecisionTraceBody.vue'

export default {
  name: 'GameNpcDecisionTraceDock',
  components: { GameBottomSheet, GameNpcDecisionTraceBody },
  props: {
    /** 'dock' 宽屏侧栏 | 'sheet' 窄屏底栏 */
    layoutMode: {
      type: String,
      default: 'dock',
      validator: function (v) {
        return v === 'dock' || v === 'sheet'
      }
    },
    pinned: { type: Boolean, default: false },
    /** 宽屏 dock 宽度（px）；由 game.vue 拖拽写入 */
    dockWidthPx: { type: Number, default: null },
    sheetOpen: { type: Boolean, default: false },
    roomId: { type: String, default: '' },
    hands: {
      type: Array,
      default: function () {
        return []
      }
    },
    loading: { type: Boolean, default: false },
    loadError: { type: String, default: '' },
    onAuthFailure: {
      type: Function,
      default: null
    },
    /** 'default' | 'retro8bit' — retro 外壳 + 可读字号 body */
    uiVariant: {
      type: String,
      default: 'default',
      validator: function (v) {
        return v === 'default' || v === 'retro8bit'
      }
    }
  },
  data: function () {
    return {
      refreshing: false,
      navBreadcrumb: '手牌列表',
      navCanGoBack: false,
    }
  },
  computed: {
    dockAsideStyle: function () {
      if (this.dockWidthPx == null || !isFinite(this.dockWidthPx)) return null
      var w = Math.round(this.dockWidthPx) + 'px'
      return {
        width: w,
        minWidth: w,
        maxWidth: w,
        flexBasis: w
      }
    },
    bodyBreadcrumb: function () {
      return this.navBreadcrumb
    },
    bodyCanGoBack: function () {
      return this.navCanGoBack
    }
  },
  watch: {
    pinned: function (v) {
      if (v && this.layoutMode === 'dock') {
        this.resetBodyNavigation()
      }
    },
    sheetOpen: function (v) {
      if (v && this.layoutMode === 'sheet') {
        this.resetBodyNavigation()
      }
    },
    loading: function (v, prev) {
      if (prev && !v) {
        this.refreshing = false
      }
    }
  },
  methods: {
    onBodyNavChange: function (payload) {
      if (!payload) return
      this.navBreadcrumb = payload.breadcrumb || ''
      this.navCanGoBack = !!payload.canGoBack
    },
    activeBodyRef: function () {
      if (this.layoutMode === 'sheet') return this.$refs.traceBodySheet
      return this.$refs.traceBody
    },
    resetBodyNavigation: function () {
      var self = this
      this.$nextTick(function () {
        var body = self.$refs.traceBody || self.$refs.traceBodySheet
        if (body && typeof body.resetNavigation === 'function') {
          body.resetNavigation()
        }
      })
    },
    onRefresh: function () {
      this.refreshing = true
      this.$emit('refresh')
    },
    onBack: function () {
      var body = this.activeBodyRef()
      if (body && typeof body.goBack === 'function') {
        body.goBack()
      }
    },
    onSheetClose: function () {
      this.$emit('update:sheetOpen', false)
      this.$emit('close-sheet')
    }
  }
}
</script>

<style scoped>
.dp-trace-dock {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  width: var(--dp-trace-dock-width, min(320px, 28vw));
  min-width: 280px;
  max-width: 50vw;
  max-height: 100%;
  box-sizing: border-box;
  border-left: 1px solid var(--dp-border-subtle, rgba(255, 255, 255, 0.1));
  background: var(--dp-game-bg, #1a1d24);
  overflow: hidden;
}
.dp-trace-dock__head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--dp-border-subtle, rgba(255, 255, 255, 0.1));
  flex-shrink: 0;
}
.dp-trace-dock__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--dp-text-primary, #e8e8e8);
}
.dp-trace-dock__tag {
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  background: rgba(64, 158, 255, 0.15);
  color: var(--dp-accent, #409eff);
}
.dp-trace-dock__toolbar,
.dp-trace-dock__sheet-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid var(--dp-border-subtle, rgba(255, 255, 255, 0.08));
  flex-shrink: 0;
}
.dp-trace-dock__sheet-toolbar {
  margin-bottom: 8px;
  padding-left: 0;
  padding-right: 0;
  border-bottom: none;
}
.dp-trace-dock__btn {
  font-size: 13px;
  padding: 6px 12px;
}
.dp-trace-dock__hint {
  font-size: 12px;
  color: var(--dp-text-secondary, #909399);
}
.dp-trace-dock__crumb {
  margin-left: auto;
  font-size: 12px;
  color: var(--dp-text-secondary, #a0a0a0);
}
.dp-trace-dock__body {
  flex: 1;
  min-height: 0;
  padding: 0 12px 12px;
  box-sizing: border-box;
}

/* 8bit 主题：终端绿边框，内容仍用 Body 可读字号 */
.dp-trace-dock--retro8bit {
  border-left: 2px solid rgba(74, 246, 38, 0.45);
  background: rgba(8, 14, 10, 0.97);
  box-shadow: inset 2px 0 0 rgba(0, 0, 0, 0.35);
}
.dp-trace-dock--retro8bit .dp-trace-dock__head {
  border-bottom-color: rgba(74, 246, 38, 0.25);
}
.dp-trace-dock--retro8bit .dp-trace-dock__title {
  font-family: 'Courier New', ui-monospace, monospace;
  font-size: 13px;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #4af626;
}
.dp-trace-dock--retro8bit .dp-trace-dock__tag {
  border-radius: 0;
  font-family: 'Courier New', ui-monospace, monospace;
  background: rgba(74, 246, 38, 0.12);
  color: #72f052;
  border: 1px solid rgba(74, 246, 38, 0.35);
}
.dp-trace-dock--retro8bit .dp-trace-dock__toolbar,
.dp-trace-dock--retro8bit .dp-trace-dock__sheet-toolbar {
  border-bottom-color: rgba(74, 246, 38, 0.15);
}
.dp-trace-dock--retro8bit .dp-trace-dock__hint,
.dp-trace-dock--retro8bit .dp-trace-dock__crumb {
  font-family: 'Courier New', ui-monospace, monospace;
  font-size: 11px;
  color: #6bdc58;
}
</style>
