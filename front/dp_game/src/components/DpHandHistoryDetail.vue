<template>
  <transition :name="isLobbyPage ? '' : 'dp-hd-root'">
    <div
      v-if="rootVisible"
      class="dp-hd"
      :class="{ 'dp-hd--lobby-page': isLobbyPage }"
      @click.self="onBackdropClick"
    >
      <div class="dp-hd__tv" :class="tvAnimClass">
        <div class="dp-hd__antenna dp-hd__antenna--l" />
        <div class="dp-hd__antenna dp-hd__antenna--r" />
        <div class="dp-hd__body">
          <div class="dp-hd__bezel">
            <div class="dp-hd__screen">
              <div v-if="showCrt" class="dp-hd__snow" :class="{ 'dp-hd__snow--active': phase === 'snow' || phase === 'glitch' }">
                <span class="dp-hd__snow-noise" />
                <span class="dp-hd__snow-bars" />
              </div>
              <div class="dp-hd__scanlines" />
              <div class="dp-hd__vignette" />
              <div v-if="showCrt" class="dp-hd__flash" :class="{ 'dp-hd__flash--pulse': phase === 'flash' || phase === 'glitch' }" />

              <div v-if="phase === 'ready' || phase === 'flash' || phase === 'glitch'" class="dp-hd__content">
                <div v-if="loading" class="dp-hd__status">
                  <span class="dp-hd__status-icon">&#9654;</span>
                  LOADING HAND DATA...
                </div>
                <div v-else-if="loadError" class="dp-hd__status dp-hd__status--err">[ERR] {{ loadError }}</div>

                <template v-else-if="detail">
                  <!-- 元信息 -->
                  <div class="dp-hd__meta">
                    <span class="dp-hd__meta-item">ROOM:{{ detail.roomId || '-' }}</span>
                    <span class="dp-hd__meta-sep">|</span>
                    <span class="dp-hd__meta-item">BLINDS:{{ detail.smallBlindChips }}/{{ detail.bigBlindChips }}</span>
                    <span class="dp-hd__meta-sep">|</span>
                    <span class="dp-hd__meta-item">DEALER:{{ detail.dealerNickname || '--' }}</span>
                    <span class="dp-hd__meta-sep">|</span>
                    <span class="dp-hd__meta-item">{{ formatTime(detail.endedAtMs) }}</span>
                    <button class="dp-hd__meta-close" @click="close" title="关闭">[X]</button>
                  </div>

                  <!-- Tabs -->
                  <div class="dp-hd__tabs">
                    <button v-for="tab in streetTabs" :key="tab.key" class="dp-hd__tab" :class="{ 'dp-hd__tab--active': activeTab === tab.key }" @click="activeTab = tab.key">{{ tab.short }}</button>
                  </div>

                  <!-- 公共牌 -->
                  <div class="dp-hd__board">
                    <span class="dp-hd__board-label">BOARD:</span>
                    <span v-if="!boardCards.length" class="dp-hd__board-empty">--</span>
                    <span v-for="c in boardCards" :key="'b-' + c" class="dp-hd__card" :class="cardClass(c)">{{ cardFace(c) }}</span>
                  </div>

                  <!-- 内容滚动区 -->
                  <div class="dp-hd__body-scroll" ref="scrollBody">
                    <!-- === 行动表 (非结算) === -->
                    <div v-if="activeTab !== 'settlement'" class="dp-hd__street-section">
                      <div v-if="!streetActions.length" class="dp-hd__empty">[ NO ACTIONS ]</div>
                      <template v-else>
                        <!-- 表头 -->
                        <div class="dp-hd__tbl-head">
                          <span class="dp-hd__tbl-hd dp-hd__tbl-hd--nick">PLAYER</span>
                          <span class="dp-hd__tbl-hd dp-hd__tbl-hd--holes">HOLE</span>
                          <span v-for="(h, hi) in roundColHeaders" :key="'hdr-' + hi" class="dp-hd__tbl-hd dp-hd__tbl-hd--round">{{ h }}</span>
                        </div>
                        <!-- 表体 -->
                        <div v-for="nick in activePlayers" :key="'apr-' + nick" class="dp-hd__tbl-row" :class="{ 'dp-hd__tbl-row--folded': foldedSet.has(nick), 'dp-hd__tbl-row--self': isSelfNick(nick) }">
                          <!-- 玩家名 + 角色标签 -->
                          <span class="dp-hd__tbl-nick">
                            <span class="dp-hd__tbl-nick-name">{{ nick }}</span>
                            <span v-for="t in (playerRoles[nick] || [])" :key="nick + '-role-' + t" class="dp-hd__role-tag" :class="roleClass(t)">{{ t }}</span>
                          </span>
                          <!-- 手牌 -->
                          <span class="dp-hd__tbl-holes">
                            <template v-if="holeCardsByStreet[nick] === null">
                              <span class="dp-hd__hole-hidden">[??]</span>
                            </template>
                            <template v-else-if="(holeCardsByStreet[nick] || []).length">
                              <span v-for="(c, hci) in holeCardsByStreet[nick]" :key="nick + '-hc-' + hci" class="dp-hd__mini-card" :class="miniCardClass(c)">{{ cardFace(c) }}</span>
                            </template>
                            <span v-else class="dp-hd__hole-empty">--</span>
                          </span>
                          <!-- 各轮行动 -->
                          <span v-for="(col, ci) in roundGrid" :key="nick + '-act-' + ci" class="dp-hd__tbl-act">{{ col[nick] || '--' }}</span>
                        </div>
                      </template>
                    </div>

                    <!-- === 结算表 === -->
                    <div v-else class="dp-hd__settle">
                      <div class="dp-hd__settle-head">=== SETTLEMENT ===</div>
                      <div class="dp-hd__tbl-head">
                        <span class="dp-hd__tbl-hd dp-hd__tbl-hd--nick">PLAYER</span>
                        <span class="dp-hd__tbl-hd dp-hd__tbl-hd--holes">HOLE</span>
                        <span class="dp-hd__tbl-hd dp-hd__tbl-hd--net">CHIPS</span>
                        <span class="dp-hd__tbl-hd dp-hd__tbl-hd--rank">HAND</span>
                      </div>
                      <div v-for="row in settleRowsWithCards" :key="'sr-' + row.nick" class="dp-hd__tbl-row" :class="{ 'dp-hd__tbl-row--win': row.net > 0, 'dp-hd__tbl-row--lose': row.net < 0, 'dp-hd__tbl-row--folded': row.folded, 'dp-hd__tbl-row--self': row.isSelf }">
                        <span class="dp-hd__tbl-nick">
                          <span class="dp-hd__tbl-nick-name">{{ row.nick }}</span>
                          <span v-for="t in (playerRoles[row.nick] || [])" :key="row.nick + '-srole-' + t" class="dp-hd__role-tag" :class="roleClass(t)">{{ t }}</span>
                        </span>
                        <span class="dp-hd__tbl-holes">
                          <template v-if="row.folded && !row.isSelf">
                            <span class="dp-hd__hole-hidden">[FOLD]</span>
                          </template>
                          <template v-else-if="row.cards.length">
                            <span v-for="(c, hci) in row.cards" :key="row.nick + '-shc-' + hci" class="dp-hd__mini-card" :class="miniCardClass(c)">{{ cardFace(c) }}</span>
                          </template>
                          <span v-else class="dp-hd__hole-empty">--</span>
                        </span>
                        <span class="dp-hd__tbl-net" :class="{ 'dp-hd__tbl-net--plus': row.net > 0, 'dp-hd__tbl-net--minus': row.net < 0 }">{{ row.net > 0 ? '+' + row.net : row.net }}</span>
                        <span class="dp-hd__tbl-rank">{{ row.rankText || '--' }}</span>
                      </div>
                      <!-- 底池 -->
                      <div v-if="pots.length" class="dp-hd__pots">
                        <div class="dp-hd__settle-head">--- POTS ---</div>
                        <div v-for="(p, i) in pots" :key="'pot-' + i" class="dp-hd__pot">
                          <span class="dp-hd__pot-label">{{ potLabel(i) }}:</span>
                          <span class="dp-hd__pot-amt">{{ p.amount }}</span>
                          <span class="dp-hd__pot-nicks">{{ (p.eligibleNicknames || []).join(', ') || 'ALL' }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </template>
              </div>
            </div>
          </div>
          <div class="dp-hd__controls">
            <span class="dp-hd__knob" />
            <span class="dp-hd__knob dp-hd__knob--sm" />
            <span class="dp-hd__led" :class="{ 'dp-hd__led--glow': phase === 'ready' }" />
            <span class="dp-hd__brand">POKER-SYS CRT-9000</span>
          </div>
        </div>
        <div class="dp-hd__feet"><span /><span /></div>
      </div>
    </div>
  </transition>
</template>

<script>
import { mapState } from 'vuex'
import { getCardClass, getCardDisplay } from '@/utils/dpGameCardVisual'
import { getHandRank } from '@/utils/dpGameHandRank'
import { ensureDpUserIdInStorage } from '@/utils/dpEnsureUserId'
import {
  STREET_ORDER, seatNicknamesOrdered, formatActionText,
  activePlayersBeforeStreet, boardForStreet, firstFoldStage,
  finalCommunityCards, playerRoleTagsByNickname, shouldShowHoleCardsOnStreetTab,
  splitRoundsByRaises, buildRoundGrid
} from '@/utils/dpHandHistoryReplay.js'

var STREET_SHORT = { preflop: 'PRE', flop: 'FLOP', turn: 'TURN', river: 'RIVR', settlement: 'SETTLE' }
var STREET_TABS = STREET_ORDER.concat(['settlement']).map(function (k) { return { key: k, short: STREET_SHORT[k] || k.toUpperCase() } })

export default {
  name: 'DpHandHistoryDetail',
  inject: { dpGameView: { default: null } },
  props: {
    handHistoryId: { type: [String, Number], default: null },
    /** game-overlay: in-game CRT panel; lobby-page: /hand-history/detail route */
    context: {
      type: String,
      default: 'game-overlay',
      validator: function (v) { return v === 'game-overlay' || v === 'lobby-page' }
    }
  },
  data: function () {
    return {
      visible: false,
      phase: 'idle',
      activeTab: 'preflop',
      detail: null,
      loading: false,
      loadError: '',
      user: null,
      timers: [],
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
    tvAnimClass: function () {
      if (!this.showCrt) return 'dp-hd__tv--instant'
      return {
        'dp-hd__tv--drop': this.phase === 'dropping',
        'dp-hd__tv--ready': this.phase === 'ready' || this.phase === 'flash' || this.phase === 'snow' || this.phase === 'glitch',
        'dp-hd__tv--glitch': this.phase === 'glitch',
        'dp-hd__tv--retract': this.phase === 'retracting'
      }
    },
    streetTabs: function () { return STREET_TABS },
    payload: function () { return (this.detail && this.detail.payload) || {} },
    actions: function () { return Array.isArray(this.payload.actions) ? this.payload.actions : [] },
    seatsAtStart: function () { return Array.isArray(this.payload.seatsAtStart) ? this.payload.seatsAtStart : [] },
    boardsByStreet: function () { return Array.isArray(this.payload.boardsByStreet) ? this.payload.boardsByStreet : [] },
    rowNicknames: function () { return seatNicknamesOrdered(this.seatsAtStart) },
    streetActions: function () {
      if (this.activeTab === 'settlement') return []
      return this.actions.filter(function (a) { return a && a.stage === this.activeTab }.bind(this))
    },
    boardCards: function () {
      if (this.activeTab === 'settlement') return finalCommunityCards(this.boardsByStreet)
      return boardForStreet(this.boardsByStreet, this.activeTab)
    },

    // ---- 手牌 ----
    holeCardsAtEnd: function () {
      var holes = this.payload.holeCardsAtEnd
      return (holes && typeof holes === 'object') ? holes : {}
    },
    holeCardsByStreet: function () {
      var self = this
      var tab = this.activeTab
      var out = {}
      var viewer = this.user && this.user.nickname
      if (tab === 'settlement') return out
      for (var i = 0; i < this.rowNicknames.length; i++) {
        var nick = this.rowNicknames[i]
        var isSelf = viewer && nick === viewer
        if (isSelf) {
          out[nick] = Array.isArray(this.holeCardsAtEnd[nick]) ? this.holeCardsAtEnd[nick] : []
          continue
        }
        if (this.foldedSet.has(nick)) {
          var ff = firstFoldStage(this.actions, nick)
          if (!shouldShowHoleCardsOnStreetTab(ff, tab)) { out[nick] = null; continue }
        }
        out[nick] = Array.isArray(this.holeCardsAtEnd[nick]) ? this.holeCardsAtEnd[nick] : []
      }
      return out
    },

    // ---- 角色标签 ----
    playerRoles: function () {
      return playerRoleTagsByNickname(this.seatsAtStart, this.detail && this.detail.dealerNickname)
    },

    // ---- 参与玩家 ----
    activePlayers: function () {
      if (this.activeTab === 'settlement') return []
      var s = activePlayersBeforeStreet(this.actions, this.activeTab, this.rowNicknames)
      return this.rowNicknames.filter(function (n) { return s.has(n) })
    },

    // ---- 行动轮次网格 ----
    roundGrid: function () {
      var playersArr = this.activePlayers
      if (!playersArr.length || !this.streetActions.length) return []
      var split = splitRoundsByRaises(this.streetActions)
      var cols = []
      if (split.prefix.length) {
        var prefixCols = buildRoundGrid([split.prefix], playersArr)
        for (var i = 0; i < prefixCols.length; i++) cols.push(prefixCols[i])
      }
      if (split.rounds.length) {
        var roundCols = buildRoundGrid(split.rounds, playersArr)
        for (var j = 0; j < roundCols.length; j++) cols.push(roundCols[j])
      }
      return cols
    },
    roundColHeaders: function () {
      var split = splitRoundsByRaises(this.streetActions)
      var h = []
      if (split.prefix.length) h.push('BLIND')
      for (var i = 0; i < split.rounds.length; i++) h.push('R' + (i + 1))
      return h.length ? h : ['ACT']
    },

    // ---- 盖牌集 ----
    foldedSet: function () {
      var s = new Set()
      for (var i = 0; i < this.actions.length; i++) {
        var a = this.actions[i]
        if (a && a.type === 'FOLD' && a.actorNickname) s.add(a.actorNickname)
      }
      return s
    },

    // ---- 结算 ----
    settleRowsWithCards: function () {
      var net = this.payload.netChipsChange
      if (!net || typeof net !== 'object') return []
      var self = this
      var community = finalCommunityCards(this.boardsByStreet)
      var viewer = this.user && this.user.nickname
      var holes = this.holeCardsAtEnd
      return this.rowNicknames.filter(function (n) { return Object.prototype.hasOwnProperty.call(net, n) }).map(function (nick) {
        var nv = net[nick]
        var folded = self.foldedSet.has(nick)
        var isSelf = viewer && nick === viewer
        var cards = []
        if (isSelf) {
          cards = Array.isArray(holes[nick]) ? holes[nick] : []
        } else if (!folded) {
          cards = Array.isArray(holes[nick]) ? holes[nick] : []
        }
        var rankText = ''
        if (cards.length >= 2 && community.length >= 3) {
          rankText = getHandRank(cards, community) || ''
        }
        return { nick: nick, net: nv, folded: folded, isSelf: isSelf, cards: cards, rankText: rankText }
      })
    },
    pots: function () { return Array.isArray(this.payload.potsBeforeSettlement) ? this.payload.potsBeforeSettlement : [] }
  },
  watch: {
    handHistoryId: function (v) {
      if (this.isLobbyPage) {
        if (v != null && this.lobbyReady) { this.activeTab = 'preflop'; this.fetchDetail(); this.startCrtSequence() }
        return
      }
      if (v != null) { this.visible = true; this.activeTab = 'preflop'; this.fetchDetail(); this.startCrtSequence() }
      else if (this.visible) { this.close() }
    }
  },
  created: function () {
    if (this.isLobbyPage) this.syncMotionPrefs()
  },
  mounted: function () {
    var self = this
    if (this.isLobbyPage) {
      this._onResize = function () { self.viewportWidth = window.innerWidth }
      window.addEventListener('resize', this._onResize)
      this.bootstrapLobbyPage()
      return
    }
    try { var raw = localStorage.getItem('userInfo'); self.user = raw ? JSON.parse(raw) : null } catch (e) { self.user = null }
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
      if (this.handHistoryId != null) {
        this.activeTab = 'preflop'
        this.fetchDetail()
        this.startCrtSequence()
      }
    },
    onBackdropClick: function () {
      if (this.isLobbyPage) return
      this.close()
    },
    clearTimers: function () {
      for (var i = 0; i < this.timers.length; i++) clearTimeout(this.timers[i])
      this.timers = []
    },
    startCrtSequence: function () {
      var self = this
      this.clearTimers()
      if (!this.showCrt) { this.phase = 'ready'; return }
      this.phase = 'dropping'
      this.timers.push(setTimeout(function () { self.phase = 'snow' }, 400))
      this.timers.push(setTimeout(function () { self.phase = 'flash' }, 750))
      this.timers.push(setTimeout(function () { self.phase = 'ready' }, 900))
    },
    close: function () {
      var self = this
      if (this.isLobbyPage) {
        this.$router.push('/hand-history')
        return
      }
      if (this.showCrt && this.phase !== 'retracting' && this.phase !== 'idle') {
        this.phase = 'retracting'
        this.timers.push(setTimeout(function () { self.visible = false; self.phase = 'idle'; self.$emit('closed') }, 320))
      } else {
        this.visible = false; this.phase = 'idle'
        this.$emit('closed')
      }
    },
    cardClass: function (c) { return getCardClass(c) },
    cardFace: function (c) { return getCardDisplay(c) },
    miniCardClass: function (c) { return getCardClass(c).replace('card-base', '').trim() },
    isSelfNick: function (n) { return this.user && this.user.nickname === n },
    roleClass: function (t) {
      if (t === '发牌' || t === '发牌猫') return 'dp-hd__role--dealer'
      if (t === '底1' || t === 'SC') return 'dp-hd__role--sb'
      if (t === '底2' || t === 'BC') return 'dp-hd__role--bb'
      return ''
    },
    formatTime: function (ms) {
      if (!ms) return '--/-- --:--'
      var d = new Date(ms)
      if (isNaN(d.getTime())) return '--/-- --:--'
      var M = String(d.getMonth() + 1).padStart(2, '0')
      var D = String(d.getDate()).padStart(2, '0')
      var h = String(d.getHours()).padStart(2, '0')
      var m = String(d.getMinutes()).padStart(2, '0')
      return M + '/' + D + ' ' + h + ':' + m
    },
    potLabel: function (i) { return i === 0 ? 'MAIN' : 'SIDE' + i },
    fetchDetail: function () {
      var user = this.isLobbyPage ? this.user : (this.vm && this.vm.user)
      var http = this.isLobbyPage ? this.$http : (this.vm && this.vm.$http)
      if (!user || !http) { this.loadError = 'NO USER'; return }
      var id = Number(this.handHistoryId)
      if (isNaN(id) || id <= 0) { this.loadError = 'BAD ID'; return }
      this.loading = true; this.loadError = ''
      var self = this
      http.get('/dpHandHistory/detail', { params: { handHistoryId: id, userId: Number(user.userId) } }).then(function (res) {
        self.detail = res.data || null
        if (!self.detail) self.loadError = 'NO DATA'
      }).catch(function (e) {
        self.detail = null
        self.loadError = (e && e.message) || 'LOAD FAILED'
      }).finally(function () { self.loading = false })
    },
    onKey: function (e) {
      if (e.ctrlKey && e.key === 'd') { e.preventDefault(); this.close(); return true }
      if (e.key === 'Escape') { e.preventDefault(); this.close(); return true }
      if (e.key === 'a' || e.key === 'A' || e.key === 'ArrowLeft') {
        e.preventDefault()
        var idx = this.streetTabs.findIndex(function (t) { return t.key === this.activeTab }.bind(this))
        if (idx > 0) this.activeTab = this.streetTabs[idx - 1].key
        return true
      }
      if (e.key === 'd' || e.key === 'D' || e.key === 'ArrowRight') {
        e.preventDefault()
        var idx2 = this.streetTabs.findIndex(function (t) { return t.key === this.activeTab }.bind(this))
        if (idx2 < this.streetTabs.length - 1) this.activeTab = this.streetTabs[idx2 + 1].key
        return true
      }
      return false
    }
  }
}
</script>

<style scoped>
.dp-hd { position:fixed;inset:0;z-index:10090;display:flex;align-items:flex-start;justify-content:center;pointer-events:none; }
.dp-hd-root-enter-active { transition:opacity 0.12s }
.dp-hd-root-leave-active { transition:opacity 0.18s }
.dp-hd-root-enter,.dp-hd-root-leave-to { opacity:0 }

/* ====== CRT TV 外壳 ====== */
.dp-hd__tv {
  margin-top:max(20px,env(safe-area-inset-top,20px));
  width:min(780px,97vw);
  pointer-events:auto;
  display:flex;flex-direction:column;align-items:center;
}
.dp-hd__tv--drop { animation:dp-hd-drop 0.38s cubic-bezier(0.34,1.56,0.64,1) forwards }
.dp-hd__tv--instant { opacity:1;transform:none }
.dp-hd__tv--retract { animation:dp-hd-retract 0.28s ease-in forwards }
.dp-hd__tv--glitch { animation:dp-hd-glitch-shake 0.15s ease }

@keyframes dp-hd-drop { from{transform:translateY(-110%);opacity:0} to{transform:translateY(0);opacity:1} }
@keyframes dp-hd-retract { to{transform:translateY(-110%);opacity:0} }
@keyframes dp-hd-glitch-shake { 0%,100%{transform:translateX(0)} 20%{transform:translateX(-4px)} 40%{transform:translateX(4px)} 60%{transform:translateX(-2px)} 80%{transform:translateX(2px)} }

.dp-hd__antenna { position:absolute;top:-28px;width:4px;height:34px;background:linear-gradient(to bottom,#9aa8b8,#3a4450);border-radius:2px 2px 0 0;z-index:0; }
.dp-hd__antenna::after { content:'';position:absolute;top:-6px;left:-3px;width:10px;height:10px;border-radius:50%;background:radial-gradient(circle at 35% 35%,#c0c8d4,#3a4450); }
.dp-hd__antenna--l { left:calc(50% - 72px);transform:rotate(-12deg) }
.dp-hd__antenna--r { right:calc(50% - 72px);transform:rotate(12deg) }

.dp-hd__body {
  position:relative;width:100%;
  background:linear-gradient(175deg,#2a3038 0%,#1a1e24 20%,#252a32 50%,#1c2026 80%,#2a3038 100%);
  border-radius:18px 18px 12px 12px;
  box-shadow:0 0 0 3px #0d0f12,0 0 0 6px #1a1d22,0 12px 48px rgba(0,0,0,0.7);
  padding:16px 16px 6px;
}
.dp-hd__bezel {
  position:relative;
  background:linear-gradient(175deg,#111418,#0a0c0f 30%,#0e1014 70%,#111418);
  border-radius:12px;
  padding:clamp(10px,2vw,16px);
  box-shadow:inset 0 2px 8px rgba(0,0,0,0.8),inset 0 -2px 4px rgba(255,255,255,0.03),0 0 0 2px #0a0c0e;
}
.dp-hd__screen {
  position:relative;overflow:hidden;
  background:rgba(4,6,8,0.98);
  border-radius:4px;
  min-height:min(380px,calc(100vh - 260px));
  max-height:min(550px,calc(100vh - 240px));
  display:flex;flex-direction:column;
  box-shadow:inset 0 0 60px rgba(0,0,0,0.5),0 0 12px rgba(74,246,38,0.06);
}

/* CRT 图层 */
.dp-hd__scanlines { position:absolute;inset:0;z-index:4;pointer-events:none;background:repeating-linear-gradient(to bottom,transparent 0 2px,rgba(0,0,0,0.1) 2px 3px);background-size:100% 3px;opacity:0.12; }
.dp-hd__vignette { position:absolute;inset:0;z-index:3;pointer-events:none;background:radial-gradient(ellipse at center,transparent 50%,rgba(0,0,0,0.5) 100%); }
.dp-hd__snow { position:absolute;inset:0;z-index:6;opacity:0;pointer-events:none;overflow:hidden;transition:opacity 0.08s;background:#0a0c0e; }
.dp-hd__snow--active { opacity:0.92 }
.dp-hd__snow-noise {
  position:absolute;inset:0;opacity:0.7;
  background-image:repeating-radial-gradient(circle at 15% 25%,rgba(255,255,255,0.55) 0 0.3px,transparent 0.4px),repeating-radial-gradient(circle at 70% 55%,rgba(210,218,228,0.45) 0 0.25px,transparent 0.35px),repeating-radial-gradient(circle at 40% 80%,rgba(255,255,255,0.5) 0 0.35px,transparent 0.45px);
  background-size:3px 3px,4px 4px,2.5px 2.5px;
  animation:dp-hd-snow-drift 0.12s steps(3) infinite;
}
.dp-hd__snow-bars {
  position:absolute;inset:0;opacity:0.3;
  background:repeating-linear-gradient(to bottom,transparent 0 8px,rgba(200,220,240,0.15) 8px 10px,transparent 10px 18px,rgba(180,200,230,0.1) 18px 20px);
  background-size:100% 20px;
  animation:dp-hd-bar-scroll 0.5s linear infinite;
}
@keyframes dp-hd-snow-drift { 0%{transform:translate(0,0)} 33%{transform:translate(1px,0.5px)} 66%{transform:translate(-0.5px,1px)} 100%{transform:translate(0,-0.5px)} }
@keyframes dp-hd-bar-scroll { to{background-position:0 20px} }
.dp-hd__flash { position:absolute;inset:0;z-index:5;pointer-events:none;opacity:0; }
.dp-hd__flash--pulse { animation:dp-hd-flash 0.12s ease-out }
@keyframes dp-hd-flash { 0%{opacity:1;background:rgba(248,250,252,0.85)} 100%{opacity:0;background:transparent} }

/* ====== 内容区 ====== */
.dp-hd__content {
  position:relative;z-index:2;flex:1;display:flex;flex-direction:column;min-height:0;
  padding:8px 12px;
  font-family:'Courier New',ui-monospace,'PingFang SC',monospace;
  color:#4af626;
}
.dp-hd__status {
  flex:1;display:flex;align-items:center;justify-content:center;gap:8px;
  font-family:'Press Start 2P',monospace;font-size:10px;color:rgba(74,246,38,0.7);
  text-shadow:0 0 4px rgba(74,246,38,0.3);
}
.dp-hd__status-icon { animation:dp-hd-blink 0.8s step-end infinite }
@keyframes dp-hd-blink { 0%,100%{opacity:1} 50%{opacity:0.3} }
.dp-hd__status--err { color:#ff6666;text-shadow:0 0 4px rgba(255,102,102,0.4) }

/* 元信息 */
.dp-hd__meta {
  display:flex;flex-wrap:wrap;align-items:center;gap:0 6px;width:100%;
  padding:3px 0 6px;
  border-bottom:1px solid rgba(74,246,38,0.16);
  font-size:9px;color:rgba(74,246,38,0.55);
  letter-spacing:0.03em;flex-shrink:0;
}
.dp-hd__meta-item { white-space:nowrap }
.dp-hd__meta-sep { color:rgba(74,246,38,0.15) }
.dp-hd__meta-close {
  margin-left:auto;flex-shrink:0;width:26px;height:20px;padding:0;
  border:1px solid rgba(74,246,38,0.3);border-radius:2px;
  background:rgba(8,12,8,0.8);color:#4af626;
  font-family:'Courier New',monospace;font-size:11px;cursor:pointer;
  line-height:1;transition:all 0.08s;
}
.dp-hd__meta-close:hover { background:#4af626;color:#080a0c;border-color:#4af626 }

/* Tabs */
.dp-hd__tabs { display:flex;gap:3px;padding:6px 0;flex-shrink:0; }
.dp-hd__tab {
  flex:1;padding:5px 2px;border:1px solid rgba(74,246,38,0.16);border-radius:2px;
  background:rgba(8,16,8,0.6);color:rgba(74,246,38,0.4);
  font-family:'Courier New',ui-monospace,monospace;font-size:9px;cursor:pointer;
  letter-spacing:0.05em;transition:all 0.08s;
}
.dp-hd__tab:hover { border-color:rgba(74,246,38,0.35);color:rgba(74,246,38,0.7) }
.dp-hd__tab--active {
  border-color:#4af626;color:#4af626;background:rgba(16,32,16,0.7);
  text-shadow:0 0 6px rgba(74,246,38,0.5);box-shadow:0 0 6px rgba(74,246,38,0.1);
}

/* 公共牌 */
.dp-hd__board {
  display:flex;align-items:center;gap:6px;padding:5px 0 6px;
  border-bottom:1px solid rgba(74,246,38,0.08);flex-shrink:0;font-size:10px;
}
.dp-hd__board-label { color:rgba(74,246,38,0.45);font-size:9px;flex-shrink:0 }
.dp-hd__board-empty { color:rgba(74,246,38,0.18);font-size:9px }
.dp-hd__card {
  display:inline-flex;align-items:center;justify-content:center;
  width:34px;height:26px;font-size:13px;font-weight:800;
  font-family:Garamond,Georgia,'Noto Serif SC',serif;
  color:#f5f5f5;text-shadow:0 1px 0 rgba(0,0,0,0.4);
  border-radius:4px;
  border:1px solid rgba(0,0,0,0.2);
  box-shadow:0 0 0 1px rgba(180,150,90,0.4),0 2px 6px rgba(0,0,0,0.4),inset 0 1px 0 rgba(255,255,255,0.15);
}
.dp-hd__card.bg-red {
  background:radial-gradient(ellipse 120% 80% at 30% 20%,rgba(255,255,255,0.35) 0%,transparent 55%),linear-gradient(145deg,#ff6b8a 0%,#e84855 35%,#a8071a 100%);
}
.dp-hd__card.bg-blue {
  background:radial-gradient(ellipse 120% 80% at 28% 18%,rgba(255,255,255,0.38) 0%,transparent 50%),linear-gradient(145deg,#69c0ff 0%,#1890ff 40%,#0050b3 100%);
}
.dp-hd__card.bg-green {
  background:radial-gradient(ellipse 120% 80% at 30% 22%,rgba(255,255,255,0.32) 0%,transparent 52%),linear-gradient(145deg,#95de64 0%,#389e0d 42%,#135200 100%);
}
.dp-hd__card.bg-black {
  background:radial-gradient(ellipse 100% 70% at 35% 15%,rgba(255,255,255,0.14) 0%,transparent 45%),linear-gradient(145deg,#4a5568 0%,#2f3542 38%,#141820 100%);
}
.dp-hd__card.bg-gray {
  background:linear-gradient(145deg,#2f3542 0%,#1a1e26 100%);color:rgba(255,255,255,0.4);
}

/* ====== 滚动区 ====== */
.dp-hd__body-scroll { flex:1;min-height:0;overflow-y:auto }
.dp-hd__empty { color:rgba(74,246,38,0.18);font-size:10px;padding:12px 0 }

/* ====== 表格 ====== */
.dp-hd__street-section { padding:2px 0 }

.dp-hd__tbl-head {
  display:flex;align-items:center;gap:0;
  padding:3px 0;border-bottom:2px solid rgba(74,246,38,0.2);
  font-family:'Press Start 2P',monospace;font-size:7px;
  color:rgba(74,246,38,0.5);text-shadow:0 0 3px rgba(74,246,38,0.15);
  position:sticky;top:0;background:rgba(4,6,8,0.92);z-index:1;
}
.dp-hd__tbl-hd--nick { width:76px;flex-shrink:0;text-align:left;padding-left:2px }
.dp-hd__tbl-hd--holes { width:48px;flex-shrink:0;text-align:center }
.dp-hd__tbl-hd--round { flex:1;text-align:center;min-width:50px }
.dp-hd__tbl-hd--net { width:56px;flex-shrink:0;text-align:right }
.dp-hd__tbl-hd--rank { flex:1;text-align:left;padding-left:4px }

.dp-hd__tbl-row {
  display:flex;align-items:center;gap:0;
  padding:4px 0;border-bottom:1px solid rgba(74,246,38,0.04);
  font-size:10px;transition:background 0.06s;
}
.dp-hd__tbl-row:hover { background:rgba(74,246,38,0.02) }
.dp-hd__tbl-row--folded { opacity:0.4 }
.dp-hd__tbl-row--self { background:rgba(74,246,38,0.03) }
.dp-hd__tbl-row--win { border-left:3px solid rgba(114,240,82,0.4) }
.dp-hd__tbl-row--lose { border-left:3px solid rgba(255,102,102,0.4) }

.dp-hd__tbl-nick {
  width:76px;flex-shrink:0;display:flex;flex-wrap:wrap;align-items:baseline;gap:1px 3px;
  padding-left:2px;overflow:hidden;
}
.dp-hd__tbl-nick-name {
  color:rgba(74,246,38,0.65);font-weight:bold;font-size:9px;
  overflow:hidden;text-overflow:ellipsis;white-space:nowrap;max-width:100%;
}
.dp-hd__role-tag {
  display:inline-block;padding:0 3px;font-size:7px;font-weight:700;border-radius:2px;
  line-height:13px;vertical-align:middle;
}
.dp-hd__role--dealer { color:#f0a040;background:rgba(240,160,64,0.12);border:1px solid rgba(240,160,64,0.28) }
.dp-hd__role--sb { color:#66aaff;background:rgba(102,170,255,0.1);border:1px solid rgba(102,170,255,0.22) }
.dp-hd__role--bb { color:#66aaff;background:rgba(102,170,255,0.1);border:1px solid rgba(102,170,255,0.22) }

/* 手牌列 */
.dp-hd__tbl-holes {
  width:48px;flex-shrink:0;display:flex;align-items:center;justify-content:center;gap:2px;
}
.dp-hd__hole-hidden { color:rgba(74,246,38,0.18);font-size:7px }
.dp-hd__hole-empty { color:rgba(74,246,38,0.1);font-size:8px }
.dp-hd__mini-card {
  display:inline-flex;align-items:center;justify-content:center;
  width:22px;height:17px;padding:0 2px;
  font-family:Garamond,Georgia,'Noto Serif SC',serif;font-weight:800;
  font-size:10px;color:#f5f5f5;
  text-shadow:0 1px 0 rgba(0,0,0,0.45);
  border-radius:3px;
  border:1px solid rgba(0,0,0,0.2);
  box-shadow:0 0 0 1px rgba(180,150,90,0.4),0 1px 3px rgba(0,0,0,0.4),inset 0 1px 0 rgba(255,255,255,0.15);
}
.dp-hd__mini-card.bg-red {
  background:radial-gradient(ellipse 120% 80% at 30% 20%,rgba(255,255,255,0.3) 0%,transparent 55%),linear-gradient(145deg,#ff6b8a 0%,#e84855 35%,#a8071a 100%);
}
.dp-hd__mini-card.bg-blue {
  background:radial-gradient(ellipse 120% 80% at 28% 18%,rgba(255,255,255,0.3) 0%,transparent 50%),linear-gradient(145deg,#69c0ff 0%,#1890ff 40%,#0050b3 100%);
}
.dp-hd__mini-card.bg-green {
  background:radial-gradient(ellipse 120% 80% at 30% 22%,rgba(255,255,255,0.28) 0%,transparent 52%),linear-gradient(145deg,#95de64 0%,#389e0d 42%,#135200 100%);
}
.dp-hd__mini-card.bg-black {
  background:radial-gradient(ellipse 100% 70% at 35% 15%,rgba(255,255,255,0.12) 0%,transparent 45%),linear-gradient(145deg,#4a5568 0%,#2f3542 38%,#141820 100%);
}
.dp-hd__mini-card.bg-gray {
  background:linear-gradient(145deg,#2f3542 0%,#1a1e26 100%);color:rgba(255,255,255,0.4);
}

/* 行动单元格 */
.dp-hd__tbl-act {
  flex:1;min-width:50px;text-align:center;color:#c0e0b0;font-size:9px;
  line-height:1.5;white-space:pre-line;padding:0 2px;
}

/* 结算 */
.dp-hd__settle { padding:2px 0 }
.dp-hd__settle-head {
  color:rgba(74,246,38,0.4);font-size:9px;padding:6px 0 4px;
  text-shadow:0 0 3px rgba(74,246,38,0.12);
}
.dp-hd__tbl-net { width:56px;flex-shrink:0;text-align:right;font-weight:bold;font-size:10px;color:#d0f0c0 }
.dp-hd__tbl-net--plus { color:#72f052;text-shadow:0 0 4px rgba(114,240,82,0.35) }
.dp-hd__tbl-net--minus { color:#ff6666 }
.dp-hd__tbl-rank { flex:1;padding-left:4px;color:rgba(74,246,38,0.32);font-size:9px }

.dp-hd__pots { margin-top:4px }
.dp-hd__pot { display:flex;gap:6px;padding:2px 0;font-size:9px;color:rgba(74,246,38,0.4) }
.dp-hd__pot-label { color:rgba(74,246,38,0.35);width:36px;flex-shrink:0 }
.dp-hd__pot-amt { color:#d0f0c0;font-weight:bold;width:45px;flex-shrink:0 }
.dp-hd__pot-nicks { color:rgba(74,246,38,0.28);overflow:hidden;text-overflow:ellipsis;white-space:nowrap }

/* 底部控制 */
.dp-hd__controls { display:flex;align-items:center;gap:10px;padding:3px 10px 2px;position:relative;z-index:1 }
.dp-hd__knob { width:16px;height:16px;border-radius:50%;background:linear-gradient(135deg,#5a6270,#2a3038);box-shadow:inset 0 1px 2px rgba(255,255,255,0.15),0 2px 4px rgba(0,0,0,0.5) }
.dp-hd__knob--sm { width:10px;height:10px }
.dp-hd__led { width:7px;height:7px;border-radius:50%;background:#3a2010;box-shadow:inset 0 0 2px rgba(0,0,0,0.5);transition:background 0.2s,box-shadow 0.2s }
.dp-hd__led--glow { background:#4af626;box-shadow:0 0 6px rgba(74,246,38,0.7),inset 0 0 2px rgba(255,255,255,0.3) }
.dp-hd__brand { margin-left:auto;font-family:'Press Start 2P',monospace;font-size:6px;color:rgba(255,255,255,0.1);letter-spacing:0.06em }

.dp-hd__feet { display:flex;gap:36px;margin-top:-2px }
.dp-hd__feet span { width:46px;height:7px;border-radius:0 0 3px 3px;background:linear-gradient(to bottom,#1a1e24,#0d0f12);box-shadow:0 2px 4px rgba(0,0,0,0.5) }

@media(prefers-reduced-motion:reduce) {
  .dp-hd__tv--drop,.dp-hd__tv--retract,.dp-hd__tv--glitch{animation:none!important}
  .dp-hd__snow-noise,.dp-hd__snow-bars{animation:none!important}
}

/* Lobby full-page route (/hand-history/detail/:id) */
.dp-hd--lobby-page {
  position:relative;inset:auto;z-index:auto;display:block;pointer-events:auto;
  width:100%;min-height:0;
}
.dp-hd--lobby-page .dp-hd__tv {
  margin-top:0;width:min(920px,100%);margin-left:auto;margin-right:auto;
}
</style>
