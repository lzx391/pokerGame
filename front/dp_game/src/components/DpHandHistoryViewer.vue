<template>
  <transition :name="isLobbyPage ? '' : 'dp-hh-root'">
    <div
      v-if="rootVisible"
      class="dp-hh"
      :class="{ 'dp-hh--lobby-page': isLobbyPage }"
      @click.self="onBackdropClick"
    >
      <div class="dp-hh__shell" :class="shellLayoutClass" @animationend="onAnimEnd">
        <!-- CRT 图层 -->
        <div class="dp-hh__scanlines" />
        <div v-if="showCrt" class="dp-hh__snow" :class="{ 'dp-hh__snow--active': snowing }">
          <span class="dp-hh__snow-noise" />
        </div>
        <div v-if="showCrt" class="dp-hh__flash" :class="{ 'dp-hh__flash--pulse': flashing }" />

        <!-- 头部 -->
        <div class="dp-hh__head">
          <span class="dp-hh__title">{{ headTitle }}</span>
          <button class="dp-hh__close" @click="close">{{ isLobbyPage ? '[BACK]' : '[X]' }}</button>
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

        <!-- 分页 + 提示 -->
        <div class="dp-hh__footer">
          <div class="dp-hh__pager">
            <button class="dp-hh__pager-btn" :class="{ 'dp-hh__pager-btn--dim': currentPage <= 1 }" :disabled="currentPage <= 1" @click="prevPage">&lt;&lt;</button>
            <span class="dp-hh__pager-info">P{{ currentPage }}/{{ totalPages || 1 }}</span>
            <button class="dp-hh__pager-btn" :class="{ 'dp-hh__pager-btn--dim': currentPage >= totalPages }" :disabled="currentPage >= totalPages" @click="nextPage">&gt;&gt;</button>
          </div>
          <div v-if="!isLobbyPage" class="dp-hh__hint-bar">
            <span>W/S nav</span>
            <span>&larr;&rarr; page</span>
            <span>Enter detail</span>
            <span>Ctrl+D close</span>
          </div>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
import { mapState } from 'vuex'
import { ensureDpUserIdInStorage } from '@/utils/dpEnsureUserId'

