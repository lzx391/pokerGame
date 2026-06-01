<template>
  <transition name="dp-hh-root">
    <div v-if="visible" class="dp-hh" @click.self="close">
      <div class="dp-hh__shell" :class="animClass" @animationend="onAnimEnd">
        <!-- CRT 图层 -->
        <div class="dp-hh__scanlines" />
        <div v-if="showCrt" class="dp-hh__snow" :class="{ 'dp-hh__snow--active': snowing }">
          <span class="dp-hh__snow-noise" />
        </div>
        <div v-if="showCrt" class="dp-hh__flash" :class="{ 'dp-hh__flash--pulse': flashing }" />

        <!-- 头部 -->
        <div class="dp-hh__head">
          <span class="dp-hh__title">&gt; HAND HISTORY</span>
          <button class="dp-hh__close" @click="close">[X]</button>
        </div>

        <!-- 加载/错误状态 -->
        <div v-if="loading" class="dp-hh__status">加载中...</div>
        <div v-else-if="loadError" class="dp-hh__status dp-hh__status--err">[ERR] {{ loadError }}</div>

        <!-- 手牌列表 -->
        <div v-else class="dp-hh__list" ref="listEl">
          <div v-if="!rows.length" class="dp-hh__empty">暂无对局记录</div>
          <div
            v-for="(r, i) in rows"
            :key="r.handHistoryId || i"
            class="dp-hh__row"
            :class="{ 'dp-hh__row--cursor': i === cursor, 'dp-hh__row--win': r.netChips > 0, 'dp-hh__row--lose': r.netChips < 0 }"
            @click="openDetail(r)"
          >
            <span class="dp-hh__col-time">{{ formatTime(r.endedAtMs) }}</span>
            <span class="dp-hh__col-pot">池 {{ r.mainPotBeforeSettlement || '-' }}</span>
            <span class="dp-hh__col-net" :class="{ 'dp-hh__col-net--plus': r.netChips > 0, 'dp-hh__col-net--minus': r.netChips < 0 }">{{ r.netChips > 0 ? '+' + r.netChips : (r.netChips || 0) }}</span>
            <span class="dp-hh__col-room">{{ r.roomId || '-' }}</span>
          </div>
        </div>

        <!-- 提示 -->
        <div class="dp-hh__hint-bar">
          <span>&uarr;&darr; nav</span>
          <span>Enter detail</span>
          <span>hands for more</span>
          <span>Esc close</span>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
