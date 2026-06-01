<template>
  <transition name="dp-cli-root">
    <div v-if="open" class="dp-cli" @click.self="close">
      <div class="dp-cli__shell" :class="shellAnimClass" @animationend="onShellAnimEnd">
        <!-- CRT 图层 -->
        <div v-if="showCrtLayers" class="dp-cli__snow" :class="{ 'dp-cli__snow--active': snowActive }" aria-hidden="true">
          <span class="dp-cli__snow-noise" />
          <span class="dp-cli__snow-bars" />
        </div>
        <div class="dp-cli__scanlines" aria-hidden="true" />
        <div class="dp-cli__flash" :class="{ 'dp-cli__flash--pulse': flashPulse }" />

        <!-- 头部 -->
        <div class="dp-cli__head">
          <span class="dp-cli__head-title">&gt; TERMINAL v2.4{{ vm && vm.isOwner ? ' [ROOT]' : '' }}</span>
          <button type="button" class="dp-cli__close" @click="close" aria-label="关闭终端">[X]</button>
        </div>

        <!-- 命令历史 -->
        <div ref="history" class="dp-cli__history">
          <div v-for="(entry, i) in history" :key="i" class="dp-cli__entry"
            :class="{ 'dp-cli__entry--err': entry.type === 'err', 'dp-cli__entry--ok': entry.type === 'ok', 'dp-cli__entry--info': entry.type === 'info' }">
            <span class="dp-cli__prompt">&gt;</span>
            <span class="dp-cli__cmd">{{ entry.cmd }}</span>
            <div v-if="entry.out" class="dp-cli__out">{{ entry.out }}</div>
          </div>
        </div>

        <!-- 输入行 -->
        <div class="dp-cli__input-line">
          <span class="dp-cli__prompt">&gt;</span>
          <span class="dp-cli__preinput">{{ inputBuffer }}</span><span class="dp-cli__cursor">█</span>
          <input ref="hiddenInput" class="dp-cli__hidden-input" type="text"
            autocomplete="off" autocorrect="off" spellcheck="false"
            :value="inputBuffer" @input="onInput" @keydown="onKeydown"
            aria-label="终端命令行输入" />
        </div>

        <!-- 提示栏 -->
        <div class="dp-cli__hint-bar">
          <span>~ 开关</span>
          <span>Ctrl+D 关闭</span>
          <span>↑↓ 历史</span>
          <span>Tab 补全</span>
          <span>music 音乐</span>
          <span>hands 历史</span>
          <span>help 帮助</span>
          <span v-if="vm && vm.isOwner" style="color:rgba(255,224,102,0.5)">npc/kick/transfer/reveal</span>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
// ---- 命令注册表 ----
var CMDS = {
  // 玩家操作
  raise:  { u: 'raise <数额>',       d: '加注指定数量',          act: 'doRaise',   needs: 'amt' },
  fold:   { u: 'fold',               d: '盖牌弃牌',              act: 'doFold' },
  call:   { u: 'call',               d: '跟注',                  act: 'doCall' },
  check:  { u: 'check',              d: '过牌',                  act: 'doCall' },
  allin:  { u: 'allin',              d: '全压',                  act: 'doAllIn' },
  cards:  { u: 'cards',              d: '查看手牌(全息投影)',    act: 'openHandCards' },
  players:{ u: 'players',            d: '列出玩家与筹码',        act: 'listPlayers' },
  pot:    { u: 'pot',                d: '显示底池与阶段',        act: 'showPot' },
  stats:  { u: 'stats',              d: '显示对局状态',          act: 'showStats' },
  sit:    { u: 'sit',                d: '入座/准备(自动选API)',  act: 'doSit' },
  leave:  { u: 'leave',              d: '离座进入观众席',        act: 'doLeave' },
  // 房主操作
  npc:    { u: 'npc add <type> [n]', d: '添加NPC(npc list看类型)', act: 'npcAdd',  needs: 'args', owner: true },
  kick:   { u: 'kick <昵称> [...]',  d: '踢出玩家(支持多个)',    act: 'kickPlayers', needs: 'args', owner: true },
  transfer:{u: 'transfer <昵称>',    d: '移交房主',              act: 'transferOwner', needs: 'nick', owner: true },
  reveal: { u: 'reveal',             d: '切换看穿底牌 开/关',   act: 'toggleReveal', owner: true },
  music:  { u: 'music [play|pause|stop]', d: '打开音乐播放器',  act: 'musicCmd', needs: 'args' },
  hands:  { u: 'hands',              d: '打开对局历史查看器',    act: 'fetchHands' },
  clear:  { u: 'clear',              d: '清屏',                  act: 'clear' },
  exit:   { u: 'exit',               d: '关闭终端',              act: 'exit' },
  help:   { u: 'help',               d: '显示命令列表',          act: 'help' }
}

