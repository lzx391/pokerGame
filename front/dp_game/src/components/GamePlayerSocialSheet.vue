<template>
  <transition name="game-prof-fade">
    <div
      v-if="visible && target"
      class="game-prof-overlay"
      role="dialog"
      aria-modal="true"
      aria-label="玩家信息"
      @click.self="$emit('close')"
    >
      <div
        class="game-prof-card"
        :class="{ 'game-prof-card--effects-off': shouldSkipEffects() }"
        @click.stop
      >
        <div
          v-if="showBackdrop"
          class="game-prof-backdrop"
          aria-hidden="true"
        >
          <img
            class="game-prof-backdrop__img"
            :src="backdropSrc"
            alt=""
            decoding="async"
          >
          <div class="game-prof-backdrop__scrim"></div>
        </div>

        <div class="game-prof-title-bar">
          <div class="game-prof-title-bar__deco">
            <span class="game-prof-suit game-prof-suit--spade" aria-hidden="true">♠</span>
            <span class="game-prof-suit game-prof-suit--heart" aria-hidden="true">♥</span>
            <span class="game-prof-title-bar__text">玩家资料</span>
            <span class="game-prof-suit game-prof-suit--diamond" aria-hidden="true">♦</span>
            <span class="game-prof-suit game-prof-suit--club" aria-hidden="true">♣</span>
          </div>
          <button type="button" class="game-prof-title-bar__close" @click="$emit('close')" aria-label="关闭">
            <i class="el-icon-close"></i>
          </button>
        </div>

        <div class="game-prof-card__inner">
          <div class="game-prof-identity">
            <div class="game-prof-avatar-frame">
              <div class="game-prof-avatar-frame__bezel">
                <dp-user-avatar
                  size="lg"
                  :nickname="displayName"
                  :avatar-url="honorAvatarUrl"
                  :cache-bust="honorAvatarCacheBust"
                />
              </div>
              <span class="game-prof-avatar-frame__corner game-prof-avatar-frame__corner--tl" aria-hidden="true"></span>
              <span class="game-prof-avatar-frame__corner game-prof-avatar-frame__corner--tr" aria-hidden="true"></span>
              <span class="game-prof-avatar-frame__corner game-prof-avatar-frame__corner--bl" aria-hidden="true"></span>
              <span class="game-prof-avatar-frame__corner game-prof-avatar-frame__corner--br" aria-hidden="true"></span>
            </div>
            <div class="game-prof-identity__meta">
              <h2 class="game-prof-name">{{ displayName }}</h2>
              <button
                v-if="target && target.userId != null && target.userId !== ''"
                type="button"
                class="game-prof-id"
                title="点击复制用户 ID"
                @click="onCopyUserId"
              >
                ID: {{ target.userId }}
              </button>
            </div>
          </div>

          <div v-if="hasWeeklyRank" class="game-prof-weekly">
            <div class="game-prof-weekly__title">
              <span class="game-prof-section-deco" aria-hidden="true">♠</span>
              本周排名
              <span class="game-prof-section-deco" aria-hidden="true">♠</span>
            </div>
            <div class="game-prof-weekly__cards">
              <div class="game-prof-weekly-card game-prof-weekly-card--hand">
                <span class="game-prof-weekly-card__rank">{{ rankHandDisplay }}</span>
                <span class="game-prof-weekly-card__label">单局之最</span>
                <span
                  v-if="honor.leaderboardWeeklyHand && honor.leaderboardWeeklyHand.multiplier != null"
                  class="game-prof-weekly-card__mult"
                >{{ formatMulti(honor.leaderboardWeeklyHand.multiplier) }}x</span>
              </div>
              <div class="game-prof-weekly-card game-prof-weekly-card--room">
                <span class="game-prof-weekly-card__rank">{{ rankRoomDisplay }}</span>
                <span class="game-prof-weekly-card__label">单房之最</span>
                <span
                  v-if="honor.leaderboardWeeklyRoom && honor.leaderboardWeeklyRoom.multiplier != null"
                  class="game-prof-weekly-card__mult"
                >{{ formatMulti(honor.leaderboardWeeklyRoom.multiplier) }}x</span>
              </div>
            </div>
          </div>

          <div
            v-if="honor && honor.totalHandsPlayed != null"
            class="game-prof-honor"
            :aria-busy="honorGlitchPhase !== 'revealed'"
          >
            <div class="game-prof-honor__title">
              <span class="game-prof-section-deco" aria-hidden="true">♦</span>
              生涯荣誉
              <span class="game-prof-section-deco" aria-hidden="true">♦</span>
            </div>
            <div class="game-prof-honor__medals">
              <div class="game-prof-medal game-prof-medal--royal">
                <div class="game-prof-medal__body">
                  <span class="game-prof-medal__name">皇家同花顺</span>
                  <span
                    class="game-prof-honor-val"
                    :class="honorValClass"
                    :aria-label="'皇家同花顺 ' + honorDisplayCount('royalFlushWins') + ' 次'"
                  >{{ honorDisplayCount('royalFlushWins') }}<small> 次</small></span>
                </div>
              </div>
              <div class="game-prof-medal game-prof-medal--straight">
                <div class="game-prof-medal__body">
                  <span class="game-prof-medal__name">同花顺</span>
                  <span
                    class="game-prof-honor-val"
                    :class="honorValClass"
                    :aria-label="'同花顺 ' + honorDisplayCount('straightFlushWins') + ' 次'"
                  >{{ honorDisplayCount('straightFlushWins') }}<small> 次</small></span>
                </div>
              </div>
              <div class="game-prof-medal game-prof-medal--four">
                <div class="game-prof-medal__body">
                  <span class="game-prof-medal__name">四条</span>
                  <span
                    class="game-prof-honor-val"
                    :class="honorValClass"
                    :aria-label="'四条 ' + honorDisplayCount('fourOfAKindWins') + ' 次'"
                  >{{ honorDisplayCount('fourOfAKindWins') }}<small> 次</small></span>
                </div>
              </div>
            </div>
            <div class="game-prof-honor__stats">
              <div class="game-prof-stat-card">
                <span class="game-prof-stat-card__label">最高净赢倍数</span>
                <span
                  class="game-prof-honor-val game-prof-stat-card__value"
                  :class="honorValClass"
                  :aria-label="'最高净赢倍数 ' + honorDisplayStat('largestPotWon')"
                >{{ honorDisplayStat('largestPotWon') }}</span>
              </div>
              <div class="game-prof-stat-card">
                <span class="game-prof-stat-card__label">单房最高净赢</span>
                <span
                  class="game-prof-honor-val game-prof-stat-card__value"
                  :class="honorValClass"
                  :aria-label="'单房最高净赢 ' + honorDisplayStat('largestRoomNet')"
                >{{ honorDisplayStat('largestRoomNet') }}</span>
              </div>
              <div class="game-prof-stat-card">
                <span class="game-prof-stat-card__label">生涯总局数</span>
                <span
                  class="game-prof-honor-val game-prof-stat-card__value"
                  :class="honorValClass"
                  :aria-label="'生涯总局数 ' + honorDisplayCount('totalHandsPlayed')"
                >{{ honorDisplayCount('totalHandsPlayed') }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="game-prof-footer">
          <button
            type="button"
            class="game-prof-btn game-prof-btn--ghost"
            @click="$emit('close')"
          >
            关闭
          </button>
          <button
            type="button"
            class="game-prof-btn game-prof-btn--outline"
            @click="onViewHandHistoryWithOpponent"
          >
            历史对局
          </button>
          <div v-if="socialPrimaryIsStaticHint" class="game-prof-footer__hint">
            {{ primaryLabel }}
          </div>
          <button
            v-else
            type="button"
            class="game-prof-btn game-prof-btn--gold"
            :disabled="primaryDisabled || sending"
            @click="onSendRequest"
          >
            <span v-if="!sending">加好友</span>
            <span v-else><i class="el-icon-loading"></i> 发送中…</span>
          </button>
          <p v-if="tip" class="game-prof-footer__tip">{{ tip }}</p>
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
import { avatarCacheBustFromUpdatedAt, avatarFileSrc } from '@/utils/dpAvatarUrl'
import { copySocialId as copySocialIdToClipboard } from '@/utils/dpCopySocialId'

var HONOR_GLITCH_CHARS = '0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ!@#$%&*'
var HONOR_SCRAMBLE_MS = 320
var HONOR_SCRAMBLE_TICK_MS = 48

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
      lookupAddStatus: '',
      honorGlitchPhase: 'idle',
      honorGlitchTick: 0,
      honorGlitchTimer: null,
      honorRevealTimer: null
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
    showBackdrop() {
      if (this.shouldSkipEffects()) return false
      return !!this.backdropSrc
    },
    backdropSrc() {
      if (!this.honorAvatarUrl) return ''
      return avatarFileSrc(this.honorAvatarUrl, this.honorAvatarCacheBust, { variant: 'full' })
    },
    honorValClass() {
      return {
        'game-prof-honor-val--reveal': this.honorGlitchPhase === 'revealed' && !this.shouldSkipEffects()
      }
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
        if (v && this.target) {
          this.refresh()
        } else {
          this.stopHonorGlitch()
        }
      }
    },
    target() {
      if (this.visible && this.target) this.refresh()
    }
  },
  beforeDestroy() {
    this.stopHonorGlitch()
  },
  methods: {
    formatNetWinMultiplier,
    formatRoomNetMultiplier,
    formatMulti(v) {
      if (v == null || isNaN(Number(v))) return '—'
      return Number(v).toFixed(1)
    },
    shouldSkipEffects() {
      if (typeof document !== 'undefined' && document.body.getAttribute('data-dp-fluidity') === 'eco') {
        return true
      }
      if (
        typeof window !== 'undefined' &&
        window.matchMedia &&
        window.matchMedia('(prefers-reduced-motion: reduce)').matches
      ) {
        return true
      }
      return false
    },
    stopHonorGlitch() {
      if (this.honorGlitchTimer != null) {
        clearInterval(this.honorGlitchTimer)
        this.honorGlitchTimer = null
      }
      if (this.honorRevealTimer != null) {
        clearTimeout(this.honorRevealTimer)
        this.honorRevealTimer = null
      }
    },
    startHonorGlitch() {
      this.stopHonorGlitch()
      if (!this.honor || this.honor.totalHandsPlayed == null) {
        this.honorGlitchPhase = 'idle'
        return
      }
      if (this.shouldSkipEffects()) {
        this.honorGlitchPhase = 'revealed'
        return
      }
      var self = this
      this.honorGlitchPhase = 'scrambling'
      this.honorGlitchTick = 0
      this.honorGlitchTimer = setInterval(function () {
        self.honorGlitchTick++
      }, HONOR_SCRAMBLE_TICK_MS)
      this.honorRevealTimer = setTimeout(function () {
        if (self.honorGlitchTimer != null) {
          clearInterval(self.honorGlitchTimer)
          self.honorGlitchTimer = null
        }
        self.honorGlitchPhase = 'revealed'
      }, HONOR_SCRAMBLE_MS)
    },
    randomGlitchString(len) {
      var s = ''
      var n = len > 0 ? len : 3
      for (var i = 0; i < n; i++) {
        s += HONOR_GLITCH_CHARS.charAt(Math.floor(Math.random() * HONOR_GLITCH_CHARS.length))
      }
      return s
    },
    honorResolvedCount(key) {
      if (!this.honor) return '0'
      var v = this.honor[key]
      return v != null ? String(v) : '0'
    },
    honorResolvedStat(key) {
      if (!this.honor) return '—'
      if (key === 'largestPotWon') {
        return formatNetWinMultiplier(this.honor.largestPotWon)
      }
      if (key === 'largestRoomNet') {
        return formatRoomNetMultiplier(this.honor.largestRoomNet)
      }
      return this.honorResolvedCount(key)
    },
    honorDisplayCount(key) {
      var resolved = this.honorResolvedCount(key)
      if (this.honorGlitchPhase === 'revealed' || this.shouldSkipEffects()) {
        return resolved
      }
      if (this.honorGlitchPhase === 'scrambling') {
        void this.honorGlitchTick
        return this.randomGlitchString(resolved.length || 2)
      }
      return '···'
    },
    honorDisplayStat(key) {
      var resolved = this.honorResolvedStat(key)
      if (this.honorGlitchPhase === 'revealed' || this.shouldSkipEffects()) {
        return resolved
      }
      if (this.honorGlitchPhase === 'scrambling') {
        void this.honorGlitchTick
        return this.randomGlitchString(Math.max(resolved.length, 3))
      }
      return '···'
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
      this.honorGlitchPhase = 'idle'
      this.stopHonorGlitch()
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
      } finally {
        var self = this
        this.$nextTick(function () {
          self.startHonorGlitch()
        })
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
   对局玩家资料卡 — 对齐大厅 home-prof 布局
   ============================================ */

.game-prof-overlay {
  position: fixed;
  inset: 0;
  z-index: 9000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.55);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  overflow-y: auto;
  padding: 16px;
}

.game-prof-fade-enter-active {
  animation: game-prof-overlay-in 0.32s ease-out both;
}
.game-prof-fade-leave-active {
  animation: game-prof-overlay-out 0.2s ease-in both;
}
.game-prof-fade-enter-active .game-prof-card {
  animation: game-prof-card-in 0.38s cubic-bezier(0.22, 1, 0.36, 1) both;
}
@keyframes game-prof-overlay-in {
  from { opacity: 0; }
  to { opacity: 1; }
}
@keyframes game-prof-overlay-out {
  from { opacity: 1; }
  to { opacity: 0; }
}
@keyframes game-prof-card-in {
  from { opacity: 0; transform: scale(0.94) translateY(12px); }
  to { opacity: 1; transform: none; }
}

.game-prof-card {
  --game-prof-glitch-duration: 800ms;
  --game-prof-avatar-desktop: clamp(88px, 22vw, 120px);
  position: relative;
  z-index: 1;
  width: min(94vw, 520px);
  max-width: calc(100vw - 16px);
  overflow: hidden;
  border-radius: 16px;
  background: var(--dp-panel-bg);
  box-shadow:
    0 24px 64px rgba(0, 0, 0, 0.28),
    0 0 0 1px color-mix(in srgb, var(--dp-subpanel-border) 60%, transparent);
  display: flex;
  flex-direction: column;
}

.game-prof-backdrop {
  display: none;
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  overflow: hidden;
}
.game-prof-backdrop__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  filter: saturate(1.08);
  transform: scale(1.08);
}
.game-prof-backdrop__scrim {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    160deg,
    color-mix(in srgb, var(--dp-panel-bg) 50%, transparent),
    color-mix(in srgb, var(--dp-panel-bg) 80%, #000 16%)
  );
}

