<template>
  <div
      v-if="embedded || visible"
      :class="embedded ? 'game-owner-tool-embedded-root' : 'hand-rank-modal-mask'"
      @click="embedded ? null : $emit('close')"
  > 
    <div
        :class="embedded ? 'game-owner-tool-embedded' : 'hand-rank-modal hand-rank-modal--legacy'"
        @click.stop
    >
      <div v-if="!embedded" style="display:flex; justify-content:space-between; align-items:center; margin-bottom:15px;">
        <span style="font-size:18px; font-weight:bold;">房主神器</span>
        <button
            type="button"
            style="background:#d9d9d9; border:none; width:28px; height:28px; border-radius:4px; cursor:pointer; font-size:16px; line-height:1;"
            @click="$emit('close')"
        >×</button>
      </div>
      <div v-else class="game-owner-tool-embedded__title">房主神器</div>

      <section class="game-owner-tool__npc-section" aria-label="添加 NPC">
      <div style="margin-bottom:12px; padding:8px; border-radius:6px; background:#fff7e6; border:1px dashed #ffa940;">
        <div style="font-size:13px; font-weight:bold; color:#d46b08; margin-bottom:4px;">
          实验功能：加入演示 NPC
        </div>
        <div style="font-size:12px; color:#8c8c8c; margin-bottom:6px;">
          下一局可加入规则 NPC：
          <span style="font-weight:bold;">BOT_FISH</span>、
          <span style="font-weight:bold;">BOT_TAG</span>、
          <span style="font-weight:bold;">BOT_LAG</span>、
          <span style="font-weight:bold;">BOT_NIT</span>、
          <span style="font-weight:bold;">BOT_CALL</span>、
          <span style="font-weight:bold;">BOT_MANIAC</span>；
          <span style="font-weight:bold;">BOT_CUSTOM</span>（自定义性格，弹窗调参）；
          另有 <span style="font-weight:bold;">BOT_LLM</span>（大模型）与
          <span style="font-weight:bold;">BOT_LLM_GLOBAL</span>（整手叙事 / 多轮上下文）。
          服务端会为 BOT 生成唯一后缀；JSON 里仍是完整 nickname，牌桌上展示为前缀 + uuid 去横线后的前 4 位。
          先调数量（1～9，受房间空位限制），再点「确认添加」。
        </div>
        <div style="display:flex; flex-direction:column; gap:10px;">
          <div
            v-for="row in ruleNpcRows"
            :key="row.id"
            style="display:flex; flex-wrap:wrap; align-items:center; gap:8px; font-size:12px;"
          >
            <span style="min-width:148px; font-weight:600;" :style="{ color: row.labelColor }">{{ row.label }}</span>
            <span style="display:inline-flex; align-items:center; gap:4px;">
              <button
                type="button"
                class="owner-npc-count-btn"
                :disabled="row.adding || npcCounts[row.id] <= 1"
                @click="bumpNpcCount(row.id, -1, 9)"
              >
                −
              </button>
              <input
                v-model.number="npcCounts[row.id]"
                type="number"
                min="1"
                max="9"
                class="owner-npc-count-input"
                :disabled="row.adding"
                @change="normalizeNpcCount(row.id, 9)"
              >
              <button
                type="button"
                class="owner-npc-count-btn"
                :disabled="row.adding || npcCounts[row.id] >= 9"
                @click="bumpNpcCount(row.id, 1, 9)"
              >
                +
              </button>
            </span>
            <button
              type="button"
              :disabled="row.adding"
              :style="confirmNpcStyle(row.btnColor, !row.adding)"
              @click="emitConfirmRule(row)"
            >
              {{ row.adding ? '提交中…' : '确认添加' }}
            </button>
            <span v-if="row.tip" style="flex:1 1 220px; color:#595959;">{{ row.tip }}</span>
          </div>

          <div style="display:flex; flex-wrap:wrap; align-items:center; gap:8px; font-size:12px;">
            <span style="min-width:148px; font-weight:600; color:#531dab;">自定义 NPC BOT_CUSTOM</span>
            <span style="display:inline-flex; align-items:center; gap:4px;">
              <button
                type="button"
                class="owner-npc-count-btn"
                :disabled="customBotAdding || npcCounts.custom <= 1"
                @click="bumpNpcCount('custom', -1, 9)"
              >
                −
              </button>
              <input
                v-model.number="npcCounts.custom"
                type="number"
                min="1"
                max="9"
                class="owner-npc-count-input"
                :disabled="customBotAdding"
                @change="normalizeNpcCount('custom', 9)"
              >
              <button
                type="button"
                class="owner-npc-count-btn"
                :disabled="customBotAdding || npcCounts.custom >= 9"
                @click="bumpNpcCount('custom', 1, 9)"
              >
                +
              </button>
            </span>
            <button
              type="button"
              :disabled="customBotAdding"
              :style="confirmNpcStyle('#531dab', !customBotAdding)"
              @click.stop="emitOpenCustomNpcDialog"
            >
              {{ customBotAdding ? '提交中…' : '配置并添加' }}
            </button>
            <span v-if="customBotAddedTip" style="flex:1 1 220px; color:#595959;">{{ customBotAddedTip }}</span>
          </div>

          <div style="display:flex; flex-wrap:wrap; align-items:center; gap:8px; font-size:12px;">
            <span style="min-width:148px; font-weight:600; color:#08979c;">DeepSeek BOT_LLM</span>
            <span style="display:inline-flex; align-items:center; gap:4px;">
              <button
                type="button"
                class="owner-npc-count-btn"
                :disabled="anyLlmBotSubmitting || npcCounts.llm <= 1"
                @click="bumpNpcCount('llm', -1, 9)"
              >
                −
              </button>
              <input
                v-model.number="npcCounts.llm"
                type="number"
                min="1"
                max="9"
                class="owner-npc-count-input"
                :disabled="anyLlmBotSubmitting"
                @change="normalizeNpcCount('llm', 9)"
              >
              <button
                type="button"
                class="owner-npc-count-btn"
                :disabled="anyLlmBotSubmitting || npcCounts.llm >= 9"
                @click="bumpNpcCount('llm', 1, 9)"
              >
                +
              </button>
            </span>
            <button
              type="button"
              :disabled="anyLlmBotSubmitting"
              :style="confirmNpcStyle('#08979c', !anyLlmBotSubmitting)"
              @click="$emit('confirm-add-npcs', { type: 'llm', count: clampCount(npcCounts.llm, 9) })"
            >
              {{ llmBotAdding ? '提交中…' : '确认添加' }}
            </button>
            <span v-if="llmBotAddedTip" style="flex:1 1 220px; color:#595959;">{{ llmBotAddedTip }}</span>
          </div>

          <div style="display:flex; flex-wrap:wrap; align-items:center; gap:8px; font-size:12px;">
            <span style="min-width:148px; font-weight:600; color:#006d75;">BOT_LLM_GLOBAL</span>
            <span style="display:inline-flex; align-items:center; gap:4px;">
              <button
                type="button"
                class="owner-npc-count-btn"
                :disabled="anyLlmBotSubmitting || npcCounts.llmGlobal <= 1"
                @click="bumpNpcCount('llmGlobal', -1, 9)"
              >
                −
              </button>
              <input
                v-model.number="npcCounts.llmGlobal"
                type="number"
                min="1"
                max="9"
                class="owner-npc-count-input"
                :disabled="anyLlmBotSubmitting"
                @change="normalizeNpcCount('llmGlobal', 9)"
              >
              <button
                type="button"
                class="owner-npc-count-btn"
                :disabled="anyLlmBotSubmitting || npcCounts.llmGlobal >= 9"
                @click="bumpNpcCount('llmGlobal', 1, 9)"
              >
                +
              </button>
            </span>
            <button
              type="button"
              :disabled="anyLlmBotSubmitting"
              :style="confirmNpcStyle('#006d75', !anyLlmBotSubmitting)"
              @click="$emit('confirm-add-npcs', { type: 'llmGlobal', count: clampCount(npcCounts.llmGlobal, 9) })"
            >
              {{ llmGlobalBotAdding ? '提交中…' : '确认添加' }}
            </button>
            <span v-if="llmGlobalBotAddedTip" style="flex:1 1 220px; color:#595959;">{{ llmGlobalBotAddedTip }}</span>
          </div>
        </div>
      </div>
      </section>

      <section class="game-owner-tool__room-mgmt" aria-label="移交房主与踢人">
      <div class="game-owner-tool__section-title">房间管理</div>
      <div class="game-owner-tool__mgmt-row">
        <button
          type="button"
          :style="tabStyle('transfer')"
          @click="$emit('update:ownerToolType', 'transfer')"
        >
          移交房主
        </button>
        <button
          type="button"
          :style="tabStyle('kick')"
          @click="$emit('update:ownerToolType', 'kick')"
        >
          踢出至观众席
        </button>
        <button
          type="button"
          class="dp-btn--owner-orange game-owner-tool__reveal-btn"
          @click="$emit('update:ownerRevealAll', !ownerRevealAll)"
        >
          {{ ownerRevealAll ? '关闭看穿' : '看穿底牌' }}
        </button>
      </div>

      <div class="game-owner-tool__mgmt-hint">
        <template v-if="ownerToolType === 'transfer'">
          移交对象：桌上本局真人（不含僵尸位）与观众席真人；不含机器人与房主。
        </template>
        <template v-else>
          踢人仅针对本局在座玩家（不含房主与僵尸位）。
        </template>
      </div>

      <div class="game-owner-tool__action-prompt">
        <span v-if="ownerToolType === 'transfer'">选择桌上玩家或观众成为新房主：</span>
        <span v-else>勾选一名或多名在座玩家，批量踢出本局并移至观众席：</span>
      </div>

      <div v-if="ownerActionPlayers.length === 0" class="game-owner-tool__empty">
        当前没有可操作的玩家。
      </div>
      <template v-else>
        <div v-if="ownerToolType === 'transfer'" class="game-owner-tool__select-wrap">
          <select
            :value="ownerActionTarget"
            class="game-owner-tool__select"
            @input="$emit('update:ownerActionTarget', $event.target.value)"
          >
            <option disabled value="">请选择玩家</option>
            <option
              v-for="p in ownerActionPlayers"
              :key="'owner-tool-' + p.nickname"
              :value="p.nickname"
            >
              {{ displayNickname(p.nickname) }}
            </option>
          </select>
        </div>
        <div v-else class="game-owner-tool__kick-block">
          <div class="game-owner-tool__kick-toolbar">
            <button
              type="button"
              class="game-owner-tool__mini-btn"
              @click="selectAllKickable"
            >
              全选
            </button>
            <button
              type="button"
              class="game-owner-tool__mini-btn"
              @click="clearKickSelection"
            >
              全不选
            </button>
            <span class="game-owner-tool__kick-count">已选 {{ kickSelectionNicknames.length }} 人</span>
          </div>
          <div class="game-owner-tool__kick-list">
            <label
              v-for="p in ownerActionPlayers"
              :key="'kick-row-' + p.nickname"
              class="game-owner-tool__kick-row"
            >
              <input
                type="checkbox"
                :checked="isKickNicknameSelected(p.nickname)"
                @change="onKickCheck(p.nickname, $event.target.checked)"
              >
              <span class="game-owner-tool__kick-name">{{ displayNickname(p.nickname) }}</span>
            </label>
          </div>
        </div>
      </template>
      </section>

      <div style="display:flex; justify-content:flex-end; gap:8px; margin-top:4px;">
        <button
          type="button"
          class="game-owner-tool__footer-btn game-owner-tool__footer-btn--ghost"
          @click="$emit('close')"
        >
          取消
        </button>
        <button
          v-if="ownerToolType === 'transfer'"
          type="button"
          :disabled="!ownerActionTarget"
          :style="confirmStyle(ownerActionTarget, '#722ed1')"
          @click="$emit('transfer-owner')"
        >
          确认移交
        </button>
        <button
          v-else
          type="button"
          :disabled="kickSelectionNicknames.length === 0"
          :style="confirmStyle(kickSelectionNicknames.length > 0, '#ff4d4f')"
          @click="$emit('kick-players', kickSelectionNicknames.slice())"
        >
          批量踢出（{{ kickSelectionNicknames.length }}）
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import { dpDisplayNickname } from '../utils/dpDisplayNickname'

