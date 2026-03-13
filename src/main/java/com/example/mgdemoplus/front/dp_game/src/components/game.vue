<template>
  <div class="game"
       style="padding:10px; max-width:800px; margin:0 auto; font-family:sans-serif; background:#f0f2f5; min-height:100vh;">

    <!-- 顶部信息栏 -->
    <div
        style="background:#fff; padding:15px; border-radius:10px; display:flex; justify-content:space-between; align-items:center; box-shadow:0 2px 5px rgba(0,0,0,0.1); margin-bottom:15px;">
      <div>
        <div style="font-size:18px; font-weight:bold;">
          房间: {{ roomId }} | 阶段: <span style="color:#1890ff;">{{ stageCN }}</span>
        </div>
        <div style="font-size:14px; color:#666;">
          底池: <span style="color:#f5222d; font-weight:bold;">{{ pot }}</span>
          | 当前跟注额: <span style="font-weight:bold;">{{ currentBetToCall }}</span>
        </div>
      </div>
      <div style="display:flex; gap:8px;">
        <button @click="showHandRankModal = true"
                style="background:#1890ff; color:#fff; border:none; padding:8px 15px; border-radius:5px; cursor:pointer; font-size:13px;">
          牌型说明
        </button>
        <button
            v-if="spectators && spectators.length > 0"
            @click="showSpectatorModal = true"
            style="background:#13c2c2; color:#fff; border:none; padding:8px 10px; border-radius:5px; cursor:pointer; font-size:12px;">
          观众席（{{ spectators.length }}）
        </button>
        <button @click="exitGame"
                style="background:#ff4d4f; color:#fff; border:none; padding:8px 15px; border-radius:5px; cursor:pointer;">
          退出对局
        </button>
      </div>
    </div>

    <!-- 牌型说明弹窗 -->
    <div v-if="showHandRankModal" class="hand-rank-modal-mask" @click="showHandRankModal = false">
      <div class="hand-rank-modal" @click.stop>
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:15px;">
          <span style="font-size:18px; font-weight:bold;">牌型大小参考（从大到小）</span>
          <button @click="showHandRankModal = false"
                  style="background:#d9d9d9; border:none; width:28px; height:28px; border-radius:4px; cursor:pointer; font-size:16px; line-height:1;">×</button>
        </div>
        <div class="hand-rank-list">
          <div v-for="(item, idx) in handRankReference" :key="idx" class="hand-rank-item">
            <span class="hand-rank-num">{{ idx + 1 }}</span>
            <span class="hand-rank-name">{{ item.name }}</span>
            <div class="hand-rank-cards">
              <div v-for="c in item.cards" :key="c" :class="getCardClass(c)" class="hand-rank-card">
                {{ getCardDisplay(c) }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 观众席弹窗 -->
    <div v-if="showSpectatorModal" class="hand-rank-modal-mask" @click="showSpectatorModal = false">
      <div class="hand-rank-modal" @click.stop>
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:15px;">
          <span style="font-size:18px; font-weight:bold;">观众席名单</span>
          <button @click="showSpectatorModal = false"
                  style="background:#d9d9d9; border:none; width:28px; height:28px; border-radius:4px; cursor:pointer; font-size:16px; line-height:1;">×</button>
        </div>
        <div v-if="!spectators || spectators.length === 0" style="font-size:13px; color:#999;">
          当前没有观众。
        </div>
        <ul v-else style="list-style:none; padding:0; margin:0;">
          <li v-for="name in spectators" :key="name"
              style="padding:6px 0; border-bottom:1px solid #f0f0f0; font-size:14px; color:#333;">
            {{ name }}
          </li>
        </ul>
      </div>
    </div>

    <!-- ========== 游戏进行中 ========== -->

    <!-- 观战提示 + 下一局加入按钮：观众 或 本手已退出的僵尸位 都显示（退了又进来的人也能立刻准备下一局） -->
    <div v-if="showSpectatorPrepareBlock"
         style="background:#fff; padding:10px 15px; border-radius:8px; margin-bottom:15px; text-align:center; font-size:13px;">
      <div style="margin-bottom:8px;">
        你当前正在<span style="color:#1890ff;">旁观本局</span>，不会参与下注和结算。
      </div>
      <button
          @click="readyNextHand"
          :disabled="nextHandReady"
          style="padding:6px 14px; border:none; border-radius:4px; cursor:pointer; background:#52c41a; color:#fff; font-size:13px;"
      >
        {{ nextHandReady ? '已报名下一局，等待房主重新发牌' : '准备在下一局加入对局' }}
      </button>
    </div>

    <!-- 公共牌（带翻转动画） -->
    <div style="display:flex; gap:8px; justify-content:center; margin:20px 0;">
      <div v-for="(c, cIdx) in communityCards" :key="c + cIdx" class="card-flip-wrapper">
        <div class="card-flip-inner" :class="{ flipped: communityCardsFlipState[cIdx] }">
          <div class="card-face card-back card-base bg-gray">?</div>
          <div class="card-face card-front" :class="getCardClass(c)">
            {{ getCardDisplay(c) }}
          </div>
        </div>
      </div>
      <div v-for="i in (5 - communityCards.length)" :key="'e' + i" class="card-base bg-gray">?</div>
    </div>

    <!-- 玩家列表：离线位只保留座位与 D/SB/BB 顺序，仅显示「该玩家已离线」 -->
    <div style="display:grid; grid-template-columns:1fr 1fr; gap:15px;">
      <div
          v-for="(p, i) in players"
          :key="(p.leftThisHand ? 'offline-' + i : p.nickname)"
          :style="getPlayerBoxStyle(p, i)"
          @click="!p.leftThisHand && onPlayerCardClick(p.nickname)"
      >
        <!-- 标记（庄家/盲注顺序保留，便于看行动顺序） -->
        <div style="display:flex; gap:5px; margin-bottom:5px;">
            <span v-if="p.dealer"
                  style="background:#faad14; color:#fff; padding:1px 5px; border-radius:3px; font-size:12px;">D</span>
          <span v-if="p.blind === 1"
                style="background:#722ed1; color:#fff; padding:1px 5px; border-radius:3px; font-size:12px;">SB</span>
          <span v-if="p.blind === 2"
                style="background:#52c41a; color:#fff; padding:1px 5px; border-radius:3px; font-size:12px;">BB</span>
        </div>

        <!-- 离线位：不显示任何个人信息，仅显示「该玩家已离线」 -->
        <template v-if="p.leftThisHand">
          <div style="font-weight:bold; color:#8c8c8c; font-size:14px;">
            该玩家已离线
          </div>
          <div style="margin-top:8px; font-size:12px; color:#bfbfbf;">
            座位保留至本局结束，行动顺序不变
          </div>
        </template>

        <!-- 正常玩家：名字、筹码、手牌、状态 -->
        <template v-else>
          <!-- 名字 -->
          <div style="font-weight:bold;">
            {{ p.nickname }}
            <span v-if="isMe(p.nickname)" style="color:#1890ff;">(我)</span>
          </div>
          <!-- 筹码 -->
          <div style="margin-top: 8px; display: flex; flex-direction: column; gap: 4px;">
            <div
                style="font-size: 13px; color: #555; display: flex; align-items: center; justify-content: center; background: #f8f9fa; border-radius: 4px; padding: 2px 0;">
              <span style="color: #8c8c8c; margin-right: 4px;">剩余积分:</span>
              <span style="font-weight: 800; font-family: monospace; color: #2f3542;">{{ p.chips }}</span>
            </div>

            <div
                style="background: #fff2f0; border: 1px solid #ffccc7; border-radius: 6px; padding: 4px 0; text-align: center;">
              <div
                  style="font-size: 11px; color: #ff4d4f; text-transform: uppercase; font-weight: bold; letter-spacing: 0.5px;">
                本轮积分
              </div>
              <div style="font-size: 16px; color: #cf1322; font-weight: 900; font-family: 'Arial Black', sans-serif;">
                {{ p.bet }}
              </div>
            </div>
          </div>
          <!-- 手牌：自己始终能看；摊牌/结算等待阶段只有未弃牌的人亮牌，弃牌的人依然盖牌 -->
          <div style="display:flex; gap:5px; margin:8px 0; justify-content:center;">
            <template v-if="isMe(p.nickname) || ((stage === 'showdown' || stage === 'settled') && !p.fold)">
              <div
                  v-for="(c, ci) in p.holeCards"
                  :key="'h' + ci"
                  :class="[getCardClass(c), 'hole-card-flip']"
                  style="width:36px; height:52px; font-size:13px;">
                {{ getCardDisplay(c) }}
              </div>
            </template>
            <template v-else-if="p.holeCards && p.holeCards.length > 0">
              <div v-for="ci in p.holeCards.length" :key="'hb' + ci" class="card-base bg-gray"
                   style="width:36px; height:52px; font-size:13px;">?
              </div>
            </template>
          </div>

          <!-- 牌型显示 -->
          <div v-if="communityCards.length >= 3 && communityCardsFlipComplete && (isMe(p.nickname) || ((stage === 'showdown' || stage === 'settled') && !p.fold))"
               style="margin-top:4px; text-align:center;">
            <template v-if="isMe(p.nickname)">
                <span
                    style="background:#e6f7ff; color:#1890ff; padding:3px 10px; border-radius:4px; font-weight:bold; font-size:12px; display:inline-block;">
                  {{ getHandRank(p.holeCards, communityCards) }}
                </span>
            </template>
            <template v-else-if="(stage === 'showdown' || stage === 'settled') && !p.fold">
                <span
                    style="background:#f6ffed; color:#52c41a; padding:3px 10px; border-radius:4px; font-weight:bold; font-size:12px; display:inline-block;">
                  {{ getHandRank(p.holeCards, communityCards) }}
                </span>
            </template>
          </div>

          <!-- 状态 -->
          <div style="font-weight:bold; font-size:12px; margin-top:4px;"
               :style="{ color: p.fold ? '#ff4d4f' : (actIndex === i ? '#faad14' : '#52c41a') }">
            {{ p.fold ? '已弃牌' : (actIndex === i ? '思考中...' : '进行中') }}
          </div>
        </template>
      </div>
    </div>

    <!-- 结算后准备 & 补码区域 -->
    <div v-if="inSettledStage"
         style="margin-top:15px; background:#fff; padding:12px; border-radius:8px; box-shadow:0 1px 4px rgba(0,0,0,0.06);">
      <div style="font-size:14px; font-weight:bold; text-align:center; margin-bottom:8px; color:#333;">
        本局已结算，请准备下一局（约30秒后未准备的玩家将被移到观众席）
      </div>
      <div style="display:flex; justify-content:center; align-items:center; gap:10px; margin-bottom:8px;">
        <div
            style="display:flex; align-items:center; justify-content:center;
                   width:32px; height:32px;
                   background:#ffffff;
                   border:2px solid #000000;
                   border-radius:50%;
                   flex-shrink:0; box-sizing:border-box;">
          <span
              style="color:#ff4d4f; font-size:14px; font-weight:900; font-family:'Arial Black', sans-serif; line-height:1;">
            {{ readyTimeLeft }}
          </span>
        </div>
        <span style="font-size:12px; color:#999;">准备倒计时</span>
      </div>
      <div style="text-align:center; font-size:13px; color:#666; margin-bottom:8px;">
        当前积分：<span style="font-weight:bold; color:#1890ff;">{{ myChips }}</span>
      </div>
      <div style="display:flex; justify-content:center; gap:10px; flex-wrap:wrap;">
        <button
            @click="toggleReady"
            :disabled="myChips < 10"
            style="padding:8px 16px; border:none; border-radius:5px; cursor:pointer; font-weight:bold;
                   background: #52c41a; color:#fff;">
          {{ myReady ? '取消准备' : (myChips >= 10 ? '准备下一局' : '积分不足大盲(10)，无法准备') }}
        </button>
        <button
            v-if="myChips < 10"
            @click="rebuy"
            style="padding:8px 16px; border:none; border-radius:5px; cursor:pointer; font-weight:bold;
                   background:#fa8c16; color:#fff;">
          补码到初始积分
        </button>
      </div>
    </div>

    <!-- ===== 我的行动区 ===== -->
    <div v-if="isMyTurn"
         style="margin-top:20px; background:#fff; padding:15px; border-radius:10px; box-shadow:0 -2px 10px rgba(0,0,0,0.05);">
      <div style="text-align:center; color:#faad14; font-weight:bold; margin-bottom:10px;">
        轮到你行动了（30秒超时自动弃牌）
      </div>
      <div style="font-size:13px; color:#666; text-align:center; margin-bottom:10px;">
        当前跟注额: {{ currentBetToCall }} | 你已下注: {{ myBet }} | 还需跟: {{ callAmount }}
      </div>

      <div style="display:flex; gap:10px; flex-wrap:wrap; justify-content:center; align-items:center;">

        <div v-if="players[actIndex]?.nickname === user.nickname"
             style="display: flex; align-items: center; justify-content: center;
                width: 40px; height: 40px;
                background: #ffffff;
                border: 2px solid #000000;
                border-radius: 50%;
                flex-shrink: 0;
                box-sizing: border-box;">
      <span
          style="color: #ff4d4f; font-size: 18px; font-weight: 900; font-family: 'Arial Black', sans-serif; line-height: 1;">
        {{ timeLeft }}
      </span>
        </div>

        <button @click="doCall"
                style="height: 40px; padding: 0 18px; background: #1890ff; color: #fff; border: none; border-radius: 5px; cursor: pointer; font-weight: bold; display: flex; align-items: center;">
          {{ callAmount > 0 ? '跟注 ' + callAmount : '过牌' }}
        </button>

        <div style="display:flex; align-items:center; gap:5px; height: 40px;">
          <button @click="raiseAmount += 5"
                  style="height: 32px; width: 32px; padding: 0; background: #fff; border: 1px solid #f57f17; color: #f57f17; border-radius: 4px; cursor: pointer; font-weight: bold;">
            +5
          </button>
          <button @click="raiseAmount += 10"
                  style="height: 32px; width: 32px; padding: 0; background: #fff; border: 1px solid #f57f17; color: #f57f17; border-radius: 4px; cursor: pointer; font-weight: bold;">
            +10
          </button>
          <input type="number" v-model.number="raiseAmount" :min="minRaise" :max="myChips"
                 style="width: 60px; height: 32px; padding: 0; border: 1px solid #d9d9d9; border-radius: 4px; text-align: center;"/>
          <button @click="raiseAmount -= 10"
                  style="height: 32px; width: 32px; padding: 0; background: #fff; border: 1px solid #f57f17; color: #f57f17; border-radius: 4px; cursor: pointer; font-weight: bold;">
            -10
          </button>
          <button @click="raiseAmount -= 5"
                  style="height: 32px; width: 32px; padding: 0; background: #fff; border: 1px solid #f57f17; color: #f57f17; border-radius: 4px; cursor: pointer; font-weight: bold;">
            -5
          </button>
        </div>

        <button @click="doRaise" :disabled="raiseAmount < minRaise"
                style="height: 40px; padding: 0 14px; background: #f57f17; color: #fff; border: none; border-radius: 5px; cursor: pointer; font-weight: bold;">
          加注
        </button>

        <button @click="doAllIn"
                style="height: 40px; padding: 0 14px; background: #c62828; color: #fff; border: none; border-radius: 5px; cursor: pointer; font-weight: bold;">
          All-In ({{ myChips }})
        </button>

        <button @click="doFold"
                style="height: 40px; padding: 0 18px; background: #ff4d4f; color: #fff; border: none; border-radius: 5px; cursor: pointer; font-weight: bold;">
          弃牌
        </button>
      </div>
    </div>

    <!-- ===== 房主控制区 ===== -->
    <div v-if="isOwner" style="margin-top:20px; background:#fff; padding:15px; border-radius:10px;">
      <div style="font-size:14px; font-weight:bold; color:#333; margin-bottom:10px; text-align:center;">房主操作</div>

      <div style="display:flex; justify-content:center; gap:10px; margin-bottom:10px; flex-wrap:wrap;">
        <button
            @click="transferOwnerMode = !transferOwnerMode"
            style="padding:6px 12px; border:none; border-radius:5px; cursor:pointer; font-size:12px; font-weight:bold;
                   background: #722ed1; color:#fff;">
          {{ transferOwnerMode ? '取消移交房主' : '移交房主（点上方玩家卡片）' }}
        </button>
      </div>

      <!-- showdown 结算：按池分配 -->
      <div v-if="stage === 'showdown'" style="text-align:center; margin-bottom:10px;">
        <div style="color:#f5222d; font-weight:bold; margin-bottom:8px;">
          摊牌阶段 - 请为每个池选择赢家
        </div>

        <!-- 有边池数据时：按池分别选赢家 -->
        <template v-if="pots.length > 0">
          <div v-for="(potItem, pi) in pots" :key="'pot' + pi"
               style="background:#fafafa; border:1px solid #e8e8e8; border-radius:8px; padding:10px; margin-bottom:10px; text-align:left;">
            <div style="font-weight:bold; margin-bottom:6px; color:#333;">
              {{ pi === 0 ? '主池' : '边池 ' + pi }} - 金额: <span style="color:#f5222d;">{{ potItem.amount }}</span>
            </div>
            <div style="font-size:12px; color:#999; margin-bottom:6px;">
              有资格的玩家: {{ potItem.eligiblePlayers.join(', ') }}
            </div>
            <div style="display:flex; flex-wrap:wrap; gap:6px;">
              <button
                  v-for="name in potItem.eligiblePlayers"
                  :key="'pw' + pi + name"
                  @click="togglePotWinner(pi, name)"
                  :style="{
                    padding: '5px 12px',
                    borderRadius: '4px',
                    cursor: 'pointer',
                    border: isPotWinner(pi, name) ? '2px solid #52c41a' : '1px solid #d9d9d9',
                    background: isPotWinner(pi, name) ? '#f6ffed' : '#fff',
                    color: isPotWinner(pi, name) ? '#52c41a' : '#333',
                    fontWeight: isPotWinner(pi, name) ? 'bold' : 'normal'
                  }"
              >
                {{ name }} {{ isPotWinner(pi, name) ? '(已选)' : '' }}
              </button>
            </div>
          </div>
          <button
              @click="confirmPotJudge"
              :disabled="!allPotsHaveWinners"
              style="width:100%; padding:12px; background:#52c41a; color:#fff; border:none; border-radius:5px; cursor:pointer; font-weight:bold;"
              :style="{ opacity: allPotsHaveWinners ? 1 : 0.4 }"
          >
            确认按池结算
          </button>
        </template>

        <!-- 没有边池数据（后端未更新）：退回旧的简单模式 -->
        <template v-else>
          <div style="font-size:13px; color:#666; margin-bottom:8px;">
            点击上方玩家卡片选择赢家（可多选平分）
          </div>
          <div v-if="selectedWinners.length > 0" style="font-size:13px; color:#333; margin-bottom:8px;">
            已选: {{ selectedWinners.join(', ') }}
          </div>
          <button
              @click="confirmJudgeWin"
              :disabled="selectedWinners.length === 0"
              style="width:100%; padding:12px; background:#52c41a; color:#fff; border:none; border-radius:5px; cursor:pointer; font-weight:bold;"
              :style="{ opacity: selectedWinners.length === 0 ? 0.4 : 1 }"
          >
            确认结算（底池 {{ pot }} 分给 {{ selectedWinners.length }} 人）
          </button>
        </template>
      </div>

      <div class="actions" style="margin-top: 20px; text-align: center; font-size:12px; color:#999;">
        摊牌后系统会自动结算并在准备阶段结束后开启下一局，无需手动点击“重新发牌”
      </div>
    </div>


  </div>
