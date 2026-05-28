<template>
  <!-- 全屏炸裂玩家档案 -->
  <transition name="prof-burst">
    <div
      v-if="visible && target"
      class="prof-player-overlay"
      role="dialog"
      aria-modal="true"
      aria-label="玩家信息"
      @click.self="$emit('close')"
    >
      <!-- 炸裂粒子 -->
      <span class="prof-particle prof-particle--1"></span>
      <span class="prof-particle prof-particle--2"></span>
      <span class="prof-particle prof-particle--3"></span>
      <span class="prof-particle prof-particle--4"></span>
      <span class="prof-particle prof-particle--5"></span>
      <span class="prof-particle prof-particle--6"></span>

      <!-- 主体卡片 -->
      <div class="prof-player-card" @click.stop>
        <!-- 关闭按钮 -->
        <button type="button" class="prof-player-close" @click="$emit('close')" aria-label="关闭">
          <i class="el-icon-close"></i>
        </button>

        <!-- 装饰底纹：巨型卡牌花色 -->
        <span class="prof-player-watermark" aria-hidden="true">♠</span>

        <!-- ===== 第一区：头像 + 身份 ===== -->
        <div class="prof-player-hero">
          <div class="prof-player-hero__avatar">
            <dp-user-avatar
              size="lg"
              :nickname="displayName"
              :avatar-url="honorAvatarUrl"
              :cache-bust="honorAvatarCacheBust"
            />
          </div>
          <div class="prof-player-hero__info">
            <div class="prof-player-hero__name">{{ displayName }}</div>
            <button
              v-if="target && target.userId != null && target.userId !== ''"
              type="button"
              class="prof-player-hero__id"
              title="点击复制用户 ID"
              @click="onCopyUserId"
            >
              #{{ target.userId }}
            </button>
            <div class="prof-player-hero__rank-tag">游戏玩家</div>
          </div>
        </div>

        <!-- ===== 第二区：周榜排名（大标） ===== -->
        <div v-if="hasWeeklyRank" class="prof-player-rank">
          <div class="prof-player-rank__header">
            <span class="prof-player-glory__deco">♠</span>
            本周排名
            <span class="prof-player-glory__deco">♠</span>
          </div>
          <div class="prof-player-rank__cards">
            <!-- 单局之最 -->
            <div class="prof-rank-card prof-rank-card--hand">
              <div class="prof-rank-card__badge">
                <span class="prof-rank-card__rank-num">{{ rankHandDisplay }}</span>
                <span class="prof-rank-card__rank-suffix">名</span>
              </div>
              <div class="prof-rank-card__info">
                <span class="prof-rank-card__type">单局之最</span>
                <span v-if="honor.leaderboardWeeklyHand && honor.leaderboardWeeklyHand.multiplier != null" class="prof-rank-card__mult">{{ formatMulti(honor.leaderboardWeeklyHand.multiplier) }}x</span>
              </div>
            </div>
            <!-- 单房之最 -->
            <div class="prof-rank-card prof-rank-card--room">
              <div class="prof-rank-card__badge">
                <span class="prof-rank-card__rank-num">{{ rankRoomDisplay }}</span>
                <span class="prof-rank-card__rank-suffix">名</span>
              </div>
              <div class="prof-rank-card__info">
                <span class="prof-rank-card__type">单房之最</span>
                <span v-if="honor.leaderboardWeeklyRoom && honor.leaderboardWeeklyRoom.multiplier != null" class="prof-rank-card__mult">{{ formatMulti(honor.leaderboardWeeklyRoom.multiplier) }}x</span>
              </div>
            </div>
          </div>
        </div>

        <!-- ===== 第三区：生涯荣誉勋章 ===== -->
        <div v-if="honor && honor.totalHandsPlayed != null" class="prof-player-glory">
          <div class="prof-player-glory__header">
            <span class="prof-player-glory__deco">♦</span>
            生涯荣誉
            <span class="prof-player-glory__deco">♦</span>
          </div>
          <div class="prof-player-glory__big-numbers">
            <!-- 皇家同花顺 -->
            <div class="prof-glory-item prof-glory-item--royal">
              <span class="prof-glory-item__number">{{ honor.royalFlushWins || 0 }}</span>
              <span class="prof-glory-item__unit">次</span>
              <span class="prof-glory-item__label">皇家同花顺</span>
              <span class="prof-glory-item__suit">♛</span>
            </div>
            <!-- 同花顺 -->
            <div class="prof-glory-item prof-glory-item--straight">
              <span class="prof-glory-item__number">{{ honor.straightFlushWins || 0 }}</span>
              <span class="prof-glory-item__unit">次</span>
              <span class="prof-glory-item__label">同花顺</span>
              <span class="prof-glory-item__suit">♠</span>
            </div>
            <!-- 四条 -->
            <div class="prof-glory-item prof-glory-item--four">
              <span class="prof-glory-item__number">{{ honor.fourOfAKindWins || 0 }}</span>
              <span class="prof-glory-item__unit">次</span>
              <span class="prof-glory-item__label">四条</span>
              <span class="prof-glory-item__suit">4</span>
            </div>
            <!-- 生涯总局数 -->
            <div class="prof-glory-item prof-glory-item--total">
              <span class="prof-glory-item__number">{{ honor.totalHandsPlayed || 0 }}</span>
              <span class="prof-glory-item__unit">局</span>
              <span class="prof-glory-item__label">生涯总局数</span>
              <span class="prof-glory-item__suit">♣</span>
            </div>
          </div>
          <!-- 辅助统计 -->
          <div class="prof-player-glory__extras">
            <div class="prof-glory-extra">
              <span class="prof-glory-extra__num">{{ formatNetWinMultiplier(honor.largestPotWon) }}</span>
              <span class="prof-glory-extra__desc">单局最高净赢</span>
            </div>
            <div class="prof-glory-extra">
              <span class="prof-glory-extra__num">{{ formatRoomNetMultiplier(honor.largestRoomNet) }}</span>
              <span class="prof-glory-extra__desc">单房最高净赢</span>
            </div>
          </div>
        </div>

        <!-- ===== 第三区：操作区 ===== -->
        <div class="prof-player-actions">
          <!-- 好友申请 -->
          <div v-if="socialPrimaryIsStaticHint" class="prof-player-actions__status">
            {{ primaryLabel }}
          </div>
          <button
            v-else
            type="button"
            class="prof-player-btn prof-player-btn--friend"
            :disabled="primaryDisabled"
            @click="onSendRequest"
          >
            <span v-if="!sending">发送好友申请</span>
            <span v-else><i class="el-icon-loading"></i> 发送中…</span>
          </button>

          <!-- 历史对局 -->
          <button
            type="button"
            class="prof-player-btn prof-player-btn--history"
            @click="onViewHandHistoryWithOpponent"
          >
            查看历史对局
          </button>

          <p v-if="tip" class="prof-player-actions__tip">{{ tip }}</p>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
