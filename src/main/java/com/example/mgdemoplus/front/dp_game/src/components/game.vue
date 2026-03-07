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
      <button @click="exitGame"
              style="background:#ff4d4f; color:#fff; border:none; padding:8px 15px; border-radius:5px; cursor:pointer;">
        退出对局
      </button>

    </div>

    <!-- ========== 未开始：准备/开始 ========== -->
<!--    <div v-if="!playing" style="background:#fff; padding:20px; border-radius:10px; text-align:center;">-->
<!--      <h2 style="margin:0 0 15px;">等待开始</h2>-->
<!--      <div v-for="p in players" :key="p.nickname"-->
<!--           style="padding:8px 0; display:flex; justify-content:center; align-items:center; gap:10px;">-->
<!--        <span style="font-weight:bold;">{{ p.nickname }}</span>-->
<!--        <span :style="{ color: p.ready ? '#52c41a' : '#ff4d4f' }">{{ p.ready ? '已准备' : '未准备' }}</span>-->
<!--        <span v-if="p.nickname === owner"-->
<!--              style="background:#faad14; color:#fff; padding:1px 6px; border-radius:3px; font-size:12px;">房主</span>-->
<!--      </div>-->
<!--      <div style="margin-top:20px; display:flex; justify-content:center; gap:10px;">-->
<!--        <button @click="toggleReady"-->
<!--                style="padding:8px 20px; border:none; border-radius:5px; cursor:pointer; background:#1890ff; color:#fff;">-->
<!--          {{ myReady ? '取消准备' : '准备' }}-->
<!--        </button>-->
<!--        <button v-if="isOwner" @click="startGame"-->
<!--                style="padding:8px 20px; border:none; border-radius:5px; cursor:pointer; background:#52c41a; color:#fff;">-->
<!--          开始游戏-->
<!--        </button>-->
<!--      </div>-->
<!--    </div>-->

    <!-- ========== 游戏进行中 ========== -->


      <!-- 观战提示 + 下一局加入按钮：当前不在 players 中的用户视为观众 -->
      <div v-if="!myPlayer"
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

      <!-- 公共牌 -->
      <div style="display:flex; gap:8px; justify-content:center; margin:20px 0;">
        <div v-for="c in communityCards" :key="c" :class="getCardClass(c)">
          {{ getCardDisplay(c) }}
        </div>
        <div v-for="i in (5 - communityCards.length)" :key="'e' + i" class="card-base bg-gray">?</div>
      </div>

      <!-- 玩家列表 -->
      <div style="display:grid; grid-template-columns:1fr 1fr; gap:15px;">
        <div
            v-for="(p, i) in players"
            :key="p.nickname"
            :style="getPlayerBoxStyle(p, i)"
            @click="handleJudgeClick(p.nickname)"
        >
          <!-- 标记 -->
          <div style="display:flex; gap:5px; margin-bottom:5px;">
            <span v-if="p.dealer"
                  style="background:#faad14; color:#fff; padding:1px 5px; border-radius:3px; font-size:12px;">D</span>
            <span v-if="p.blind === 1"
                  style="background:#722ed1; color:#fff; padding:1px 5px; border-radius:3px; font-size:12px;">SB</span>
            <span v-if="p.blind === 2"
                  style="background:#52c41a; color:#fff; padding:1px 5px; border-radius:3px; font-size:12px;">BB</span>
          </div>
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
          <!--          <div style="font-size:13px; color:#666;">筹码: {{ p.chips }} | 本轮注: {{ p.bet }}</div>-->
          <!-- 手牌：自己始终能看；摊牌时只有未弃牌的人亮牌，弃牌的人依然盖牌 -->
          <div style="display:flex; gap:5px; margin:8px 0; justify-content:center;">
            <template v-if="isMe(p.nickname) || (stage === 'showdown' && !p.fold)">
              <div v-for="(c, ci) in p.holeCards" :key="'h' + ci" :class="getCardClass(c)"
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
          <!-- 状态 -->
          <div style="font-weight:bold; font-size:12px;"
               :style="{ color: p.fold ? '#ff4d4f' : (actIndex === i ? '#faad14' : '#52c41a') }">
            {{ p.fold ? '已弃牌' : (actIndex === i ? '思考中...' : '进行中') }}
          </div>
          <!-- 摊牌选赢家提示：弃牌的人也能被选（盖牌结算） -->