// NPC 类型映射
var NPC_TYPES = {
  fish:    { archetype: 'FISH',    desc: '被动跟注型' },
  maniac:  { archetype: 'MANIAC',  desc: '疯狂进攻型' },
  tag:     { archetype: 'TAG',     desc: '紧凶型' },
  lag:     { archetype: 'LAG',     desc: '松凶型' },
  nit:     { archetype: 'NIT',     desc: '岩石型' },
  call:    { archetype: 'CALL',    desc: '站岗型' },
  llm:     { desc: '大模型NPC' },
  llmglobal:{ desc: '全局叙事大模型' },
  custom:  { desc: '自定义NPC' }
}

export default {
  name: 'DpTerminalCli',
  inject: { dpGameView: { default: null } },
  data: function () {
    return {
      open: false,
      phase: 'idle',
      inputBuffer: '',
      history: [],
      cmdHistory: [],
      historyIdx: -1,
      flashTimer: null,
      snowTimer: null
    }
  },
  computed: {
    vm: function () { return this.dpGameView },
    cmdList: function () { return Object.keys(CMDS) },
    snowActive: function () { return this.phase === 'snow' },
    flashPulse: function () { return this.phase === 'reveal-flash' },
    showCrtLayers: function () {
      var vm = this.vm
      return vm && vm.gameUiTheme === 'retro8bit' && vm.viewportWidth > 600 && !vm.ecoMode && !vm.prefersReducedMotion
    },
    shellAnimClass: function () {
      return {
        'dp-cli__shell--slide-in': this.phase === 'slide-in',
        'dp-cli__shell--snow': this.phase === 'snow',
        'dp-cli__shell--ready': this.phase === 'ready' || this.phase === 'reveal-flash',
        'dp-cli__shell--retract': this.phase === 'retract',
        'dp-cli__shell--instant': !this.showCrtLayers
      }
    }
  },
  beforeDestroy: function () { this.clearTimers() },
  methods: {
    clearTimers: function () {
      if (this.flashTimer) { clearTimeout(this.flashTimer); this.flashTimer = null }
      if (this.snowTimer) { clearTimeout(this.snowTimer); this.snowTimer = null }
    },

    // ====== 开/关 ======
    toggle: function () { this.open ? this.close() : this.openTerminal() },
    openTerminal: function () {
      this.open = true; this.inputBuffer = ''; this.historyIdx = -1
      this.phase = this.showCrtLayers ? 'slide-in' : 'ready'
      var self = this; this.$nextTick(function () { self.focusInput() })
    },
    close: function () {
      if (this.showCrtLayers && this.phase !== 'retract' && this.phase !== 'idle') {
        this.phase = 'retract'; return
      }
      this.open = false; this.phase = 'idle'
    },
    onShellAnimEnd: function (e) {
      if (e.target !== e.currentTarget) return
      var n = e.animationName || ''
      if (n.indexOf('dp-cli-slide-in') !== -1) {
        this.phase = 'snow'
        var self = this
        this.snowTimer = setTimeout(function () {
          self.phase = 'reveal-flash'
          self.flashTimer = setTimeout(function () { self.phase = 'ready'; self.focusInput() }, 100)
        }, 300)
        return
      }
      if (n.indexOf('dp-cli-retract') !== -1) { this.open = false; this.phase = 'idle' }
    },

    // ====== 输入 ======
    focusInput: function () {
      var inp = this.$refs.hiddenInput
      if (inp && typeof inp.focus === 'function') inp.focus()
    },
    onInput: function (e) { this.inputBuffer = e.target.value || '' },
    onKeydown: function (e) {
      if (e.key === 'Enter') { e.preventDefault(); this.executeLine(); return }
      if (e.key === 'Escape') { return } // 不抢全屏键
      if (e.ctrlKey && e.key === 'd') { e.preventDefault(); this.close(); return }
      if (e.key === '`' || e.key === '~') { e.preventDefault(); this.close(); return }
      if (e.key === 'Tab') { e.preventDefault(); this.doAutocomplete(); return }
      if (e.key === 'ArrowUp') { e.preventDefault(); this.navHistory(-1); return }
      if (e.key === 'ArrowDown') { e.preventDefault(); this.navHistory(1); return }
    },
    doAutocomplete: function () {
      var input = this.inputBuffer.trim()
      if (!input) return
      var lower = input.toLowerCase()

      // —— npc 命令：支持 npc / npc ad / npc add / npc add fi / npc fi 所有阶段 ——
      if (lower === 'npc' || lower.indexOf('npc ') === 0) {
        this.autocompleteNpc(input); return
      }
      // —— kick 命令：kick / kick 张 ——
      if (lower === 'kick' || lower.indexOf('kick ') === 0) {
        this.autocompletePlayerArg(input, 'kick', this.getKickablePlayers()); return
      }
      // —— transfer 命令：transfer / transfer 张 ——
      if (lower === 'transfer' || lower.indexOf('transfer ') === 0) {
        this.autocompletePlayerArg(input, 'transfer', this.getTransferablePlayerNicks()); return
      }
      // —— music 命令：music / music l / music play ——
      if (lower === 'music' || lower.indexOf('music ') === 0) {
        this.autocompleteMusic(input); return
      }
      // —— 通用命令名补全 ——
      var matches = this.cmdList.filter(function (c) { return c.indexOf(lower) === 0 })
      if (matches.length === 1) { this.inputBuffer = matches[0] + ' ' }
      else if (matches.length > 1) { this.addEntry('info', lower, matches.join('  ')) }
    },

    // 补全 "npc <subcmd|type>" 所有阶段
    autocompleteNpc: function (input) {
      var parts = input.split(/\s+/)
      // parts[0] = "npc" guaranteed
      var seg1 = parts[1] || ''   // 第二段：可能为空、子命令前缀、NPC类型前缀
      var seg2 = parts[2] || ''   // 第三段：NPC类型（当 seg1 是子命令时）

      var npcSubs = ['add', 'list']
      var npcKeys = Object.keys(NPC_TYPES)
      var prefix = input.slice(0, input.lastIndexOf(' ') + 1) // 保留原始大小写的已输入前缀

      // 情况1：只有 "npc" → 补子命令或NPC类型
      if (parts.length === 2) {
        // seg1 是子命令或NPC类型的前缀
        var subMatch = npcSubs.filter(function (s) { return s.indexOf(seg1) === 0 })
        var typeMatch = npcKeys.filter(function (t) { return t.indexOf(seg1) === 0 })
        var all = subMatch.concat(typeMatch)
        if (all.length === 1) { this.inputBuffer = 'npc ' + all[0] + ' ' }
        else if (all.length > 1) { this.addEntry('info', input, all.join('  ')) }
        return
      }

      // 情况2：三段或以上 → seg1 是子命令，seg2 是NPC类型前缀
      if (parts.length >= 3) {
        var seg1Lower = seg1.toLowerCase()
        // 如果 seg1 是 add/list，补 seg2
        if (seg1Lower === 'add') {
          var addMatch = npcKeys.filter(function (t) { return t.indexOf(seg2) === 0 })
          if (addMatch.length === 1) { this.inputBuffer = 'npc add ' + addMatch[0] + ' ' }
          else if (addMatch.length > 1) { this.addEntry('info', input, addMatch.join('  ')) }
          return
        }
        // seg1 是 list → 无需补全
        if (seg1Lower === 'list') return
        // seg1 是 NPC 类型前缀，seg2 是数量 → 无需补全
        return
      }
    },

    // 补全 music 子命令
    autocompleteMusic: function (input) {
      var parts = input.split(/\s+/)
      if (parts.length === 2) {
        var partial = parts[1].toLowerCase()
        var subs = ['list', 'play', 'pause', 'stop']
        var matches = subs.filter(function (s) { return s.indexOf(partial) === 0 })
        if (matches.length === 1) { this.inputBuffer = 'music ' + matches[0] + ' ' }
        else if (matches.length > 1) { this.addEntry('info', input, matches.join('  ')) }
      }
    },

    // 通用：「cmd <玩家名片段>」补全
    autocompletePlayerArg: function (input, cmd, playerList) {
      var prefix = cmd + ' ' // 保持用户原始大小写
      var actualPrefix = input.slice(0, cmd.length + 1) // "kick " or "transfer "
      var partial = input.slice(cmd.length + 1) // 玩家名片段
      var matches = playerList.filter(function (n) { return n.toLowerCase().indexOf(partial.toLowerCase()) === 0 })
      if (matches.length === 1) {
        this.inputBuffer = actualPrefix + matches[0] + ' '
      } else if (matches.length > 1) {
        this.addEntry('info', input, matches.join('  '))
      }
    },
    navHistory: function (dir) {
      if (!this.cmdHistory.length) return
      var next = this.historyIdx + dir
      if (next < -1) next = -1
      if (next >= this.cmdHistory.length) next = this.cmdHistory.length - 1
      this.historyIdx = next
      this.inputBuffer = next === -1 ? '' : this.cmdHistory[next]
      var self = this; this.$nextTick(function () { self.focusInput() })
    },

    // ====== 命令执行 ======
    executeLine: function () {
      var raw = this.inputBuffer.trim()
      if (!raw) return
      this.cmdHistory.push(raw); this.historyIdx = -1

      var parts = raw.split(/\s+/)
      var cmd = parts[0].toLowerCase()
      var args = parts.slice(1)

      var def = CMDS[cmd]
      if (!def) { this.addEntry('err', raw, '[ERR] 未知命令: ' + cmd + ' — help 查看指令'); this.inputBuffer = ''; return }
      if (def.owner && !this.vm.isOwner) { this.addEntry('err', raw, '[ERR] ' + cmd + ' 仅房主可用'); this.inputBuffer = ''; return }

      var act = def.act
      if (act === 'help') { this.printHelp(raw) }
      else if (act === 'clear') { this.history = [] }
      else if (act === 'exit') { this.close() }
      else if (act === 'npcAdd') { this.execNpcAdd(raw, args) }
      else if (act === 'kickPlayers') { this.execKick(raw, args) }
      else if (act === 'transferOwner') { this.execTransfer(raw, args) }
      else if (act === 'toggleReveal') { this.addEntry('ok', raw, undefined); this.execToggleReveal() }
      else if (act === 'musicCmd') { this.execMusic(raw, args) }
      else if (act === 'fetchHands') { this.addEntry('ok', raw, undefined); this.execFetchHands() }
      else if (def.needs === 'amt') { this.execWithAmount(def, raw, args) }
      else { this.addEntry('ok', raw, undefined); this.invokeAction(act) }
      this.inputBuffer = ''
    },

    // ---- 通用动作 ----
    invokeAction: function (actionName, amount) {
      var vm = this.vm
      if (!vm) { this.appendOut('[ERR] 无游戏实例'); return }
      try {
        switch (actionName) {
          case 'doFold':
            if (!vm.isMyTurn) { this.appendOut('[ERR] 非你回合'); return }
            vm.doFold(); this.appendOut('[OK] 盖牌')
            break
          case 'doCall':
            if (!vm.isMyTurn) { this.appendOut('[ERR] 非你回合'); return }
            vm.doCall(); this.appendOut('[OK] 跟注 ' + (vm.callAmount || 0))
            break
          case 'doRaise':
            if (!vm.isMyTurn) { this.appendOut('[ERR] 非你回合'); return }
            if (amount > vm.myChips) { this.appendOut('[ERR] 余额不足: ' + vm.myChips); return }
            vm.$store.commit('dpGame/SET_RAISE_AMOUNT', amount)
            vm.doRaise(); this.appendOut('[OK] 加注 ' + amount)
            break
          case 'doAllIn':
            if (!vm.isMyTurn) { this.appendOut('[ERR] 非你回合'); return }
            vm.doAllIn(); this.appendOut('[OK] 全压 ' + vm.myChips)
            break
          case 'openHandCards':
            vm.$store.commit('dpGame/SET_HERO_HAND_HOLOGRAM', true)
            this.appendOut('[OK] 全息投影开启')
            break
          case 'listPlayers': this.printPlayerList(); break
          case 'showPot': this.appendOut('底池: ' + (vm.pot || 0) + ' | 阶段: ' + (vm.stageCN || 'N/A')); break
          case 'showStats':
            this.appendOut('阶段:' + (vm.stageCN || vm.stage) + ' | 底池:' + (vm.pot || 0) + ' | 筹码:' + (vm.myChips || 0) + ' | 房间:' + (vm.roomId || 'N/A'))
            break
          case 'doSit':
            this.execSit()
            break
          case 'doLeave':
            if (typeof vm.doLeaveSeat === 'function') { vm.doLeaveSeat() }
            else { this.appendOut('[ERR] 无法离座') }
            break
          default: this.appendOut('[ERR] 未实现: ' + actionName)
        }
      } catch (e) { this.appendOut('[ERR] ' + (e.message || '失败')) }
    },

    execWithAmount: function (def, raw, args) {
      var amt = parseInt(args[0], 10)
      if (isNaN(amt) || amt <= 0) { this.addEntry('err', raw, '[ERR] 用法: ' + def.u); return }
      this.addEntry('ok', raw, undefined)
      this.invokeAction(def.act, amt)
    },

    // ---- NPC ----
    execNpcAdd: function (raw, args) {
      var sub = (args[0] || '').toLowerCase()
      // 子命令: add / list — npc add fish 3  vs  npc list
      if (sub === 'add') {
        args = args.slice(1); sub = (args[0] || '').toLowerCase()
      }
      if (sub === 'list') {
        var lines = []
        Object.keys(NPC_TYPES).forEach(function (k) {
          var t = NPC_TYPES[k]
          lines.push('  ' + k + ' — ' + t.desc + (t.archetype ? ' [' + t.archetype + ']' : ''))
        })
        this.addEntry('info', raw, lines.join('\n'))
        return
      }
      var nt = NPC_TYPES[sub]
      if (!nt) { this.addEntry('err', raw, '[ERR] 未知NPC类型: ' + sub + ' — npc list 查看'); return }
      var count = parseInt(args[1], 10) || 1
      if (count < 1) count = 1; if (count > 9) count = 9

      this.addEntry('ok', raw, undefined)
      var vm = this.vm
      if (sub === 'custom') {
        vm.$store.commit('dpGame/SET_MODAL', { customNpcPendingCount: count, showCustomNpcStyleDialog: true })
        this.appendOut('[OK] 正在打开自定义NPC对话框 (x' + count + ')')
      } else if (nt.archetype) {
        vm.confirmAddOwnerNpcs({ type: 'rule', archetype: nt.archetype, count: count })
        this.appendOut('[OK] 已请求添加 ' + count + ' 个 ' + nt.archetype)
      } else if (sub === 'llm') {
        vm.confirmAddOwnerNpcs({ type: 'llm', count: count })
        this.appendOut('[OK] 已请求添加 ' + count + ' 个 LLM NPC')
      } else if (sub === 'llmglobal') {
        vm.confirmAddOwnerNpcs({ type: 'llmGlobal', count: count })
        this.appendOut('[OK] 已请求添加 ' + count + ' 个 LLM Global NPC')
      }
    },

    // ---- 踢人 ----
    execKick: function (raw, args) {
      if (!args.length) { this.addEntry('err', raw, '[ERR] 用法: kick <昵称> [昵称...]'); return }
      this.addEntry('ok', raw, undefined)
      var vm = this.vm
      // 收集有效昵称
      var allNicks = this.getAllPlayerNicks()
      var valid = args.filter(function (a) { return allNicks.indexOf(a) !== -1 })
      var invalid = args.filter(function (a) { return allNicks.indexOf(a) === -1 })
      if (invalid.length) this.appendOut('[WARN] 未找到: ' + invalid.join(', '))
      if (!valid.length) { this.appendOut('[ERR] 无有效玩家'); return }
      vm.doKickPlayers(valid)
      this.appendOut('[OK] 已请求踢出: ' + valid.join(', '))
    },

    // ---- 移交房主 ----
    execTransfer: function (raw, args) {
      var nick = args[0]
      if (!nick) { this.addEntry('err', raw, '[ERR] 用法: transfer <昵称>'); return }
      var vm = this.vm
      var myNick = (vm.user && vm.user.nickname) || ''
      if (nick === myNick) { this.addEntry('err', raw, '[ERR] 不能移交给自己'); return }
      var validNicks = this.getTransferablePlayerNicks()
      if (validNicks.indexOf(nick) === -1) { this.addEntry('err', raw, '[ERR] 未找到玩家: ' + nick); return }
      this.addEntry('ok', raw, undefined)
      vm.$store.commit('dpGame/SET_OWNER_TOOL', { ownerActionTarget: nick })
      vm.doTransferOwner()
      this.appendOut('[OK] 已请求移交给: ' + nick)
    },

    // ---- 看牌切换 ----
    execToggleReveal: function () {
      var vm = this.vm
      var next = !vm.ownerRevealAll
      vm.$store.commit('dpGame/SET_OWNER_REVEAL_ALL', next)
      this.appendOut('[OK] 看穿底牌已' + (next ? '开启' : '关闭'))
    },

    // ---- 音乐盒：打开音乐播放面板 ----
    execMusic: function (raw, args) {
      var sub = (args[0] || '').toLowerCase()
      var vm = this.vm
      // music play/pause/stop 快捷指令（不打开面板）
      if (sub === 'play') {
        var tid = parseInt(args[1], 10)
        if (isNaN(tid)) { this.addEntry('err', raw, '[ERR] 用法: music play <id>'); return }
        var track = (vm.musicTracks || []).find(function (t) { return t.id === tid })
        if (!track) { this.addEntry('err', raw, '[ERR] 未找到曲目 id=' + tid); return }
        this.addEntry('ok', raw, undefined)
        vm.sendRoomMusicSync({ action: 'play', trackId: track.id, webPath: track.webPath, displayName: track.displayName || '' })
        this.appendOut('[OK] 正在播放: ' + (track.displayName || '#' + track.id))
        return
      }
      if (sub === 'pause') {
        var ms = vm.roomMusicState
        if (!ms || !ms.webPath) { this.addEntry('err', raw, '[ERR] 无正在播放的曲目'); return }
        this.addEntry('ok', raw, undefined)
        vm.sendRoomMusicSync({ action: 'pause', trackId: ms.trackId, webPath: ms.webPath, displayName: ms.displayName || '' })
        this.appendOut('[OK] 已暂停'); return
      }
      if (sub === 'stop') {
        var ms2 = vm.roomMusicState
        if (!ms2 || !ms2.webPath) { this.addEntry('err', raw, '[ERR] 无正在播放的曲目'); return }
        this.addEntry('ok', raw, undefined)
        vm.sendRoomMusicSync({ action: 'stop', trackId: ms2.trackId, webPath: ms2.webPath, displayName: ms2.displayName || '' })
        this.appendOut('[OK] 已停止'); return
      }
      // 无参或未知子命令 → 打开面板
      this.addEntry('ok', raw, '[OK] 打开音乐播放器')
      vm.showMusicPlayer = true
    },

    // ---- 对局历史：打开历史查看面板 ----
    execFetchHands: function () {
      var vm = this.vm
      if (!vm || !vm.user) { this.appendOut('[ERR] 无用户数据'); return }
      this.addEntry('ok', 'hands', '[OK] 打开对局历史')
      vm.showHandHistoryPanel = true
    },

    // ---- 外部调用：打开终端并自动执行命令 ----
    autoRun: function (cmd) {
      var self = this
      if (!this.open) {
        this.openTerminal()
        // 等 CRT 动画完再执行
        var check = setInterval(function () {
          if (self.phase === 'ready' || self.phase === 'reveal-flash' || !self.showCrtLayers) {
            clearInterval(check)
            self.inputBuffer = cmd
            self.executeLine()
          }
        }, 80)
      } else {
        this.inputBuffer = cmd
        this.executeLine()
      }
    },

    // ---- 入座/准备：根据状态选正确 API ----
    execSit: function () {
      var vm = this.vm
      if (!vm) { this.appendOut('[ERR] 无游戏实例'); return }
      // 观众/已离座 → readyNextHand（报名下一局）
      if (vm.showSpectatorPrepareBlock) {
        if (typeof vm.readyNextHand === 'function') {
          vm.readyNextHand()
          this.appendOut('[OK] 已报名下一局加入')
        } else {
          this.appendOut('[ERR] readyNextHand 不可用')
        }
        return
      }
      // 已入座且结算阶段 → toggleReady（准备/取消准备）
      if (vm.stage === 'settled') {
        if (typeof vm.toggleReady === 'function') {
          vm.toggleReady()
          this.appendOut('[OK] 已' + (vm.myReady ? '取消准备' : '发送准备'))
        } else {
          this.appendOut('[ERR] toggleReady 不可用')
        }
        return
      }
      // 其他情况：尝试 readyNextHand
      if (typeof vm.readyNextHand === 'function') {
        vm.readyNextHand()
        this.appendOut('[OK] 已发送加入请求')
      } else {
        this.appendOut('[ERR] 无法操作')
      }
    },

    // ---- 玩家名查找辅助 ----
    getAllPlayerNicks: function () {
      var vm = this.vm; if (!vm) return []
      var order = vm.playersDisplayOrder || []
      return order.map(function (item) { return (item.player && item.player.nickname) || '' }).filter(Boolean)
    },
    getTransferablePlayerNicks: function () {
      var vm = this.vm; if (!vm) return []
      var order = vm.playersDisplayOrder || []
      var myNick = (vm.user && vm.user.nickname) || ''
      return order.map(function (item) { return (item.player && item.player.nickname) || '' })
        .filter(function (n) { return n && n !== myNick && n.indexOf('BOT_') !== 0 }) // 排除自己和Bot
    },
    getKickablePlayers: function () {
      var vm = this.vm; if (!vm) return []
      var order = vm.playersDisplayOrder || []
      var myNick = (vm.user && vm.user.nickname) || ''
      return order.map(function (item) { return (item.player && item.player.nickname) || '' })
        .filter(function (n) { return n && n !== myNick })
    },

    // ====== 输出 ======
    addEntry: function (type, cmd, out) {
      this.history.push({ type: type, cmd: cmd, out: out })
      var self = this; this.$nextTick(function () { var el = self.$refs.history; if (el) el.scrollTop = el.scrollHeight })
    },
    appendOut: function (text) {
      if (!this.history.length) return
      var last = this.history[this.history.length - 1]
      last.out = last.out ? (last.out + '\n' + text) : text
      var self = this; this.$nextTick(function () { var el = self.$refs.history; if (el) el.scrollTop = el.scrollHeight })
    },
    printHelp: function (raw) {
      var lines = []; var self = this
      lines.push('  —— 玩家指令 ——')
      var playerCmds = ['raise','fold','call','check','allin','cards','players','pot','stats','music','hands','sit','leave']
      playerCmds.forEach(function (n) { var d = CMDS[n]; lines.push('  ' + d.u + '  — ' + d.d) })
      if (this.vm && this.vm.isOwner) {
        lines.push('  —— 房主指令 ——')
        var ownerCmds = ['npc','kick','transfer','reveal']
        ownerCmds.forEach(function (n) { var d = CMDS[n]; lines.push('  ' + d.u + '  — ' + d.d) })
      }
      lines.push('  —— 通用 ——')
      var utilCmds = ['clear','exit','help']
      utilCmds.forEach(function (n) { var d = CMDS[n]; lines.push('  ' + d.u + '  — ' + d.d) })
      this.addEntry('info', raw, lines.join('\n'))
    },
    printPlayerList: function () {
      var vm = this.vm; var order = vm.playersDisplayOrder || []
      if (!order.length) { this.appendOut('[INFO] 暂无玩家'); return }
      var myNick = (vm.user && vm.user.nickname) || ''
      var lines = order.map(function (item, i) {
        var pl = item.player
        var nick = (pl && pl.nickname) || '???'
        var chips = (pl && pl.chips != null) ? pl.chips : '?'
        var marker = ''
        if (vm.dealerDisplayIndex === i) marker += ' [D]'
        if (myNick && pl && pl.nickname === myNick) marker += ' (你)'
        return '  ' + nick + '  STK:' + chips + marker
      })
      this.appendOut(lines.join('\n'))
    }
  }
}
</script>