export default {
  name: 'GameOwnerToolModal',
  data () {
    return {
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
      /** 踢人页多选（在座玩家 nickname） */
      kickSelectionNicknames: []
    }
  },
  watch: {
    ownerActionPlayers: {
      deep: true,
      handler () {
        this.pruneKickSelection()
      }
    }
  },
  props: {
    /** 嵌入 game.vue 底部抽屉时不使用遮罩层，由外层 sheet 承载 */
    embedded: { type: Boolean, default: false },
    visible: { type: Boolean, default: false },
    ownerRevealAll: { type: Boolean, default: false },
    ownerToolType: { type: String, default: 'transfer' },
    ownerActionTarget: { type: String, default: '' },
    ownerActionPlayers: { type: Array, default: function () { return [] } },
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
    anyLlmBotSubmitting () {
      return !!(this.llmBotAdding || this.llmGlobalBotAdding)
    },
    ruleNpcRows () {
      return [
        {
          id: 'fish',
          archetype: 'FISH',
          label: '新手猫 BOT_FISH',
          labelColor: '#d46b08',
          btnColor: '#faad14',
          adding: this.demoBotAdding,
          tip: this.demoBotAddedTip
        },
        {
          id: 'tag',
          archetype: 'TAG',
          label: '保守猫 BOT_TAG',
          labelColor: '#237804',
          btnColor: '#389e0d',
          adding: this.tagBotAdding,
          tip: this.tagBotAddedTip
        },
        {
          id: 'lag',
          archetype: 'LAG',
          label: '松凶猫 BOT_LAG',
          labelColor: '#c41d7f',
          btnColor: '#eb2f96',
          adding: this.lagBotAdding,
          tip: this.lagBotAddedTip
        },
        {
          id: 'nit',
          archetype: 'NIT',
          label: '胆小猫 BOT_NIT',
          labelColor: '#434343',
          btnColor: '#8c8c8c',
          adding: this.nitBotAdding,
          tip: this.nitBotAddedTip
        },
        {
          id: 'call',
          archetype: 'CALL',
          label: '头铁猫 BOT_CALL',
          labelColor: '#10239e',
          btnColor: '#2f54eb',
          adding: this.callBotAdding,
          tip: this.callBotAddedTip
        },
        {
          id: 'maniac',
          archetype: 'MANIAC',
          label: '激进猫 BOT_MANIAC',
          labelColor: '#cf1322',
          btnColor: '#f5222d',
          adding: this.maniacBotAdding,
          tip: this.maniacBotAddedTip
        }
      ]
    }
  },
  methods: {
    isKickNicknameSelected (nick) {
      return this.kickSelectionNicknames.indexOf(nick) >= 0
    },
    onKickCheck (nick, checked) {
      var i = this.kickSelectionNicknames.indexOf(nick)
      if (checked) {
        if (i < 0) this.kickSelectionNicknames.push(nick)
      } else if (i >= 0) {
        this.kickSelectionNicknames.splice(i, 1)
      }
    },
    selectAllKickable () {
      this.kickSelectionNicknames = this.ownerActionPlayers.map(function (p) {
        return p.nickname
      })
    },
    clearKickSelection () {
      this.kickSelectionNicknames = []
    },
    pruneKickSelection () {
      var allowed = {}
      for (var i = 0; i < this.ownerActionPlayers.length; i++) {
        allowed[this.ownerActionPlayers[i].nickname] = true
      }
      this.kickSelectionNicknames = this.kickSelectionNicknames.filter(function (n) {
        return !!allowed[n]
      })
    },
    displayNickname: dpDisplayNickname,
    clampCount (raw, max) {
      var n = parseInt(raw, 10)
      if (isNaN(n)) n = 1
      return Math.max(1, Math.min(max, n))
    },
    normalizeNpcCount (key, max) {
      this.npcCounts[key] = this.clampCount(this.npcCounts[key], max)
    },
    bumpNpcCount (key, delta, max) {
      this.npcCounts[key] = this.clampCount(this.clampCount(this.npcCounts[key], max) + delta, max)
    },
    emitConfirmRule (row) {
      if (!row || !row.id || !row.archetype) return
      this.$emit('confirm-add-npcs', {
        type: 'rule',
        archetype: row.archetype,
        count: this.clampCount(this.npcCounts[row.id], 9)
      })
    },
    emitOpenCustomNpcDialog () {
      this.$emit('confirm-add-npcs', {
        type: 'custom',
        count: this.clampCount(this.npcCounts.custom, 9)
      })
    },
    confirmNpcStyle (bg, enabled) {
      return {
        padding: '6px 12px',
        border: 'none',
        borderRadius: '4px',
        cursor: enabled ? 'pointer' : 'not-allowed',
        fontSize: '12px',
        fontWeight: 'bold',
        background: enabled ? bg : '#d9d9d9',
        color: '#fff'
      }
    },
    tabStyle(type) {
      var on = this.ownerToolType === type
      var color = type === 'transfer' ? '#722ed1' : '#ff4d4f'
      return {
        padding: '6px 10px',
        borderRadius: '4px',
        border: '1px solid ' + (on ? color : '#d9d9d9'),
        background: on ? (type === 'transfer' ? '#f9f0ff' : '#fff1f0') : '#fff',
        color: on ? color : '#333',
        cursor: 'pointer',
        fontSize: '12px',
        fontWeight: 'bold'
      }
    },
    confirmStyle(enabled, bg) {
      return {
        padding: '6px 12px',
        borderRadius: '4px',
        border: 'none',
        cursor: enabled ? 'pointer' : 'not-allowed',
        fontSize: '12px',
        fontWeight: 'bold',
        background: enabled ? bg : '#d9d9d9',
        color: '#fff'
      }
    }
  }
}
</script>

