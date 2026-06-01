<template>
  <transition name="dp-mp-root">
    <div v-if="visible" class="dp-mp" @click.self="close">
      <div class="dp-mp__shell" :class="animClass" @animationend="onAnimEnd">
        <!-- CRT 图层 -->
        <div class="dp-mp__scanlines" />
        <div v-if="showCrt" class="dp-mp__snow" :class="{ 'dp-mp__snow--active': snowing }">
          <span class="dp-mp__snow-noise" />
        </div>
        <div v-if="showCrt" class="dp-mp__flash" :class="{ 'dp-mp__flash--pulse': flashing }" />

        <!-- 头部 -->
        <div class="dp-mp__head">
          <span class="dp-mp__title">&gt; MUSIC PLAYER</span>
          <button class="dp-mp__close" @click="close">[X]</button>
        </div>

        <!-- 当前播放信息 -->
        <div class="dp-mp__now">
          <span v-if="nowPlaying" class="dp-mp__now-icon">&#9835;</span>
          <span class="dp-mp__now-text">{{ nowPlaying || '已停止' }}</span>
          <span v-if="nowPlaying" class="dp-mp__now-by">({{ lastOperator }})</span>
        </div>

        <!-- ASCII 进度条 -->
        <div v-if="nowPlaying" class="dp-mp__progress">
          <span class="dp-mp__progress-bar">[{{ progressFilled }}{{ progressEmpty }}]</span>
        </div>

        <!-- 曲目列表 -->
        <div class="dp-mp__list" ref="trackList">
          <div
            v-for="(t, i) in tracks"
            :key="t.id"
            class="dp-mp__row"
            :class="{ 'dp-mp__row--cursor': i === cursor, 'dp-mp__row--active': isTrackActive(t) }"
            @click="playTrack(t)"
          >
            <span class="dp-mp__row-num">{{ i + 1 }}</span>
            <span class="dp-mp__row-name">{{ t.displayName || '#' + t.id }}</span>
            <span v-if="isTrackActive(t)" class="dp-mp__row-playing">&#9654; NOW</span>
          </div>
          <p v-if="!tracks.length" class="dp-mp__empty">曲库为空</p>
        </div>

        <!-- 控制栏 -->
        <div class="dp-mp__controls">
          <button class="dp-mp__btn" :disabled="!tracks.length" @click="playSelected">&gt; PLAY</button>
          <button class="dp-mp__btn" :disabled="!nowPlaying" @click="doPause">&gt; {{ isPaused ? 'RESUME' : 'PAUSE' }}</button>
          <button class="dp-mp__btn" :disabled="!nowPlaying" @click="doStop">&gt; STOP</button>
        </div>

        <!-- 提示栏 -->
        <div class="dp-mp__hint-bar">
          <span>&uarr;&darr; nav</span>
          <span>Enter play</span>
          <span>Esc close</span>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
import { mapState } from 'vuex'

