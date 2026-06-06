<template>
  <el-dialog
      :visible.sync="dialogVisible"
      :title="dialogTitle"
      width="92%"
      top="4vh"
      :custom-class="dialogCustomClass"
      append-to-body
      :modal="false"
      :z-index="dialogZIndex"
      :close-on-click-modal="false"
      @closed="onClosed"
  >
    <p class="dp-deck-preset-dialog__hint dp-deck-preset-dialog__hint--primary">
      <template v-if="isRetro8bit">
        &gt; SELECT CARDS IN DEAL ORDER — hole×2 per seat → flop×3 → turn×1 → river×1<br>
        &gt; TABLE {{ playerCount }}P — SUGGEST PREFIX {{ suggestedLen }} CARDS<br>
        &gt; EFFECT ON NEXT HAND ONLY — NO BROADCAST
      </template>
      <template v-else>
        <strong>按发牌顺序点击选牌</strong>（点选即可，无需拖拽）。各玩家底牌 2 张 → 翻牌 3 张 → 转牌 1 张 → 河牌 1 张。
        当前 {{ playerCount }} 人桌建议预设前 <strong>{{ suggestedLen }}</strong> 张（2×人数+5）。
        随时可预设，<strong>下一局</strong>发牌时生效；其他玩家不会收到通知。
      </template>
    </p>
    <p v-if="savedPresetCount > 0" class="dp-deck-preset-dialog__status">
      {{ isRetro8bit ? '> STATUS: PRESET ' + savedPresetCount + ' CARDS — NEXT HAND' : ('已预设 ' + savedPresetCount + ' 张，下一局发牌时生效') }}
    </p>

    <div class="dp-deck-preset-dialog__selected">
      <div class="dp-deck-preset-dialog__selected-head">
        <span>{{ isRetro8bit ? ('> LOG: SELECTED ' + selectedCards.length) : ('已选 ' + selectedCards.length + ' 张') }}</span>
        <span>
          <el-button size="mini" :disabled="!selectedCards.length" @click="undoLast">{{ isRetro8bit ? 'UNDO' : '撤销' }}</el-button>
          <el-button size="mini" :disabled="!selectedCards.length" @click="clearSelected">{{ isRetro8bit ? 'CLR' : '清空' }}</el-button>
        </span>
      </div>
      <div v-if="selectedCards.length" class="dp-deck-preset-dialog__selected-list">
        <button
            v-for="(card, idx) in selectedCards"
            :key="'sel-' + card + '-' + idx"
            type="button"
            class="dp-deck-preset-dialog__chip"
            :class="isRetro8bit ? 'dp-deck-preset-dialog__chip--crt' : chipColorClass(card)"
            :title="'第 ' + (idx + 1) + ' 张 · 点击移除'"
            @click="removeAt(idx)"
        >
          <span class="dp-deck-preset-dialog__chip-idx">{{ idx + 1 }}</span>
          <span
              v-if="isRetro8bit"
              class="dp-hd__mini-card"
              :class="crtMiniCardClass(card)"
          >{{ cardFace(card) }}</span>
          <template v-else>{{ cardLabel(card) }}</template>
        </button>
      </div>
      <p v-else class="dp-deck-preset-dialog__empty">{{ isRetro8bit ? '> awaiting card input...' : '从下方点选牌，按发牌顺序追加。' }}</p>
    </div>

    <div class="dp-deck-preset-dialog__grid" role="list" aria-label="可选牌面">
      <div v-for="suit in suits" :key="suit" class="dp-deck-preset-dialog__suit-col">
        <div class="dp-deck-preset-dialog__suit-head" :class="'dp-deck-preset-dialog__suit-head--' + suit">
          {{ suitLabels[suit] }}
        </div>
        <button
            v-for="rank in ranks"
            :key="suit + '_' + rank"
            type="button"
            role="listitem"
            :class="gridCardClasses(suit, rank)"
            :disabled="isUsed(suit + '_' + rank) || submitting"
            :aria-label="cardLabel(suit + '_' + rank) + (isUsed(suit + '_' + rank) ? '，已选' : '')"
            @click="appendCard(suit + '_' + rank)"
        >
          {{ isRetro8bit ? cardFace(suit + '_' + rank) : rank }}
        </button>
      </div>
    </div>

    <span slot="footer" class="dialog-footer">
      <el-button @click="dialogVisible = false">{{ isRetro8bit ? 'ABORT' : '取消' }}</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!selectedCards.length" @click="confirm">
        {{ confirmButtonLabel }}
      </el-button>
    </span>
  </el-dialog>
