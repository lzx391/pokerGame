<template>
  <div
    class="hand-detail-page-root"
    :class="{ 'dp-game-root': !embedded }"
    :data-dp-game-theme="!embedded ? gameUiTheme : undefined"
  >
  <div class="hand-detail-page hand-detail-page--embedded">
    <div class="hand-detail-page__shell">
      <header class="hand-detail-page__hero">
        <div class="hand-detail-page__hero-top">
          <button type="button" class="hand-detail-page__back" @click="goBack">
            <span class="hand-detail-page__back-icon" aria-hidden="true">←</span>
            返回列表
          </button>
          <div v-if="!embedded" class="dp-game-theme-row hand-detail-page__theme-row">
            <span class="dp-game-theme-row__label">界面主题</span>
            <select
              class="dp-game-theme-select"
              aria-label="选择界面主题"
              :value="gameUiTheme"
              @change="onLobbyThemeChange($event.target.value)"
            >
              <option v-for="t in gameThemeOptions" :key="t.id" :value="t.id">{{ t.label }}</option>
            </select>
          </div>
        </div>
        <div class="hand-detail-page__hero-text">
          <h1 class="hand-detail-page__title">牌谱详情</h1>
          <p class="hand-detail-page__subtitle">按街复盘行动与公共牌，结算页查看盈亏与边池。</p>
        </div>
      </header>

      <div v-if="loading" class="hand-detail-page__state hand-detail-page__state--loading">
        <span class="hand-detail-page__spinner" aria-hidden="true" />
        加载中…
      </div>
      <p v-else-if="loadError" class="hand-detail-page__state hand-detail-page__state--error">{{ loadError }}</p>

      <template v-else-if="detail">
        <div class="hand-detail-page__meta-bar">
          <div class="hand-detail-page__meta-chip" title="房间">
            <span class="hand-detail-page__meta-k">房间</span>
            <span class="hand-detail-page__meta-v">{{ detail.roomId }}</span>
          </div>
          <div class="hand-detail-page__meta-chip" title="结束时间">
            <span class="hand-detail-page__meta-k">结束</span>
            <span class="hand-detail-page__meta-v">{{ formatTime(detail.endedAtMs) }}</span>
          </div>
          <div class="hand-detail-page__meta-chip" title="开局底分">
            <span class="hand-detail-page__meta-k">开局底分</span>
            <span class="hand-detail-page__meta-v">{{ detail.smallBlindChips }} / {{ detail.bigBlindChips }}</span>
          </div>
          <div class="hand-detail-page__meta-chip" title="发牌位">
            <span class="hand-detail-page__meta-k">发牌位</span>
            <span class="hand-detail-page__meta-v">{{ detail.dealerNickname || '—' }}</span>
          </div>
        </div>

        <nav class="hand-detail-page__tabs" role="tablist" aria-label="牌局阶段">
          <button
            v-for="tab in STREET_TABS"
            :key="tab.key"
            type="button"
            role="tab"
            :aria-selected="activeTab === tab.key"
            class="hand-detail-page__tab"
            :class="{ 'hand-detail-page__tab--active': activeTab === tab.key }"
            @click="activeTab = tab.key"
          >
            {{ tab.label }}
          </button>
        </nav>

        <section v-if="activeTab !== 'settlement'" class="hand-detail-page__panel">
          <div class="hand-detail-page__board">
            <div class="hand-detail-page__board-head">
              <span class="hand-detail-page__board-title">公共牌</span>
              <span class="hand-detail-page__board-hint">{{ streetHint }}</span>
            </div>
            <div class="hand-detail-page__board-felt">
              <template v-if="!communityCardsForTab.length">
                <div class="hand-detail-page__board-empty">本街尚未发出公共牌</div>
              </template>
              <div v-else class="hand-detail-page__card-row">
                <div
                  v-for="c in communityCardsForTab"
                  :key="'b-' + activeTab + '-' + c"
                  :class="[cardClass(c), 'hand-detail-page__card']"
                >
                  {{ cardFace(c) }}
                </div>
              </div>
            </div>
          </div>

          <div v-if="!streetActions.length" class="hand-detail-page__empty-block">本街暂无行动记录</div>
          <div v-else class="hand-detail-table-wrap">
            <table class="hand-detail-table">
              <thead>
                <tr>
                  <th scope="col">玩家</th>
                  <th
                    v-for="(label, idx) in roundColumnHeaders"
                    :key="'rh' + idx"
                    scope="col"
                  >{{ label }}</th>
                  <th scope="col">底牌</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="nick in rowNicknames" :key="nick">
                  <td class="hand-detail-page__player-cell">
                    <div class="hand-detail-page__player-line">
                      <span class="hand-detail-page__name">{{ nick }}</span>
                      <span
                        v-for="t in (playerRoleTags[nick] || [])"
                        :key="nick + '-role-' + t"
                        class="hand-detail-page__role-tag"
                        :class="roleTagClass(t)"
                      >{{ t }}</span>
                    </div>
                  </td>
                  <td
                    v-for="(label, idx) in roundColumnHeaders"
                    :key="nick + '-' + label + '-' + idx"
                    class="hand-detail-table__cell-actions"
                  >
                    <span class="hand-detail-table__action-text">{{ cellText(nick, idx) }}</span>
                  </td>
                  <td class="hand-detail-table__holes-cell">
                    <template v-if="holeCardsByNickForStreet[nick] === null">
                      <span class="hand-detail-page__no-holes">隐藏</span>
                    </template>
                    <div
                      v-else-if="holeCardsByNickForStreet[nick].length"
                      class="hand-detail-page__card-row hand-detail-page__card-row--holes"
                    >
                      <div
                        v-for="(hc, hidx) in holeCardsByNickForStreet[nick]"
                        :key="nick + '-s-' + hidx"
                        :class="[cardClass(hc), 'hand-detail-page__card']"
                      >
                        {{ cardFace(hc) }}
                      </div>
                    </div>
                    <span v-else class="hand-detail-page__no-holes">—</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <section v-else class="hand-detail-page__panel">
          <div class="hand-detail-page__board hand-detail-page__board--final">
            <div class="hand-detail-page__board-head">
              <span class="hand-detail-page__board-title">终局公共牌</span>
            </div>
            <div class="hand-detail-page__board-felt hand-detail-page__board-felt--final">
              <template v-if="!finalBoardCards.length">
                <div class="hand-detail-page__board-empty">本手未发出公共牌</div>
              </template>
              <div v-else class="hand-detail-page__card-row">
                <div
                  v-for="c in finalBoardCards"
                  :key="'final-' + c"
                  :class="[cardClass(c), 'hand-detail-page__card']"
                >
                  {{ cardFace(c) }}
                </div>
              </div>
            </div>
          </div>

          <h2 class="hand-detail-page__subh">盈亏与摊牌</h2>
          <div class="hand-detail-table-wrap">
            <table class="hand-detail-table hand-detail-table--settlement">
              <thead>
                <tr>
                  <th scope="col">玩家</th>
                  <th scope="col">本手盈亏</th>
                  <th scope="col">底牌</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in settlementRowsWithCards" :key="row.nick">
                  <td class="hand-detail-page__player-cell">
                    <div class="hand-detail-page__player-line">
                      <span class="hand-detail-page__name">{{ row.nick }}</span>
                      <span
                        v-for="t in (playerRoleTags[row.nick] || [])"
                        :key="row.nick + '-sr-' + t"
                        class="hand-detail-page__role-tag"
                        :class="roleTagClass(t)"
                      >{{ t }}</span>
                    </div>
                  </td>
                  <td :class="['hand-detail-table__net', netClass(row.net)]">{{ row.net }}</td>
                  <td class="hand-detail-table__holes-cell">
                    <span v-if="row.folded && !row.isSelf" class="hand-detail-page__folded-label">已盖牌</span>
                    <div v-else-if="row.cards.length" class="hand-detail-page__card-row hand-detail-page__card-row--holes">
                      <div
                        v-for="(c, idx) in row.cards"
                        :key="row.nick + '-h-' + idx"
                        :class="[cardClass(c), 'hand-detail-page__card']"
                      >
                        {{ cardFace(c) }}
                      </div>
                    </div>
                    <span v-else class="hand-detail-page__no-holes">—</span>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <h2 class="hand-detail-page__subh">边池</h2>
          <ul v-if="pots.length" class="hand-detail-page__pots">
            <li v-for="(p, i) in pots" :key="'pot' + i" class="hand-detail-page__pot-item">
              <span class="hand-detail-page__pot-index">池 {{ i + 1 }}</span>
              <span class="hand-detail-page__pot-amount">{{ p.amount }}</span>
              <span class="hand-detail-page__pot-names">{{ (p.eligibleNicknames || []).join('、') || '—' }}</span>
            </li>
          </ul>
          <p v-else class="hand-detail-page__empty-block">无池数据</p>
        </section>
      </template>
    </div>
  </div>
  </div>
