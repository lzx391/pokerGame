<template>
  <div class="dp-top-bar">
    <!-- 第一行：房间/阶段 + 公共池与需匹配额 + 设置（原三行合并为两行里的首行） -->
    <div class="dp-top-bar__row dp-top-bar__row--primary">
      <div class="dp-top-bar__primary-text">
        <span class="dp-top-bar__title">
          {{ lbl.room }}: {{ roomId }} | {{ lbl.phase }}: <span ref="guideTopStage" class="dp-top-bar__accent">{{ displayStageLabel }}</span>
        </span>
        <span class="dp-top-bar__meta-sep" aria-hidden="true">·</span>
        <span class="dp-top-bar__sub">
          {{ lbl.pot }} <span ref="guideTopPot" class="dp-top-bar__pot">{{ pot }}</span>
          <span class="dp-top-bar__meta-sep dp-top-bar__meta-sep--thin" aria-hidden="true">|</span>
          {{ lbl.toCall }} <span ref="guideTopAlign" class="dp-top-bar__bet">{{ currentBetToCall }}</span>
          <template v-if="showHeroEconomy">
            <span class="dp-top-bar__meta-sep dp-top-bar__meta-sep--thin" aria-hidden="true">|</span>
            <span
                ref="guideTopHeroEco"
                class="dp-top-bar__hero-eco"
                role="region"
                :aria-label="heroEconomyAriaLabel"
            >
              <span class="dp-top-bar__hero-eco-stash">{{ lbl.stack }} <strong class="dp-top-bar__hero-eco-strong">{{ heroMyChips }}</strong></span>
              <span class="dp-top-bar__meta-sep dp-top-bar__meta-sep--thin" aria-hidden="true">|</span>
              <span class="dp-top-bar__hero-eco-secondary">{{ displayHeroSecondaryLabel }} <strong class="dp-top-bar__hero-eco-strong">{{ heroEconomySecondaryValue }}</strong></span>
              <span class="dp-top-bar__meta-sep dp-top-bar__meta-sep--thin" aria-hidden="true">|</span>
              <span class="dp-top-bar__hero-eco-secondary">{{ lbl.invested }} <strong class="dp-top-bar__hero-eco-strong">{{ heroCarryInChips }}</strong></span>
            </span>
          </template>
        </span>
      </div>
      <div ref="settingsRoot" class="dp-top-bar__settings-wrap">
        <button
            ref="guideTopSettings"
            type="button"
            class="dp-btn dp-top-bar__btn dp-top-bar__btn--ghost dp-top-bar__settings-btn"
            :aria-expanded="settingsOpen ? 'true' : 'false'"
            aria-haspopup="true"
            aria-controls="dp-top-bar-settings-panel"
            @click.stop="toggleSettings"
        >
          设置
        </button>
        <div
            v-show="settingsOpen"
            id="dp-top-bar-settings-panel"
            class="dp-top-bar__settings-panel dp-game-theme-row"
            role="region"
            aria-label="对局显示设置"
            @click.stop
        >
          <span class="dp-game-theme-row__label">界面主题</span>
          <dp-theme-picker
              :game-ui-theme="gameUiTheme"
              :theme-options="themeOptions"
              aria-label="选择对局界面主题"
              @input-theme="onThemeChange($event)"
          />
          <label class="dp-game-eco-label">
            <input
                type="checkbox"
                :checked="ecoMode"
                aria-label="节能模式：减少动画与模糊效果"
                @change="$emit('update:ecoMode', $event.target.checked)"
            >
            节能模式
          </label>
        </div>
      </div>
    </div>
    <!-- 第二行：操作按钮 -->
    <div class="dp-top-bar__row dp-top-bar__row--actions">
      <div class="dp-top-bar__actions">
        <button
            ref="guideTopFullscreen"
            type="button"
            class="dp-btn dp-top-bar__btn dp-top-bar__btn--ghost"
            :aria-pressed="isFullscreen ? 'true' : 'false'"
            @click="$emit('toggle-fullscreen')"
        >
          {{ isFullscreen ? '退出全屏' : '全屏' }}
        </button>
        <button ref="guideTopPlayGuide" type="button" class="dp-btn dp-btn--primary dp-top-bar__btn" @click="$emit('show-play-guide')">
          玩法说明
        </button>
        <button
            v-if="canToggleReveal"
            ref="guideTopReveal"
            type="button"
            class="dp-btn dp-top-bar__btn dp-top-bar__btn--reveal"
            :class="ownerRevealAll ? 'dp-btn--primary' : 'dp-btn--ghost'"
            :aria-pressed="ownerRevealAll ? 'true' : 'false'"
            aria-label="看穿底牌"
            @click="$emit('toggle-reveal')"
        >
          {{ ownerRevealAll ? '关闭看牌' : '看穿底牌' }}
        </button>
        <button
            v-if="canManageExperimentalDeckPreset"
            ref="guideTopDeckPreset"
            type="button"
            class="dp-btn dp-top-bar__btn dp-top-bar__btn--ghost"
            aria-label="实验排牌"
            @click="$emit('open-deck-preset')"
        >
          实验排牌
        </button>
        <button
            v-if="isOwner && gameUiTheme !== 'retro8bit'"
            ref="guideTopOwnerHub"
            type="button"
            class="dp-btn dp-top-bar__btn dp-top-bar__btn--owner"
            aria-label="房主操作"
            @click="$emit('open-owner-hub')"
        >
          房主操作
        </button>
        <button
            v-if="canNpcDecisionTrace && gameUiTheme !== 'retro8bit'"
            ref="guideTopDecisionTrace"
            type="button"
            class="dp-btn dp-top-bar__btn"
            :class="npcDecisionTracePinned ? 'dp-btn--primary' : 'dp-btn--ghost'"
            :aria-pressed="npcDecisionTracePinned ? 'true' : 'false'"
            aria-label="固定决策追踪侧栏"
            @click="$emit('toggle-decision-trace-dock')"
        >
          {{ npcDecisionTracePinned ? '决策追踪已固定' : '决策追踪' }}
        </button>
        <button
            v-if="canToggleReveal && gameUiTheme === 'retro8bit'"
            ref="guideTopRevealRetro"
            type="button"
            class="dp-owner-touch__entry dp-owner-touch__entry--topbar dp-owner-touch__entry--retro dp-top-bar__btn"
            :class="{ 'dp-owner-touch__entry--retro-pinned': ownerRevealAll }"
            :aria-pressed="ownerRevealAll ? 'true' : 'false'"
            aria-label="看穿底牌"
            @click="$emit('toggle-reveal')"
        >
          <span class="dp-owner-touch__entry-icon" aria-hidden="true">👁</span>
          <span class="dp-owner-touch__entry-label">{{ ownerRevealAll ? 'REVEAL+' : 'REVEAL' }}</span>
        </button>
        <button
            v-if="canManageExperimentalDeckPreset && gameUiTheme === 'retro8bit'"
            ref="guideTopDeckPresetRetro"
            type="button"
            class="dp-owner-touch__entry dp-owner-touch__entry--topbar dp-owner-touch__entry--retro dp-top-bar__btn"
            aria-label="实验排牌"
            @click="$emit('open-deck-preset')"
        >
          <span class="dp-owner-touch__entry-icon" aria-hidden="true">🃏</span>
          <span class="dp-owner-touch__entry-label">DECK</span>
        </button>
        <button
            v-if="isOwner && gameUiTheme === 'retro8bit'"
            ref="guideTopOwnerTouch"
            type="button"
            class="dp-owner-touch__entry dp-owner-touch__entry--topbar dp-owner-touch__entry--retro dp-top-bar__btn"
            aria-label="房主操作"
            :aria-expanded="ownerTouchOpen ? 'true' : 'false'"
            @click="$emit('open-owner-hub')"
        >
          <span class="dp-owner-touch__entry-icon" aria-hidden="true">⚙</span>
          <span class="dp-owner-touch__entry-label">OWNER</span>
        </button>
        <button
            v-if="canNpcDecisionTrace && gameUiTheme === 'retro8bit'"
            ref="guideTopDecisionTrace"
            type="button"
            class="dp-owner-touch__entry dp-owner-touch__entry--topbar dp-owner-touch__entry--retro dp-top-bar__btn"
            :class="{ 'dp-owner-touch__entry--retro-pinned': npcDecisionTracePinned }"
            :aria-pressed="npcDecisionTracePinned ? 'true' : 'false'"
            aria-label="固定决策追踪侧栏"
            @click="$emit('toggle-decision-trace-dock')"
        >
          <span class="dp-owner-touch__entry-icon" aria-hidden="true">▤</span>
          <span class="dp-owner-touch__entry-label">{{ npcDecisionTracePinned ? 'TRACE+' : 'TRACE' }}</span>
        </button>
        <button
            v-if="canInviteFriend"
            ref="guideTopInvite"
            type="button"
            class="dp-btn dp-top-bar__btn dp-top-bar__btn--ghost"
            aria-label="邀请好友进房"
            @click="$emit('open-invite-friend')"
        >
          邀请好友
        </button>
        <button
            ref="guideTopFriendChat"
            type="button"
            class="dp-btn dp-top-bar__btn dp-top-bar__btn--ghost dp-top-bar__btn--friend-chat"
            :class="{ 'dp-top-bar__btn--friend-chat-alert': friendChatUnreadTotal > 0 }"
            :aria-label="friendChatBtnAria"
            @click="$emit('open-friend-chat')"
        >
          <span class="dp-top-bar__friend-chat-label">{{ friendChatBtnLabel }}</span>
          <span
              v-if="friendChatUnreadTotal > 0"
              class="dp-top-bar__friend-chat-pip"
              aria-hidden="true"
          >!</span>
        </button>
        <button
            ref="guideTopHandHistory"
            type="button"
            class="dp-btn dp-top-bar__btn dp-top-bar__btn--ghost"
            @click="$emit('open-hand-history')"
        >
          历史对局
        </button>
        <button
            ref="guideTopMusic"
            type="button"
            class="dp-btn dp-top-bar__btn dp-top-bar__btn--ghost"
            @click="$emit('open-music-box')"
        >
          音乐盒
        </button>
        <button
            v-if="waitNextHandCount > 0"
            ref="guideTopWaitList"
            type="button"
            class="dp-btn dp-btn--cyan dp-top-bar__btn"
            @click="$emit('show-wait-next-hand')"
        >
          等待名单（{{ waitNextHandCount }}）
        </button>
        <button
            v-if="spectatorCount > 0"
            ref="guideTopSpectators"
            type="button"
            class="dp-btn dp-btn--cyan dp-top-bar__btn"
            @click="$emit('show-spectators')"
        >
          观众席（{{ spectatorCount }}）
        </button>
        <button
            v-if="showSpectatorPrepare"
            ref="guideTopReadyNext"
            type="button"
            class="dp-btn dp-top-bar__btn"
            :class="nextHandReady ? 'dp-btn--ghost' : 'dp-btn--success'"
            @click="$emit('ready-next-hand')"
        >
          {{ nextHandReady ? '取消下一局报名' : '下一局加入对局' }}
        </button>
        <button ref="guideTopExit" type="button" class="dp-btn dp-btn--danger dp-top-bar__btn" @click="$emit('exit')">
          {{ exitLabel }}
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import {
  formatTopBarLabel,
  dpTopBarStageLabel,
  dpTopBarHeroSecondaryLabel,
  dpTopBarHeroEconomyAria
} from '@features/room/utils/dpTopBarLabels'

