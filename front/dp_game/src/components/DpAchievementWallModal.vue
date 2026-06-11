<template>
  <div class="dp-ach-wall-root">
  <el-dialog
    :visible.sync="dialogVisible"
    width="min(92vw, 480px)"
    custom-class="dp-achievement-wall-dialog"
    append-to-body
    :modal="false"
    :z-index="dialogZIndex"
    :close-on-click-modal="!loading"
    :show-close="false"
    @closed="onClosed"
  >
    <div slot="title" class="dp-ach-wall-title-bar">
      <div class="dp-ach-wall-title-bar__deco">
        <span class="dp-ach-wall-suit dp-ach-wall-suit--spade" aria-hidden="true">♠</span>
        <span class="dp-ach-wall-title-bar__text">{{ titleText }}</span>
        <span class="dp-ach-wall-suit dp-ach-wall-suit--heart" aria-hidden="true">♥</span>
      </div>
      <button
        type="button"
        class="dp-ach-wall-title-bar__close"
        :disabled="loading"
        @click="dialogVisible = false"
        aria-label="关闭"
      >
        <i class="el-icon-close"></i>
      </button>
    </div>

    <div v-if="loading" class="dp-ach-wall-loading">
      <span class="dp-ach-wall-loading__chip" aria-hidden="true"></span>
      <span>加载中…</span>
    </div>

    <div v-else-if="loadError" class="dp-ach-wall-empty">
      {{ loadError }}
    </div>

    <div v-else-if="!items.length" class="dp-ach-wall-empty">
      暂无成就
    </div>

    <ul v-else class="dp-ach-wall-list" role="list">
      <li
        v-for="item in items"
        :key="item.id || item.code"
        class="dp-ach-wall-card"
        :class="{ 'dp-ach-wall-card--unlocked': item.unlocked }"
      >
        <div class="dp-ach-wall-card__head">
          <span class="dp-ach-wall-card__title">{{ item.title }}</span>
          <span v-if="item.unlocked" class="dp-ach-wall-card__badge" aria-label="已解锁">已解锁</span>
          <span v-else class="dp-ach-wall-card__badge dp-ach-wall-card__badge--locked" aria-label="未解锁">未解锁</span>
        </div>
        <p class="dp-ach-wall-card__desc">{{ item.description }}</p>
        <p v-if="item.unlocked" class="dp-ach-wall-card__time">
          解锁于 {{ displayUnlockedAt(item.unlockedAt) }}
        </p>
        <button
          v-if="canShowReplay(item)"
          type="button"
          class="dp-ach-wall-card__replay"
          @click.stop="openHandHistoryReplay(item.handHistoryId)"
        >
          查看回放
        </button>
      </li>
    </ul>

    <div slot="footer" class="dp-ach-wall-footer">
      <button type="button" class="dp-ach-wall-btn dp-ach-wall-btn--ghost" @click="dialogVisible = false">
        关闭
      </button>
    </div>
  </el-dialog>

  <transition name="dp-ach-replay">
    <div
      v-if="replayHandHistoryId != null"
      class="dp-ach-replay-overlay"
      :style="{ zIndex: replayZIndex }"
      @click.self="closeReplay"
    >
      <div class="dp-ach-replay-panel" @click.stop>
        <hand-history-detail
          :hand-history-id="replayHandHistoryId"
          :achievement-subject-user-id="replaySubjectUserId"
          :achievement-subject-nickname="replaySubjectNickname"
          embedded
          @back="closeReplay"
        />
      </div>
    </div>
  </transition>
  </div>
</template>

<script>
import { dpResultSuccess, dpResultData, dpResultMessage, dpAxiosErrorMessage } from '@/utils/dpApiResult'
import { dpLayerZIndex, dpNextZIndex } from '@/utils/dpModalZIndex'
import {
  dpGetOverlayPortalRoot,
  dpPortalOverlayToBody,
  dpPruneAchievementStrayVModal,
  dpRestoreOverlayFromPortal,
  dpScheduleOverlayFullscreenReparent,
  dpSyncDialogPairedVModal
} from '@/utils/dpOverlayPortal'
import { displayAchievementUnlockedAt, canShowAchievementReplay } from '@/utils/dpAchievementFormat'
import HandHistoryDetail from '@/components/HandHistoryDetail.vue'