.game-prof-title-bar {
  position: relative;
  z-index: 2;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 16px 8px;
  flex-shrink: 0;
}
.game-prof-title-bar__deco {
  display: flex;
  align-items: center;
  gap: 8px;
}
.game-prof-title-bar__text {
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--dp-text-primary);
}
.game-prof-suit {
  font-size: 13px;
  line-height: 1;
}
.game-prof-suit--spade,
.game-prof-suit--club { color: var(--dp-text-primary); }
.game-prof-suit--heart,
.game-prof-suit--diamond { color: var(--dp-danger); }
.game-prof-title-bar__close {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: 1px solid var(--dp-input-border);
  background: color-mix(in srgb, var(--dp-panel-bg) 85%, transparent);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: var(--dp-text-muted);
  transition: all 0.2s;
  flex-shrink: 0;
}
.game-prof-title-bar__close:hover {
  background: color-mix(in srgb, var(--dp-danger) 12%, var(--dp-panel-bg));
  border-color: var(--dp-danger);
  color: var(--dp-danger);
}

.game-prof-card__inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 4px 14px 8px;
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

.game-prof-identity {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  text-align: center;
}

.game-prof-avatar-frame {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8px;
  border-radius: 12px;
  background: linear-gradient(
    145deg,
    color-mix(in srgb, var(--dp-text-muted) 30%, var(--dp-panel-bg)),
    color-mix(in srgb, var(--dp-text-muted) 50%, var(--dp-panel-bg)) 30%,
    color-mix(in srgb, var(--dp-text-muted) 40%, var(--dp-panel-bg)) 55%,
    color-mix(in srgb, var(--dp-text-muted) 15%, var(--dp-panel-bg)) 85%
  );
  box-shadow:
    0 4px 16px rgba(0, 0, 0, 0.12),
    0 0 0 1px rgba(0, 0, 0, 0.06),
    inset 0 1px 0 rgba(255, 255, 255, 0.4);
}
.game-prof-avatar-frame__bezel {
  position: relative;
  z-index: 2;
  border-radius: 10px;
  overflow: hidden;
  box-shadow:
    inset 0 2px 6px rgba(0, 0, 0, 0.15),
    0 0 0 2px rgba(0, 0, 0, 0.08);
}
.game-prof-avatar-frame__corner {
  position: absolute;
  z-index: 3;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--dp-text-muted) 60%, var(--dp-panel-bg));
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.2),
    inset 0 1px 0 rgba(255, 255, 255, 0.5);
}
.game-prof-avatar-frame__corner--tl { top: 6px; left: 6px; }
.game-prof-avatar-frame__corner--tr { top: 6px; right: 6px; }
.game-prof-avatar-frame__corner--bl { bottom: 6px; left: 6px; }
.game-prof-avatar-frame__corner--br { bottom: 6px; right: 6px; }

