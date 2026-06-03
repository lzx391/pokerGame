<template>
  <div
    class="dp-game-root"
    :data-dp-game-theme="effectiveThemeForCss"
  >
    <div class="lb-page lb-page--dp">
      <header class="lb-page__header">
        <div class="lb-page__header-main">
          <h1 class="lb-page__title">排行榜</h1>
          <p class="lb-page__hints">
            <span>{{ staleHintText }}</span>
            <span class="lb-page__hints-sep">·</span>
            <span>每周一 0 点刷新（上海时区）</span>
          </p>
          <p v-if="weekMonday" class="lb-page__week">本周：{{ weekMonday }}</p>
        </div>
        <div class="lb-page__header-actions">
          <div class="dp-game-theme-row lb-page__theme-row">
            <span class="dp-game-theme-row__label">界面主题</span>
            <dp-theme-picker
              :game-ui-theme="gameUiTheme"
              :theme-options="gameThemeOptions"
              @input-theme="onLobbyThemeChange($event)"
            />
            <dp-fluidity-toggle />
          </div>
          <button type="button" class="lb-page__back" @click="goBack">返回大厅</button>
        </div>
      </header>

      <div class="lb-page__tabs" role="tablist" aria-label="周榜类型">
        <button
          type="button"
          role="tab"
          class="lb-page__tab"
          :class="{ 'lb-page__tab--active': activeTab === 'hand' }"
          :aria-selected="activeTab === 'hand'"
          @click="switchTab('hand')"
        >
          单局之最
        </button>
        <button
          type="button"
          role="tab"
          class="lb-page__tab"
          :class="{ 'lb-page__tab--active': activeTab === 'room' }"
          :aria-selected="activeTab === 'room'"
          @click="switchTab('room')"
        >
          单房之最
        </button>
      </div>

      <div class="lb-page__body">
        <p
          v-if="loading && !retroLbFx"
          class="lb-page__status"
        >加载中…</p>
        <div
          v-else-if="loading && retroLbFx"
          class="lb-scan-skeleton"
          aria-busy="true"
          aria-label="Scanning leaderboard"
        >
          <div class="lb-scan-skeleton__beam" aria-hidden="true" />
          <table class="lb-table">
            <thead>
              <tr>
                <th class="lb-table__col-rank">RANK</th>
                <th class="lb-table__col-nick">PLAYER</th>
                <th class="lb-table__col-mult">MULT</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(sk, skIdx) in scanSkeletonRows"
                :key="'sk-' + skIdx"
              >
                <td class="lb-table__rank">
                  <span class="lb-table__rank-num lb-table__pixel-en">{{ sk.rank }}</span>
                </td>
                <td class="lb-table__nick">
                  <span class="lb-table__pixel-en">{{ sk.nick }}</span>
                </td>
                <td class="lb-table__mult">
                  <span class="lb-table__pixel-en">{{ sk.mult }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-else-if="loadError" class="lb-page__error">{{ loadError }}</p>
        <template v-else>
          <p v-if="!items.length" class="lb-page__empty">
            {{ emptyMessage }}
          </p>
          <div
            v-else
            class="lb-table-wrap"
            :class="tableWrapClasses"
          >
            <div
              v-if="retroLbFx"
              class="lb-table-wrap__glitch-noise"
              aria-hidden="true"
            />
            <div
              v-if="retroLbFx"
              class="lb-table-wrap__glitch-bars"
              aria-hidden="true"
            />
            <div
              v-if="retroLbFx"
              class="lb-table-wrap__scan-beam"
              aria-hidden="true"
            />
            <table class="lb-table">
              <thead>
                <tr>
                  <th class="lb-table__col-rank">{{ retroLbFx ? 'RANK' : '名次' }}</th>
                  <th class="lb-table__col-nick">{{ retroLbFx ? 'PLAYER' : '昵称' }}</th>
                  <th class="lb-table__col-mult">{{ retroLbFx ? 'MULT' : '倍数' }}</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(row, idx) in items"
                  :key="row.userId + '-' + idx + '-' + scanRevealKey"
                  :class="podiumRowClass(row.rank)"
                  :style="scanRowStyle(idx)"
                >
                  <td class="lb-table__rank">
                    <span class="lb-table__rank-cell">
                      <svg
                        v-if="isPodiumRank(row.rank)"
                        class="lb-table__crown"
                        :class="'lb-table__crown--' + podiumTier(row.rank)"
                        width="18"
                        height="14"
                        viewBox="0 0 18 14"
                        fill="currentColor"
                        aria-hidden="true"
                        focusable="false"
                      >
                        <path d="M2 12h14v2H2v-2zm1.5-2L5.5 2 8 6.2 10 1l2 5.2L13.5 2 16.5 10H3.5z" />
                      </svg>
                      <span
                        class="lb-table__rank-num"
                        :class="{ 'lb-table__pixel-en': retroLbFx }"
                      >{{ formatRankLabel(row.rank) }}</span>
                    </span>
                  </td>
                  <td class="lb-table__nick">
                    <div v-if="isPodiumRank(row.rank)" class="lb-table__nick-podium">
                      <dp-user-avatar
                        :avatar-url="row.avatarUrl"
                        :nickname="row.nickname"
                        :cache-bust="avatarCacheBustFromUpdatedAt(row.avatarUpdatedAt)"
                        size="sm"
                        img-loading="lazy"
                      />
                      <span
                        class="lb-table__nick-text"
                        :class="{ 'lb-table__pixel-en': retroLbFx }"
                      >{{ displayNickname(row.nickname) }}</span>
                    </div>
                    <span
                      v-else
                      :class="{ 'lb-table__pixel-en': retroLbFx }"
                    >{{ displayNickname(row.nickname) }}</span>
                  </td>
                  <td class="lb-table__mult">
                    <span :class="{ 'lb-table__pixel-en': retroLbFx }">{{ formatMultiplier(row.multiplier) }}</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </div>

      <footer class="lb-page__footer">
        <template v-if="isLoggedIn">
          <template v-if="myRank != null || myMultiplier != null">
            <span class="lb-page__footer-label">我的</span>
            <span v-if="myRank != null">第 <strong>{{ myRank }}</strong> 名</span>
            <span v-if="myRank != null && myMultiplier != null" class="lb-page__footer-sep">·</span>
            <span v-if="myMultiplier != null">{{ formatMultiplier(myMultiplier) }}</span>
          </template>
          <span v-else class="lb-page__footer-muted">本周暂无上榜记录</span>
        </template>
        <span v-else class="lb-page__footer-muted">登录后查看我的名次</span>
      </footer>
    </div>
  </div>
</template>

<script>
import '@/styles/dp-game-themes.css'
import '@/styles/dp-lobby-shell.css'
import '@/styles/dp-leaderboard-scan.css'
import { mapState } from 'vuex'
import dpLobbyThemeMixin from '@/mixins/dpLobbyThemeMixin'
import DpFluidityToggle from '@/components/DpFluidityToggle.vue'
import DpUserAvatar from '@/components/DpUserAvatar.vue'
import { getWeeklyHandLeaderboard, getWeeklyRoomLeaderboard } from '@/api/api.dpLeaderboard'
import { dpResultSuccess, dpResultData, dpResultMessage, dpAxiosErrorMessage } from '@/utils/dpApiResult'
import { avatarCacheBustFromUpdatedAt } from '@/utils/dpAvatarUrl'
import { dpDisplayNickname } from '@/utils/dpDisplayNickname'

var TAB_CACHE_MS = 30000
var SCAN_ROW_STAGGER_MS = 48
var SCAN_BASE_MS = 440
var GLITCH_BURST_MS = 100
var SKELETON_ROW_COUNT = 8
var SCAN_SKELETON_RANKS = ['#--', '#--', '#--', '#--', '#--', '#--', '#--', '#--']
var SCAN_SKELETON_NICKS = ['SCAN...', 'LOAD...', 'WAIT...', 'SYNC...', 'SCAN...', 'LOAD...', 'WAIT...', 'SYNC...']

export default {
  name: 'LeaderboardPage',
  components: { DpFluidityToggle, DpUserAvatar },
  mixins: [dpLobbyThemeMixin],
  data() {
    return {
      activeTab: 'hand',
      items: [],
      weekMonday: '',
      staleHintText: '榜单约每分钟更新',
      emptyMessage: '暂无上榜数据，请稍后再看。',
      myRank: null,
      myMultiplier: null,
      loading: false,
      loadError: '',
      isLoggedIn: false,
      prefersReducedMotion: false,
      scanRevealKey: 0,
      scanActive: false,
      panelGlitchBurst: false,
      _scanDoneTimer: null,
      _glitchTimer: null,
      /** @type {Record<string, { at: number, payload: object }>} */
      tabCache: {}
    }
  },
  computed: {
    ...mapState('dpGame', ['ecoMode']),
    retroLbFx() {
      return (
        this.gameUiTheme === 'retro8bit' &&
        !this.ecoMode &&
        !this.prefersReducedMotion
      )
    },
    scanSkeletonRows() {
      var rows = []
      for (var i = 0; i < SKELETON_ROW_COUNT; i++) {
        rows.push({
          rank: SCAN_SKELETON_RANKS[i] || '#--',
          nick: SCAN_SKELETON_NICKS[i] || 'SCAN...',
          mult: '×--.--'
        })
      }
      return rows
    },
    tableWrapClasses() {
      return {
        'lb-table-wrap--fx': this.retroLbFx,
        'lb-table-wrap--scan-active': this.retroLbFx && this.scanActive,
        'lb-table-wrap--glitch-burst': this.retroLbFx && this.panelGlitchBurst
      }
    }
  },
  created() {
    this.readLoginState()
    this.fetchBoard(this.activeTab, false)
  },
  mounted() {
    this.syncReducedMotion()
    if (typeof window !== 'undefined' && window.matchMedia) {
      var mq = window.matchMedia('(prefers-reduced-motion: reduce)')
      var self = this
      var onChange = function () {
        self.syncReducedMotion()
      }
      if (mq.addEventListener) mq.addEventListener('change', onChange)
      else if (mq.addListener) mq.addListener(onChange)
    }
  },
  beforeDestroy() {
    this.clearScanTimer()
    if (this._glitchTimer) {
      clearTimeout(this._glitchTimer)
      this._glitchTimer = null
    }
  },
  methods: {
    readLoginState() {
      try {
        var raw = localStorage.getItem('userInfo')
        if (!raw) {
          this.isLoggedIn = false
          return
        }
        var u = JSON.parse(raw)
        this.isLoggedIn = !!(u && u.token)
      } catch (e) {
        this.isLoggedIn = false
      }
    },
    goBack() {
      this.$router.push('/home')
    },
    syncReducedMotion() {
      if (typeof window === 'undefined' || !window.matchMedia) {
        this.prefersReducedMotion = false
        return
      }
      this.prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    },
    displayNickname(nickname) {
      var text = dpDisplayNickname(nickname)
      return text || (this.retroLbFx ? 'UNKNOWN' : '—')
    },
    formatRankLabel(rank) {
      if (!this.retroLbFx) return rank
      if (rank == null || rank === '') return '#--'
      var n = Number(rank)
      if (!Number.isFinite(n)) return '#--'
      return '#' + String(n).padStart(2, '0')
    },
    scanRowStyle(idx) {
      if (!this.retroLbFx || !this.scanActive) return null
      return { '--lb-scan-delay': idx * SCAN_ROW_STAGGER_MS + 'ms' }
    },
    clearScanTimer() {
      if (this._scanDoneTimer) {
        clearTimeout(this._scanDoneTimer)
        this._scanDoneTimer = null
      }
    },
    scheduleScanReveal() {
      this.clearScanTimer()
      if (!this.retroLbFx || !this.items.length) {
        this.scanActive = false
        return
      }
      this.scanActive = false
      var self = this
      this.$nextTick(function () {
        self.scanRevealKey++
        self.scanActive = true
        var duration = SCAN_BASE_MS + self.items.length * SCAN_ROW_STAGGER_MS + 60
        self._scanDoneTimer = setTimeout(function () {
          self.scanActive = false
          self._scanDoneTimer = null
        }, duration)
      })
    },
    runPanelGlitchThen(fn) {
      if (this._glitchTimer) {
        clearTimeout(this._glitchTimer)
        this._glitchTimer = null
      }
      if (!this.retroLbFx) {
        if (typeof fn === 'function') fn()
        return
      }
      this.panelGlitchBurst = true
      var self = this
      this._glitchTimer = setTimeout(function () {
        self.panelGlitchBurst = false
        self._glitchTimer = null
        if (typeof fn === 'function') fn()
      }, GLITCH_BURST_MS)
    },
    switchTab(tab) {
      if (tab === this.activeTab) return
      this.activeTab = tab
      var self = this
      if (tab === 'room' && this.retroLbFx) {
        this.runPanelGlitchThen(function () {
          self.fetchBoard(tab, true)
        })
      } else {
        this.fetchBoard(tab, true)
      }
    },
    formatMultiplier(value) {
      if (value == null || value === '') return '—'
      var n = Number(value)
      if (!Number.isFinite(n)) return '—'
      return '×' + n.toFixed(2)
    },
    avatarCacheBustFromUpdatedAt: avatarCacheBustFromUpdatedAt,
    isPodiumRank(rank) {
      return rank === 1 || rank === 2 || rank === 3
    },
    podiumTier(rank) {
      if (rank === 1) return 'gold'
      if (rank === 2) return 'silver'
      if (rank === 3) return 'bronze'
      return ''
    },
    podiumRowClass(rank) {
      if (rank === 1) return 'lb-table__row--podium-gold'
      if (rank === 2) return 'lb-table__row--podium-silver'
      if (rank === 3) return 'lb-table__row--podium-bronze'
      return ''
    },
    applyPayload(data) {
      this.items = Array.isArray(data.items) ? data.items : []
      this.weekMonday = data.weekMonday || ''
      if (data.staleHint) {
        this.staleHintText = String(data.staleHint)
      }
      if (data.message && !this.items.length) {
        this.emptyMessage = String(data.message)
      } else if (!this.items.length) {
        this.emptyMessage = this.staleHintText || '暂无上榜数据，请稍后再看。'
      }
      this.myRank = data.myRank != null ? data.myRank : null
      this.myMultiplier = data.myMultiplier != null ? data.myMultiplier : null
    },
    cachePayload(tab, data) {
      this.tabCache[tab] = { at: Date.now(), payload: data }
    },
    getCached(tab) {
      var entry = this.tabCache[tab]
      if (!entry) return null
      if (Date.now() - entry.at > TAB_CACHE_MS) return null
      return entry.payload
    },
    async fetchBoard(tab, useCache) {
      if (useCache) {
        var cached = this.getCached(tab)
        if (cached) {
          this.loading = false
          this.loadError = ''
          this.applyPayload(cached)
          this.scheduleScanReveal()
          return
        }
      }
      this.scanActive = false
      this.loading = true
      this.loadError = ''
      try {
        var req =
          tab === 'room'
            ? getWeeklyRoomLeaderboard(this.$http)
            : getWeeklyHandLeaderboard(this.$http)
        var res = await req
        var body = res && res.data
        if (!dpResultSuccess(body)) {
          this.loadError = dpResultMessage(body)
          this.items = []
          return
        }
        var data = dpResultData(body) || {}
        this.applyPayload(data)
        this.cachePayload(tab, data)
      } catch (err) {
        this.loadError = dpAxiosErrorMessage(err)
        this.items = []
      } finally {
        this.loading = false
        if (!this.loadError) {
          this.scheduleScanReveal()
        }
      }
    }
  }
}
</script>

<style scoped>
.lb-page {
  max-width: 720px;
  margin: 0 auto;
  padding: clamp(12px, 3vw, 24px) clamp(12px, 4vw, 20px) clamp(72px, 12vw, 88px);
  text-align: left;
  min-height: 100%;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}
.lb-page__header {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.lb-page__title {
  font-size: clamp(20px, 4.5vw, 26px);
  margin: 0 0 6px;
  font-weight: 700;
}
.lb-page__hints {
  margin: 0;
  font-size: 13px;
  color: var(--dp-text-secondary, #909399);
  line-height: 1.5;
}
.lb-page__hints-sep {
  margin: 0 4px;
}
.lb-page__week {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--dp-text-secondary, #606266);
}
.lb-page__header-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}
.lb-page__theme-row {
  font-size: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.lb-page__back {
  padding: 8px 14px;
  border: 1px solid var(--dp-input-border, #dcdfe6);
  border-radius: 6px;
  background: var(--dp-btn-ghost-bg, #fff);
  color: var(--dp-text-primary, #303133);
  cursor: pointer;
  font-size: 14px;
}
.lb-page__back:hover {
  border-color: var(--dp-accent, #409eff);
  color: var(--dp-accent, #409eff);
}
.lb-page__tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}
.lb-page__tab {
  flex: 1;
  padding: 10px 12px;
  border: 1px solid var(--dp-input-border, #dcdfe6);
  border-radius: 8px;
  background: var(--dp-btn-ghost-bg, #fff);
  color: var(--dp-text-primary, #303133);
  font-size: 14px;
  cursor: pointer;
  transition:
    border-color var(--dp-motion-duration-shell, 0.2s),
    color var(--dp-motion-duration-shell, 0.2s),
    background var(--dp-motion-duration-shell, 0.2s);
}
.lb-page__tab:hover {
  border-color: var(--dp-accent, #409eff);
  color: var(--dp-accent, #409eff);
}
.lb-page__tab--active {
  border-color: var(--dp-accent, #409eff);
  background: color-mix(in srgb, var(--dp-accent, #409eff) 12%, var(--dp-btn-ghost-bg, #fff));
  color: var(--dp-accent, #409eff);
  font-weight: 600;
}
.lb-page__body {
  flex: 1 1 auto;
  min-height: 120px;
}
.lb-page__status {
  color: var(--dp-text-secondary, #909399);
  font-size: 14px;
}
.lb-page__error {
  color: var(--dp-danger, #f56c6c);
  font-size: 14px;
}
.lb-page__empty {
  color: var(--dp-text-secondary, #606266);
  font-size: 14px;
  line-height: 1.6;
  padding: 16px 0;
}
.lb-table-wrap {
  overflow-x: auto;
  border: 1px solid var(--dp-subpanel-border, #ebeef5);
  border-radius: 8px;
  background: var(--dp-subpanel-bg, #fff);
}
.lb-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}
.lb-table th,
.lb-table td {
  padding: 10px 12px;
  border-bottom: 1px solid var(--dp-subpanel-border, #ebeef5);
  text-align: left;
}
.lb-table th {
  background: var(--dp-subpanel-bg, #f5f7fa);
  color: var(--dp-text-secondary, #606266);
  font-weight: 600;
}
.lb-table tr:last-child td {
  border-bottom: none;
}
.lb-table__col-rank {
  width: clamp(56px, 14vw, 80px);
}
.lb-table__col-mult {
  width: 100px;
  text-align: right;
}
.lb-table__rank {
  font-weight: 600;
  color: var(--dp-text-primary, #303133);
}
.lb-table__rank-cell {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-width: 0;
}
.lb-table__crown {
  flex-shrink: 0;
  display: block;
}
.lb-table__crown--gold {
  color: var(--dp-lb-podium-gold);
}
.lb-table__crown--silver {
  color: var(--dp-lb-podium-silver);
}
.lb-table__crown--bronze {
  color: var(--dp-lb-podium-bronze);
}
.lb-table__rank-num {
  font-variant-numeric: tabular-nums;
  line-height: 1.2;
}
.lb-table__nick-podium {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}
.lb-table__nick-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}
.lb-table__row--podium-gold td {
  background: var(--dp-lb-podium-row-bg-gold);
  box-shadow: inset 4px 0 0 var(--dp-lb-podium-gold);
}
.lb-table__row--podium-gold .lb-table__rank,
.lb-table__row--podium-gold .lb-table__nick-text {
  color: var(--dp-lb-podium-gold);
}
.lb-table__row--podium-silver td {
  background: var(--dp-lb-podium-row-bg-silver);
  box-shadow: inset 4px 0 0 var(--dp-lb-podium-silver);
}
.lb-table__row--podium-silver .lb-table__rank,
.lb-table__row--podium-silver .lb-table__nick-text {
  color: var(--dp-lb-podium-silver);
}
.lb-table__row--podium-bronze td {
  background: var(--dp-lb-podium-row-bg-bronze);
  box-shadow: inset 4px 0 0 var(--dp-lb-podium-bronze);
}
.lb-table__row--podium-bronze .lb-table__rank,
.lb-table__row--podium-bronze .lb-table__nick-text {
  color: var(--dp-lb-podium-bronze);
}
.lb-table__row--podium-gold .lb-table__mult,
.lb-table__row--podium-silver .lb-table__mult,
.lb-table__row--podium-bronze .lb-table__mult {
  color: var(--dp-text-primary, #303133);
}
.lb-table__mult {
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: var(--dp-warning, #e6a23c);
  font-weight: 600;
}
.lb-page__footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 12px 16px;
  padding-bottom: max(12px, env(safe-area-inset-bottom));
  background: var(--dp-panel-bg, rgba(255, 255, 255, 0.96));
  border-top: 1px solid var(--dp-subpanel-border, #ebeef5);
  box-shadow: var(--dp-depth-elev-1, 0 -4px 12px rgba(0, 0, 0, 0.06));
  font-size: 14px;
  color: var(--dp-text-primary, #303133);
  text-align: center;
  z-index: 10;
}
.lb-page__footer-label {
  margin-right: 6px;
  color: var(--dp-text-secondary, #606266);
}
.lb-page__footer-sep {
  margin: 0 6px;
  color: var(--dp-text-muted, #c0c4cc);
}
.lb-page__footer-muted {
  color: var(--dp-text-secondary, #909399);
}
.lb-page__footer strong {
  color: var(--dp-accent, #409eff);
}

.lb-page--dp {
  font-family: var(--dp-font-ui, inherit);
  color: var(--dp-text-primary, #303133);
}
.lb-page--dp .lb-page__title {
  color: var(--dp-text-primary, #303133);
}
</style>