export default {
  name: 'DpMusicPlayer',
  inject: { dpGameView: { default: null } },
  props: {
    open: { type: Boolean, default: false }
  },
  data: function () {
    return {
      visible: false,
      phase: 'ready',
      cursor: 0,
      snowTimer: null,
      flashTimer: null,
      progressTick: 0
    }
  },
  computed: {
    ...mapState('dpGame', ['musicTracks', 'musicTracksLoading', 'roomMusicState', 'gameUiTheme', 'ecoMode']),
    vm: function () { return this.dpGameView },
    showCrt: function () {
      return this.gameUiTheme === 'retro8bit' && this.vm && this.vm.viewportWidth > 600 && !this.ecoMode && !this.vm.prefersReducedMotion
    },
    snowing: function () { return this.phase === 'snowing' },
    flashing: function () { return this.phase === 'flashing' },
    animClass: function () {
      if (!this.showCrt) return 'dp-mp__shell--instant'
      return {
        'dp-mp__shell--slide-in': this.phase === 'sliding',
        'dp-mp__shell--ready': this.phase === 'ready' || this.phase === 'flashing'
      }
    },
    tracks: function () { return this.musicTracks || [] },
    nowPlaying: function () {
      var ms = this.roomMusicState
      if (!ms || !ms.displayName || ms.action === 'stop') return ''
      return ms.displayName
    },
    lastOperator: function () {
      var ms = this.roomMusicState
      return (ms && ms.byNickname) || ''
    },
    isPaused: function () {
      var ms = this.roomMusicState
      return ms && ms.action === 'pause'
    },
    progressFilled: function () {
      var n = Math.min(16, Math.floor((this.progressTick % 60) / 60 * 16))
      var s = ''
      for (var i = 0; i < n; i++) s += '='
      if (this.nowPlaying && n < 16) s += '>'
      return s
    },
    progressEmpty: function () {
      var n = Math.min(16, Math.floor((this.progressTick % 60) / 60 * 16))
      var s = ''
      var remaining = this.nowPlaying ? 15 - n : 16
      for (var i = 0; i < remaining; i++) s += ' '
      return s
    }
  },
  watch: {
    open: function (v) {
      if (v) this.startOpen(); else this.doClose()
    },
    visible: function (v) {
      if (v && this.showCrt) {
        this.phase = 'sliding'
      } else if (v && !this.showCrt) {
        this.phase = 'ready'
      }
    }
  },
  mounted: function () {
    if (this.open) this.startOpen()
    this.startProgressTick()
  },
  beforeDestroy: function () {
    this.clearTimers()
  },
  methods: {
    clearTimers: function () {
      if (this.snowTimer) { clearTimeout(this.snowTimer); this.snowTimer = null }
      if (this.flashTimer) { clearTimeout(this.flashTimer); this.flashTimer = null }
    },
    startProgressTick: function () {
      var self = this
      setInterval(function () { self.progressTick++ }, 1000)
    },
    startOpen: function () {
      this.visible = true; this.cursor = 0
      if (this.showCrt) {
        this.phase = 'sliding'
      } else {
        this.phase = 'ready'
      }
    },
    close: function () { this.$emit('update:open', false) },
    doClose: function () {
      this.clearTimers(); this.visible = false; this.phase = 'ready'
    },
    onAnimEnd: function (e) {
      if (e.target !== e.currentTarget) return
      var n = e.animationName || ''
      if (n.indexOf('dp-mp-slide-in') !== -1) {
        this.phase = 'snowing'
        var self = this
        this.snowTimer = setTimeout(function () {
          self.phase = 'flashing'
          self.flashTimer = setTimeout(function () { self.phase = 'ready' }, 100)
        }, 300)
      }
    },
    // ---- 控制 ----
    playSelected: function () {
      var t = this.tracks[this.cursor]
      if (t) this.playTrack(t)
    },
    playTrack: function (t) {
      if (!t || !this.vm) return
      this.vm.sendRoomMusicSync({ action: 'play', trackId: t.id, webPath: t.webPath, displayName: t.displayName || '' })
    },
    doPause: function () {
      var ms = this.roomMusicState
      if (!ms || !this.vm) return
      if (ms.action === 'pause') {
        this.vm.sendRoomMusicSync({ action: 'play', trackId: ms.trackId, webPath: ms.webPath, displayName: ms.displayName || '' })
      } else {
        this.vm.sendRoomMusicSync({ action: 'pause', trackId: ms.trackId, webPath: ms.webPath, displayName: ms.displayName || '' })
      }
    },
    doStop: function () {
      var ms = this.roomMusicState
      if (!ms || !this.vm) return
      this.vm.sendRoomMusicSync({ action: 'stop', trackId: ms.trackId, webPath: ms.webPath, displayName: ms.displayName || '' })
    },
    isTrackActive: function (t) {
      var ms = this.roomMusicState
      return ms && ms.trackId === t.id && ms.action !== 'stop'
    },
    // ---- 键盘 ----
    onKey: function (e) {
      if (e.key === 'Escape') { e.preventDefault(); this.close(); return }
      if (e.key === 'ArrowUp') { e.preventDefault(); this.cursor = Math.max(0, this.cursor - 1); return }
      if (e.key === 'ArrowDown') { e.preventDefault(); this.cursor = Math.min(this.tracks.length - 1, this.cursor + 1); return }
      if (e.key === 'Enter') { e.preventDefault(); this.playSelected(); return }
    }
  }
}
</script>

<style scoped>
.dp-mp { position:fixed;inset:0;z-index:10075;background:transparent;pointer-events:none; }
.dp-mp-root-enter-active{transition:opacity 0.12s}
.dp-mp-root-leave-active{transition:opacity 0.15s}
.dp-mp-root-enter,.dp-mp-root-leave-to{opacity:0}

.dp-mp__shell {
  position:absolute;right:max(12px,env(safe-area-inset-right));top:max(12px,env(safe-area-inset-top));
  width:min(380px,calc(100vw - 24px));height:min(480px,calc(100vh - 40px));min-height:300px;
  background:rgba(8,10,12,0.97);border:2px solid rgba(74,246,38,0.34);border-radius:4px;
  display:flex;flex-direction:column;pointer-events:auto;
  font-family:'Courier New',ui-monospace,'PingFang SC',monospace;
  box-shadow:0 0 0 1px #000,-4px 8px 28px rgba(0,0,0,0.6);overflow:hidden;
}
.dp-mp__shell--slide-in{animation:dp-mp-slide-in 0.28s cubic-bezier(0.22,0.61,0.36,1) forwards}
.dp-mp__shell--instant{transform:translateX(0);opacity:1}
@keyframes dp-mp-slide-in{from{transform:translateX(105%);opacity:0}to{transform:translateX(0);opacity:1}}