</template>

<script>
import '@/styles/dp-game-themes.css'
import '@/styles/dp-lobby-shell.css'
import dpLobbyThemeMixin from '@/mixins/dpLobbyThemeMixin'
import '@/styles/dp-poker-cards.css'
import { getCardClass, getCardDisplay } from '@/utils/dpGameCardVisual'
import {
  STREET_TABS,
  seatNicknamesOrdered,
  activePlayersBeforeStreet,
  splitRoundsByRaises,
  buildRoundGrid,
  boardForStreet,
  formatActionText,
  firstFoldStage,
  shouldShowHoleCardsOnStreetTab,
  finalCommunityCards,
  playerRoleTagsByNickname
} from '@/utils/dpHandHistoryReplay.js'
import { ensureDpUserIdInStorage } from '@/utils/dpEnsureUserId'

export default {
  name: 'HandHistoryDetail',
  mixins: [dpLobbyThemeMixin],
  props: {
    handHistoryId: {
      type: [String, Number],
      required: true
    },
    /** 为 true 时「返回列表」交给父组件（如对局内弹层），不跳转路由 */
    embedded: { type: Boolean, default: false }
  },
  data() {
    return {
      STREET_TABS,
      user: null,
      detail: null,
      loading: false,
      loadError: '',
      activeTab: 'preflop'
    }
  },
  computed: {
    payload() {
      return (this.detail && this.detail.payload) || {}
    },
    actions() {
      return Array.isArray(this.payload.actions) ? this.payload.actions : []
    },
    seatsAtStart() {
      return Array.isArray(this.payload.seatsAtStart) ? this.payload.seatsAtStart : []
    },
    boardsByStreet() {
      return Array.isArray(this.payload.boardsByStreet) ? this.payload.boardsByStreet : []
    },
    rowNicknames() {
      return seatNicknamesOrdered(this.seatsAtStart)
    },
    streetActions() {
      if (this.activeTab === 'settlement') return []
      return this.actions.filter((a) => a && a.stage === this.activeTab)
    },
    communityCardsForTab() {
      if (this.activeTab === 'settlement') return []
      return boardForStreet(this.boardsByStreet, this.activeTab)
    },
    /**
     * 当前街底牌列：本人始终可看自己的底牌；他人若盖牌则不展示，否则仍按街与盖牌时机规则。
     * null 表示本格不展示他人底牌。
     */
    holeCardsByNickForStreet() {
      const tab = this.activeTab
      const out = {}
      if (tab === 'settlement') return out
      const holes = this.payload.holeCardsAtEnd
      const map = holes && typeof holes === 'object' ? holes : {}
      const viewer = this.user && this.user.nickname
      for (const nick of this.rowNicknames) {
        const isSelf = viewer && nick === viewer
        if (isSelf) {
          const raw = map[nick]
          out[nick] = Array.isArray(raw) ? raw : []
          continue
        }
        if (this.foldedNicknames.has(nick)) {
          out[nick] = null
          continue
        }
        const ff = firstFoldStage(this.actions, nick)
        if (!shouldShowHoleCardsOnStreetTab(ff, tab)) {
          out[nick] = null
          continue
        }
        const raw = map[nick]
        out[nick] = Array.isArray(raw) ? raw : []
      }
      return out
    },
    finalBoardCards() {
      return finalCommunityCards(this.boardsByStreet)
    },
    playerRoleTags() {
      return playerRoleTagsByNickname(this.seatsAtStart, this.detail && this.detail.dealerNickname)
    },
    roundColumnHeaders() {
      if (this.activeTab === 'settlement') return []
      const { prefix, rounds } = splitRoundsByRaises(this.streetActions)
      const h = []
      if (prefix.length) h.push('前置')
      for (let i = 0; i < rounds.length; i++) {
        h.push('第' + (i + 1) + '圈')
      }
      return h
    },
    foldedNicknames() {
      const s = new Set()
      for (const a of this.actions) {
        if (a && a.type === 'FOLD' && a.actorNickname) s.add(a.actorNickname)
      }
      return s
    },
    settlementRowsWithCards() {
      const holes = this.payload.holeCardsAtEnd
      const map = holes && typeof holes === 'object' ? holes : {}
      const viewer = this.user && this.user.nickname
      return this.settlementNetRows.map((row) => {
        const folded = this.foldedNicknames.has(row.nick)
        const isSelf = viewer && row.nick === viewer
        let cards = []
        if (isSelf) {
          const raw = map[row.nick]
          cards = Array.isArray(raw) ? raw : []
        } else if (folded) {
          cards = []
        } else {
          const raw = map[row.nick]
          cards = Array.isArray(raw) ? raw : []
        }
        return { ...row, folded, isSelf, cards }
      })
    },
    playersForStreet() {
      const street = this.activeTab
      if (street === 'settlement') return []
      const ordered = this.rowNicknames
      const playersStart = activePlayersBeforeStreet(this.actions, street, ordered)
      let playersArr = ordered.filter((n) => playersStart.has(n))
      if (!playersArr.length && this.streetActions.length) {
        const seen = new Set()
        for (const a of this.streetActions) {
          if (a && a.actorNickname) seen.add(a.actorNickname)
        }
        playersArr = ordered.filter((n) => seen.has(n))
      }
      return playersArr
    },
    roundGrid() {
      const street = this.activeTab
      if (street === 'settlement') return []
      const playersArr = this.playersForStreet
      const { prefix, rounds } = splitRoundsByRaises(this.streetActions)
      const cols = []
      if (prefix.length) {
        cols.push(buildRoundGrid([prefix], playersArr)[0])
      }
      if (rounds.length) {
        cols.push(...buildRoundGrid(rounds, playersArr))
      }
      return cols
    },
    roundColCount() {
      return this.roundGrid.length
    },
    settlementNetRows() {
      const net = this.payload.netChipsChange
      if (!net || typeof net !== 'object') return []
      const order = this.rowNicknames
      const rows = []
      for (const nick of order) {
        if (Object.prototype.hasOwnProperty.call(net, nick)) {
          rows.push({ nick, net: net[nick] })
        }
      }
      for (const k of Object.keys(net)) {
        if (order.indexOf(k) === -1) {
          rows.push({ nick: k, net: net[k] })
        }
      }
      return rows
    },
    pots() {
      return Array.isArray(this.payload.potsBeforeSettlement) ? this.payload.potsBeforeSettlement : []
    },
    streetHint() {
      const map = {
        preflop: '翻前无公共牌',
        flop: '最多 3 张',
        turn: '第 4 张',
        river: '第 5 张'
      }
      return map[this.activeTab] || ''
    }
  },
  watch: {
    handHistoryId() {
      this.fetchDetail()
    }
  },
  async created() {
    try {
      const raw = localStorage.getItem('userInfo')
      this.user = raw ? JSON.parse(raw) : null
    } catch (e) {
      this.user = null
    }
    if (!this.user || !this.user.nickname) {
      if (this.embedded) {
        this.$emit('back')
      } else {
        this.$router.replace('/login')
      }
      return
    }
    this.user = (await ensureDpUserIdInStorage(this.$http)) || this.user
    var uid = Number(this.user && this.user.userId)
    if (!this.user || isNaN(uid) || uid <= 0) {
      if (this.embedded) {
        this.$emit('back')
      } else {
        this.$router.replace('/login')
      }
      return
    }
    this.user.userId = uid
    this.fetchDetail()
  },
  methods: {
    cardClass(c) {
      return getCardClass(c)
    },
    cardFace(c) {
      return getCardDisplay(c)
    },
    roleTagClass(t) {
      if (t === '发牌') return 'hand-detail-page__role-tag--dealer'
      if (t === '底1') return 'hand-detail-page__role-tag--sb'
      if (t === '底2') return 'hand-detail-page__role-tag--bb'
      return ''
    },
    goBack() {
      if (this.embedded) {
        this.$emit('back')
      } else {
        this.$router.push('/hand-history')
      }
    },
    formatTime(ms) {
      if (ms == null || ms === '') return '—'
      var d = new Date(Number(ms))
      if (isNaN(d.getTime())) return '—'
      return d.toLocaleString()
    },
    netClass(n) {
      if (n == null) return ''
      if (n > 0) return 'hand-detail-table__net--win'
      if (n < 0) return 'hand-detail-table__net--lose'
      return ''
    },
    cellText(nick, colIdx) {
      const g = this.roundGrid
      if (!g.length) {
        if (colIdx !== 0) return '—'
        var parts = []
        for (var i = 0; i < this.streetActions.length; i++) {
          var a = this.streetActions[i]
          if (a && a.actorNickname === nick) {
            parts.push(formatActionText(a))
          }
        }
        return parts.length ? parts.join('\n') : '—'
      }
      const col = g[colIdx]
      if (!col) return '—'
      return col[nick] != null ? col[nick] : '—'
    },
    async fetchDetail() {
      var id = Number(this.handHistoryId)
      if (isNaN(id) || id <= 0) {
        this.loadError = '无效的手牌 ID'
        this.detail = null
        return
      }
      this.loading = true
      this.loadError = ''
      try {
        var params = {
          handHistoryId: id,
          userId: Number(this.user.userId)
        }
        var res = await this.$http.get('/dpHandHistory/detail', { params: params })
        this.detail = res.data || null
        if (!this.detail) {
          this.loadError = '未找到数据'
        }
      } catch (e) {
        console.error('hand history detail', e)
        this.detail = null
        this.loadError = '加载失败：无权限或牌谱不存在。'
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.hand-detail-page {
  --hd-bg: linear-gradient(165deg, #eef2f7 0%, #e4eaf3 45%, #f0f4fa 100%);
  --hd-surface: #ffffff;
  --hd-felt: linear-gradient(145deg, #0d3d2e 0%, #0f4a36 40%, #0a3024 100%);
  --hd-felt-border: rgba(255, 255, 255, 0.08);
  --hd-text: #1a2332;
  --hd-muted: #5c6b7e;
  --hd-accent: #1e6b55;
  --hd-tab-active: #0f6b4f;
  min-height: 100%;
  box-sizing: border-box;
  background: var(--hd-bg);
  padding: clamp(16px, 4vw, 28px) clamp(12px, 3vw, 24px) 40px;
}

.hand-detail-page__shell {
  max-width: 920px;
  margin: 0 auto;
}

.hand-detail-page__hero {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 20px;
}

.hand-detail-page__hero-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  flex-wrap: wrap;
}

.hand-detail-page__theme-row {
  justify-content: flex-end;
  margin-bottom: 0;
}

.hand-detail-page__back {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 9px 16px 9px 12px;
  border: none;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.85);
  box-shadow: 0 1px 3px rgba(26, 35, 50, 0.08), 0 0 0 1px rgba(26, 35, 50, 0.06);
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  color: var(--hd-text);
  transition: box-shadow 0.2s ease, transform 0.15s ease;
}

.hand-detail-page__back:hover {
  box-shadow: 0 4px 14px rgba(15, 107, 79, 0.18), 0 0 0 1px rgba(15, 107, 79, 0.2);
  color: var(--hd-tab-active);
}

.hand-detail-page__back-icon {
  font-size: 16px;
  line-height: 1;
  opacity: 0.75;
}

.hand-detail-page__hero-text {
  width: 100%;
}

.hand-detail-page__title {
  margin: 0 0 6px;
  font-size: clamp(1.35rem, 3vw, 1.65rem);
  font-weight: 700;
  letter-spacing: -0.02em;
  color: var(--hd-text);
}

.hand-detail-page__subtitle {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--hd-muted);
  max-width: 36em;
}

.hand-detail-page__state {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px;
  border-radius: 14px;
  background: var(--hd-surface);
  box-shadow: 0 2px 12px rgba(26, 35, 50, 0.06);
  font-size: 14px;
}

.hand-detail-page__state--loading {
  color: var(--hd-muted);
}

.hand-detail-page__state--error {
  color: #c45656;
  border: 1px solid rgba(196, 86, 86, 0.25);
}

.hand-detail-page__spinner {
  width: 18px;
  height: 18px;
  border: 2px solid #e0e6ee;
  border-top-color: var(--hd-accent);
  border-radius: 50%;
  animation: hand-detail-spin 0.75s linear infinite;
}

@keyframes hand-detail-spin {
  to {
    transform: rotate(360deg);
  }
}

.hand-detail-page__meta-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 18px;
}

.hand-detail-page__meta-chip {
  display: inline-flex;
  align-items: baseline;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 12px;
  background: var(--hd-surface);
  box-shadow: 0 1px 0 rgba(255, 255, 255, 0.9) inset, 0 2px 10px rgba(26, 35, 50, 0.06);
  border: 1px solid rgba(26, 35, 50, 0.06);
}

.hand-detail-page__meta-k {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: #8b98a8;
}

.hand-detail-page__meta-v {
  font-size: 13px;
  font-weight: 600;
  color: var(--hd-text);
  word-break: break-all;
}

.hand-detail-page__tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0;
  padding: 4px;
  margin-bottom: 18px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.65);
  box-shadow: inset 0 1px 2px rgba(26, 35, 50, 0.06);
  border: 1px solid rgba(26, 35, 50, 0.07);
}