<style scoped>
/* ====== 遮罩 ====== */
.dp-cli { position: fixed; inset: 0; z-index: 10070; background: transparent; pointer-events: none; }
.dp-cli-root-enter-active { transition: opacity 0.12s ease; }
.dp-cli-root-leave-active { transition: opacity 0.18s ease; }
.dp-cli-root-enter, .dp-cli-root-leave-to { opacity: 0; }

/* ====== 右侧面板壳 ====== */
.dp-cli__shell {
  position: absolute; right: max(12px, env(safe-area-inset-right)); top: max(12px, env(safe-area-inset-top));
  width: min(420px, calc(100vw - 24px)); height: min(540px, calc(100vh - 40px)); min-height: 240px;
  background: rgba(8, 10, 12, 0.96); border: 2px solid rgba(74, 246, 38, 0.34); border-radius: 4px;
  display: flex; flex-direction: column; pointer-events: auto;
  font-family: 'Courier New', ui-monospace, 'PingFang SC', monospace;
  box-shadow: 0 0 0 1px #000, -4px 8px 28px rgba(0,0,0,0.6); overflow: hidden;
}
.dp-cli__shell--slide-in { animation: dp-cli-slide-in 0.28s cubic-bezier(0.22,0.61,0.36,1) forwards; }
.dp-cli__shell--retract { animation: dp-cli-retract 0.22s ease-in forwards; }
.dp-cli__shell--instant { transform: translateX(0); opacity: 1; }
@keyframes dp-cli-slide-in { from{transform:translateX(105%);opacity:0} to{transform:translateX(0);opacity:1} }
@keyframes dp-cli-retract { from{transform:translateX(0);opacity:1} to{transform:translateX(105%);opacity:0} }