export default {
  name: 'DpHandHistoryViewer',
  inject: { dpGameView: { default: null } },
  props: {
    open: { type: Boolean, default: false },
    /** mine：`/dpHandHistory/list`；withOpponent：与同局真人双方的共同列表 */
    listMode: {
      type: String,
      default: 'mine',
      validator: function (v) { return v === 'mine' || v === 'withOpponent' }
    },
    /** `listMode===withOpponent` 时为对方 dp_user.id */
    otherUserId: { type: Number, default: null },
    /** 列表标题展示用，须与卡片/dpDisplayNickname 一致 */
    opponentDisplayName: { type: String, default: '' },
    /** game-overlay: in-game panel; lobby-page: full-page /hand-history route */
    context: {
      type: String,
      default: 'game-overlay',
      validator: function (v) { return v === 'game-overlay' || v === 'lobby-page' }
    }
  },
  data: function () {
    return {
      visible: false,
      phase: 'ready',
      cursor: 0,
      rows: [],
      currentPage: 1,
      totalPages: 1,
      pageSize: 10,
      loading: false,
      loadError: '',
      snowTimer: null,
      flashTimer: null,
      user: null,
      viewportWidth: typeof window !== 'undefined' ? window.innerWidth : 1024,
      prefersReducedMotion: false,
      lobbyReady: false
    }
  },
  computed: {
    ...mapState('dpGame', ['gameUiTheme', 'ecoMode']),
    isLobbyPage: function () { return this.context === 'lobby-page' },
    vm: function () { return this.isLobbyPage ? null : this.dpGameView },
    rootVisible: function () {
      return this.isLobbyPage ? this.lobbyReady : this.visible
    },
    showCrt: function () {
      var retro = this.isLobbyPage
        ? this.gameUiTheme === 'retro8bit'
        : (this.vm && this.vm.gameUiTheme === 'retro8bit')
      if (!retro) return false
      var vw = this.isLobbyPage ? this.viewportWidth : (this.vm && this.vm.viewportWidth)
      return vw > 600 && !this.ecoMode && !this.prefersReducedMotion
    },
    snowing: function () { return this.phase === 'snowing' },
    flashing: function () { return this.phase === 'flashing' },
    animClass: function () {
      if (!this.showCrt) return 'dp-hh__shell--instant'
      return {
        'dp-hh__shell--slide-in': this.phase === 'sliding',
        'dp-hh__shell--ready': this.phase === 'ready' || this.phase === 'flashing'
      }
    },
    shellLayoutClass: function () {
      var cls = [this.animClass]
      if (this.isLobbyPage) cls.push('dp-hh__shell--lobby-page')
      return cls
    },
    headTitle: function () {
      if (
        this.listMode === 'withOpponent'
        && this.opponentDisplayName
      ) {
        return '> VS ' + String(this.opponentDisplayName).toUpperCase()
      }
      return '> HAND HISTORY'
    }
  },
  watch: {
    open: function (v) {
      if (this.isLobbyPage) return
      if (v) this.startOpen(); else this.doClose()
    },
    otherUserId: function () {
      if (this.isLobbyPage || !this.visible) return
      this.currentPage = 1
      this.cursor = 0
      this.fetchList()
    },
    visible: function (v) {
      if (this.isLobbyPage) return
      if (v && this.showCrt) { this.phase = 'sliding' }
      else if (v && !this.showCrt) { this.phase = 'ready'; this.fetchList() }
    }
  },
  created: function () {
    if (this.isLobbyPage) this.syncMotionPrefs()
  },
  mounted: function () {
    if (!this.isLobbyPage) return
    var self = this
    this._onResize = function () { self.viewportWidth = window.innerWidth }
    window.addEventListener('resize', this._onResize)
    this.bootstrapLobbyPage()
  },
  beforeDestroy: function () {
    this.clearTimers()
    if (this._onResize) window.removeEventListener('resize', this._onResize)
  },
  methods: {
    syncMotionPrefs: function () {
      if (typeof window === 'undefined' || !window.matchMedia) {
        this.prefersReducedMotion = false
        return
      }
      this.prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    },
    bootstrapLobbyPage: async function () {
      try {
        var raw = localStorage.getItem('userInfo')
        this.user = raw ? JSON.parse(raw) : null
      } catch (e) {
        this.user = null
      }
      if (!this.user || !this.user.nickname) {
        this.$router.replace('/login')
        return
      }
      this.user = (await ensureDpUserIdInStorage(this.$http)) || this.user
      var uid = Number(this.user && this.user.userId)
      if (!this.user || isNaN(uid) || uid <= 0) {
        this.$router.replace('/login')
        return
      }
      this.user.userId = uid
      this.lobbyReady = true
      this.startOpen()
    },
    onBackdropClick: function () {
      if (this.isLobbyPage) return
      this.close()
    },
    clearTimers: function () {
      if (this.snowTimer) { clearTimeout(this.snowTimer); this.snowTimer = null }
      if (this.flashTimer) { clearTimeout(this.flashTimer); this.flashTimer = null }
    },
    startOpen: function () {
      if (this.listMode === 'withOpponent') {
        var oid = Number(this.otherUserId)
        if (!oid || oid <= 0 || isNaN(oid)) {
          this.loadError = '无效的对手 ID'
          this.visible = true
          this.phase = 'ready'
          this.rows = []
          return
        }
      }
      this.visible = true; this.cursor = 0; this.currentPage = 1; this.loadError = ''
      if (this.showCrt) { this.phase = 'sliding' }
      else { this.phase = 'ready'; this.fetchList() }
    },
    close: function () {
      if (this.isLobbyPage) {
        this.$router.push('/home')
        return
      }
      this.$emit('update:open', false)
    },
    doClose: function () {
      if (this.isLobbyPage) return
      this.clearTimers(); this.visible = false; this.phase = 'ready'
    },
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
      var user = this.isLobbyPage ? this.user : (this.vm && this.vm.user)
      var http = this.isLobbyPage ? this.$http : (this.vm && this.vm.$http)
      if (!user || !http) { this.loadError = '无用户数据'; return }
      if (this.listMode === 'withOpponent') {
        var otherId = Number(this.otherUserId)
        if (!otherId || otherId <= 0 || isNaN(otherId)) {
          this.loadError = '无效的对手 ID'
          this.rows = []
          this.loading = false
          return
        }
      }
      this.loading = true; this.loadError = ''
      var self = this
      var params = {
        page: this.currentPage,
        pageSize: this.pageSize
      }
      var url = '/dpHandHistory/list'
      if (this.listMode === 'withOpponent') {
        url = '/dpHandHistory/checkUserAndOtherPlayerHandHistoryList'
        params.otherUserId = Number(this.otherUserId)
      }
      http.get(url, {
        params: params
      }).then(function (res) {
        var body = res.data || {}
        // 兼容两种响应格式：{records,total} 或 {code,data:{records,total}}
        var data = (body.data && typeof body.data === 'object' && !Array.isArray(body.data)) ? body.data : body
        console.log('[handHistory] raw res.data keys:', Object.keys(body), 'data keys:', Object.keys(data), 'total:', data.total, 'records:', (data.records || []).length)
        self.rows = Array.isArray(data.records) ? data.records : []
        self.currentPage = typeof data.page === 'number' ? data.page : self.currentPage
        self.totalPages = Math.ceil((typeof data.total === 'number' ? data.total : 0) / self.pageSize) || 1
        self.cursor = 0
      }).catch(function (e) {
        console.error('[handHistory] fetch error:', e)
        self.loadError = e && e.message ? e.message : '加载失败'
        self.rows = []
      }).finally(function () { self.loading = false })
    },
    prevPage: function () {
      if (this.currentPage <= 1) return
      this.currentPage--; this.fetchList()
    },
    nextPage: function () {
      if (this.currentPage >= this.totalPages) return
      this.currentPage++; this.fetchList()
    },
    // ---- 交互 ----
    openDetail: function (r) {
      if (!r || !r.handHistoryId) return
      if (this.isLobbyPage) {
        this.$router.push('/hand-history/detail/' + encodeURIComponent(String(r.handHistoryId)))
        return
      }
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
      if (e.ctrlKey && e.key === 'd') { e.preventDefault(); this.close(); return true }
      if (e.key === 'Escape') { e.preventDefault(); this.close(); return true }
      // W/↑ 上移
      if (e.key === 'w' || e.key === 'W' || e.key === 'ArrowUp') {
        e.preventDefault(); this.cursor = Math.max(0, this.cursor - 1); return true
      }
      // S/↓ 下移
      if (e.key === 's' || e.key === 'S' || e.key === 'ArrowDown') {
        e.preventDefault(); this.cursor = Math.min(this.rows.length - 1, this.cursor + 1); return true
      }
      // ← 上一页
      if (e.key === 'ArrowLeft') {
        e.preventDefault(); this.prevPage(); return true
      }
      // → 下一页
      if (e.key === 'ArrowRight') {
        e.preventDefault(); this.nextPage(); return true
      }
      // Enter 打开详情
      if (e.key === 'Enter') {
        e.preventDefault(); var r = this.rows[this.cursor]; if (r) this.openDetail(r); return true
      }
      return false
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
  width:min(400px,calc(100vw - 24px));height:min(520px,calc(100vh - 40px));min-height:300px;
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

.dp-hh__head{display:flex;align-items:center;justify-content:flex-start;gap:12px;width:100%;padding:10px 14px;flex-shrink:0;border-bottom:1px solid rgba(74,246,38,0.22);background:color-mix(in srgb,var(--dp-panel-bg,#12151a) 88%,var(--dp-accent,#4af626) 12%);position:relative;z-index:3}
.dp-hh__title{flex:1 1 auto;min-width:0;font-family:'Press Start 2P',monospace;font-size:12px;line-height:1.45;color:#4af626;text-shadow:0 0 6px rgba(74,246,38,0.55);letter-spacing:0.04em}
.dp-hh__close{margin-left:auto;flex-shrink:0;width:32px;height:32px;padding:0;border:1px solid rgba(74,246,38,0.28);border-radius:0;background:#0a0c0e;color:#4af626;font-family:'Courier New',monospace;font-size:14px;cursor:pointer}
.dp-hh__close:hover{background:#4af626;color:#080a0c}

.dp-hh__status{padding:22px;text-align:center;color:#72f052;font-size:14px;line-height:1.45;position:relative;z-index:1}
.dp-hh__status--err{color:#ff6666}

.dp-hh__list{flex:1 1 auto;min-height:0;overflow-y:auto;position:relative;z-index:1}
.dp-hh__row{display:flex;align-items:center;gap:10px;padding:8px 14px;cursor:pointer;border:1px solid transparent;border-bottom:1px solid rgba(74,246,38,0.06);font-size:13px;line-height:1.45;transition:background 0.08s,border-color 0.08s}
.dp-hh__row:hover{background:rgba(74,246,38,0.04)}
.dp-hh__row--cursor{border-color:rgba(74,246,38,0.5);background:rgba(74,246,38,0.08)}
.dp-hh__row--win{border-left:3px solid rgba(114,240,82,0.4)}
.dp-hh__row--lose{border-left:3px solid rgba(255,68,68,0.4)}
.dp-hh__col-time{color:rgba(74,246,38,0.5);width:84px;flex-shrink:0}
.dp-hh__col-pot{color:#e0f0d8;width:92px;flex-shrink:0}
.dp-hh__col-net{flex:1;text-align:right;font-weight:bold;color:#e0f0d8}
.dp-hh__col-net--plus{color:#72f052;text-shadow:0 0 3px rgba(114,240,82,0.3)}
.dp-hh__col-net--minus{color:#ff6666}
.dp-hh__col-room{color:rgba(74,246,38,0.4);font-size:12px}
.dp-hh__empty{text-align:center;color:rgba(74,246,38,0.35);padding:32px;font-size:14px;line-height:1.45}

.dp-hh__footer{flex-shrink:0;border-top:1px solid rgba(74,246,38,0.06);position:relative;z-index:1}
.dp-hh__pager{display:flex;align-items:center;justify-content:center;gap:12px;padding:8px 14px 3px}
.dp-hh__pager-btn{background:none;border:1px solid rgba(74,246,38,0.2);color:rgba(74,246,38,0.45);font-size:12px;cursor:pointer;padding:3px 10px;font-family:'Courier New',monospace;border-radius:2px;line-height:1.35;transition:all 0.08s}
.dp-hh__pager-btn:hover:not(:disabled){border-color:rgba(74,246,38,0.5);color:#4af626;background:rgba(74,246,38,0.06)}
.dp-hh__pager-btn--dim{color:rgba(74,246,38,0.12);border-color:rgba(74,246,38,0.06)}
.dp-hh__pager-btn:disabled{cursor:default}
.dp-hh__pager-info{color:rgba(74,246,38,0.55);font-size:12px;font-weight:bold}
.dp-hh__hint-bar{display:flex;gap:12px;padding:3px 14px 8px;font-size:11px;line-height:1.35;color:rgba(74,246,38,0.28);user-select:none}

@media(prefers-reduced-motion:reduce){.dp-hh__shell--slide-in{animation:none!important}}

/* Lobby full-page route (/hand-history) */
.dp-hh--lobby-page {
  position:relative;inset:auto;z-index:auto;pointer-events:auto;
  display:flex;justify-content:center;width:100%;min-height:0;
}
.dp-hh--lobby-page .dp-hh__shell--lobby-page {
  position:relative;right:auto;top:auto;
  width:min(920px,100%);height:auto;min-height:min(560px,calc(100dvh - 120px));max-height:none;
  margin:0 auto;
}
</style>
