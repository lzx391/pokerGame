<template>
  <div
    :class="{ 'dp-game-root': !embedded }"
    :data-dp-game-theme="embedded ? undefined : effectiveThemeForCss"
    :style="embedded ? {} : customThemeInlineStyle"
  >
  <div class="hand-history-page hand-history-page--dp">
    <div class="hand-history-page__header">
      <h1 v-if="showPageTitle">历史对局</h1>
      <div class="hand-history-page__header-actions">
        <div v-if="!embedded" class="dp-game-theme-row hand-history-page__theme-row">
          <span class="dp-game-theme-row__label">界面主题</span>
            <dp-theme-picker
              :game-ui-theme="gameUiTheme"
              :theme-options="gameThemeOptions"
              :custom-theme-base="customThemeBase"
              :custom-theme-overrides="customThemeOverrides"
              @input-theme="onLobbyThemeChange($event)"
              @custom-base="$store.commit('dpGame/SET_CUSTOM_THEME', { baseId: $event })"
              @custom-overrides="$store.commit('dpGame/SET_CUSTOM_THEME', { overrides: $event })"
            />
        </div>
        <button type="button" class="hand-history-page__back" @click="goBack">
          {{ embedded ? '关闭' : '返回大厅' }}
        </button>
      </div>
    </div>

    <div v-if="user && user.nickname" class="hand-history-page__user">
      当前账号：<strong>{{ user.nickname }}</strong>
    </div>

    <p v-if="loading" class="hand-history-page__status">加载中…</p>
    <p v-else-if="loadError" class="hand-history-page__error">{{ loadError }}</p>

    <template v-else>
      <p v-if="total === 0" class="hand-history-page__empty">
        暂无记录。若数据库里只有牌谱主表 <code>dp_observed_hand_history</code> 而没有
        <code>dp_observed_hand_participant</code> 参与者行（例如未登录/未解析到昵称），则不会出现在此列表。
      </p>
      <template v-else>
        <p class="hand-history-page__meta">
          共 <strong>{{ total }}</strong> 条，每页 {{ pageSize }} 条
        </p>
        <div class="hand-history-table-wrap">
          <table class="hand-history-table">
            <thead>
              <tr>
                <th>结束时间</th>
                <th>房间</th>
                <th>主池</th>
                <th>鱼干输赢</th>
                <!-- <th>座位</th> -->
                <th>角色</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(r, i) in rows" :key="r.handHistoryId + '-' + i">
                <td>{{ formatTime(r.endedAtMs) }}</td>
                <td>{{ r.roomId }}</td>
                <td>{{ r.mainPotBeforeSettlement }}</td>
                <td :class="netClass(r.netChips)">{{ r.netChips }}</td>
                <!-- <td>{{ r.seatIndex }}</td> -->
                <td>{{ roleLabel(r) }}</td>
                <td>
                  <button
                    type="button"
                    class="hand-history-table__detail-btn"
                    @click="goDetail(r.handHistoryId)"
                  >
                    查看详情
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="totalPages > 1" class="hand-history-page__pager">
          <button
            type="button"
            class="hand-history-page__pager-btn"
            :disabled="page <= 1 || loading"
            @click="goPage(page - 1)"
          >
            上一页
          </button>
          <span class="hand-history-page__pager-info">第 {{ page }} / {{ totalPages }} 页</span>
          <button
            type="button"
            class="hand-history-page__pager-btn"
            :disabled="page >= totalPages || loading"
            @click="goPage(page + 1)"
          >
            下一页
          </button>
        </div>
      </template>
    </template>
  </div>
  </div>
</template>

<script>
import '@/styles/dp-game-themes.css'
import '@/styles/dp-lobby-shell.css'
import dpLobbyThemeMixin from '@/mixins/dpLobbyThemeMixin'
import { ensureDpUserIdInStorage } from '@/utils/dpEnsureUserId'