<!--          <div v-if="stage === 'showdown' && isOwner" style="color:#1890ff; font-size:11px; margin-top:4px;">-->
<!--            {{ selectedWinners.includes(p.nickname) ? '已选中' : '[点我选为赢家]' }}-->
<!--          </div>-->
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
      <span style="color: #ff4d4f; font-size: 18px; font-weight: 900; font-family: 'Arial Black', sans-serif; line-height: 1;">
        {{ timeLeft }}
      </span>
        </div>

        <button @click="doCall"
                style="height: 40px; padding: 0 18px; background: #1890ff; color: #fff; border: none; border-radius: 5px; cursor: pointer; font-weight: bold; display: flex; align-items: center;">
          {{ callAmount > 0 ? '跟注 ' + callAmount : '过牌' }}
        </button>

        <div style="display:flex; align-items:center; gap:5px; height: 40px;">
          <button @click="raiseAmount += 5" style="height: 32px; width: 32px; padding: 0; background: #fff; border: 1px solid #f57f17; color: #f57f17; border-radius: 4px; cursor: pointer; font-weight: bold;">+5</button>
          <button @click="raiseAmount += 10" style="height: 32px; width: 32px; padding: 0; background: #fff; border: 1px solid #f57f17; color: #f57f17; border-radius: 4px; cursor: pointer; font-weight: bold;">+10</button>
          <input type="number" v-model.number="raiseAmount" :min="minRaise" :max="myChips"
                 style="width: 60px; height: 32px; padding: 0; border: 1px solid #d9d9d9; border-radius: 4px; text-align: center;"/>
          <button @click="raiseAmount -= 10" style="height: 32px; width: 32px; padding: 0; background: #fff; border: 1px solid #f57f17; color: #f57f17; border-radius: 4px; cursor: pointer; font-weight: bold;">-10</button>
          <button @click="raiseAmount -= 5" style="height: 32px; width: 32px; padding: 0; background: #fff; border: 1px solid #f57f17; color: #f57f17; border-radius: 4px; cursor: pointer; font-weight: bold;">-5</button>
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

        <!-- 下一阶段：当 actIndex === -1（所有人跟齐）且不在 showdown -->


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

        <!-- 重新发牌 -->
        <button
            @click="doNewHand"
            style="width:100%; padding:10px; background:#616161; color:#fff; border:none; border-radius:5px; cursor:pointer; font-weight:bold;"
        >
          重新发牌
        </button>
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

      // UI
      raiseAmount: 0,
      selectedWinners: [],   // 旧的简单模式备用
      potWinners: {},        // 按池选赢家 { 0: ['Alice'], 1: ['Bob','Charlie'] }
      nextHandReady: false,  // 是否已报名下一局加入
      loading: false,

      // 定时器
      pollTimer: null,
      heartbeatTimer: null,
      //游戏计时器
      actionTimer: null,
      timeLeft: 30
    }
  },

  computed: {
    stageCN() {
      var m = {preflop: '翻牌前', flop: '翻牌圈', turn: '转牌圈', river: '河牌圈', showdown: '摊牌结算'}
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

      // 如果这个人存在，且名字是我自己
      if (currentPlayer && currentPlayer.nickname === this.user.nickname) {
        this.startCountdown();
      } else {
        this.stopCountdown();
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
  },

  methods: {
  //   showToast(message) {
  //     // 1. 创建 div
  //     const toast = document.createElement('div');
  //     toast.innerText = message;
  //
  //     // 2. 设置样式 (黑底白字，圆角，居中)
  //     Object.assign(toast.style, {
  //       position: 'fixed',
  //       top: '50%',
  //       left: '50%',
  //       transform: 'translate(-50%, -50%)',
  //       backgroundColor: 'rgba(0, 0, 0, 0.7)',
  //       color: '#fff',
  //       padding: '10px 20px',
  //       borderRadius: '5px',
  //       zIndex: '9999',
  //       fontSize: '14px',
  //       transition: 'opacity 0.5s'
  //     });
  //
  //     // 3. 挂载到页面
  //     document.body.appendChild(toast);
  //
  //     // 4. 2秒后消失并移除
  //     setTimeout(() => {
  //       toast.style.opacity = '0';
  //       setTimeout(() => document.body.removeChild(toast), 500);
  //     }, 2000);
  //   },
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
        var res = await this.$http.get('/dpRoom/getAllRooms',{
          params: { roomId: this.roomId }
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
        this.pot = room.pot
        this.pots = room.pots || []
        this.currentBetToCall = room.currentBetToCall
        this.actIndex = room.currentActorIndex
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

    // ---- 房主：下一阶段 ----
    // async doNextStage() {
    //   try {
    //     var res = await this.$http.post('/dpRoom/nextStage', null, {
    //       params: {roomId: this.roomId, ownerNickname: this.user.nickname}
    //     })
    //     if (res.data !== 'ok') alert('推进失败（可能还有玩家未跟齐）')
    //     await this.loadGame()
    //   } catch (err) {
    //     alert('网络错误: ' + err.message)
    //   }
    // },

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
      if (!confirm('确定要重新发牌吗？当前底池将清零')) return
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
  }
  }
}
</script>

<style scoped>
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

/* 内部装饰线：增加“高级感”的关键 */
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
</style>