.dp-mp__scanlines{position:absolute;inset:0;z-index:2;pointer-events:none;background:repeating-linear-gradient(to bottom,transparent 0 2px,rgba(0,0,0,0.12) 2px 3px);background-size:100% 3px;opacity:0.14}
.dp-mp__snow{position:absolute;inset:-4%;z-index:4;opacity:0;pointer-events:none;overflow:hidden;transition:opacity 0.1s;background:#0e1012}
.dp-mp__snow--active{opacity:0.94}
.dp-mp__snow-noise{position:absolute;inset:0;opacity:0.7;background-image:repeating-radial-gradient(circle at 12% 18%,rgba(255,255,255,0.6) 0 0.4px,transparent 0.5px),repeating-radial-gradient(circle at 78% 62%,rgba(210,218,228,0.5) 0 0.35px,transparent 0.45px);background-size:3px 3px,4px 4px}
.dp-mp__flash{position:absolute;inset:0;z-index:5;pointer-events:none;opacity:0}
.dp-mp__flash--pulse{animation:dp-mp-flash 0.1s ease-out}
@keyframes dp-mp-flash{0%{opacity:1;background:rgba(248,250,252,0.9)}100%{opacity:0;background:transparent}}

.dp-mp__head{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:8px 12px;flex-shrink:0;border-bottom:1px solid rgba(74,246,38,0.22);background:color-mix(in srgb,var(--dp-panel-bg,#12151a) 88%,var(--dp-accent,#4af626) 12%);position:relative;z-index:3}
.dp-mp__title{font-family:'Press Start 2P',monospace;font-size:10px;color:#4af626;text-shadow:0 0 6px rgba(74,246,38,0.55);letter-spacing:0.04em}
.dp-mp__close{flex-shrink:0;width:30px;height:30px;padding:0;border:1px solid rgba(74,246,38,0.28);border-radius:0;background:#0a0c0e;color:#4af626;font-family:'Courier New',monospace;font-size:13px;cursor:pointer}
.dp-mp__close:hover{background:#4af626;color:#080a0c}

.dp-mp__now{display:flex;align-items:center;gap:8px;padding:8px 14px;border-bottom:1px solid rgba(74,246,38,0.12);position:relative;z-index:1;min-height:18px}
.dp-mp__now-icon{color:#4af626;font-size:16px}
.dp-mp__now-text{color:#72f052;font-size:12px;text-shadow:0 0 4px rgba(114,240,82,0.3)}
.dp-mp__now-by{color:rgba(74,246,38,0.5);font-size:10px;margin-left:auto}
.dp-mp__progress{padding:2px 14px 6px;position:relative;z-index:1}
.dp-mp__progress-bar{color:#4af626;font-size:11px;text-shadow:0 0 4px rgba(74,246,38,0.4);letter-spacing:0.05em}

.dp-mp__list{flex:1 1 auto;min-height:0;overflow-y:auto;padding:4px 8px;position:relative;z-index:1}
.dp-mp__row{display:flex;align-items:center;gap:8px;padding:6px 8px;cursor:pointer;border:1px solid transparent;font-size:12px;transition:background 0.08s, border-color 0.08s}
.dp-mp__row:hover{background:rgba(74,246,38,0.06)}
.dp-mp__row--cursor{border-color:rgba(74,246,38,0.5);background:rgba(74,246,38,0.08)}
.dp-mp__row--active{border-left:3px solid #4af626}
.dp-mp__row-num{color:rgba(74,246,38,0.4);width:22px;text-align:right;flex-shrink:0}
.dp-mp__row-name{color:#e0f0d8;flex:1}
.dp-mp__row-playing{color:#4af626;font-size:10px;text-shadow:0 0 4px rgba(74,246,38,0.6);animation:dp-mp-blink 1.2s step-end infinite}
@keyframes dp-mp-blink{0%,100%{opacity:1}50%{opacity:0.5}}
.dp-mp__empty{text-align:center;color:rgba(74,246,38,0.35);padding:20px;font-size:12px}

.dp-mp__controls{display:flex;gap:6px;padding:8px 14px;border-top:1px solid rgba(74,246,38,0.16);position:relative;z-index:1}
.dp-mp__btn{flex:1;padding:8px 0;border:1px solid rgba(74,246,38,0.28);border-radius:0;background:rgba(10,26,10,0.6);color:#4af626;font-family:'Courier New',ui-monospace,monospace;font-size:11px;cursor:pointer;letter-spacing:0.05em;text-shadow:0 0 3px rgba(74,246,38,0.3);transition:background 0.08s,color 0.08s}
.dp-mp__btn:hover:not(:disabled){background:#4af626;color:#080a0c}
.dp-mp__btn:disabled{opacity:0.3;cursor:default}

.dp-mp__hint-bar{display:flex;gap:12px;padding:4px 14px 8px;font-size:9px;color:rgba(74,246,38,0.28);border-top:1px solid rgba(74,246,38,0.06);position:relative;z-index:1;user-select:none}

@media(prefers-reduced-motion:reduce){.dp-mp__shell--slide-in{animation:none!important}.dp-mp__row-playing{animation:none}}
</style>