export default {
  name: 'GameTopBar',
  props: {
    roomId: { type: String, required: true },
    /** 后端 stage 键（preflop/flop/…），retro8bit 映射英文阶段名 */
    stage: { type: String, default: '' },
    stageLabel: { type: String, required: true },
    pot: { type: Number, required: true },
    currentBetToCall: { type: Number, required: true },
    spectatorCount: { type: Number, default: 0 },
    /** 已报名下一局上桌的人数 */
    waitNextHandCount: { type: Number, default: 0 },
    isFullscreen: { type: Boolean, default: false },
    /** 纯观众或本手已退：在顶栏内展示报名下一局 */
    showSpectatorPrepare: { type: Boolean, default: false },
    nextHandReady: { type: Boolean, default: false },
    /** 与 game.vue 的 data-dp-game-theme 同步 */
    gameUiTheme: { type: String, required: true },
    ecoMode: { type: Boolean, required: true },
    /** 是否在顶栏显示「房主操作」入口 */
    isOwner: { type: Boolean, default: false },
    /** RBAC：拥有 game:hole_cards:view 权限 */
    canViewHoleCards: { type: Boolean, default: false },
    /** RBAC：拥有 game:experimental_deck_preset 权限 */
    canManageExperimentalDeckPreset: { type: Boolean, default: false },
    /** RBAC：拥有 game:npc_decision_trace 权限 */
    canNpcDecisionTrace: { type: Boolean, default: false },
    /** RBAC 看牌权限：可切换看穿底牌 */
    canToggleReveal: { type: Boolean, default: false },
    ownerRevealAll: { type: Boolean, default: false },
    /** retro8bit 触控房主面板是否已打开（顶栏按钮 aria） */
    ownerTouchOpen: { type: Boolean, default: false },
    /** 局内未离座成员或观众：可邀请互为好友进房 */
    canInviteFriend: { type: Boolean, default: false },
    /** Vuex dpMailbox：好友私信未读总数 */
    friendChatUnreadTotal: { type: Number, default: 0 },
    themeOptions: {
      type: Array,
      default: function () {
        return []
      }
    },
    /** 上桌本人：在统计行展示持有与本轮/还需补（与底栏拆离，仅顶栏一处） */
    showHeroEconomy: { type: Boolean, default: false },
    heroMyChips: { type: [Number, String], default: 0 },
    heroEconomySecondaryLabel: { type: String, default: '本轮' },
    heroEconomySecondaryValue: { type: [Number, String], default: 0 },
    heroCarryInChips: { type: [Number, String], default: 0 },
    /** 默认主题：决策追踪 dock 是否固定（Pin） */
    npcDecisionTracePinned: { type: Boolean, default: false },
    /** 教程页等可改为「退出教程」 */
    exitLabel: { type: String, default: '退出对局' }
  },
  computed: {
    lbl: function () {
      var t = this.gameUiTheme
      return {
        room: formatTopBarLabel(t, 'room'),
        phase: formatTopBarLabel(t, 'phase'),
        pot: formatTopBarLabel(t, 'pot'),
        toCall: formatTopBarLabel(t, 'toCall'),
        stack: formatTopBarLabel(t, 'stack'),
        invested: formatTopBarLabel(t, 'invested')
      }
    },
    displayStageLabel: function () {
      return dpTopBarStageLabel(this.gameUiTheme, this.stage, this.stageLabel)
    },
    displayHeroSecondaryLabel: function () {
      return dpTopBarHeroSecondaryLabel(this.gameUiTheme, this.heroEconomySecondaryLabel)
    },
    heroEconomyAriaLabel: function () {
      if (!this.showHeroEconomy) return ''
      return dpTopBarHeroEconomyAria(
          this.gameUiTheme,
          this.heroMyChips,
          this.heroEconomySecondaryLabel,
          this.heroEconomySecondaryValue,
          this.heroCarryInChips
      )
    },
    friendChatBtnLabel: function () {
      return this.gameUiTheme === 'retro8bit' ? 'DM' : '私信'
    },
    friendChatBtnAria: function () {
      var base = this.gameUiTheme === 'retro8bit' ? '好友私信' : '打开好友私信'
      if (this.friendChatUnreadTotal > 0) {
        return base + '，' + this.friendChatUnreadTotal + ' 条未读'
      }
      return base
    }
  },
  data: function () {
    return {
      settingsOpen: false
    }
  },
  mounted: function () {
    this._closeSettingsOnOutside = function (e) {
      var root = this.$refs.settingsRoot
      if (!root || root.contains(e.target)) return
      this.settingsOpen = false
    }.bind(this)
    document.addEventListener('click', this._closeSettingsOnOutside)
  },
  beforeDestroy: function () {
    document.removeEventListener('click', this._closeSettingsOnOutside)
  },
  methods: {
    toggleSettings: function () {
      this.settingsOpen = !this.settingsOpen
    },
    onThemeChange: function (id) {
      this.$emit('update:gameUiTheme', id)
    },
    openSettingsForGuide: function () {
      this.settingsOpen = true
    },
    closeSettingsForGuide: function () {
      this.settingsOpen = false
    }
  }
}
</script>
