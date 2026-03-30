<template>
  <div class="hand-detail-page">
    <div class="hand-detail-page__header">
      <button type="button" class="hand-detail-page__back" @click="goBack">返回列表</button>
      <h1>牌谱详情</h1>
    </div>

    <p v-if="loading" class="hand-detail-page__status">加载中…</p>
    <p v-else-if="loadError" class="hand-detail-page__error">{{ loadError }}</p>

    <template v-else-if="detail">
      <div class="hand-detail-page__meta">
        <span>房间 {{ detail.roomId }}</span>
        <span>结束 {{ formatTime(detail.endedAtMs) }}</span>
        <span>盲注 {{ detail.smallBlindChips }} / {{ detail.bigBlindChips }}</span>
        <span>庄家 {{ detail.dealerNickname || '—' }}</span>
      </div>

      <nav class="hand-detail-page__tabs" role="tablist">
        <button
          v-for="tab in STREET_TABS"
          :key="tab.key"
          type="button"
          role="tab"
          class="hand-detail-page__tab"
          :class="{ 'hand-detail-page__tab--active': activeTab === tab.key }"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </nav>

      <section v-if="activeTab !== 'settlement'" class="hand-detail-page__panel">
        <div class="hand-detail-page__board-block">
          <div class="hand-detail-page__board-label">公共牌</div>
          <div v-if="!communityCardsForTab.length" class="hand-detail-page__empty hand-detail-page__empty--inline">（本街未发公共牌）</div>
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
        <div v-if="!streetActions.length" class="hand-detail-page__empty">本街无行动记录。</div>
        <div v-else class="hand-detail-table-wrap">
          <table class="hand-detail-table">
            <thead>
              <tr>
                <th>玩家</th>
                <th v-for="(label, idx) in roundColumnHeaders" :key="'rh' + idx">{{ label }}</th>
                <th>底牌</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="nick in rowNicknames" :key="nick">
                <td class="hand-detail-page__player-cell">
                  <span class="hand-detail-page__name">{{ nick }}</span>
                  <span
                    v-for="t in (playerRoleTags[nick] || [])"
                    :key="nick + '-role-' + t"
                    class="hand-detail-page__role-tag"
                  >{{ t }}</span>
                </td>
                <td
                  v-for="(label, idx) in roundColumnHeaders"
                  :key="nick + '-' + label + '-' + idx"
                  class="hand-detail-table__cell-actions"
                >
                  {{ cellText(nick, idx) }}
                </td>
                <td class="hand-detail-table__holes-cell">
                  <template v-if="holeCardsByNickForStreet[nick] === null">
                    <span class="hand-detail-page__no-holes">—</span>
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
        <div class="hand-detail-page__board-block hand-detail-page__board-block--settlement">
          <div class="hand-detail-page__board-label">公共牌（终局）</div>
          <div v-if="!finalBoardCards.length" class="hand-detail-page__empty hand-detail-page__empty--inline">（本手未发公共牌）</div>
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
        <h2 class="hand-detail-page__subh">盈亏与摊牌</h2>
        <div class="hand-detail-table-wrap">
          <table class="hand-detail-table hand-detail-table--settlement">
            <thead>
              <tr>
                <th>玩家</th>
                <th>本手盈亏</th>
                <th>底牌</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in settlementRowsWithCards" :key="row.nick">
                <td class="hand-detail-page__player-cell">
                  <span class="hand-detail-page__name">{{ row.nick }}</span>
                  <span
                    v-for="t in (playerRoleTags[row.nick] || [])"
                    :key="row.nick + '-sr-' + t"
                    class="hand-detail-page__role-tag"
                  >{{ t }}</span>
                </td>
                <td :class="netClass(row.net)">{{ row.net }}</td>
                <td class="hand-detail-table__holes-cell">
                  <span v-if="row.folded && !row.isSelf" class="hand-detail-page__folded-label">已弃牌</span>
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

        <h2 class="hand-detail-page__subh">池</h2>
        <ul v-if="pots.length" class="hand-detail-page__pots">
          <li v-for="(p, i) in pots" :key="'pot' + i">
            池 {{ i + 1 }}：{{ p.amount }} — {{ (p.eligibleNicknames || []).join('、') || '—' }}
          </li>
        </ul>
        <p v-else class="hand-detail-page__empty">无池数据</p>
      </section>
    </template>
  </div>
</template>

<script>
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