import DpUserAvatar from '@/components/DpUserAvatar.vue'
import { mapState } from 'vuex'
import { dpDisplayNickname } from '../utils/dpDisplayNickname'
import { dpResultSuccess, dpResultData, dpResultMessage, dpAxiosErrorMessage } from '../utils/dpApiResult'
import { dpSocialApi } from '@/api/api.dpSocial'
import { formatNetWinMultiplier, formatRoomNetMultiplier } from '../utils/dpRoomNetMultiplier'
import { avatarCacheBustFromUpdatedAt } from '@/utils/dpAvatarUrl'
import { copySocialId as copySocialIdToClipboard } from '@/utils/dpCopySocialId'

export default {
  name: 'GamePlayerSocialSheet',
  components: { DpUserAvatar },
  props: {
    visible: { type: Boolean, default: false },
    target: {
      type: Object,
      default: null
    }
  },
  data() {
    return {
      sending: false,
      sentOk: false,
      tip: '',
      honor: null,
      lookupAddStatus: ''
    }
  },
  computed: {
    ...mapState('dpMailbox', ['friends']),
    displayName() {
      if (!this.target || !this.target.nickname) return ''
      return dpDisplayNickname(this.target)
    },
    honorAvatarUrl() {
      return this.honor && this.honor.avatarUrl ? this.honor.avatarUrl : ''
    },
    honorAvatarCacheBust() {
      return avatarCacheBustFromUpdatedAt(this.honor && this.honor.avatarUpdatedAt)
    },
    friendIds() {
      return (this.friends || []).map(function (f) {
        return Number(f.userId)
      }).filter(function (n) {
        return n > 0
      })
    },
    isFriend() {
      if (this.lookupAddStatus === 'ALREADY_FRIENDS') return true
      if (!this.target) return false
      return this.friendIds.indexOf(Number(this.target.userId)) !== -1
    },
    primaryLabel() {
      if (this.isFriend) return '已是好友'
      if (this.sentOk || this.lookupAddStatus === 'PENDING_OUTBOUND') return '申请已发送'
      return '发送好友申请'
    },
    socialPrimaryIsStaticHint() {
      return (
        this.isFriend ||
        this.sentOk ||
        this.lookupAddStatus === 'PENDING_OUTBOUND'
      )
    },
    primaryDisabled() {
      return this.socialPrimaryIsStaticHint || !!this.tip
    },
    hasWeeklyRank() {
      if (!this.honor) return false
      var h = this.honor.leaderboardWeeklyHand
      var r = this.honor.leaderboardWeeklyRoom
      return (h && h.rank != null) || (r && r.rank != null)
    },
    rankHandDisplay() {
      var r = this.honor && this.honor.leaderboardWeeklyHand && this.honor.leaderboardWeeklyHand.rank
      return r != null ? r : '—'
    },
    rankRoomDisplay() {
      var r = this.honor && this.honor.leaderboardWeeklyRoom && this.honor.leaderboardWeeklyRoom.rank
      return r != null ? r : '—'
    }
  },
  watch: {
    visible: {
      immediate: true,
      handler(v) {
        if (v && this.target) this.refresh()
      }
    },
    target() {
      if (this.visible && this.target) this.refresh()
    }
  },
  methods: {
    formatNetWinMultiplier,
    formatRoomNetMultiplier,
    formatMulti(v) {
      if (v == null || isNaN(Number(v))) return '—'
      return Number(v).toFixed(1)
    },
    onCopyUserId() {
      if (!this.target) return
      var self = this
      copySocialIdToClipboard(this.target.userId, {
        onSuccess: function () {
          if (self.$message) self.$message.success('已复制 ID')
        }
      })
    },
    refresh() {
      this.tip = ''
      this.sentOk = false
      this.lookupAddStatus = ''
      this.honor = null
      this.loadFriendAddStatus()
      this.loadHonor()
    },
    async loadHonor() {
      if (!this.target) return
      var uid = Number(this.target.userId)
      if (!uid || uid <= 0 || isNaN(uid)) return
      try {
        var res = await this.$http.get('/dpUser/stats/' + uid)
        if (dpResultSuccess(res.data)) {
          this.honor = (res.data.data && res.data.data.honor) || null
        }
      } catch (e) {
        // 静默
      }
    },
    async loadFriendAddStatus() {
      if (!this.target) return
      var uid = Number(this.target.userId)
      if (!uid || uid <= 0 || isNaN(uid)) return
      try {
        var res = await dpSocialApi(this.$http).lookupUser(String(uid))
        if (!dpResultSuccess(res.data)) return
        var d = dpResultData(res.data) || {}
        var status = d.addStatus != null ? String(d.addStatus) : ''
        this.lookupAddStatus = status
        if (status === 'PENDING_OUTBOUND') this.sentOk = true
        if (status === 'PENDING_INBOUND') {
          this.tip = '对方已向您发来申请，请在大厅邮箱中处理。'
        }
      } catch (e) {
        /* 静默 */
      }
    },
    onViewHandHistoryWithOpponent() {
      if (!this.target) return
      var uid = Number(this.target.userId)
      if (!uid || uid <= 0 || isNaN(uid)) return
      this.$emit('view-hand-history-with-opponent', {
        userId: uid,
        displayName: this.displayName || this.target.nickname
      })
    },
    async onSendRequest() {
      if (!this.target || this.primaryDisabled) return
      var uid = Number(this.target.userId)
      if (!uid || uid <= 0) return
      this.sending = true
      this.tip = ''
      try {
        var res = await this.$http.post('/dp/friends/requests', { toUserId: uid })
        if (dpResultSuccess(res.data)) {
          this.sentOk = true
          this.$message.success((res.data.data && res.data.data.message) || '已发送')
          return
        }
        var msg = dpResultMessage(res.data)
        if (msg.indexOf('对方已向您发来申请') !== -1) {
          this.tip = msg + '，请在大厅邮箱中处理。'
          return
        }
        if (msg.indexOf('已是好友') !== -1) {
          this.lookupAddStatus = 'ALREADY_FRIENDS'
          this.$message.info(msg)
          return
        }
        if (msg.indexOf('申请已存在') !== -1) {
          this.sentOk = true
          this.lookupAddStatus = 'PENDING_OUTBOUND'
          this.$message.success(msg)
          return
        }
        this.$message.error(msg)
      } catch (err) {
        this.$message.error(dpAxiosErrorMessage(err, '无法发送好友申请，请稍后重试'))
      } finally {
        this.sending = false
      }
    }
  }
}
</script>

