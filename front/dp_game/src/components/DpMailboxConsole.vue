<template>
  <div
    v-if="visible"
    class="dp-mailbox-overlay"
    role="dialog"
    aria-modal="true"
    aria-labelledby="dp-mailbox-head-title"
    @click.self="requestClose"
  >
    <div
      ref="shell"
      class="dp-console-shell dp-mailbox-shell"
      tabindex="0"
      role="application"
      aria-label="Mailbox console"
      @keydown="onKeydown"
    >
      <div class="dp-console-shell__scanlines" aria-hidden="true" />
      <header class="dp-console-shell__head">
        <h2 id="dp-mailbox-head-title" class="dp-console-shell__head-title">— MAILBOX —</h2>
      </header>

      <section v-if="view === 'menu'" class="dp-mailbox-body">
        <ul class="dp-console-menu" role="menu" aria-label="Mailbox menu">
          <li
            v-for="(row, i) in menuRows"
            :key="row.id"
            role="menuitem"
            :aria-selected="i === selectedIndex"
            class="dp-console-menu__row"
            :class="menuRowClass(i, row)"
            @click="onMenuRowTap(i)"
          >
            <div class="dp-console-menu__left">
              <span class="dp-console-menu__cursor" aria-hidden="true">&gt;</span>
              <span class="dp-console-menu__label">{{ row.label }}</span>
            </div>
            <div class="dp-console-menu__right">
              <span class="dp-console-menu__value">{{ menuRowHint(row) }}</span>
            </div>
          </li>
        </ul>
      </section>

      <section v-else class="dp-mailbox-body dp-mailbox-body--list">
        <div class="dp-mailbox-list-head">
          <button type="button" class="dp-mailbox-list-head__back" @click="goMenu">BACK</button>
          <p class="dp-mailbox-list-head__title">{{ listTitle }}</p>
        </div>

        <p v-if="mailboxLoading" class="dp-mailbox-list__hint">LOADING...</p>
        <p v-else-if="listEmpty" class="dp-mailbox-list__hint">{{ listEmptyText }}</p>

        <ul v-else class="dp-mailbox-list" role="list">
          <li
            v-for="(row, i) in currentList"
            :key="listRowKey(row)"
            class="dp-mailbox-list__item"
            :class="{ 'dp-mailbox-list__item--selected': i === selectedIndex }"
            role="listitem"
            @click="selectListRow(i)"
          >
            <div class="dp-mailbox-list__text">
              <div class="dp-mailbox-list__primary">{{ listPrimaryName(row) }}</div>
              <div class="dp-mailbox-list__secondary">{{ listSecondaryText(row) }}</div>
              <button
                v-if="listUserId(row) != null"
                type="button"
                class="dp-mailbox-list__idline"
                title="点击复制 ID"
                @click.stop="copyId(listUserId(row))"
              >
                ID {{ listUserId(row) }}
              </button>
            </div>
            <div class="dp-mailbox-list__actions">
              <button
                type="button"
                class="dp-mailbox-list__act dp-mailbox-list__act--yes"
                :disabled="!!actionBusyId"
                :aria-busy="listActionBusy(row, true)"
                @click.stop="onPrimaryAction(row)"
              >
                {{ view === 'apply-list' ? 'YES' : 'JOIN' }}
              </button>
              <button
                type="button"
                class="dp-mailbox-list__act dp-mailbox-list__act--no"
                :disabled="!!actionBusyId"
                :aria-busy="listActionBusy(row, false)"
                @click.stop="onSecondaryAction(row)"
              >
                NO
              </button>
            </div>
          </li>
        </ul>
      </section>

      <p class="dp-console-footer" aria-hidden="true">
        <span class="dp-console-footer__kb">{{ footerHintKb }}</span>
        <span class="dp-console-footer__touch">{{ footerHintTouch }}</span>
      </p>
    </div>

    <p ref="liveRegion" class="dp-console-live" aria-live="polite" aria-atomic="true">
      {{ liveAnnounce }}
    </p>
  </div>
</template>

<script>
import '@/styles/dp-create-room-console.css'
import '@/styles/dp-mailbox-console.css'
import { mapState, mapActions } from 'vuex'
import { dpSocialDisplayNickname } from '@/utils/dpSocialDisplayName'
import { copySocialId as copySocialIdToClipboard } from '@/utils/dpCopySocialId'

var MENU_ROWS = [
  { id: 'apply', label: 'APPLY' },
  { id: 'invite', label: 'INVITE' },
  { id: 'close', label: 'CLOSE' }
]