export default {
  name: 'DpHandHistoryViewer',
  inject: { dpGameView: { default: null } },
  props: {
    open: { type: Boolean, default: false }
  },
  data: function () {
    return {
      visible: false,
      phase: 'ready',
      cursor: 0,
      rows: [],
      loading: false,
      loadError: '',
      snowTimer: null,
      flashTimer: null
    }
  },
  computed: {
    vm: function () { return this.dpGameView },
    showCrt: function () {
      return this.vm && this.vm.gameUiTheme === 'retro8bit' && this.vm.viewportWidth > 600 && !this.vm.ecoMode && !this.vm.prefersReducedMotion
    },
    snowing: function () { return this.phase === 'snowing' },
    flashing: function () { return this.phase === 'flashing' },
    animClass: function () {
      if (!this.showCrt) return 'dp-hh__shell--instant'
      return {
        'dp-hh__shell--slide-in': this.phase === 'sliding',
        'dp-hh__shell--ready': this.phase === 'ready' || this.phase === 'flashing'
      }
    }
  },
  watch: {
    open: function (v) {
      if (v) this.startOpen(); else this.doClose()
    },
    visible: function (v) {
      if (v && this.showCrt) { this.phase = 'sliding' }
      else if (v && !this.showCrt) { this.phase = 'ready'; this.fetchList() }
    }
  },
  beforeDestroy: function () { this.clearTimers() },
  methods: {
    clearTimers: function () {
      if (this.snowTimer) { clearTimeout(this.snowTimer); this.snowTimer = null }
      if (this.flashTimer) { clearTimeout(this.flashTimer); this.flashTimer = null }
    },
    startOpen: function () {
      this.visible = true; this.cursor = 0; this.loadError = ''
      if (this.showCrt) { this.phase = 'sliding' }
      else { this.phase = 'ready'; this.fetchList() }
    },
    close: function () { this.$emit('update:open', false) },
    doClose: function () { this.clearTimers(); this.visible = false; this.phase = 'ready' },
    onAnimEnd: function (e) {
      if (e.target !== e.currentTarget) return
      var n = e.animationName || ''
      if (n.indexOf('dp-hh-slide-in') !== -1) {
        this.phase = 'snowing'; this.fetchList()
        var self = this
        this.snowTimer = setTimeout(function () {
          self.phase = 'flashing'
          self.flashTimer = setTimeout(function () { self.phase = 'ready' }, 100)
        }, 350)
      }
    },
    // ---- 数据 ----
    fetchList: function () {
      var vm = this.vm
      if (!vm || !vm.user) { this.loadError = '无用户数据'; return }
      this.loading = true; this.loadError = ''
      var self = this
      vm.$http.get('/dpHandHistory/list', {
        params: { userId: Number(vm.user.userId), page: 1, pageSize: 10 }
      }).then(function (res) {
        var data = res.data || {}
        self.rows = Array.isArray(data.records) ? data.records : []
        self.cursor = 0
      }).catch(function (e) {
        self.loadError = e && e.message ? e.message : '加载失败'
        self.rows = []
      }).finally(function () { self.loading = false })
    },
    // ---- 交互 ----
    openDetail: function (r) {
      if (!r || !r.handHistoryId) return
      // 复用现有 HandHistoryDetail 组件 —— 通过 game.vue
      if (this.vm && typeof this.vm.openHandHistoryDetail === 'function') {
        this.vm.openHandHistoryDetail(r.handHistoryId)
      }
    },
    formatTime: function (ms) {
      if (!ms) return '--/-- --:--'
      var d = new Date(ms)
      var M = String(d.getMonth() + 1).padStart(2, '0')
      var D = String(d.getDate()).padStart(2, '0')
      var h = String(d.getHours()).padStart(2, '0')
      var m = String(d.getMinutes()).padStart(2, '0')
      return M + '/' + D + ' ' + h + ':' + m
    },
    // ---- 键盘 ----
    onKey: function (e) {
      if (e.key === 'Escape') { e.preventDefault(); this.close(); return }
      if (e.key === 'ArrowUp') { e.preventDefault(); this.cursor = Math.max(0, this.cursor - 1); return }
      if (e.key === 'ArrowDown') { e.preventDefault(); this.cursor = Math.min(this.rows.length - 1, this.cursor + 1); return }
      if (e.key === 'Enter') { e.preventDefault(); var r = this.rows[this.cursor]; if (r) this.openDetail(r); return }
    }
  }
}
</script>

<style scoped>
.dp-hh { position:fixed;inset:0;z-index:10075;background:transparent;pointer-events:none; }
.dp-hh-root-enter-active{transition:opacity 0.12s}
.dp-hh-root-leave-active{transition:opacity 0.15s}
.dp-hh-root-enter,.dp-hh-root-leave-to{opacity:0}

.dp-hh__shell {
  position:absolute;right:max(12px,env(safe-area-inset-right));top:max(12px,env(safe-area-inset-top));
  width:min(400px,calc(100vw - 24px));height:min(500px,calc(100vh - 40px));min-height:280px;
  background:rgba(8,10,12,0.97);border:2px solid rgba(74,246,38,0.34);border-radius:4px;
  display:flex;flex-direction:column;pointer-events:auto;
  font-family:'Courier New',ui-monospace,'PingFang SC',monospace;
  box-shadow:0 0 0 1px #000,-4px 8px 28px rgba(0,0,0,0.6);overflow:hidden;
}
.dp-hh__shell--slide-in{animation:dp-hh-slide-in 0.28s cubic-bezier(0.22,0.61,0.36,1) forwards}
.dp-hh__shell--instant{transform:translateX(0);opacity:1}
@keyframes dp-hh-slide-in{from{transform:translateX(105%);opacity:0}to{transform:translateX(0);opacity:1}}