.game-prof-identity__meta {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  width: 100%;
}
.game-prof-name {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--dp-text-primary);
  letter-spacing: 0.03em;
  line-height: 1.25;
}
.game-prof-id {
  margin: 0;
  padding: 3px 12px;
  font-size: 12px;
  font-family: 'Courier New', monospace;
  color: var(--dp-text-muted);
  background: color-mix(in srgb, var(--dp-subpanel-bg) 88%, transparent);
  border: none;
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.2s;
}
.game-prof-id:hover {
  color: var(--dp-accent);
  background: color-mix(in srgb, var(--dp-accent) 10%, var(--dp-subpanel-bg));
}

.game-prof-section-deco {
  font-size: 12px;
  opacity: 0.5;
}

/* ---- 本周排名 ---- */
.game-prof-weekly {
  background: color-mix(in srgb, var(--dp-subpanel-bg) 72%, transparent);
  border-radius: 14px;
  padding: 12px 10px;
  border: 1px solid color-mix(in srgb, var(--dp-subpanel-border) 80%, transparent);
}
.game-prof-weekly__title {
  text-align: center;
  font-size: 13px;
  font-weight: 700;
  color: var(--dp-text-primary);
  margin-bottom: 10px;
  letter-spacing: 0.06em;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.game-prof-weekly__cards {
  display: flex;
  gap: 8px;
}
.game-prof-weekly-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 10px 8px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--dp-panel-bg) 88%, transparent);
  border: 1px solid color-mix(in srgb, var(--dp-subpanel-border) 75%, transparent);
}
.game-prof-weekly-card--hand {
  border-color: color-mix(in srgb, var(--dp-accent) 45%, transparent);
}
.game-prof-weekly-card--room {
  border-color: color-mix(in srgb, var(--dp-success) 45%, transparent);
}
.game-prof-weekly-card__rank {
  font-size: 26px;
  font-weight: 800;
  line-height: 1;
  color: var(--dp-text-primary);
  font-variant-numeric: tabular-nums;
}
.game-prof-weekly-card--hand .game-prof-weekly-card__rank {
  color: var(--dp-accent);
}
.game-prof-weekly-card--room .game-prof-weekly-card__rank {
  color: var(--dp-success);
}
.game-prof-weekly-card__label {
  font-size: 10px;
  color: var(--dp-text-muted);
}
.game-prof-weekly-card__mult {
  font-size: 12px;
  font-weight: 600;
  color: var(--dp-text-secondary);
}