export default {
  name: 'DpMailboxConsole',
  props: {
    visible: {
      type: Boolean,
      default: false
    }
  },
  data: function () {
    return {
      view: 'menu',
      selectedIndex: 0,
      tickSeconds: 0,
      tickTimer: null,
      liveAnnounce: '',
      prefersReducedMotion: false,
      menuRows: MENU_ROWS
    }
  },
  computed: {
    ...mapState('dpMailbox', [
      'friendRequests',
      'roomInvites',
      'mailboxLoading',
      'actionBusyId'
    ]),
    ...mapState('dpGame', ['ecoMode']),
    skipBlink: function () {
      if (this.ecoMode) return true
      if (this.prefersReducedMotion) return true
      if (typeof document !== 'undefined' && document.body.getAttribute('data-dp-fluidity') === 'eco') {
        return true
      }
      return false
    },
    useRowBlink: function () {
      return !this.skipBlink
    },
    currentList: function () {
      if (this.view === 'apply-list') return this.friendRequests || []
      if (this.view === 'invite-list') return this.roomInvites || []
      return []
    },
    listTitle: function () {
      if (this.view === 'apply-list') return 'APPLY'
      if (this.view === 'invite-list') return 'INVITE'
      return ''
    },
    listEmpty: function () {
      return !this.mailboxLoading && this.currentList.length === 0
    },
    listEmptyText: function () {
      if (this.view === 'apply-list') return 'NO PENDING APPLY'
      if (this.view === 'invite-list') return 'NO PENDING INVITE'
      return ''
    },
    footerHintKb: function () {
      if (this.view === 'menu') return '↑↓ move · ENTER select · ESC close'
      return '↑↓ move · Y/J join · N reject · ESC back'
    },
    footerHintTouch: function () {
      if (this.view === 'menu') return 'tap row · tap CLOSE'
      return 'tap row · YES/JOIN · NO'
    }
  },
  watch: {
    visible: function (v) {
      if (v) this.onOpen()
      else this.onClosed()
    }
  },
  mounted: function () {
    this.syncReducedMotion()
    if (typeof window !== 'undefined' && window.matchMedia) {
      var mq = window.matchMedia('(prefers-reduced-motion: reduce)')
      var self = this
      var onMotion = function () {
        self.syncReducedMotion()
      }
      if (mq.addEventListener) mq.addEventListener('change', onMotion)
      else if (mq.addListener) mq.addListener(onMotion)
    }
    if (this.visible) this.onOpen()
  },
  beforeDestroy: function () {
    this.stopTick()
  },
  methods: {
    ...mapActions('dpMailbox', [
      'fetchMailbox',
      'fetchUnreadCount',
      'acceptFriend',
      'rejectFriend',
      'acceptRoomInvite',
      'rejectRoomInvite'
    ]),
    syncReducedMotion: function () {
      if (typeof window === 'undefined' || !window.matchMedia) {
        this.prefersReducedMotion = false
        return
      }
      this.prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    },
    announce: function (text) {
      this.liveAnnounce = text
    },
    focusShell: function () {
      var el = this.$refs.shell
      if (el && typeof el.focus === 'function') el.focus()
    },
    onOpen: function () {
      this.view = 'menu'
      this.selectedIndex = 0
      this.tickSeconds = 0
      this.$emit('open')
      var http = this.$http
      this.fetchMailbox({ http }).catch(function () {})
      this.fetchUnreadCount({ http }).catch(function () {})
      this.startTick()
      this.announce('Mailbox menu')
      this.$nextTick(this.focusShell)
    },
    onClosed: function () {
      this.stopTick()
    },
    requestClose: function () {
      this.$emit('close')
    },
    startTick: function () {
      var self = this
      this.stopTick()
      this.tickSeconds = 0
      this.tickTimer = setInterval(function () {
        self.tickSeconds++
      }, 1000)
    },
    stopTick: function () {
      if (this.tickTimer != null) {
        clearInterval(this.tickTimer)
        this.tickTimer = null
      }
      this.tickSeconds = 0
    },
    menuRowClass: function (i, row) {
      return {
        'dp-console-menu__row--selected': i === this.selectedIndex,
        'dp-console-menu__row--blink': i === this.selectedIndex && this.useRowBlink,
        'dp-console-menu__row--action': row.id === 'close'
      }
    },
    menuRowHint: function (row) {
      if (row.id === 'apply') {
        var n = (this.friendRequests && this.friendRequests.length) || 0
        return n > 0 ? String(n) : '—'
      }
      if (row.id === 'invite') {
        var m = (this.roomInvites && this.roomInvites.length) || 0
        return m > 0 ? String(m) : '—'
      }
      if (row.id === 'close') return 'ESC'
      return ''
    },
    goMenu: function () {
      this.view = 'menu'
      this.selectedIndex = 0
      this.announce('Mailbox menu')
      this.$nextTick(this.focusShell)
    },
    enterList: function (targetView) {
      this.view = targetView
      this.selectedIndex = 0
      this.announce(this.listTitle + ' list')
      this.$nextTick(this.focusShell)
    },
    onMenuRowTap: function (i) {
      if (i !== this.selectedIndex) {
        this.selectedIndex = i
        this.announce(this.menuRows[i].label)
        return
      }
      this.activateMenuRow(i)
    },
    activateMenuRow: function (i) {
      var row = this.menuRows[i]
      if (!row) return
      if (row.id === 'apply') {
        this.enterList('apply-list')
        return
      }
      if (row.id === 'invite') {
        this.enterList('invite-list')
        return
      }
      if (row.id === 'close') this.requestClose()
    },
    selectListRow: function (i) {
      if (i < 0 || i >= this.currentList.length) return
      this.selectedIndex = i
      this.announce(this.listPrimaryName(this.currentList[i]))
    },
    moveSelection: function (delta) {
      if (this.view === 'menu') {
        var mn = this.menuRows.length
        this.selectedIndex = (this.selectedIndex + delta + mn) % mn
        this.announce(this.menuRows[this.selectedIndex].label)
        return
      }
      var ln = this.currentList.length
      if (ln === 0) return
      this.selectedIndex = (this.selectedIndex + delta + ln) % ln
      this.announce(this.listPrimaryName(this.currentList[this.selectedIndex]))
    },
    listRowKey: function (row) {
      if (this.view === 'apply-list') return 'fr-' + (row && row.id)
      return 'riv-' + (row && row.id)
    },
    listPrimaryName: function (row) {
      if (this.view === 'apply-list') {
        return dpSocialDisplayNickname(row && row.fromNickname, row && row.fromUserId, '未知用户')
      }
      return dpSocialDisplayNickname(row && row.inviterNickname, row && row.inviterUserId, '未知用户')
    },
    listSecondaryText: function (row) {
      if (this.view === 'apply-list') {
        return '希望加你为好友 · ' + (row && row.createdAt ? row.createdAt : '—')
      }
      var rid = row && row.roomId != null ? row.roomId : '—'
      return '房间 ' + rid + ' · ' + this.inviteTtlLabel(row)
    },
    inviteTtlLabel: function (inv) {
      var base = inv && inv.remainingSeconds != null ? Number(inv.remainingSeconds) : NaN
      if (!isFinite(base)) return 'TTL —'
      var sec = Math.max(0, Math.floor(base) - this.tickSeconds)
      return sec <= 0 ? 'TTL 0 s' : 'TTL ' + sec + ' s'
    },
    listUserId: function (row) {
      if (this.view === 'apply-list') return row && row.fromUserId
      return row && row.inviterUserId
    },
    listActionBusy: function (row, primary) {
      if (!row || row.id == null) return false
      var id = String(row.id)
      if (this.view === 'apply-list') return this.actionBusyId === 'f:' + id
      if (primary) return this.actionBusyId === 'r:' + id
      return this.actionBusyId === 'r:' + id
    },
    copyId: function (raw) {
      copySocialIdToClipboard(raw, {
        onSuccess: function () {}
      })
    },
    onPrimaryAction: function (row) {
      if (!row || this.actionBusyId) return
      if (this.view === 'apply-list') {
        this.doAcceptFriend(row.id)
        return
      }
      this.doAcceptInvite(row)
    },
    onSecondaryAction: function (row) {
      if (!row || this.actionBusyId) return
      if (this.view === 'apply-list') {
        this.doRejectFriend(row.id)
        return
      }
      this.doRejectInvite(row.id)
    },
    doAcceptFriend: async function (id) {
      await this.acceptFriend({ http: this.$http, id: id })
    },
    doRejectFriend: async function (id) {
      await this.rejectFriend({ http: this.$http, id: id })
    },
    doAcceptInvite: async function (row) {
      var id = row && row.id
      var res = await this.acceptRoomInvite({ http: this.$http, id: id })
      if (!res || !res.ok) return
      var rid = res.roomId || (row && row.roomId) || ''
      if (!rid) {
        alert('未返回房间号')
        return
      }
      this.$emit('join', String(rid).trim())
    },
    doRejectInvite: async function (id) {
      await this.rejectRoomInvite({ http: this.$http, id: id })
    },
    onKeydown: function (e) {
      var key = e.key
      if (key === 'Escape') {
        e.preventDefault()
        if (this.view === 'menu') this.requestClose()
        else this.goMenu()
        return
      }
      if (key === 'ArrowUp' || key === 'w' || key === 'W') {
        e.preventDefault()
        this.moveSelection(-1)
        return
      }
      if (key === 'ArrowDown' || key === 's' || key === 'S') {
        e.preventDefault()
        this.moveSelection(1)
        return
      }
      if (key === 'Enter') {
        e.preventDefault()
        if (this.view === 'menu') this.activateMenuRow(this.selectedIndex)
        else {
          var row = this.currentList[this.selectedIndex]
          if (row) this.onPrimaryAction(row)
        }
        return
      }
      if (this.view !== 'menu') {
        if (key === 'y' || key === 'Y' || key === 'j' || key === 'J') {
          e.preventDefault()
          var sel = this.currentList[this.selectedIndex]
          if (sel) this.onPrimaryAction(sel)
          return
        }
        if (key === 'n' || key === 'N') {
          e.preventDefault()
          var selNo = this.currentList[this.selectedIndex]
          if (selNo) this.onSecondaryAction(selNo)
        }
      }
    }
  }
}
</script>
