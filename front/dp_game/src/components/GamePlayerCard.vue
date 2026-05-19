<template>
  <div
    class="dp-player-card"
    :class="{
      'player-card--win-streak':
        !heroHandDock && !player.leftThisHand && fieldChipLeader,
      'dp-player-card--compact': compact,
      'dp-player-card--rival-mini': rivalMini,
      'dp-player-card--hand-dock': heroHandDock,
      /* 仅摊牌圈用毛玻璃；结算阶段与节能模式一致，仅用半透明底无 backdrop-filter */
      'dp-player-card--hand-reveal-glass':
        stage === 'showdown' && !player.leftThisHand
    }"
    :style="cardBoxStyle"
    @click="onClick"
  >
    <div
        v-if="seatChatText"
        class="dp-player-card__seat-chat"
        :class="seatChatBubbleClass"
        role="status"
    >
      {{ seatChatText }}
    </div>

    <!-- 他人简化卡片：昵称 + 小鱼干/本轮 + 必要徽标 -->
    <template v-if="rivalMini && player.leftThisHand">
      <div class="dp-player-card__rival-offline">离线</div>
      <div class="dp-player-card__rival-name dp-player-card__rival-name--dim">{{ displayPlayerName }}</div>
    </template>

    <template v-else-if="rivalMini">
      <div class="dp-player-card__rival-head">
        <span class="dp-player-card__rival-name">{{ displayPlayerName }}</span>
        <span
          v-if="!player.fold && actIndex === seatIndex"
          class="dp-player-card__rival-turn-dot"
          title="思考中"
          aria-label="该座位正在行动"
        />
      </div>
      <div
        class="dp-player-card__rival-stats"
        :class="{ 'dp-player-card__rival-stats--fold': player.fold }"
      >
        <span>{{ player.chips }}</span>
        <span class="dp-player-card__rival-stats-sep">·</span>
        <span>{{ catCopy.roundShort }} {{ player.bet }}</span>
      </div>
      <div v-if="player.fold" class="dp-player-card__rival-fold">已盖牌</div>
      <div
        v-if="foldGhostFly"
        ref="foldGhostRow"
        class="dp-player-card__fold-ghost-row dp-player-card__fold-ghost-row--rival"
        aria-hidden="true"
      >
        <div
          v-for="n in ghostHoleLen"
          :key="'fg-r' + n + '-' + handDealKey"
          class="hole-card-fly-wrapper"
          :class="{ 'hole-fold-to-muck': foldMuckFlying }"
          :style="foldGhostWrapperStyle(n - 1)"
          @animationend="onHoleWrapperAnimEnd($event, n - 1)"
        >
          <div class="card-base bg-gray dp-player-card__hole-back-rival">?</div>
        </div>
      </div>
      <div
        v-if="showHoleCardsArea"
        ref="holeCardsRow"
        class="dp-player-card__hole-row dp-player-card__hole-row--rival"
        :aria-label="'手牌'"
      >
        <template v-if="showHoleCardsRevealed">
          <div
            v-for="(c, ci) in player.holeCards"
            :key="'h' + ci + '-' + handDealKey"
            class="hole-card-fly-wrapper"
            :class="{
              'hole-deal-fly-in': holeDealFlyActive(ci),
              'hole-fold-to-muck': foldMuckFlying
            }"
            :style="holeWrapperStyle(ci)"
            @animationend="onHoleWrapperAnimEnd($event, ci)"
          >
            <div
              :class="[getCardClass(c), 'hole-card-flip', { 'hole-card-flip--instant': holeFlipUseInstant }]"
              :style="holeCardInnerStyleRival(ci)"
            >
              {{ getCardDisplay(c) }}
            </div>
          </div>
        </template>
        <template v-else-if="player.holeCards && player.holeCards.length > 0">
          <div
            v-for="n in player.holeCards.length"
            :key="'hb' + (n - 1) + '-' + handDealKey"
            class="hole-card-fly-wrapper"
            :class="{
              'hole-deal-fly-in': holeDealFlyActive(n - 1),
              'hole-fold-to-muck': foldMuckFlying
            }"
            :style="holeWrapperStyle(n - 1)"
            @animationend="onHoleWrapperAnimEnd($event, n - 1)"
          >
            <div
              class="card-base bg-gray dp-player-card__hole-back-rival"
            >?</div>
          </div>
        </template>
      </div>
      <div v-if="showHandRankSectionRivalSeat && rankBlockHasVisiblePart" class="dp-player-card__hand-rank dp-player-card__hand-rank--rival">
        <span
          v-if="displayHandRankName"
          class="dp-player-card__rank-pill"
          :class="showHandRankAsOpen ? 'dp-player-card__rank-pill--open' : 'dp-player-card__rank-pill--showdown'"
        >
          {{ displayHandRankName }}
        </span>
        <div
          v-if="showShowdownLeaderDetail && showHandRankFiveCardRow.length !== 5 && displayHandRankDetail"
          class="dp-player-card__rank-detail"
        >
          {{ displayHandRankDetail }}
        </div>
        <div
          v-if="showHandRankFiveCardRow.length === 5"
          class="best-hand-cards dp-player-card__best-hand dp-player-card__best-hand--rival-leader"
        >
          <div
            v-for="(c, ci) in showHandRankFiveCardRow"
            :key="'sb5r-' + ci + '-' + handDealKey"
            :class="[getCardClass(c), 'best-hand-card', 'best-hand-card-enter', 'dp-player-card__best-card', 'dp-player-card__best-card--rival-leader']"
            :style="bestHandCardEnterStyle(ci)"
          >
            {{ getCardDisplay(c) }}
          </div>
        </div>
      </div>
    </template>

    <template v-else-if="player.leftThisHand">
      <div class="dp-player-card__offline-title">该玩家已离线</div>
      <div class="dp-player-card__offline-hint">座位保留至本局结束，行动顺序不变</div>
    </template>

    <template v-else-if="heroHandDock">
      <div class="dp-player-card__head dp-player-card__head--hand-dock-only">
        <div class="dp-player-card__name">
          {{ displayPlayerName }}
          <span v-if="isMe" class="dp-player-card__name-me">（我）</span>
          <span v-if="player.fold" class="dp-player-card__fold-inline">已盖牌</span>
        </div>
      </div>

      <div
        v-if="showHoleCardsArea"
        ref="holeCardsRow"
        class="dp-player-card__hole-row dp-player-card__hole-row--hand-dock"
        :aria-label="isMe ? '我的手牌' : '手牌'"
      >
        <template v-if="showHoleCardsRevealed">
          <div
            v-for="(c, ci) in player.holeCards"
            :key="'h' + ci + '-' + handDealKey"
            class="hole-card-fly-wrapper"
            :class="{
              'hole-deal-fly-in': holeDealFlyActive(ci),
              'hole-fold-to-muck': foldMuckFlying
            }"
            :style="holeWrapperStyle(ci)"
            @animationend="onHoleWrapperAnimEnd($event, ci)"
          >
            <div
              :class="[getCardClass(c), 'hole-card-flip', { 'hole-card-flip--instant': holeFlipUseInstant }]"
              :style="holeCardInnerStyle(ci)"
            >
              {{ getCardDisplay(c) }}
            </div>
          </div>
        </template>
        <template v-else-if="player.holeCards && player.holeCards.length > 0">
          <div
            v-for="n in player.holeCards.length"
            :key="'hb' + (n - 1) + '-' + handDealKey"
            class="hole-card-fly-wrapper"
            :class="{
              'hole-deal-fly-in': holeDealFlyActive(n - 1),
              'hole-fold-to-muck': foldMuckFlying
            }"
            :style="holeWrapperStyle(n - 1)"
            @animationend="onHoleWrapperAnimEnd($event, n - 1)"
          >
            <div
              class="card-base bg-gray"
              style="width:36px; height:52px; font-size:13px;"
            >?</div>
          </div>
        </template>
      </div>

      <div v-if="showHandRankSection && rankBlockHasVisiblePart" class="dp-player-card__hand-rank">
        <span
          v-if="displayHandRankName"
          class="dp-player-card__rank-pill"
          :class="showHandRankAsOpen ? 'dp-player-card__rank-pill--open' : 'dp-player-card__rank-pill--showdown'"
        >
          {{ displayHandRankName }}
        </span>
        <div
          v-if="showShowdownLeaderDetail && showHandRankFiveCardRow.length !== 5 && displayHandRankDetail"
          class="dp-player-card__rank-detail"
        >
          {{ displayHandRankDetail }}
        </div>
        <div
          v-if="showHandRankFiveCardRow.length === 5"
          class="best-hand-cards dp-player-card__best-hand"
        >
          <div
            v-for="(c, ci) in showHandRankFiveCardRow"
            :key="'best' + ci + '-' + handDealKey"
            :class="[getCardClass(c), 'best-hand-card', 'best-hand-card-enter', 'dp-player-card__best-card']"
            :style="bestHandCardEnterStyle(ci)"
          >
            {{ getCardDisplay(c) }}
          </div>
        </div>
      </div>
    </template>

    <template v-else>
      <div class="dp-player-card__head">
        <div class="dp-player-card__name">
          {{ displayPlayerName }}
          <span v-if="isMe" class="dp-player-card__name-me">（我）</span>
        </div>
        <div
          class="dp-player-card__status"
          :class="{
            'dp-player-card__status--fold': player.fold,
            'dp-player-card__status--turn': !player.fold && actIndex === seatIndex,
            'dp-player-card__status--idle': !player.fold && actIndex !== seatIndex
          }"
        >
          {{ player.fold ? '已盖牌' : (actIndex === seatIndex ? '思考中…' : '进行中') }}
        </div>
      </div>

      <div
        class="dp-player-card__stats-row"
        :class="{ 'dp-player-card__stats-row--has-holes': showHoleCardsArea }"
      >
        <div class="dp-player-card__chips">
          <span class="dp-player-card__mini-label">{{ catCopy.stackShort }}</span>
          <span class="dp-player-card__chips-val">{{ player.chips }}</span>
        </div>
        <div class="dp-player-card__bet" aria-label="本轮已出小鱼干">
          <span class="dp-player-card__mini-label">{{ catCopy.roundShort }}</span>
          <span class="dp-player-card__bet-val">{{ player.bet }}</span>
        </div>
        <div
          v-if="showHoleCardsArea"
          ref="holeCardsRow"
          class="dp-player-card__hole-row"
          :aria-label="isMe ? '我的手牌' : '手牌'"
        >
          <template v-if="showHoleCardsRevealed">
            <div
              v-for="(c, ci) in player.holeCards"
              :key="'h' + ci + '-' + handDealKey"
              class="hole-card-fly-wrapper"
              :class="{
                'hole-deal-fly-in': holeDealFlyActive(ci),
                'hole-fold-to-muck': foldMuckFlying
              }"
              :style="holeWrapperStyle(ci)"
              @animationend="onHoleWrapperAnimEnd($event, ci)"
            >
              <div
                :class="[getCardClass(c), 'hole-card-flip', { 'hole-card-flip--instant': holeFlipUseInstant }]"
                :style="holeCardInnerStyle(ci)"
              >
                {{ getCardDisplay(c) }}
              </div>
            </div>
          </template>
          <template v-else-if="player.holeCards && player.holeCards.length > 0">
            <div
              v-for="n in player.holeCards.length"
              :key="'hb' + (n - 1) + '-' + handDealKey"
              class="hole-card-fly-wrapper"
              :class="{
                'hole-deal-fly-in': holeDealFlyActive(n - 1),
                'hole-fold-to-muck': foldMuckFlying
              }"
              :style="holeWrapperStyle(n - 1)"
              @animationend="onHoleWrapperAnimEnd($event, n - 1)"
            >
              <div
                class="card-base bg-gray"
                style="width:36px; height:52px; font-size:13px;"
              >?</div>
            </div>
          </template>
        </div>
      </div>

      <div v-if="showHandRankSection && rankBlockHasVisiblePart" class="dp-player-card__hand-rank">
        <span
          v-if="displayHandRankName"
          class="dp-player-card__rank-pill"
          :class="showHandRankAsOpen ? 'dp-player-card__rank-pill--open' : 'dp-player-card__rank-pill--showdown'"
        >
          {{ displayHandRankName }}
        </span>
        <div
          v-if="showShowdownLeaderDetail && showHandRankFiveCardRow.length !== 5 && displayHandRankDetail"
          class="dp-player-card__rank-detail"
        >
          {{ displayHandRankDetail }}
        </div>
        <div
          v-if="showHandRankFiveCardRow.length === 5"
          class="best-hand-cards dp-player-card__best-hand"
        >
          <div
            v-for="(c, ci) in showHandRankFiveCardRow"
            :key="'best' + ci + '-' + handDealKey"
            :class="[getCardClass(c), 'best-hand-card', 'best-hand-card-enter', 'dp-player-card__best-card']"
            :style="bestHandCardEnterStyle(ci)"
          >
            {{ getCardDisplay(c) }}
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script>
import { getCardClass, getCardDisplay } from '../utils/dpGameCardVisual'
import { dpDisplayNickname } from '../utils/dpDisplayNickname'
import { DP_DEAL_STAGGER_MS } from '../constants/dpGameDealTiming'
import { getDealerAnchorViewportPoint } from '../utils/dpGameDealerAnchor'
import { CAT_COPY } from '../constants/dpCatThemeCopy'