</template>

<script>
import {
  suggestedPrefixLength,
  DP_SUITS,
  DP_RANKS,
  DP_SUIT_LABELS
} from '../utils/dpDeckCards'
import { getCardClass, getCardDisplay } from '../utils/dpGameCardVisual'
import { dpNextZIndex } from '@/utils/dpModalZIndex'
import {
  dpGetOverlayPortalRoot,
  dpPortalOverlayToBody,
  dpPruneDeckPresetStrayVModal,
  dpRestoreOverlayFromPortal,
  dpScheduleOverlayFullscreenReparent,
  dpSyncDialogPairedVModal
} from '@/utils/dpOverlayPortal'

export default {
  name: 'GameDeckPresetDialog',
  inject: {
    dpGameView: { default: null }
  },
  props: {
    visible: { type: Boolean, default: false },
    playerCount: { type: Number, default: 0 },
    savedPresetCount: { type: Number, default: 0 },
    initialCards: {
      type: Array,
      default: function () {
        return []
      }
    },
    submitting: { type: Boolean, default: false },
    gameUiTheme: { type: String, default: 'default' }
  },
  data: function () {
    return {
      dialogZIndex: dpNextZIndex('deckPreset'),
      selectedCards: [],
      suits: DP_SUITS,
      ranks: DP_RANKS,
      suitLabels: DP_SUIT_LABELS,
      _portalAnchor: null
    }
  },
  computed: {
    dialogVisible: {
      get: function () {
        return this.visible
      },
      set: function (v) {
        this.$emit('update:visible', v)
      }
    },
    suggestedLen: function () {
      return suggestedPrefixLength(this.playerCount)
    },
    isRetro8bit: function () {
      return this.gameUiTheme === 'retro8bit'
    },
    dialogTitle: function () {
      return this.isRetro8bit ? '> DECK_PRESET // NEXT_HAND' : '实验玩法 · 预设下局牌序'
    },
    dialogCustomClass: function () {
      return this.isRetro8bit
        ? 'dp-deck-preset-dialog dp-deck-preset-dialog--retro8bit'
        : 'dp-deck-preset-dialog'
    },
    confirmButtonLabel: function () {
      if (this.isRetro8bit) {
        return this.submitting ? 'EXECUTING...' : ('CONFIRM_PRESET (' + this.selectedCards.length + ')')
      }
      return '确认预设（' + this.selectedCards.length + ' 张）'
    },
    usedSet: function () {
      var set = {}
      for (var i = 0; i < this.selectedCards.length; i++) {
        set[this.selectedCards[i]] = true
      }
      return set
    }
  },
  watch: {
    visible: function (v) {
      if (v) {
        this.dialogZIndex = dpNextZIndex('deckPreset')
        dpPruneDeckPresetStrayVModal()
        this.bootstrapSelected()
        var self = this
        this.$nextTick(function () {
          self.attachPortal()
          dpScheduleOverlayFullscreenReparent(self.dpGameView)
        })
      } else {
        this.detachPortal()
      }
    },
    initialCards: {
      deep: true,
      handler: function () {
        if (this.visible) this.bootstrapSelected()
      }
    }
  },
  beforeDestroy: function () {
    this.detachPortal()
  },
  methods: {
    bootstrapSelected: function () {
      this.selectedCards = (this.initialCards || []).slice()
    },
    findDialogWrapper: function () {
      if (typeof document === 'undefined') return null
      var nodes = document.querySelectorAll('.el-dialog__wrapper')
      var i = 0
      for (i = nodes.length - 1; i >= 0; i--) {
        var node = nodes[i]
        if (!node || node.style.display === 'none') continue
        if (node.querySelector('.dp-deck-preset-dialog')) return node
      }
      return null
    },
    attachPortal: function () {
      var self = this
      var attempt = function () {
        var wrapper = self.findDialogWrapper()
        if (!wrapper) return false
        if (!self._portalAnchor) {
          self._portalAnchor = { parent: null, next: null }
        }
        dpPortalOverlayToBody(wrapper, self._portalAnchor, dpGetOverlayPortalRoot())
        dpSyncDialogPairedVModal(wrapper, self.dialogZIndex)
        dpPruneDeckPresetStrayVModal()
        dpScheduleOverlayFullscreenReparent(self.dpGameView)
        return true
      }
      if (attempt()) return
      if (typeof requestAnimationFrame === 'function') {
        requestAnimationFrame(function () {
          if (!attempt()) setTimeout(attempt, 0)
        })
      } else {
        setTimeout(attempt, 0)
      }
      setTimeout(attempt, 50)
      setTimeout(attempt, 120)
    },
    detachPortal: function () {
      var wrapper = this.findDialogWrapper()
      dpRestoreOverlayFromPortal(wrapper, this._portalAnchor)
      this._portalAnchor = null
      dpPruneDeckPresetStrayVModal()
    },
    onClosed: function () {
      this.detachPortal()
      this.selectedCards = []
    },
    suitColorClass: function (code) {
      return this.colorClassFor(code, 'card')
    },
    chipColorClass: function (code) {
      return this.colorClassFor(code, 'chip')
    },
    crtMiniCardClass: function (code) {
      return getCardClass(code).replace('card-base', '').trim()
    },
    cardFace: function (code) {
      return getCardDisplay(code, { tenChar: 'T' })
    },
    gridCardClasses: function (suit, rank) {
      var code = suit + '_' + rank
      var used = this.isUsed(code)
      if (this.isRetro8bit) {
        return [
          'dp-hd__mini-card',
          'dp-deck-preset-dialog__crt-card',
          this.crtMiniCardClass(code),
          {
            'dp-deck-preset-dialog__crt-card--used': used,
            'dp-deck-preset-dialog__crt-card--disabled': used || this.submitting
          }
        ]
      }
      return [
        'dp-deck-preset-dialog__card',
        this.suitColorClass(code),
        {
          'dp-deck-preset-dialog__card--used': used,
          'dp-deck-preset-dialog__card--disabled': used || this.submitting
        }
      ]
    },
    colorClassFor: function (code, kind) {
      var prefix = kind === 'chip'
        ? 'dp-deck-preset-dialog__chip--'
        : 'dp-deck-preset-dialog__card--'
      if (!code || code.indexOf('_') < 0) return prefix + 'gray'
      var suit = code.split('_')[0]
      if (suit === 'hearts') return prefix + 'red'
      if (suit === 'diamonds') return prefix + 'blue'
      if (suit === 'clubs') return prefix + 'green'
      if (suit === 'spades') return prefix + 'black'
      return prefix + 'gray'
    },
    cardLabel: function (code) {
      if (!code || code.indexOf('_') < 0) return '?'
      var parts = code.split('_')
      var suit = parts[0]
      var rank = parts[1]
      return (this.suitLabels[suit] || '') + rank
    },
    isUsed: function (code) {
      return !!this.usedSet[code]
    },
    appendCard: function (code) {
      if (this.isUsed(code) || this.submitting) return
      this.selectedCards.push(code)
    },
    removeAt: function (idx) {
      this.selectedCards.splice(idx, 1)
    },
    undoLast: function () {
      this.selectedCards.pop()
    },
    clearSelected: function () {
      this.selectedCards = []
    },
    confirm: function () {
      this.$emit('confirm', this.selectedCards.slice())
    }
  }
}
</script>