.hand-detail-page__tab {
  flex: 1 1 auto;
  min-width: 72px;
  padding: 10px 12px;
  border: none;
  border-radius: 10px;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  color: var(--hd-muted);
  transition: color 0.15s ease, background 0.15s ease, box-shadow 0.15s ease;
}

.hand-detail-page__tab:hover {
  color: var(--hd-text);
}

.hand-detail-page__tab--active {
  background: var(--hd-surface);
  color: var(--hd-tab-active);
  box-shadow: 0 2px 10px rgba(15, 107, 79, 0.12);
}

.hand-detail-page__panel {
  padding: 20px;
  border-radius: 18px;
  background: var(--hd-surface);
  box-shadow: 0 4px 24px rgba(26, 35, 50, 0.07), 0 0 0 1px rgba(26, 35, 50, 0.04);
}

.hand-detail-page__board {
  margin-bottom: 20px;
}

.hand-detail-page__board--final {
  margin-bottom: 8px;
}

.hand-detail-page__board-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
  flex-wrap: wrap;
}

.hand-detail-page__board-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--hd-text);
}

.hand-detail-page__board-hint {
  font-size: 12px;
  color: var(--hd-muted);
}

.hand-detail-page__board-felt {
  position: relative;
  min-height: 88px;
  padding: 16px 18px;
  border-radius: 14px;
  background: var(--hd-felt);
  box-shadow: inset 0 1px 0 var(--hd-felt-border), 0 8px 24px rgba(0, 0, 0, 0.18);
  border: 1px solid rgba(0, 0, 0, 0.2);
}