<style scoped>
/* ============================================
   对局玩家档案 — 星辰炸裂
   ============================================ */

/* ---- 全屏遮罩 ---- */
.prof-player-overlay {
  position: fixed;
  inset: 0;
  z-index: 9000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0,0,0,0.55);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  overflow-y: auto;
  padding: 20px;
}

/* ======== 炸裂动画 ======== */
.prof-burst-enter-active {
  animation: prof-burst-in 0.45s cubic-bezier(0.34, 1.56, 0.64, 1) both;
}
.prof-burst-leave-active {
  animation: prof-burst-out 0.22s ease-in both;
}
.prof-burst-enter-active .prof-player-card {
  animation: prof-card-bounce 0.5s 0.05s cubic-bezier(0.34, 1.56, 0.64, 1) both;
}
.prof-burst-enter-active .prof-particle {
  animation: prof-particle-burst 0.6s cubic-bezier(0, 1, 0.5, 1) both;
}
.prof-burst-enter-active .prof-player-hero__avatar {
  animation: prof-slide-in-left 0.4s 0.12s cubic-bezier(0.34, 1.56, 0.64, 1) both;
}
.prof-burst-enter-active .prof-player-hero__info {
  animation: prof-fade-up 0.4s 0.18s ease-out both;
}
.prof-burst-enter-active .prof-player-glory {
  animation: prof-fade-up 0.4s 0.24s ease-out both;
}
.prof-burst-enter-active .prof-player-actions {
  animation: prof-fade-up 0.4s 0.30s ease-out both;
}