.dp-hh__scanlines{position:absolute;inset:0;z-index:2;pointer-events:none;background:repeating-linear-gradient(to bottom,transparent 0 2px,rgba(0,0,0,0.12) 2px 3px);background-size:100% 3px;opacity:0.14}
.dp-hh__snow{position:absolute;inset:-4%;z-index:4;opacity:0;pointer-events:none;overflow:hidden;transition:opacity 0.1s;background:#0e1012}
.dp-hh__snow--active{opacity:0.94}
.dp-hh__snow-noise{position:absolute;inset:0;opacity:0.7;background-image:repeating-radial-gradient(circle at 18% 22%,rgba(255,255,255,0.55) 0 0.35px,transparent 0.45px),repeating-radial-gradient(circle at 75% 60%,rgba(210,218,228,0.45) 0 0.3px,transparent 0.4px);background-size:3px 3px,4px 4px}
.dp-hh__flash{position:absolute;inset:0;z-index:5;pointer-events:none;opacity:0}
.dp-hh__flash--pulse{animation:dp-hh-flash 0.1s ease-out}
@keyframes dp-hh-flash{0%{opacity:1;background:rgba(248,250,252,0.9)}100%{opacity:0;background:transparent}}

.dp-hh__head{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:8px 12px;flex-shrink:0;border-bottom:1px solid rgba(74,246,38,0.22);background:color-mix(in srgb,var(--dp-panel-bg,#12151a) 88%,var(--dp-accent,#4af626) 12%);position:relative;z-index:3}
.dp-hh__title{font-family:'Press Start 2P',monospace;font-size:10px;color:#4af626;text-shadow:0 0 6px rgba(74,246,38,0.55);letter-spacing:0.04em}
.dp-hh__close{flex-shrink:0;width:30px;height:30px;padding:0;border:1px solid rgba(74,246,38,0.28);border-radius:0;background:#0a0c0e;color:#4af626;font-family:'Courier New',monospace;font-size:13px;cursor:pointer}
.dp-hh__close:hover{background:#4af626;color:#080a0c}

.dp-hh__status{padding:20px;text-align:center;color:#72f052;font-size:12px;position:relative;z-index:1}
.dp-hh__status--err{color:#ff6666}

.dp-hh__list{flex:1 1 auto;min-height:0;overflow-y:auto;position:relative;z-index:1}
.dp-hh__row{display:flex;align-items:center;gap:8px;padding:6px 12px;cursor:pointer;border:1px solid transparent;border-bottom:1px solid rgba(74,246,38,0.06);font-size:11px;transition:background 0.08s,border-color 0.08s}
.dp-hh__row:hover{background:rgba(74,246,38,0.04)}
.dp-hh__row--cursor{border-color:rgba(74,246,38,0.5);background:rgba(74,246,38,0.08)}
.dp-hh__row--win{border-left:3px solid rgba(114,240,82,0.4)}
.dp-hh__row--lose{border-left:3px solid rgba(255,68,68,0.4)}
.dp-hh__col-time{color:rgba(74,246,38,0.5);width:72px;flex-shrink:0}
.dp-hh__col-pot{color:#e0f0d8;width:80px;flex-shrink:0}
.dp-hh__col-net{flex:1;text-align:right;font-weight:bold;color:#e0f0d8}
.dp-hh__col-net--plus{color:#72f052;text-shadow:0 0 3px rgba(114,240,82,0.3)}
.dp-hh__col-net--minus{color:#ff6666}
.dp-hh__col-room{color:rgba(74,246,38,0.4);font-size:10px}
.dp-hh__empty{text-align:center;color:rgba(74,246,38,0.35);padding:30px;font-size:12px}

.dp-hh__hint-bar{display:flex;gap:12px;padding:4px 14px 8px;font-size:9px;color:rgba(74,246,38,0.28);border-top:1px solid rgba(74,246,38,0.06);position:relative;z-index:1;user-select:none}

@media(prefers-reduced-motion:reduce){.dp-hh__shell--slide-in{animation:none!important}}
</style>
