<template>
  <div class="dp-awc-root">
  <transition name="dp-awc-root">
    <div
      v-if="visible"
      ref="root"
      class="dp-awc"
      :style="{ zIndex: overlayZIndex }"
      @click.self="close"
    >
      <div class="dp-awc__cabinet" :class="cabinetAnimClass">
        <div class="dp-awc__antenna dp-awc__antenna--l" />
        <div class="dp-awc__antenna dp-awc__antenna--r" />

        <div class="dp-awc__body">
          <div class="dp-awc__bezel">
            <div class="dp-awc__screen">
              <div v-if="showCrt" class="dp-awc__scanlines" />
              <div v-if="showCrt" class="dp-awc__vignette" />
              <div v-if="showCrt" class="dp-awc__snow" :class="{ 'dp-awc__snow--active': snowing }">
                <span class="dp-awc__snow-noise" />
              </div>
              <div v-if="showCrt" class="dp-awc__flash" :class="{ 'dp-awc__flash--pulse': flashing }" />

              <div v-if="phase === 'ready' || phase === 'flash'" class="dp-awc__content">
                <div class="dp-awc__head">
                  <span class="dp-awc__title">{{ headTitle }}</span>
                  <button type="button" class="dp-awc__close" title="关闭" @click="close">[X]</button>
                </div>

                <div class="dp-awc__stats">
                  <span class="dp-awc__stats-item">UNLOCKED:{{ unlockedCount }}/{{ items.length }}</span>
                  <span class="dp-awc__stats-sep">|</span>
                  <span class="dp-awc__stats-item">SYS:TROPHY-OS</span>
                  <span v-if="subjectCode" class="dp-awc__stats-sep">|</span>
                  <span v-if="subjectCode" class="dp-awc__stats-item">UID:{{ subjectCode }}</span>
                </div>

                <div v-if="loading" class="dp-awc__status">
                  <span class="dp-awc__status-icon">&#9654;</span>
                  LOADING TROPHY DATA...
                </div>
                <div v-else-if="loadError" class="dp-awc__status dp-awc__status--err">[ERR] {{ loadError }}</div>
                <div v-else-if="!items.length" class="dp-awc__status">[ NO TROPHIES FOUND ]</div>

                <div v-else class="dp-awc__split">
                  <div class="dp-awc__list" ref="listEl">
                    <div
                      v-for="(item, i) in items"
                      :key="item.id || item.code || i"
                      class="dp-awc__row"
                      :class="{
                        'dp-awc__row--cursor': i === cursor,
                        'dp-awc__row--unlocked': item.unlocked,
                        'dp-awc__row--locked': !item.unlocked
                      }"
                      @click="selectIndex(i)"
                    >
                      <span class="dp-awc__row-icon" aria-hidden="true">{{ item.unlocked ? '★' : '·' }}</span>
                      <span class="dp-awc__row-title">{{ item.title }}</span>
                      <span
                        class="dp-awc__row-badge"
                        :class="item.unlocked ? 'dp-awc__row-badge--on' : 'dp-awc__row-badge--off'"
                      >{{ item.unlocked ? '[UNLOCKED]' : '[LOCKED]' }}</span>
                    </div>
                  </div>

                  <div class="dp-awc__detail">
                    <div class="dp-awc__detail-head">=== TROPHY FILE ===</div>
                    <template v-if="selectedItem">
                      <div class="dp-awc__detail-line">
                        <span class="dp-awc__detail-key">CODE:</span>
                        <span class="dp-awc__detail-val">{{ selectedItem.code || '---' }}</span>
                      </div>
                      <div class="dp-awc__detail-line">
                        <span class="dp-awc__detail-key">NAME:</span>
                        <span class="dp-awc__detail-val dp-awc__detail-val--title">{{ selectedItem.title }}</span>
                      </div>
                      <div class="dp-awc__detail-line dp-awc__detail-line--desc">
                        <span class="dp-awc__detail-key">DESC:</span>
                        <span class="dp-awc__detail-val dp-awc__detail-val--desc">
                          <span class="dp-awc__typed">{{ typedDesc }}</span><span v-if="typing" class="dp-awc__caret">_</span>
                        </span>
                      </div>
                      <div v-if="selectedItem.unlocked" class="dp-awc__detail-line">
                        <span class="dp-awc__detail-key">DATE:</span>
                        <span class="dp-awc__detail-val dp-awc__detail-val--date">{{ displayUnlockedAt(selectedItem.unlockedAt) }}</span>
                      </div>
                      <div v-if="canShowReplay(selectedItem)" class="dp-awc__detail-replay">
                        <button
                          type="button"
                          class="dp-awc__replay-btn"
                          @click.stop="openHandHistoryReplay(selectedItem.handHistoryId)"
                        >
                          <span class="dp-awc__replay-btn-icon" aria-hidden="true">▶</span>
                          <span class="dp-awc__replay-btn-label">REPLAY TAPE</span>
                          <span class="dp-awc__replay-btn-slot" aria-hidden="true">[REC]</span>
                        </button>
                      </div>
                      <div v-else-if="!selectedItem.unlocked" class="dp-awc__detail-hint">[ CLASSIFIED — COMPLETE OBJECTIVE TO DECRYPT ]</div>
                    </template>
                    <div v-else class="dp-awc__detail-empty">SELECT A TROPHY</div>
                  </div>
                </div>

                <div v-if="!loading && items.length" class="dp-awc__footer">
                  <span>W/S nav</span>
                  <span>Enter select</span>
                  <span v-if="canShowReplay(selectedItem)">R replay</span>
                  <span>Esc close</span>
                </div>
              </div>
            </div>
          </div>

          <div class="dp-awc__controls">
            <span class="dp-awc__knob" />
            <span class="dp-awc__knob dp-awc__knob--sm" />
            <span class="dp-awc__led" :class="{ 'dp-awc__led--glow': phase === 'ready' }" />
            <span class="dp-awc__brand">TROPHY-OS CRT-8800</span>
          </div>
        </div>

        <div class="dp-awc__feet"><span /><span /></div>
      </div>
    </div>
  </transition>

  <dp-hand-history-detail
    v-if="replayHandHistoryId != null"
    context="achievement-overlay"
    :hand-history-id="replayHandHistoryId"
    @closed="replayHandHistoryId = null"
  />
  </div>
