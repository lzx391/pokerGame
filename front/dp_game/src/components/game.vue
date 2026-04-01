<template>
  <div
      ref="gameRoot"
      class="dp-game-root"
      :class="{
        'dp-game-root--pseudo-fs': pseudoFullscreen,
        'dp-game-root--layout-fs': layoutFullscreen,
        'dp-game-root--mobile-hero-dock': mobileHeroDockActive
      }"
      :data-dp-game-theme="gameUiTheme"
      :data-dp-eco-mode="ecoMode ? 'true' : 'false'"
  >
    <div class="dp-game-page-flex">
    <div class="dp-game-page-seg dp-game-page-seg--pre-table">
    <game-top-bar
        :room-id="roomId"
        :stage-label="stageCN"
        :pot="pot"
        :current-bet-to-call="currentBetToCall"
        :spectator-count="spectators.length"
        :is-fullscreen="layoutFullscreen"
        :show-spectator-prepare="showSpectatorPrepareBlock"
        :next-hand-ready="nextHandReady"
        :game-ui-theme.sync="gameUiTheme"
        :eco-mode.sync="ecoMode"
        :theme-options="gameThemeOptions"
        @show-hand-rank="showHandRankModal = true"
        @show-spectators="showSpectatorModal = true"
        @toggle-fullscreen="toggleDpFullscreen"
        @open-hand-history="openHandHistory"
        @exit="exitGame"
        @ready-next-hand="readyNextHand"
    />

    </div>

    <div class="dp-game-page-seg dp-game-page-seg--table">
    <!-- <div v-if="playing" class="dp-game-hint">
      各人手牌与公共牌均由庄位（D）发出
    </div> -->

    <!-- 圆桌：公共牌在桌面中心；入座时本人在 6 点方向，所有座位沿椭圆整圈均分 -->
    <div class="dp-game-table">
      <div class="dp-game-table__layout">
        <div class="dp-game-table__felt" aria-hidden="true" />
        <div class="dp-game-table__center">
          <div class="dp-game-table__center-stack">
            <div
                v-if="showTableActionTimer"
                class="dp-game-table-action-timer"
                :class="[
                  'dp-game-table-action-timer--' + tableActionTimerUrgency,
                  { 'dp-game-table-action-timer--rich': !ecoMode }
                ]"
                role="status"
                aria-live="polite"
                :aria-label="'当前行动 ' + tableActionActorDisplayName + '，剩余 ' + timeLeft + ' 秒'"
            >
              <div class="dp-game-table-action-timer__inner">
                <div
                    class="dp-game-table-action-timer__ring"
                    :style="{ '--dp-table-timer-pct': actionTimerProgressPct }"
                >
                  <span class="dp-game-table-action-timer__sec">{{ timeLeft }}</span>
                </div>
                <div class="dp-game-table-action-timer__meta">
                  <span class="dp-game-table-action-timer__who">{{ tableActionActorDisplayName }}</span>
                  <span class="dp-game-table-action-timer__hint">思考中</span>
                </div>
              </div>
            </div>
            <game-community-cards
                :community-cards="communityCards"
                :flip-state="communityCardsFlipState"
            />
            <div
                class="dp-game-muck-pile"
                data-dp-muck-anchor="true"
                title="弃牌堆"
                aria-label="弃牌堆"
            />
          </div>
        </div>
        <div
            v-for="(row, displayIdx) in playersDisplayOrder"
            :key="(row.player.leftThisHand ? 'offline-' + row.seatIndex : row.player.nickname)"
            class="dp-game-table__seat"
            :class="{ 'dp-game-table__seat--empty': viewerSeatedAtTable && displayIdx === 0 }"
            :style="getPlayerRoundTableStyle(displayIdx, playersDisplayOrder.length)"
        >
          <game-player-card
              v-if="!(viewerSeatedAtTable && displayIdx === 0)"
              :player="row.player"
              :seat-index="row.seatIndex"
              :box-style="getPlayerBoxStyle(row.player, row.seatIndex)"
              :act-index="actIndex"
              :stage="stage"
              :community-cards="communityCards"
              :community-cards-flip-complete="communityCardsFlipComplete"
              :is-owner="isOwner"
              :owner-reveal-all="ownerRevealAll"
              :my-nickname="user ? user.nickname : ''"
              :hand-deal-key="currentHandSeed"
              :hole-deal-seat-order="holeDealOrderFromDealer(row.seatIndex)"
              :hole-deal-player-count="holeDealPlayerCountForAnim"
              :rival-mini="true"
              :showdown-hand-leaders="showdownHandLeaderNicknames"
              :seat-chat-text="seatChatTextFor(row.player.nickname)"
              :seat-chat-side="getSeatChatBubbleSide(displayIdx, playersDisplayOrder.length)"
              @card-click="onPlayerCardClick"
          />
        </div>
      </div>
    </div>

    </div>

    <div class="dp-game-page-seg dp-game-page-seg--post-table">
    <!-- 宽屏非全屏：内联手牌+操作；窄屏或全屏/伪全屏：底栏按钮 + 底部抽屉（见 dp-game-shell.css） -->
    <div
        v-if="heroDockRow || isMyTurn || inSettledStage || isOwner"
        class="dp-game-hero-action-row dp-game-hero-action-row--hide-narrow"
        aria-label="本人手牌与操作"
    >
      <div class="dp-game-room-chat__bar dp-game-room-chat__bar--hero-row" aria-label="房间聊天">
        <input
            v-model="chatInputDraft"
            type="text"
            maxlength="200"
            placeholder="说一句…"
            class="dp-game-room-chat__input"
            aria-label="房间聊天输入"
            @keydown.enter.prevent="sendRoomChat"
        >
        <button
            type="button"
            class="dp-game-room-chat__send"
            @click="sendRoomChat"
        >
          发送
        </button>
      </div>
      <div
          v-if="isOwner"
          class="dp-game-hero-action-row__owner-cluster"
          aria-label="房主操作"
      >
        <button
            v-if="heroDockRow"
            type="button"
            class="dp-game-hero-action-row__owner-btn dp-game-hero-action-row__owner-btn--hand"
            @click="showMobileHandSheet = true"
        >
          查看手牌
        </button>
        <button
            type="button"
            class="dp-game-hero-action-row__owner-btn"
            @click="openOwnerHubSheet"
        >
          房主操作
        </button>
      </div>
      <div
          v-if="heroDockRow"
          class="dp-game-hero-dock"
          :class="{ 'dp-game-hero-dock--hand-reveal': stage === 'showdown' || stage === 'settled' }"
      >
        <game-player-card
            :player="heroDockRow.player"
            :seat-index="heroDockRow.seatIndex"
            :box-style="getPlayerBoxStyle(heroDockRow.player, heroDockRow.seatIndex)"
            :act-index="actIndex"
            :stage="stage"
            :community-cards="communityCards"
            :community-cards-flip-complete="communityCardsFlipComplete"
            :is-owner="isOwner"
            :owner-reveal-all="ownerRevealAll"
            :my-nickname="user ? user.nickname : ''"
            :hand-deal-key="currentHandSeed"
            :hole-deal-seat-order="holeDealOrderFromDealer(heroDockRow.seatIndex)"
            :hole-deal-player-count="holeDealPlayerCountForAnim"
            :rival-mini="false"
            :showdown-hand-leaders="showdownHandLeaderNicknames"
            :seat-chat-text="seatChatTextFor(heroDockRow.player.nickname)"
            @card-click="onPlayerCardClick"
        />
      </div>
      <!-- 右侧槽位固定：轮到本人显示面板，否则用主题底「盖住」占位，并与左侧手牌区等高 -->
      <div
          class="dp-game-inline-action-slot"
          :class="{ 'dp-game-inline-action-slot--solo': !heroDockRow }"
      >
        <game-action-panel
            v-if="isMyTurn || inSettledStage"
            :settled-prepare="inSettledStage"
            :ready-time-left="readyTimeLeft"
            :my-ready="myReady"
            :time-left="timeLeft"
            :current-bet-to-call="currentBetToCall"
            :my-bet="myBet"
            :call-amount="callAmount"
            :small-blind="smallBlind"
            :big-blind="bigBlind"
            :min-raise="minRaise"
            :min-total-to-raise="minTotalToRaise"
            :last-raise-increment="lastRaiseIncrementEffective"
            :pot="pot"
            :my-chips="myChips"
            :raise-amount.sync="raiseAmount"
            @call="doCall"
            @raise="doRaise"
            @all-in="doAllIn"
            @fold="doFold"
            @toggle-ready="toggleReady"
            @rebuy="rebuy"
        />
        <div
            v-else-if="heroDockRow"
            class="dp-game-action-slot-cover"
            aria-hidden="true"
        />
      </div>
    </div>

    <div
        v-if="heroDockRow || isMyTurn || inSettledStage || isOwner"
        class="dp-game-mobile-hero-bar"
        aria-label="手牌与行动"
    >
      <div class="dp-game-room-chat__bar dp-game-room-chat__bar--mobile-dock" aria-label="房间聊天">
        <input
            v-model="chatInputDraft"
            type="text"
            maxlength="200"
            placeholder="说一句…"
            class="dp-game-room-chat__input"
            aria-label="房间聊天输入"
            @keydown.enter.prevent="sendRoomChat"
        >
        <button
            type="button"
            class="dp-game-room-chat__send"
            @click="sendRoomChat"
        >
          发送
        </button>
      </div>
      <div
          v-if="isOwner"
          class="dp-game-mobile-hero-bar__owner-cluster"
          aria-label="房主操作"
      >
        <button
            v-if="heroDockRow"
            type="button"
            class="dp-game-mobile-hero-bar__btn"
            @click="showMobileHandSheet = true"
        >
          查看手牌
        </button>
        <button
            type="button"
            class="dp-game-mobile-hero-bar__btn dp-game-mobile-hero-bar__btn--owner"
            @click="openOwnerHubSheet"
        >
          房主操作
        </button>
      </div>
      <button
          v-if="heroDockRow && !isOwner"
          type="button"
          class="dp-game-mobile-hero-bar__btn"
          @click="showMobileHandSheet = true"
      >
        查看手牌
      </button>
      <button
          v-if="isMyTurn"
          type="button"
          class="dp-game-mobile-hero-bar__btn dp-game-mobile-hero-bar__btn--action"
          :class="{ 'dp-game-mobile-hero-bar__btn--urgent': timeLeft <= 10 }"
          @click="showMobileActionSheet = true"
      >
        行动（{{ timeLeft }}s）
      </button>
      <button
          v-if="inSettledStage"
          type="button"
          class="dp-game-mobile-hero-bar__btn dp-game-mobile-hero-bar__btn--action"
          :class="{ 'dp-game-mobile-hero-bar__btn--urgent': readyTimeLeft <= 8 }"
          @click="showMobileActionSheet = true"
      >
        准备（{{ readyTimeLeft }}s）
      </button>
    </div>

    <!-- 观众等非桌上座位的聊天（先关闭，需要时取消注释并恢复 dp-game-seat-chat-orphans 样式块）
    <div
        v-if="spectatorSeatChatEntries.length"
        class="dp-game-seat-chat-orphans"
        aria-label="观众消息"
    >
      <div
          v-for="e in spectatorSeatChatEntries"
          :key="'chat-orphan-' + e.nickname"
          class="dp-game-seat-chat-orphans__row"
      >
        <span class="dp-game-seat-chat-orphans__who">{{ formatChatNick(e.nickname) }}</span>
        <span class="dp-game-seat-chat-orphans__text">{{ e.text }}</span>
      </div>
    </div>
    -->

    <div
        v-if="!mobileHeroDockActive && !isOwner"
        class="dp-game-action-hud"
        aria-label="房间聊天"
    >
      <div class="dp-game-room-chat__bar">
        <input
            v-model="chatInputDraft"
            type="text"
            maxlength="200"
            placeholder="说一句…"
            class="dp-game-room-chat__input"
            aria-label="房间聊天输入"
            @keydown.enter.prevent="sendRoomChat"
        >
        <button
            type="button"
            class="dp-game-room-chat__send"
            @click="sendRoomChat"
        >
          发送
        </button>
      </div>
    </div>

    </div>
    </div>

    <game-hand-rank-modal
        :visible="showHandRankModal"
        :items="handRankReference"
        @close="showHandRankModal = false"
    />

    <game-spectator-modal
        :visible="showSpectatorModal"
        :spectators="spectators"
        @close="showSpectatorModal = false"
    />

    <game-hand-history-modal
        :visible="showHandHistoryModal"
        :game-ui-theme="gameUiTheme"
        @close="showHandHistoryModal = false"
    />

    <div
        v-if="showMobileHandSheet && heroDockRow"
        class="dp-game-sheet-mask dp-game-sheet-mask--bottom"
        role="dialog"
        aria-modal="true"
        aria-label="查看手牌"
        @click.self="showMobileHandSheet = false"
    >
      <div class="dp-game-sheet" @click.stop>
        <div class="dp-game-sheet__head">
          <span class="dp-game-sheet__title">我的手牌</span>
          <button
              type="button"
              class="dp-game-sheet__close"
              aria-label="关闭"
              @click="showMobileHandSheet = false"
          >
            ×
          </button>
        </div>
        <div class="dp-game-sheet__body">
          <div
              class="dp-game-hero-dock dp-game-hero-dock--in-sheet"
              :class="{ 'dp-game-hero-dock--hand-reveal': stage === 'showdown' || stage === 'settled' }"
          >
            <game-player-card
                :player="heroDockRow.player"
                :seat-index="heroDockRow.seatIndex"
                :box-style="getPlayerBoxStyle(heroDockRow.player, heroDockRow.seatIndex)"
                :act-index="actIndex"
                :stage="stage"
                :community-cards="communityCards"
                :community-cards-flip-complete="communityCardsFlipComplete"
                :is-owner="isOwner"
                :owner-reveal-all="ownerRevealAll"
                :my-nickname="user ? user.nickname : ''"
                :hand-deal-key="currentHandSeed"
                :hole-deal-seat-order="holeDealOrderFromDealer(heroDockRow.seatIndex)"
                :hole-deal-player-count="holeDealPlayerCountForAnim"
                :rival-mini="false"
                :showdown-hand-leaders="showdownHandLeaderNicknames"
                :seat-chat-text="seatChatTextFor(heroDockRow.player.nickname)"
                :skip-hole-deal-animation="true"
                @card-click="onPlayerCardClick"
            />
          </div>
        </div>
      </div>
    </div>

    <div
        v-if="showMobileActionSheet && (isMyTurn || inSettledStage)"
        class="dp-game-sheet-mask dp-game-sheet-mask--bottom"
        role="dialog"
        aria-modal="true"
        :aria-label="inSettledStage ? '准备下一局' : '下注行动'"
        @click.self="showMobileActionSheet = false"
    >
      <div class="dp-game-sheet dp-game-sheet--wide" @click.stop>
        <div class="dp-game-sheet__head">
          <span class="dp-game-sheet__title">{{ inSettledStage ? '准备下一局' : '本轮行动' }}</span>
          <button
              type="button"
              class="dp-game-sheet__close"
              aria-label="关闭"
              @click="showMobileActionSheet = false"
          >
            ×
          </button>
        </div>
        <div class="dp-game-sheet__body dp-game-sheet__body--action">
          <game-action-panel
              :settled-prepare="inSettledStage"
              :ready-time-left="readyTimeLeft"
              :my-ready="myReady"
              :time-left="timeLeft"
              :current-bet-to-call="currentBetToCall"
              :my-bet="myBet"
              :call-amount="callAmount"
              :small-blind="smallBlind"
              :big-blind="bigBlind"
              :min-raise="minRaise"
              :min-total-to-raise="minTotalToRaise"
              :last-raise-increment="lastRaiseIncrementEffective"
              :pot="pot"
              :my-chips="myChips"
              :raise-amount.sync="raiseAmount"
              @call="doCall"
              @raise="doRaise"
              @all-in="doAllIn"
              @fold="doFold"
              @toggle-ready="toggleReady"
              @rebuy="rebuy"
          />
        </div>
      </div>
    </div>

    <div
        v-if="showOwnerHubSheet && isOwner"
        class="dp-game-sheet-mask dp-game-sheet-mask--bottom"
        role="dialog"
        aria-modal="true"
        aria-label="房主操作"
        @click.self="closeOwnerHubPanel"
    >
      <div class="dp-game-sheet dp-game-sheet--wide" @click.stop>
        <div class="dp-game-sheet__head">
          <span class="dp-game-sheet__title">房主操作</span>
          <button
              type="button"
              class="dp-game-sheet__close"
              aria-label="关闭"
              @click="closeOwnerHubPanel"
          >
            ×
          </button>
        </div>
        <div class="dp-game-sheet__body dp-game-sheet__body--owner-hub">
          <game-owner-panel
              hide-title
              hide-tool-entry
              in-sheet
              :stage="stage"
              :pots="pots"
              :pot="pot"
              :pot-winners="potWinners"
              :selected-winners="selectedWinners"
              :all-pots-have-winners="allPotsHaveWinners"
              @toggle-pot-winner="onTogglePotWinnerPayload"
              @confirm-pot-judge="confirmPotJudge"
              @confirm-judge-win="confirmJudgeWin"
          />
          <game-owner-tool-modal
              :embedded="true"
              :visible="true"
              :owner-reveal-all.sync="ownerRevealAll"
              :owner-tool-type.sync="ownerToolType"
              :owner-action-target.sync="ownerActionTarget"
              :owner-action-players="ownerActionPlayers"
              :demo-bot-adding="demoBotAdding"
              :demo-bot-added-tip="demoBotAddedTip"
              :maniac-bot-adding="maniacBotAdding"
              :maniac-bot-added-tip="maniacBotAddedTip"
              :tag-bot-adding="tagBotAdding"
              :tag-bot-added-tip="tagBotAddedTip"
              :shark-bot-adding="sharkBotAdding"
              :shark-bot-added-tip="sharkBotAddedTip"
              :llm-bot-adding="llmBotAdding"
              :llm-bot-added-tip="llmBotAddedTip"
              @close="closeOwnerHubPanel"
              @add-demo-bot="addDemoBot"
              @add-maniac-bot="addManiacBot"
              @add-tag-bot="addTagBot"
              @add-shark-bot="addSharkBot"
              @add-llm-bot="addLlmBot"
              @transfer-owner="doTransferOwner"
              @kick-player="doKickPlayer"
          />
        </div>
      </div>
    </div>

  </div>
