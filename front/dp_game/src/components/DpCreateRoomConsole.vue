<template>
  <div ref="pageRoot" class="dp-console-page" role="main" aria-label="创建房间游戏机菜单">
    <div class="dp-console-page__toolbar">
      <h1 class="dp-console-page__title">NEW TABLE</h1>
      <div class="dp-console-page__actions">
        <dp-fluidity-toggle />
        <button type="button" class="dp-console-page__back" @click="onBack">返回大厅</button>
      </div>
    </div>

    <div class="dp-console-stage">
      <div
        ref="shell"
        class="dp-console-shell"
        tabindex="0"
        role="application"
        aria-label="房间设置菜单"
        @keydown="onKeydown"
      >
        <div class="dp-console-shell__scanlines" aria-hidden="true" />
        <header class="dp-console-shell__head">
          <h2 class="dp-console-shell__head-title">— CREATE TABLE —</h2>
        </header>

        <section v-if="passwordEditMode" class="dp-console-pass" aria-label="密码编辑">
          <p class="dp-console-pass__title">ROOM PASSWORD</p>
          <p class="dp-console-pass__mask" aria-live="polite">{{ passwordMaskDisplay }}</p>
          <div class="dp-console-pass__picker">
            <button
              type="button"
              class="dp-console-pass__chev"
              aria-label="上一个字符"
              :disabled="committing"
              @click="cyclePasswordChar(-1)"
            >
              &lt;
            </button>
            <button
              type="button"
              class="dp-console-pass__char-btn"
              aria-label="当前字符"
              :disabled="committing"
              @click="passwordAppendChar"
            >
              {{ passwordPickerChar }}
            </button>
            <button
              type="button"
              class="dp-console-pass__chev"
              aria-label="下一个字符"
              :disabled="committing"
              @click="cyclePasswordChar(1)"
            >
              &gt;
            </button>
          </div>
          <div class="dp-console-pass__actions">
            <button
              type="button"
              class="dp-console-pass__act"
              :disabled="committing"
              @click="passwordAppendChar"
            >
              ADD
            </button>
            <button
              type="button"
              class="dp-console-pass__act"
              :disabled="committing"
              @click="passwordDeleteChar"
            >
              DEL
            </button>
            <button
              type="button"
              class="dp-console-pass__act dp-console-pass__act--ok"
              :disabled="committing"
              @click="confirmPasswordEdit"
            >
              OK
            </button>
            <button
              type="button"
              class="dp-console-pass__act dp-console-pass__act--cancel"
              :disabled="committing"
              @click="cancelPasswordEdit"
            >
              ESC
            </button>
          </div>
          <div class="dp-console-pass__charset" role="group" aria-label="字符集">
            <button
              v-for="(ch, ci) in passwordCharsetChars"
              :key="'pw-' + ci"
              type="button"
              class="dp-console-pass__charset-btn"
              :class="{ 'dp-console-pass__charset-btn--active': ci === passwordPickerIndex }"
              :aria-label="'字符 ' + ch"
              :disabled="committing"
              @click="pickPasswordChar(ci)"
            >
              {{ ch === ' ' ? '·' : ch }}
            </button>
          </div>
          <p class="dp-console-pass__hint">
            <span class="dp-console-pass__hint-kb"
              >←→ pick · A/SPACE add · ENTER done · ESC cancel · UP delete</span
            >
            <span class="dp-console-pass__hint-touch">tap char · &lt;&gt; · ADD/DEL/OK</span>
          </p>
        </section>

        <ul v-else class="dp-console-menu" role="menu" aria-label="房间选项">
          <li
            v-for="(row, i) in menuRows"
            :key="row.id"
            role="menuitem"
            :aria-selected="i === selectedIndex"
            class="dp-console-menu__row"
            :class="rowRowClass(i, row)"
            @click="onMenuRowTap(i)"
          >
            <div class="dp-console-menu__left">
              <span class="dp-console-menu__cursor" aria-hidden="true">&gt;</span>
              <span class="dp-console-menu__label">{{ row.label }}</span>
            </div>
            <div class="dp-console-menu__right">
              <span v-if="rowHasTouchAdjust(row)" class="dp-console-menu__adj">
                <button
                  type="button"
                  class="dp-console-menu__chev"
                  :aria-label="row.label + ' 减少'"
                  :disabled="committing"
                  @click.stop="onRowAdjust(i, -1)"
                >
                  &lt;
                </button>
                <button
                  type="button"
                  class="dp-console-menu__value-btn"
                  :aria-label="row.label + ' 当前值'"
                  :disabled="committing"
                  @click.stop="onRowValueTap(i)"
                >
                  {{ rowValueText(row) }}
                </button>
                <button
                  type="button"
                  class="dp-console-menu__chev"
                  :aria-label="row.label + ' 增加'"
                  :disabled="committing"
                  @click.stop="onRowAdjust(i, 1)"
                >
                  &gt;
                </button>
              </span>
              <span v-else class="dp-console-menu__value">{{ rowValueText(row) }}</span>
            </div>
          </li>
        </ul>

        <p class="dp-console-footer" aria-hidden="true">
          <span class="dp-console-footer__kb"
            >↑↓ move · ←→ change · ENTER select · ESC back</span
          >
          <span class="dp-console-footer__touch"
            >tap row · tap &lt;&gt; · double-tap CREATE</span
          >
        </p>
      </div>

      <p
        v-if="statusMessage && !dialogVisible"
        class="dp-console-status"
        role="alert"
        aria-live="assertive"
      >
        {{ statusMessage }}
      </p>
    </div>

    <p ref="liveRegion" class="dp-console-live" aria-live="polite" aria-atomic="true">
      {{ liveAnnounce }}
    </p>

    <div
      v-if="dialogVisible"
      class="dp-console-dialog"
      role="alertdialog"
      aria-modal="true"
      :aria-labelledby="dialogTitleId"
    >
      <div class="dp-console-dialog__box">
        <p :id="dialogTitleId" class="dp-console-dialog__msg">{{ dialogMessage }}</p>
        <button type="button" class="dp-console-dialog__btn" @click="dismissDialog">OK</button>
      </div>
    </div>

  </div>
