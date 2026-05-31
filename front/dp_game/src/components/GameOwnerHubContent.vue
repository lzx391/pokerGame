<template>
  <div
      class="dp-owner-hub-content"
      :class="{ 'dp-owner-hub-content--inactive': !active }"
  >
    <!-- root menu -->
    <div
        v-if="currentScreen === 'root'"
        class="dp-owner-hub-content__screen"
        role="menu"
        aria-label="房主终端主菜单"
    >
      <button
          v-for="(item, idx) in rootItems"
          :key="item.id"
          type="button"
          class="dp-owner-terminal__row"
          :class="{ 'dp-owner-terminal__row--active': cursorIndex === idx }"
          role="menuitem"
          :aria-selected="cursorIndex === idx ? 'true' : 'false'"
          @click="onRootClick(idx)"
      >
        <span class="dp-owner-terminal__row-cursor" aria-hidden="true">{{ cursorIndex === idx ? '›' : ' ' }}</span>
        <span class="dp-owner-terminal__row-label">{{ item.label }}</span>
      </button>
    </div>

    <!-- NPC pick -->
    <div
        v-else-if="currentScreen === 'npc-pick'"
        class="dp-owner-hub-content__screen"
        role="listbox"
        aria-label="选择 NPC 类型"
    >
      <p class="dp-owner-hub-content__hint">↑↓ 选择 · ←→ 数量 · Enter 确认</p>
      <button
          v-for="(row, idx) in allNpcRows"
          :key="row.id"
          type="button"
          class="dp-owner-terminal__row dp-owner-terminal__row--npc"
          :class="{ 'dp-owner-terminal__row--active': cursorIndex === idx }"
          role="option"
          :aria-selected="cursorIndex === idx ? 'true' : 'false'"
          @click="onNpcRowClick(idx)"
      >
        <span class="dp-owner-terminal__row-cursor" aria-hidden="true">{{ cursorIndex === idx ? '›' : ' ' }}</span>
        <span class="dp-owner-terminal__row-label" :style="{ color: row.labelColor }">{{ row.label }}</span>
        <span v-if="cursorIndex === idx" class="dp-owner-hub-content__count">× {{ npcCounts[row.id] }}</span>
        <span v-if="row.adding" class="dp-owner-hub-content__status">提交中…</span>
      </button>
    </div>

    <!-- NPC confirm -->
    <div
        v-else-if="currentScreen === 'npc-confirm'"
        class="dp-owner-hub-content__screen dp-owner-hub-content__screen--confirm"
    >
      <p class="dp-owner-hub-content__confirm-title">确认添加</p>
      <p class="dp-owner-hub-content__confirm-body">
        {{ npcConfirmLabel }}
      </p>
      <p class="dp-owner-hub-content__hint">Enter 确认 · Esc 返回</p>
    </div>

    <!-- transfer pick -->
    <div
        v-else-if="currentScreen === 'transfer-pick'"
        class="dp-owner-hub-content__screen"
        role="listbox"
        aria-label="选择移交对象"
    >
      <p class="dp-owner-hub-content__hint">选择新房主 · Enter 确认</p>
      <p v-if="transferPlayers.length === 0" class="dp-owner-hub-content__empty">当前没有可移交的玩家。</p>
      <button
          v-for="(p, idx) in transferPlayers"
          :key="'transfer-' + p.nickname"
          type="button"
          class="dp-owner-terminal__row"
          :class="{ 'dp-owner-terminal__row--active': cursorIndex === idx }"
          role="option"
          :aria-selected="cursorIndex === idx ? 'true' : 'false'"
          @click="onTransferRowClick(idx)"
      >
        <span class="dp-owner-terminal__row-cursor" aria-hidden="true">{{ cursorIndex === idx ? '›' : ' ' }}</span>
        <span class="dp-owner-terminal__row-label">{{ displayNickname(p.nickname) }}</span>
      </button>
    </div>

    <!-- transfer confirm -->
    <div
        v-else-if="currentScreen === 'transfer-confirm'"
        class="dp-owner-hub-content__screen dp-owner-hub-content__screen--confirm"
    >
      <p class="dp-owner-hub-content__confirm-title">移交房主</p>
      <p class="dp-owner-hub-content__confirm-body">
        移交给 <strong>{{ displayNickname(pendingTransferNick) }}</strong> ？
      </p>
      <p class="dp-owner-hub-content__hint">Enter 确认 · Esc 返回</p>
    </div>

    <!-- kick pick -->
    <div
        v-else-if="currentScreen === 'kick-pick'"
        class="dp-owner-hub-content__screen"
        role="listbox"
        aria-label="选择踢出玩家"
    >
      <p class="dp-owner-hub-content__hint">Space 勾选 · Enter 下一步</p>
      <p v-if="kickPlayers.length === 0" class="dp-owner-hub-content__empty">当前没有可踢出的玩家。</p>
      <button
          v-for="(p, idx) in kickPlayers"
          :key="'kick-' + p.nickname"
          type="button"
          class="dp-owner-terminal__row"
          :class="{
            'dp-owner-terminal__row--active': cursorIndex === idx,
            'dp-owner-terminal__row--checked': isKickSelected(p.nickname)
          }"
          role="option"
          :aria-selected="cursorIndex === idx ? 'true' : 'false'"
          @click="onKickRowClick(idx)"
      >
        <span class="dp-owner-terminal__row-cursor" aria-hidden="true">{{ cursorIndex === idx ? '›' : ' ' }}</span>
        <span class="dp-owner-hub-content__check" aria-hidden="true">{{ isKickSelected(p.nickname) ? '[×]' : '[ ]' }}</span>
        <span class="dp-owner-terminal__row-label">{{ displayNickname(p.nickname) }}</span>
      </button>
      <p class="dp-owner-hub-content__kick-summary">已选 {{ kickSelectionNicknames.length }} 人</p>
    </div>

    <!-- kick confirm -->
    <div
        v-else-if="currentScreen === 'kick-confirm'"
        class="dp-owner-hub-content__screen dp-owner-hub-content__screen--confirm"
    >
      <p class="dp-owner-hub-content__confirm-title dp-owner-hub-content__confirm-title--danger">批量踢出</p>
      <p class="dp-owner-hub-content__confirm-body">
        踢出 {{ kickSelectionNicknames.length }} 人至观众席？
      </p>
      <p class="dp-owner-hub-content__confirm-detail">{{ kickConfirmPreview }}</p>
      <p class="dp-owner-hub-content__hint">Enter 确认 · Esc 返回</p>
    </div>

    <!-- reveal confirm -->
    <div
        v-else-if="currentScreen === 'reveal-confirm'"
        class="dp-owner-hub-content__screen dp-owner-hub-content__screen--confirm"
    >
      <p class="dp-owner-hub-content__confirm-title">看穿底牌</p>
      <p class="dp-owner-hub-content__confirm-body">
        当前：<strong>{{ ownerRevealAll ? '开启' : '关闭' }}</strong>
      </p>
      <p class="dp-owner-hub-content__hint">Enter 切换 · Esc 返回</p>
    </div>
  </div>