.hand-detail-page__board-felt--final {
  min-height: 96px;
}

.hand-detail-page__board-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 56px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.45);
  letter-spacing: 0.02em;
}

.hand-detail-page__empty-block {
  margin: 0 0 16px;
  padding: 16px;
  text-align: center;
  font-size: 14px;
  color: var(--hd-muted);
  background: #f6f8fb;
  border-radius: 12px;
  border: 1px dashed #d5dde8;
}

.hand-detail-page__card-row {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  vertical-align: middle;
}

.hand-detail-page__card-row--holes {
  justify-content: flex-start;
}

.hand-detail-page__card {
  flex-shrink: 0;
  transform: scale(1);
  transform-origin: center center;
}

.hand-detail-page .card-base:hover {
  transform: scale(1);
  filter: none;
}

.hand-detail-page .card-base::after {
  animation: none;
  opacity: 0;
}

.hand-detail-table--settlement .hand-detail-table__holes-cell {
  min-width: 120px;
  vertical-align: middle;
}

.hand-detail-page__folded-label {
  display: inline-block;
  padding: 4px 10px;
  font-size: 12px;
  font-weight: 600;
  color: #8b98a8;
  background: #f0f3f7;
  border-radius: 8px;
}

.hand-detail-page__no-holes {
  color: #a8b4c4;
  font-size: 13px;
  font-weight: 500;
}

