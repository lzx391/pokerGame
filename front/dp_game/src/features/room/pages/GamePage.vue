<template>
  <div
      ref="gameRoot"
      class="dp-game-root"
      :data-dp-layout-tier="layoutTier"
      :class="{
        'dp-game-root--pseudo-fs': pseudoFullscreen,
        'dp-game-root--layout-fs': layoutFullscreen,
        'dp-game-root--mobile-hero-dock': mobileHeroDockActive,
        'dp-game-root--retro-desktop-fx': showRetroDesktopFx,
        'dp-game-root--trace-dock-open': showDecisionTraceDockWide,
        'dp-game-root--trace-dock-resizing': traceDockResizing,
        'dp-game-root--debug-bounds': debugBoundsEnabled
      }"
      :data-dp-game-theme="effectiveThemeForCss"
      :data-dp-eco-mode="ecoMode ? 'true' : 'false'"
      :data-dp-stage="cardDisplayStage"
      :data-dp-orientation="layoutOrientation"
      :style="retroPolygonRootStyle"
  >
    <!-- 顶栏 | 主区(牌桌) | 底栏 —— 三块同级 flex，无额外嵌套 -->
    <div class="dp-game-body-row">
    <div class="dp-game-layout">
    <header class="dp-game-layout__header">
    <game-top-bar
        :room-id="roomId"
        :stage="cardDisplayStage"
        :stage-label="displayStageCN"
        :pot="displayPot"
        :current-bet-to-call="displayCurrentBetToCall"
        :spectator-count="spectators.length"
        :wait-next-hand-count="waitNextHand.length"
        :is-fullscreen="layoutFullscreen"
        :is-owner="isOwner"
        :can-view-hole-cards="canViewHoleCards"
        :can-toggle-reveal="canToggleReveal"
        :owner-reveal-all="ownerRevealAll"
        :owner-touch-open="ownerTouchSheetOpen"
        :can-invite-friend="canInviteFriend"
        :show-spectator-prepare="showSpectatorPrepareBlock"
        :next-hand-ready="nextHandReady"
        :game-ui-theme="gameUiTheme"
        @update:gameUiTheme="onGameUiThemeChange"
        :eco-mode="ecoMode"
        @update:ecoMode="$store.commit('dpGame/SET_ECO_MODE', $event)"
        :theme-options="gameThemeOptions"
        @show-play-guide="$store.commit('dpGame/SET_MODAL', { showPlayGuideModal: true, playGuideTab: 'flow' })"
        @show-spectators="$store.commit('dpGame/SET_MODAL', { showSpectatorModal: true })"
        @show-wait-next-hand="$store.commit('dpGame/SET_MODAL', { showWaitNextHandModal: true })"
        @toggle-fullscreen="toggleDpFullscreen"
        @open-hand-history="openHandHistory"
        @open-music-box="onOpenMusicBox"
        @open-owner-hub="onOwnerHubClick"
        @toggle-reveal="onToggleRevealAll"
        @open-invite-friend="onInviteFriendClick"
        @open-friend-chat="onFriendChatClick"
        :friend-chat-unread-total="friendChatUnreadTotal"
        @exit="exitGame"
        @ready-next-hand="readyNextHand"
        :show-hero-economy="topBarShowHeroEconomy"
        :hero-my-chips="displayMyChips"
        :hero-economy-secondary-label="topBarHeroEconomySecondaryLabel"
        :hero-economy-secondary-value="topBarHeroEconomySecondaryValue"
        :hero-carry-in-chips="myCarryInChips"
        :npc-decision-trace-pinned="npcDecisionTraceDockPinned"
        @toggle-decision-trace-dock="onToggleDecisionTraceDock"
    />

    </header>

    <main
        ref="gameMain"
        class="dp-game-layout__main dp-game-layout__main--fit-table"
        :class="{ 'dp-game-layout__main--settlement-scroll': cardDisplayStage === 'showdown' || cardDisplayStage === 'settled' }"
    >
    <p
        v-if="layoutTier === 'phone' && layoutOrientation === 'portrait'"
        class="dp-game-layout__portrait-hint"
        role="status"
    >
      横屏可获得更大牌桌视野，建议旋转手机并全屏游玩。
    </p>
    <div class="dp-game-table-fit" :style="tableFitClipStyleObj">
      <div ref="tableFitInner" class="dp-game-table-fit__inner">
          <game-round-table
              :chip-leader-nicknames="tableChipLeaderNicknames"
              :players-display-order="playersDisplayOrder"
              :show-table-action-timer="showTableActionTimer"
              :time-left="timeLeft"
              :timer-actor-name="tableActionActorDisplayName"
              :timer-urgency="tableActionTimerUrgency"
              :timer-progress-pct="actionTimerProgressPct"
              :eco-mode="ecoMode"
              :community-cards="communityCards"
              :community-cards-flip-state="communityCardsFlipState"
              :viewer-seated-at-table="viewerSeatedAtTable"
              :act-index="actIndex"
              :stage="stage"
              :card-display-stage="cardDisplayStage"
              :retro-showdown-tv-pending="retroShowdownTvPending"
              :community-cards-flip-complete="communityCardsFlipComplete"
              :is-owner="isOwner"
              :can-view-all-hole-cards="canViewAllHoleCards"
              :owner-reveal-all="ownerRevealAll"
              :my-nickname="user ? user.nickname : ''"
              :current-hand-seed="currentHandSeed"
              :hole-deal-player-count-for-anim="holeDealPlayerCountForAnim"
              :showdown-hand-leader-nicknames="tableShowdownHandLeaderNicknames"
              :dealer-display-index="dealerDisplayIndex"
              :get-player-box-style="getPlayerBoxStyle"
              :hole-deal-order-from-dealer="holeDealOrderFromDealer"
              :seat-chat-text-for="seatChatTextFor"
              :join-reveal-nicks="joinRevealNicks"
              :seat-enter-reveal-enabled="useRetroSeatEnterReveal"
              :game-ui-theme="gameUiTheme"
              :pot="displayPot"
              :player-economy-for-display="playerEconomyForDisplay"
              :show-retro-desktop-fx="showRetroDesktopFx"
              :retro-desktop-animated="useRetroDesktopAmbience"
              :retro-glitch-seq="retroGlitchSeq"
              @hole-deal-intro-complete="$store.commit('dpGame/SET_HERO_HOLE_DEAL', true)"
              @seat-enter-reveal-done="onSeatEnterRevealDone"
              @card-click="onPlayerCardClick"
          />
        </div>
    </div>

    </main>

    <footer class="dp-game-layout__footer">
      <game-hero-dock-footer ref="heroDockFooter" />
    </footer>
    </div>

    <div
        v-if="showDecisionTraceDockWide"
        class="dp-trace-dock-split"
        :style="traceDockLayoutStyle"
    >
      <div
          class="dp-trace-dock-resizer"
          :class="{ 'dp-trace-dock-resizer--active': traceDockResizing }"
          role="separator"
          aria-orientation="vertical"
          aria-label="调整决策追踪面板宽度"
          aria-valuemin="280"
          :aria-valuemax="traceDockWidthMaxPx"
          :aria-valuenow="traceDockWidthPx"
          tabindex="0"
          @pointerdown.prevent="onTraceDockResizeStart"
      />

      <game-npc-decision-trace-dock
          layout-mode="dock"
          :pinned="npcDecisionTraceDockPinned"
          :dock-width-px="traceDockWidthPx"
          :ui-variant="gameUiTheme === 'retro8bit' ? 'retro8bit' : 'default'"
          :room-id="roomId"
          :hands="traceHands"
          :loading="traceHandsLoading"
          :load-error="traceHandsLoadError"
          :on-auth-failure="(body) => handleDeckPresetAuthFailure(body)"
          @refresh="loadTraceHands()"
      />
    </div>
    </div>

    <game-dp-floating-modals />

    <audio
        ref="roomBgm"
        class="dp-room-bgm"
        preload="none"
        aria-hidden="true"
    />

    <game-dp-game-sheets />

    <game-hero-hand-hologram v-if="gameUiTheme === 'retro8bit'" ref="heroHandHologram" />
    <dp-crt-boot-sequence ref="crtBootSequence" />
    <dp-terminal-cli v-if="gameUiTheme === 'retro8bit'" ref="terminalCli" />
    <dp-crt-event-popup v-if="gameUiTheme === 'retro8bit'" ref="crtEventPopup" />
    <dp-music-player ref="musicPlayer" v-if="gameUiTheme === 'retro8bit'" :open.sync="showMusicPlayer" />
    <dp-hand-history-viewer ref="handHistoryViewer" v-if="gameUiTheme === 'retro8bit'" :open.sync="showHandHistoryPanel" />
    <dp-hand-history-viewer
        v-if="gameUiTheme === 'retro8bit'"
        ref="opponentHandHistoryViewer"
        :open.sync="showOpponentHandHistoryPanel"
        list-mode="withOpponent"
        :other-user-id="opponentHandHistoryOtherUserId"
        :opponent-display-name="opponentHandHistoryDisplayName"
    />
    <dp-hand-history-detail
      ref="handHistoryDetail"
      v-if="gameUiTheme === 'retro8bit'"
      :hand-history-id="handHistoryDetailId"
      :achievement-subject-user-id="handHistoryDetailSubjectUserId"
      :achievement-subject-nickname="handHistoryDetailSubjectNickname"
      @closed="onHandHistoryDetailClosed"
    />

    <dp-retro-ambient-overlay
        v-if="showRetroDesktopFx"
        :animated="useRetroDesktopAmbience"
        @glitch-burst="onRetroGlitchBurst"
    />

  </div>
</template>

<script>
import '@/styles/dp-game-themes.css'
import '@/styles/dp-game-shell.css'
import '@/styles/dp-game-debug-bounds.css'
import '@/styles/dp-game-modals.css'
import '@/styles/dp-game-eco-mode.css'
import '@/styles/dp-retro-desktop-fx.css'
import GameTopBar from '../components/GameTopBar.vue'
import { buildRetroTableLayout, holeDealOrderFromDealer as holeDealOrderFromDealerUtil } from '../utils/dpGameRoundTableLayout'
import { dpDisplayNickname, isDpBotNickname, isDpLlmBotNickname, isDpRuleBotNickname } from '@shared/utils/dpDisplayNickname'
import { resolveRoomPersonMeta } from '../utils/dpRoomPlayerLookup'
import { dpSocialApi } from '@features/social/api/socialApi'
import { musicFileSrc } from '../utils/dpGameMusicUrl'
import GameRoundTable from '../components/GameRoundTable.vue'
import GameHeroDockFooter from '../components/GameHeroDockFooter.vue'
import GameDpFloatingModals from '../components/GameDpFloatingModals.vue'
import GameDpGameSheets from '../components/GameDpGameSheets.vue'
import GameNpcDecisionTraceDock from '@features/npc/components/GameNpcDecisionTraceDock.vue'
import GameHeroHandHologram from '../components/GameHeroHandHologram.vue'
import DpCrtBootSequence from '@shared/components/DpCrtBootSequence.vue'
import DpTerminalCli from '@features/download/components/DpTerminalCli.vue'
import DpCrtEventPopup from '@shared/components/DpCrtEventPopup.vue'
import DpMusicPlayer from '@features/music/components/DpMusicPlayer.vue'
import DpHandHistoryViewer from '@features/history/components/DpHandHistoryViewer.vue'
import DpHandHistoryDetail from '@features/history/components/DpHandHistoryDetail.vue'
import DpRetroAmbientOverlay from '@shared/components/DpRetroAmbientOverlay.vue'
import dpGameFullscreenMixin from '../mixins/dpGameFullscreenMixin'
import dpGameTableFitMixin from '../mixins/dpGameTableFitMixin'
import dpGameActionCountdownMixin from '../mixins/dpGameActionCountdownMixin'
import dpGameLayoutTierMixin from '../mixins/dpGameLayoutTierMixin'
import { dpGamePlayerBoxStyle } from '../utils/dpGamePlayerBoxStyle'
import { ensureDpUserIdInStorage } from '@features/user/utils/dpEnsureUserId'
import { bootstrapDpAuthPermissions } from '@features/auth/utils/dpAuthBootstrap'
import { dpResultSuccess, dpResultData, dpResultMessage } from '@shared/utils/dpApiResult'
import {
  isDeckPresetUnlocked,
  setDeckPresetSessionUnlock,
  dpDeckPresetSessionPassword,
  clearDeckPresetSessionUnlock
} from '@features/room/utils/dpDeckPresetUnlock'
import { fetchTraceHands } from '@features/npc/utils/dpNpcDecisionTrace'
import {
  dpNpcDecisionTraceAuthPassword,
  isNpcDecisionTraceUnlocked
} from '@features/npc/utils/dpNpcDecisionTraceAuth'
import { dpRoomApi } from '../api/roomApi'
import { mapState, mapGetters } from 'vuex'
import { dpSocialDisplayNickname } from '@features/social/utils/dpSocialDisplayName'
import { encodeRoomApplyFingerprint } from '../utils/dpGameRoomFingerprint'
import { CAT_COPY, dpPotDisplayLabel } from '@shared/constants/dpCatThemeCopy'
import { DP_CUSTOM_NPC_UI_ENABLED } from '@features/npc/constants/dpCustomNpcUi'
import { dpHandHologramDevLog } from '@features/room/utils/dpHandHologramDevLog'
import { isDpDebugBoundsEnabled, DP_DEBUG_BOUNDS_BODY_CLASS } from '@features/room/utils/dpDebugBounds'
import { dpInviteFriendsDevLog } from '@features/social/utils/dpInviteFriendsDevLog'
import { dpOwnerTerminalDevLog } from '@features/room/utils/dpOwnerTerminalDevLog'
import { dpSeatEnterDevLog } from '@features/room/utils/dpSeatEnterDevLog'
import {
  dpRetroMonsterGateLog,
  dpRetroMonsterLog,
  dpRetroMonsterLogStartupHint
} from '@features/room/utils/dpRetroDesktopFxDevLog'
import { extractPlayerNicknames, diffNewSeatNicknames } from '@features/room/utils/dpSeatEnterNickDiff'
import {
  communityFlipCompleteMsForTheme,
  communityFlipDelayMsForTheme
} from '../constants/dpGameDealTiming'
import { shouldRetroShowdownTvSequence, resolveCardDisplayStage, resolveShowdownHandLeaders, isRetroBettingStage, isRetroRevealStage, isSettlePresentationFrozen, shouldDeferRetroNpcSeatChat, captureBettingEconomySnapshot, resolvePlayerEconomyDisplay, resolveFrozenTablePot, resolveFrozenCurrentBetToCall, resolveFrozenChipLeaderNicknames } from '@features/room/utils/dpRetroShowdownReveal'
import { dpGameStageDisplay } from '@shared/constants/dpCatThemeCopy'