</template>

<script>
import '@/styles/dp-create-room-console.css'
import { mapState } from 'vuex'
import DpFluidityToggle from '@/components/DpFluidityToggle.vue'
import { dpCreateRoomAndStart } from '@/utils/dpCreateRoomSubmit'
import { shouldSkipRetroEnterEffects } from '@/utils/dpRetroEnterGameHandoff'

var ROOM_PRESETS = {
  casual: { smallBlind: 2, startingStackBb: 40, maxSeatCount: 6, roomPassword: '' },
  standard: { smallBlind: 5, startingStackBb: 50, maxSeatCount: 9, roomPassword: '' },
  deep: { smallBlind: 10, startingStackBb: 100, maxSeatCount: 6, roomPassword: '' }
}

var PROFILE_ORDER = ['casual', 'standard', 'deep']
var PROFILE_LABELS = {
  casual: 'CASUAL',
  standard: 'STANDARD',
  deep: 'DEEP'
}

var PASSWORD_CHARSET =
  'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-. '

var MENU_ROWS = [
  { id: 'profile', label: 'PROFILE', type: 'profile' },
  { id: 'sc', label: 'SMALL BLIND', type: 'number', field: 'smallBlind', min: 1, max: 999, step: 1 },
  {
    id: 'stack',
    label: 'STACK (BB)',
    type: 'number',
    field: 'startingStackBb',
    min: 5,
    max: 200,
    step: 5
  },
  {
    id: 'seats',
    label: 'SEATS',
    type: 'number',
    field: 'maxSeatCount',
    min: 2,
    max: 9,
    step: 1
  },
  {
    id: 'think',
    label: 'THINK TIME',
    type: 'number',
    field: 'thinkTimeSeconds',
    min: 15,
    max: 180,
    step: 5
  },
  { id: 'password', label: 'PASSWORD', type: 'password' },
  { id: 'create', label: 'CREATE', type: 'action-create' },
  { id: 'back', label: 'BACK', type: 'action-back' }
]