</template>

<script>
import { mapState } from 'vuex'
import { dpResultSuccess, dpResultData, dpResultMessage, dpAxiosErrorMessage } from '@/utils/dpApiResult'
import { dpLayerZIndex, dpNextZIndex } from '@/utils/dpModalZIndex'
import {
  dpGetOverlayPortalRoot,
  dpPortalOverlayToBody,
  dpRestoreOverlayFromPortal,
  dpScheduleOverlayFullscreenReparent
} from '@/utils/dpOverlayPortal'
import { registerDpFullscreenOverlayReparent, unregisterDpFullscreenOverlayReparent } from '@/utils/dpFullscreenOverlayBridge'
import { shouldSkipRetroEnterEffects } from '@/utils/dpRetroEnterGameHandoff'
import { displayAchievementUnlockedAt, canShowAchievementReplay } from '@/utils/dpAchievementFormat'
import DpHandHistoryDetail from '@/components/DpHandHistoryDetail.vue'

export default {
  name: 'DpAchievementWallCrt',
  components: { DpHandHistoryDetail },
  inject: {
    dpGameView: { default: null }
  },
  props: {
    visible: { type: Boolean, default: false },
    /** null = 当前登录用户；数字 = 查看他人 */
    userId: { type: Number, default: null },
    subjectName: { type: String, default: '' }
  },
  data: function () {
    return {
      overlayZIndex: dpLayerZIndex('achievement'),
      phase: 'idle',
      loading: false,
      loadError: '',
      items: [],
      replayHandHistoryId: null,
      cursor: 0,
      typedDesc: '',
      typing: false,
      typeTimer: null,
      snowTimer: null,
      flashTimer: null,
      viewportWidth: typeof window !== 'undefined' ? window.innerWidth : 1024,
      prefersReducedMotion: false,
      _portalAnchor: null,
      _onKey: null,
      _onFsChange: null,
      _onResize: null
    }
  },
  computed: {
    ...mapState('dpGame', ['ecoMode']),
    showCrt: function () {
      return this.viewportWidth > 600 && !this.ecoMode && !this.prefersReducedMotion && !shouldSkipRetroEnterEffects()
    },
    snowing: function () { return this.phase === 'snowing' },
    flashing: function () { return this.phase === 'flashing' },
    cabinetAnimClass: function () {
      if (!this.showCrt) return 'dp-awc__cabinet--instant'
      return {
        'dp-awc__cabinet--drop': this.phase === 'dropping',
        'dp-awc__cabinet--ready': this.phase === 'ready' || this.phase === 'flash' || this.phase === 'snowing' || this.phase === 'flashing',
        'dp-awc__cabinet--retract': this.phase === 'retracting'
      }
    },
    headTitle: function () {
      if (this.userId != null && this.subjectName) {
        return '> ' + String(this.subjectName).toUpperCase() + ' — TROPHIES'
      }
      if (this.userId != null) return '> PLAYER TROPHIES'
      return '> MY TROPHY LOG'
    },
    subjectCode: function () {
      if (this.userId == null || this.userId <= 0) return ''
      return String(this.userId)
    },
    unlockedCount: function () {
      var n = 0
      for (var i = 0; i < this.items.length; i++) {
        if (this.items[i] && this.items[i].unlocked) n++
      }
      return n
    },
    selectedItem: function () {
      return this.items[this.cursor] || null
    },
    achievementRevision: function () {
      return this.$store.state.dpAchievement.revision
    }
  },
  watch: {
    visible: function (v) {
      if (v) {
        this.overlayZIndex = dpNextZIndex('achievement')
        this.startOpen()
      } else {
        this.replayHandHistoryId = null
        this.doClose()
      }
    },
    achievementRevision: function () {
      if (!this.visible) return
      if (this.userId != null && this.userId > 0) return
      this.loadAchievements()
    },
    cursor: function () {
      this.startTypewriter()
    },
    items: function () {
      if (this.cursor >= this.items.length) {
        this.cursor = Math.max(0, this.items.length - 1)
      }
      this.startTypewriter()
    }
  },
  mounted: function () {
    this.syncMotionPrefs()
    this._fsReparent = this.reparentRoot.bind(this)
    this.reparentRoot()
    registerDpFullscreenOverlayReparent(this._fsReparent)
    this._onFsChange = this.reparentRoot.bind(this)
    document.addEventListener('fullscreenchange', this._onFsChange)
    document.addEventListener('webkitfullscreenchange', this._onFsChange)
    this._onResize = this.syncViewport.bind(this)
    window.addEventListener('resize', this._onResize)
    if (this.visible) this.startOpen()
  },
  beforeDestroy: function () {
    unregisterDpFullscreenOverlayReparent(this._fsReparent)
    this._fsReparent = null
    this.clearTimers()
    this.detachKeyListener()
    this.detachPortal()
    if (this._onFsChange) {
      document.removeEventListener('fullscreenchange', this._onFsChange)
      document.removeEventListener('webkitfullscreenchange', this._onFsChange)
      this._onFsChange = null
    }
    if (this._onResize) {
      window.removeEventListener('resize', this._onResize)
      this._onResize = null
    }
  },
  methods: {
    syncMotionPrefs: function () {
      if (typeof window === 'undefined' || !window.matchMedia) {
        this.prefersReducedMotion = false
        return
      }
      this.prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    },
    syncViewport: function () {
      this.viewportWidth = window.innerWidth
    },
    reparentRoot: function () {
      var el = this.$refs.root
      if (!el) return
      if (!this._portalAnchor) {
        this._portalAnchor = { parent: null, next: null }
      }
      dpPortalOverlayToBody(el, this._portalAnchor, dpGetOverlayPortalRoot())
      dpScheduleOverlayFullscreenReparent(this.dpGameView)
    },
    detachPortal: function () {
      var el = this.$refs.root
      dpRestoreOverlayFromPortal(el, this._portalAnchor)
      this._portalAnchor = null
    },
    clearTimers: function () {
      if (this.typeTimer) { clearInterval(this.typeTimer); this.typeTimer = null }
      if (this.snowTimer) { clearTimeout(this.snowTimer); this.snowTimer = null }
      if (this.flashTimer) { clearTimeout(this.flashTimer); this.flashTimer = null }
    },
    startOpen: function () {
      var self = this
      this.cursor = 0
      this.loadError = ''
      this.phase = this.showCrt ? 'dropping' : 'ready'
      this.$nextTick(function () {
        self.reparentRoot()
        self.attachKeyListener()
        if (!self.showCrt) {
          self.loadAchievements()
        } else {
          self.snowTimer = setTimeout(function () {
            self.phase = 'snowing'
            self.loadAchievements()
            self.snowTimer = setTimeout(function () {
              self.phase = 'flashing'
              self.flashTimer = setTimeout(function () {
                self.phase = 'ready'
              }, 120)
            }, 220)
          }, 340)
        }
      })
    },
    doClose: function () {
      this.clearTimers()
      this.detachKeyListener()
      this.replayHandHistoryId = null
      this.phase = 'idle'
      this.items = []
      this.loadError = ''
      this.loading = false
      this.typedDesc = ''
      this.typing = false
    },
    close: function () {
      this.$emit('update:visible', false)
    },
    selectIndex: function (i) {
      if (i < 0 || i >= this.items.length) return
      this.cursor = i
    },
    attachKeyListener: function () {
      if (this._onKey) return
      var self = this
      this._onKey = function (e) {
        if (!self.visible) return
        if (self.onKey(e)) {
          e.stopPropagation()
        }
      }
      window.addEventListener('keydown', this._onKey, true)
    },
    detachKeyListener: function () {
      if (!this._onKey) return
      window.removeEventListener('keydown', this._onKey, true)
      this._onKey = null
    },
    onKey: function (e) {
      if (e.key === 'Escape') { e.preventDefault(); this.close(); return true }
      if (this.loading || this.loadError || !this.items.length) return false
      if (e.key === 'w' || e.key === 'W' || e.key === 'ArrowUp') {
        e.preventDefault()
        this.cursor = Math.max(0, this.cursor - 1)
        this.scrollCursorIntoView()
        return true
      }
      if (e.key === 's' || e.key === 'S' || e.key === 'ArrowDown') {
        e.preventDefault()
        this.cursor = Math.min(this.items.length - 1, this.cursor + 1)
        this.scrollCursorIntoView()
        return true
      }
      if (e.key === 'Enter') {
        e.preventDefault()
        this.startTypewriter()
        return true
      }
      if ((e.key === 'r' || e.key === 'R') && this.canShowReplay(this.selectedItem)) {
        e.preventDefault()
        this.openHandHistoryReplay(this.selectedItem.handHistoryId)
        return true
      }
      return false
    },
    scrollCursorIntoView: function () {
      var self = this
      this.$nextTick(function () {
        var list = self.$refs.listEl
        if (!list) return
        var row = list.children[self.cursor]
        if (row && row.scrollIntoView) row.scrollIntoView({ block: 'nearest' })
      })
    },
    startTypewriter: function () {
      if (this.typeTimer) {
        clearInterval(this.typeTimer)
        this.typeTimer = null
      }
      var item = this.selectedItem
      var text = item && item.description ? String(item.description) : ''
      if (!text || !this.showCrt) {
        this.typedDesc = text
        this.typing = false
        return
      }
      this.typedDesc = ''
      this.typing = true
      var i = 0
      var self = this
      this.typeTimer = setInterval(function () {
        if (i >= text.length) {
          clearInterval(self.typeTimer)
          self.typeTimer = null
          self.typing = false
          return
        }
        self.typedDesc += text.charAt(i)
        i++
      }, 24)
    },
    displayUnlockedAt: function (raw) {
      return displayAchievementUnlockedAt(raw)
    },
    canShowReplay: function (item) {
      return canShowAchievementReplay(item)
    },
    openHandHistoryReplay: function (handHistoryId) {
      if (handHistoryId == null || handHistoryId === '') return
      if (this.dpGameView && typeof this.dpGameView.openHandHistoryDetail === 'function') {
        this.dpGameView.openHandHistoryDetail(handHistoryId)
        dpScheduleOverlayFullscreenReparent(this.dpGameView)
        return
      }
      this.replayHandHistoryId = handHistoryId
    },
    loadAchievements: async function () {
      this.loading = true
      this.loadError = ''
      try {
        var url = this.userId != null && this.userId > 0
          ? '/dpUser/achievements/' + this.userId
          : '/dpUser/achievements'
        var res = await this.$http.get(url)
        if (!dpResultSuccess(res.data)) {
          this.loadError = dpResultMessage(res.data) || 'LOAD FAILED'
          this.items = []
          return
        }
        var d = dpResultData(res.data) || {}
        this.items = Array.isArray(d.achievements) ? d.achievements : []
        if (this.items.length && this.cursor >= this.items.length) {
          this.cursor = 0
        }
      } catch (e) {
        this.loadError = dpAxiosErrorMessage(e) || 'LOAD FAILED'
        this.items = []
      } finally {
        this.loading = false
        this.startTypewriter()
      }
    }
  }
}
</script>