<style scoped>
.game-owner-tool__npc-section {
  margin-bottom: 16px;
}

.game-owner-tool__room-mgmt {
  margin-bottom: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--dp-subpanel-border, #e8e8e8);
}

.game-owner-tool__section-title {
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 700;
  color: var(--dp-text-primary, #252018);
}

.game-owner-tool__mgmt-hint {
  margin-bottom: 8px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--dp-text-secondary, #595959);
}

.game-owner-tool__action-prompt {
  margin-bottom: 10px;
  font-size: 13px;
  line-height: 1.45;
  color: var(--dp-text-primary, #252018);
}

.game-owner-tool__empty {
  margin-bottom: 8px;
  font-size: 13px;
  color: var(--dp-text-muted, #8c8c8c);
}

.game-owner-tool__select-wrap {
  margin-bottom: 12px;
}

.game-owner-tool__select {
  width: 100%;
  box-sizing: border-box;
  padding: 6px 8px;
  border-radius: 4px;
  border: 1px solid var(--dp-input-border, #d9d9d9);
  background: var(--dp-input-bg, #fff);
  color: var(--dp-text-primary, #252018);
  font-size: 13px;
}

.game-owner-tool__kick-block {
  margin-bottom: 12px;
}

.game-owner-tool__kick-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 12px;
  align-items: center;
}

.game-owner-tool__mini-btn {
  padding: 4px 10px;
  border-radius: 4px;
  border: 1px solid var(--dp-input-border, #d9d9d9);
  background: var(--dp-btn-ghost-bg, #fff);
  color: var(--dp-text-primary, #252018);
  cursor: pointer;
}

.game-owner-tool__mini-btn:hover {
  filter: brightness(1.05);
}

.game-owner-tool__kick-count {
  color: var(--dp-text-muted, #8c8c8c);
  line-height: 28px;
}

.game-owner-tool__kick-list {
  max-height: 200px;
  overflow-y: auto;
  padding: 8px;
  border: 1px solid var(--dp-subpanel-border, var(--dp-panel-border, #e8e8e8));
  border-radius: 6px;
  background: var(--dp-subpanel-bg, #f5f5f5);
  color: var(--dp-text-primary, #252018);
}

.game-owner-tool__kick-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 4px;
  cursor: pointer;
  font-size: 13px;
  color: inherit;
}

.game-owner-tool__kick-name {
  color: var(--dp-text-primary, #252018);
  font-weight: 500;
}

.game-owner-tool__footer-btn {
  padding: 6px 12px;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
}

.game-owner-tool__footer-btn--ghost {
  border: 1px solid var(--dp-input-border, #d9d9d9);
  background: var(--dp-btn-ghost-bg, #fff);
  color: var(--dp-text-primary, #252018);
}

.owner-npc-count-btn {
  width: 28px;
  height: 28px;
  padding: 0;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  font-size: 16px;
  line-height: 1;
  color: #333;
}
.owner-npc-count-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.owner-npc-count-input {
  width: 44px;
  height: 28px;
  text-align: center;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-size: 13px;
}
</style>
<style src="../styles/dp-game-modals.css"></style>