export default {
  name: 'DpAchievementWallModal',
  components: { HandHistoryDetail },
  inject: {
    dpGameView: { default: null }
  },
  props: {
    visible: { type: Boolean, default: false },
    /** null = 当前登录用户；数字 = 查看他人 */
    userId: { type: Number, default: null },
    subjectName: { type: String, default: '' },
    /** 他人成就墙：主体玩家原始昵称（牌谱视角） */
    subjectNickname: { type: String, default: '' }
  },
  data() {
    return {
      dialogZIndex: dpLayerZIndex('achievement'),
      loading: false,
      loadError: '',
      items: [],
      replayHandHistoryId: null,
      replaySubjectUserId: null,
      replaySubjectNickname: '',
      replayZIndex: dpLayerZIndex('handHistory'),
      _portalAnchor: null
    }
  },
  computed: {
    dialogVisible: {
      get() {
        return this.visible
      },
      set(v) {
        this.$emit('update:visible', v)
      }
    },
    titleText() {
      if (this.userId != null && this.subjectName) {
        return this.subjectName + ' 的成就墙'
      }
      if (this.userId != null) {
        return '成就墙'
      }
      return '我的成就墙'
    },
    achievementRevision() {
      return this.$store.state.dpAchievement.revision
    }
  },
  watch: {
    visible(v) {
      if (v) {
        this.dialogZIndex = dpNextZIndex('achievement')
        dpPruneAchievementStrayVModal()
        var self = this
        this.$nextTick(function () {
          self.loadAchievements()
          self.attachPortal()
          dpScheduleOverlayFullscreenReparent(self.dpGameView)
        })
      } else {
        this.replayHandHistoryId = null
        this.replaySubjectUserId = null
        this.replaySubjectNickname = ''
        this.detachPortal()
      }
    },
    achievementRevision() {
      if (!this.visible) return
      if (this.userId != null && this.userId > 0) return
      this.loadAchievements()
    }
  },
  beforeDestroy() {
    this.detachPortal()
  },
  methods: {
    findDialogWrapper() {
      if (typeof document === 'undefined') return null
      var nodes = document.querySelectorAll('.el-dialog__wrapper')
      var i = 0
      for (i = nodes.length - 1; i >= 0; i--) {
        var node = nodes[i]
        if (!node || node.style.display === 'none') continue
        if (node.querySelector('.dp-achievement-wall-dialog')) return node
      }
      return null
    },
    attachPortal() {
      var self = this
      var attempt = function () {
        var wrapper = self.findDialogWrapper()
        if (!wrapper) return false
        if (!self._portalAnchor) {
          self._portalAnchor = { parent: null, next: null }
        }
        dpPortalOverlayToBody(wrapper, self._portalAnchor, dpGetOverlayPortalRoot())
        dpSyncDialogPairedVModal(wrapper, self.dialogZIndex)
        dpPruneAchievementStrayVModal()
        dpScheduleOverlayFullscreenReparent(self.dpGameView)
        return true
      }
      if (attempt()) return
      if (typeof requestAnimationFrame === 'function') {
        requestAnimationFrame(function () {
          if (!attempt()) setTimeout(attempt, 0)
        })
      } else {
        setTimeout(attempt, 0)
      }
      setTimeout(attempt, 50)
    },
    detachPortal() {
      var wrapper = this.findDialogWrapper()
      dpRestoreOverlayFromPortal(wrapper, this._portalAnchor)
      this._portalAnchor = null
      dpPruneAchievementStrayVModal()
    },
    onClosed() {
      this.detachPortal()
      this.items = []
      this.loadError = ''
      this.loading = false
    },
    displayUnlockedAt(raw) {
      return displayAchievementUnlockedAt(raw)
    },
    canShowReplay(item) {
      return canShowAchievementReplay(item)
    },
    closeReplay() {
      this.replayHandHistoryId = null
      this.replaySubjectUserId = null
      this.replaySubjectNickname = ''
    },
    openHandHistoryReplay(handHistoryId) {
      if (handHistoryId == null || handHistoryId === '') return
      var subjectUid = (this.userId != null && this.userId > 0) ? this.userId : null
      var subjectNick = subjectUid ? (this.subjectNickname || '') : ''
      if (this.dpGameView && typeof this.dpGameView.openHandHistoryDetail === 'function') {
        this.dpGameView.openHandHistoryDetail(handHistoryId, subjectUid, subjectNick)
        dpScheduleOverlayFullscreenReparent(this.dpGameView)
        return
      }
      this.replayZIndex = dpNextZIndex('handHistory')
      this.replaySubjectUserId = subjectUid
      this.replaySubjectNickname = subjectNick
      this.replayHandHistoryId = handHistoryId
    },
    async loadAchievements() {
      this.loading = true
      this.loadError = ''
      this.items = []
      try {
        var url = this.userId != null && this.userId > 0
          ? '/dpUser/achievements/' + this.userId
          : '/dpUser/achievements'
        var res = await this.$http.get(url)
        if (!dpResultSuccess(res.data)) {
          this.loadError = dpResultMessage(res.data) || '加载失败'
          return
        }
        var d = dpResultData(res.data) || {}
        this.items = Array.isArray(d.achievements) ? d.achievements : []
      } catch (e) {
        this.loadError = dpAxiosErrorMessage(e) || '加载失败'
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.dp-ach-wall-title-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.dp-ach-wall-title-bar__deco {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.dp-ach-wall-title-bar__text {
  font-size: 17px;
  font-weight: 700;
  color: var(--dp-text-primary, #f2e8d8);
  letter-spacing: 0.04em;
}
.dp-ach-wall-suit--spade { color: #c8cdd6; }
.dp-ach-wall-suit--heart { color: #e07070; }
.dp-ach-wall-title-bar__close {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border: 1px solid var(--dp-border-soft, rgba(255, 255, 255, 0.12));
  border-radius: 8px;
  background: transparent;
  color: var(--dp-text-muted, #9aa3b2);
  cursor: pointer;
}
.dp-ach-wall-loading,
.dp-ach-wall-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 120px;
  color: var(--dp-text-muted, #9aa3b2);
  font-size: 14px;
}
.dp-ach-wall-loading__chip {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 2px solid rgba(212, 175, 55, 0.35);
  border-top-color: var(--dp-accent-gold, #d4af37);
  animation: dp-ach-wall-spin 0.8s linear infinite;
}
@keyframes dp-ach-wall-spin {
  to { transform: rotate(360deg); }
}
.dp-ach-wall-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.dp-ach-wall-card {
  padding: 14px 16px;
  border-radius: 12px;
  border: 1px solid var(--dp-border-soft, rgba(255, 255, 255, 0.1));
  background: rgba(0, 0, 0, 0.18);
  opacity: 0.55;
  filter: grayscale(0.35);
  transition: opacity 0.2s ease, filter 0.2s ease, border-color 0.2s ease;
}
.dp-ach-wall-card--unlocked {
  opacity: 1;
  filter: none;
  border-color: rgba(212, 175, 55, 0.45);
  background: linear-gradient(135deg, rgba(212, 175, 55, 0.12), rgba(0, 0, 0, 0.2));
  box-shadow: 0 0 0 1px rgba(212, 175, 55, 0.08) inset;
}
.dp-ach-wall-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 6px;
}
.dp-ach-wall-card__title {
  font-size: 18px;
  font-weight: 700;
  color: var(--dp-text-primary, #f2e8d8);
  letter-spacing: 0.03em;
}
.dp-ach-wall-card__badge {
  flex-shrink: 0;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 999px;
  background: rgba(212, 175, 55, 0.2);
  color: var(--dp-accent-gold, #d4af37);
  border: 1px solid rgba(212, 175, 55, 0.35);
}
.dp-ach-wall-card__badge--locked {
  background: rgba(255, 255, 255, 0.06);
  color: var(--dp-text-muted, #9aa3b2);
  border-color: rgba(255, 255, 255, 0.1);
}
.dp-ach-wall-card__desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--dp-text-muted, #b8c0cc);
}
.dp-ach-wall-card__time {
  margin: 8px 0 0;
  font-size: 12px;
  color: rgba(212, 175, 55, 0.85);
  font-variant-numeric: tabular-nums;
}
.dp-ach-wall-card__replay {
  margin-top: 10px;
  padding: 6px 12px;
  border-radius: 8px;
  border: 1px solid rgba(212, 175, 55, 0.4);
  background: rgba(212, 175, 55, 0.1);
  color: var(--dp-accent-gold, #d4af37);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s ease, border-color 0.15s ease;
}
.dp-ach-wall-card__replay:hover {
  background: rgba(212, 175, 55, 0.18);
  border-color: rgba(212, 175, 55, 0.55);
}
.dp-ach-wall-footer {
  display: flex;
  justify-content: flex-end;
}
.dp-ach-wall-btn {
  min-height: 36px;
  padding: 0 16px;
  border-radius: 8px;
  font-size: 14px;
  cursor: pointer;
}
.dp-ach-wall-btn--ghost {
  border: 1px solid var(--dp-border-soft, rgba(255, 255, 255, 0.15));
  background: transparent;
  color: var(--dp-text-muted, #c8cdd6);
}

.dp-ach-replay-overlay {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: max(12px, env(safe-area-inset-top, 12px)) 12px 24px;
  background: rgba(0, 0, 0, 0.55);
  overflow-y: auto;
  pointer-events: auto;
}
.dp-ach-replay-panel {
  width: min(920px, 100%);
  max-height: calc(100vh - 48px);
  overflow: auto;
  border-radius: 16px;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.45);
}
.dp-ach-replay-enter-active,
.dp-ach-replay-leave-active {
  transition: opacity 0.15s ease;
}
.dp-ach-replay-enter,
.dp-ach-replay-leave-to {
  opacity: 0;
}
</style>
