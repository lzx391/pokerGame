<template>
  <div class="dp-owner-touch">
    <button
        v-if="showEntry"
        type="button"
        class="dp-owner-touch__entry"
        :class="entryClass"
        aria-label="房主操作"
        :aria-expanded="open ? 'true' : 'false'"
        @click="openPanel"
    >
      <span class="dp-owner-touch__entry-icon" aria-hidden="true">⚙</span>
      <span class="dp-owner-touch__entry-label">{{ entryLabel }}</span>
    </button>

    <transition name="dp-sheet">
      <game-bottom-sheet
          v-if="open"
          title="房主操作"
          aria-label="房主操作"
          :wide="true"
          body-modifier="owner-hub"
          @close="closePanel"
      >
        <template slot="overlay">
          <custom-npc-style-dialog
              v-if="showCustomNpcStyleDialog"
              :visible="true"
              :pending-count="customNpcPendingCount"
              :submitting="customBotAdding"
              @cancel="$emit('close-custom-npc')"
              @confirm="(profile) => $emit('submit-custom-npc', profile)"
          />
        </template>

        <div
            class="dp-owner-touch__sheet"
            :class="{ 'dp-owner-touch__sheet--retro': gameUiTheme === 'retro8bit' }"
        >
          <game-owner-hub-content
              ref="hubContent"
              :active="open"
              :touch-mode="true"
              :terminal-focused="false"
              :owner-reveal-all="ownerRevealAll"
              :demo-bot-adding="demoBotAdding"
              :demo-bot-added-tip="demoBotAddedTip"
              :maniac-bot-adding="maniacBotAdding"
              :maniac-bot-added-tip="maniacBotAddedTip"
              :tag-bot-adding="tagBotAdding"
              :tag-bot-added-tip="tagBotAddedTip"
              :lag-bot-adding="lagBotAdding"
              :lag-bot-added-tip="lagBotAddedTip"
              :nit-bot-adding="nitBotAdding"
              :nit-bot-added-tip="nitBotAddedTip"
              :call-bot-adding="callBotAdding"
              :call-bot-added-tip="callBotAddedTip"
              :llm-bot-adding="llmBotAdding"
              :llm-bot-added-tip="llmBotAddedTip"
              :llm-global-bot-adding="llmGlobalBotAdding"
              :llm-global-bot-added-tip="llmGlobalBotAddedTip"
              :custom-bot-adding="customBotAdding"
              :custom-bot-added-tip="customBotAddedTip"
              @confirm-add-npcs="$emit('confirm-add-npcs', $event)"
              @transfer-owner="$emit('transfer-owner')"
              @kick-players="$emit('kick-players', $event)"
              @toggle-reveal="$emit('toggle-reveal')"
              @request-close="closePanel"
          />

          <div
              v-if="touchFooterVisible"
              class="dp-owner-touch__footer"
              role="toolbar"
              aria-label="房主操作确认"
          >
            <button
                type="button"
                class="dp-owner-touch__footer-btn dp-owner-touch__footer-btn--back"
                @click="onFooterBack"
            >
              {{ footerBackLabel }}
            </button>

            <div
                v-if="hubScreen === 'npc-pick' && hubListLength > 0"
                class="dp-owner-touch__count-stepper"
                aria-label="NPC 数量"
            >
              <button
                  type="button"
                  class="dp-owner-touch__count-btn"
                  :disabled="npcCount <= 1"
                  aria-label="减少数量"
                  @click="bumpNpc(-1)"
              >
                −
              </button>
              <span class="dp-owner-touch__count-val">× {{ npcCount }}</span>
              <button
                  type="button"
                  class="dp-owner-touch__count-btn"
                  :disabled="npcCount >= 9"
                  aria-label="增加数量"
                  @click="bumpNpc(1)"
              >
                +
              </button>
            </div>

            <button
                v-if="footerPrimary"
                type="button"
                class="dp-owner-touch__footer-btn dp-owner-touch__footer-btn--primary"
                :class="{ 'dp-owner-touch__footer-btn--danger': footerPrimary.danger }"
                :disabled="footerPrimary.disabled"
                @click="onFooterPrimary"
            >
              {{ footerPrimary.label }}
            </button>
          </div>
        </div>
      </game-bottom-sheet>
    </transition>
  </div>
</template>