</template>

<script>
export default {
  data() {
    return {
      roomId: '',
      user: null,

      // 房间数据（对应后端 DpRoom 字段）
      owner: '',
      players: [],
      playing: false,
      stage: 'preflop',
      communityCards: [],
      pot: 0,
      pots: [],             // 主池+边池列表 [{amount, eligiblePlayers}]
      currentBetToCall: 0,
      actIndex: -1,
      // 观众席名单（由后端 DpRoom.spectators 提供）
      spectators: [],

      // UI
      raiseAmount: 0,
      selectedWinners: [],   // 旧的简单模式备用
      potWinners: {},        // 按池选赢家 { 0: ['Alice'], 1: ['Bob','Charlie'] }
      nextHandReady: false,  // 是否已报名下一局加入
      transferOwnerMode: false, // 是否处于选择新房主模式
      loading: false,

      // 公共牌翻转动画：每个下标 true=已翻开，false=未翻开
      communityCardsFlipState: [],
      // 公共牌全部翻完后再显示牌型，增强沉浸感
      communityCardsFlipComplete: false,
      communityCardsFlipCompleteTimer: null,

      // 定时器
      pollTimer: null,
      heartbeatTimer: null,
      //游戏计时器
      actionTimer: null,
      timeLeft: 30,
      // 结算后准备阶段倒计时
      readyTimer: null,
      readyTimeLeft: 30,

      // 牌型说明弹窗
      showHandRankModal: false,
      showSpectatorModal: false,
      handRankReference: [
        { name: '皇家同花顺', cards: ['hearts_A', 'hearts_K', 'hearts_Q', 'hearts_J', 'hearts_10'] },
        { name: '同花顺', cards: ['hearts_9', 'hearts_8', 'hearts_7', 'hearts_6', 'hearts_5'] },
        { name: '四条', cards: ['hearts_A', 'spades_A', 'diamonds_A', 'clubs_A', 'hearts_2'] },
        { name: '葫芦', cards: ['hearts_A', 'spades_A', 'diamonds_A', 'hearts_K', 'spades_K'] },
        { name: '同花', cards: ['hearts_A', 'hearts_J', 'hearts_9', 'hearts_6', 'hearts_2'] },
        { name: '顺子', cards: ['hearts_10', 'spades_9', 'diamonds_8', 'clubs_7', 'hearts_6'] },
        { name: '三条', cards: ['hearts_A', 'spades_A', 'diamonds_A', 'hearts_K', 'clubs_2'] },
        { name: '两对', cards: ['hearts_A', 'spades_A', 'hearts_K', 'diamonds_K', 'clubs_2'] },
        { name: '一对', cards: ['hearts_A', 'spades_A', 'hearts_K', 'diamonds_Q', 'clubs_2'] },
        { name: '高牌', cards: ['hearts_A', 'spades_K', 'diamonds_Q', 'clubs_J', 'hearts_9'] }
      ]
    }
  },

  computed: {
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
    minRaise() {
      return this.callAmount + 10
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
    }
  },

  watch: {
    isMyTurn: function (v) {
      if (v) this.raiseAmount = this.minRaise
    },
// 监听当前行动者的索引变化
    actIndex(newVal) {
      // 获取当前轮到的那个人
      const currentPlayer = this.players[newVal];

      // 如果这个人存在，且名字是我自己（守卫 user 未加载）
      if (this.user && currentPlayer && currentPlayer.nickname === this.user.nickname) {
        this.startCountdown();
      } else {
        this.stopCountdown();
      }
    },
    // 监听阶段变化，用于控制结算后准备阶段的倒计时
    stage(newVal) {
      if (newVal === 'settled') {
        this.startReadyCountdown()
      } else {
        this.stopReadyCountdown()
      }
    }
  },

  created() {
    this.roomId = this.$route.params.roomId

    var raw = localStorage.getItem('userInfo')
    if (!raw) {
      alert('登录信息丢失，请重新登录')
      this.$router.push('/login')
      return
    }
    this.user = JSON.parse(raw)

    // 立即加载一次
    this.loadGame()

    // 1秒轮询游戏状态
    this.pollTimer = setInterval(function () {
      if (!this.loading) this.loadGame()
    }.bind(this), 1000)

    // 5秒独立心跳（和 loadGame 解耦，loadGame 失败不影响心跳）
    this.sendHeartbeat()
    this.heartbeatTimer = setInterval(function () {
      this.sendHeartbeat()
    }.bind(this), 5000)
  },

  beforeDestroy() {
    if (this.pollTimer) clearInterval(this.pollTimer)
    if (this.heartbeatTimer) clearInterval(this.heartbeatTimer)
    if (this.actionTimer) clearInterval(this.actionTimer)
    if (this.readyTimer) clearInterval(this.readyTimer)
    if (this.communityCardsFlipCompleteTimer) clearTimeout(this.communityCardsFlipCompleteTimer)
  },

  methods: {
    // ---- 心跳（独立，不依赖 loadGame） ----
    sendHeartbeat() {
      if (!this.user) return
      this.$http.post('/dpRoom/heartbeat', null, {
        params: {roomId: this.roomId, nickname: this.user.nickname}
      }).catch(function (e) {
        console.error('心跳失败', e)
      })
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
          alert('房间已解散或你已被移出')
          clearInterval(this.pollTimer)
          clearInterval(this.heartbeatTimer)
          this.$router.push('/home')
          return
        }

        this.owner = room.owner
        this.players = room.players || []
        this.playing = room.playing
        this.stage = room.currentStage
        this.communityCards = room.communityCards || []
        this.syncCommunityCardsFlipState(room.communityCards || [])
        this.pot = room.pot
        this.pots = room.pots || []
        this.currentBetToCall = room.currentBetToCall
        this.actIndex = room.currentActorIndex
        this.spectators = room.spectators || []
        // 报名下一局状态以服务端为准（被清到观众席后不在 waitNextHand，避免界面“已报名”但实际未报名导致流程卡死）
        var list = room.waitNextHand || []
        this.nextHandReady = !!(this.user && list.indexOf(this.user.nickname) !== -1)
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
        if (res.data !== 'ok') alert('操作失败')
        await this.loadGame()
      } catch (err) {
        alert('网络错误: ' + err.message)
      }
    },
    // 结算后筹码归零时补码
    async rebuy() {
      try {
        var res = await this.$http.post('/dpRoom/rebuy', null, {
          params: {roomId: this.roomId, nickname: this.user.nickname}
        })
        if (res.data !== 'ok') {
          alert('补码失败：' + res.data)
        } else {
          alert('补码成功，可以准备下一局了')
        }
        await this.loadGame()
      } catch (err) {
        alert('网络错误: ' + err.message)
      }
    },

    // ---- 房主开始游戏 ----
    async startGame() {
      var notReady = this.players.filter(function (p) {
        return !p.ready
      })
      if (notReady.length > 0) {
        alert('还有玩家未准备: ' + notReady.map(function (p) {
          return p.nickname
        }).join(', '))
        return
      }
      if (this.players.length < 2) {
        alert('至少需要2名玩家')
        return
      }
      try {
        var res = await this.$http.post('/dpRoom/startGame', null, {
          params: {roomId: this.roomId, ownerNickname: this.user.nickname}
        })
        if (res.data !== 'ok') alert('开始失败')
        await this.loadGame()
      } catch (err) {
        alert('网络错误: ' + err.message)
      }
    },

    // ---- 跟注/过牌 ----
    async doCall() {
      await this.submitBet(this.callAmount)
    },

    // ---- 加注 ----
    async doRaise() {
      if (this.raiseAmount < this.minRaise) {
        alert('加注额不能低于 ' + this.minRaise)
        return
      }
      if (this.raiseAmount > this.myChips) {
        alert('筹码不足！')
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
        if (res.data !== 'ok') alert('下注失败，请检查金额')
        this.raiseAmount = 0
        await this.loadGame()
      } catch (err) {
        alert('网络错误: ' + err.message)
      }
    },

    // ---- 弃牌 ----
    async doFold() {
      try {
        var res = await this.$http.post('/dpRoom/fold', null, {
          params: {roomId: this.roomId, nickname: this.user.nickname}
        })
        if (res.data !== 'ok') alert('弃牌失败')
        await this.loadGame()
      } catch (err) {
        alert('网络错误: ' + err.message)
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

    // 统一的玩家卡片点击入口：优先处理房主移交，其次才是摊牌选赢家
    onPlayerCardClick(nickname) {
      // 房主移交模式优先级最高
      if (this.transferOwnerMode && this.isOwner) {
        this.doTransferOwner(nickname)
        return
      }
      // 其他情况沿用原有的摊牌点击逻辑
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

    isPotWinner(potIndex, nickname) {
      var winners = this.potWinners[potIndex] || []
      return winners.indexOf(nickname) > -1
    },

    // ---- 按池确认结算 ----
    async confirmPotJudge() {
      // 拼接格式: "0:Alice,Bob;1:Charlie"
      var parts = []
      for (var i = 0; i < this.pots.length; i++) {
        var winners = this.potWinners[i] || []
        if (winners.length === 0) {
          alert('第 ' + (i === 0 ? '主' : i) + ' 池还没选赢家')
          return
        }
        parts.push(i + ':' + winners.join(','))
      }
      var potWinnersStr = parts.join(';')

      // 组装确认信息
      var msg = '确认结算？\n'
      for (var j = 0; j < this.pots.length; j++) {
        var potName = j === 0 ? '主池' : '边池 ' + j
        msg += potName + '(' + this.pots[j].amount + ') -> ' + (this.potWinners[j] || []).join(', ') + '\n'
      }
      if (!confirm(msg)) return

      try {
        var res = await this.$http.post('/dpRoom/judgeWin', null, {
          params: {roomId: this.roomId, potWinners: potWinnersStr}
        })
        if (res.data !== 'ok') alert('结算失败')
        this.potWinners = {}
        this.selectedWinners = []
        await this.loadGame()
      } catch (err) {
        alert('网络错误: ' + err.message)
      }
    },

    // ---- 房主：确认结算 ----
    async confirmJudgeWin() {
      if (this.selectedWinners.length === 0) {
        alert('请至少选择一位赢家')
        return
      }
      var names = this.selectedWinners.join(', ')
      if (!confirm('确定由 [' + names + '] 平分底池 ' + this.pot + ' 吗？')) return

      try {
        var res = await this.$http.post('/dpRoom/judgeWin', null, {
          params: {roomId: this.roomId, winnerNickname: this.selectedWinners.join(',')}
        })
        if (res.data !== 'ok') alert('结算失败')
        this.selectedWinners = []
        await this.loadGame()
      } catch (err) {
        alert('网络错误: ' + err.message)
      }
    },

    // ---- 房主：重新发牌 ----
    async doNewHand() {
      if (!confirm('确定要重新发牌吗？请先结算完毕')) return
      try {
        var res = await this.$http.post('/dpRoom/newHand', null, {
          params: {roomId: this.roomId, ownerNickname: this.user.nickname}
        })
        if (res.data !== 'ok') alert('发牌失败')
        this.selectedWinners = []
        this.potWinners = {}
        await this.loadGame()
      } catch (err) {
        alert('网络错误: ' + err.message)
      }
    },

    // ---- 房主：移交房主 ----
    async doTransferOwner(nickname) {
      // 自己不能移交给自己
      if (nickname === this.user.nickname) {
        this.transferOwnerMode = false
        return
      }
      var ok = confirm('确定将房主移交给 [' + nickname + '] 吗？')
      if (!ok) {
        this.transferOwnerMode = false
        return
      }
      try {
        var res = await this.$http.post('/dpRoom/transferOwner', null, {
          params: {
            roomId: this.roomId,
            fromNickname: this.user.nickname,
            toNickname: nickname
          }
        })
        if (res.data !== 'ok') {
          alert('移交失败：' + res.data)
        } else {
          alert('已将房主移交给 ' + nickname)
        }
        await this.loadGame()
      } catch (err) {
        alert('网络错误: ' + err.message)
      } finally {
        this.transferOwnerMode = false
      }
    },

    // ---- 退出 ----
    async exitGame() {
      if (!confirm('确定退出对局？')) return
      try {
        await this.$http.post('/dpRoom/exitRoom', null, {
          params: {roomId: this.roomId, nickname: this.user.nickname}
        })
      } catch (err) {
        console.error('退出失败', err)
      }
      clearInterval(this.pollTimer)
      clearInterval(this.heartbeatTimer)
      this.$router.push('/home')
    },

    // ---- 观众：报名在下一局加入 ----
    async readyNextHand() {
      if (!this.user) return
      try {
        var res = await this.$http.post('/dpRoom/readyNextHand', null, {
          params: {roomId: this.roomId, nickname: this.user.nickname}
        })
        if (res.data === 'ok') {
          this.nextHandReady = true
          alert('已报名下一局，将在下一局开局时自动加入对局')
        } else {
          alert('报名失败：' + res.data)
        }
      } catch (err) {
        alert('网络错误: ' + err.message)
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
        })(j, 350 * (j - prevLen))
      }
      if (numNew > 0) {
        var flipDuration = 600
        var lastFlipStart = 350 * (numNew - 1)
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

    // 后端牌格式: "hearts_A", "spades_10", "diamonds_K" 等
    // 只显示数字/字母，颜色靠方块背景区分红黑
    getCardDisplay(c) {
      if (!c || !c.includes('_')) return '?'
      return c.split('_')[1]
    },

    getCardClass(c) {
      if (!c || !c.includes('_')) return 'card-base bg-gray'
      var suit = c.split('_')[0]
      if (suit === 'hearts') return 'card-base bg-red'
      if (suit === 'diamonds') return 'card-base bg-blue'
      if (suit === 'clubs') return 'card-base bg-green'
      if (suit === 'spades') return 'card-base bg-black'
      return 'card-base bg-gray'
    },

    getPlayerBoxStyle(p, i) {
      var s = {
        background: '#fff',
        padding: '12px',
        borderRadius: '10px',
        border: '2px solid transparent',
        transition: 'all 0.2s'
      }

      // 离线位：灰显，不参与行动高亮
      if (p.leftThisHand) {
        s.background = '#f5f5f5'
        s.borderColor = '#d9d9d9'
        s.opacity = '0.85'
        return s
      }

      // 当前行动者金色边框
      if (this.actIndex === i) {
        s.borderColor = '#faad14'
        s.background = '#fffbe6'
      }

      // 自己蓝色边框
      if (this.isMe(p.nickname)) {
        s.borderColor = '#1890ff'
      }

      // 弃牌变灰
      if (p.fold) {
        s.opacity = '0.5'
      }

      // 摊牌选中绿色
      if (this.selectedWinners.includes(p.nickname)) {
        s.borderColor = '#52c41a'
        s.borderWidth = '3px'
        s.background = '#f6ffed'
        s.opacity = '1'
      } else if (this.stage === 'showdown' && this.isOwner) {
        s.cursor = 'pointer'
        s.borderStyle = 'dashed'
        s.borderColor = '#d9d9d9'
      }

      return s
    },

    startCountdown() {
      this.stopCountdown(); // 先清除旧的
      this.timeLeft = 30;
      this.actionTimer = setInterval(() => {
        if (this.timeLeft > 0) {
          this.timeLeft--;
        } else {
          this.stopCountdown();
          // 这里可以加个逻辑，比如时间到了自动弃牌：this.doFold();
        }
      }, 1000);
    },

    stopCountdown() {
      if (this.actionTimer) {
        clearInterval(this.actionTimer);
        this.actionTimer = null;
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
    },

    // ========== 新增：牌型计算方法 ==========

    /**
     * 计算最大牌型
     * @param holeCards - 玩家手牌，如 ["hearts_A", "spades_K"]
     * @param communityCards - 公共牌，如 ["hearts_10", "hearts_J", "hearts_Q", "clubs_2", "diamonds_5"]
     * @returns 牌型名称，如 "皇家同花顺"、"同花顺"、"四条" 等
     */
    getHandRank(holeCards, communityCards) {
      if (!holeCards || holeCards.length < 2 || !communityCards || communityCards.length < 3) {
        return '牌不足'
      }

      // 合并所有牌
      var allCards = holeCards.concat(communityCards)

      // 解析牌：将 "hearts_A" 转成 { suit: 'hearts', rank: 14 }
      var rankMap = {
        '2': 2,
        '3': 3,
        '4': 4,
        '5': 5,
        '6': 6,
        '7': 7,
        '8': 8,
        '9': 9,
        '10': 10,
        'J': 11,
        'Q': 12,
        'K': 13,
        'A': 14
      }
      var parsed = []
      for (var i = 0; i < allCards.length; i++) {
        var c = allCards[i]
        var parts = c.split('_')
        parsed.push({suit: parts[0], rank: rankMap[parts[1]] || 0})
      }

      // 生成所有5张牌的组合 (C(7,5) = 21种)
      var combos = this.getCombinations(parsed, 5)

      // 对每种组合评分，取最高分
      var bestScore = 0
      var bestName = ''

      for (var j = 0; j < combos.length; j++) {
        var result = this.evaluateHand(combos[j])
        if (result.score > bestScore) {
          bestScore = result.score
          bestName = result.name
        }
      }

      return bestName
    },

    // 生成组合的辅助函数
    getCombinations(arr, k) {
      var result = []
      var combo = []

      function backtrack(start) {
        if (combo.length === k) {
          result.push(combo.slice())
          return
        }
        for (var i = start; i < arr.length; i++) {
          combo.push(arr[i])
          backtrack(i + 1)
          combo.pop()
        }
      }

      backtrack(0)
      return result
    },

    // 评估5张牌的牌型
    evaluateHand(cards) {
      // 复制数组避免修改原数据
      var cardsCopy = cards.slice()
      // 按点数排序（降序）
      cardsCopy.sort(function (a, b) {
        return b.rank - a.rank
      })

      var ranks = []
      var suits = []
      for (var i = 0; i < cardsCopy.length; i++) {
        ranks.push(cardsCopy[i].rank)
        suits.push(cardsCopy[i].suit)
      }

      // 判断同花
      var isFlush = true
      for (var f = 1; f < suits.length; f++) {
        if (suits[f] !== suits[0]) {
          isFlush = false
          break
        }
      }

      // 判断顺子
      var isStraight = false
      // 普通顺子
      var uniqueRanks = []
      for (var u = 0; u < ranks.length; u++) {
        if (uniqueRanks.indexOf(ranks[u]) === -1) uniqueRanks.push(ranks[u])
      }
      if (ranks[0] - ranks[4] === 4 && uniqueRanks.length === 5) {
        isStraight = true
      }
      // A-2-3-4-5 小顺子
      if (ranks[0] === 14 && ranks[1] === 5 && ranks[2] === 4 && ranks[3] === 3 && ranks[4] === 2) {
        isStraight = true
      }

      // 统计点数出现次数
      var rankCount = {}
      for (var r = 0; r < ranks.length; r++) {
        rankCount[ranks[r]] = (rankCount[ranks[r]] || 0) + 1
      }
      var counts = []
      for (var key in rankCount) {
        counts.push(rankCount[key])
      }
      counts.sort(function (a, b) {
        return b - a
      })

      // 判断牌型并返回分数
      // 分数越高越大，方便比较

      // 皇家同花顺
      if (isFlush && isStraight && ranks[0] === 14 && ranks[1] === 13) {
        return {score: 10, name: '皇家同花顺'}
      }

      // 同花顺
      if (isFlush && isStraight) {
        return {score: 9, name: '同花顺'}
      }

      // 四条
      if (counts[0] === 4) {
        return {score: 8, name: '四条'}
      }

      // 葫芦（满堂红）
      if (counts[0] === 3 && counts[1] === 2) {
        return {score: 7, name: '葫芦'}
      }

      // 同花
      if (isFlush) {
        return {score: 6, name: '同花'}
      }

      // 顺子
      if (isStraight) {
        return {score: 5, name: '顺子'}
      }

      // 三条
      if (counts[0] === 3) {
        return {score: 4, name: '三条'}
      }

      // 两对
      if (counts[0] === 2 && counts[1] === 2) {
        return {score: 3, name: '两对'}
      }

      // 一对
      if (counts[0] === 2) {
        return {score: 2, name: '一对'}
      }

      // 高牌
      return {score: 1, name: '高牌'}
    }
  }
}
</script>

<style scoped>
/* 牌型说明弹窗 */
.hand-rank-modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}
.hand-rank-modal {
  background: #fff;
  border-radius: 10px;
  padding: 20px;
  max-width: 400px;
  width: 90%;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.2);
}
.hand-rank-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.hand-rank-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  background: #fafafa;
  border-radius: 6px;
  font-size: 14px;
}
.hand-rank-num {
  width: 24px;
  height: 24px;
  background: #1890ff;
  color: #fff;
  border-radius: 4px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 12px;
  flex-shrink: 0;
}
.hand-rank-name {
  font-weight: bold;
  color: #333;
  min-width: 90px;
  flex-shrink: 0;
}
.hand-rank-cards {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

/* 公共牌翻转动画 */
.card-flip-wrapper {
  perspective: 600px;
  width: 44px;
  height: 62px;
}
.card-flip-inner {
  position: relative;
  width: 100%;
  height: 100%;
  transition: transform 0.6s ease-in-out;
  transform-style: preserve-3d;
}
.card-flip-inner.flipped {
  transform: rotateY(180deg);
}
.card-flip-inner .card-face {
  position: absolute;
  width: 100%;
  height: 100%;
  backface-visibility: hidden;
  -webkit-backface-visibility: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 5px;
}
.card-flip-inner .card-back {
  background: linear-gradient(135deg, #8c8c8c 0%, #6b6b6b 100%);
  color: #d9d9d9;
  border: 1px dashed #999;
  z-index: 2;
}
.card-flip-inner .card-front {
  transform: rotateY(180deg);
  z-index: 1;
}

/* 扑克牌基础美化 */
/* 扑克牌基础美化 - 沉稳色调版 */
.card-base {
  width: 44px;
  height: 62px;
  border-radius: 5px; /* 稍微方一点更硬朗 */
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #e0e0e0; /* 文字不再用纯白，用浅灰更有质感 */
  font-weight: 800;
  font-size: 18px;
  font-family: 'Garamond', 'Georgia', serif; /* 换个带衬线的字体，更有赌场风 */

  /* 镶边效果：深色细边框 + 低调阴影 */
  border: 1px solid rgba(0, 0, 0, 0.3);
  box-shadow: 2px 2px 6px rgba(0, 0, 0, 0.4), inset 0 0 12px rgba(0, 0, 0, 0.2);

  position: relative;
  transition: all 0.2s ease;
  overflow: hidden;
}

/* 内部装饰线：增加"高级感"的关键 */
.card-base::before {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  right: 2px;
  bottom: 2px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 3px;
  pointer-events: none;
}

/* --- 暗色调花色背景 --- */

/* 暗红 (红桃) */
.bg-red {
  background: linear-gradient(135deg, #8b0000 0%, #5d0000 100%);
}

/* 暗蓝 (方块) */
.bg-blue {
  background: linear-gradient(135deg, #1e3a5f 0%, #102a43 100%);
}

/* 暗绿 (梅花) */
.bg-green {
  background: linear-gradient(135deg, #1b4332 0%, #081c15 100%);
}

/* 墨黑 (黑桃) */
.bg-black {
  background: linear-gradient(135deg, #2d2d2d 0%, #1a1a1a 100%);
}

/* 盖牌/未知 */
.bg-gray {
  background: linear-gradient(135deg, #434343 0%, #232323 100%);
  color: #666;
  border: 1px dashed #555;
}

/* 悬停效果：轻微发光 */
.card-base:hover {
  transform: translateY(-4px);
  box-shadow: 0 6px 12px rgba(0, 0, 0, 0.5);
  filter: brightness(1.1);
}

.bg-red {
  background: #f5222d;
}

.bg-blue {
  background: #1890ff;
}

.bg-green {
  background: #52c41a;
}

.bg-black {
  background: #2f3542;
}

.bg-gray {
  background: #8c8c8c;
}

/* 玩家手牌翻转动画：在摊牌/结算阶段亮牌时增加翻牌效果 */
.hole-card-flip {
  transform-origin: center;
  animation: hole-card-flip-anim 0.45s ease-out;
}

@keyframes hole-card-flip-anim {
  0% {
    transform: rotateY(90deg);
  }
  100% {
    transform: rotateY(0deg);
  }
}

/* 牌型说明弹窗内的小牌 */
.hand-rank-cards .hand-rank-card {
  width: 32px;
  height: 46px;
  font-size: 12px;
}
.hand-rank-cards .hand-rank-card:hover {
  transform: none;
  filter: none;
}
</style>