<style scoped>
.dp-awc {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  background: rgba(0, 0, 0, 0.42);
  pointer-events: auto;
}

.dp-awc-root-enter-active { transition: opacity 0.12s; }
.dp-awc-root-leave-active { transition: opacity 0.18s; }
.dp-awc-root-enter, .dp-awc-root-leave-to { opacity: 0; }

.dp-awc__cabinet {
  position: relative;
  margin-top: max(16px, env(safe-area-inset-top, 16px));
  width: min(720px, 96vw);
  display: flex;
  flex-direction: column;
  align-items: center;
}

.dp-awc__cabinet--drop { animation: dp-awc-drop 0.36s cubic-bezier(0.34, 1.4, 0.64, 1) forwards; }
.dp-awc__cabinet--instant { opacity: 1; transform: none; }
.dp-awc__cabinet--retract { animation: dp-awc-retract 0.26s ease-in forwards; }

@keyframes dp-awc-drop {
  from { transform: translateY(-108%); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
@keyframes dp-awc-retract {
  to { transform: translateY(-108%); opacity: 0; }
}

.dp-awc__antenna {
  position: absolute;
  top: -24px;
  width: 4px;
  height: 30px;
  background: linear-gradient(to bottom, #9aa8b8, #3a4450);
  border-radius: 2px 2px 0 0;
  z-index: 0;
}
.dp-awc__antenna::after {
  content: '';
  position: absolute;
  top: -5px;
  left: -3px;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 35%, #c0c8d4, #3a4450);
}
.dp-awc__antenna--l { left: calc(50% - 64px); transform: rotate(-14deg); }
.dp-awc__antenna--r { right: calc(50% - 64px); transform: rotate(14deg); }

.dp-awc__body {
  position: relative;
  width: 100%;
  background: linear-gradient(175deg, #2a3038 0%, #1a1e24 22%, #252a32 50%, #1c2026 80%, #2a3038 100%);
  border-radius: 16px 16px 10px 10px;
  box-shadow: 0 0 0 3px #0d0f12, 0 0 0 6px #1a1d22, 0 10px 40px rgba(0, 0, 0, 0.72);
  padding: 14px 14px 5px;
}

.dp-awc__bezel {
  position: relative;
  background: linear-gradient(175deg, #111418, #0a0c0f 30%, #0e1014 70%, #111418);
  border-radius: 10px;
  padding: clamp(8px, 1.8vw, 14px);
  box-shadow: inset 0 2px 8px rgba(0, 0, 0, 0.8), inset 0 -2px 4px rgba(255, 255, 255, 0.03), 0 0 0 2px #0a0c0e;
}

.dp-awc__screen {
  position: relative;
  overflow: hidden;
  background: rgba(4, 6, 8, 0.98);
  border-radius: 4px;
  min-height: min(380px, calc(100vh - 220px));
  max-height: min(520px, calc(100vh - 200px));
  display: flex;
  flex-direction: column;
  box-shadow: inset 0 0 50px rgba(0, 0, 0, 0.5), 0 0 10px rgba(74, 246, 38, 0.05);
}

.dp-awc__scanlines {
  position: absolute;
  inset: 0;
  z-index: 4;
  pointer-events: none;
  background: repeating-linear-gradient(to bottom, transparent 0 2px, rgba(0, 0, 0, 0.1) 2px 3px);
  background-size: 100% 3px;
  opacity: 0.12;
}

.dp-awc__vignette {
  position: absolute;
  inset: 0;
  z-index: 3;
  pointer-events: none;
  background: radial-gradient(ellipse at center, transparent 52%, rgba(0, 0, 0, 0.48) 100%);
}

.dp-awc__snow {
  position: absolute;
  inset: 0;
  z-index: 6;
  opacity: 0;
  pointer-events: none;
  overflow: hidden;
  transition: opacity 0.08s;
  background: #0a0c0e;
}
.dp-awc__snow--active { opacity: 0.9; }
.dp-awc__snow-noise {
  position: absolute;
  inset: 0;
  opacity: 0.65;
  background-image:
    repeating-radial-gradient(circle at 18% 22%, rgba(255, 255, 255, 0.5) 0 0.35px, transparent 0.45px),
    repeating-radial-gradient(circle at 75% 60%, rgba(210, 218, 228, 0.4) 0 0.3px, transparent 0.4px);
  background-size: 3px 3px, 4px 4px;
}

.dp-awc__flash {
  position: absolute;
  inset: 0;
  z-index: 5;
  pointer-events: none;
  opacity: 0;
}
.dp-awc__flash--pulse { animation: dp-awc-flash 0.1s ease-out; }
@keyframes dp-awc-flash {
  0% { opacity: 1; background: rgba(248, 250, 252, 0.88); }
  100% { opacity: 0; background: transparent; }
}

.dp-awc__content {
  position: relative;
  z-index: 2;
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 10px 12px;
  font-family: 'Courier New', ui-monospace, 'PingFang SC', monospace;
  color: #4af626;
}

.dp-awc__head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(74, 246, 38, 0.2);
  flex-shrink: 0;
}

.dp-awc__title {
  flex: 1;
  min-width: 0;
  font-family: 'Press Start 2P', monospace;
  font-size: 10px;
  line-height: 1.5;
  color: #4af626;
  text-shadow: 0 0 6px rgba(74, 246, 38, 0.45);
  letter-spacing: 0.04em;
}

.dp-awc__close {
  flex-shrink: 0;
  width: 30px;
  height: 26px;
  padding: 0;
  border: 1px solid rgba(74, 246, 38, 0.3);
  border-radius: 2px;
  background: rgba(8, 12, 8, 0.85);
  color: #4af626;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  cursor: pointer;
  line-height: 1;
}
.dp-awc__close:hover { background: #4af626; color: #080a0c; }

.dp-awc__stats {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0 8px;
  padding: 6px 0 8px;
  font-size: 10px;
  line-height: 1.45;
  color: rgba(74, 246, 38, 0.5);
  letter-spacing: 0.03em;
  flex-shrink: 0;
}
.dp-awc__stats-sep { color: rgba(74, 246, 38, 0.15); }

.dp-awc__status {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-family: 'Press Start 2P', monospace;
  font-size: 11px;
  color: rgba(74, 246, 38, 0.65);
  text-shadow: 0 0 4px rgba(74, 246, 38, 0.25);
}
.dp-awc__status-icon { animation: dp-awc-blink 0.8s step-end infinite; }
@keyframes dp-awc-blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.3; } }
.dp-awc__status--err { color: #ff6666; text-shadow: 0 0 4px rgba(255, 102, 102, 0.35); }

.dp-awc__split {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 0.95fr);
  gap: 10px;
}

@media (max-width: 600px) {
  .dp-awc__split { grid-template-columns: 1fr; grid-template-rows: 1fr auto; }
}

.dp-awc__list {
  min-height: 0;
  overflow-y: auto;
  border: 1px solid rgba(74, 246, 38, 0.12);
  background: rgba(6, 10, 6, 0.55);
}

.dp-awc__row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 10px;
  border-bottom: 1px solid rgba(74, 246, 38, 0.05);
  font-size: 12px;
  line-height: 1.4;
  cursor: pointer;
  transition: background 0.08s, border-color 0.08s;
}
.dp-awc__row:hover { background: rgba(74, 246, 38, 0.03); }
.dp-awc__row--cursor {
  border-color: rgba(74, 246, 38, 0.35);
  background: rgba(74, 246, 38, 0.07);
}
.dp-awc__row--locked { opacity: 0.52; filter: grayscale(0.4); }
.dp-awc__row--unlocked.dp-awc__row--cursor { border-left: 3px solid rgba(114, 240, 82, 0.55); }

.dp-awc__row-icon {
  flex-shrink: 0;
  width: 14px;
  text-align: center;
  color: rgba(255, 255, 85, 0.85);
  font-size: 13px;
}
.dp-awc__row--locked .dp-awc__row-icon { color: rgba(74, 246, 38, 0.25); }

.dp-awc__row-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #e0f0d8;
}

.dp-awc__row-badge {
  flex-shrink: 0;
  font-size: 9px;
  letter-spacing: 0.04em;
}
.dp-awc__row-badge--on { color: #72f052; text-shadow: 0 0 4px rgba(114, 240, 82, 0.3); }
.dp-awc__row-badge--off { color: rgba(74, 246, 38, 0.28); }

.dp-awc__detail {
  min-height: 0;
  overflow-y: auto;
  border: 1px solid rgba(240, 160, 64, 0.22);
  background: rgba(240, 160, 64, 0.03);
  padding: 10px 12px;
  font-size: 11px;
  line-height: 1.5;
}

.dp-awc__detail-head {
  font-family: 'Press Start 2P', monospace;
  font-size: 8px;
  color: rgba(240, 160, 64, 0.7);
  margin-bottom: 10px;
  letter-spacing: 0.05em;
}

.dp-awc__detail-line {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: flex-start;
}
.dp-awc__detail-line--desc { flex-direction: column; gap: 4px; }

.dp-awc__detail-key {
  flex-shrink: 0;
  color: rgba(240, 160, 64, 0.55);
  font-family: 'Press Start 2P', monospace;
  font-size: 8px;
  padding-top: 2px;
}

.dp-awc__detail-val {
  color: #f0e8d0;
  word-break: break-word;
}
.dp-awc__detail-val--title { color: #ffff88; text-shadow: 0 0 4px rgba(255, 255, 136, 0.2); }
.dp-awc__detail-val--desc { color: rgba(224, 240, 216, 0.88); min-height: 3.6em; }
.dp-awc__detail-val--date { color: #72f052; font-variant-numeric: tabular-nums; }

.dp-awc__typed { white-space: pre-wrap; }
.dp-awc__caret {
  animation: dp-awc-blink 0.7s step-end infinite;
  color: #4af626;
}

.dp-awc__detail-hint {
  margin-top: 6px;
  font-size: 10px;
  color: rgba(74, 246, 38, 0.35);
  letter-spacing: 0.03em;
}

.dp-awc__detail-replay {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed rgba(240, 160, 64, 0.18);
}

.dp-awc__replay-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 8px 10px;
  border: 2px solid rgba(74, 246, 38, 0.45);
  border-radius: 0;
  background:
    repeating-linear-gradient(
      90deg,
      rgba(8, 12, 8, 0.95) 0 3px,
      rgba(12, 18, 10, 0.95) 3px 6px
    );
  box-shadow:
    inset 0 0 0 1px rgba(74, 246, 38, 0.12),
    0 0 8px rgba(74, 246, 38, 0.08);
  color: #4af626;
  font-family: 'Press Start 2P', 'Courier New', ui-monospace, monospace;
  font-size: 8px;
  line-height: 1.4;
  letter-spacing: 0.06em;
  cursor: pointer;
  text-shadow: 0 0 4px rgba(74, 246, 38, 0.35);
  transition: background 0.08s, border-color 0.08s, color 0.08s, box-shadow 0.08s;
}
.dp-awc__replay-btn:hover {
  border-color: #72f052;
  background: rgba(74, 246, 38, 0.12);
  color: #72f052;
  box-shadow:
    inset 0 0 0 1px rgba(114, 240, 82, 0.25),
    0 0 12px rgba(74, 246, 38, 0.22);
}
.dp-awc__replay-btn:active {
  transform: translateY(1px);
  box-shadow: inset 0 2px 6px rgba(0, 0, 0, 0.45);
}
.dp-awc__replay-btn-icon {
  flex-shrink: 0;
  font-size: 10px;
  color: #ffff88;
  text-shadow: 0 0 6px rgba(255, 255, 136, 0.45);
}
.dp-awc__replay-btn-label {
  flex: 1;
  min-width: 0;
  text-align: left;
}
.dp-awc__replay-btn-slot {
  flex-shrink: 0;
  padding: 2px 5px;
  border: 1px solid rgba(240, 160, 64, 0.45);
  background: rgba(240, 160, 64, 0.08);
  color: rgba(240, 160, 64, 0.85);
  font-size: 7px;
  letter-spacing: 0.08em;
}

.dp-awc__detail-empty {
  color: rgba(240, 160, 64, 0.35);
  padding: 20px 0;
  text-align: center;
}

.dp-awc__footer {
  flex-shrink: 0;
  display: flex;
  gap: 14px;
  padding: 8px 0 2px;
  font-size: 10px;
  color: rgba(74, 246, 38, 0.28);
  user-select: none;
}

.dp-awc__controls {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 6px 4px;
}
.dp-awc__knob {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, #5a6068, #1a1e24);
  border: 2px solid #0a0c0e;
  box-shadow: inset 0 -2px 4px rgba(0, 0, 0, 0.5);
}
.dp-awc__knob--sm { width: 12px; height: 12px; }
.dp-awc__led {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #2a1010;
  border: 1px solid #1a0808;
  margin-left: auto;
}
.dp-awc__led--glow {
  background: #4af626;
  box-shadow: 0 0 8px rgba(74, 246, 38, 0.7);
}
.dp-awc__brand {
  font-family: 'Press Start 2P', monospace;
  font-size: 7px;
  color: rgba(180, 190, 200, 0.45);
  letter-spacing: 0.06em;
}

.dp-awc__feet {
  display: flex;
  justify-content: space-between;
  width: 72%;
  margin-top: 2px;
}
.dp-awc__feet span {
  width: 42%;
  height: 8px;
  background: linear-gradient(to bottom, #1e2228, #0e1014);
  border-radius: 0 0 6px 6px;
}

@media (prefers-reduced-motion: reduce) {
  .dp-awc__cabinet--drop { animation: none !important; }
}
</style>