export default {
  mixins: [dpGameFullscreenMixin, dpGameTableFitMixin, dpGameActionCountdownMixin, dpGameLayoutTierMixin],
  provide() {
    return {
      dpGameView: this
    }
  },
  components: {
    GameTopBar,
    GameRoundTable,
    GameHeroDockFooter,
    GameDpFloatingModals,
    GameDpGameSheets,
    GameNpcDecisionTraceDock,
    GameHeroHandHologram,
    DpCrtBootSequence,
    DpTerminalCli,
    DpCrtEventPopup,
    DpMusicPlayer,
    DpHandHistoryViewer,
    DpHandHistoryDetail,
    DpRetroAmbientOverlay
  },
  data() {
    return {
      communityCardsFlipCompleteTimer: null,
      /** WS/HTTP 房间快照指纹；与 {@link encodeRoomApplyFingerprint} 一致，未变则跳过 APPLY_ROOM */
      _lastRoomApplyFingerprint: '',
      gameWs: null,
      gameWsConnected: false,
      /** 每开一条新连接前自增，用于丢弃旧 socket 的 onclose/onopen，避免顶替连接时误触重连 */
      gameWsSession: 0,
      wsReconnectTimer: null,
      /** 已连续重连失败次数；成功 onopen 时清零 */
      wsReconnectAttempt: 0,
      /** 离房 / 解散 / 组件销毁后禁止再连 */
      wsNoReconnect: false,
      pollTimer: null,
      backupPollTimer: null,
      heartbeatTimer: null,
      _seatChatTimers: null,
      _dpRoomClosedHandled: false,
      _lastRoomBgmUrl: '',
      _lastRoomMusicWebPath: '',
      playerSocialOpen: false,
      playerSocialTarget: null,
      npcMoodOpen: false,
      npcMoodTarget: null,
      inviteFriendOpen: false,
      friendChatPickerOpen: false,
      friendChatVisible: false,
      friendChatPeerId: null,
      friendChatPeerName: '',
      friendChatPeerAvatar: '',
      friendChatPeerAvatarUpdatedAt: null,
      friendChatPeerUnread: 0,
      ownerTerminalOpen: false,
      ownerTouchSheetOpen: false,
      showOwnerPotJudgeSheet: false,
      showMusicPlayer: false,
      showHandHistoryPanel: false,
      showOpponentHandHistoryPanel: false,
      handHistoryDetailId: null,
      handHistoryDetailSubjectUserId: null,
      handHistoryDetailSubjectNickname: '',
      viewportWidth: typeof window !== 'undefined' ? window.innerWidth : 1024,
      prefersReducedMotion: false,
      _hologramResizeTimer: null,
      _hologramPrmMedia: null,
      _seatEnterNickSeeded: false,
      joinRevealNicks: {},
      _gameUiThemeChangeTimer: null,
      retroGlitchSeq: 0,
      /** retro8bit：首帧 stage 同步完成后才允许 TV 摊牌序列（避免进房即 settled 误触） */
      retroStageNavReady: false,
      /** 本局摊牌 TV 是否已排队（避免 stage 重复触发）；勿用 _ 前缀，Vue2 computed 无法订阅 */
      retroShowdownTvPending: false,
      /** 进入摊牌前的下注街，供 TV 期间 cardDisplayStage 回退 */
      retroShowdownFromStage: null,
      /** 最后一帧下注街经济快照（TV 期间冻结积分/底池展示） */
      retroBettingEconomySnapshot: null,
      showDeckPresetDialog: false,
      showDeckPresetPasswordGate: false,
      deckPresetSavedCount: 0,
      deckPresetInitialCards: [],
      deckPresetSubmitting: false,
      /** 'deck-preset' | 'decision-trace' — routes shared experimental password gate */
      experimentalGatePendingFeature: null,
      npcDecisionTraceDockPinned: false,
      showNpcDecisionTraceSheet: false,
      _decisionTraceDockLoadedOnce: false,
      traceHands: [],
      traceHandsLoading: false,
      traceHandsLoadError: '',
      /** 宽屏决策追踪侧栏宽度（px）；localStorage 持久化 */
      traceDockWidthPx: 320,
      traceDockResizing: false,
      debugBoundsEnabled: false,
    }
  },

  computed: {
    ...mapState('dpGame', [
      'gameUiTheme', 'ecoMode', 'gameThemeOptions', 'roomId', 'user', 'currentHandSeed', 'owner', 'players', 'playing', 'stage', 'communityCards', 'pot', 'pots', 'currentBetToCall', 'lastRaiseIncrement', 'actIndex', 'spectators', 'waitNextHand', 'raiseAmount', 'selectedWinners', 'potWinners', 'nextHandReady', 'loading', 'communityCardsFlipState', 'communityCardsFlipComplete', 'seatChatTextByNick', 'roomChatMessages', 'chatInputDraft', 'showPlayGuideModal', 'playGuideTab', 'showSpectatorModal', 'showWaitNextHandModal', 'showHandHistoryModal', 'showOpponentHandHistoryModal', 'opponentHandHistoryOtherUserId', 'opponentHandHistoryDisplayName', 'showMusicBoxModal', 'musicTracks', 'musicTracksLoading', 'musicTracksError', 'roomMusicState', 'showOwnerHubSheet', 'showCustomNpcStyleDialog', 'customNpcPendingCount', 'ownerToolType', 'ownerActionTarget', 'demoBotAdding', 'demoBotAddedTip', 'maniacBotAdding', 'maniacBotAddedTip', 'tagBotAdding', 'tagBotAddedTip', 'lagBotAdding', 'lagBotAddedTip', 'nitBotAdding', 'nitBotAddedTip', 'callBotAdding', 'callBotAddedTip', 'llmBotAdding', 'llmBotAddedTip', 'llmGlobalBotAdding', 'llmGlobalBotAddedTip', 'customBotAdding', 'customBotAddedTip', 'ownerRevealAll', 'showMobileHandSheet', 'showMobileActionSheet', 'showHeroHandHologram', 'heroHoleDealIntroDone', 'chipLeaderNicknames', 'myCarryInChips'
    ]),
    ...mapGetters('dpGame', [
      'effectiveThemeForCss', 'handRankReference', 'stageCN', 'isOwner', 'canInviteFriend', 'isMyTurn', 'myPlayer', 'showSpectatorPrepareBlock', 'myReady', 'myChips', 'myBet', 'callAmount', 'smallBlind', 'bigBlind', 'lastRaiseIncrementEffective', 'minTotalToRaise', 'minRaise', 'allPotsHaveWinners', 'inSettledStage', 'ownerActionPlayers', 'playersDisplayOrder', 'viewerSeatedAtTable', 'holeDealPlayerCountForAnim', 'heroDockRow', 'dealerDisplayIndex', 'showdownHandLeaderNicknames', 'spectatorSeatChatEntries', 'tableActionActorDisplayName', 'showHeroViewHandButton', 'showBottomHeroDock'
    ]),
    ...mapGetters('dpAuth', ['canViewHoleCards']),
    ...mapState('dpMailbox', ['friendChatUnreadTotal']),
    ...mapGetters('dpMailbox', ['friendUnreadForUser']),
    useRetroFriendChatPanelWide() {
      return this.gameUiTheme === 'retro8bit' && this.viewportWidth > 600
    },
    friendChatMyUserId() {
      var u = this.user
      var n = u && u.userId != null && u.userId !== '' ? Number(u.userId) : 0
      return isNaN(n) || n <= 0 ? 0 : n
    },
    roomApiParams() {
      return { roomId: this.roomId }
    },
    actionTimerThinkTotalSec() {
      var v = Number(this.$store.state.dpGame.thinkTimeSeconds)
      return isFinite(v) && v >= 1 ? Math.floor(v) : 30
    },
    actionTimerProgressPct() {
      var t = Number(this.timeLeft)
      var total = this.actionTimerThinkTotalSec
      if (isNaN(t) || t < 0 || total < 1) return 0
      return Math.min(1, t / total)
    },
    tableActionTimerUrgency() {
      var t = Number(this.timeLeft)
      if (isNaN(t)) return 'ok'
      if (t > 10) return 'ok'
      if (t > 5) return 'warning'
      return 'danger'
    },
    showTableActionTimer() {
      return this.actionCountdownShouldRun()
    },
    /** 顶栏展示本人持有/本轮/还需补（与原底栏筹码条同期机一致，避免重复） */
    topBarShowHeroEconomy() {
      return !!(this.viewerSeatedAtTable && this.heroDockRow)
    },
    topBarHeroEconomySecondaryLabel() {
      if (this.isMyTurn && !this.uiInSettledStage && (Number(this.callAmount) || 0) > 0) {
        return '还需补'
      }
      return '本轮'
    },
    topBarHeroEconomySecondaryValue() {
      if (this.isMyTurn && !this.uiInSettledStage && (Number(this.callAmount) || 0) > 0) {
        return Number(this.callAmount) || 0
      }
      return Number(this.displayMyBet) || 0
    },
    useRetroHandHologramWide() {
      return this.gameUiTheme === 'retro8bit' && this.viewportWidth > 600
    },
    useRetroInvitePanelWide() {
      return this.gameUiTheme === 'retro8bit' && this.viewportWidth > 600
    },
    useRetroOwnerPanelWide() {
      return this.gameUiTheme === 'retro8bit' && this.viewportWidth > 600
    },
    canViewAllHoleCards() {
      return !!this.canViewHoleCards
    },
    canToggleReveal() {
      return this.canViewAllHoleCards
    },
    /** 默认主题决策追踪：宽屏右侧 dock；窄屏 bottom sheet */
    useDecisionTraceDockWide() {
      return this.viewportWidth > 600 && this.layoutTier !== 'phone'
    },
    showDecisionTraceDockWide() {
      return this.isOwner
        && this.npcDecisionTraceDockPinned
        && this.useDecisionTraceDockWide
    },
    traceDockWidthMaxPx() {
      var vw = this.viewportWidth || (typeof window !== 'undefined' ? window.innerWidth : 1024)
      return Math.floor(vw * 0.5)
    },
    traceDockLayoutStyle() {
      if (!this.showDecisionTraceDockWide) return {}
      return { '--dp-trace-dock-width': this.traceDockWidthPx + 'px' }
    },
    useRetroSeatEnterReveal() {
      return this.gameUiTheme === 'retro8bit'
        && this.viewportWidth > 600
        && !this.ecoMode
        && !this.prefersReducedMotion
    },
    /**
     * Wide retro table FX layout: same gate as hologram / seat rays / pot particles (vw > 600).
     * Phone tier excluded; tablet (e.g. 778px) included — not desktop-only ≥900/1024.
     */
    isRetroDesktopLayout() {
      if (this.layoutTier === 'phone') return false
      return this.viewportWidth > 600
    },
    showRetroDesktopFx() {
      return this.gameUiTheme === 'retro8bit' && this.isRetroDesktopLayout
    },
    useRetroDesktopAmbience() {
      return this.showRetroDesktopFx && !this.ecoMode && !this.prefersReducedMotion
    },
    retroPolygonRootStyle() {
      if (this.gameUiTheme !== 'retro8bit') return {}
      var n = (this.playersDisplayOrder && this.playersDisplayOrder.length) || 0
      var layout = buildRetroTableLayout(n, {
        viewerSeatedAtTable: this.viewerSeatedAtTable,
        logReason: 'game-root-polygon'
      })
      return { '--dp-table-polygon': layout.clipPath }
    },
    /** retro8bit TV 播放期间：玩家卡片仍按上一下注街展示（紧凑/未亮牌） */
    cardDisplayStage() {
      if (this.gameUiTheme !== 'retro8bit') return this.stage
      return resolveCardDisplayStage(
        this.stage,
        this.retroShowdownTvPending,
        this.retroShowdownFromStage
      )
    },
    /** 牌桌摊牌高亮：TV 结束后再展示 */
    tableShowdownHandLeaderNicknames() {
      return resolveShowdownHandLeaders(
        this.stage,
        this.retroShowdownTvPending,
        this.showdownHandLeaderNicknames
      )
    },
    /** retro8bit TV 播放中：presentation 冻结（亮牌/结算 UI/积分/NPC 气泡） */
    settlePresentationFrozen() {
      if (this.gameUiTheme !== 'retro8bit') return false
      return isSettlePresentationFrozen(this.retroShowdownTvPending, this.stage)
    },
    uiInSettledStage() {
      if (this.settlePresentationFrozen) return false
      return this.inSettledStage
    },
    deckPresetPlayerCount() {
      var ps = this.players || []
      var n = 0
      for (var i = 0; i < ps.length; i++) {
        var p = ps[i]
        if (p && !p.leftThisHand) n++
      }
      return n
    },
    mobileHeroDockActive() {
      return !!(this.heroDockRow || this.isMyTurn || this.uiInSettledStage || this.isOwner)
    },
    displayStageCN() {
      if (!this.settlePresentationFrozen) return this.stageCN
      return dpGameStageDisplay(this.cardDisplayStage)
    },
    displayPot() {
      return resolveFrozenTablePot(this.pot, this.retroBettingEconomySnapshot, this.settlePresentationFrozen)
    },
    displayCurrentBetToCall() {
      return resolveFrozenCurrentBetToCall(
        this.currentBetToCall,
        this.retroBettingEconomySnapshot,
        this.settlePresentationFrozen
      )
    },
    tableChipLeaderNicknames() {
      return resolveFrozenChipLeaderNicknames(
        this.chipLeaderNicknames,
        this.retroBettingEconomySnapshot,
        this.settlePresentationFrozen
      )
    },
    displayMyChips() {
      var mp = this.myPlayer
      if (!mp) return 0
      return resolvePlayerEconomyDisplay(
        mp,
        this.retroBettingEconomySnapshot,
        this.settlePresentationFrozen
      ).chips
    },
    displayMyBet() {
      var mp = this.myPlayer
      if (!mp) return 0
      return resolvePlayerEconomyDisplay(
        mp,
        this.retroBettingEconomySnapshot,
        this.settlePresentationFrozen
      ).bet
    }
  },

  watch: {
    showRetroDesktopFx: function (on) {
      if (this.gameUiTheme === 'retro8bit') this.logRetroFxGate('showRetroDesktopFx')
      if (on) this.logRetroMonsterMountState('showRetroDesktopFx')
    },
    useRetroDesktopAmbience: function () {
      if (this.gameUiTheme === 'retro8bit') this.logRetroFxGate('useRetroDesktopAmbience')
    },
    layoutTier: function () {
      if (this.gameUiTheme === 'retro8bit') this.logRetroFxGate('layoutTier')
    },
    viewportWidth: function () {
      if (this.gameUiTheme === 'retro8bit') this.logRetroFxGate('viewportWidth')
      this.traceDockWidthPx = this.clampTraceDockWidth(this.traceDockWidthPx)
    },
    ecoMode: function () {
      if (this.gameUiTheme === 'retro8bit') this.logRetroFxGate('ecoMode')
    },
    prefersReducedMotion: function () {
      if (this.gameUiTheme === 'retro8bit') this.logRetroFxGate('prefersReducedMotion')
    },
    gameUiTheme: function (theme) {
      if (theme === 'retro8bit') {
        this.$nextTick(function () {
          this.logRetroFxGate('gameUiTheme')
        }.bind(this))
      }
    },
    isMyTurn: function (v) {
      if (v) this.$store.commit('dpGame/SET_RAISE_AMOUNT', this.minRaise)
      else this.$store.commit('dpGame/SET_MOBILE_SHEETS', { showMobileActionSheet: false })
    },
    heroDockRow: function (row) {
      if (!row) {
        this.$store.commit('dpGame/SET_MOBILE_SHEETS', { showMobileHandSheet: false })
        this.$store.commit('dpGame/SET_HERO_HAND_HOLOGRAM', false)
      }
    },
    minRaise: function () {
      if (this.isMyTurn && this.raiseAmount < this.minRaise) {
        this.$store.commit('dpGame/SET_RAISE_AMOUNT', this.minRaise)
      }
    },
    actIndex() {
      this.syncActionCountdown()
    },
    playing() {
      this.syncActionCountdown()
    },
    currentHandSeed() {
      this.$store.commit('dpGame/SET_HERO_HOLE_DEAL', false)
      this.syncActionCountdown()
    },
    stage(newVal, oldVal) {
      this.syncActionCountdown()
      if (newVal !== 'preflop') {
        this.$store.commit('dpGame/SET_HERO_HOLE_DEAL', true)
      }
      if (newVal === 'settled') {
        if (!this.settlePresentationFrozen) {
          this.startReadyCountdown()
        }
      } else {
        this.stopReadyCountdown()
        this.$store.commit('dpGame/SET_MOBILE_SHEETS', { showMobileActionSheet: false })
      }
      if (newVal === 'showdown' && this.isOwner && this.useRetroOwnerPanelWide) {
        this.showOwnerPotJudgeSheet = true
        dpOwnerTerminalDevLog('pot judge sheet', { action: 'auto-open', stage: newVal })
      }
      if (newVal !== 'showdown') {
        this.showOwnerPotJudgeSheet = false
      }
      this.onRetroStageTransition(newVal, oldVal)
      if (newVal === 'preflop' && oldVal === 'settled') {
        var handSeed = this.currentHandSeed || ''
        this.fireCrtPopup('info', 'NEW HAND', '第 ' + handSeed + ' 局', '发牌中...')
      }
      var self = this
      this.$nextTick(function () {
        self.syncRoomBgmAudio()
      })
    },
    isOwner(v) {
      if (!v) {
        this.$store.commit('dpGame/CLOSE_OWNER_HUB')
        this.closeOwnerTerminal()
        this.showOwnerPotJudgeSheet = false
      }
    },
    showMusicPlayer(v) {
      if (!v) { this.refocusTerminalIfOpen() }
    },
    showHandHistoryPanel(v) {
      if (!v) { this.refocusTerminalIfOpen() }
    },
    showOpponentHandHistoryPanel(v) {
      if (!v) { this.refocusTerminalIfOpen() }
    },
    handHistoryDetailId(v) {
      if (v == null && !this.showHandHistoryPanel && !this.showOpponentHandHistoryPanel) { this.refocusTerminalIfOpen() }
    }
  },

  beforeRouteUpdate(to, from, next) {
    if (to.params.roomId !== from.params.roomId) {
      this.resetRoomChatUiForEnter()
      this.resetSeatEnterStateForRoom()
      this.$store.commit('dpGame/SET_SESSION', { roomId: to.params.roomId })
      var self = this
      this.loadGame().then(function () {
        self.fetchRoomChatRecent()
        self.shutdownGameWsPermanently()
        self.connectGameWs()
      })
    }
    next()
  },

  created() {
    this.debugBoundsEnabled = isDpDebugBoundsEnabled()
    if (this.debugBoundsEnabled && typeof document !== 'undefined') {
      document.body.classList.add(DP_DEBUG_BOUNDS_BODY_CLASS)
    }
    this._seatChatTimers = Object.create(null)
    this._deferredSeatChats = []
    this._seatEnterNickSnapshot = new Set()
    this.resetRoomChatUiForEnter()
    this.resetSeatEnterStateForRoom()
    this.traceDockWidthPx = this.readTraceDockWidthPx()
    this.$store.commit('dpGame/SET_SESSION', { roomId: this.$route.params.roomId })

    var self = this
    ;(async function () {
      var raw = localStorage.getItem('userInfo')
      if (!raw) {
        self.$message.error('登录信息丢失，请重新登录')
        self.$router.push('/login')
        return
      }
      var user = await ensureDpUserIdInStorage(self.$http)
      if (!user || !user.nickname) {
        self.$message.error('登录信息丢失，请重新登录')
        self.$router.push('/login')
        return
      }
      var uid = Number(user.userId)
      if (isNaN(uid) || uid <= 0) {
        self.$message.error('登录信息不完整，请重新登录以同步账号 ID')
        self.$router.push('/login')
        return
      }
      user.userId = uid
      self.$store.commit('dpGame/SET_SESSION', { user: user })
      bootstrapDpAuthPermissions(self).catch(function () {})

      // 先 HTTP 拉一次，再建立 WebSocket（推送与定时器同 1s 节奏）
      self.loadGame().then(function () {
        self.fetchRoomChatRecent()
        self.connectGameWs()
      })

      self.loadMusicTracks()

      // 未连上 WS 时 1 秒轮询兜底；握手过程中 readyState===CONNECTING 也要停掉，否则会连着打一串 getNowRoom
      self.pollTimer = setInterval(function () {
        if (self.loading) return
        if (self.gameWsConnected) return
        if (self.gameWs && self.gameWs.readyState === WebSocket.CONNECTING) return
        self.loadGame()
      }, 1000)

      // 已连上 WS 时低频 HTTP 兜底（防止长连异常而界面停滞）
      self.backupPollTimer = setInterval(function () {
        if (!self.loading && self.gameWsConnected) self.loadGame()
      }, 15000)

      // 5秒独立心跳（和 loadGame 解耦，loadGame 失败不影响心跳）
      self.sendHeartbeat()
      self.heartbeatTimer = setInterval(function () {
        self.sendHeartbeat()
      }, 5000)
    })()
  },

  mounted() {
    this.initHologramViewportListeners()
    this._onGameKeydown = this.onGameKeydown.bind(this)
    window.addEventListener('keydown', this._onGameKeydown)
    if (this.gameUiTheme === 'retro8bit') {
      this.logRetroFxGate('mounted')
      if (this.showRetroDesktopFx) {
        this.logRetroMonsterMountState('mounted')
      }
    }
  },

  beforeDestroy() {
    if (this.debugBoundsEnabled && typeof document !== 'undefined') {
      document.body.classList.remove(DP_DEBUG_BOUNDS_BODY_CLASS)
    }
    this.teardownHologramViewportListeners()
    this.teardownTraceDockResizeListeners()
    if (this._onGameKeydown) {
      window.removeEventListener('keydown', this._onGameKeydown)
      this._onGameKeydown = null
    }
    this.$store.commit('dpGame/SET_MODAL', { showCustomNpcStyleDialog: false })
    try {
      var bgm = this.$refs.roomBgm
      if (bgm) {
        bgm.pause()
        bgm.removeAttribute('src')
      }
    } catch (e) { /* ignore */ }
    this.shutdownGameWsPermanently()
    if (this.pollTimer) clearInterval(this.pollTimer)
    if (this.backupPollTimer) clearInterval(this.backupPollTimer)
    if (this.heartbeatTimer) clearInterval(this.heartbeatTimer)
    if (this.actionTimer) clearInterval(this.actionTimer)
    if (this.readyTimer) clearInterval(this.readyTimer)
    if (this.communityCardsFlipCompleteTimer) clearTimeout(this.communityCardsFlipCompleteTimer)
    if (this._gameUiThemeChangeTimer) clearTimeout(this._gameUiThemeChangeTimer)
    this.clearRetroShowdownTvPending()
    this._lastRoomApplyFingerprint = ''
    if (this._seatChatTimers) {
      var self = this
      Object.keys(this._seatChatTimers).forEach(function (k) {
        clearTimeout(self._seatChatTimers[k])
      })
      this._seatChatTimers = Object.create(null)
    }
    this.resetSeatEnterStateForRoom()
  },

  methods: {
    retroDesktopFxBlockReason: function () {
      if (this.gameUiTheme !== 'retro8bit') return 'wrong-theme'
      if (this.layoutTier === 'phone') return 'phone-layout'
      if (this.viewportWidth <= 600) return 'viewport-too-narrow'
      return null
    },
    logRetroFxGate: function (reason) {
      var fxOn = this.showRetroDesktopFx
      var animOn = this.useRetroDesktopAmbience
      var mountBlock = this.retroDesktopFxBlockReason()
      var blockers = []
      if (mountBlock) blockers.push(mountBlock)
      if (this.ecoMode) blockers.push('eco-mode-ON (turn off in top bar to enable glitch monster)')
      if (this.prefersReducedMotion) blockers.push('prefers-reduced-motion')
      dpRetroMonsterGateLog({
        reason: reason || 'gate',
        theme: this.gameUiTheme,
        layoutTier: this.layoutTier,
        vw: this.viewportWidth,
        showRetroDesktopFx: fxOn,
        blockReason: fxOn ? null : (mountBlock || 'unknown'),
        useRetroDesktopAmbience: animOn,
        animBlockReason: fxOn && !animOn
          ? (this.ecoMode ? 'eco-mode' : (this.prefersReducedMotion ? 'prefers-reduced-motion' : null))
          : null,
        eco: this.ecoMode,
        prm: this.prefersReducedMotion,
        retroGlitchSeq: this.retroGlitchSeq,
        pipelineMounted: fxOn,
        schedulerWouldRun: animOn,
        blockers: blockers.length ? blockers : null
      })
      if (!animOn && fxOn && this.ecoMode) {
        dpRetroMonsterGateLog({
          hint: 'eco mode blocks glitch scheduler + monster animation — disable eco in top bar'
        })
      }
    },
    logRetroMonsterMountState: function (reason) {
      dpRetroMonsterLogStartupHint()
      dpRetroMonsterLog('mount', {
        reason: reason,
        showRetroDesktopFx: this.showRetroDesktopFx,
        useRetroDesktopAmbience: this.useRetroDesktopAmbience,
        isRetroDesktopLayout: this.isRetroDesktopLayout,
        layoutTier: this.layoutTier,
        viewportWidth: this.viewportWidth,
        ecoMode: this.ecoMode,
        prefersReducedMotion: this.prefersReducedMotion,
        retroGlitchSeq: this.retroGlitchSeq
      })
    },
    onRetroGlitchBurst: function () {
      this.retroGlitchSeq++
      dpRetroMonsterLog('seq-increment', { retroGlitchSeq: this.retroGlitchSeq })
    },
    onGameKeydown: function (e) {
      if (this.gameUiTheme !== 'retro8bit') return
      var tag = (e.target && e.target.tagName) ? e.target.tagName.toLowerCase() : ''
      var isInput = tag === 'input' || tag === 'textarea' || tag === 'select' || (e.target && e.target.isContentEditable)

      // Ctrl+D 优先级：对局详情 → 音乐盒 → 对局历史 → 终端（仅非输入框时路由到面板）
      if (e.ctrlKey && e.key === 'd') {
        if (!isInput) {
          if (this.handHistoryDetailId != null) {
            e.preventDefault()
            this.onHandHistoryDetailClosed(); return
          }
          if (this.showMusicPlayer) {
            e.preventDefault()
            var mp = this.$refs.musicPlayer
            if (mp && typeof mp.onKey === 'function') { mp.onKey(e); return }
            this.showMusicPlayer = false; return
          }
          if (this.showHandHistoryPanel || this.showOpponentHandHistoryPanel) {
            e.preventDefault()
            var hh = this.resolveActiveHandHistoryViewer()
            if (hh && typeof hh.onKey === 'function') { hh.onKey(e); return }
            this.showHandHistoryPanel = false
            this.showOpponentHandHistoryPanel = false
            return
          }
        }
        return
      }

      // `~` 热键：toggle 终端（不在输入框内时）
      if (e.key === '`' || e.key === '~') {
        if (isInput) return
        // 如果对局详情开着，先关掉
        if (this.handHistoryDetailId != null) {
          e.preventDefault()
          this.onHandHistoryDetailClosed()
          return
        }
        // 如果音乐盒开着，先关音乐盒
        if (this.showMusicPlayer) {
          e.preventDefault()
          var mp2 = this.$refs.musicPlayer
          if (mp2 && typeof mp2.close === 'function') { mp2.close() }
          else { this.showMusicPlayer = false }
          return
        }
        // 如果历史对局开着，先关历史对局
        if (this.showHandHistoryPanel || this.showOpponentHandHistoryPanel) {
          e.preventDefault()
          var hh2 = this.resolveActiveHandHistoryViewer()
          if (hh2 && typeof hh2.close === 'function') { hh2.close() }
          else {
            this.showHandHistoryPanel = false
            this.showOpponentHandHistoryPanel = false
          }
          return
        }
        e.preventDefault()
        var cli = this.$refs.terminalCli
        if (cli && typeof cli.toggle === 'function') {
          cli.toggle()
        }
        return
      }

      // 路由按键到打开的面板（不在输入框内时）
      if (!isInput) {
        if (this.handHistoryDetailId != null) {
          var hd3 = this.$refs.handHistoryDetail
          if (hd3 && typeof hd3.onKey === 'function' && hd3.onKey(e)) return
        }
        if (this.showMusicPlayer) {
          var mp3 = this.$refs.musicPlayer
          if (mp3 && typeof mp3.onKey === 'function' && mp3.onKey(e)) return
        }
        if (this.showHandHistoryPanel || this.showOpponentHandHistoryPanel) {
          var hh3 = this.resolveActiveHandHistoryViewer()
          if (hh3 && typeof hh3.onKey === 'function' && hh3.onKey(e)) return
        }
      }
    },
    refocusTerminalIfOpen: function () {
      var cli = this.$refs.terminalCli
      if (cli && cli.open && typeof cli.focusInput === 'function') {
        var self = this
        this.$nextTick(function () { cli.focusInput() })
      }
    },
    fireCrtPopup: function (type, title, subtitle, detail, onComplete) {
      if (this.gameUiTheme !== 'retro8bit') {
        if (typeof onComplete === 'function') onComplete()
        return
      }
      var popup = this.$refs.crtEventPopup
      if (popup && typeof popup.trigger === 'function') {
        popup.trigger(type, title, subtitle, detail, onComplete)
      } else if (typeof onComplete === 'function') {
        onComplete()
      }
    },
    clearRetroShowdownTvPending: function () {
      if (this._retroShowdownTvFallbackTimer) {
        clearTimeout(this._retroShowdownTvFallbackTimer)
        this._retroShowdownTvFallbackTimer = null
      }
      this.retroShowdownTvPending = false
      this.retroShowdownFromStage = null
      this.retroBettingEconomySnapshot = null
    },
    /** retro8bit：下注街→摊牌时播放 TV 弹窗（纯 overlay，不改牌桌状态） */
    beginRetroShowdownTvSequence: function () {
      if (this.gameUiTheme !== 'retro8bit') return
      if (this.retroShowdownTvPending) return
      this.retroShowdownTvPending = true
      var self = this
      var finished = false
      function finishRetroShowdownTv() {
        if (finished) return
        finished = true
        self.clearRetroShowdownTvPending()
        self.$nextTick(function () {
          self.$forceUpdate()
          if (self.stage === 'settled') {
            self.startReadyCountdown()
          }
          self.$nextTick(function () {
            self.flushDeferredSeatChats()
          })
        })
      }
      if (this._retroShowdownTvFallbackTimer) {
        clearTimeout(this._retroShowdownTvFallbackTimer)
      }
      this._retroShowdownTvFallbackTimer = setTimeout(finishRetroShowdownTv, 3500)
      this.fireCrtPopup(
        'danger',
        'SHOWDOWN',
        '决胜时刻',
        '摊牌!',
        finishRetroShowdownTv
      )
    },
    onRetroStageTransition: function (newVal, oldVal) {
      if (this.gameUiTheme !== 'retro8bit') {
        this.retroStageNavReady = true
        return
      }
      if (!this.retroStageNavReady) {
        this.retroStageNavReady = true
        return
      }
      if (newVal === 'preflop') {
        this.clearRetroShowdownTvPending()
        this._deferredSeatChats = []
        return
      }
      if (
        !this.retroShowdownTvPending
        && shouldRetroShowdownTvSequence(oldVal, newVal, this.retroStageNavReady)
      ) {
        this.retroShowdownFromStage = oldVal
        this.beginRetroShowdownTvSequence()
      }
    },
    onGameUiThemeChange(nextTheme) {
      var next = nextTheme || 'default'
      if (next === this.gameUiTheme) return
      if (this._gameUiThemeChangeTimer) {
        clearTimeout(this._gameUiThemeChangeTimer)
      }
      var self = this
      this._gameUiThemeChangeTimer = setTimeout(function () {
        self._gameUiThemeChangeTimer = null
        self.applyGameUiThemeChange(next)
      }, 80)
    },
    applyGameUiThemeChange(next) {
      var self = this
      var leavingRetro = this.gameUiTheme === 'retro8bit' && next !== 'retro8bit'
      var enteringRetro = this.gameUiTheme !== 'retro8bit' && next === 'retro8bit'
      if (leavingRetro) {
        this.teardownRetro8bitUi()
      }
      var commitTheme = function () {
        self.$store.commit('dpGame/SET_GAME_UI_THEME', next)
        self.$nextTick(function () {
          if (typeof self.scheduleTableFitUpdate === 'function') {
            self.scheduleTableFitUpdate()
            if (typeof requestAnimationFrame === 'function') {
              requestAnimationFrame(function () {
                self.scheduleTableFitUpdate()
              })
            }
          }
        })
      }
      if (leavingRetro) {
        this.$nextTick(function () {
          self.$nextTick(function () {
            if (typeof requestAnimationFrame === 'function') {
              requestAnimationFrame(commitTheme)
            } else {
              commitTheme()
            }
          })
        })
        return
      }
      // 切换到 retro8bit：播放终端启动动画
      if (enteringRetro) {
        var boot = self.$refs.crtBootSequence
        if (boot && typeof boot.play === 'function') {
          boot.play(function () {
            commitTheme()
          })
          return
        }
      }
      commitTheme()
    },
    /** 离开 retro8bit 前统一关闭特效/面板，再 nextTick×2 + rAF 切主题，避免 v-if 与 overlay 竞态 */
    teardownRetro8bitUi() {
      this.clearRetroShowdownTvPending()
      this._deferredSeatChats = []
      this.$store.commit('dpGame/SET_HERO_HAND_HOLOGRAM', false)
      this.$store.commit('dpGame/CLOSE_OWNER_HUB')
      this.$store.commit('dpGame/SET_MOBILE_SHEETS', {
        showMobileHandSheet: false,
        showMobileActionSheet: false
      })
      this.ownerTerminalOpen = false
      this.inviteFriendOpen = false
      this.showOwnerPotJudgeSheet = false
      this.joinRevealNicks = {}
      this.resetRetroChatReveal()
      var holo = this.$refs.heroHandHologram
      if (holo && typeof holo.forceTeardown === 'function') {
        holo.forceTeardown()
      }
    },
    resetRetroChatReveal() {
      var footer = this.$refs.heroDockFooter
      if (!footer || !footer.$refs) return
      var refs = ['guideRoomChatPanel', 'guideMobileRoomChatPanel']
      for (var i = 0; i < refs.length; i++) {
        var panel = footer.$refs[refs[i]]
        if (panel && typeof panel.closeForGuide === 'function') {
          panel.closeForGuide()
        }
      }
    },
    initHologramViewportListeners() {
      if (this._hologramViewportReady) return
      this._hologramViewportReady = true
      this.prefersReducedMotion = this.readPrefersReducedMotion()
      if (typeof window !== 'undefined') {
        this.viewportWidth = window.innerWidth
        window.addEventListener('resize', this.onHologramResize)
        if (window.matchMedia) {
          this._hologramPrmMedia = window.matchMedia('(prefers-reduced-motion: reduce)')
          if (this._hologramPrmMedia.addEventListener) {
            this._hologramPrmMedia.addEventListener('change', this.onHologramPrmChange)
          } else if (this._hologramPrmMedia.addListener) {
            this._hologramPrmMedia.addListener(this.onHologramPrmChange)
          }
        }
      }
    },
    teardownHologramViewportListeners() {
      if (!this._hologramViewportReady) return
      this._hologramViewportReady = false
      if (typeof window !== 'undefined') {
        window.removeEventListener('resize', this.onHologramResize)
      }
      if (this._hologramResizeTimer) {
        clearTimeout(this._hologramResizeTimer)
        this._hologramResizeTimer = null
      }
      if (this._hologramPrmMedia) {
        if (this._hologramPrmMedia.removeEventListener) {
          this._hologramPrmMedia.removeEventListener('change', this.onHologramPrmChange)
        } else if (this._hologramPrmMedia.removeListener) {
          this._hologramPrmMedia.removeListener(this.onHologramPrmChange)
        }
        this._hologramPrmMedia = null
      }
    },
    readPrefersReducedMotion() {
      if (typeof window === 'undefined' || !window.matchMedia) return false
      return window.matchMedia('(prefers-reduced-motion: reduce)').matches
    },
    onHologramPrmChange() {
      this.prefersReducedMotion = this.readPrefersReducedMotion()
    },
    onHologramResize() {
      var self = this
      if (this._hologramResizeTimer) clearTimeout(this._hologramResizeTimer)
      this._hologramResizeTimer = setTimeout(function () {
        self.viewportWidth = window.innerWidth
      }, 150)
    },
    onHeroViewHandClick(source) {
      try {
        dpHandHologramDevLog('click received', {
          source: source || 'unknown',
          theme: this.gameUiTheme,
          viewportWidth: this.viewportWidth,
          useRetroHandHologramWide: this.useRetroHandHologramWide,
          showHeroViewHandButton: this.showHeroViewHandButton,
          heroDockRowPresent: !!this.heroDockRow,
          heroHoleDealIntroDone: this.heroHoleDealIntroDone,
          stage: this.stage,
          layoutFullscreen: this.layoutFullscreen,
          isFullscreen: this.isFullscreen,
          pseudoFullscreen: this.pseudoFullscreen,
          showHeroHandHologram: this.showHeroHandHologram
        })
        if (!this.showHeroViewHandButton) {
          dpHandHologramDevLog('blocked: showHeroViewHandButton is false', {
            heroDockRowPresent: !!this.heroDockRow,
            stage: this.stage,
            heroHoleDealIntroDone: this.heroHoleDealIntroDone
          })
          return
        }
        if (!this.heroDockRow) {
          dpHandHologramDevLog('blocked: heroDockRow is null')
          return
        }
        if (this.useRetroHandHologramWide) {
          if (this.showHeroHandHologram) {
            dpHandHologramDevLog('branch: close hologram')
            this.$store.commit('dpGame/SET_HERO_HAND_HOLOGRAM', false)
            return
          }
          dpHandHologramDevLog('branch: open hologram (wide retro8bit)')
          this.$store.commit('dpGame/SET_HERO_HAND_HOLOGRAM', true)
          var self = this
          this.$nextTick(function () {
            self.$nextTick(function () {
              self.verifyHeroHandHologramOpen()
            })
          })
          return
        }
        dpHandHologramDevLog('branch: open mobile hand sheet', {
          reason: this.gameUiTheme !== 'retro8bit'
            ? 'theme is not retro8bit'
            : 'viewportWidth <= 600'
        })
        this.$store.commit('dpGame/SET_MOBILE_SHEETS', { showMobileHandSheet: true })
      } catch (e) {
        dpHandHologramDevLog('onHeroViewHandClick threw', { error: String(e && e.message ? e.message : e) })
        if (process.env.NODE_ENV !== 'production') {
          console.warn('[dp-game] onHeroViewHandClick failed', e)
        }
        this.fallbackHeroHandSheet('click handler exception')
      }
    },
    fallbackHeroHandSheet(reason) {
      dpHandHologramDevLog('fallback sheet', { reason: reason || 'unspecified' })
      this.$store.commit('dpGame/SET_HERO_HAND_HOLOGRAM', false)
      this.$store.commit('dpGame/SET_MOBILE_SHEETS', { showMobileHandSheet: true })
    },
    verifyHeroHandHologramOpen() {
      if (!this.showHeroHandHologram) {
        dpHandHologramDevLog('verify skipped: showHeroHandHologram already false')
        return
      }
      var holo = this.$refs.heroHandHologram
      if (!holo) {
        dpHandHologramDevLog('fallback sheet: heroHandHologram ref missing after open')
        this.fallbackHeroHandSheet('ref missing after open')
        return
      }
      if (typeof holo.isShowing !== 'function') {
        dpHandHologramDevLog('fallback sheet: isShowing() not available on ref')
        this.fallbackHeroHandSheet('isShowing missing')
        return
      }
      if (!holo.isShowing()) {
        dpHandHologramDevLog('fallback sheet: isShowing() false after open', {
          phase: holo.hologramPhase
        })
        this.fallbackHeroHandSheet('isShowing false after open')
        return
      }
      dpHandHologramDevLog('open confirmed: hologram is showing', {
        phase: holo.hologramPhase
      })
    },
    /**
     * 离开房间时多处可能同时触发跳转（WS roomClosed + 轮询 getNowRoom 为空、热更新等）；
     * Vue Router 3 对重复 push 同一地址会抛 NavigationDuplicated，需吞掉或跳过。
     */
    navigateHomeIfNeeded() {
      if (this.$route.path === '/home') return Promise.resolve()
      return this.$router.push('/home').catch(function (err) {
        if (err && err.name === 'NavigationDuplicated') return
        throw err
      })
    },

    // ---- 心跳（独立，不依赖 loadGame） ----
    sendHeartbeat() {
      if (!this.user) return
      this.$http.post('/dpRoom/heartbeat', null, {
        params: this.roomApiParams
      }).catch(function (e) {
        console.error('心跳失败', e)
      })
    },

    gameWsBaseUrl() {
      // Electron 桌面客户端：连到 config.json 配置的服务器地址
      if (typeof window !== 'undefined' && window.dpElectron && window.dpElectron.serverUrl) {
        var url = window.dpElectron.serverUrl.replace(/\/+$/, '')
        return url.replace(/^https?:/, url.indexOf('https:') === 0 ? 'wss:' : 'ws:')
      }
      // 与页面同源；开发时游戏 WS 走 vue.config.js 的 /dp-ws → 后端 /ws
      var secure = window.location.protocol === 'https:'
      return (secure ? 'wss:' : 'ws:') + '//' + window.location.host
    },

    clearWsReconnectTimer() {
      if (this.wsReconnectTimer != null) {
        clearTimeout(this.wsReconnectTimer)
        this.wsReconnectTimer = null
      }
    },

    /**
     * 永久关闭 WS（离房、解散、组件销毁）：取消重连并摘掉回调，避免 onclose 再 schedule。
     */
    shutdownGameWsPermanently() {
      this.wsNoReconnect = true
      this.clearWsReconnectTimer()
      this.gameWsSession++
      var w = this.gameWs
      this.gameWs = null
      this.gameWsConnected = false
      if (w) {
        w.onopen = null
        w.onclose = null
        w.onerror = null
        w.onmessage = null
        try {
          w.close()
        } catch (e) { /* ignore */ }
      }
    },

    /**
     * 主动离房：在 exitRoom 之前关掉推送/轮询，并标记已处理 roomClosed，避免弹「房间已解散」。
     */
    beginIntentionalLeave() {
      this._dpRoomClosedHandled = true
      this.shutdownGameWsPermanently()
      if (this.pollTimer) {
        clearInterval(this.pollTimer)
        this.pollTimer = null
      }
      if (this.backupPollTimer) {
        clearInterval(this.backupPollTimer)
        this.backupPollTimer = null
      }
      if (this.heartbeatTimer) {
        clearInterval(this.heartbeatTimer)
        this.heartbeatTimer = null
      }
    },

    scheduleWsReconnect(sessionAtOpen) {
      var self = this
      if (self.wsNoReconnect) return
      if (self.gameWsSession !== sessionAtOpen) return
      var exp = Math.min(5, self.wsReconnectAttempt)
      var delay = Math.min(30000, 1000 * Math.pow(2, exp))
      var jitter = Math.floor(Math.random() * 400)
      self.wsReconnectAttempt++
      self.clearWsReconnectTimer()
      self.wsReconnectTimer = setTimeout(function () {
        self.wsReconnectTimer = null
        if (self.wsNoReconnect) return
        if (self.gameWsSession !== sessionAtOpen) return
        var g = self.gameWs
        if (g && (g.readyState === WebSocket.OPEN || g.readyState === WebSocket.CONNECTING)) return
        self.connectGameWs()
      }, delay + jitter)
    },

    connectGameWs() {
      var self = this
      if (self.wsNoReconnect) return

      self.clearWsReconnectTimer()

      self.gameWsSession++
      var sessionAtOpen = self.gameWsSession

      if (self.gameWs) {
        var old = self.gameWs
        self.gameWs = null
        old.onopen = null
        old.onclose = null
        old.onerror = null
        old.onmessage = null
        try {
          old.close()
        } catch (e) { /* ignore */ }
      }
      self.gameWsConnected = false

      // 开发服：走 /dp-ws → vue 代理转成后端 /ws（避免与 webpack HMR 的 /ws 冲突）
      var path = process.env.NODE_ENV === 'development' ? '/dp-ws/dp-game' : '/ws/dp-game'
      var tok = self.user && self.user.token ? String(self.user.token) : ''
      var url = self.gameWsBaseUrl() + path + '?roomId=' + encodeURIComponent(self.roomId)
        + (tok ? '&token=' + encodeURIComponent(tok) : '')
      try {
        var ws = new WebSocket(url)
        self.gameWs = ws
        ws.onopen = function () {
          if (self.gameWsSession !== sessionAtOpen || self.wsNoReconnect) {
            try {
              ws.close()
            } catch (err) { /* ignore */ }
            return
          }
          self.gameWsConnected = true
          self.wsReconnectAttempt = 0
          self.fetchRoomChatRecent()
        }
        ws.onmessage = function (ev) {
          try {
            var data = JSON.parse(ev.data)
            if (data._ws === 'roomClosed') {
              self.handleRoomClosedFromServer()
              return
            }
            if (data._ws === 'chat') {
              self.pushRoomChatFromServer(data)
              return
            }
            if (data._ws === 'roomMusic') {
              self.applyRoomMusicMessage(data)
              return
            }
            if (data._ws === 'npcDecisionTraceHand') {
              self.onNpcDecisionTraceHandPush(data)
              return
            }
            self.applyRoomFromServer(data)
          } catch (e) {
            console.error('WebSocket 消息解析失败', e)
          }
        }
        ws.onclose = function () {
          if (self.gameWsSession !== sessionAtOpen) return
          self.gameWsConnected = false
          if (self.gameWs === ws) self.gameWs = null
          if (!self.wsNoReconnect) self.scheduleWsReconnect(sessionAtOpen)
        }
        ws.onerror = function (e) {
          console.warn('WebSocket 错误（将按退避重试）', e)
        }
      } catch (e) {
        console.error('WebSocket 连接失败', e)
        if (!self.wsNoReconnect && self.gameWsSession === sessionAtOpen) {
          self.scheduleWsReconnect(sessionAtOpen)
        }
      }
    },

    handleRoomClosedFromServer() {
      if (this._dpRoomClosedHandled) return
      this._dpRoomClosedHandled = true
      this.$store.commit('dpGame/RESET_ON_ROOM_CLOSED')
      this._lastRoomBgmUrl = ''
      this._lastRoomMusicWebPath = ''
      try {
        var bgm = this.$refs.roomBgm
        if (bgm) {
          bgm.pause()
          bgm.removeAttribute('src')
        }
      } catch (e) { /* ignore */ }
      var self = this
      this.shutdownGameWsPermanently()
      if (this.pollTimer) clearInterval(this.pollTimer)
      if (this.backupPollTimer) clearInterval(this.backupPollTimer)
      if (this.heartbeatTimer) clearInterval(this.heartbeatTimer)
      this.$alert('房间已解散或你已被移出', '提示', {
        confirmButtonText: '确定',
        type: 'warning'
      }).then(function () {
        return self.navigateHomeIfNeeded()
      }).catch(function () {
        return self.navigateHomeIfNeeded()
      })
    },

    formatChatNick(name) {
      return dpDisplayNickname(name || '')
    },

    seatChatTextFor(nickname) {
      if (!nickname) return ''
      var m = this.seatChatTextByNick
      return (m && m[nickname]) ? m[nickname] : ''
    },

    applyRoomMusicMessage(data) {
      if (!data || data._ws !== 'roomMusic') return
      this.$store.commit('dpGame/SET_ROOM_MUSIC_STATE', {
        action: data.action,
        trackId: data.trackId,
        webPath: data.webPath,
        displayName: data.displayName,
        byNickname: data.byNickname,
        serverTime: data.serverTime
      })
      var wp = data.webPath != null ? String(data.webPath).trim() : ''
      if (wp) this._lastRoomMusicWebPath = wp
      var self = this
      this.$nextTick(function () {
        self.syncRoomBgmAudio()
      })
    },

    /**
     * 根据 {@link #roomMusicState} 与当前阶段驱动隐藏 {@code <audio>}；摊牌/结算时暂停以免与结算 BGM 叠播。
     */
    syncRoomBgmAudio() {
      var el = this.$refs.roomBgm
      if (!el) return
      var st = this.stage
      if (st === 'showdown' || st === 'settled') {
        try {
          el.pause()
        } catch (e) { /* ignore */ }
        return
      }
      var m = this.roomMusicState
      if (!m || !m.action) return
      var a = m.action
      if (a === 'stop') {
        try {
          el.pause()
          el.currentTime = 0
          el.removeAttribute('src')
        } catch (e) { /* ignore */ }
        this._lastRoomBgmUrl = ''
        this._lastRoomMusicWebPath = ''
        return
      }
      if (a === 'pause') {
        try {
          el.pause()
        } catch (e) { /* ignore */ }
        return
      }
      if (a !== 'play' || !m.webPath) return
      var url = musicFileSrc(m.webPath)
      if (this._lastRoomBgmUrl !== url) {
        this._lastRoomBgmUrl = url
        el.src = url
      }
      var p = el.play()
      if (p && typeof p.then === 'function') {
        p.catch(function () { /* 未与页面交互时部分浏览器会拒绝自动播放 */ })
      }
    },

    sendRoomMusicSync(payload) {
      if (!this.user) return
      if (!this.gameWs || this.gameWs.readyState !== WebSocket.OPEN) {
        this.$message.warning('未连接房间推送，请稍候再试')
        return
      }
      var action = payload.action
      var webPath = (payload.webPath != null ? String(payload.webPath) : '').trim()
      var displayName = (payload.displayName != null ? String(payload.displayName) : '').trim()
      if ((action === 'pause' || action === 'stop') && !webPath) {
        var rm = this.roomMusicState
        webPath = (rm && rm.webPath) ? String(rm.webPath).trim() : ''
        if (!webPath) webPath = (this._lastRoomMusicWebPath || '').trim()
        if (!displayName && rm && rm.displayName) {
          displayName = String(rm.displayName).trim()
        }
      }
      if (action === 'play' && webPath) {
        this._lastRoomMusicWebPath = webPath
      }
      var body = {
        _ws: 'roomMusicSync',
        action: action,
        trackId: payload.trackId != null ? payload.trackId : 0,
        webPath: webPath,
        displayName: displayName
      }
      try {
        this.gameWs.send(JSON.stringify(body))
      } catch (e) {
        console.error('roomMusicSync', e)
        this.$message.error('发送失败')
      }
    },

    async loadMusicTracks() {
      this.$store.commit('dpGame/SET_MUSIC_TRACKS', { loading: true, error: '' })
      try {
        var res = await this.$http.get('/dpMusic/list')
        this.$store.commit('dpGame/SET_MUSIC_TRACKS', {
          tracks: Array.isArray(res.data) ? res.data : [],
          loading: false,
          error: ''
        })
      } catch (e) {
        console.error('dpMusic/list', e)
        this.$store.commit('dpGame/SET_MUSIC_TRACKS', {
          tracks: [],
          loading: false,
          error: CAT_COPY.musicListLoadFailed
        })
      }
    },

    normalizeRoomChatRow(data, nick, text) {
      var id = data.id != null ? String(data.id) : ''
      if (!id) id = String(Date.now()) + '-' + Math.random().toString(36).slice(2, 8)
      return {
        id: id,
        nickname: nick,
        text: text,
        serverTime: data.serverTime != null ? Number(data.serverTime) : Date.now(),
        senderUserId: data.senderUserId != null ? data.senderUserId : null
      }
    },

    /** 进房 / 换房：清空上一局的聊天列表与座位气泡（NPC 桌边话仅存前端，不落库） */
    resetRoomChatUiForEnter() {
      this.$store.commit('dpGame/CLEAR_ROOM_CHAT_MESSAGES')
      this.$store.commit('dpGame/CLEAR_ALL_SEAT_CHAT')
      if (this._seatChatTimers) {
        var self = this
        Object.keys(this._seatChatTimers).forEach(function (k) {
          clearTimeout(self._seatChatTimers[k])
        })
        this._seatChatTimers = Object.create(null)
      }
    },

    resetSeatEnterStateForRoom() {
      this._seatEnterNickSeeded = false
      this._seatEnterNickSnapshot = new Set()
      this.joinRevealNicks = {}
      dpSeatEnterDevLog('reset')
    },

    syncSeatEnterRevealFromRoom(room) {
      var nextNicks = extractPlayerNicknames(room && room.players)
      if (!this._seatEnterNickSeeded) {
        this._seatEnterNickSnapshot = new Set(nextNicks)
        this._seatEnterNickSeeded = true
        dpSeatEnterDevLog('seed', { count: nextNicks.length, nicks: nextNicks })
        return
      }
      var added = diffNewSeatNicknames(this._seatEnterNickSnapshot, nextNicks)
      if (added.length && !this.useRetroSeatEnterReveal) {
        dpSeatEnterDevLog('gate-off', { added: added })
      }
      if (added.length && this.useRetroSeatEnterReveal) {
        var self = this
        added.forEach(function (nick) {
          self.$set(self.joinRevealNicks, nick, true)
        })
      }
      this._seatEnterNickSnapshot = new Set(nextNicks)
      dpSeatEnterDevLog('diff', { added: added, skipped: !this.useRetroSeatEnterReveal })
    },

    onSeatEnterRevealDone(nick) {
      this.$delete(this.joinRevealNicks, nick)
      dpSeatEnterDevLog('done', { nick: nick })
    },

    currentActingNicknameForSeatChat: function () {
      if (!this.actionCountdownShouldRun()) return null
      var list = this.players
      var i = this.actIndex
      if (i < 0 || !list || i >= list.length) return null
      var p = list[i]
      return p && p.nickname ? p.nickname : null
    },

    maybeBeginRetroShowdownTvForDeferredNpcChat: function () {
      if (this.gameUiTheme !== 'retro8bit' || this.retroShowdownTvPending) return
      if (!isRetroBettingStage(this.stage) && !isRetroRevealStage(this.stage)) return
      if (!this.retroShowdownFromStage) {
        this.retroShowdownFromStage = isRetroBettingStage(this.stage) ? this.stage : 'river'
      }
      this.beginRetroShowdownTvSequence()
    },

    pushRoomChatFromServer(data) {
      var nick = (data.nickname || '').trim()
      var text = (data.text != null ? String(data.text) : '').trim()
      if (!nick || !text) return
      var deferNpcRetro = this.gameUiTheme === 'retro8bit' && isDpBotNickname(nick)
        && shouldDeferRetroNpcSeatChat({
          isNpc: true,
          stage: this.stage,
          tvPending: this.retroShowdownTvPending,
          presentationFrozen: this.settlePresentationFrozen,
          playing: this.playing,
          actionCountdownActive: this.actionCountdownShouldRun(),
          actingNickname: this.currentActingNicknameForSeatChat(),
          chatNickname: nick
        })
      if (this.settlePresentationFrozen || deferNpcRetro) {
        if (!this._deferredSeatChats) this._deferredSeatChats = []
        this._deferredSeatChats.push(data)
        if (deferNpcRetro) {
          this.maybeBeginRetroShowdownTvForDeferredNpcChat()
        }
        return
      }
      this.applySeatChatFromServer(data, nick, text)
    },

    flushDeferredSeatChats: function () {
      if (!this._deferredSeatChats || !this._deferredSeatChats.length) return
      var queue = this._deferredSeatChats.slice()
      this._deferredSeatChats = []
      for (var i = 0; i < queue.length; i++) {
        var data = queue[i]
        var nick = (data.nickname || '').trim()
        var text = (data.text != null ? String(data.text) : '').trim()
        if (!nick || !text) continue
        this.applySeatChatFromServer(data, nick, text)
      }
    },

    applySeatChatFromServer(data, nick, text) {
      this.$store.commit('dpGame/APPEND_ROOM_CHAT_MESSAGE', this.normalizeRoomChatRow(data, nick, text))
      var ttl = typeof data.ttlMs === 'number' && data.ttlMs > 0 ? data.ttlMs : 15000
      var prev = this._seatChatTimers[nick]
      if (prev) {
        clearTimeout(prev)
        delete this._seatChatTimers[nick]
      }
      this.$store.commit('dpGame/SET_SEAT_CHAT', { nick: nick, text: text })
      var self = this
      var tid = setTimeout(function () {
        if (self.seatChatTextByNick[nick] === text) {
          self.$store.commit('dpGame/DELETE_SEAT_CHAT', nick)
        }
        delete self._seatChatTimers[nick]
      }, ttl)
      this._seatChatTimers[nick] = tid
    },

    playerEconomyForDisplay(player) {
      return resolvePlayerEconomyDisplay(
        player,
        this.retroBettingEconomySnapshot,
        this.settlePresentationFrozen
      )
    },

    /**
     * 拉取最近房间聊天记录（HTTP 进房补全；不落座位气泡）
     */
    async fetchRoomChatRecent() {
      if (!this.roomId || !this.$http) return
      var api = dpRoomApi(this.$http)
      try {
        var res = await api.recentChat(this.roomId, { limit: 50 })
        var body = res.data
        if (!dpResultSuccess(body)) return
        var d = dpResultData(body) || {}
        var items = Array.isArray(d.items) ? d.items : []
        var rows = items
          .map(function (row) {
            var nick = (row.nickname || '').trim()
            var text = row.text != null ? String(row.text).trim() : ''
            if (!nick || !text) return null
            return {
              id: row.id != null ? String(row.id) : '',
              nickname: nick,
              text: text,
              serverTime: row.serverTime != null ? Number(row.serverTime) : 0,
              senderUserId: row.senderUserId != null ? row.senderUserId : null
            }
          })
          .filter(Boolean)
        this.$store.commit('dpGame/REPLACE_ROOM_CHAT_MESSAGES', rows)
      } catch (e) {
        console.warn('fetchRoomChatRecent', e)
      }
    },

    sendRoomChat() {
      var t = (this.chatInputDraft || '').trim()
      if (!t) return
      if (!this.user) return
      if (!this.gameWs || this.gameWs.readyState !== WebSocket.OPEN) {
        this.$message.warning('未连接房间推送，请稍候再试')
        return
      }
      if (t.length > 200) {
        this.$message.warning('单条最多 200 字')
        return
      }
      try {
        this.gameWs.send(JSON.stringify({
          _ws: 'chatSend',
          text: t
        }))
        this.$store.commit('dpGame/SET_CHAT_DRAFT', '')
      } catch (e) {
        console.error('发送聊天失败', e)
        this.$message.error('发送失败')
      }
    },

    applyRoomFromServer(room) {
      this.syncSeatEnterRevealFromRoom(room)
      if (room) {
        this.$store.commit('dpGame/SYNC_ACTION_COUNTDOWN_FIELDS', room)
        if (this.gameUiTheme === 'retro8bit' && isRetroBettingStage(room.currentStage)) {
          this.retroBettingEconomySnapshot = captureBettingEconomySnapshot(room)
        }
      }
      var fp = encodeRoomApplyFingerprint(room)
      if (fp && fp === this._lastRoomApplyFingerprint) {
        this.$nextTick(function () {
          this.syncActionCountdown()
          if (this.isMyTurn && this.raiseAmount < this.minRaise) {
            this.$store.commit('dpGame/SET_RAISE_AMOUNT', this.minRaise)
          }
        }.bind(this))
        return
      }
      this._lastRoomApplyFingerprint = fp
      this.$store.commit('dpGame/APPLY_ROOM', room)
      this.syncCommunityCardsFlipState(room.communityCards || [])
      this.$nextTick(function () {
        this.syncActionCountdown()
        if (this.isMyTurn && this.raiseAmount < this.minRaise) {
          this.$store.commit('dpGame/SET_RAISE_AMOUNT', this.minRaise)
        }
      }.bind(this))
    },

    /** eco 或系统「减少动态效果」：公共牌翻面走短定时，与 CSS 瞬时翻转对齐 */
    prefersReducedMotionForFlip() {
      try {
        return window.matchMedia('(prefers-reduced-motion: reduce)').matches
      } catch (e) {
        return false
      }
    },

    // ---- 拉取房间状态 ----
    async loadGame() {
      this.$store.commit('dpGame/SET_LOADING', true)
      try {
        var res = await this.$http.get('/dpRoom/getNowRoom', {
          params: this.roomApiParams
        })
        var room = res.data
        if (!room) {
          if (!this.wsNoReconnect) {
            this.handleRoomClosedFromServer()
          }
          return
        }
        this.applyRoomFromServer(room)
      } catch (err) {
        console.error('拉取状态失败', err)
      } finally {
        this.$store.commit('dpGame/SET_LOADING', false)
      }
    },

    // ---- 准备/取消准备（与 readyNextHand 一致：ok 才 commit 本地态、提示、loadGame）----
    async toggleReady() {
      if (!this.user) return { ok: false, message: '未登录' }
      var wasReady = this.myReady
      try {
        var res = await this.$http.post('/dpRoom/toggleReady', null, {
          params: this.roomApiParams
        })
        if (res.data === 'ok') {
          this.$store.commit('dpGame/PATCH_MY_PLAYER_READY', !wasReady)
          if (wasReady) {
            this.$message.success('已取消准备')
          } else {
            this.$message.success('已准备下一局')
          }
          await this.loadGame()
          return { ok: true, wasReady: wasReady }
        }
        this.$message.error('操作失败：' + res.data)
        return { ok: false, message: String(res.data) }
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
        return { ok: false, message: err.message || '网络错误' }
      }
    },
    // 结算后积分归零时补满
    async rebuy() {
      try {
        var res = await this.$http.post('/dpRoom/rebuy', null, {
          params: { roomId: this.roomId }
        })
        if (res.data !== 'ok') {
          this.$message.error('补满失败：' + res.data)
          return { ok: false, message: String(res.data) }
        }
        this.$message.success('补满成功，可在结算阶段准备下一局')
        await this.loadGame()
        return { ok: true }
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
        return { ok: false, message: err.message || '网络错误' }
      }
    },

    // ---- 跟投/观望 ----
    async doCall() {
      await this.submitBet(this.callAmount)
    },

    // ---- 加投 ----
    async doRaise() {
      if (this.raiseAmount < this.minRaise) {
        this.$message.warning(
          '加投不能低于 ' + this.minRaise + '（总投入至少到 ' + this.minTotalToRaise + '）'
        )
        return
      }
      if (this.raiseAmount > this.myChips) {
        this.$message.warning('小鱼干不足！')
        return
      }
      await this.submitBet(this.raiseAmount)
    },

    // ---- All-In ----
    async doAllIn() {
      await this.submitBet(this.myChips)
    },

    // ---- 统一提交本轮投入（接口字段名仍为 bet）----
    async submitBet(amount) {
      try {
        var res = await this.$http.post('/dpRoom/bet', null, {
          params: { roomId: this.roomId, bet: amount }
        })
        if (res.data !== 'ok') this.$message.error('投入失败，请检查数额')
        this.$store.commit('dpGame/SET_RAISE_AMOUNT', 0)
        await this.loadGame()
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },

    // ---- 盖牌 ----
    async doFold() {
      try {
        var res = await this.$http.post('/dpRoom/fold', null, {
          params: { roomId: this.roomId }
        })
        if (res.data !== 'ok') this.$message.error('盖牌失败')
        await this.loadGame()
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },

    // ---- 主动离座：将自己移到观众席 ----
    async doLeaveSeat() {
      if (!this.user || !this.user.nickname) return
      try {
        await this.dpConfirm('确定主动离座并进入观众席吗？', '主动离座', {
          confirmButtonText: '确认离座',
          cancelButtonText: '取消'
        })
      } catch (e) {
        return
      }
      try {
        var res = await this.$http.post('/dpRoom/kickPlayer', null, {
          params: { roomId: this.roomId, nickname: this.user.nickname }
        })
        if (res.data !== 'ok') {
          this.$message.error('离座失败：' + res.data)
          return
        }
        this.$message.success('你已离座，当前为观众席状态')
        this.$store.commit('dpGame/SET_MOBILE_SHEETS', {
          showMobileHandSheet: false,
          showMobileActionSheet: false
        })
        this.$store.commit('dpGame/SET_HERO_HAND_HOLOGRAM', false)
        await this.loadGame()
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },

    // ---- 摊牌阶段：点击玩家卡片选/取消赢家（简单模式备用） ----
    handleJudgeClick(nickname) {
      if (!this.isOwner || this.stage !== 'showdown') return
      // 有边池数据时，不用这个旧的点击方式
      if (this.pots.length > 0) return

      this.$store.commit('dpGame/TOGGLE_SELECTED_WINNER', nickname)
    },

    onInviteFriendClick() {
      if (!this.canInviteFriend) {
        dpInviteFriendsDevLog('blocked', { canInviteFriend: false })
        return
      }
      dpInviteFriendsDevLog('top bar click', {
        theme: this.gameUiTheme,
        viewportWidth: this.viewportWidth,
        useRetroInvitePanelWide: this.useRetroInvitePanelWide,
        inviteFriendOpen: this.inviteFriendOpen,
        canInviteFriend: this.canInviteFriend
      })
      if (this.useRetroInvitePanelWide) {
        if (this.inviteFriendOpen) {
          dpInviteFriendsDevLog('branch', 'close panel')
          this.closeInviteFriendSheet()
        } else {
          dpInviteFriendsDevLog('branch', 'open panel')
          this.inviteFriendOpen = true
        }
      } else {
        dpInviteFriendsDevLog('branch', this.inviteFriendOpen ? 'close sheet' : 'open sheet')
        this.inviteFriendOpen = !this.inviteFriendOpen
      }
      this.scheduleReparentElementUiLayersIntoFullscreenRoot()
    },
    closeInviteFriendSheet() {
      this.inviteFriendOpen = false
    },
    onFriendChatClick() {
      if (this.useRetroFriendChatPanelWide) {
        this.friendChatPickerOpen = !this.friendChatPickerOpen
      } else {
        this.friendChatPickerOpen = !this.friendChatPickerOpen
      }
      this.scheduleReparentElementUiLayersIntoFullscreenRoot()
    },
    closeFriendChatPicker() {
      this.friendChatPickerOpen = false
    },
    friendChatDisplayName(f) {
      return dpSocialDisplayNickname(f && f.nickname, f && f.userId, '未知好友')
    },
    openFriendChatFromPicker(f) {
      var uid = f && f.userId != null ? Number(f.userId) : 0
      if (!isFinite(uid) || uid <= 0) return
      this.friendChatPickerOpen = false
      this.friendChatPeerId = uid
      this.friendChatPeerName = this.friendChatDisplayName(f)
      this.friendChatPeerAvatar = (f && f.avatarUrl) || ''
      this.friendChatPeerAvatarUpdatedAt = f && f.avatarUpdatedAt != null ? f.avatarUpdatedAt : null
      this.friendChatPeerUnread = this.friendUnreadForUser(uid)
      this.friendChatVisible = true
      this.scheduleReparentElementUiLayersIntoFullscreenRoot()
    },
    onFriendChatClosed() {
      this.friendChatPeerId = null
      this.friendChatPeerName = ''
      this.friendChatPeerAvatar = ''
      this.friendChatPeerAvatarUpdatedAt = null
      this.friendChatPeerUnread = 0
      this.$store.dispatch('dpMailbox/fetchFriendChatUnreadSummary', { http: this.$http }).catch(function () {})
    },
    closePlayerSocialSheet() {
      this.playerSocialOpen = false
      this.playerSocialTarget = null
    },
    closeNpcMoodSheet() {
      this.npcMoodOpen = false
      this.npcMoodTarget = null
    },
    /**
     * 规则 / LLM bot：打开情绪面板（规则 bot 展示 mood；LLM 仅提示无情绪）。
     * @param {{ nickname: string }} payload
     */
    openNpcMoodSheet(payload) {
      var nickname = payload && payload.nickname
      if (!nickname) return
      var player = null
      var players = this.players || []
      for (var i = 0; i < players.length; i++) {
        if (players[i] && players[i].nickname === nickname) {
          player = players[i]
          break
        }
      }
      var isLlm = isDpLlmBotNickname(nickname)
      this.npcMoodTarget = {
        nickname: nickname,
        isLlm: isLlm,
        mood: player && player.mood != null ? Number(player.mood) : 0,
        moodState: player && player.moodState ? player.moodState : null
      }
      this.npcMoodOpen = true
    },
    /**
     * 从玩家信息底栏打开「与 TA 的共同历史对局」（独立弹层，数据走 checkUserAndOtherPlayerHandHistoryList）
     * @param {{ userId: number, displayName: string }} payload
     */
    openOpponentHandHistoryFromSocial(payload) {
      if (!payload || payload.userId == null || payload.userId === '') return
      var uid = Number(payload.userId)
      if (!uid || uid <= 0 || isNaN(uid)) return
      this.$store.commit('dpGame/SET_MODAL', {
        opponentHandHistoryOtherUserId: uid,
        opponentHandHistoryDisplayName: payload.displayName || ''
      })
      if (this.gameUiTheme === 'retro8bit') {
        this.showOpponentHandHistoryPanel = true
        return
      }
      this.$store.commit('dpGame/SET_MODAL', {
        showOpponentHandHistoryModal: true
      })
      this.scheduleReparentElementUiLayersIntoFullscreenRoot()
    },
    /**
     * @param {string|{nickname:string,userId?:number}} payload
     */
    onPlayerCardClick(payload) {
      var nickname = typeof payload === 'string'
        ? payload
        : (payload && payload.nickname)
      if (!nickname) return

      if (this.user && nickname === this.user.nickname) {
        return
      }

      if (this.isOwner && this.stage === 'showdown' && (!this.pots || this.pots.length === 0)) {
        this.handleJudgeClick(nickname)
        return
      }

      if (isDpRuleBotNickname(nickname) || isDpLlmBotNickname(nickname)) {
        this.openNpcMoodSheet({ nickname: nickname })
        return
      }

      var rawUid = typeof payload === 'object' && payload ? payload.userId : null
      this.openPlayerSocialProfile({ nickname: nickname, userId: rawUid })
    },
    /**
     * 观众席 / 邀请好友等入口：打开局内玩家资料（与点击桌上玩家卡片一致）。
     * @param {{ nickname: string, userId?: number|string }} payload
     */
    async openPlayerSocialProfile(payload) {
      var nickname = payload && payload.nickname
      if (!nickname) return

      if (this.user && nickname === this.user.nickname) {
        return
      }

      if (isDpRuleBotNickname(nickname) || isDpLlmBotNickname(nickname)) {
        this.openNpcMoodSheet({ nickname: nickname })
        return
      }

      var friends = (this.$store.state.dpMailbox && this.$store.state.dpMailbox.friends) || []
      var meta = resolveRoomPersonMeta({
        players: this.players,
        friends: friends,
        nickname: nickname,
        userId: payload && payload.userId
      })
      var uid = meta.userId

      if (!uid || uid <= 0 || isNaN(uid)) {
        try {
          var res = await dpSocialApi(this.$http).lookupUser(String(nickname))
          if (dpResultSuccess(res.data)) {
            var user = (dpResultData(res.data) || {}).user
            var looked = user && user.userId != null ? Number(user.userId) : 0
            if (looked > 0 && !isNaN(looked)) uid = looked
          }
        } catch (e) {
          /* 静默，沿用下方统一提示 */
        }
      }

      if (!uid || uid <= 0 || isNaN(uid)) {
        this.$message.warning('无法获取该玩家的账号信息，请对方使用已登录账号进房后再试')
        return
      }

      this.playerSocialTarget = { nickname: nickname, userId: uid }
      this.playerSocialOpen = true
    },

    // ---- 按池选赢家 ----
    togglePotWinner(potIndex, nickname) {
      var winners = (this.potWinners[potIndex] || []).slice()
      var idx = winners.indexOf(nickname)
      if (idx > -1) {
        winners.splice(idx, 1)
      } else {
        winners.push(nickname)
      }
      this.$store.commit('dpGame/SET_POT_WINNERS_AT', { potIndex: potIndex, winners: winners })
    },

    onTogglePotWinnerPayload(payload) {
      this.togglePotWinner(payload.potIndex, payload.nickname)
    },

    // ---- 按池确认结算 ----
    async confirmPotJudge() {
      // 拼接格式: "0:Alice,Bob;1:Charlie"
      var parts = []
      for (var i = 0; i < this.pots.length; i++) {
        var winners = this.potWinners[i] || []
        if (winners.length === 0) {
          this.$message.warning(dpPotDisplayLabel(i) + ' 还没选赢家')
          return
        }
        parts.push(i + ':' + winners.join(','))
      }
      var potWinnersStr = parts.join(';')

      // 组装确认信息（HTML 换行，避免原生 confirm 打断全屏）
      var lines = ['确认结算？']
      for (var j = 0; j < this.pots.length; j++) {
        var potName = dpPotDisplayLabel(j)
        lines.push(
          potName + '(' + this.pots[j].amount + ') -> '
          + (this.potWinners[j] || []).map(dpDisplayNickname).join(', ')
        )
      }
      var msgHtml = lines.join('<br/>')
      try {
        await this.dpConfirm(msgHtml, '确认结算', {
          confirmButtonText: '确定结算',
          dangerouslyUseHTMLString: true
        })
      } catch (e) {
        return
      }

      try {
        var res = await this.$http.post('/dpRoom/judgeWin', null, {
          params: {roomId: this.roomId, potWinners: potWinnersStr}
        })
        if (res.data !== 'ok') this.$message.error('结算失败')
        this.$store.commit('dpGame/CLEAR_JUDGE_SELECTION')
        await this.loadGame()
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },

    // ---- 房主：确认结算 ----
    async confirmJudgeWin() {
      if (this.selectedWinners.length === 0) {
        this.$message.warning('请至少选择一位赢家')
        return
      }
      var names = this.selectedWinners.map(dpDisplayNickname).join(', ')
      try {
        await this.dpConfirm('确定由 [' + names + '] 平分小鱼干池 ' + this.pot + ' 吗？', '确认结算')
      } catch (e) {
        return
      }

      try {
        var res = await this.$http.post('/dpRoom/judgeWin', null, {
          params: {roomId: this.roomId, winnerNickname: this.selectedWinners.join(',')}
        })
        if (res.data !== 'ok') this.$message.error('结算失败')
        this.$store.commit('dpGame/SET_SELECTED_WINNERS', [])
        await this.loadGame()
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },

    // ---- 房主神器：顶栏入口（宽 retro 终端 / 窄 legacy sheet） ----
    onOwnerHubClick() {
      if (!this.isOwner) {
        dpOwnerTerminalDevLog('blocked', { isOwner: false })
        return
      }
      dpOwnerTerminalDevLog('top bar click', {
        theme: this.gameUiTheme,
        viewportWidth: this.viewportWidth,
        useRetroOwnerPanelWide: this.useRetroOwnerPanelWide,
        ownerTerminalOpen: this.ownerTerminalOpen,
        ownerTouchSheetOpen: this.ownerTouchSheetOpen,
        isOwner: this.isOwner,
        stage: this.stage
      })
      if (this.gameUiTheme === 'retro8bit') {
        if (this.ownerTouchSheetOpen) {
          dpOwnerTerminalDevLog('branch', 'close touch sheet')
          this.closeOwnerTouchPanel()
        } else {
          dpOwnerTerminalDevLog('branch', 'open touch sheet')
          this.openOwnerTouchPanel()
        }
        this.scheduleReparentElementUiLayersIntoFullscreenRoot()
        return
      }
      if (this.showOwnerHubSheet) {
        dpOwnerTerminalDevLog('branch', 'close legacy sheet')
        this.closeOwnerHubPanel()
      } else {
        dpOwnerTerminalDevLog('branch', 'open legacy sheet')
        this.$store.commit('dpGame/OPEN_OWNER_HUB')
      }
      this.scheduleReparentElementUiLayersIntoFullscreenRoot()
    },

    openOwnerTouchPanel() {
      if (!this.isOwner) return
      this.ownerTouchSheetOpen = true
      this.scheduleReparentElementUiLayersIntoFullscreenRoot()
    },

    closeOwnerTouchPanel() {
      this.ownerTouchSheetOpen = false
    },

    onOwnerTouchToggleReveal() {
      this.onToggleRevealAll()
    },

    closeOwnerTerminal() {
      this.ownerTerminalOpen = false
    },

    closeOwnerPotJudgeSheet() {
      this.showOwnerPotJudgeSheet = false
      dpOwnerTerminalDevLog('pot judge sheet', { action: 'close', stage: this.stage })
    },

    onOwnerTerminalToggleReveal() {
      this.onToggleRevealAll()
    },

    onToggleRevealAll() {
      if (!this.canToggleReveal) {
        dpOwnerTerminalDevLog('blocked', { canToggleReveal: false })
        return
      }
      var next = !this.ownerRevealAll
      this.$store.commit('dpGame/SET_OWNER_REVEAL_ALL', next)
      this.$message.success(next ? '已开启看穿底牌' : '已关闭看穿底牌')
    },

    closeOwnerHubPanel() {
      this.$store.commit('dpGame/CLOSE_OWNER_HUB')
      this.closeOwnerTerminal()
      this.closeOwnerTouchPanel()
    },

    async loadDeckPresetStatus() {
      if (!this.isOwner || !this.roomId || !this.user) return
      var pwd = dpDeckPresetSessionPassword(this.roomId)
      if (!pwd) return
      try {
        var res = await this.$http.get('/dpRoom/nextHandDeckPrefixStatus', {
          params: {
            roomId: this.roomId,
            experimentalPassword: pwd
          }
        })
        var body = res.data
        if (!dpResultSuccess(body)) {
          this.handleDeckPresetAuthFailure(body)
          return
        }
        var d = dpResultData(body) || {}
        this.deckPresetSavedCount = d.presetCount != null ? d.presetCount : 0
        this.deckPresetInitialCards = Array.isArray(d.cards) ? d.cards.slice() : []
      } catch (e) {
        /* 非关键路径，静默 */
      }
    },

    handleDeckPresetAuthFailure(body) {
      var msg = dpResultMessage(body) || ''
      if (msg.indexOf('密码') >= 0 || msg.indexOf('访问') >= 0 || msg.indexOf('未启用') >= 0 || msg.indexOf('验证') >= 0) {
        clearDeckPresetSessionUnlock(this.roomId)
        this.showDeckPresetDialog = false
        var reopenTrace = this.npcDecisionTraceDockPinned
        this.npcDecisionTraceDockPinned = false
        this.showNpcDecisionTraceSheet = false
        this.experimentalGatePendingFeature = reopenTrace ? 'decision-trace' : 'deck-preset'
        this.showDeckPresetPasswordGate = true
        this.$message.error(msg || '实验功能访问验证已失效，请重新输入密码')
      }
    },

    openDeckPresetDialog() {
      if (!this.isOwner) return
      this.closeOwnerHubPanel()
      if (isDeckPresetUnlocked(this.roomId)) {
        this.showDeckPresetDialog = true
        this.loadDeckPresetStatus()
      } else {
        this.experimentalGatePendingFeature = 'deck-preset'
        this.showDeckPresetPasswordGate = true
      }
    },

    openDecisionTracePanel() {
      if (!this.isOwner) return
      this.closeOwnerHubPanel()
      this.openDecisionTraceDock()
    },

    openDecisionTraceDock() {
      if (!this.isOwner) return
      this.closeOwnerHubPanel()
      if (isNpcDecisionTraceUnlocked(this.roomId)) {
        this.activateDecisionTraceDock()
      } else {
        this.experimentalGatePendingFeature = 'decision-trace'
        this.showDeckPresetPasswordGate = true
      }
    },

    activateDecisionTraceDock() {
      this.npcDecisionTraceDockPinned = true
      if (!this.useDecisionTraceDockWide) {
        this.showNpcDecisionTraceSheet = true
      }
      if (!this._decisionTraceDockLoadedOnce) {
        this._decisionTraceDockLoadedOnce = true
        this.loadTraceHands()
      }
    },

    onToggleDecisionTraceDock() {
      if (this.npcDecisionTraceDockPinned) {
        this.npcDecisionTraceDockPinned = false
        this.showNpcDecisionTraceSheet = false
        return
      }
      this.openDecisionTraceDock()
    },

    readTraceDockWidthPx() {
      var KEY = 'dp_trace_dock_width_px'
      var DEF = 320
      var MIN = 280
      try {
        var v = parseInt(localStorage.getItem(KEY), 10)
        if (!isFinite(v)) return DEF
        return Math.max(MIN, Math.min(this.traceDockWidthMaxPx, v))
      } catch (e) {
        return DEF
      }
    },

    clampTraceDockWidth(px) {
      return Math.max(280, Math.min(this.traceDockWidthMaxPx, Math.round(px)))
    },

    onTraceDockResizeStart(ev) {
      if (!this.showDecisionTraceDockWide) return
      var el = ev.currentTarget
      if (el && el.setPointerCapture && ev.pointerId != null) {
        try {
          el.setPointerCapture(ev.pointerId)
        } catch (e) { /* ignore */ }
      }
      this._traceDockResizePointerId = ev.pointerId
      this._traceDockResizeStartX = ev.clientX
      this._traceDockResizeStartW = this.traceDockWidthPx
      this.traceDockResizing = true
      this._onTraceDockResizeMove = this.onTraceDockResizeMove.bind(this)
      this._onTraceDockResizeEnd = this.onTraceDockResizeEnd.bind(this)
      document.addEventListener('pointermove', this._onTraceDockResizeMove)
      document.addEventListener('pointerup', this._onTraceDockResizeEnd)
      document.addEventListener('pointercancel', this._onTraceDockResizeEnd)
    },

    onTraceDockResizeMove(ev) {
      if (!this.traceDockResizing) return
      if (this._traceDockResizePointerId != null && ev.pointerId !== this._traceDockResizePointerId) return
      if (ev.cancelable) ev.preventDefault()
      // 右栏 dock 左缘分隔条：向右拖应缩小 dock、向左拖应放大（与 clientX 增量反向）
      var delta = this._traceDockResizeStartX - ev.clientX
      this.traceDockWidthPx = this.clampTraceDockWidth(this._traceDockResizeStartW + delta)
    },

    onTraceDockResizeEnd(ev) {
      if (!this.traceDockResizing) return
      if (ev && this._traceDockResizePointerId != null && ev.pointerId !== this._traceDockResizePointerId) return
      this.traceDockResizing = false
      this._traceDockResizePointerId = null
      this.teardownTraceDockResizeListeners()
      try {
        localStorage.setItem('dp_trace_dock_width_px', String(this.traceDockWidthPx))
      } catch (e) { /* ignore */ }
    },

    teardownTraceDockResizeListeners() {
      if (this._onTraceDockResizeMove) {
        document.removeEventListener('pointermove', this._onTraceDockResizeMove)
        this._onTraceDockResizeMove = null
      }
      if (this._onTraceDockResizeEnd) {
        document.removeEventListener('pointerup', this._onTraceDockResizeEnd)
        document.removeEventListener('pointercancel', this._onTraceDockResizeEnd)
        this._onTraceDockResizeEnd = null
      }
      this.traceDockResizing = false
      this._traceDockResizePointerId = null
    },

    onDecisionTraceSheetClose() {
      this.showNpcDecisionTraceSheet = false
      this.npcDecisionTraceDockPinned = false
    },

    onDeckPresetPasswordVerified(password) {
      setDeckPresetSessionUnlock(this.roomId, password)
      this.showDeckPresetPasswordGate = false
      var pending = this.experimentalGatePendingFeature
      this.experimentalGatePendingFeature = null
      if (pending === 'decision-trace') {
        this.activateDecisionTraceDock()
        return
      }
      this.showDeckPresetDialog = true
      this.loadDeckPresetStatus()
    },

    async loadTraceHands() {
      if (!this.isOwner || !this.roomId) return
      var pwd = dpNpcDecisionTraceAuthPassword(this.roomId)
      if (!isNpcDecisionTraceUnlocked(this.roomId)) {
        this.traceHandsLoadError = 'SESSION LOCKED'
        this.npcDecisionTraceDockPinned = false
        this.showNpcDecisionTraceSheet = false
        this.experimentalGatePendingFeature = 'decision-trace'
        this.showDeckPresetPasswordGate = true
        return
      }
      this.traceHandsLoading = true
      this.traceHandsLoadError = ''
      try {
        var result = await fetchTraceHands(this.$http, this.roomId, pwd)
        if (!result.ok) {
          this.handleDeckPresetAuthFailure(result.body)
          if (!this.showDeckPresetPasswordGate) {
            this.traceHandsLoadError = dpResultMessage(result.body) || '加载失败'
          }
          return
        }
        this.traceHands = result.hands || []
      } catch (err) {
        this.traceHandsLoadError = 'NETWORK ERROR'
      } finally {
        this.traceHandsLoading = false
      }
    },

    onNpcDecisionTraceHandPush() {
      /* Pull-only REST：决策 trace 仅通过 dock「刷新」拉取，忽略 WS 推送 */
    },

    async submitDeckPreset(cards) {
      if (!this.isOwner || this.deckPresetSubmitting) return
      var pwd = dpDeckPresetSessionPassword(this.roomId)
      if (!pwd) {
        this.showDeckPresetDialog = false
        this.showDeckPresetPasswordGate = true
        return
      }
      this.deckPresetSubmitting = true
      try {
        var res = await this.$http.post('/dpRoom/setNextHandDeckPrefix', {
          roomId: this.roomId,
          experimentalPassword: pwd,
          cards: cards || []
        })
        var body = res.data
        if (!dpResultSuccess(body)) {
          this.handleDeckPresetAuthFailure(body)
          if (!this.showDeckPresetPasswordGate) {
            this.$message.error(dpResultMessage(body) || '预设失败')
          }
          return
        }
        var d = dpResultData(body) || {}
        this.deckPresetSavedCount = d.presetCount != null ? d.presetCount : (cards || []).length
        this.deckPresetInitialCards = (cards || []).slice()
        this.$message.success(d.message || '已预设下局牌序')
        this.showDeckPresetDialog = false
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      } finally {
        this.deckPresetSubmitting = false
      }
    },

    // ---- 房主：移交房主（通过弹窗选择玩家） ----
    async doTransferOwner(options) {
      if (!this.ownerActionTarget) {
        this.$message.warning('请先选择要移交房主的玩家')
        return
      }
      if (this.ownerActionTarget === this.user.nickname) {
        this.$message.warning('不能把房主移交给自己')
        return
      }
      var skipConfirm = options && options.skipConfirm
      if (!skipConfirm) {
        this.scheduleReparentElementUiLayersIntoFullscreenRoot()
        try {
          await this.dpConfirm(
            '确定将房主移交给 [' + dpDisplayNickname(this.ownerActionTarget) + '] 吗？',
            '移交房主'
          )
        } catch (e) {
          return
        }
      }
      try {
        var res = await this.$http.post('/dpRoom/transferOwner', null, {
          params: {
            roomId: this.roomId,
            toNickname: this.ownerActionTarget
          }
        })
        if (res.data !== 'ok') {
          this.$message.error('移交失败：' + res.data)
        } else {
          this.$message.success('已将房主移交给 ' + dpDisplayNickname(this.ownerActionTarget))
        }
        await this.loadGame()
        this.closeOwnerHubPanel()
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },

    // ---- 房主：踢人到观众席（可多选批量） ----
    async doKickPlayers (nicknames, options) {
      var raw = [].concat(nicknames || []).filter(Boolean)
      var seen = {}
      var list = []
      for (var i = 0; i < raw.length; i++) {
        var n = raw[i]
        if (seen[n]) continue
        seen[n] = true
        list.push(n)
      }
      if (!list.length) {
        this.$message.warning('请至少选择一名要踢出的玩家')
        return
      }
      var skipConfirm = options && options.skipConfirm
      if (!skipConfirm) {
        var preview = list.slice(0, 8).map(function (n) {
          return dpDisplayNickname(n)
        }).join('、')
        if (list.length > 8) preview += ' …'
        this.scheduleReparentElementUiLayersIntoFullscreenRoot()
        try {
          await this.dpConfirm(
            '确定将以下 ' +
              list.length +
              ' 人踢出本局并移至观众席吗？\n\n' +
              preview,
            '批量踢出'
          )
        } catch (e) {
          return
        }
      }
      try {
        var res = await this.$http.post('/dpRoom/kickPlayersBatch', null, {
          params: { roomId: this.roomId, nicknames: list.join(',') }
        })
        var body = res.data
        if (!dpResultSuccess(body)) {
          var errData = body && body.data ? body.data : {}
          var fn = errData.failedNicknames || []
          var msg = dpResultMessage(body)
          if (fn.length) {
            msg +=
              '：' +
              fn
                .map(function (n) {
                  return dpDisplayNickname(n)
                })
                .join('、')
          }
          this.$message.error(msg)
        } else {
          var d = dpResultData(body) || {}
          var fc = d.failCount != null ? d.failCount : 0
          if (fc > 0) {
            var failedNicks = d.failedNicknames || []
            var detail = failedNicks
              .map(function (n) {
                return dpDisplayNickname(n)
              })
              .join('、')
            this.$message.warning(
              '已踢出 ' +
                (d.successCount != null ? d.successCount : list.length - fc) +
                ' 人，另有 ' +
                fc +
                ' 人未成功：' +
                detail
            )
          } else {
            var okn = d.successCount != null ? d.successCount : list.length
            this.$message.success('已将 ' + okn + ' 人踢至观众席')
          }
        }
        await this.loadGame()
        this.closeOwnerHubPanel()
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },

    closeCustomNpcStyleDialog () {
      this.$store.commit('dpGame/SET_MODAL', { showCustomNpcStyleDialog: false })
    },

    /**
     * 自定义 NPC：弹窗确定后提交（本批共用一套 profile）。
     */
    async submitCustomNpcBatch (profile) {
      if (!DP_CUSTOM_NPC_UI_ENABLED) return
      if (!this.roomId || !this.user || !this.user.nickname) {
        this.$message.warning('请先登录')
        return
      }
      var count = this.customNpcPendingCount
      this.$store.commit('dpGame/SET_MODAL', { showCustomNpcStyleDialog: false })
      this.$store.commit('dpGame/SET_BOT_STATE', {
        customBotAdding: true,
        customBotAddedTip: ''
      })
      try {
        var res = await this.$http.post('/dpRoom/addCustomNpcBatch', {
          roomId: this.roomId,
          count: count,
          profile: profile
        })
        var msg
        if (res.data === 'ok') {
          msg =
            '已请求在下一局加入最多 ' +
            count +
            ' 个自定义 NPC（受空位限制；本批共用一套参数），请等待本局结束。'
        } else {
          msg = '添加失败：' + res.data
        }
        this.$store.commit('dpGame/SET_BOT_STATE', { customBotAddedTip: msg })
      } catch (e) {
        this.$store.commit('dpGame/SET_BOT_STATE', {
          customBotAddedTip: '网络错误：' + (e && e.message ? e.message : e)
        })
      } finally {
        this.$store.commit('dpGame/SET_BOT_STATE', { customBotAdding: false })
      }
    },

    /**
     * 房主神器：按数量将 NPC 加入下一局等待列表（规则档走 addRuleNpcBatch）。
     */
    async confirmAddOwnerNpcs (payload) {
      if (!this.roomId || !payload) return
      var type = payload.type
      var count = parseInt(payload.count, 10)
      if (isNaN(count) || count < 1) count = 1
      if (count > 9) count = 9

      if (type === 'custom') {
        if (!DP_CUSTOM_NPC_UI_ENABLED) {
          this.$message.warning('自定义 NPC 功能暂未开放')
          return
        }
        this.$store.commit('dpGame/SET_MODAL', {
          customNpcPendingCount: count,
          showCustomNpcStyleDialog: true
        })
        return
      }

      var ruleStore = {
        FISH: { prefix: 'demoBot' },
        MANIAC: { prefix: 'maniacBot' },
        TAG: { prefix: 'tagBot' },
        LAG: { prefix: 'lagBot' },
        NIT: { prefix: 'nitBot' },
        CALL: { prefix: 'callBot' }
      }

      var adding = {}
      var tipEmpty = {}
      var tipPrefix = ''
      var run = null

      if (type === 'rule') {
        var arch = String(payload.archetype || 'FISH').toUpperCase().replace(/^BOT_/, '')
        var rs = ruleStore[arch]
        if (!rs) {
          this.$message.warning('不支持的机器人类型')
          return
        }
        tipPrefix = rs.prefix
        adding[tipPrefix + 'Adding'] = true
        tipEmpty[tipPrefix + 'AddedTip'] = ''
        run = async function () {
          var res = await this.$http.post('/dpRoom/addRuleNpcBatch', null, {
            params: { roomId: this.roomId, archetype: arch, count: count }
          })
          if (res.data === 'ok') {
            return '已请求在下一局加入最多 ' + count + ' 个 ' + arch + '（受空位限制；每人独立编号），请等待本局结束。'
          }
          return '添加失败：' + res.data
        }.bind(this)
      } else if (type === 'llm') {
        tipPrefix = 'llmBot'
        adding.llmBotAdding = true
        tipEmpty.llmBotAddedTip = ''
        run = async function () {
          var ok = 0
          var lastErr = ''
          for (var i = 0; i < count; i++) {
            var res = await this.$http.post('/dpRoom/addLlmBot', null, {
              params: { roomId: this.roomId }
            })
            if (res.data === 'ok') {
              ok++
            } else {
              lastErr = String(res.data)
              break
            }
          }
          if (ok === count) {
            return '已请求在下一局加入 ' + count + ' 个 BOT_LLM，请等待本局结束（需配置服务端方舟密钥）。'
          }
          if (ok > 0) {
            return '仅成功添加 ' + ok + '/' + count + ' 个：' + (lastErr || '席位可能已满')
          }
          return '添加大模型 NPC 失败：' + (lastErr || 'fail')
        }.bind(this)
      } else if (type === 'llmGlobal') {
        tipPrefix = 'llmGlobalBot'
        adding.llmGlobalBotAdding = true
        tipEmpty.llmGlobalBotAddedTip = ''
        run = async function () {
          var ok = 0
          var lastErr = ''
          for (var i = 0; i < count; i++) {
            var res = await this.$http.post('/dpRoom/addLlmGlobalBot', null, {
              params: { roomId: this.roomId }
            })
            if (res.data === 'ok') {
              ok++
            } else {
              lastErr = String(res.data)
              break
            }
          }
          if (ok === count) {
            return '已请求在下一局加入 ' + count + ' 个 BOT_LLM_GLOBAL（全局叙事多轮），请等待本局结束（需服务端方舟密钥）。'
          }
          if (ok > 0) {
            return '仅成功添加 ' + ok + '/' + count + ' 个：' + (lastErr || '席位可能已满')
          }
          return '添加 BOT_LLM_GLOBAL 失败：' + (lastErr || 'fail')
        }.bind(this)
      } else {
        return
      }

      this.$store.commit('dpGame/SET_BOT_STATE', Object.assign({}, adding, tipEmpty))
      try {
        var msg = await run()
        var tipPatch = {}
        tipPatch[tipPrefix + 'AddedTip'] = msg
        this.$store.commit('dpGame/SET_BOT_STATE', tipPatch)
      } catch (e) {
        var errPatch = {}
        errPatch[tipPrefix + 'AddedTip'] = '网络错误：' + (e && e.message ? e.message : e)
        this.$store.commit('dpGame/SET_BOT_STATE', errPatch)
      } finally {
        var idle = {}
        idle[tipPrefix + 'Adding'] = false
        this.$store.commit('dpGame/SET_BOT_STATE', idle)
      }
    },

    /**
     * retro8bit 触控板：批量串行添加多种 NPC（count=0 已过滤）。
     */
    async confirmBatchAddOwnerNpcs (payload) {
      if (!this.roomId || !payload || !payload.items || !payload.items.length) return
      if (!this.user || !this.user.nickname) {
        this.$message.warning('请先登录')
        return
      }

      var items = payload.items.filter(function (it) {
        var c = parseInt(it.count, 10)
        return !isNaN(c) && c > 0
      })
      if (!items.length) {
        this.$message.warning('请至少选择一种 NPC 并设置数量')
        return
      }

      var okParts = []
      var errParts = []

      for (var i = 0; i < items.length; i++) {
        var item = items[i]
        var count = parseInt(item.count, 10)
        if (isNaN(count) || count < 1) continue
        if (count > 9) count = 9

        try {
          if (item.type === 'rule') {
            var arch = String(item.archetype || 'FISH').toUpperCase().replace(/^BOT_/, '')
            var res = await this.$http.post('/dpRoom/addRuleNpcBatch', null, {
              params: { roomId: this.roomId, archetype: arch, count: count }
            })
            if (res.data === 'ok') {
              okParts.push(arch + '×' + count)
            } else {
              errParts.push(arch + ': ' + res.data)
            }
          } else if (item.type === 'custom') {
            if (!DP_CUSTOM_NPC_UI_ENABLED) {
              errParts.push('CUSTOM: 功能暂未开放')
              continue
            }
            var profile = payload.customProfile
            if (!profile) {
              errParts.push('CUSTOM: 缺少参数')
              continue
            }
            var resCustom = await this.$http.post('/dpRoom/addCustomNpcBatch', {
              roomId: this.roomId,
              count: count,
              profile: profile
            })
            if (resCustom.data === 'ok') {
              okParts.push('CUSTOM×' + count)
            } else {
              errParts.push('CUSTOM: ' + resCustom.data)
            }
          } else if (item.type === 'llm') {
            var llmOk = 0
            var llmErr = ''
            for (var li = 0; li < count; li++) {
              var resLlm = await this.$http.post('/dpRoom/addLlmBot', null, {
                params: { roomId: this.roomId }
              })
              if (resLlm.data === 'ok') {
                llmOk++
              } else {
                llmErr = String(resLlm.data)
                break
              }
            }
            if (llmOk === count) {
              okParts.push('LLM×' + count)
            } else if (llmOk > 0) {
              okParts.push('LLM×' + llmOk)
              errParts.push('LLM: 仅成功 ' + llmOk + '/' + count + (llmErr ? ' (' + llmErr + ')' : ''))
            } else {
              errParts.push('LLM: ' + (llmErr || 'fail'))
            }
          } else if (item.type === 'llmGlobal') {
            var gOk = 0
            var gErr = ''
            for (var gi = 0; gi < count; gi++) {
              var resG = await this.$http.post('/dpRoom/addLlmGlobalBot', null, {
                params: { roomId: this.roomId }
              })
              if (resG.data === 'ok') {
                gOk++
              } else {
                gErr = String(resG.data)
                break
              }
            }
            if (gOk === count) {
              okParts.push('LLM_GLOBAL×' + count)
            } else if (gOk > 0) {
              okParts.push('LLM_GLOBAL×' + gOk)
              errParts.push('LLM_GLOBAL: 仅成功 ' + gOk + '/' + count + (gErr ? ' (' + gErr + ')' : ''))
            } else {
              errParts.push('LLM_GLOBAL: ' + (gErr || 'fail'))
            }
          }
        } catch (e) {
          var label = item.type === 'rule'
            ? String(item.archetype || 'RULE')
            : String(item.type || 'NPC').toUpperCase()
          errParts.push(label + ': 网络错误')
        }
      }

      if (okParts.length && !errParts.length) {
        this.$message.success('已请求批量添加 ' + okParts.join('+') + '，请等待本局结束（受空位限制）。')
      } else if (okParts.length && errParts.length) {
        this.$message.warning('部分成功 ' + okParts.join('+') + '；失败：' + errParts.join('；'))
      } else if (errParts.length) {
        this.$message.error('批量添加失败：' + errParts.join('；'))
      }

      this.closeOwnerTouchPanel()
    },

    onOpenMusicBox() {
      if (this.gameUiTheme === 'retro8bit') { this.showMusicPlayer = true; return }
      this.$store.commit('dpGame/SET_MODAL', { showMusicBoxModal: true })
    },

    openHandHistory() {
      if (this.gameUiTheme === 'retro8bit') { this.showHandHistoryPanel = true; return }
      this.$store.commit('dpGame/SET_MODAL', { showHandHistoryModal: true })
    },
    resolveActiveHandHistoryViewer() {
      if (this.showOpponentHandHistoryPanel) {
        return this.$refs.opponentHandHistoryViewer || this.$refs.handHistoryViewer
      }
      return this.$refs.handHistoryViewer
    },
    openHandHistoryDetail(handHistoryId, achievementSubjectUserId, achievementSubjectNickname) {
      this.handHistoryDetailId = handHistoryId
      var subjectUid = Number(achievementSubjectUserId)
      if (!isNaN(subjectUid) && subjectUid > 0) {
        this.handHistoryDetailSubjectUserId = subjectUid
        this.handHistoryDetailSubjectNickname = achievementSubjectNickname ? String(achievementSubjectNickname) : ''
      } else {
        this.handHistoryDetailSubjectUserId = null
        this.handHistoryDetailSubjectNickname = ''
      }
    },
    onHandHistoryDetailClosed() {
      this.handHistoryDetailId = null
      this.handHistoryDetailSubjectUserId = null
      this.handHistoryDetailSubjectNickname = ''
    },
    chatPanelUsesMobileDock() {
      return this.viewportWidth <= 600
        || this.layoutTier === 'desktop'
        || !!this.layoutFullscreen
    },
    resolveActiveChatPanel(footer) {
      if (!footer || !footer.$refs) return null
      var refs = footer.$refs
      if (this.chatPanelUsesMobileDock() && refs.guideMobileRoomChatPanel) {
        return refs.guideMobileRoomChatPanel
      }
      if (refs.guideRoomChatPanel) return refs.guideRoomChatPanel
      return refs.guideMobileRoomChatPanel || null
    },
    expandChat() {
      var footer = this.$refs.heroDockFooter
      if (!footer) return
      var panel = this.resolveActiveChatPanel(footer)
      if (panel && typeof panel.openWithReveal === 'function') {
        panel.openWithReveal()
        this.$nextTick(function () {
          var el = panel.$el || panel.$refs.list || (panel.$refs.guideChatListWrap)
          if (el && typeof el.scrollIntoView === 'function') {
            el.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
          }
        })
      }
    },
    /** 终端 CLI：切换聊天面板开/关；返回 'opened' | 'closed' | null */
    toggleChat() {
      var footer = this.$refs.heroDockFooter
      if (!footer) return null
      var panel = this.resolveActiveChatPanel(footer)
      if (!panel) return null
      if (process.env.NODE_ENV !== 'production') {
        console.log('[dp-terminal] toggleChat', {
          usesMobileDock: this.chatPanelUsesMobileDock(),
          isVisuallyOpen: panel.isVisuallyOpen
        })
      }
      var result = null
      if (typeof panel.toggleWithReveal === 'function') {
        result = panel.toggleWithReveal()
      } else if (panel.isVisuallyOpen) {
        if (typeof panel.closeWithReveal === 'function') {
          panel.closeWithReveal()
        } else if (typeof panel.closeForGuide === 'function') {
          panel.closeForGuide()
        }
        result = 'closed'
      } else if (typeof panel.openWithReveal === 'function') {
        panel.openWithReveal()
        result = 'opened'
      }
      if (result === 'opened') {
        var self = this
        this.$nextTick(function () {
          var el = panel.$el || panel.$refs.list || panel.$refs.guideChatListWrap
          if (el && typeof el.scrollIntoView === 'function') {
            el.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
          }
        })
      }
      return result
    },

    // ---- 退出 ----
    async exitGame() {
      try {
        await this.dpConfirm('确定退出对局？', '退出对局', {
          confirmButtonText: '退出',
          cancelButtonText: '取消'
        })
      } catch (e) {
        return
      }
      this.$store.commit('dpGame/SET_MODAL', { showCustomNpcStyleDialog: false })
      this.beginIntentionalLeave()
      try {
        await this.$http.post('/dpRoom/exitRoom', null, {
          params: { roomId: this.roomId }
        })
      } catch (err) {
        console.error('退出失败', err)
      }
      this.navigateHomeIfNeeded()
    },

    // ---- 观众：报名 / 取消下一局加入（再点一次从候补列表移除）----
    async readyNextHand() {
      if (!this.user) return { ok: false, message: '未登录' }
      try {
        var rp = this.roomApiParams
        if (this.nextHandReady) {
          var cancelRes = await this.$http.post('/dpRoom/cancelReadyNextHand', null, {
            params: rp
          })
          if (cancelRes.data === 'ok') {
            this.$store.commit('dpGame/SET_NEXT_HAND_READY', false)
            this.$message.success('已取消下一局报名')
            await this.loadGame()
            return { ok: true, cancelled: true }
          }
          this.$message.error('取消失败：' + cancelRes.data)
          return { ok: false, message: String(cancelRes.data) }
        }
        var res = await this.$http.post('/dpRoom/readyNextHand', null, {
          params: rp
        })
        if (res.data === 'ok') {
          this.$store.commit('dpGame/SET_NEXT_HAND_READY', true)
          this.$message.success('已报名下一局，将在下一局开局时自动加入对局')
          await this.loadGame()
          return { ok: true }
        }
        this.$message.error('报名失败：' + res.data)
        return { ok: false, message: String(res.data) }
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
        return { ok: false, message: err.message || '网络错误' }
      }
    },

    /**
     * 同步公共牌翻转状态：新牌先背面，再依次翻转；翻完后再允许显示牌型
     *
     * 注意：房间状态会高频推送（约 1s）。若每次推送都清掉「翻完」定时器，而公共牌张数未变（numNew===0），
     * 将不会重新设定时器，导致 communityCardsFlipComplete 长期为 false，成牌牌型区可卡住数秒～十余秒。
     * 因此仅在公共牌变少（新一手）或新增公共牌（numNew>0）时取消并重设定时器。
     */
    syncCommunityCardsFlipState(newCards) {
      var instantFlip = this.ecoMode || this.prefersReducedMotionForFlip()
      var prevLen = this.communityCardsFlipState.length
      if (newCards.length < prevLen) {
        if (this.communityCardsFlipCompleteTimer) {
          clearTimeout(this.communityCardsFlipCompleteTimer)
          this.communityCardsFlipCompleteTimer = null
        }
        this.$store.commit('dpGame/SET_COMMUNITY_FLIP_STATE', [])
        this.$store.commit('dpGame/SET_COMMUNITY_FLIP_COMPLETE', false)
        prevLen = 0
      }
      var numNew = newCards.length - prevLen
      if (numNew > 0) {
        if (this.communityCardsFlipCompleteTimer) {
          clearTimeout(this.communityCardsFlipCompleteTimer)
          this.communityCardsFlipCompleteTimer = null
        }
        this.$store.commit('dpGame/SET_COMMUNITY_FLIP_COMPLETE', false)
      }
      var flip = this.communityCardsFlipState.slice()
      for (var i = flip.length; i < newCards.length; i++) {
        flip.push(instantFlip)
      }
      if (flip.length !== this.communityCardsFlipState.length) {
        this.$store.commit('dpGame/SET_COMMUNITY_FLIP_STATE', flip)
      }
      if (numNew > 0 && instantFlip) {
        for (var k = 0; k < newCards.length; k++) {
          flip[k] = true
        }
        this.$store.commit('dpGame/SET_COMMUNITY_FLIP_STATE', flip)
        var selfInstant = this
        this.communityCardsFlipCompleteTimer = setTimeout(function () {
          selfInstant.$store.commit('dpGame/SET_COMMUNITY_FLIP_COMPLETE', true)
          selfInstant.communityCardsFlipCompleteTimer = null
        }, 180)
      } else if (numNew > 0) {
        var theme = this.gameUiTheme
        for (var j = prevLen; j < newCards.length; j++) {
          var self = this
          ;(function (capturedIdx, capturedDelay) {
            setTimeout(function () {
              if (self.communityCardsFlipState.length > capturedIdx) {
                self.$store.commit('dpGame/SET_FLIP_AT', { index: capturedIdx, value: true })
              }
            }, capturedDelay)
          })(j, communityFlipDelayMsForTheme(theme, j - prevLen))
        }
        var selfDone = this
        this.communityCardsFlipCompleteTimer = setTimeout(function () {
          selfDone.$store.commit('dpGame/SET_COMMUNITY_FLIP_COMPLETE', true)
          selfDone.communityCardsFlipCompleteTimer = null
        }, communityFlipCompleteMsForTheme(theme, numNew))
      } else if (newCards.length > 0 && this.communityCardsFlipState.every(function (x) {
        return x
      })) {
        this.$store.commit('dpGame/SET_COMMUNITY_FLIP_COMPLETE', true)
      }
    },

    /**
     * 开局发牌动画：从发牌位顺时针下一位起为 0，依次 1、2…（与常见首圈发牌顺序一致，仅用于错开飞入时间）
     */
    holeDealOrderFromDealer(seatIndex) {
      return holeDealOrderFromDealerUtil(seatIndex, this.players)
    },

    getPlayerBoxStyle(p, i) {
      return dpGamePlayerBoxStyle(p, i, {
        actIndex: this.actIndex,
        stage: this.cardDisplayStage,
        isOwner: this.isOwner,
        selectedWinners: this.selectedWinners,
        myNickname: this.user && this.user.nickname
      })
    }
  }
}
</script>