/* CRT 图层 */
.dp-cli__scanlines { position:absolute;inset:0;z-index:2;pointer-events:none;background:repeating-linear-gradient(to bottom,transparent 0 2px,rgba(0,0,0,0.12) 2px 3px);background-size:100% 3px;opacity:0.16; }
.dp-cli__snow { position:absolute;inset:-4%;z-index:4;opacity:0;pointer-events:none;overflow:hidden;transition:opacity 0.1s;background:#0e1012; }
.dp-cli__snow--active { opacity:0.94;animation:dp-cli-jitter 0.06s steps(2) infinite; }
@keyframes dp-cli-jitter { 0%,100%{transform:translate(0,0)} 50%{transform:translate(-1px,0.5px)} }
.dp-cli__snow-noise,.dp-cli__snow-bars { position:absolute;inset:0;pointer-events:none; }
.dp-cli__snow-noise { opacity:0.85;background-image:repeating-radial-gradient(circle at 12% 18%,rgba(255,255,255,0.6) 0 0.4px,transparent 0.5px),repeating-radial-gradient(circle at 78% 62%,rgba(210,218,228,0.5) 0 0.35px,transparent 0.45px);background-size:3px 3px,4px 4px;animation:dp-cli-noise-flicker 0.04s steps(3) infinite; }
@keyframes dp-cli-noise-flicker { 0%{background-position:0 0,1px 0;opacity:0.8} 50%{background-position:2px 1px,-1px 2px;opacity:0.95} 100%{background-position:-1px 2px,2px -1px;opacity:0.72} }
.dp-cli__snow-bars { opacity:0.65;mix-blend-mode:screen;background:repeating-linear-gradient(to bottom,rgba(0,0,0,0.12) 0 1px,rgba(235,240,248,0.14) 1px 2px,transparent 2px 6px);background-size:100% 6px;animation:dp-cli-bars-roll 0.25s linear infinite; }
@keyframes dp-cli-bars-roll { to{background-position:0 -6px} }
.dp-cli__flash { position:absolute;inset:0;z-index:5;pointer-events:none;opacity:0; }
.dp-cli__flash--pulse { animation:dp-cli-flash 0.1s ease-out; }
@keyframes dp-cli-flash { 0%{opacity:1;background:rgba(248,250,252,0.9)} 100%{opacity:0;background:transparent} }

/* 头部 */
.dp-cli__head { display:flex;align-items:center;justify-content:space-between;gap:12px;padding:8px 12px;flex-shrink:0;border-bottom:1px solid rgba(74,246,38,0.22);background:color-mix(in srgb, var(--dp-panel-bg,#12151a) 88%, var(--dp-accent,#4af626) 12%);position:relative;z-index:3; }
.dp-cli__head-title { font-family:'Press Start 2P',monospace;font-size:10px;color:#4af626;text-shadow:0 0 6px rgba(74,246,38,0.55);letter-spacing:0.04em; }
.dp-cli__close { flex-shrink:0;width:30px;height:30px;padding:0;border:1px solid rgba(74,246,38,0.28);border-radius:0;background:#0a0c0e;color:#4af626;font-family:'Courier New',monospace;font-size:13px;cursor:pointer; }
.dp-cli__close:hover { background:#4af626;color:#080a0c; }

/* 命令历史 */
.dp-cli__history { flex:1 1 auto;min-height:0;overflow-y:auto;padding:10px 14px 4px;font-size:12px;line-height:1.5;position:relative;z-index:1; }
.dp-cli__entry { margin-bottom:4px;white-space:pre-wrap;word-break:break-all; }
.dp-cli__prompt { color:#4af626;text-shadow:0 0 6px rgba(74,246,38,0.5);margin-right:5px;user-select:none; }
.dp-cli__cmd { color:#e0f0d8; }
.dp-cli__out { color:#72f052;margin-left:16px;margin-top:1px;font-size:11px;text-shadow:0 0 3px rgba(114,240,82,0.3); }
.dp-cli__entry--err .dp-cli__out,.dp-cli__entry--err .dp-cli__cmd { color:#ff6666; }
.dp-cli__entry--ok .dp-cli__out { color:#72f052; }
.dp-cli__entry--info .dp-cli__out { color:#ffe066;text-shadow:0 0 3px rgba(255,224,102,0.3); }

/* 输入行 */
.dp-cli__input-line { flex-shrink:0;padding:6px 14px 10px;font-size:13px;display:flex;align-items:center;border-top:1px solid rgba(74,246,38,0.14);position:relative;z-index:1; }
.dp-cli__preinput { color:#e0f0d8;white-space:pre; }
.dp-cli__cursor { color:#4af626;text-shadow:0 0 10px rgba(74,246,38,0.8);animation:dp-cli-blink 0.8s step-end infinite;margin-left:1px; }
@keyframes dp-cli-blink { 0%,100%{opacity:1} 50%{opacity:0} }
.dp-cli__hidden-input { position:absolute;left:0;top:0;width:100%;height:100%;opacity:0;cursor:text;caret-color:transparent; }

/* 提示栏 */
.dp-cli__hint-bar { flex-shrink:0;display:flex;gap:12px;padding:4px 14px 8px;font-size:9px;color:rgba(74,246,38,0.32);border-top:1px solid rgba(74,246,38,0.06);position:relative;z-index:1;user-select:none; }

@media (prefers-reduced-motion:reduce) {
  .dp-cli__shell--slide-in,.dp-cli__shell--retract,.dp-cli__snow--active,.dp-cli__snow-noise,.dp-cli__snow-bars,.dp-cli__flash--pulse,.dp-cli__cursor { animation:none!important; }
  .dp-cli__snow--active { opacity:0.45; }
  .dp-cli__cursor { opacity:1; }
  .dp-cli__shell--slide-in,.dp-cli__shell--retract { transform:translateX(0);opacity:1; }
}
</style>