export default {
  name: 'HandHistoryDetail',
  props: {
    handHistoryId: {
      type: [String, Number],
      required: true
    }
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
     * 当前街底牌列：本人始终可看自己的底牌；他人若弃牌则不展示，否则仍按街与弃牌时机规则。
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
    }
  },
  watch: {
    handHistoryId() {
      this.fetchDetail()
    }
  },
  created() {
    try {
      const raw = localStorage.getItem('userInfo')
      this.user = raw ? JSON.parse(raw) : null
    } catch (e) {
      this.user = null
    }
    if (!this.user || !this.user.nickname) {
      this.$router.replace('/login')
      return
    }
    this.fetchDetail()
  },
  methods: {
    cardClass(c) {
      return getCardClass(c)
    },
    cardFace(c) {
      return getCardDisplay(c)
    },
    goBack() {
      this.$router.push('/hand-history')
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
          nickname: this.user.nickname
        }
        if (this.user.userId != null && this.user.userId !== '') {
          params.userId = this.user.userId
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
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 16px;
}
.hand-detail-page__header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.hand-detail-page__header h1 {
  margin: 0;
  font-size: 1.35rem;
}
.hand-detail-page__back {
  padding: 8px 14px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  font-size: 14px;
}
.hand-detail-page__back:hover {
  border-color: #409eff;
  color: #409eff;
}
.hand-detail-page__status {
  color: #909399;
}
.hand-detail-page__error {
  color: #f56c6c;
  line-height: 1.5;
}
.hand-detail-page__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 20px;
  font-size: 13px;
  color: #606266;
  margin-bottom: 16px;
}
.hand-detail-page__tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}
.hand-detail-page__tab {
  padding: 8px 14px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  font-size: 14px;
  color: #606266;
}
.hand-detail-page__tab:hover {
  border-color: #c6e2ff;
  color: #409eff;
}
.hand-detail-page__tab--active {
  border-color: #409eff;
  color: #409eff;
  font-weight: 600;
}
.hand-detail-page__panel {
  margin-top: 8px;
}
.hand-detail-page__board-block--settlement {
  margin-bottom: 20px;
}
.hand-detail-page__board-block {
  margin: 0 0 16px;
  text-align: left;
}
.hand-detail-page__board-label {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
  font-weight: 600;
}
.hand-detail-page__empty--inline {
  margin: 0;
}
.hand-detail-page__card-row {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  vertical-align: middle;
}
.hand-detail-page__card-row--holes {
  justify-content: flex-start;
}
.hand-detail-page__card {
  flex-shrink: 0;
  transform: scale(0.92);
  transform-origin: center center;
}
.hand-detail-page .card-base:hover {
  transform: scale(0.92);
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
  color: #909399;
  font-size: 13px;
}
.hand-detail-page__no-holes {
  color: #c0c4cc;
  font-size: 14px;
}
.hand-detail-page__empty {
  color: #909399;
  font-size: 14px;
}
.hand-detail-page__subh {
  font-size: 1rem;
  margin: 16px 0 8px;
  font-weight: 600;
  color: #303133;
}
.hand-detail-page__pots {
  margin: 0;
  padding-left: 0;
  list-style: none;
  font-size: 14px;
  color: #606266;
  line-height: 1.5;
}
.hand-detail-page__pots li {
  margin-bottom: 6px;
}
.hand-detail-page__holes {
  font-size: 15px;
  letter-spacing: 0.05em;
}
.hand-detail-table-wrap {
  overflow-x: auto;
}
.hand-detail-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.hand-detail-table th,
.hand-detail-table td {
  border: 1px solid #ebeef5;
  padding: 8px 10px;
  text-align: left;
  min-width: 72px;
}
.hand-detail-table th {
  background: #f5f7fa;
  color: #606266;
  font-weight: 600;
}
.hand-detail-page__player-cell {
  text-align: left;
  vertical-align: top;
  white-space: nowrap;
}
.hand-detail-page__name {
  font-weight: 600;
  color: #303133;
  margin-right: 6px;
}
.hand-detail-page__role-tag {
  display: inline-block;
  margin-right: 4px;
  margin-top: 2px;
  padding: 1px 6px;
  font-size: 11px;
  font-weight: 600;
  color: #409eff;
  background: #ecf5ff;
  border: 1px solid #d9ecff;
  border-radius: 4px;
  vertical-align: middle;
}
.hand-detail-table__cell-actions {
  white-space: pre-line;
  line-height: 1.5;
  font-size: 12px;
  color: #303133;
  vertical-align: top;
  max-width: 240px;
  min-width: 100px;
}
.hand-detail-table__net--win {
  color: #67c23a;
  font-weight: 600;
}
.hand-detail-table__net--lose {
  color: #f56c6c;
}
</style>