@keyframes prof-burst-in {
  from { opacity: 0; }
  to   { opacity: 1; }
}
@keyframes prof-burst-out {
  from { opacity: 1; }
  to   { opacity: 0; }
}
@keyframes prof-card-bounce {
  from { transform: scale(0.3) rotate(-8deg); opacity: 0; }
  60%  { transform: scale(1.04) rotate(0.5deg); opacity: 1; }
  to   { transform: scale(1) rotate(0deg); opacity: 1; }
}
@keyframes prof-slide-in-left {
  from { transform: translateX(-30px); opacity: 0; }
  to   { transform: translateX(0); opacity: 1; }
}
@keyframes prof-fade-up {
  from { transform: translateY(16px); opacity: 0; }
  to   { transform: translateY(0); opacity: 1; }
}

/* 粒子炸开 */
@keyframes prof-particle-burst {
  0%   { transform: translate(0, 0) scale(1); opacity: 0.8; }
  100% { opacity: 0; }
}
.prof-particle--1 { --px: -120px; --py: -80px;  animation-delay: 0s;    }
.prof-particle--2 { --px: 100px;  --py: -100px; animation-delay: 0.02s; }
.prof-particle--3 { --px: -90px;  --py: 90px;   animation-delay: 0.04s; }
.prof-particle--4 { --px: 110px;  --py: 70px;   animation-delay: 0.01s; }
.prof-particle--5 { --px: -140px; --py: 10px;   animation-delay: 0.03s; }
.prof-particle--6 { --px: 130px;  --py: -20px;  animation-delay: 0.05s; }