/* ---- 荣誉墙（对齐大厅） ---- */
.game-prof-honor {
  background: color-mix(in srgb, var(--dp-subpanel-bg) 72%, transparent);
  border-radius: 14px;
  padding: 14px 12px;
  border: 1px solid color-mix(in srgb, var(--dp-subpanel-border) 80%, transparent);
  position: relative;
  overflow: hidden;
}
.game-prof-honor__title {
  text-align: center;
  font-size: 14px;
  font-weight: 700;
  color: var(--dp-text-primary);
  margin-bottom: 12px;
  letter-spacing: 0.06em;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.game-prof-honor__medals {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  scrollbar-width: thin;
}
.game-prof-honor__stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.game-prof-medal {
  flex: 1 1 0;
  min-width: 88px;
  min-height: 96px;
  border-radius: 12px;
  padding: 10px 6px;
  text-align: center;
  border: 1.5px solid;
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}
.game-prof-medal::before {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: linear-gradient(
    to top,
    rgba(0, 0, 0, 0.78) 0%,
    rgba(0, 0, 0, 0.48) 42%,
    rgba(0, 0, 0, 0.12) 100%
  );
  pointer-events: none;
}
.game-prof-medal--royal {
  border-color: color-mix(in srgb, var(--dp-warning) 65%, transparent);
  background-image: url('/RF.png');
}
.game-prof-medal--straight {
  border-color: color-mix(in srgb, var(--dp-accent) 65%, transparent);
  background-image: url('/SF.png');
}
.game-prof-medal--four {
  border-color: color-mix(in srgb, var(--dp-danger) 65%, transparent);
  background-image: url('/4K.png');
}
.game-prof-medal__body {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.game-prof-medal__name {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.82);
}
.game-prof-medal .game-prof-honor-val {
  color: #fff;
}
.game-prof-medal .game-prof-honor-val small {
  color: rgba(255, 255, 255, 0.72);
}

.game-prof-honor-val {
  font-size: 20px;
  font-weight: 800;
  color: var(--dp-text-primary);
  line-height: 1;
  font-variant-numeric: tabular-nums;
}
.game-prof-honor-val small {
  font-size: 11px;
  font-weight: 500;
  color: var(--dp-text-muted);
}

@keyframes game-prof-honor-reveal {
  0% {
    opacity: 0.35;
    filter: brightness(2);
    transform: scale(1.06);
  }
  40% {
    opacity: 1;
    filter: brightness(1.35);
  }
  100% {
    opacity: 1;
    filter: none;
    transform: none;
  }
}
.game-prof-honor-val--reveal {
  animation: game-prof-honor-reveal var(--game-prof-glitch-duration) cubic-bezier(0.22, 1, 0.36, 1) both;
}

.game-prof-stat-card {
  background: color-mix(in srgb, var(--dp-panel-bg) 88%, transparent);
  border-radius: 10px;
  padding: 8px 6px;
  text-align: center;
  border: 1px solid color-mix(in srgb, var(--dp-subpanel-border) 75%, transparent);
}
.game-prof-stat-card__label {
  display: block;
  font-size: 10px;
  color: var(--dp-text-muted);
  margin-bottom: 4px;
  line-height: 1.2;
}
.game-prof-stat-card__value {
  display: block;
  font-size: 16px;
}

/* ---- Footer 操作 ---- */
.game-prof-footer {
  position: relative;
  z-index: 2;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  padding: 8px 14px 14px;
  flex-shrink: 0;
}
.game-prof-footer__hint {
  flex: 1 1 100%;
  text-align: center;
  font-size: 13px;
  font-weight: 600;
  color: var(--dp-text-secondary);
  padding: 8px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--dp-subpanel-bg) 80%, transparent);
}
.game-prof-footer__tip {
  flex: 1 1 100%;
  margin: 0;
  font-size: 12px;
  color: var(--dp-text-muted);
  text-align: center;
  line-height: 1.4;
}

