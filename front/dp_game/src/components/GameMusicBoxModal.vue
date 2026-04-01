<template>
  <div v-if="visible" class="hand-rank-modal-mask" @click="$emit('close')">
    <div
        class="hand-rank-modal hand-rank-modal--music-box"
        :data-dp-game-theme="gameUiTheme"
        role="dialog"
        aria-modal="true"
        aria-labelledby="dp-music-box-title"
        @click.stop
    >
      <div class="dp-game-dialog__head">
        <span id="dp-music-box-title" class="dp-game-dialog__title">音乐盒</span>
        <button
            type="button"
            class="dp-game-dialog__close"
            aria-label="关闭"
            @click="$emit('close')"
        >
          ×
        </button>
      </div>
      <div class="dp-game-dialog__body dp-game-dialog__body--music-box">
        <p class="dp-music-box__hint">
          与同桌共用一条推送：谁点播放/暂停，所有人同步（走房间 WebSocket）。
        </p>
        <p v-if="statusLine" class="dp-music-box__status">{{ statusLine }}</p>
        <p v-if="listLoading" class="dp-music-box__muted">加载曲库…</p>
        <p v-else-if="listError" class="dp-music-box__err">{{ listError }}</p>
        <ul v-else class="dp-music-box__list">
          <li
              v-for="t in tracks"
              :key="t.id"
              class="dp-music-box__row"
          >
            <span class="dp-music-box__name">{{ t.displayName || ('#' + t.id) }}</span>
            <span class="dp-music-box__actions">
              <button
                  type="button"
                  class="dp-btn dp-btn--primary dp-music-box__btn"
                  :disabled="!t.webPath || !canControl"
                  @click="emitPlay(t)"
              >
                播放
              </button>
            </span>
          </li>
        </ul>
        <div class="dp-music-box__toolbar">
          <button
              type="button"
              class="dp-btn dp-top-bar__btn dp-top-bar__btn--ghost"
              :disabled="!canControl || !hasCurrentTrack"
              @click="emitPause"
          >
            暂停
          </button>
          <button
              type="button"
              class="dp-btn dp-top-bar__btn dp-top-bar__btn--ghost"
              :disabled="!canControl"
              @click="emitStop"
          >
            停止
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { dpDisplayNickname } from '../utils/dpDisplayNickname'

export default {
  name: 'GameMusicBoxModal',
  props: {
    visible: { type: Boolean, default: false },
    gameUiTheme: { type: String, default: 'default' },
    tracks: { type: Array, default: function () { return [] } },
    listLoading: { type: Boolean, default: false },
    listError: { type: String, default: '' },
    /** 最近一次服务端广播的 roomMusic 状态 */
    roomMusic: { type: Object, default: null },
    canControl: { type: Boolean, default: true }
  },
  computed: {
    hasCurrentTrack () {
      var m = this.roomMusic
      return !!(m && m.webPath)
    },
    statusLine () {
      var m = this.roomMusic
      if (!m || !m.action) return ''
      var who = m.byNickname ? dpDisplayNickname(m.byNickname) : '—'
      if (m.action === 'stop') {
        return '当前：已停止（上次操作：' + who + '）'
      }
      var title = (m.displayName && String(m.displayName).trim()) || '未命名'
      if (m.action === 'pause') {
        return '当前：已暂停 · ' + title + '（' + who + '）'
      }
      if (m.action === 'play') {
        return '正在播放：' + title + '（' + who + '）'
      }
      return ''
    }
  },
  methods: {
    emitPlay (t) {
      if (!t || !t.webPath) return
      this.$emit('sync', {
        action: 'play',
        trackId: t.id,
        webPath: t.webPath,
        displayName: (t.displayName && String(t.displayName).trim()) || ('曲目 ' + t.id)
      })
    },
    emitPause () {
      var m = this.roomMusic
      this.$emit('sync', {
        action: 'pause',
        trackId: m && m.trackId != null ? m.trackId : 0,
        webPath: (m && m.webPath) || '',
        displayName: (m && m.displayName) || ''
      })
    },
    emitStop () {
      var m = this.roomMusic
      this.$emit('sync', {
        action: 'stop',
        trackId: m && m.trackId != null ? m.trackId : 0,
        webPath: (m && m.webPath) || '',
        displayName: (m && m.displayName) || ''
      })
    }
  }
}
</script>

<style scoped>
.dp-music-box__hint {
  font-size: 12px;
  color: var(--dp-text-muted, #909399);
  line-height: 1.45;
  margin: 0 0 10px;
}
.dp-music-box__status {
  font-size: 13px;
  color: var(--dp-text-secondary, #606266);
  margin: 0 0 12px;
  line-height: 1.4;
}
.dp-music-box__muted {
  font-size: 13px;
  color: var(--dp-text-muted, #909399);
  margin: 0;
}
.dp-music-box__err {
  font-size: 13px;
  color: var(--dp-danger, #f56c6c);
  margin: 0;
}
.dp-music-box__list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: min(52vh, 360px);
  overflow: auto;
}
.dp-music-box__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--dp-panel-border, rgba(255, 255, 255, 0.08));
}
.dp-music-box__name {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  color: var(--dp-text-primary, #303133);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.dp-music-box__actions {
  flex-shrink: 0;
}
.dp-music-box__btn {
  padding: 6px 12px;
  font-size: 13px;
}
.dp-music-box__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--dp-panel-border, rgba(255, 255, 255, 0.08));
}
</style>

<style src="../styles/dp-game-modals.css"></style>