<script>
import GameBottomSheet from './GameBottomSheet.vue'
import GameOwnerHubContent from './GameOwnerHubContent.vue'
import CustomNpcStyleDialog from './CustomNpcStyleDialog.vue'

export default {
  name: 'GameOwnerTouchPanel',
  components: { GameBottomSheet, GameOwnerHubContent, CustomNpcStyleDialog },
  props: {
    showEntry: { type: Boolean, default: false },
    open: { type: Boolean, default: false },
    gameUiTheme: { type: String, default: 'default' },
    entryVariant: {
      type: String,
      default: 'topbar',
      validator: function (v) {
        return v === 'topbar' || v === 'mobile'
      }
    },
    ownerRevealAll: { type: Boolean, default: false },
    showCustomNpcStyleDialog: { type: Boolean, default: false },
    customNpcPendingCount: { type: Number, default: 1 },
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
  computed: {
    entryLabel: function () {
      if (this.gameUiTheme === 'retro8bit') {
        return this.entryVariant === 'mobile' ? '房主' : 'OWNER'
      }
      return '房主操作'
    },
    entryClass: function () {
      return {
        'dp-owner-touch__entry--topbar': this.entryVariant === 'topbar',
        'dp-owner-touch__entry--mobile': this.entryVariant === 'mobile',
        'dp-owner-touch__entry--retro': this.gameUiTheme === 'retro8bit'
      }
    },
    hub: function () {
      return this.$refs.hubContent || null
    },
    hubScreen: function () {
      return this.hub ? this.hub.currentScreen : 'root'
    },
    hubListLength: function () {
      return this.hub ? this.hub.listLength : 0
    },
    npcCount: function () {
      if (!this.hub || this.hubScreen !== 'npc-pick') return 1
      var row = this.hub.allNpcRows[this.hub.cursorIndex]
      if (!row) return 1
      return this.hub.npcCounts[row.id] || 1
    },
    touchFooterVisible: function () {
      return this.hubScreen !== 'root'
    },
    footerBackLabel: function () {
      return this.hub && this.hub.stackDepth > 1 ? '返回' : '关闭'
    },
    footerPrimary: function () {
      if (!this.hub) return null
      var screen = this.hubScreen
      if (screen === 'npc-pick' && this.hubListLength > 0) {
        return { label: '选择类型', action: 'npc-next', disabled: false }
      }
      if (screen === 'npc-confirm') {
        return { label: '确认添加', action: 'npc-confirm', disabled: false }
      }
      if (screen === 'transfer-pick' && this.hubListLength > 0) {
        return { label: '下一步', action: 'transfer-next', disabled: false }
      }
      if (screen === 'transfer-confirm') {
        return { label: '确认移交', action: 'transfer-confirm', disabled: false }
      }
      if (screen === 'kick-pick') {
        var n = this.hub.kickSelectionNicknames.length
        return {
          label: n > 0 ? '下一步（' + n + '）' : '请勾选玩家',
          action: 'kick-next',
          disabled: n === 0
        }
      }
      if (screen === 'kick-confirm') {
        return { label: '确认踢出', action: 'kick-confirm', danger: true, disabled: false }
      }
      return null
    }
  },
  methods: {
    openPanel: function () {
      this.$emit('open')
    },
    closePanel: function () {
      if (this.hub && typeof this.hub.resetStack === 'function') {
        this.hub.resetStack()
      }
      this.$emit('close')
    },
    onFooterBack: function () {
      if (!this.hub) {
        this.closePanel()
        return
      }
      if (this.hub.stackDepth > 1 && typeof this.hub.popScreen === 'function') {
        this.hub.popScreen()
        return
      }
      this.closePanel()
    },
    bumpNpc: function (delta) {
      if (this.hub && typeof this.hub.bumpNpcCount === 'function') {
        this.hub.bumpNpcCount(delta)
      }
    },
    onFooterPrimary: function () {
      if (!this.hub || !this.footerPrimary) return
      var action = this.footerPrimary.action
      if (action === 'npc-next') {
        this.hub.enterNpcConfirm()
      } else if (action === 'npc-confirm') {
        this.hub.emitNpcConfirm()
      } else if (action === 'transfer-next') {
        this.hub.enterTransferConfirm()
      } else if (action === 'transfer-confirm') {
        this.hub.emitTransferConfirm()
      } else if (action === 'kick-next') {
        this.hub.pushScreen('kick-confirm')
      } else if (action === 'kick-confirm') {
        this.hub.emitKickConfirm()
      }
    }
  }
}
</script>

<style scoped>
.dp-owner-touch__entry {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 44px;
  min-width: 44px;
  padding: 0.45em 0.85em;
  border-radius: 6px;
  border: 1px solid transparent;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  touch-action: manipulation;
  -webkit-tap-highlight-color: transparent;
}

.dp-owner-touch__entry--topbar {
  background: var(--dp-owner-purple-bg, #722ed1);
  color: var(--dp-owner-purple-fg, #fff);
}

.dp-owner-touch__entry--mobile {
  flex: 0 0 auto;
  min-height: 48px;
  padding: 0 14px;
  border-radius: 8px;
  background: var(--dp-owner-purple-bg, #722ed1);
  color: var(--dp-owner-purple-fg, #fff);
  font-size: 14px;
}

.dp-owner-touch__entry--retro {
  border-radius: 0;
  font-family: 'Courier New', ui-monospace, monospace;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  background: rgba(10, 26, 10, 0.85);
  color: #4af626;
  border: 2px solid rgba(74, 246, 38, 0.45);
  box-shadow: 0 0 0 1px #000, 2px 2px 0 rgba(0, 0, 0, 0.45);
}

.dp-owner-touch__entry--retro.dp-owner-touch__entry--mobile {
  background: rgba(10, 26, 10, 0.92);
}

.dp-owner-touch__entry-icon {
  font-size: 1.05em;
  line-height: 1;
}

.dp-owner-touch__sheet {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.dp-owner-touch__footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  padding-top: 8px;
  border-top: 1px solid var(--dp-panel-border, rgba(255, 255, 255, 0.12));
}

.dp-owner-touch__footer-btn {
  flex: 1 1 120px;
  min-height: 48px;
  padding: 0 16px;
  border-radius: 8px;
  border: 1px solid var(--dp-btn-ghost-border, rgba(255, 255, 255, 0.2));
  background: var(--dp-btn-ghost-bg, rgba(255, 255, 255, 0.08));
  color: var(--dp-text-primary, #e8e8e8);
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  touch-action: manipulation;
}

.dp-owner-touch__footer-btn--primary {
  background: var(--dp-owner-purple-bg, #722ed1);
  color: var(--dp-owner-purple-fg, #fff);
  border-color: transparent;
}

.dp-owner-touch__footer-btn--danger {
  background: #cf1322;
  color: #fff;
}

.dp-owner-touch__footer-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.dp-owner-touch__count-stepper {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex: 0 0 auto;
}

.dp-owner-touch__count-btn {
  width: 44px;
  height: 44px;
  border-radius: 8px;
  border: 1px solid var(--dp-btn-ghost-border, rgba(255, 255, 255, 0.2));
  background: var(--dp-btn-ghost-bg, rgba(255, 255, 255, 0.08));
  color: var(--dp-text-primary, #e8e8e8);
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
  touch-action: manipulation;
}

.dp-owner-touch__count-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.dp-owner-touch__count-val {
  min-width: 3em;
  text-align: center;
  font-weight: 700;
  font-size: 16px;
}

.dp-owner-touch__sheet--retro .dp-owner-touch__footer {
  border-top-color: rgba(74, 246, 38, 0.22);
}

.dp-owner-touch__sheet--retro .dp-owner-touch__footer-btn,
.dp-owner-touch__sheet--retro .dp-owner-touch__count-btn {
  border-radius: 0;
  font-family: 'Courier New', ui-monospace, monospace;
  background: rgba(10, 26, 10, 0.75);
  color: #4af626;
  border: 1px solid rgba(74, 246, 38, 0.28);
}

.dp-owner-touch__sheet--retro .dp-owner-touch__footer-btn--primary {
  background: rgba(10, 40, 10, 0.9);
  box-shadow: inset 0 0 12px rgba(74, 246, 38, 0.12);
}

.dp-owner-touch__sheet--retro .dp-owner-touch__footer-btn--danger {
  background: rgba(60, 10, 10, 0.85);
  color: #ff7875;
  border-color: rgba(255, 120, 117, 0.35);
}
</style>