.game-prof-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: none;
  border-radius: 8px;
  font-family: inherit;
  cursor: pointer;
  font-size: 14px;
  padding: 9px 18px;
  font-weight: 600;
  transition: all 0.2s cubic-bezier(0.25, 0.46, 0.45, 0.94);
}
.game-prof-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.game-prof-btn--ghost {
  background: color-mix(in srgb, var(--dp-subpanel-bg) 88%, transparent);
  color: var(--dp-text-secondary);
}
.game-prof-btn--ghost:hover:not(:disabled) {
  background: var(--dp-input-border);
}
.game-prof-btn--outline {
  background: transparent;
  border: 1.5px solid var(--dp-input-border);
  color: var(--dp-text-secondary);
}
.game-prof-btn--outline:hover:not(:disabled) {
  border-color: var(--dp-accent);
  color: var(--dp-accent);
}
.game-prof-btn--gold {
  background: linear-gradient(135deg, var(--dp-warning), color-mix(in srgb, var(--dp-warning) 70%, #000));
  color: #fff;
  box-shadow: 0 3px 12px color-mix(in srgb, var(--dp-warning) 35%, transparent);
}
.game-prof-btn--gold:hover:not(:disabled) {
  box-shadow: 0 5px 18px color-mix(in srgb, var(--dp-warning) 50%, transparent);
  transform: translateY(-1px);
}

/* ---- 桌面方卡 ≥641px ---- */
@media (min-width: 641px) {
  .game-prof-card:not(.game-prof-card--effects-off) .game-prof-backdrop {
    display: block;
  }

  .game-prof-card {
    aspect-ratio: 1 / 1;
    max-height: min(92vw, 520px);
  }

  .game-prof-identity {
    flex-direction: row;
    align-items: flex-start;
    text-align: left;
    gap: 14px;
  }

  .game-prof-identity__meta {
    align-items: flex-start;
    flex: 1;
    min-width: 0;
    padding-top: 4px;
  }

  .game-prof-avatar-frame {
    flex-shrink: 0;
    width: var(--game-prof-avatar-desktop);
    height: var(--game-prof-avatar-desktop);
    padding: 6px;
    box-sizing: border-box;
  }

  .game-prof-avatar-frame__bezel {
    width: 100%;
    height: 100%;
  }

  .game-prof-avatar-frame__bezel >>> .dp-user-avatar--lg {
    width: 100% !important;
    height: 100% !important;
    min-width: 0;
    min-height: 0;
  }

  .game-prof-name {
    font-size: clamp(17px, 2.5vw, 20px);
  }
}

/* ---- 手机竖卡 ≤640px ---- */
@media (max-width: 640px) {
  .game-prof-card {
    width: min(94vw, 400px);
  }

  .game-prof-honor-val {
    font-size: 18px;
  }

  .game-prof-stat-card__value {
    font-size: 14px;
  }

  .game-prof-footer {
    flex-direction: column;
    align-items: stretch;
  }

  .game-prof-btn {
    width: 100%;
    justify-content: center;
  }
}
</style>

<style>
body[data-dp-fluidity='eco'] .game-prof-backdrop {
  display: none !important;
}
body[data-dp-fluidity='eco'] .game-prof-honor-val--reveal {
  animation: none !important;
  filter: none !important;
  transform: none !important;
}

@media (prefers-reduced-motion: reduce) {
  .game-prof-backdrop {
    display: none !important;
  }
  .game-prof-honor-val--reveal {
    animation: none !important;
    filter: none !important;
    transform: none !important;
  }
  .game-prof-fade-enter-active .game-prof-card {
    animation: none !important;
  }
}
</style>