export default {
  name: 'GamePlayerCard',
  inject: {
    dpGameView: { default: null }
  },
  props: {
    player: { type: Object, required: true },
    seatIndex: { type: Number, required: true },
    boxStyle: { type: Object, required: true },
    actIndex: { type: Number, required: true },
    stage: { type: String, required: true },
    communityCards: { type: Array, required: true },
    communityCardsFlipComplete: { type: Boolean, required: true },
    isOwner: { type: Boolean, required: true },
    ownerRevealAll: { type: Boolean, required: true },
    myNickname: { type: String, default: '' },
    /** 与房间 currentHandSeed 同步，每新一手变化以触发展位发手牌动画 */
    handDealKey: { type: Number, default: 0 },
    /** 设为 false 可恢复旧版「宽大座位」；默认全体紧凑多列 */
    compact: { type: Boolean, default: true },
    /** 圆桌模式下他人座位：极简信息密度 */
    rivalMini: { type: Boolean, default: false },
    /** 开局发牌顺序（发牌位下家为 0），用于多座位错开飞入 */
    holeDealSeatOrder: { type: Number, default: 0 },
    /** 本桌人数：用于「先一圈再一圈」与公共牌同间隔(350ms)错开发牌 */
    holeDealPlayerCount: { type: Number, default: 1 },
    /** 摊牌阶段牌力最高者昵称列表（平局时并列者均展示完整牌型） */
    showdownHandLeaders: { type: Array, default: function () { return [] } },
    /** 该座位玩家最近一条房间聊天（同一人新发会顶掉；由 game.vue 按昵称写入） */
    seatChatText: { type: String, default: '' },
    /** 聊天气泡锚点：top | left | right（圆桌左/右半圈侧向伸出，减轻被邻座遮挡） */
    seatChatSide: {
      type: String,
      default: 'top',
      validator: function (v) {
        return v === 'top' || v === 'left' || v === 'right'
      }
    },
    /**
     * 抽屉/镜像展示用手牌：跳过庄位发牌飞入，避免与桌上已发完的节奏重复播飞入。
     * 可与 dealRevealStaggerSec 配合：仍逐张翻面。
     */
    skipHoleDealAnimation: { type: Boolean, default: false },
    /**
     * 与 skipHoleDealAnimation 同用时：每张间隔秒数再翻开（0 则保持翻面瞬间完成）。
     */
    dealRevealStaggerSec: { type: Number, default: 0 },
    /**
     * 本人内联手牌区：仅昵称 + 底牌 + 牌型；后手/本轮与发牌位/底分档标由外层承担。
     */
    heroHandDock: { type: Boolean, default: false },
    /**
     * 圆桌等外层传入：当前是否为「场上积分最多」之一（并列则多人 true），用于底牌区光效。
     */
    fieldChipLeader: { type: Boolean, default: false }
  },
  data() {
    return {
      catCopy: CAT_COPY,
      holeDealFlyByIndex: {},
      holeDealOriginByIndex: null,
      /** 本手是否走庄位发牌；用于翻牌 delay，避免飞入结束改 style 导致翻牌动画重播 */
      holeDealChainFlip: false,
      /** 紧凑位他人：preflop 背面飞入结束后隐藏底牌行 */
      holeDealIntroDone: false,
      _holeIntroClearTimer: null,
      /** 防止 handDealKey 多次 kick 在同一手内重复触发飞入 */
      holeDealFlightStarted: false,
      /** 盖牌：手牌飞向桌面盖牌区动画 */
      foldMuckFlying: false,
      foldFlyPerCard: [],
      foldMuckAnimComplete: false,
      foldMuckEndsPending: 0,
      _foldMuckFallbackTimer: null,
      /** 他人紧凑位底牌已隐藏时：临时渲染两张背面用于盖牌飞入盖牌区 */
      foldGhostFly: false
    }
  },
  watch: {
    handDealKey: {
      immediate: true,
      handler() {
        this.resetHoleDealFlyState()
        var self = this
        function kick() {
          self.tryStartHoleDealFly()
        }
        this.$nextTick(kick)
        setTimeout(kick, 60)
        setTimeout(kick, 200)
      }
    },
    /**
     * 手牌常比 currentHandSeed 晚一拍下发；仅 handDealKey 不会再次触发 tryStart，导致飞牌从未启动。
     */
    'player.holeCards': {
      deep: true,
      handler() {
        if (this.skipHoleDealAnimation) return
        if (!this.player.holeCards || this.player.holeCards.length === 0) return
        if (this.stage !== 'preflop') return
        if (this.holeDealIntroDone) return
        var self = this
        function kick() {
          self.tryStartHoleDealFly()
        }
        this.$nextTick(kick)
        setTimeout(kick, 60)
        setTimeout(kick, 200)
      }
    },
    stage(val) {
      if (val !== 'preflop') this.holeDealIntroDone = true
    },
    'player.fold': function (now, was) {
      if (now === true && was === false) {
        this.onPlayerFoldEdge()
      }
      if (now === false) {
        this.clearFoldMuckFallbackTimer()
        this.foldMuckAnimComplete = false
        this.foldMuckFlying = false
        this.foldFlyPerCard = []
        this.foldMuckEndsPending = 0
        this.foldGhostFly = false
      }
    }
  },
  beforeDestroy() {
    this.clearHoleIntroTimer()
    this.clearFoldMuckFallbackTimer()
  },
  computed: {
    seatChatBubbleClass() {
      var o = { 'dp-player-card__seat-chat--rival': this.rivalMini }
      if (this.seatChatSide === 'left') {
        o['dp-player-card__seat-chat--out-left'] = true
      } else if (this.seatChatSide === 'right') {
        o['dp-player-card__seat-chat--out-right'] = true
      }
      return o
    },
    cardBoxStyle() {
      var s = Object.assign({}, this.boxStyle)
      if (this.rivalMini) {
        s.padding = '5px 7px'
        s.borderRadius = '10px'
      } else if (this.compact) {
        s.padding = '7px 9px'
        s.borderRadius = '8px'
      }
      /* 摊牌 / 准备下一局：与页面底混色半透明 + 描边，减轻遮挡中央公共牌。
       * 第二色用 var(--dp-game-bg) 而非 transparent：浅色童话主题下「26%+透明」会与底图融成一片，像没渲染座位卡。 */
      if ((this.stage === 'showdown' || this.stage === 'settled') && !this.player.leftThisHand) {
        var base = s.background || 'var(--dp-player-card-bg)'
        var pct = this.rivalMini ? '48%' : '58%'
        s.background = 'color-mix(in srgb, ' + base + ' ' + pct + ', var(--dp-game-bg))'
        s.boxShadow =
          (s.boxShadow ? s.boxShadow + ', ' : '') + 'inset 0 0 0 1px rgba(100, 100, 100, 0.22)'
      }
      return s
    },
    /** 是否渲染底牌行（紧凑他人：preflop 开局暂显背面飞入，结束后隐藏） */
    showHoleCardsArea() {
      if (this.player.leftThisHand) return false
      if (!this.player.holeCards || this.player.holeCards.length === 0) return false
      /* 盖牌飞入盖牌区后隐藏底牌区；本人仍可见；房主开启「看穿底牌」时仍展示 */
      if (
        this.player.fold
        && this.foldMuckAnimComplete
        && !this.isMe
        && !(this.isOwner && this.ownerRevealAll)
      ) {
        return false
      }
      if (!this.compact) return true
      /**
       * 本人内联手牌区：翻牌前仅在手牌飞入阶段展示，结束后收起；翻牌前看牌用「查看手牌」；翻牌后 dock 常驻。
       * 圆桌本人 rival-mini：常态仅信息条不铺底牌；宽屏翻前底部 dock 不挂载，
       * 故仅在 preflop 且 intro 未完成时短暂渲染底牌行以播放庄位飞入动画，intro 结束后收起。
       * 抽屉 skipHoleDealAnimation 会把 introDone 置 true，不得按 intro 隐藏。
       */
      if (this.heroHandDock && this.isMe) {
        if (this.skipHoleDealAnimation) return true
        if (this.stage === 'showdown' || this.stage === 'settled') return true
        if (this.stage === 'preflop') return !this.holeDealIntroDone
        return this.showHoleCardsRevealed
      }
      if (this.rivalMini && this.isMe) {
        return this.stage === 'preflop' && !this.holeDealIntroDone
      }
      if (this.showHoleCardsRevealed) return true
      if (this.stage === 'preflop') return !this.holeDealIntroDone
      return false
    },
    isMe() {
      return !!this.myNickname && this.player.nickname === this.myNickname
    },
    displayPlayerName() {
      return dpDisplayNickname(this.player.nickname)
    },
    /** 是否展示真实手牌（本人 / 房主看穿 / 摊牌后） */
    showHoleCardsRevealed() {
      if (this.player.leftThisHand) return false
      return (
        this.isMe
        || (this.isOwner && this.ownerRevealAll && this.player.holeCards && this.player.holeCards.length > 0)
        || ((this.stage === 'showdown' || this.stage === 'settled') && !this.player.fold)
      )
    },
    /** Flop 后且成牌可读时展示牌型与最大五张 */
    showHandRankSection() {
      if (this.player.leftThisHand) return false
      if (this.communityCards.length < 3) return false
      var boardOk =
        this.communityCardsFlipComplete
        || (this.stage === 'showdown' && this.communityCards.length >= 5)
        || (this.stage === 'settled' && this.communityCards.length >= 3)
      if (!boardOk) return false
      return (
        this.isMe
        || (this.isOwner && this.ownerRevealAll && this.player.holeCards && this.player.holeCards.length > 0)
        || ((this.stage === 'showdown' || this.stage === 'settled') && !this.player.fold)
      )
    },
    /** 圆桌 rival-mini 模板用：本人席只做信息条，不挂牌型/最佳五张（与 dock 分工） */
    showHandRankSectionRivalSeat() {
      if (this.rivalMini && this.isMe) return false
      return this.showHandRankSection
    },
    /** 翻后有成牌服务端字段时才渲染牌型区；翻前本来就不展示该区域（community 不足三张） */
    rankBlockHasVisiblePart() {
      if (!this.showHandRankSection) return false
      if (this.displayHandRankName) return true
      if (this.showHandRankFiveCardRow.length === 5) return true
      return !!(this.showShowdownLeaderDetail && this.displayHandRankDetail)
    },
    /** 牌型标签用「己方可见」配色还是摊牌公开配色 */
    showHandRankAsOpen() {
      return this.isMe
        || (this.isOwner && this.ownerRevealAll && this.player.holeCards && this.player.holeCards.length > 0)
    },
    /** 牌型名称：仅展示服务端在下发快照中填入的 `handRankName`（与 `DpRoomServiceImpl#getAllRooms` 一致），不再在前端推导 */
    displayHandRankName() {
      if (this.player.leftThisHand) return ''
      var n = this.player.handRankName
      return n != null && String(n).trim() !== '' ? String(n).trim() : ''
    },
    /** 成牌说明：仅服务端 `handRankDetail` */
    displayHandRankDetail() {
      if (this.player.leftThisHand) return ''
      var d = this.player.handRankDetail
      return d != null && String(d).trim() !== '' ? String(d).trim() : ''
    },
    /** 摊牌或准备下一局：牌力最高者（含平局并列）展示精确五张（与本人 bestHand 同款） */
    showShowdownLeaderDetail() {
      var leaders = this.showdownHandLeaders
      if (!leaders || !leaders.length) return false
      if (leaders.indexOf(this.player.nickname) === -1) return false
      return (
        (this.stage === 'showdown' || this.stage === 'settled')
        && this.showHandRankSection
      )
    },
    /**
     * 牌型区下方五张：仅用接口里的 `DpPlayer.bestHandCards`（DpUtilHandEvaluator.sortCardsForDisplay），与后端一致；
     * 服务端未下发 5 张时不再在前端拼凑。
     */
    showHandRankFiveCardRow() {
      var fromServer =
        this.player.bestHandCards && this.player.bestHandCards.length === 5
          ? this.player.bestHandCards
          : []
      if (fromServer.length !== 5) return []
      if (this.showShowdownLeaderDetail) return fromServer
      if (!this.rivalMini) return fromServer
      return []
    },
    /** 盖牌幽灵动画：手牌张数（用于 v-for 1..n） */
    ghostHoleLen() {
      var hc = this.player.holeCards
      return hc && hc.length ? hc.length : 0
    },
    /** 抽屉内可逐张翻：有 stagger 时不加 instant，保留 hole-card-flip 动画 */
    holeFlipUseInstant() {
      if (!this.skipHoleDealAnimation) return false
      return !(this.dealRevealStaggerSec > 0)
    }
  },
  methods: {
    getCardClass,
    getCardDisplay,
    prefersReducedMotion() {
      if (this.dpGameView && this.dpGameView.ecoMode) return true
      if (typeof window === 'undefined' || !window.matchMedia) return false
      return window.matchMedia('(prefers-reduced-motion: reduce)').matches
    },
    shouldRunHoleDealFromDealer() {
      if (this.skipHoleDealAnimation) return false
      if (this.player.leftThisHand) return false
      var hc = this.player.holeCards
      if (!hc || hc.length === 0) return false
      if (this.stage === 'showdown' || this.stage === 'settled') return false
      if (this.stage === 'preflop') return true
      if (this.compact && !this.showHoleCardsRevealed) return false
      return true
    },
    clearHoleIntroTimer() {
      if (this._holeIntroClearTimer) {
        clearTimeout(this._holeIntroClearTimer)
        this._holeIntroClearTimer = null
      }
    },
    clearFoldMuckFallbackTimer() {
      if (this._foldMuckFallbackTimer) {
        clearTimeout(this._foldMuckFallbackTimer)
        this._foldMuckFallbackTimer = null
      }
    },
    resetHoleDealFlyState() {
      this.clearHoleIntroTimer()
      if (this.skipHoleDealAnimation) {
        this.holeDealFlyByIndex = {}
        this.holeDealOriginByIndex = null
        this.holeDealChainFlip = false
        this.holeDealIntroDone = true
        this.holeDealFlightStarted = true
        this.foldMuckFlying = false
        this.foldFlyPerCard = []
        this.foldMuckAnimComplete = false
        this.foldMuckEndsPending = 0
        this.foldGhostFly = false
        this.clearFoldMuckFallbackTimer()
        return
      }
      this.holeDealFlyByIndex = {}
      this.holeDealOriginByIndex = null
      this.holeDealChainFlip = false
      this.holeDealIntroDone = false
      this.holeDealFlightStarted = false
      this.foldMuckFlying = false
      this.foldFlyPerCard = []
      this.foldMuckAnimComplete = false
      this.foldMuckEndsPending = 0
      this.foldGhostFly = false
      this.clearFoldMuckFallbackTimer()
      var self = this
      var tailMs = this.computeHoleDealSequenceTailMs()
      this._holeIntroClearTimer = setTimeout(function () {
        self._holeIntroClearTimer = null
        self.finishHoleDealIntroIfNeeded()
      }, tailMs)
    },
    tryStartHoleDealFly() {
      if (this.holeDealIntroDone) return
      if (this.holeDealFlightStarted) return
      if (this.prefersReducedMotion()) {
        this.finishHoleDealIntroIfNeeded()
        return
      }
      if (!this.shouldRunHoleDealFromDealer()) return
      if (Object.keys(this.holeDealFlyByIndex).length > 0) return
      var self = this
      this.$nextTick(function () {
        self.startHoleDealFly()
      })
    },
    finishHoleDealIntroIfNeeded() {
      /* 翻牌前尚无 holeCards 时不得结束 intro，否则父级会收起 6 点座位，飞牌永远不播 */
      if (this.stage === 'preflop' && this.compact) {
        var hc0 = this.player.holeCards
        if (!hc0 || hc0.length === 0) return
      }
      this.clearHoleIntroTimer()
      if (this.stage !== 'preflop' || !this.compact) return
      var before = this.holeDealIntroDone
      if (!this.showHoleCardsRevealed) {
        this.holeDealIntroDone = true
      } else if (this.isMe && (this.heroHandDock || this.rivalMini)) {
        this.holeDealIntroDone = true
      }
      if (!before && this.holeDealIntroDone && this.isMe && (this.heroHandDock || this.rivalMini)) {
        this.$emit('hole-deal-intro-complete')
      }
    },
    /** 最后一手牌飞入起点时间 + 飞入/翻面余量（与人数、张数相关） */
    computeHoleDealSequenceTailMs() {
      var stagger = DP_DEAL_STAGGER_MS
      var pc = Math.max(1, this.holeDealPlayerCount || 1)
      var nh = this.player.holeCards ? this.player.holeCards.length : 0
      if (nh <= 0) return 5000
      var lastStart = (nh - 1) * pc * stagger + (pc - 1) * stagger
      return lastStart + 2200
    },
    holeDealDelayMsForCard(cardIdx) {
      var stagger = DP_DEAL_STAGGER_MS
      var pc = Math.max(1, this.holeDealPlayerCount || 1)
      var seat = this.holeDealSeatOrder || 0
      return cardIdx * pc * stagger + seat * stagger
    },
    startHoleDealFly() {
      var self = this
      this.$nextTick(function () {
        var row = self.$refs.holeCardsRow
        var n = self.player.holeCards ? self.player.holeCards.length : 0
        if (!row || n === 0) return
        self.holeDealFlightStarted = true
        self.clearHoleIntroTimer()
        var tailMs = self.computeHoleDealSequenceTailMs()
        self._holeIntroClearTimer = setTimeout(function () {
          self._holeIntroClearTimer = null
          self.finishHoleDealIntroIfNeeded()
        }, tailMs)
        self.holeDealChainFlip = true
        self.holeDealOriginByIndex = self.computeHoleOriginsFromDealer(row, n)
        var next = {}
        for (var i = 0; i < n; i++) {
          next[i] = { delay: self.holeDealDelayMsForCard(i) }
        }
        self.holeDealFlyByIndex = next
      })
    },
    computeHoleOriginsFromDealer(row, n) {
      if (typeof document === 'undefined') return null
      if (!row) return null
      var anchor = getDealerAnchorViewportPoint()
      if (!anchor) return null
      var dcx = anchor.x
      var dcy = anchor.y
      var wrappers = row.querySelectorAll('.hole-card-fly-wrapper')
      var map = {}
      for (var i = 0; i < n && i < wrappers.length; i++) {
        var el = wrappers[i]
        var r = el.getBoundingClientRect()
        map[i] = {
          x: dcx - (r.left + r.width / 2),
          y: dcy - (r.top + r.height / 2)
        }
      }
      return Object.keys(map).length ? map : null
    },
    holeDealFlyActive(ci) {
      return !!this.holeDealFlyByIndex[ci]
    },
    holeDealFlyStyle(ci) {
      var m = this.holeDealFlyByIndex[ci]
      if (!m) return {}
      var origin = this.holeDealOriginByIndex && this.holeDealOriginByIndex[ci]
      var rotDeg = ci === 0 ? -5 : 5
      var style = {
        animationDelay: (m.delay || 0) + 'ms',
        zIndex: 2 + ci,
        '--hole-from-rot': rotDeg + 'deg'
      }
      if (origin) {
        style['--hole-from-x'] = origin.x + 'px'
        style['--hole-from-y'] = origin.y + 'px'
      } else {
        style['--hole-from-x'] = '0px'
        style['--hole-from-y'] = '64px'
      }
      return style
    },
    holeWrapperStyle(ci) {
      var deal = this.holeDealFlyStyle(ci)
      var fold = this.foldMuckFlyStyle(ci)
      if (!fold || Object.keys(fold).length === 0) return deal
      return Object.assign({}, deal, fold)
    },
    foldMuckFlyStyle(ci) {
      if (!this.foldMuckFlying || !this.foldFlyPerCard || !this.foldFlyPerCard[ci]) return {}
      return this.foldFlyPerCard[ci]
    },
    foldGhostWrapperStyle(ci) {
      return this.foldMuckFlyStyle(ci)
    },
    /** 摊牌/结算同时翻开；仅首圈庄位发牌时沿用座位 stagger */
    holeFlipDelaySec(ci) {
      if (this.skipHoleDealAnimation) {
        if (this.dealRevealStaggerSec > 0) {
          return (this.dealRevealStaggerSec * ci) + 's'
        }
        return '0s'
      }
      if (this.stage === 'showdown' || this.stage === 'settled') return '0s'
      if (this.holeDealChainFlip) {
        return (this.holeDealDelayMsForCard(ci) / 1000 + 0.42) + 's'
      }
      return (ci * 0.08) + 's'
    },
    /** 飞入后再翻开，与庄位发牌节奏衔接 */
    holeCardInnerStyle(ci) {
      var base = {
        width: '36px',
        height: '52px',
        fontSize: '13px'
      }
      base.animationDelay = this.holeFlipDelaySec(ci)
      return base
    },
    holeCardInnerStyleRival(ci) {
      var base = {
        width: '28px',
        height: '40px',
        fontSize: '11px'
      }
      base.animationDelay = this.holeFlipDelaySec(ci)
      return base
    },
    bestHandCardEnterStyle(ci) {
      if (this.stage === 'showdown' || this.stage === 'settled') {
        return { animationDelay: '0s' }
      }
      return { animationDelay: (ci * 0.07) + 's' }
    },
    onPlayerFoldEdge() {
      var self = this
      this.clearFoldMuckFallbackTimer()
      if (this.prefersReducedMotion()) {
        this.foldMuckAnimComplete = true
        return
      }
      var nh = this.player.holeCards ? this.player.holeCards.length : 0
      if (nh <= 0) {
        this.foldMuckAnimComplete = true
        return
      }
      this.$nextTick(function () {
        var muck = typeof document !== 'undefined'
          ? document.querySelector('[data-dp-muck-anchor="true"]')
          : null
        if (!muck) {
          self.foldMuckAnimComplete = true
          return
        }
        var row = self.$refs.holeCardsRow
        var wrappers = row ? row.querySelectorAll('.hole-card-fly-wrapper') : []
        var useGhost = self.rivalMini && wrappers.length === 0
        if (useGhost) {
          self.foldGhostFly = true
          self.$nextTick(function () {
            self.startFoldMuckFromRow(self.$refs.foldGhostRow, muck)
          })
          return
        }
        if (!wrappers.length) {
          self.foldMuckAnimComplete = true
          return
        }
        self.startFoldMuckFromRow(row, muck)
      })
    },
    startFoldMuckFromRow(row, muck) {
      var self = this
      if (!muck && typeof document !== 'undefined') {
        muck = document.querySelector('[data-dp-muck-anchor="true"]')
      }
      if (!row || !muck) {
        this.foldGhostFly = false
        this.foldMuckAnimComplete = true
        return
      }
      var wrappers = row.querySelectorAll('.hole-card-fly-wrapper')
      if (!wrappers.length) {
        this.foldGhostFly = false
        this.foldMuckAnimComplete = true
        return
      }
      var mr = muck.getBoundingClientRect()
      var mtx = mr.left + mr.width / 2
      var mty = mr.top + mr.height / 2
      var arr = []
      for (var i = 0; i < wrappers.length; i++) {
        var r = wrappers[i].getBoundingClientRect()
        var cx = r.left + r.width / 2
        var cy = r.top + r.height / 2
        arr.push({
          '--fold-dx': (mtx - cx) + 'px',
          '--fold-dy': (mty - cy) + 'px',
          animationDelay: (i * 50) + 'ms',
          zIndex: 14 + i
        })
      }
      this.foldFlyPerCard = arr
      this.foldMuckEndsPending = arr.length
      this.foldMuckFlying = true
      this._foldMuckFallbackTimer = setTimeout(function () {
        self._foldMuckFallbackTimer = null
        if (!self.foldMuckFlying) return
        self.foldMuckFlying = false
        self.foldMuckAnimComplete = true
        self.foldFlyPerCard = []
        self.foldMuckEndsPending = 0
        self.foldGhostFly = false
      }, 900)
    },
    onHoleWrapperAnimEnd(ev, ci) {
      this.onHoleDealFlyEnd(ev, ci)
      this.onFoldMuckFlyEnd(ev)
    },
    onHoleDealFlyEnd(ev, ci) {
      if (!ev) return
      var name = ev.animationName || ''
      if (name.indexOf('hole-deal-fly') === -1) return
      if (this.holeDealFlyByIndex[ci]) {
        this.$delete(this.holeDealFlyByIndex, ci)
      }
      if (Object.keys(this.holeDealFlyByIndex).length === 0) {
        this.holeDealOriginByIndex = null
        this.holeDealChainFlip = false
        this.finishHoleDealIntroIfNeeded()
      }
    },
    onFoldMuckFlyEnd(ev) {
      if (!this.foldMuckFlying || !ev) return
      if (ev.target !== ev.currentTarget) return
      var name = ev.animationName || ''
      if (name.indexOf('hole-fold-fly-muck') === -1) return
      this.foldMuckEndsPending -= 1
      if (this.foldMuckEndsPending <= 0) {
        this.clearFoldMuckFallbackTimer()
        this.foldMuckFlying = false
        this.foldMuckAnimComplete = true
        this.foldFlyPerCard = []
        this.foldMuckEndsPending = 0
        this.foldGhostFly = false
      }
    },
    onClick() {
      if (!this.player.leftThisHand) {
        this.$emit('card-click', {
          nickname: this.player.nickname,
          userId: this.player.userId
        })
      }
    }
  }
}
</script>

<style src="../styles/dp-poker-cards.css"></style>