@keyframes prof-particle-burst {
  0%   { transform: translate(0, 0) scale(1); opacity: 0.8; }
  100% { transform: translate(var(--px), var(--py)) scale(0); opacity: 0; }
}

/* ======== 粒子 ======== */
.prof-particle {
  position: absolute;
  top: 50%; left: 50%;
  width: 12px; height: 12px;
  margin-left: -6px; margin-top: -6px;
  border-radius: 3px;
  background: var(--dp-warning, #c8963e);
  pointer-events: none;
  z-index: 0;
  transform: scale(0);
}
.prof-particle--2 { background: var(--dp-accent, #1890ff); width: 8px; height: 8px; border-radius: 50%; }
.prof-particle--3 { background: var(--dp-danger, #ff4d4f); width: 14px; height: 14px; border-radius: 2px; }
.prof-particle--4 { background: var(--dp-success, #52c41a); width: 6px; height: 6px; border-radius: 50%; }
.prof-particle--5 { background: var(--dp-warning, #c8963e); width: 10px; height: 10px; border-radius: 50%; }
.prof-particle--6 { background: var(--dp-accent, #1890ff); width: 7px; height: 7px; border-radius: 2px; }

/* ======== 主卡片 ======== */
.prof-player-card {
  position: relative;
  z-index: 2;
  width: min(94vw, 460px);
  background:
    radial-gradient(ellipse 80% 50% at 50% 0%, rgba(255,255,255,0.08) 0%, transparent 60%),
    var(--dp-panel-bg);
  border-radius: 24px;
  padding: 32px 24px 24px;
  box-shadow:
    0 24px 64px rgba(0,0,0,0.25),
    0 0 0 1px rgba(255,255,255,0.1);
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* ---- 关掉 ---- */
.prof-player-close {
  position: absolute;
  top: 14px; right: 14px;
  z-index: 10;
  width: 32px; height: 32px;
  border-radius: 50%;
  border: none;
  background: rgba(0,0,0,0.06);
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
  font-size: 16px;
  color: var(--dp-text-muted);
  transition: all 0.2s;
}
.prof-player-close:hover {
  background: rgba(0,0,0,0.12);
  color: var(--dp-danger);
}

/* ---- 巨型水印花色 ---- */
.prof-player-watermark {
  position: absolute;
  top: 50%; right: -30px;
  transform: translateY(-50%);
  font-size: 200px;
  line-height: 1;
  color: var(--dp-subpanel-bg);
  pointer-events: none;
  z-index: 0;
  opacity: 0.6;
}

/* ======== Hero ======== */
.prof-player-hero {
  display: flex;
  align-items: center;
  gap: 16px;
  position: relative;
  z-index: 1;
}
.prof-player-hero__info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.prof-player-hero__name {
  font-size: 22px;
  font-weight: 800;
  color: var(--dp-text-primary);
  letter-spacing: 0.03em;
  line-height: 1.2;
}
.prof-player-hero__id {
  display: inline-block;
  width: fit-content;
  padding: 2px 10px;
  font-size: 12px;
  font-family: 'Courier New', monospace;
  font-weight: 600;
  color: var(--dp-text-muted);
  background: var(--dp-subpanel-bg);
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.prof-player-hero__id:hover {
  color: var(--dp-accent);
  background: color-mix(in srgb, var(--dp-accent) 10%, var(--dp-subpanel-bg));
}
.prof-player-hero__rank-tag {
  font-size: 12px;
  font-weight: 600;
  color: var(--dp-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

/* ======== 周榜排名区 ======== */
.prof-player-rank {
  position: relative;
  z-index: 1;
  padding: 16px;
  border-radius: 18px;
  background: linear-gradient(155deg, var(--dp-subpanel-bg), color-mix(in srgb, var(--dp-subpanel-bg) 85%, var(--dp-panel-bg)));
  border: 1px solid var(--dp-subpanel-border);
}
.prof-player-rank__header {
  text-align: center;
  font-size: 13px;
  font-weight: 700;
  color: var(--dp-text-secondary);
  letter-spacing: 0.08em;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.prof-player-rank__cards {
  display: flex;
  gap: 10px;
}
.prof-rank-card {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 14px;
  border-radius: 16px;
  background: var(--dp-panel-bg);
  border: 1px solid var(--dp-subpanel-border);
  transition: transform 0.2s ease;
}
.prof-rank-card:hover { transform: translateY(-2px); }

.prof-rank-card__badge {
  width: 64px; height: 64px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  position: relative;
}
.prof-rank-card--hand .prof-rank-card__badge {
  background: radial-gradient(circle at 35% 28%, rgba(255,255,255,0.45), transparent 50%),
    conic-gradient(
      color-mix(in srgb, var(--dp-accent) 70%, #fff),
      var(--dp-accent),
      color-mix(in srgb, var(--dp-accent) 70%, #000),
      var(--dp-accent),
      color-mix(in srgb, var(--dp-accent) 70%, #fff)
    );
  box-shadow: 0 0 16px color-mix(in srgb, var(--dp-accent) 25%, transparent);
}
.prof-rank-card--room .prof-rank-card__badge {
  background: radial-gradient(circle at 35% 28%, rgba(255,255,255,0.45), transparent 50%),
    conic-gradient(
      color-mix(in srgb, var(--dp-success) 70%, #fff),
      var(--dp-success),
      color-mix(in srgb, var(--dp-success) 70%, #000),
      var(--dp-success),
      color-mix(in srgb, var(--dp-success) 70%, #fff)
    );
  box-shadow: 0 0 16px color-mix(in srgb, var(--dp-success) 25%, transparent);
}

.prof-rank-card__rank-num {
  font-size: 26px;
  font-weight: 900;
  color: #fff;
  line-height: 1;
  text-shadow: 0 2px 4px rgba(0,0,0,0.25);
}
.prof-rank-card__rank-suffix {
  font-size: 11px;
  color: rgba(255,255,255,0.85);
  margin-top: 1px;
  font-weight: 600;
}
.prof-rank-card__info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.prof-rank-card__type {
  font-size: 14px;
  font-weight: 700;
  color: var(--dp-text-primary);
}
.prof-rank-card__mult {
  font-size: 13px;
  font-weight: 600;
  color: var(--dp-text-muted);
}

/* ======== 生涯荣誉区 ======== */
.prof-player-glory {
  position: relative;
  z-index: 1;
  padding: 18px 16px;
  border-radius: 18px;
  background: linear-gradient(155deg, var(--dp-subpanel-bg), color-mix(in srgb, var(--dp-subpanel-bg) 80%, var(--dp-panel-bg)));
  border: 1px solid var(--dp-subpanel-border);
}
.prof-player-glory__header {
  text-align: center;
  font-size: 13px;
  font-weight: 700;
  color: var(--dp-text-secondary);
  letter-spacing: 0.08em;
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.prof-player-glory__deco {
  font-size: 10px;
  opacity: 0.4;
}

/* ---- 大数字网格 ---- */
.prof-player-glory__big-numbers {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr 1fr;
  gap: 8px;
  margin-bottom: 14px;
}
@media (max-width: 400px) {
  .prof-player-glory__big-numbers {
    grid-template-columns: 1fr 1fr;
  }
}

.prof-glory-item {
  position: relative;
  text-align: center;
  padding: 10px 6px;
  border-radius: 14px;
  background: var(--dp-panel-bg);
  overflow: hidden;
  transition: transform 0.25s ease;
}
.prof-glory-item:hover {
  transform: translateY(-2px);
}
.prof-glory-item__number {
  display: block;
  font-size: 36px;
  font-weight: 900;
  line-height: 1;
  letter-spacing: -0.02em;
}
.prof-glory-item__unit {
  font-size: 13px;
  font-weight: 600;
  margin-left: 2px;
}
.prof-glory-item__label {
  display: block;
  font-size: 10px;
  color: var(--dp-text-muted);
  margin-top: 4px;
  letter-spacing: 0.04em;
}
.prof-glory-item__suit {
  position: absolute;
  bottom: -6px; right: -4px;
  font-size: 40px;
  line-height: 1;
  opacity: 0.06;
  pointer-events: none;
}

.prof-glory-item--royal .prof-glory-item__number { color: var(--dp-warning); }
.prof-glory-item--royal .prof-glory-item__unit  { color: var(--dp-warning); }
.prof-glory-item--straight .prof-glory-item__number { color: var(--dp-accent); }
.prof-glory-item--straight .prof-glory-item__unit  { color: var(--dp-accent); }
.prof-glory-item--four .prof-glory-item__number { color: var(--dp-danger); }
.prof-glory-item--four .prof-glory-item__unit  { color: var(--dp-danger); }
.prof-glory-item--total .prof-glory-item__number { color: var(--dp-text-primary); }
.prof-glory-item--total .prof-glory-item__unit  { color: var(--dp-text-primary); }

/* ---- 辅助统计 ---- */
.prof-player-glory__extras {
  display: flex;
  gap: 8px;
}
.prof-glory-extra {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 8px;
  border-radius: 10px;
  background: var(--dp-panel-bg);
}
.prof-glory-extra__num {
  font-size: 20px;
  font-weight: 800;
  color: var(--dp-text-primary);
  line-height: 1.1;
}
.prof-glory-extra__desc {
  font-size: 11px;
  color: var(--dp-text-muted);
}

/* ======== 操作区 ======== */
.prof-player-actions {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.prof-player-actions__status {
  padding: 12px;
  text-align: center;
  font-size: 14px;
  font-weight: 600;
  color: var(--dp-text-primary);
  background: var(--dp-subpanel-bg);
  border-radius: 12px;
  border: 1px solid var(--dp-subpanel-border);
}
.prof-player-actions__tip {
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--dp-text-muted);
  text-align: center;
  line-height: 1.4;
}

/* ---- 按钮 ---- */
.prof-player-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 13px 20px;
  font-size: 15px;
  font-weight: 700;
  font-family: inherit;
  border: none;
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.25, 0.46, 0.45, 0.94);
  position: relative;
  overflow: hidden;
}
.prof-player-btn::after {
  content: '';
  position: absolute;
  inset: 0;
  background: rgba(255,255,255,0.15);
  opacity: 0;
  transition: opacity 0.2s;
}
.prof-player-btn:hover::after { opacity: 1; }
.prof-player-btn:active { transform: scale(0.97); }
.prof-player-btn:disabled { opacity: 0.4; cursor: not-allowed; }

.prof-player-btn--friend {
  background: linear-gradient(135deg, var(--dp-warning), color-mix(in srgb, var(--dp-warning) 75%, #000));
  color: #fff;
  box-shadow: 0 4px 16px color-mix(in srgb, var(--dp-warning) 35%, transparent);
}
.prof-player-btn--history {
  background: transparent;
  color: var(--dp-text-secondary);
  border: 1.5px solid var(--dp-input-border);
  font-weight: 600;
}
.prof-player-btn--history:hover {
  border-color: var(--dp-accent);
  color: var(--dp-accent);
}
</style>