</template>

<script>
import { mapGetters } from 'vuex'
import { dpDisplayNickname } from '../utils/dpDisplayNickname'
import { dpOwnerTerminalDevLog } from '../utils/dpOwnerTerminalDevLog'

export default {
  name: 'GameOwnerHubContent',
  props: {
    active: { type: Boolean, default: false },
    terminalFocused: { type: Boolean, default: false },
    ownerRevealAll: { type: Boolean, default: false },
    demoBotAdding: { type: Boolean, default: false },
    demoBotAddedTip: { type: String, default: '' },
    maniacBotAdding: { type: Boolean, default: false },
    maniacBotAddedTip: { type: String, default: '' },
    tagBotAdding: { type: Boolean, default: false },
    tagBotAddedTip: { type: String, default: '' },
    lagBotAdding: { type: Boolean, default: false },
    lagBotAddedTip: { type: String, default: '' },
    nitBotAdding: { type: Boolean, default: false },
    nitBotAddedTip: { type: String, default: '' },
    callBotAdding: { type: Boolean, default: false },
    callBotAddedTip: { type: String, default: '' },
    llmBotAdding: { type: Boolean, default: false },
    llmBotAddedTip: { type: String, default: '' },
    llmGlobalBotAdding: { type: Boolean, default: false },
    llmGlobalBotAddedTip: { type: String, default: '' },
    customBotAdding: { type: Boolean, default: false },
    customBotAddedTip: { type: String, default: '' }
  },
  data() {
    return {
      menuStack: ['root'],
      cursorIndex: 0,
      npcCounts: {
        fish: 1,
        tag: 1,
        lag: 1,
        nit: 1,
        call: 1,
        maniac: 1,
        custom: 1,
        llm: 1,
        llmGlobal: 1
      },
      kickSelectionNicknames: [],
      pendingTransferNick: '',
      pendingNpcRow: null,
      submitting: false
    }
  },
  computed: {
    ...mapGetters('dpGame', ['ownerActionPlayers']),
    currentScreen() {
      return this.menuStack[this.menuStack.length - 1] || 'root'
    },
    stackDepth() {
      return this.menuStack.length
    },
    rootItems() {
      return [
        { id: 'add-npc', label: '添加NPC' },
        { id: 'transfer', label: '移交房主' },
        { id: 'kick', label: '踢出玩家' },
        { id: 'reveal', label: '看穿底牌' }
      ]
    },
    ruleNpcRows() {
      return [
        { id: 'fish', archetype: 'FISH', label: '新手猫 BOT_FISH', labelColor: '#d46b08', adding: this.demoBotAdding, tip: this.demoBotAddedTip },
        { id: 'tag', archetype: 'TAG', label: '保守猫 BOT_TAG', labelColor: '#237804', adding: this.tagBotAdding, tip: this.tagBotAddedTip },
        { id: 'lag', archetype: 'LAG', label: '松凶猫 BOT_LAG', labelColor: '#c41d7f', adding: this.lagBotAdding, tip: this.lagBotAddedTip },
        { id: 'nit', archetype: 'NIT', label: '胆小猫 BOT_NIT', labelColor: '#434343', adding: this.nitBotAdding, tip: this.nitBotAddedTip },
        { id: 'call', archetype: 'CALL', label: '头铁猫 BOT_CALL', labelColor: '#10239e', adding: this.callBotAdding, tip: this.callBotAddedTip },
        { id: 'maniac', archetype: 'MANIAC', label: '激进猫 BOT_MANIAC', labelColor: '#cf1322', adding: this.maniacBotAdding, tip: this.maniacBotAddedTip }
      ]
    },
    llmNpcRows() {
      return [
        { id: 'custom', type: 'custom', label: 'BOT_CUSTOM', labelColor: '#531dab', adding: this.customBotAdding, tip: this.customBotAddedTip },
        { id: 'llm', type: 'llm', label: 'BOT_LLM', labelColor: '#08979c', adding: this.llmBotAdding, tip: this.llmBotAddedTip },
        { id: 'llmGlobal', type: 'llmGlobal', label: 'BOT_LLM_GLOBAL', labelColor: '#006d75', adding: this.llmGlobalBotAdding, tip: this.llmGlobalBotAddedTip }
      ]
    },
    allNpcRows() {
      return this.ruleNpcRows.concat(this.llmNpcRows)
    },
    transferPlayers() {
      if (this.$store.state.dpGame.ownerToolType !== 'transfer') {
        return []
      }
      return this.ownerActionPlayers
    },
    kickPlayers() {
      if (this.$store.state.dpGame.ownerToolType !== 'kick') {
        return []
      }
      return this.ownerActionPlayers
    },
    listLength() {
      if (this.currentScreen === 'root') return this.rootItems.length
      if (this.currentScreen === 'npc-pick') return this.allNpcRows.length
      if (this.currentScreen === 'transfer-pick') return this.transferPlayers.length
      if (this.currentScreen === 'kick-pick') return this.kickPlayers.length
      return 0
    },
    npcConfirmLabel() {
      var row = this.pendingNpcRow
      if (!row) return ''
      var count = this.clampCount(this.npcCounts[row.id], 9)
      if (row.type === 'custom' || row.id === 'custom') {
        return 'BOT_CUSTOM × ' + count
      }
      if (row.type === 'llm') return 'BOT_LLM × ' + count
      if (row.type === 'llmGlobal') return 'BOT_LLM_GLOBAL × ' + count
      return row.label + ' × ' + count
    },
    kickConfirmPreview() {
      var list = this.kickSelectionNicknames.slice(0, 6).map(this.displayNickname)
      if (this.kickSelectionNicknames.length > 6) list.push('…')
      return list.join('、')
    }
  },
  watch: {
    active(now) {
      if (!now) this.resetStack()
    },
    ownerActionPlayers: {
      deep: true,
      handler() {
        this.pruneKickSelection()
      }
    },
    menuStack: {
      deep: true,
      handler(stack) {
        dpOwnerTerminalDevLog('menu stack', {
          stack: stack.slice(),
          stackDepth: stack.length,
          screen: stack[stack.length - 1]
        })
      }
    }
  },
  methods: {
    displayNickname: dpDisplayNickname,
    resetStack() {
      this.menuStack = ['root']
      this.cursorIndex = 0
      this.kickSelectionNicknames = []
      this.pendingTransferNick = ''
      this.pendingNpcRow = null
      this.submitting = false
    },
    pushScreen(screen) {
      this.menuStack.push(screen)
      this.cursorIndex = 0
      dpOwnerTerminalDevLog('menu stack push', { screen: screen, stackDepth: this.menuStack.length })
    },
    popScreen() {
      if (this.menuStack.length <= 1) return false
      this.menuStack.pop()
      this.cursorIndex = 0
      dpOwnerTerminalDevLog('menu stack pop', { stackDepth: this.menuStack.length, screen: this.currentScreen })
      return true
    },
    clampCount(raw, max) {
      var n = parseInt(raw, 10)
      if (isNaN(n)) n = 1
      return Math.max(1, Math.min(max, n))
    },
    bumpNpcCount(delta) {
      var row = this.allNpcRows[this.cursorIndex]
      if (!row) return
      this.npcCounts[row.id] = this.clampCount(this.clampCount(this.npcCounts[row.id], 9) + delta, 9)
    },
    isKickSelected(nick) {
      return this.kickSelectionNicknames.indexOf(nick) >= 0
    },
    toggleKickAtIndex(idx) {
      var p = this.kickPlayers[idx]
      if (!p) return
      var nick = p.nickname
      var i = this.kickSelectionNicknames.indexOf(nick)
      if (i >= 0) {
        this.kickSelectionNicknames.splice(i, 1)
      } else {
        this.kickSelectionNicknames.push(nick)
      }
    },
    pruneKickSelection() {
      var allowed = {}
      for (var i = 0; i < this.kickPlayers.length; i++) {
        allowed[this.kickPlayers[i].nickname] = true
      }
      this.kickSelectionNicknames = this.kickSelectionNicknames.filter(function (n) {
        return !!allowed[n]
      })
    },
    onRootClick(idx) {
      this.cursorIndex = idx
      this.activateRootItem()
    },
    onNpcRowClick(idx) {
      this.cursorIndex = idx
      this.enterNpcConfirm()
    },
    onTransferRowClick(idx) {
      this.cursorIndex = idx
      this.enterTransferConfirm()
    },
    onKickRowClick(idx) {
      this.cursorIndex = idx
      this.toggleKickAtIndex(idx)
    },
    activateRootItem() {
      var item = this.rootItems[this.cursorIndex]
      if (!item) return
      if (item.id === 'add-npc') {
        this.pushScreen('npc-pick')
        return
      }
      if (item.id === 'transfer') {
        this.$store.commit('dpGame/SET_OWNER_TOOL', { ownerToolType: 'transfer', ownerActionTarget: '' })
        this.pushScreen('transfer-pick')
        return
      }
      if (item.id === 'kick') {
        this.$store.commit('dpGame/SET_OWNER_TOOL', { ownerToolType: 'kick' })
        this.kickSelectionNicknames = []
        this.pushScreen('kick-pick')
        return
      }
      if (item.id === 'reveal') {
        this.pushScreen('reveal-confirm')
      }
    },
    enterNpcConfirm() {
      var row = this.allNpcRows[this.cursorIndex]
      if (!row || row.adding) return
      this.pendingNpcRow = row
      this.pushScreen('npc-confirm')
    },
    enterTransferConfirm() {
      var p = this.transferPlayers[this.cursorIndex]
      if (!p) return
      this.pendingTransferNick = p.nickname
      this.$store.commit('dpGame/SET_OWNER_TOOL', { ownerActionTarget: p.nickname })
      this.pushScreen('transfer-confirm')
    },
    emitNpcConfirm() {
      var row = this.pendingNpcRow
      if (!row || this.submitting) return
      var count = this.clampCount(this.npcCounts[row.id], 9)
      this.submitting = true
      if (row.type === 'custom' || row.id === 'custom') {
        dpOwnerTerminalDevLog('API emit', { action: 'confirm-add-npcs', type: 'custom', count: count })
        this.$emit('confirm-add-npcs', { type: 'custom', count: count })
      } else if (row.type === 'llm') {
        dpOwnerTerminalDevLog('API emit', { action: 'confirm-add-npcs', type: 'llm', count: count })
        this.$emit('confirm-add-npcs', { type: 'llm', count: count })
      } else if (row.type === 'llmGlobal') {
        dpOwnerTerminalDevLog('API emit', { action: 'confirm-add-npcs', type: 'llmGlobal', count: count })
        this.$emit('confirm-add-npcs', { type: 'llmGlobal', count: count })
      } else {
        dpOwnerTerminalDevLog('API emit', { action: 'confirm-add-npcs', type: 'rule', archetype: row.archetype, count: count })
        this.$emit('confirm-add-npcs', { type: 'rule', archetype: row.archetype, count: count })
      }
      this.submitting = false
      this.resetStack()
    },
    emitTransferConfirm() {
      if (this.submitting || !this.pendingTransferNick) return
      this.submitting = true
      dpOwnerTerminalDevLog('API emit', { action: 'transfer-owner', target: this.pendingTransferNick })
      this.$emit('transfer-owner')
      this.submitting = false
    },
    emitKickConfirm() {
      if (this.submitting || !this.kickSelectionNicknames.length) return
      this.submitting = true
      dpOwnerTerminalDevLog('API emit', { action: 'kick-players', count: this.kickSelectionNicknames.length })
      this.$emit('kick-players', this.kickSelectionNicknames.slice())
      this.submitting = false
    },
    emitRevealToggle() {
      if (this.submitting) return
      this.submitting = true
      dpOwnerTerminalDevLog('API emit', { action: 'toggle-reveal', next: !this.ownerRevealAll })
      this.$emit('toggle-reveal')
      this.submitting = false
      this.popScreen()
    },
    handleKeydown(event) {
      if (!this.active || !this.terminalFocused || this.submitting) return false
      var key = event.key
      var code = event.code
      var isUp = key === 'w' || key === 'W' || key === 'ArrowUp'
      var isDown = key === 's' || key === 'S' || key === 'ArrowDown'
      var isLeft = key === 'a' || key === 'A' || key === 'ArrowLeft'
      var isRight = key === 'd' || key === 'D' || key === 'ArrowRight'
      var isEnter = key === 'Enter'
      var isEsc = key === 'Escape'
      var isSpace = key === ' ' || code === 'Space'

      dpOwnerTerminalDevLog('keyboard', {
        key: key,
        focused: this.terminalFocused,
        cursorIndex: this.cursorIndex,
        screen: this.currentScreen
      })

      if (isEsc) {
        event.preventDefault()
        if (this.menuStack.length > 1) {
          this.popScreen()
          return true
        }
        this.$emit('request-close')
        return true
      }

      if (this.currentScreen === 'npc-confirm') {
        if (isEnter) {
          event.preventDefault()
          this.emitNpcConfirm()
          return true
        }
        return false
      }
      if (this.currentScreen === 'transfer-confirm') {
        if (isEnter) {
          event.preventDefault()
          this.emitTransferConfirm()
          return true
        }
        return false
      }
      if (this.currentScreen === 'kick-confirm') {
        if (isEnter) {
          event.preventDefault()
          this.emitKickConfirm()
          return true
        }
        return false
      }
      if (this.currentScreen === 'reveal-confirm') {
        if (isEnter) {
          event.preventDefault()
          this.emitRevealToggle()
          return true
        }
        return false
      }

      if (this.currentScreen === 'npc-pick') {
        if (isLeft) {
          event.preventDefault()
          this.bumpNpcCount(-1)
          return true
        }
        if (isRight) {
          event.preventDefault()
          this.bumpNpcCount(1)
          return true
        }
      }

      if (this.currentScreen === 'kick-pick' && isSpace) {
        event.preventDefault()
        this.toggleKickAtIndex(this.cursorIndex)
        return true
      }

      if (isUp && this.listLength > 0) {
        event.preventDefault()
        this.cursorIndex = (this.cursorIndex - 1 + this.listLength) % this.listLength
        return true
      }
      if (isDown && this.listLength > 0) {
        event.preventDefault()
        this.cursorIndex = (this.cursorIndex + 1) % this.listLength
        return true
      }

      if (isEnter) {
        event.preventDefault()
        if (this.currentScreen === 'root') {
          this.activateRootItem()
        } else if (this.currentScreen === 'npc-pick') {
          this.enterNpcConfirm()
        } else if (this.currentScreen === 'transfer-pick') {
          this.enterTransferConfirm()
        } else if (this.currentScreen === 'kick-pick') {
          if (this.kickSelectionNicknames.length > 0) {
            this.pushScreen('kick-confirm')
          }
        }
        return true
      }

      return false
    },
    handleRootEsc() {
      if (this.menuStack.length > 1) {
        this.popScreen()
        return true
      }
      return false
    }
  }
}
</script>