export default {
  name: 'HandHistory',
  mixins: [dpLobbyThemeMixin],
  props: {
    /** 嵌入对局弹层：不整页跳转，通过事件关闭 / 打开详情 */
    embedded: { type: Boolean, default: false },
    /** mine：`/dpHandHistory/list`；withOpponent：`/dpHandHistory/checkUserAndOtherPlayerHandHistoryList` */
    listMode: {
      type: String,
      default: 'mine',
      validator(v) {
        return v === 'mine' || v === 'withOpponent'
      }
    },
    /** `listMode===withOpponent` 时为对方 dp_user.id */
    otherUserId: { type: Number, default: null }
  },
  data() {
    return {
      user: null,
      rows: [],
      total: 0,
      page: 1,
      pageSize: 10,
      loading: false,
      loadError: ''
    }
  },
  computed: {
    totalPages() {
      if (this.total <= 0) return 1
      return Math.max(1, Math.ceil(this.total / this.pageSize))
    },
    showPageTitle() {
      if (this.embedded && this.listMode === 'withOpponent') return false
      return true
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
        this.$emit('close')
      } else {
        this.$router.replace('/login')
      }
      return
    }
    this.user = (await ensureDpUserIdInStorage(this.$http)) || this.user
    var uid = Number(this.user && this.user.userId)
    if (!this.user || isNaN(uid) || uid <= 0) {
      if (this.embedded) {
        this.$emit('close')
      } else {
        this.$router.replace('/login')
      }
      return
    }
    this.user.userId = uid
    if (this.listMode === 'withOpponent') {
      var oid = Number(this.otherUserId)
      if (!oid || oid <= 0 || isNaN(oid)) {
        if (this.embedded) this.$emit('close')
        else this.$router.replace('/login')
        return
      }
    }
    this.fetchList(1)
  },
  methods: {
    goPage(p) {
      if (p < 1 || p > this.totalPages) return
      this.fetchList(p)
    },
    goBack() {
      if (this.embedded) {
        this.$emit('close')
      } else {
        this.$router.push('/home')
      }
    },
    goDetail(handHistoryId) {
      if (handHistoryId == null || handHistoryId === '') return
      if (this.embedded) {
        this.$emit('view-detail', handHistoryId)
      } else {
        this.$router.push('/hand-history/detail/' + encodeURIComponent(String(handHistoryId)))
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
      if (n > 0) return 'hand-history-table__net--win'
      if (n < 0) return 'hand-history-table__net--lose'
      return ''
    },
    roleLabel(r) {
      var parts = []
      if (r.dealer) parts.push('发牌猫')
      if (r.blindPos === 1) parts.push('SC')
      else if (r.blindPos === 2) parts.push('BC')
      if (!parts.length) parts.push('—')
      return parts.join(' ')
    },
    async fetchList(page) {
      var p = typeof page === 'number' && page >= 1 ? page : this.page
      this.loading = true
      this.loadError = ''
      try {
        var params = {
          userId: Number(this.user.userId),
          page: p,
          pageSize: this.pageSize
        }
        var url = '/dpHandHistory/list'
        if (this.listMode === 'withOpponent') {
          url = '/dpHandHistory/checkUserAndOtherPlayerHandHistoryList'
          params.otherUserId = Number(this.otherUserId)
        }
        var res = await this.$http.get(url, { params: params })
        var data = res.data || {}
        this.page = typeof data.page === 'number' ? data.page : p
        this.pageSize = typeof data.pageSize === 'number' ? data.pageSize : this.pageSize
        this.total = typeof data.total === 'number' ? data.total : 0
        this.rows = Array.isArray(data.records) ? data.records : []
      } catch (e) {
        console.error('hand history list', e)
        this.loadError = '加载失败：请确认后端已启动且已建表 dp_observed_hand_participant。'
        this.rows = []
        this.total = 0
      } finally {
        this.loading = false
      }
    }
  }
}
</script>