.hand-detail-page__subh {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 0.95rem;
  margin: 22px 0 12px;
  font-weight: 700;
  color: var(--hd-text);
  letter-spacing: -0.01em;
}

.hand-detail-page__subh::before {
  content: '';
  width: 4px;
  height: 1em;
  border-radius: 2px;
  background: linear-gradient(180deg, #1e8a6a, #0f5c45);
}

.hand-detail-page__pots {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.hand-detail-page__pot-item {
  display: grid;
  grid-template-columns: auto 1fr;
  grid-template-rows: auto auto;
  column-gap: 14px;
  row-gap: 4px;
  padding: 12px 14px;
  border-radius: 12px;
  background: #f6f8fb;
  border: 1px solid #e8edf4;
}

.hand-detail-page__pot-index {
  grid-column: 1;
  grid-row: 1 / -1;
  align-self: center;
  font-size: 12px;
  font-weight: 700;
  color: var(--hd-accent);
  white-space: nowrap;
}

.hand-detail-page__pot-amount {
  grid-column: 2;
  grid-row: 1;
  font-size: 16px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  color: var(--hd-text);
}

.hand-detail-page__pot-names {
  grid-column: 2;
  grid-row: 2;
  font-size: 13px;
  color: var(--hd-muted);
  line-height: 1.45;
}

.hand-detail-table-wrap {
  overflow-x: auto;
  margin: 0 -4px;
  padding: 0 4px;
  border-radius: 12px;
}

.hand-detail-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #e8edf4;
}

.hand-detail-table th,
.hand-detail-table td {
  border: none;
  border-bottom: 1px solid #eef1f6;
  padding: 12px 14px;
  text-align: left;
  min-width: 72px;
  vertical-align: middle;
}

.hand-detail-table tbody tr:last-child td {
  border-bottom: none;
}

.hand-detail-table tbody tr:nth-child(even) {
  background: #fafbfd;
}

.hand-detail-table tbody tr:hover {
  background: #f3f7fb;
}

.hand-detail-table th {
  background: linear-gradient(180deg, #f8fafc 0%, #f0f4f8 100%);
  color: #4a5568;
  font-weight: 700;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.hand-detail-page__player-cell {
  text-align: left;
  vertical-align: middle;
  min-width: 140px;
}

.hand-detail-page__player-line {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 8px;
}

.hand-detail-page__name {
  font-weight: 700;
  color: var(--hd-text);
}

.hand-detail-page__role-tag {
  display: inline-block;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 700;
  border-radius: 6px;
  vertical-align: middle;
}

.hand-detail-page__role-tag--dealer {
  color: #b45309;
  background: #fff7ed;
  border: 1px solid #fed7aa;
}

.hand-detail-page__role-tag--sb {
  color: #1d4ed8;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
}

.hand-detail-page__role-tag--bb {
  color: #6d28d9;
  background: #f5f3ff;
  border: 1px solid #ddd6fe;
}

.hand-detail-table__cell-actions {
  white-space: pre-line;
  line-height: 1.55;
  font-size: 13px;
  color: #2d3748;
  vertical-align: middle;
  max-width: 260px;
  min-width: 104px;
}

.hand-detail-table__action-text {
  display: inline-block;
}

.hand-detail-table__net {
  font-variant-numeric: tabular-nums;
  font-weight: 700;
}

.hand-detail-table__net--win {
  color: #15803d;
}

.hand-detail-table__net--lose {
  color: #dc2626;
}

/* 对局弹层内：与 .dp-game-root 主题 --dp-* 对齐 */
.hand-detail-page--embedded {
  font-family: var(--dp-font-ui, inherit);
  --hd-bg: var(--dp-game-bg, #f0f2f5);
  --hd-surface: var(--dp-panel-bg, #ffffff);
  --hd-text: var(--dp-text-primary, #1a2332);
  --hd-muted: var(--dp-text-secondary, #5c6b7e);
  --hd-accent: var(--dp-accent, #1e6b55);
  --hd-tab-active: var(--dp-accent, #0f6b4f);
  /* 公共牌底板在下方 .hand-detail-page__board-felt 中覆盖为与圆桌台呢一致的 --dp-table-felt-* */
  --hd-felt: linear-gradient(145deg, #0d3d2e 0%, #0f4a36 40%, #0a3024 100%);
  --hd-felt-border: rgba(255, 255, 255, 0.08);
  --hd-chip-border: var(--dp-panel-border, rgba(26, 35, 50, 0.06));
  padding: clamp(12px, 3vw, 20px) clamp(10px, 2vw, 18px) 28px;
}

.hand-detail-page--embedded .hand-detail-page__back {
  background: var(--dp-subpanel-bg, rgba(255, 255, 255, 0.85));
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12), 0 0 0 1px var(--dp-panel-border, rgba(26, 35, 50, 0.06));
  color: var(--hd-text);
}

.hand-detail-page--embedded .hand-detail-page__back:hover {
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.2), 0 0 0 1px var(--dp-accent, #1890ff);
  color: var(--dp-accent);
}

.hand-detail-page--embedded .hand-detail-page__state {
  box-shadow: var(--dp-panel-shadow, 0 2px 12px rgba(26, 35, 50, 0.06));
  border: 1px solid var(--dp-panel-border, transparent);
}

.hand-detail-page--embedded .hand-detail-page__state--error {
  color: var(--dp-danger, #c45656);
  border-color: rgba(255, 82, 82, 0.35);
  background: var(--dp-subpanel-bg, #fff);
}

.hand-detail-page--embedded .hand-detail-page__spinner {
  border-color: var(--dp-subpanel-border, #e0e6ee);
  border-top-color: var(--dp-accent, #1e6b55);
}

.hand-detail-page--embedded .hand-detail-page__meta-k {
  color: var(--dp-text-muted, #8b98a8);
}

.hand-detail-page--embedded .hand-detail-page__meta-chip {
  background: var(--hd-surface);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  border: 1px solid var(--hd-chip-border);
}

.hand-detail-page--embedded .hand-detail-page__tabs {
  background: var(--dp-subpanel-bg, rgba(255, 255, 255, 0.65));
  border-color: var(--dp-panel-border, rgba(26, 35, 50, 0.07));
}

.hand-detail-page--embedded .hand-detail-page__tab--active {
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.12);
}

.hand-detail-page--embedded .hand-detail-page__panel {
  box-shadow: var(--dp-panel-shadow, 0 4px 24px rgba(26, 35, 50, 0.07)), 0 0 0 1px var(--dp-panel-border, rgba(26, 35, 50, 0.04));
}

/* 与 dp-game-shell.css 圆桌 .dp-game-table__felt 同源：随主题 --dp-table-felt-* / --dp-panel-bg 变化 */
.hand-detail-page--embedded .hand-detail-page__board-felt {
  isolation: isolate;
  background:
    radial-gradient(
      ellipse 55% 50% at 50% 52%,
      color-mix(in srgb, var(--dp-table-felt-spot, var(--dp-success)) 14%, var(--dp-panel-bg)) 0%,
      color-mix(in srgb, var(--dp-panel-bg) 88%, var(--dp-table-felt-depth, #0a1620)) 72%,
      color-mix(in srgb, var(--dp-text-primary) 8%, var(--dp-panel-bg)) 100%
    );
  box-shadow:
    inset 0 0 0 2px color-mix(in srgb, var(--dp-accent) 22%, transparent),
    var(--dp-table-felt-inset-bottom, inset 0 -12px 36px rgba(0, 0, 0, 0.22)),
    var(--dp-table-felt-drop-shadow, 0 10px 28px rgba(0, 0, 0, 0.18));
  border: 1px solid var(--dp-panel-border);
}

.hand-detail-page--embedded .hand-detail-page__board-felt::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 14px;
  z-index: 0;
  pointer-events: none;
  background: var(--dp-table-felt-texture);
  opacity: var(--dp-table-felt-texture-opacity);
}

.hand-detail-page--embedded .hand-detail-page__board-felt > * {
  position: relative;
  z-index: 1;
}

.hand-detail-page--embedded .hand-detail-page__board-empty {
  color: color-mix(in srgb, var(--dp-text-primary) 52%, transparent);
}

.hand-detail-page--embedded .hand-detail-page__empty-block {
  background: var(--dp-subpanel-bg, #f6f8fb);
  color: var(--hd-muted);
  border-color: var(--dp-input-border, #d5dde8);
}

.hand-detail-page--embedded .hand-detail-page__folded-label {
  color: var(--dp-text-muted, #8b98a8);
  background: var(--dp-subpanel-bg, #f0f3f7);
}

.hand-detail-page--embedded .hand-detail-page__no-holes {
  color: var(--dp-text-muted, #a8b4c4);
}

.hand-detail-page--embedded .hand-detail-page__subh::before {
  background: linear-gradient(180deg, var(--dp-accent, #1e8a6a), var(--dp-success, #0f5c45));
}

.hand-detail-page--embedded .hand-detail-page__pot-item {
  background: var(--dp-subpanel-bg, #f6f8fb);
  border-color: var(--dp-subpanel-border, #e8edf4);
}

.hand-detail-page--embedded .hand-detail-table {
  border-color: var(--dp-subpanel-border, #e8edf4);
}

.hand-detail-page--embedded .hand-detail-table th,
.hand-detail-page--embedded .hand-detail-table td {
  border-bottom-color: var(--dp-subpanel-border, #eef1f6);
}

.hand-detail-page--embedded .hand-detail-table tbody tr:nth-child(even) {
  background: var(--dp-subpanel-bg, #fafbfd);
}

.hand-detail-page--embedded .hand-detail-table tbody tr:hover {
  background: var(--dp-subpanel-bg, #f3f7fb);
  box-shadow: inset 0 0 0 1px var(--dp-panel-border, transparent);
}

.hand-detail-page--embedded .hand-detail-table th {
  background: var(--dp-subpanel-bg, #f0f4f8);
  color: var(--dp-text-secondary, #4a5568);
}

.hand-detail-page--embedded .hand-detail-table__cell-actions {
  color: var(--hd-text);
}

.hand-detail-page--embedded .hand-detail-page__role-tag--dealer {
  color: var(--dp-warning, #b45309);
  background: var(--dp-subpanel-bg, #fff7ed);
  border: 1px solid var(--dp-panel-border, #fed7aa);
}

.hand-detail-page--embedded .hand-detail-page__role-tag--sb {
  color: var(--dp-accent, #1d4ed8);
  background: var(--dp-subpanel-bg, #eff6ff);
  border: 1px solid var(--dp-panel-border, #bfdbfe);
}

.hand-detail-page--embedded .hand-detail-page__role-tag--bb {
  color: var(--dp-owner-purple-fg, #6d28d9);
  background: var(--dp-subpanel-bg, #f5f3ff);
  border: 1px solid var(--dp-panel-border, #ddd6fe);
}

.hand-detail-page--embedded .hand-detail-table__net--win {
  color: var(--dp-success, #15803d);
}

.hand-detail-page--embedded .hand-detail-table__net--lose {
  color: var(--dp-danger, #dc2626);
}

@media (max-width: 520px) {
  .hand-detail-page__tab {
    min-width: 56px;
    padding: 8px 8px;
    font-size: 12px;
  }

  .hand-detail-page__panel {
    padding: 14px;
  }
}
</style>