export default {
  name: 'DpCreateRoomConsole',
  components: { DpFluidityToggle },
  props: {
    user: {
      type: Object,
      default: function () {
        return {}
      }
    },
    /** @type {() => import('@/components/DpCrtBootSequence.vue').default | null} */
    resolveBootRef: {
      type: Function,
      default: null
    }
  },
  data: function () {
    return {
      config: {
        smallBlind: 5,
        startingStackBb: 50,
        maxSeatCount: 9,
        thinkTimeSeconds: 30,
        roomPassword: ''
      },
      profileId: 'standard',
      selectedIndex: 0,
      passwordEditMode: false,
      passwordDraft: '',
      passwordPickerIndex: 0,
      statusMessage: '',
      dialogVisible: false,
      dialogMessage: '',
      liveAnnounce: '',
      committing: false,
      prefersReducedMotion: false,
      menuRows: MENU_ROWS,
      dialogTitleId: 'dp-console-dialog-title',
      lastCreateTapAt: 0
    }
  },
  computed: {
    ...mapState('dpGame', ['ecoMode']),
    skipRetroEnterEffects: function () {
      return shouldSkipRetroEnterEffects()
    },
    useRowBlink: function () {
      return !this.skipRetroEnterEffects
    },
    bigBlindChips: function () {
      var sc = Math.max(1, Number(this.config.smallBlind) || 5)
      return sc * 2
    },
    passwordPickerChar: function () {
      return PASSWORD_CHARSET.charAt(this.passwordPickerIndex) || 'A'
    },
    passwordMaskDisplay: function () {
      var s = this.passwordDraft || ''
      if (!s) return '(public)'
      return '*'.repeat(Math.min(s.length, 16))
    },
    passwordCharsetChars: function () {
      return PASSWORD_CHARSET.split('')
    }
  },
  mounted: function () {
    this.syncReducedMotion()
    if (typeof window !== 'undefined' && window.matchMedia) {
      var mqMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
      var self = this
      var onMotion = function () {
        self.syncReducedMotion()
      }
      if (mqMotion.addEventListener) {
        mqMotion.addEventListener('change', onMotion)
      } else if (mqMotion.addListener) {
        mqMotion.addListener(onMotion)
      }
    }
    this.announce('Create table menu. Use arrow keys or tap rows.')
    this.$nextTick(this.focusShell)
  },
  methods: {
    syncReducedMotion: function () {
      if (typeof window === 'undefined' || !window.matchMedia) {
        this.prefersReducedMotion = false
        return
      }
      this.prefersReducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    },
    focusShell: function () {
      var el = this.$refs.shell
      if (el && typeof el.focus === 'function') el.focus()
    },
    announce: function (text) {
      this.liveAnnounce = text
    },
    rowRowClass: function (i, row) {
      var c = {
        'dp-console-menu__row--selected': i === this.selectedIndex,
        'dp-console-menu__row--blink': i === this.selectedIndex && this.useRowBlink
      }
      if (row.type === 'action-create') c['dp-console-menu__row--create'] = true
      if (row.type && row.type.indexOf('action') === 0) c['dp-console-menu__row--action'] = true
      return c
    },
    rowValueText: function (row) {
      if (row.id === 'profile') return PROFILE_LABELS[this.profileId] || 'STANDARD'
      if (row.id === 'sc') {
        return this.config.smallBlind + ' SC  BC ' + this.bigBlindChips
      }
      if (row.id === 'stack') return String(this.config.startingStackBb)
      if (row.id === 'seats') return String(this.config.maxSeatCount)
      if (row.id === 'think') return String(this.config.thinkTimeSeconds) + 's'
      if (row.id === 'password') {
        var p = this.config.roomPassword || ''
        if (!p) return 'PUBLIC'
        return '*'.repeat(Math.min(p.length, 8))
      }
      if (row.id === 'create') return '>>'
      if (row.id === 'back') return 'ESC'
      return ''
    },
    clearStatus: function () {
      this.statusMessage = ''
    },
    showError: function (msg) {
      var text = String(msg || '').replace(/^\[ERR\]\s*/i, '')
      this.statusMessage = text
      this.dialogMessage = text
      this.dialogVisible = true
      this.announce(text)
    },
    dismissDialog: function () {
      this.dialogVisible = false
      this.$nextTick(this.focusShell)
    },
    onBack: function () {
      if (this.committing) return
      this.$emit('back')
    },
    rowHasTouchAdjust: function (row) {
      return row.type === 'profile' || row.type === 'number' || row.id === 'password'
    },
    selectRow: function (i) {
      if (i < 0 || i >= this.menuRows.length) return
      this.selectedIndex = i
      this.clearStatus()
      var row = this.menuRows[i]
      this.announce(row.label + ' ' + this.rowValueText(row))
    },
    onMenuRowTap: function (i) {
      if (this.committing || this.passwordEditMode) return
      var row = this.menuRows[i]
      if (row.id === 'back') {
        this.onBack()
        return
      }
      if (row.id === 'create') {
        var now = Date.now()
        var doubleTap = now - this.lastCreateTapAt < 400
        this.lastCreateTapAt = now
        if (doubleTap) {
          this.doCommit()
          return
        }
        if (i !== this.selectedIndex) {
          this.selectRow(i)
          return
        }
        this.doCommit()
        return
      }
      if (i !== this.selectedIndex) {
        this.selectRow(i)
        return
      }
      if (row.id === 'password') {
        this.enterPasswordEdit()
      }
    },
    onRowAdjust: function (i, delta) {
      if (this.committing || this.passwordEditMode) return
      var row = this.menuRows[i]
      if (row.id === 'password') {
        this.selectRow(i)
        this.enterPasswordEdit()
        return
      }
      if (i !== this.selectedIndex) this.selectRow(i)
      if (row.type === 'profile') {
        this.cycleProfile(delta)
        return
      }
      if (row.type === 'number') {
        this.adjustNumberField(row, delta)
      }
    },
    onRowValueTap: function (i) {
      if (this.committing || this.passwordEditMode) return
      var row = this.menuRows[i]
      if (row.id === 'password') {
        this.selectRow(i)
        this.enterPasswordEdit()
        return
      }
      this.onRowAdjust(i, 1)
    },
    pickPasswordChar: function (index) {
      if (this.committing) return
      var len = PASSWORD_CHARSET.length
      if (index < 0 || index >= len) return
      this.passwordPickerIndex = index
      this.announce('Character ' + this.passwordPickerChar)
    },
    onKeydown: function (e) {
      if (this.committing) return
      if (this.dialogVisible) {
        if (e.key === 'Enter' || e.key === 'Escape') {
          e.preventDefault()
          this.dismissDialog()
        }
        return
      }
      var key = e.key

      if (this.passwordEditMode) {
        if (key === ' ' || key === 'a' || key === 'A') {
          e.preventDefault()
          this.passwordAppendChar()
          return
        }
        if (key === 'ArrowUp' || key === 'w' || key === 'W') {
          e.preventDefault()
          this.passwordDeleteChar()
          return
        }
        if (key === 'ArrowLeft' || key === 'ArrowRight') {
          e.preventDefault()
          this.cyclePasswordChar(key === 'ArrowLeft' ? -1 : 1)
          return
        }
        if (key === 'Enter') {
          e.preventDefault()
          this.confirmPasswordEdit()
          return
        }
        if (key === 'Escape') {
          e.preventDefault()
          this.cancelPasswordEdit()
        }
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
      if (key === 'ArrowLeft' || key === 'a' || key === 'A') {
        e.preventDefault()
        this.adjustValue(-1)
        return
      }
      if (key === 'ArrowRight' || key === 'd' || key === 'D') {
        e.preventDefault()
        this.adjustValue(1)
        return
      }
      if (key === 'Enter') {
        e.preventDefault()
        this.onEnter()
        return
      }
      if (key === 'Escape') {
        e.preventDefault()
        if (this.passwordEditMode) this.cancelPasswordEdit()
        else this.onBack()
      }
    },
    moveSelection: function (delta) {
      if (this.passwordEditMode) {
        if (delta < 0) this.passwordDeleteChar()
        return
      }
      var n = this.menuRows.length
      this.selectedIndex = (this.selectedIndex + delta + n) % n
      this.clearStatus()
      var row = this.menuRows[this.selectedIndex]
      this.announce(row.label + ' ' + this.rowValueText(row))
    },
    adjustValue: function (delta) {
      this.clearStatus()
      if (this.passwordEditMode) {
        this.cyclePasswordChar(delta)
        return
      }
      var row = this.menuRows[this.selectedIndex]
      if (row.type === 'profile') {
        this.cycleProfile(delta)
        return
      }
      if (row.type === 'number') {
        this.adjustNumberField(row, delta)
      }
    },
    cycleProfile: function (delta) {
      var idx = PROFILE_ORDER.indexOf(this.profileId)
      if (idx < 0) idx = 1
      idx = (idx + delta + PROFILE_ORDER.length) % PROFILE_ORDER.length
      this.applyProfile(PROFILE_ORDER[idx])
    },
    applyProfile: function (id) {
      var preset = ROOM_PRESETS[id]
      if (!preset) return
      this.profileId = id
      this.config.smallBlind = preset.smallBlind
      this.config.startingStackBb = preset.startingStackBb
      this.config.maxSeatCount = preset.maxSeatCount
      this.config.roomPassword = preset.roomPassword || ''
      this.announce('Profile ' + PROFILE_LABELS[id])
    },
    adjustNumberField: function (row, delta) {
      var field = row.field
      var cur = Number(this.config[field])
      if (!Number.isFinite(cur)) cur = row.min
      var step = row.step || 1
      var next = cur + delta * step
      next = Math.round(next)
      if (next < row.min) next = row.min
      if (next > row.max) next = row.max
      this.config[field] = next
      this.announce(row.label + ' ' + next)
    },
    onEnter: function () {
      this.clearStatus()
      if (this.passwordEditMode) {
        this.confirmPasswordEdit()
        return
      }
      var row = this.menuRows[this.selectedIndex]
      if (row.id === 'password') {
        this.enterPasswordEdit()
        return
      }
      if (row.id === 'create') {
        this.doCommit()
        return
      }
      if (row.id === 'back') {
        this.onBack()
      }
    },
    enterPasswordEdit: function () {
      this.passwordEditMode = true
      this.passwordDraft = this.config.roomPassword || ''
      this.passwordPickerIndex = 0
      this.announce('Password edit mode')
    },
    cancelPasswordEdit: function () {
      this.passwordEditMode = false
      this.passwordDraft = ''
      this.announce('Password edit cancelled')
      this.$nextTick(this.focusShell)
    },
    confirmPasswordEdit: function () {
      this.config.roomPassword = this.passwordDraft
      this.passwordEditMode = false
      this.passwordDraft = ''
      this.announce('Password set')
      this.$nextTick(this.focusShell)
    },
    cyclePasswordChar: function (delta) {
      var len = PASSWORD_CHARSET.length
      this.passwordPickerIndex = (this.passwordPickerIndex + delta + len) % len
      this.announce('Character ' + this.passwordPickerChar)
    },
    passwordAppendChar: function () {
      if (this.passwordDraft.length >= 32) {
        this.showError('Password max 32 characters')
        return
      }
      this.passwordDraft += this.passwordPickerChar
      this.announce('Character added')
    },
    passwordDeleteChar: function () {
      if (!this.passwordDraft) return
      this.passwordDraft = this.passwordDraft.slice(0, -1)
      this.announce('Character removed')
    },
    validateConfig: function () {
      if (!this.user || !this.user.nickname) {
        return 'Not logged in'
      }
      var sc = Number(this.config.smallBlind)
      if (!Number.isFinite(sc) || sc < 1) return 'Small blind must be >= 1'
      var stack = Number(this.config.startingStackBb)
      if (!Number.isFinite(stack) || stack < 5 || stack > 200) {
        return 'Stack must be 5–200 BB'
      }
      var seats = Number(this.config.maxSeatCount)
      if (!Number.isFinite(seats) || seats < 2 || seats > 9) return 'Seats must be 2–9'
      var think = Number(this.config.thinkTimeSeconds)
      if (!Number.isFinite(think) || think < 15 || think > 180) {
        return 'Think time must be 15–180 seconds'
      }
      return null
    },
    doCommit: function () {
      if (this.committing) return
      var err = this.validateConfig()
      if (err) {
        this.showError(err)
        return
      }
      this.committing = true
      this.runSubmit()
    },
    resolveBootRefInstance: function () {
      if (typeof this.resolveBootRef === 'function') {
        return this.resolveBootRef()
      }
      return null
    },
    runSubmit: function () {
      var self = this
      var useHandoff = !this.skipRetroEnterEffects
      dpCreateRoomAndStart({
        http: this.$http,
        router: this.$router,
        user: this.user,
        config: this.config,
        crtHandoff: useHandoff,
        bootRef: useHandoff ? this.resolveBootRefInstance() : null,
        onError: function (msg) {
          self.committing = false
          self.showError(msg)
          self.$message.error(msg)
        }
      }).then(function (result) {
        if (!result.ok) {
          self.committing = false
        }
      })
    }
  }
}
</script>
