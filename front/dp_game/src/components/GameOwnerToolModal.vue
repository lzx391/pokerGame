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
      <div v-else class="game-owner-tool-embedded__title">房间管理 · 机器人与踢人</div>

      <div style="margin-bottom:12px; font-size:13px; color:#666;">
        <template v-if="ownerToolType === 'transfer'">
          移交对象：桌上本局真人（不含僵尸位）与观众席真人；不含机器人与房主。
        </template>
        <template v-else>
          踢人仅针对本局在座玩家（不含房主与僵尸位）。
        </template>
      </div>

      <div style="margin-bottom:12px; padding:8px; border-radius:6px; background:#fff7e6; border:1px dashed #ffa940;">
        <div style="font-size:13px; font-weight:bold; color:#d46b08; margin-bottom:4px;">
          实验功能：加入演示 NPC
        </div>
        <div style="font-size:12px; color:#8c8c8c; margin-bottom:6px;">
          你可以在下一局加入不同风格的机器人玩家，当前支持：
          <span style="font-weight:bold;">BOT_Lag</span>（简单鱼式，偏被动，适合新手练习）、
          <span style="font-weight:bold;">BOT_Maniac</span>（激进风格，喜欢乱加投）、
          <span style="font-weight:bold;">BOT_Tag</span>（紧凶风格，范围较紧、偏价值投入）、
          <span style="font-weight:bold;">BOT_Shark</span>（会简单“读对手”的聪明型）、
          <span style="font-weight:bold;">BOT_LLM</span>（大模型决策，需在服务端配置方舟密钥与接入点）。
        </div>
        <div style="display:flex; flex-direction:column; gap:6px;">
          <div>
            <button
              type="button"
              :disabled="demoBotAdding"
              style="padding:6px 10px; border:none; border-radius:4px; cursor:pointer; font-size:12px; font-weight:bold;
                     background:#faad14; color:#fff; margin-right:8px;"
              @click="$emit('add-demo-bot')"
            >
              {{ demoBotAdding ? '正在添加 BOT_Lag...' : '添加 BOT_Lag 到下一局' }}
            </button>
            <span v-if="demoBotAddedTip" style="font-size:12px; color:#595959;">{{ demoBotAddedTip }}</span>
          </div>
          <div>
            <button
              type="button"
              :disabled="maniacBotAdding"
              style="padding:6px 10px; border:none; border-radius:4px; cursor:pointer; font-size:12px; font-weight:bold;
                     background:#f5222d; color:#fff; margin-right:8px;"
              @click="$emit('add-maniac-bot')"
            >
              {{ maniacBotAdding ? '正在添加 BOT_Maniac...' : '添加疯子 BOT_Maniac 到下一局' }}
            </button>
            <span v-if="maniacBotAddedTip" style="font-size:12px; color:#595959;">{{ maniacBotAddedTip }}</span>
          </div>
          <div>
            <button
              type="button"
              :disabled="tagBotAdding"
              style="padding:6px 10px; border:none; border-radius:4px; cursor:pointer; font-size:12px; font-weight:bold;
                     background:#389e0d; color:#fff; margin-right:8px;"
              @click="$emit('add-tag-bot')"
            >
              {{ tagBotAdding ? '正在添加 BOT_Tag...' : '添加紧凶 BOT_Tag 到下一局' }}
            </button>
            <span v-if="tagBotAddedTip" style="font-size:12px; color:#595959;">{{ tagBotAddedTip }}</span>
          </div>
          <div>
            <button
              type="button"
              :disabled="sharkBotAdding"
              style="padding:6px 10px; border:none; border-radius:4px; cursor:pointer; font-size:12px; font-weight:bold;
                     background:#722ed1; color:#fff; margin-right:8px;"
              @click="$emit('add-shark-bot')"
            >
              {{ sharkBotAdding ? '正在添加 BOT_Shark...' : '添加聪明 BOT_Shark 到下一局' }}
            </button>
            <span v-if="sharkBotAddedTip" style="font-size:12px; color:#595959;">{{ sharkBotAddedTip }}</span>
          </div>
          <div>
            <button
              type="button"
              :disabled="llmBotAdding"
              style="padding:6px 10px; border:none; border-radius:4px; cursor:pointer; font-size:12px; font-weight:bold;
                     background:#08979c; color:#fff; margin-right:8px;"
              @click="$emit('add-llm-bot')"
            >
              {{ llmBotAdding ? '正在添加 BOT_LLM...' : '添加大模型 BOT_LLM 到下一局' }}
            </button>
            <span v-if="llmBotAddedTip" style="font-size:12px; color:#595959;">{{ llmBotAddedTip }}</span>
          </div>
        </div>
      </div>

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

      <div style="margin-bottom:10px; font-size:13px; color:#333;">
        <span v-if="ownerToolType === 'transfer'">选择桌上玩家或观众成为新房主：</span>
        <span v-else>选择一名玩家踢出本局并移至观众席：</span>
      </div>

      <div v-if="ownerActionPlayers.length === 0" style="font-size:13px; color:#999;">
        当前没有可操作的玩家。
      </div>
      <div v-else style="margin-bottom:12px;">
        <select
          :value="ownerActionTarget"
          style="width:100%; padding:6px 8px; border-radius:4px; border:1px solid #d9d9d9; font-size:13px;"
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

      <div style="display:flex; justify-content:flex-end; gap:8px; margin-top:4px;">
        <button
          type="button"
          style="padding:6px 12px; border-radius:4px; border:1px solid #d9d9d9; background:#fff; cursor:pointer; font-size:12px;"
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
          :disabled="!ownerActionTarget"
          :style="confirmStyle(ownerActionTarget, '#ff4d4f')"
          @click="$emit('kick-player')"
        >
          确认踢出
        </button>
      </div>
    </div>
  </div>
</template>

<script>
import { dpDisplayNickname } from '../utils/dpDisplayNickname'

export default {
  name: 'GameOwnerToolModal',
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
    sharkBotAdding: { type: Boolean, default: false },
    sharkBotAddedTip: { type: String, default: '' },
    llmBotAdding: { type: Boolean, default: false },
    llmBotAddedTip: { type: String, default: '' }
  },
  methods: {
    displayNickname: dpDisplayNickname,
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

<style src="../styles/dp-game-modals.css"></style>