</template>

<script>
import '../styles/dp-game-themes.css'
import '../styles/dp-game-shell.css'
import '../styles/dp-game-modals.css'
import '../styles/dp-game-element-ui.css'
import '../styles/dp-game-eco-mode.css'
import { GAME_UI_THEMES } from '../constants/dpGameThemes'
import { readGameTheme, writeGameTheme } from '../utils/dpGameTheme'
import { readEcoMode, writeEcoMode } from '../utils/dpGameEcoMode'
import GamePlayerCard from './GamePlayerCard.vue'
import GameTopBar from './GameTopBar.vue'
import GameHandRankModal from './GameHandRankModal.vue'
import GameSpectatorModal from './GameSpectatorModal.vue'
import GameHandHistoryModal from './GameHandHistoryModal.vue'
import GameOwnerToolModal from './GameOwnerToolModal.vue'
import GameCommunityCards from './GameCommunityCards.vue'
import GameActionPanel from './GameActionPanel.vue'
import GameOwnerPanel from './GameOwnerPanel.vue'
import { HAND_RANK_REFERENCE } from '../constants/dpGameHandRankReference'
import { pickShowdownLeaderNicknames } from '../utils/dpGameHandRank'
import { dpDisplayNickname } from '../utils/dpDisplayNickname'
import { playSettlementMusic, stopSettlementMusic } from '../utils/dpGameSettlementMusic'