<style scoped>
.dp-deck-preset-dialog__hint {
  margin: 0 0 10px;
  font-size: 13px;
  line-height: 1.5;
  color: #595959;
}
.dp-deck-preset-dialog__hint--primary {
  padding: 8px 10px;
  border-radius: 6px;
  background: #e6f7ff;
  border: 1px solid #91d5ff;
  color: #434343;
}
.dp-deck-preset-dialog__status {
  margin: 0 0 8px;
  font-size: 13px;
  color: #389e0d;
  font-weight: 600;
}
.dp-deck-preset-dialog__warn {
  margin: 0 0 8px;
  font-size: 13px;
  color: #cf1322;
}
.dp-deck-preset-dialog__selected {
  margin-bottom: 12px;
  padding: 10px;
  border-radius: 8px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
}
.dp-deck-preset-dialog__selected-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 13px;
  font-weight: 600;
}
.dp-deck-preset-dialog__selected-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-height: 120px;
  overflow-y: auto;
}
.dp-deck-preset-dialog__chip {
  border: 1px solid #d9d9d9;
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  background: #fff;
  transition: background 0.15s ease, border-color 0.15s ease, transform 0.12s ease;
}
.dp-deck-preset-dialog__chip:hover {
  border-color: #40a9ff;
  background: #f0f9ff;
  transform: translateY(-1px);
}
.dp-deck-preset-dialog__chip-idx {
  opacity: 0.55;
  margin-right: 4px;
  font-size: 10px;
}
.dp-deck-preset-dialog__empty {
  margin: 0;
  font-size: 12px;
  color: #8c8c8c;
}
.dp-deck-preset-dialog__grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}
.dp-deck-preset-dialog__suit-col {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.dp-deck-preset-dialog__suit-head {
  text-align: center;
  font-weight: 700;
  font-size: 14px;
  padding: 4px 0;
}
.dp-deck-preset-dialog__suit-head--hearts,
.dp-deck-preset-dialog__suit-head--diamonds {
  color: #cf1322;
}
.dp-deck-preset-dialog__suit-head--clubs {
  color: #237804;
}
.dp-deck-preset-dialog__suit-head--spades {
  color: #262626;
}
.dp-deck-preset-dialog__card {
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  padding: 8px 0;
  min-height: 36px;
  font-size: 14px;
  font-weight: 700;
  cursor: pointer;
  background: #fff;
  color: #262626;
  transition:
    background 0.15s ease,
    border-color 0.15s ease,
    box-shadow 0.15s ease,
    transform 0.12s ease,
    opacity 0.15s ease;
}
.dp-deck-preset-dialog__card:hover:not(:disabled) {
  border-color: #40a9ff;
  box-shadow: 0 2px 8px rgba(24, 144, 255, 0.22);
  transform: translateY(-2px);
}
.dp-deck-preset-dialog__card:active:not(:disabled) {
  transform: translateY(0);
  box-shadow: 0 1px 3px rgba(24, 144, 255, 0.18);
}
.dp-deck-preset-dialog__card--used,
.dp-deck-preset-dialog__card--disabled {
  opacity: 0.38;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}
.dp-deck-preset-dialog__card--red,
.dp-deck-preset-dialog__chip--red {
  color: #cf1322;
}
.dp-deck-preset-dialog__card--blue,
.dp-deck-preset-dialog__chip--blue {
  color: #096dd9;
}
.dp-deck-preset-dialog__card--green,
.dp-deck-preset-dialog__chip--green {
  color: #237804;
}
.dp-deck-preset-dialog__card--black,
.dp-deck-preset-dialog__chip--black {
  color: #262626;
}
.dp-deck-preset-dialog__card--gray,
.dp-deck-preset-dialog__chip--gray {
  color: #8c8c8c;
}
</style>