<style scoped>
.hand-history-page {
  max-width: min(900px, 100%);
  width: 100%;
  margin: 0 auto;
  padding: clamp(16px, 4vw, 24px) clamp(12px, 3vw, 16px);
  box-sizing: border-box;
  min-width: 0;
}
.hand-history-page__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}
.hand-history-page__header-actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}
.hand-history-page__theme-row {
  justify-content: flex-end;
}
.hand-history-page__header h1 {
  margin: 0;
  font-size: 1.35rem;
}
.hand-history-page__back {
  padding: 8px 14px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  font-size: 14px;
}
.hand-history-page__back:hover {
  border-color: #409eff;
  color: #409eff;
}
.hand-history-page__user {
  font-size: 14px;
  color: #303133;
  margin-bottom: 12px;
}
.hand-history-page__status {
  color: #909399;
  margin: 12px 0;
}
.hand-history-page__error {
  color: #f56c6c;
  margin: 12px 0;
  line-height: 1.5;
}
.hand-history-page__empty {
  color: #606266;
  line-height: 1.6;
  margin: 16px 0;
  font-size: 14px;
}
.hand-history-page__empty code {
  font-size: 12px;
  background: #f4f4f5;
  padding: 0 4px;
  border-radius: 3px;
}
.hand-history-table-wrap {
  overflow-x: auto;
  overflow-y: visible;
  -webkit-overflow-scrolling: touch;
  overscroll-behavior-x: contain;
  margin-top: 12px;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  touch-action: pan-x pan-y;
}
.hand-history-table {
  width: 100%;
  min-width: 680px;
  border-collapse: collapse;
  font-size: 13px;
}
.hand-history-table th,
.hand-history-table td {
  border: 1px solid #ebeef5;
  padding: 8px 10px;
  text-align: left;
}
.hand-history-table th {
  background: #f5f7fa;
  color: #606266;
  font-weight: 600;
}
.hand-history-table__net--win {
  color: #67c23a;
  font-weight: 600;
}
.hand-history-table__net--lose {
  color: #f56c6c;
}
.hand-history-page__meta {
  font-size: 13px;
  color: #606266;
  margin: 0 0 8px;
}
.hand-history-page__pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 16px;
  flex-wrap: wrap;
}
.hand-history-page__pager-btn {
  padding: 8px 16px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  font-size: 14px;
}
.hand-history-page__pager-btn:hover:not(:disabled) {
  border-color: #409eff;
  color: #409eff;
}
.hand-history-page__pager-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.hand-history-page__pager-info {
  font-size: 14px;
  color: #606266;
}
.hand-history-table__detail-btn {
  padding: 4px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  font-size: 13px;
  color: #409eff;
}
.hand-history-table__detail-btn:hover {
  border-color: #409eff;
  background: #ecf5ff;
}

/* 对局弹层内：跟随 .dp-game-root 的 data-dp-game-theme / --dp-* */
.hand-history-page--dp {
  font-family: var(--dp-font-ui, inherit);
  color: var(--dp-text-primary, #303133);
}
.hand-history-page--dp .hand-history-page__header h1 {
  color: var(--dp-text-primary, #303133);
}
.hand-history-page--dp .hand-history-page__back {
  border-color: var(--dp-input-border, #dcdfe6);
  background: var(--dp-btn-ghost-bg, #fff);
  color: var(--dp-text-primary, #303133);
}
.hand-history-page--dp .hand-history-page__back:hover {
  border-color: var(--dp-accent, #409eff);
  color: var(--dp-accent, #409eff);
}
.hand-history-page--dp .hand-history-page__user {
  color: var(--dp-text-secondary, #606266);
}
.hand-history-page--dp .hand-history-page__status {
  color: var(--dp-text-muted, #909399);
}
.hand-history-page--dp .hand-history-page__error {
  color: var(--dp-danger, #f56c6c);
}
.hand-history-page--dp .hand-history-page__empty {
  color: var(--dp-text-secondary, #606266);
}
.hand-history-page--dp .hand-history-page__empty code {
  background: var(--dp-subpanel-bg, #f4f4f5);
  color: var(--dp-text-primary, #303133);
  border: 1px solid var(--dp-subpanel-border, transparent);
}
.hand-history-page--dp .hand-history-table th,
.hand-history-page--dp .hand-history-table td {
  border-color: var(--dp-subpanel-border, #ebeef5);
}
.hand-history-page--dp .hand-history-table th {
  background: var(--dp-subpanel-bg, #f5f7fa);
  color: var(--dp-text-secondary, #606266);
}
.hand-history-page--dp .hand-history-table__net--win {
  color: var(--dp-success, #67c23a);
}
.hand-history-page--dp .hand-history-table__net--lose {
  color: var(--dp-danger, #f56c6c);
}
.hand-history-page--dp .hand-history-page__meta {
  color: var(--dp-text-secondary, #606266);
}
.hand-history-page--dp .hand-history-page__pager-btn {
  border-color: var(--dp-input-border, #dcdfe6);
  background: var(--dp-btn-ghost-bg, #fff);
  color: var(--dp-text-primary, #303133);
}
.hand-history-page--dp .hand-history-page__pager-btn:hover:not(:disabled) {
  border-color: var(--dp-accent, #409eff);
  color: var(--dp-accent, #409eff);
}
.hand-history-page--dp .hand-history-page__pager-info {
  color: var(--dp-text-secondary, #606266);
}
.hand-history-page--dp .hand-history-table__detail-btn {
  border-color: var(--dp-input-border, #dcdfe6);
  background: var(--dp-subpanel-bg, #fff);
  color: var(--dp-accent, #409eff);
}
.hand-history-page--dp .hand-history-table__detail-btn:hover {
  border-color: var(--dp-accent, #409eff);
  background: var(--dp-subpanel-bg, #ecf5ff);
}
</style>