export default {
  provide() {
    return {
      dpGameView: this
    }
  },
  components: {
    GamePlayerCard,
    GameTopBar,
    GameHandRankModal,
    GameSpectatorModal,
    GameHandHistoryModal,
    GameOwnerToolModal,
    GameCommunityCards,
    GameActionPanel,
    GameOwnerPanel
  },
  data() {
    return {
      gameUiTheme: readGameTheme(),
      /** 用户勾选：减轻动画/模糊/GPU 压力，存 localStorage */
      ecoMode: readEcoMode(),
      gameThemeOptions: GAME_UI_THEMES,
      roomId: '',
      user: null,

      // 房间数据（对应后端 DpRoom 字段）
      /** 每手牌唯一，用于前端手牌「庄位发牌」动画（与 newHand 时 currentHandSeed 一致） */
      currentHandSeed: 0,
      owner: '',
      players: [],
      playing: false,
      stage: 'preflop',
      communityCards: [],
      pot: 0,
      pots: [],             // 主池+边池列表 [{amount, eligiblePlayers}]
      currentBetToCall: 0,
      /** 与后端 DpRoom.lastRaiseIncrement 一致：当前圈最小再加注的「增量」基准 */
      lastRaiseIncrement: 10,
      actIndex: -1,
      // 观众席名单（由后端 DpRoom.spectators 提供）
      spectators: [],

      // UI
      raiseAmount: 0,
      selectedWinners: [],   // 旧的简单模式备用
      potWinners: {},        // 按池选赢家 { 0: ['Alice'], 1: ['Bob','Charlie'] }
      nextHandReady: false,  // 是否已报名下一局加入
      loading: false,

      // 公共牌翻转动画：每个下标 true=已翻开，false=未翻开
      communityCardsFlipState: [],
      // 公共牌全部翻完后再显示牌型，增强沉浸感
      communityCardsFlipComplete: false,
      communityCardsFlipCompleteTimer: null,

      // 游戏对局 WebSocket（与后端 /ws/dp-game 同步，无 Redis）
      gameWs: null,
      gameWsConnected: false,

      /** 房间聊天：按昵称只保留一条文案（新发顶掉旧），到期移除 */
      seatChatTextByNick: {},
      chatInputDraft: '',

      // 定时器
      pollTimer: null,
      backupPollTimer: null,
      heartbeatTimer: null,
      //游戏计时器
      actionTimer: null,
      /** 与 syncActionCountdown 配合：同一行动者会话内轮询不重置秒数 */
      _actionCountdownKey: null,
      timeLeft: 30,
      // 结算后准备阶段倒计时
      readyTimer: null,
      readyTimeLeft: 30,

      // 牌型说明弹窗
      showHandRankModal: false,
      showSpectatorModal: false,
      showHandHistoryModal: false,
      // 房主踢人/移交房主弹窗
      showOwnerHubSheet: false,
      ownerToolType: 'transfer',  // 'transfer' | 'kick'
      ownerActionTarget: '',      // 当前选择的目标玩家昵称
      // 演示用 NPC 状态（仅前端提示用）
      demoBotAdding: false,
      demoBotAddedTip: '',
      // 疯子型 NPC 状态
      maniacBotAdding: false,
      maniacBotAddedTip: '',
      // 紧凶型 NPC 状态
      tagBotAdding: false,
      tagBotAddedTip: '',
      // 聪明型 NPC 状态
      sharkBotAdding: false,
      sharkBotAddedTip: '',
      // 大模型 NPC 状态
      llmBotAdding: false,
      llmBotAddedTip: '',

      // 房主专用：一键看穿所有人底牌（仅本机显示，不影响后端和 NPC 决策）
      ownerRevealAll: false,

      /** 窄屏：底部弹层查看手牌 / 行动 */
      showMobileHandSheet: false,
      showMobileActionSheet: false,

      /** 是否处于浏览器全屏（整页对局根节点） */
      isFullscreen: false,
      /** 无 Fullscreen API 时的铺满视口回退（常见于部分 iOS WebView） */
      pseudoFullscreen: false,

      /** 本手是否已触发结算 BGM（与 currentHandSeed 对齐，避免轮询重复 play） */
      _settlementMusicStartedForHand: null
    }
  },

  computed: {
    /** 当前环境是否暴露全屏 API（部分移动端 WebView 不可用） */
    dpFullscreenApiSupported() {
      var d = document
      return !!(
        d.fullscreenEnabled ||
        d.webkitFullscreenEnabled ||
        d.mozFullScreenEnabled ||
        d.msFullscreenEnabled
      )
    },
    /** 原生全屏或伪全屏任一开启时，圆桌使用加宽布局 */
    layoutFullscreen() {
      return this.isFullscreen || this.pseudoFullscreen
    },
    handRankReference() {
      return HAND_RANK_REFERENCE
    },
    stageCN() {
      var m = {preflop: '翻牌前', flop: '翻牌圈', turn: '转牌圈', river: '河牌圈', showdown: '摊牌结算', settled: '准备下一局'}
      return m[this.stage] || this.stage
    },
    isOwner() {
      return this.user && this.owner === this.user.nickname
    },
    isMyTurn() {
      if (!this.user || this.actIndex < 0 || this.actIndex >= this.players.length) return false
      return this.players[this.actIndex].nickname === this.user.nickname
    },
    myPlayer() {
      if (!this.user) return null
      return this.players.find(function (p) {
        return p.nickname === this.user.nickname
      }.bind(this)) || null
    },
    // 是否显示“准备在下一局加入对局”：纯观众 或 本手已退出的僵尸位（退了又进来）都显示，便于立刻报名下一局
    showSpectatorPrepareBlock() {
      if (!this.myPlayer) return true
      return !!this.myPlayer.leftThisHand
    },
    myReady() {
      return this.myPlayer ? this.myPlayer.ready : false
    },
    myChips() {
      return this.myPlayer ? this.myPlayer.chips : 0
    },
    myBet() {
      return this.myPlayer ? this.myPlayer.bet : 0
    },
    callAmount() {
      return Math.max(0, this.currentBetToCall - this.myBet)
    },
    smallBlind() {
      // 使用与后端一致的小盲配置，默认 5，后续可从服务端房间配置透传
      return 5
    },
    bigBlind() {
      // 使用与后端一致的大盲配置，默认 10，后续可从服务端房间配置透传
      return 10
    },
    /** 后端下发的加注增量，异常时回落为大盲 */
    lastRaiseIncrementEffective() {
      var v = Number(this.lastRaiseIncrement)
      if (!isFinite(v) || v < 1) return this.bigBlind
      return Math.floor(v)
    },
    /** 标准 NL：合法加注后「本街总注」至少为该值（仅展示/兼容；桌上最小再加注已临时关闭） */
    minTotalToRaise() {
      return this.currentBetToCall + this.lastRaiseIncrementEffective
    },
    /**
     * 本轮至少再下多少筹码：临时关闭标准最小再加注，只要比跟注多 1（或无人跟注压力时至少 1）即可抬升，与后端 DpRoomServiceImpl 注释掉的校验一致。
     */
    minRaise() {
      var call = this.callAmount
      if (!isFinite(call) || call < 0) call = 0
      var fullMin = call > 0 ? call + 1 : 1
      var cap = Math.min(fullMin, this.myChips)
      return Math.max(1, cap)
    },
    allPotsHaveWinners() {
      if (this.pots.length === 0) return false
      for (var i = 0; i < this.pots.length; i++) {
        if (!this.potWinners[i] || this.potWinners[i].length === 0) return false
      }
      return true
    },
    // 当前是否处于“结算完成，等待准备下一局”阶段（僵尸位不算，他们走观众区的“准备在下一局加入对局”）
    inSettledStage() {
      return this.stage === 'settled' && !!this.myPlayer && !this.myPlayer.leftThisHand
    },
    // 房主神器中可操作的玩家列表：排除房主自己和僵尸位
    ownerActionPlayers() {
      return this.players.filter(function (p) {
        return !p.leftThisHand && p.nickname !== this.owner
      }.bind(this))
    },
    /**
     * 网格展示顺序：从「自己」起按座位顺时针展开（与 players 数组顺序一致，只是旋转起点）。
     * seatIndex 仍为原数组下标，供 actIndex、庄位发牌动画等与后端一致。
     */
    playersDisplayOrder() {
      var list = this.players
      if (!list || list.length === 0) return []
      var myNick = this.user && this.user.nickname
      var start = 0
      if (myNick) {
        var idx = list.findIndex(function (p) {
          return p.nickname === myNick
        })
        if (idx >= 0) start = idx
      }
      var out = []
      for (var j = 0; j < list.length; j++) {
        var seatIndex = (start + j) % list.length
        out.push({ player: list[seatIndex], seatIndex: seatIndex })
      }
      return out
    },
    /** 当前登录用户是否在本桌玩家列表中（非观众席只看桌时用于整圈均分座位） */
    viewerSeatedAtTable() {
      var nick = this.user && this.user.nickname
      if (!nick || !this.players || !this.players.length) return false
      return this.players.some(function (p) {
        return p.nickname === nick
      })
    },
    /** 手牌两圈发牌动画与公共牌 stagger 对齐时的桌上人数 */
    holeDealPlayerCountForAnim() {
      if (!this.players || !this.players.length) return 1
      return this.players.length
    },
    /** 本人完整卡片放在圆桌下方时，取 displayOrder 中「自己」那一项（与 seatIndex 一致） */
    heroDockRow() {
      if (!this.viewerSeatedAtTable) return null
      var order = this.playersDisplayOrder
      if (!order || !order.length) return null
      return order[0]
    },
    /**
     * 摊牌 / 准备下一局阶段桌上牌力最高者昵称列表（含踢脚比较；平局时并列者全部列出）。
     * settled 时仍展示上一手公共牌与牌型，须与 showdown 共用同一套领先者逻辑。
     */
    showdownHandLeaderNicknames() {
      if (this.stage !== 'showdown' && this.stage !== 'settled') return []
      if (!this.players || !this.players.length) return []
      if (!this.communityCards || this.communityCards.length < 3) return []
      var boardReady =
        this.communityCardsFlipComplete
        || this.communityCards.length >= 5
        || this.stage === 'settled'
      if (!boardReady) return []
      return pickShowdownLeaderNicknames(this.players, this.communityCards)
    },
    /** 当前在桌上 players 里的昵称集合之外，仍可能有观众聊天，在操作区上方展示 */
    spectatorSeatChatEntries() {
      var map = this.seatChatTextByNick
      if (!map || typeof map !== 'object') return []
      var seated = {}
      var players = this.players || []
      for (var i = 0; i < players.length; i++) {
        var n = players[i] && players[i].nickname
        if (n) seated[n] = true
      }
      var out = []
      for (var k in map) {
        if (!Object.prototype.hasOwnProperty.call(map, k)) continue
        if (seated[k]) continue
        out.push({ nickname: k, text: map[k] })
      }
      return out
    },
    /** 下注街有人行动时，桌面中央展示倒计时（全场可见；秒数与操作区一致） */
    showTableActionTimer() {
      return this.actionCountdownShouldRun()
    },
    tableActionActorDisplayName() {
      var i = this.actIndex
      var list = this.players
      if (i < 0 || !list || i >= list.length) return '—'
      var p = list[i]
      if (!p || !p.nickname) return '—'
      return dpDisplayNickname(p.nickname)
    },
    /** 圆环进度：剩余比例 0~1 */
    actionTimerProgressPct() {
      var t = Number(this.timeLeft)
      if (isNaN(t) || t < 0) return 0
      return Math.min(1, t / 30)
    },
    tableActionTimerUrgency() {
      var t = Number(this.timeLeft)
      if (isNaN(t)) return 'ok'
      if (t > 10) return 'ok'
      if (t > 5) return 'warn'
      return 'danger'
    },
    /** 窄屏底栏占位：避免固定底栏挡住聊天与房主区 */
    mobileHeroDockActive() {
      return !!(this.heroDockRow || this.isMyTurn || this.inSettledStage || this.isOwner)
    }
  },

  watch: {
    gameUiTheme: function (id) {
      writeGameTheme(id)
      this.syncBodyDpGameTheme()
    },
    ecoMode: function (on) {
      writeEcoMode(!!on)
    },
    isMyTurn: function (v) {
      if (v) this.raiseAmount = this.minRaise
      else this.showMobileActionSheet = false
    },
    heroDockRow: function (row) {
      if (!row) this.showMobileHandSheet = false
    },
    minRaise: function () {
      if (this.isMyTurn && this.raiseAmount < this.minRaise) {
        this.raiseAmount = this.minRaise
      }
    },
    actIndex() {
      this.syncActionCountdown()
    },
    playing() {
      this.syncActionCountdown()
    },
    currentHandSeed() {
      this.syncActionCountdown()
    },
    // 监听阶段变化，用于控制结算后准备阶段的倒计时
    stage(newVal) {
      this.syncActionCountdown()
      if (newVal === 'settled') {
        this.startReadyCountdown()
      } else {
        this.stopReadyCountdown()
        this.showMobileActionSheet = false
      }
    },
    isOwner(v) {
      if (!v) this.showOwnerHubSheet = false
    }
  },

    created() {
    this._seatChatTimers = Object.create(null)
    this.roomId = this.$route.params.roomId
    this.syncBodyDpGameTheme()

    var raw = localStorage.getItem('userInfo')
    if (!raw) {
      this.$message.error('登录信息丢失，请重新登录')
      this.$router.push('/login')
      return
    }
    this.user = JSON.parse(raw)

    // 先 HTTP 拉一次，再建立 WebSocket（推送与定时器同 1s 节奏）
    this.loadGame().then(function () {
      this.connectGameWs()
    }.bind(this))

    // 未连上 WS 时 1 秒轮询兜底；握手过程中 readyState===CONNECTING 也要停掉，否则会连着打一串 getNowRoom
    this.pollTimer = setInterval(function () {
      if (this.loading) return
      if (this.gameWsConnected) return
      if (this.gameWs && this.gameWs.readyState === WebSocket.CONNECTING) return
      this.loadGame()
    }.bind(this), 1000)

    // 已连上 WS 时低频 HTTP 兜底（防止长连异常而界面停滞）
    this.backupPollTimer = setInterval(function () {
      if (!this.loading && this.gameWsConnected) this.loadGame()
    }.bind(this), 15000)

    // 5秒独立心跳（和 loadGame 解耦，loadGame 失败不影响心跳）
    this.sendHeartbeat()
    this.heartbeatTimer = setInterval(function () {
      this.sendHeartbeat()
    }.bind(this), 5000)
  },

  mounted() {
    this._dpFsChange = this.syncDpFullscreenState.bind(this)
    document.addEventListener('fullscreenchange', this._dpFsChange)
    document.addEventListener('webkitfullscreenchange', this._dpFsChange)
    this.syncDpFullscreenState()
    this.wrapDpMessageForFullscreenOverlays()
    var self = this
    this.$nextTick(function () {
      self.tryEnterDpFullscreen()
      self.scheduleReparentElementUiLayersIntoFullscreenRoot()
    })
  },

  beforeDestroy() {
    stopSettlementMusic()
    this.clearBodyDpGameTheme()
    if (this._dpFsChange) {
      document.removeEventListener('fullscreenchange', this._dpFsChange)
      document.removeEventListener('webkitfullscreenchange', this._dpFsChange)
      this._dpFsChange = null
    }
    this.exitDpFullscreenIfActive()
    this.setPseudoFullscreen(false)
    this.disconnectGameWs()
    if (this.pollTimer) clearInterval(this.pollTimer)
    if (this.backupPollTimer) clearInterval(this.backupPollTimer)
    if (this.heartbeatTimer) clearInterval(this.heartbeatTimer)
    if (this.actionTimer) clearInterval(this.actionTimer)
    if (this.readyTimer) clearInterval(this.readyTimer)
    if (this.communityCardsFlipCompleteTimer) clearTimeout(this.communityCardsFlipCompleteTimer)
    if (this._seatChatTimers) {
      var self = this
      Object.keys(this._seatChatTimers).forEach(function (k) {
        clearTimeout(self._seatChatTimers[k])
      })
      this._seatChatTimers = Object.create(null)
    }
  },

  methods: {
    /** 将当前界面主题同步到 document.body，使挂在 body 上的 Element MessageBox / Message / v-modal 继承 --dp-* */
    syncBodyDpGameTheme() {
      try {
        document.body.setAttribute('data-dp-game-theme', this.gameUiTheme || 'default')
      } catch (e) { /* ignore */ }
    },
    clearBodyDpGameTheme() {
      try {
        document.body.removeAttribute('data-dp-game-theme')
      } catch (e) { /* ignore */ }
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

    syncDpFullscreenState() {
      var root = this.$refs.gameRoot
      var active = document.fullscreenElement || document.webkitFullscreenElement
      this.isFullscreen = !!(root && active === root)
      if (this.isFullscreen && this.pseudoFullscreen) {
        this.setPseudoFullscreen(false)
      }
    },

    exitDpFullscreenIfActive() {
      var root = this.$refs.gameRoot
      var active = document.fullscreenElement || document.webkitFullscreenElement
      if (!this.isFullscreen && !(root && active === root)) return
      if (!active || (root && active !== root)) return
      var swallow = function (p) {
        if (p && typeof p.then === 'function') {
          p.catch(function () {
            /* 路由销毁/切 tab 时常见：Document not active；同步错误见 try/catch */
          })
        }
      }
      try {
        if (document.exitFullscreen) swallow(document.exitFullscreen())
        else if (document.webkitExitFullscreen) swallow(document.webkitExitFullscreen())
      } catch (e) { /* ignore */ }
    },

    setPseudoFullscreen(on) {
      this.pseudoFullscreen = !!on
      try {
        document.body.style.overflow = on ? 'hidden' : ''
      } catch (e) { /* ignore */ }
    },

    /** 进入对局时自动全屏（失败则伪全屏）；与顶栏「全屏」共用逻辑 */
    tryEnterDpFullscreen() {
      var root = this.$refs.gameRoot
      if (!root || this.layoutFullscreen) return
      var self = this
      if (!this.dpFullscreenApiSupported) {
        this.setPseudoFullscreen(true)
        return
      }
      var req =
        root.requestFullscreen ||
        root.webkitRequestFullscreen ||
        root.mozRequestFullScreen ||
        root.msRequestFullscreen
      if (!req) {
        this.setPseudoFullscreen(true)
        return
      }
      Promise.resolve(req.call(root)).catch(function (e) {
        console.error('进入全屏失败', e)
        self.setPseudoFullscreen(true)
      })
    },

    toggleDpFullscreen() {
      var root = this.$refs.gameRoot
      if (!root) return
      var self = this

      if (this.layoutFullscreen) {
        if (this.isFullscreen) {
          Promise.resolve(
            document.exitFullscreen
              ? document.exitFullscreen()
              : document.webkitExitFullscreen
                ? document.webkitExitFullscreen()
                : null
          ).catch(function (e) {
            console.error('退出全屏失败', e)
            self.$message.warning('无法退出全屏')
          })
        }
        if (this.pseudoFullscreen) this.setPseudoFullscreen(false)
        return
      }

      this.tryEnterDpFullscreen()
    },

    /**
     * 浏览器原生全屏时，只有全屏元素子树会显示。Element UI 的 MessageBox / $message 默认挂在 body 上，
     * 用户在全屏里看不到；退出全屏后才“突然”出现在页面上方。将相关节点移入对局根节点即可。
     */
    reparentElementUiLayersIntoFullscreenRoot() {
      var root = this.$refs.gameRoot
      if (!root || !this.isFullscreen) return
      var moveIfOutside = function (node) {
        if (!node || !node.parentNode || root.contains(node)) return
        root.appendChild(node)
      }
      var w = 0
      var wrappers = document.querySelectorAll('.el-message-box__wrapper')
      for (w = 0; w < wrappers.length; w++) {
        moveIfOutside(wrappers[w])
      }
      var modals = document.getElementsByClassName('v-modal')
      for (w = 0; w < modals.length; w++) {
        moveIfOutside(modals[w])
      }
      var msgs = document.querySelectorAll('.el-message')
      for (w = 0; w < msgs.length; w++) {
        moveIfOutside(msgs[w])
      }
    },

    scheduleReparentElementUiLayersIntoFullscreenRoot() {
      var self = this
      if (!self.isFullscreen) return
      var run = function () {
        self.reparentElementUiLayersIntoFullscreenRoot()
      }
      self.$nextTick(function () {
        run()
        if (typeof requestAnimationFrame === 'function') {
          requestAnimationFrame(run)
        }
        setTimeout(run, 0)
        setTimeout(run, 50)
      })
    },

    /**
     * 仅本页实例：在全屏下把 $message 生成的节点也移入 gameRoot（与 dpConfirm 一致）。
     */
    wrapDpMessageForFullscreenOverlays() {
      if (this._dpMessageFullscreenWrapDone) return
      var raw = this.$message
      if (!raw || typeof raw !== 'function') return
      var self = this
      var wrapped = function () {
        var ret = raw.apply(raw, arguments)
        self.scheduleReparentElementUiLayersIntoFullscreenRoot()
        return ret
      }
      var k
      for (k in raw) {
        if (!Object.prototype.hasOwnProperty.call(raw, k) || typeof raw[k] !== 'function') continue
        wrapped[k] = (function (methodName) {
          return function () {
            var ret = raw[methodName].apply(raw, arguments)
            self.scheduleReparentElementUiLayersIntoFullscreenRoot()
            return ret
          }
        })(k)
      }
      this.$message = wrapped
      this._dpMessageFullscreenWrapDone = true
    },

    /**
     * 使用 Element 弹层替代 window.confirm，避免原生对话框打断当前页面状态。
     */
    dpConfirm(text, title, options) {
      var o = Object.assign({
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
        closeOnClickModal: false
      }, options || {})
      var p = this.$confirm(text, title || '请确认', o)
      this.scheduleReparentElementUiLayersIntoFullscreenRoot()
      return p
    },

    // ---- 心跳（独立，不依赖 loadGame） ----
    sendHeartbeat() {
      if (!this.user) return
      this.$http.post('/dpRoom/heartbeat', null, {
        params: {roomId: this.roomId, nickname: this.user.nickname}
      }).catch(function (e) {
        console.error('心跳失败', e)
      })
    },

    gameWsBaseUrl() {
      // 与页面同源；开发时游戏 WS 走 vue.config.js 的 /dp-ws → 后端 /ws
      var secure = window.location.protocol === 'https:'
      return (secure ? 'wss:' : 'ws:') + '//' + window.location.host
    },

    connectGameWs() {
      this.disconnectGameWs()
      // 开发服：走 /dp-ws → vue 代理转成后端 /ws（避免与 webpack HMR 的 /ws 冲突）
      var path = process.env.NODE_ENV === 'development' ? '/dp-ws/dp-game' : '/ws/dp-game'
      var url = this.gameWsBaseUrl() + path + '?roomId=' + encodeURIComponent(this.roomId)
      try {
        var ws = new WebSocket(url)
        this.gameWs = ws
        var self = this
        ws.onopen = function () {
          self.gameWsConnected = true
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
            self.applyRoomFromServer(data)
          } catch (e) {
            console.error('WebSocket 消息解析失败', e)
          }
        }
        ws.onclose = function () {
          self.gameWsConnected = false
          if (self.gameWs === ws) self.gameWs = null
        }
        ws.onerror = function (e) {
          console.error('WebSocket 错误', e)
        }
      } catch (e) {
        console.error('WebSocket 连接失败', e)
      }
    },

    disconnectGameWs() {
      if (this.gameWs) {
        try {
          this.gameWs.close()
        } catch (e) { /* ignore */ }
        this.gameWs = null
      }
      this.gameWsConnected = false
    },

    handleRoomClosedFromServer() {
      if (this._dpRoomClosedHandled) return
      this._dpRoomClosedHandled = true
      var self = this
      this.disconnectGameWs()
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

    pushRoomChatFromServer(data) {
      var nick = (data.nickname || '').trim()
      var text = (data.text != null ? String(data.text) : '').trim()
      if (!nick || !text) return
      var ttl = typeof data.ttlMs === 'number' && data.ttlMs > 0 ? data.ttlMs : 15000
      var prev = this._seatChatTimers[nick]
      if (prev) {
        clearTimeout(prev)
        delete this._seatChatTimers[nick]
      }
      this.$set(this.seatChatTextByNick, nick, text)
      var self = this
      var tid = setTimeout(function () {
        if (self.seatChatTextByNick[nick] === text) {
          self.$delete(self.seatChatTextByNick, nick)
        }
        delete self._seatChatTimers[nick]
      }, ttl)
      this._seatChatTimers[nick] = tid
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
          nickname: this.user.nickname,
          text: t
        }))
        this.chatInputDraft = ''
      } catch (e) {
        console.error('发送聊天失败', e)
        this.$message.error('发送失败')
      }
    },

    applyRoomFromServer(room) {
      this.owner = room.owner
      this.players = room.players || []
      this.playing = room.playing
      this.currentHandSeed = room.currentHandSeed != null ? room.currentHandSeed : 0
      this.stage = room.currentStage
      this.syncSettlementMusic()
      this.communityCards = room.communityCards || []
      this.syncCommunityCardsFlipState(room.communityCards || [])
      this.pot = room.pot
      this.pots = room.pots || []
      this.currentBetToCall = room.currentBetToCall
      this.lastRaiseIncrement =
        room.lastRaiseIncrement != null ? room.lastRaiseIncrement : this.bigBlind
      this.actIndex = room.currentActorIndex
      this.spectators = room.spectators || []
      var list = room.waitNextHand || []
      this.nextHandReady = !!(this.user && list.indexOf(this.user.nickname) !== -1)
      this.$nextTick(function () {
        this.syncActionCountdown()
        if (this.isMyTurn && this.raiseAmount < this.minRaise) {
          this.raiseAmount = this.minRaise
        }
      }.bind(this))
    },

    /**
     * 摊牌/准备下一局阶段播放结算 BGM；进入新一手（preflop）或非结算街时停止。
     */
    syncSettlementMusic() {
      if (!this.playing) {
        stopSettlementMusic()
        return
      }
      var st = this.stage
      var seed = this.currentHandSeed
      if (st === 'showdown' || st === 'settled') {
        if (this._settlementMusicStartedForHand !== seed) {
          this._settlementMusicStartedForHand = seed
          playSettlementMusic()
        }
      } else {
        stopSettlementMusic()
      }
    },

    // ---- 拉取房间状态 ----
    async loadGame() {
      this.loading = true
      try {
        var res = await this.$http.get('/dpRoom/getNowRoom', {
          params: {roomId: this.roomId}
        })
        var room = res.data
        if (!room) {
          this.handleRoomClosedFromServer()
          return
        }
        this.applyRoomFromServer(room)
      } catch (err) {
        console.error('拉取状态失败', err)
      } finally {
        this.loading = false
      }
    },

    // ---- 准备/取消准备 ----
    async toggleReady() {
      try {
        var res = await this.$http.post('/dpRoom/toggleReady', null, {
          params: {roomId: this.roomId, nickname: this.user.nickname}
        })
        if (res.data !== 'ok') this.$message.error('操作失败')
        await this.loadGame()
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },
    // 结算后筹码归零时补码
    async rebuy() {
      try {
        var res = await this.$http.post('/dpRoom/rebuy', null, {
          params: {roomId: this.roomId, nickname: this.user.nickname}
        })
        if (res.data !== 'ok') {
          this.$message.error('补码失败：' + res.data)
        } else {
          this.$message.success('补码成功，可以准备下一局了')
        }
        await this.loadGame()
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },

    // ---- 跟注/过牌 ----
    async doCall() {
      await this.submitBet(this.callAmount)
    },

    // ---- 加注 ----
    async doRaise() {
      if (this.raiseAmount < this.minRaise) {
        this.$message.warning('加注额不能低于 ' + this.minRaise)
        return
      }
      if (this.raiseAmount > this.myChips) {
        this.$message.warning('筹码不足！')
        return
      }
      await this.submitBet(this.raiseAmount)
    },

    // ---- All-In ----
    async doAllIn() {
      await this.submitBet(this.myChips)
    },

    // ---- 统一提交下注 ----
    async submitBet(amount) {
      try {
        var res = await this.$http.post('/dpRoom/bet', null, {
          params: {roomId: this.roomId, nickname: this.user.nickname, bet: amount}
        })
        if (res.data !== 'ok') this.$message.error('下注失败，请检查金额')
        this.raiseAmount = 0
        await this.loadGame()
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },

    // ---- 弃牌 ----
    async doFold() {
      try {
        var res = await this.$http.post('/dpRoom/fold', null, {
          params: {roomId: this.roomId, nickname: this.user.nickname}
        })
        if (res.data !== 'ok') this.$message.error('弃牌失败')
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

      var idx = this.selectedWinners.indexOf(nickname)
      if (idx > -1) {
        this.selectedWinners.splice(idx, 1)
      } else {
        this.selectedWinners.push(nickname)
      }
    },

    // 统一的玩家卡片点击入口：仅用于摊牌选赢家（房主神器不再通过点卡片）
    onPlayerCardClick(nickname) {
      this.handleJudgeClick(nickname)
    },

    // ---- 按池选赢家 ----
    togglePotWinner(potIndex, nickname) {
      var winners = this.potWinners[potIndex] || []
      var idx = winners.indexOf(nickname)
      if (idx > -1) {
        winners.splice(idx, 1)
      } else {
        winners.push(nickname)
      }
      this.$set(this.potWinners, potIndex, winners)
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
          this.$message.warning('第 ' + (i === 0 ? '主' : i) + ' 池还没选赢家')
          return
        }
        parts.push(i + ':' + winners.join(','))
      }
      var potWinnersStr = parts.join(';')

      // 组装确认信息（HTML 换行，避免原生 confirm 打断全屏）
      var lines = ['确认结算？']
      for (var j = 0; j < this.pots.length; j++) {
        var potName = j === 0 ? '主池' : '边池 ' + j
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
        this.potWinners = {}
        this.selectedWinners = []
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
        await this.dpConfirm('确定由 [' + names + '] 平分底池 ' + this.pot + ' 吗？', '确认结算')
      } catch (e) {
        return
      }

      try {
        var res = await this.$http.post('/dpRoom/judgeWin', null, {
          params: {roomId: this.roomId, winnerNickname: this.selectedWinners.join(',')}
        })
        if (res.data !== 'ok') this.$message.error('结算失败')
        this.selectedWinners = []
        await this.loadGame()
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },

    // ---- 房主神器：底栏入口与底部抽屉 ----
    openOwnerHubSheet() {
      this.ownerToolType = 'transfer'
      this.ownerActionTarget = ''
      this.showOwnerHubSheet = true
      this.demoBotAddedTip = ''
      this.maniacBotAddedTip = ''
      this.tagBotAddedTip = ''
      this.sharkBotAddedTip = ''
      this.llmBotAddedTip = ''
    },

    closeOwnerHubPanel() {
      this.showOwnerHubSheet = false
      this.ownerActionTarget = ''
    },

    // ---- 房主：移交房主（通过弹窗选择玩家） ----
    async doTransferOwner() {
      if (!this.ownerActionTarget) {
        this.$message.warning('请先选择要移交房主的玩家')
        return
      }
      if (this.ownerActionTarget === this.user.nickname) {
        this.$message.warning('不能把房主移交给自己')
        return
      }
      try {
        await this.dpConfirm(
          '确定将房主移交给 [' + dpDisplayNickname(this.ownerActionTarget) + '] 吗？',
          '移交房主'
        )
      } catch (e) {
        return
      }
      try {
        var res = await this.$http.post('/dpRoom/transferOwner', null, {
          params: {
            roomId: this.roomId,
            fromNickname: this.user.nickname,
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

    // ---- 房主：踢人到观众席（通过弹窗选择玩家） ----
    async doKickPlayer() {
      if (!this.ownerActionTarget) {
        this.$message.warning('请先选择要踢出的玩家')
        return
      }
      try {
        await this.dpConfirm(
          '确定将 [' + dpDisplayNickname(this.ownerActionTarget) + '] 踢出本局并移至观众席吗？',
          '踢出玩家'
        )
      } catch (e) {
        return
      }
      try {
        var res = await this.$http.post('/dpRoom/kickPlayer', null, {
          params: {roomId: this.roomId, nickname: this.ownerActionTarget}
        })
        if (res.data !== 'ok') {
          this.$message.error('踢人失败：' + res.data)
        } else {
          this.$message.success('已将 [' + dpDisplayNickname(this.ownerActionTarget) + '] 踢至观众席')
        }
        await this.loadGame()
        this.closeOwnerHubPanel()
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },

    /**
     * 将 DEMO 型 NPC（服务端昵称为 BOT_Fish，界面展示为 BOT_Lag）加入下一局等待列表。
     * 当前用于基础难度练习与流程验证。
     */
    async addDemoBot() {
      if (!this.roomId) return
      this.demoBotAdding = true
      this.demoBotAddedTip = ''
      try {
        var res = await this.$http.post('/dpRoom/addDemoBot', null, {
          params: {roomId: this.roomId}
        })
        if (res.data === 'ok') {
          this.demoBotAddedTip = '已请求在下一局加入 BOT_Lag，请等待本局结束后自动入座。'
        } else {
          this.demoBotAddedTip = '添加 NPC 失败：' + res.data
        }
      } catch (e) {
        this.demoBotAddedTip = '网络错误：' + (e && e.message ? e.message : e)
      } finally {
        this.demoBotAdding = false
      }
    },

    /**
     * 将疯子型 NPC（BOT_Maniac）加入下一局等待列表。
     */
    async addManiacBot() {
      if (!this.roomId) return
      this.maniacBotAdding = true
      this.maniacBotAddedTip = ''
      try {
        var res = await this.$http.post('/dpRoom/addManiacBot', null, {
          params: {roomId: this.roomId}
        })
        if (res.data === 'ok') {
          this.maniacBotAddedTip = '已请求在下一局加入 BOT_Maniac，请等待本局结束后自动入座。'
        } else {
          this.maniacBotAddedTip = '添加疯子 NPC 失败：' + res.data
        }
      } catch (e) {
        this.maniacBotAddedTip = '网络错误：' + (e && e.message ? e.message : e)
      } finally {
        this.maniacBotAdding = false
      }
    },

    /**
     * 将紧凶型 NPC（BOT_Tag）加入下一局等待列表。
     * 该机器人打得相对紧凶，但不会像 Shark 那样根据对手历史动态调整策略。
     */
    async addTagBot() {
      if (!this.roomId) return
      this.tagBotAdding = true
      this.tagBotAddedTip = ''
      try {
        var res = await this.$http.post('/dpRoom/addTagBot', null, {
          params: {roomId: this.roomId}
        })
        if (res.data === 'ok') {
          this.tagBotAddedTip = '已请求在下一局加入 BOT_Tag，请等待本局结束后自动入座。'
        } else {
          this.tagBotAddedTip = '添加紧凶 NPC 失败：' + res.data
        }
      } catch (e) {
        this.tagBotAddedTip = '网络错误：' + (e && e.message ? e.message : e)
      } finally {
        this.tagBotAdding = false
      }
    },

    /**
     * 将聪明型 NPC（BOT_Shark）加入下一局等待列表。
     * 该机器人会根据对手最近几手的行为粗略判断其风格，调整自己的弃牌/跟注/加注倾向。
     */
    async addSharkBot() {
      if (!this.roomId) return
      this.sharkBotAdding = true
      this.sharkBotAddedTip = ''
      try {
        var res = await this.$http.post('/dpRoom/addSharkBot', null, {
          params: {roomId: this.roomId}
        })
        if (res.data === 'ok') {
          this.sharkBotAddedTip = '已请求在下一局加入 BOT_Shark，请等待本局结束后自动入座。'
        } else {
          this.sharkBotAddedTip = '添加聪明 NPC 失败：' + res.data
        }
      } catch (e) {
        this.sharkBotAddedTip = '网络错误：' + (e && e.message ? e.message : e)
      } finally {
        this.sharkBotAdding = false
      }
    },

    /**
     * 将大模型 NPC（BOT_LLM）加入下一局等待列表（后端 /dpRoom/addLlmBot）。
     */
    async addLlmBot() {
      if (!this.roomId) return
      this.llmBotAdding = true
      this.llmBotAddedTip = ''
      try {
        var res = await this.$http.post('/dpRoom/addLlmBot', null, {
          params: {roomId: this.roomId}
        })
        if (res.data === 'ok') {
          this.llmBotAddedTip = '已请求在下一局加入 BOT_LLM，请等待本局结束后自动入座（需配置服务端方舟密钥）。'
        } else {
          this.llmBotAddedTip = '添加大模型 NPC 失败：' + res.data
        }
      } catch (e) {
        this.llmBotAddedTip = '网络错误：' + (e && e.message ? e.message : e)
      } finally {
        this.llmBotAdding = false
      }
    },

    openHandHistory() {
      this.showHandHistoryModal = true
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
      try {
        await this.$http.post('/dpRoom/exitRoom', null, {
          params: {roomId: this.roomId, nickname: this.user.nickname}
        })
      } catch (err) {
        console.error('退出失败', err)
      }
      clearInterval(this.pollTimer)
      clearInterval(this.heartbeatTimer)
      this.navigateHomeIfNeeded()
    },

    // ---- 观众：报名在下一局加入 ----
    async readyNextHand() {
      if (!this.user) return
      try {
        var rp = { roomId: this.roomId, nickname: this.user.nickname }
        if (this.user.userId != null && this.user.userId !== '') {
          rp.userId = this.user.userId
        }
        var res = await this.$http.post('/dpRoom/readyNextHand', null, {
          params: rp
        })
        if (res.data === 'ok') {
          this.nextHandReady = true
          this.$message.success('已报名下一局，将在下一局开局时自动加入对局')
        } else {
          this.$message.error('报名失败：' + res.data)
        }
      } catch (err) {
        this.$message.error('网络错误: ' + err.message)
      }
    },

    /**
     * 同步公共牌翻转状态：新牌先背面，再依次翻转；翻完后再允许显示牌型
     */
    syncCommunityCardsFlipState(newCards) {
      if (this.communityCardsFlipCompleteTimer) {
        clearTimeout(this.communityCardsFlipCompleteTimer)
        this.communityCardsFlipCompleteTimer = null
      }
      var prevLen = this.communityCardsFlipState.length
      if (newCards.length < prevLen) {
        this.communityCardsFlipState = []
        this.communityCardsFlipComplete = false
        prevLen = 0
      }
      var numNew = newCards.length - prevLen
      if (numNew > 0) {
        this.communityCardsFlipComplete = false
      }
      for (var i = this.communityCardsFlipState.length; i < newCards.length; i++) {
        this.communityCardsFlipState.push(false)
      }
      for (var j = prevLen; j < newCards.length; j++) {
        var self = this
        ;(function (capturedIdx, capturedDelay) {
          setTimeout(function () {
            if (self.communityCardsFlipState.length > capturedIdx) {
              self.$set(self.communityCardsFlipState, capturedIdx, true)
            }
          }, capturedDelay)
        })(j, 520 + 350 * (j - prevLen))
      }
      if (numNew > 0) {
        var flipDuration = 800
        /* 等对应公共牌飞入动画（约 0.48s）后再翻牌，与 GameCommunityCards 发牌间隔一致 */
        var lastFlipStart = 520 + 350 * (numNew - 1)
        var self = this
        this.communityCardsFlipCompleteTimer = setTimeout(function () {
          self.communityCardsFlipComplete = true
          self.communityCardsFlipCompleteTimer = null
        }, lastFlipStart + flipDuration)
      } else if (newCards.length > 0 && this.communityCardsFlipState.every(function (x) {
        return x
      })) {
        this.communityCardsFlipComplete = true
      }
    },

    // ---- 工具方法 ----
    isMe(nickname) {
      return this.user && this.user.nickname === nickname
    },

    /**
     * 开局发牌动画：从庄家顺时针下一位起为 0，依次 1、2…（与常见首圈发牌顺序一致，仅用于错开飞入时间）
     */
    holeDealOrderFromDealer(seatIndex) {
      var list = this.players
      if (!list || !list.length) return 0
      var dealerIdx = -1
      for (var i = 0; i < list.length; i++) {
        if (list[i].dealer) {
          dealerIdx = i
          break
        }
      }
      if (dealerIdx < 0) return 0
      var start = (dealerIdx + 1) % list.length
      return (seatIndex - start + list.length) % list.length
    },

    /**
     * 圆桌极角（弧度）：与 getPlayerRoundTableStyle 一致，供座位聊天气泡左右侧向使用。
     */
    getRoundTableSeatTheta(displayIdx, total) {
      if (!total) return 0
      var seated = this.viewerSeatedAtTable
      if (seated) {
        return Math.PI + (2 * Math.PI * displayIdx) / total
      }
      return -Math.PI / 2 + (2 * Math.PI * displayIdx) / total
    },

    /**
     * 座位聊天气泡锚点：桌左半圈从卡片左侧向外伸，右半圈从右侧伸，避免多人时被「上方」邻座挡住；
     * 中间带（含正上、正下）仍用正上方。
     */
    getSeatChatBubbleSide(displayIdx, total) {
      if (!total) return 'top'
      /* 纯观众视角座位角度与入座不同，统一用上方气泡，避免「正上」座位被划到左侧 */
      if (!this.viewerSeatedAtTable) return 'top'
      var theta = this.getRoundTableSeatTheta(displayIdx, total)
      var rx = 46
      var cx = 50
      var x = cx + Math.sin(theta) * rx
      if (x < 38) return 'left'
      if (x > 62) return 'right'
      return 'top'
    },

    /**
     * 圆桌座位：θ=0 为 12 点方向。入座时 displayIdx=0 固定为本人（6 点），全体按座位数整圈均分，
     * 避免旧版「对手只在 θ∈(0,π)」导致 sinθ>0、全部挤在桌面右半圈的问题。
     */
    getPlayerRoundTableStyle(displayIdx, total) {
      if (!total) return {}
      var theta = this.getRoundTableSeatTheta(displayIdx, total)
      var rx = 46
      var ry = 41
      var cx = 50
      var cy = 44
      var x = cx + Math.sin(theta) * rx
      var y = cy - Math.cos(theta) * ry
      /* 摊牌 / 结算等待：上移靠上侧座位，下移靠下侧座位，减轻与中央公共牌重叠 */
      if (this.stage === 'showdown' || this.stage === 'settled') {
        var c = Math.cos(theta)
        if (c > 0.2) {
          y -= 9
        } else if (c > -0.15) {
          y -= 4
        } else if (c < -0.35) {
          y += 10
        }
      }
      /* 顶栏在圆桌之上 + 座位 translate(-50%,-50%) 会让上家牌盒上半截伸进顶栏带；按屏宽加大下移量（勿用顶栏 z-index 盖住座位） */
      if (typeof window !== 'undefined') {
        var w = window.innerWidth
        var cosT = Math.cos(theta)
        if (w <= 600) {
          if (cosT > 0.2) {
            y += 12
          } else if (cosT > 0) {
            y += 6
          }
        } else if (w <= 900) {
          if (cosT > 0.08) {
            y += 6
          }
        }
      }
      return {
        left: x + '%',
        top: y + '%'
      }
    },

    getPlayerBoxStyle(p, i) {
      var s = {
        background: 'var(--dp-player-card-bg)',
        padding: '10px',
        borderRadius: '10px',
        border: '2px solid transparent',
        transition: 'all 0.2s'
      }

      // 离线位：灰显，不参与行动高亮
      if (p.leftThisHand) {
        s.background = 'var(--dp-player-card-offline-bg)'
        s.borderColor = 'var(--dp-player-card-offline-border)'
        s.opacity = '0.85'
        return s
      }

      // 当前行动者高亮边框
      if (this.actIndex === i) {
        s.borderColor = 'var(--dp-player-card-turn-border)'
        s.background = 'var(--dp-player-card-turn-bg)'
      }

      // 自己强调边框
      if (this.isMe(p.nickname)) {
        s.borderColor = 'var(--dp-player-border-me)'
      }

      // 弃牌变灰
      if (p.fold) {
        s.opacity = '0.5'
      }

      // 摊牌选中
      if (this.selectedWinners.includes(p.nickname)) {
        s.borderColor = 'var(--dp-player-card-winner-border)'
        s.borderWidth = '3px'
        s.background = 'var(--dp-player-card-winner-bg)'
        s.opacity = '1'
      } else if (this.stage === 'showdown' && this.isOwner) {
        s.cursor = 'pointer'
        s.borderStyle = 'dashed'
        s.borderColor = 'var(--dp-player-showdown-border)'
      }

      return s
    },

    /**
     * 是否处于「有人要下注」阶段且当前座位有效（与后端 currentActorIndex 对齐）。
     */
    actionCountdownShouldRun() {
      if (!this.playing) return false
      var st = this.stage
      if (st === 'showdown' || st === 'settled') return false
      var i = this.actIndex
      var list = this.players
      if (i < 0 || !list || i >= list.length) return false
      var p = list[i]
      if (!p || p.leftThisHand || p.fold) return false
      return true
    },

    actionCountdownSessionKey() {
      return String(this.playing) + '|' + this.stage + '|' + this.actIndex + '|' + this.currentHandSeed
    },

    /**
     * 任意玩家行动时全场共用同一段 30s 本地倒计时；换行动者/新一手才重置。
     * 轮询拉取同一状态时不会反复把秒数打回 30。
     */
    syncActionCountdown() {
      if (!this.actionCountdownShouldRun()) {
        this.stopCountdown()
        this._actionCountdownKey = null
        return
      }
      var key = this.actionCountdownSessionKey()
      if (this._actionCountdownKey === key) return
      this._actionCountdownKey = key
      this.startCountdown()
    },

    startCountdown() {
      this.stopCountdown()
      this.timeLeft = 30
      var self = this
      this.actionTimer = setInterval(function () {
        if (self.timeLeft > 0) {
          self.timeLeft--
        } else {
          self.stopCountdown()
        }
      }, 1000)
    },

    stopCountdown() {
      if (this.actionTimer) {
        clearInterval(this.actionTimer)
        this.actionTimer = null
      }
    },

    // 结算后准备阶段倒计时（与行动计时风格一致）
    startReadyCountdown() {
      this.stopReadyCountdown()
      this.readyTimeLeft = 30
      this.readyTimer = setInterval(() => {
        if (this.readyTimeLeft > 0) {
          this.readyTimeLeft--
        } else {
          this.stopReadyCountdown()
        }
      }, 1000)
    },

    stopReadyCountdown() {
      if (this.readyTimer) {
        clearInterval(this.readyTimer)
        this.readyTimer = null
      }
    }
  }
}
</script>
