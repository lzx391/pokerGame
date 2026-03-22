<template>
  <div
    :class="{ 'player-card--win-streak': !player.leftThisHand && (player.winStreak || 0) >= 2 }"
    :style="boxStyle"
    @click="onClick"
  >
    <div style="display:flex; flex-wrap:wrap; gap:5px; margin-bottom:5px; align-items:center;">
      <span
        v-if="player.dealer"
        style="background:#faad14; color:#fff; padding:1px 5px; border-radius:3px; font-size:12px;"
      >D</span>
      <span
        v-if="player.blind === 1"
        style="background:#722ed1; color:#fff; padding:1px 5px; border-radius:3px; font-size:12px;"
      >SB</span>
      <span
        v-if="player.blind === 2"
        style="background:#52c41a; color:#fff; padding:1px 5px; border-radius:3px; font-size:12px;"
      >BB</span>
      <span
        v-if="!player.leftThisHand && (player.winStreak || 0) >= 2"
        class="win-streak-badge"
        :title="'已连续赢下 ' + (player.winStreak || 0) + ' 手'"
      >
        <span class="win-streak-badge__emoji" aria-hidden="true">🔥</span>
        <span class="win-streak-badge__text">{{ player.winStreak }}连胜</span>
      </span>
    </div>

    <template v-if="player.leftThisHand">
      <div style="font-weight:bold; color:#8c8c8c; font-size:14px;">
        该玩家已离线
      </div>
      <div style="margin-top:8px; font-size:12px; color:#bfbfbf;">
        座位保留至本局结束，行动顺序不变
      </div>
    </template>

    <template v-else>
      <div style="font-weight:bold;">
        {{ player.nickname }}
        <span v-if="isMe" style="color:#1890ff;">(我)</span>
      </div>

      <div style="margin-top: 8px; display: flex; flex-direction: column; gap: 4px;">
        <div
          style="font-size: 13px; color: #555; display: flex; align-items: center; justify-content: center; background: #f8f9fa; border-radius: 4px; padding: 2px 0;"
        >
          <span style="color: #8c8c8c; margin-right: 4px;">剩余积分:</span>
          <span style="font-weight: 800; font-family: monospace; color: #2f3542;">{{ player.chips }}</span>
        </div>

        <div
          style="background: #fff2f0; border: 1px solid #ffccc7; border-radius: 6px; padding: 4px 0; text-align: center;"
        >
          <div
            style="font-size: 11px; color: #ff4d4f; text-transform: uppercase; font-weight: bold; letter-spacing: 0.5px;"
          >
            本轮积分
          </div>
          <div style="font-size: 16px; color: #cf1322; font-weight: 900; font-family: 'Arial Black', sans-serif;">
            {{ player.bet }}
          </div>
        </div>
      </div>

      <div style="display:flex; gap:5px; margin:8px 0; justify-content:center;">
        <template
          v-if="
            isMe
              || (isOwner && ownerRevealAll && player.holeCards && player.holeCards.length > 0)
              || ((stage === 'showdown' || stage === 'settled') && !player.fold)
          "
        >
          <div
            v-for="(c, ci) in player.holeCards"
            :key="'h' + ci"
            :class="[getCardClass(c), 'hole-card-flip']"
            :style="{
              width: '36px',
              height: '52px',
              fontSize: '13px',
              animationDelay: (ci * 0.08) + 's'
            }"
          >
            {{ getCardDisplay(c) }}
          </div>
        </template>
        <template v-else-if="player.holeCards && player.holeCards.length > 0">
          <div
            v-for="ci in player.holeCards.length"
            :key="'hb' + ci"
            class="card-base bg-gray"
            style="width:36px; height:52px; font-size:13px;"
          >?</div>
        </template>
      </div>

      <div
        v-if="
          communityCards.length >= 3
            && communityCardsFlipComplete
            && (isMe
              || (isOwner && ownerRevealAll && player.holeCards && player.holeCards.length > 0)
              || ((stage === 'showdown' || stage === 'settled') && !player.fold))
        "
        style="margin-top:4px; text-align:center;"
      >
        <template v-if="isMe || (isOwner && ownerRevealAll && player.holeCards && player.holeCards.length > 0)">
          <span
            style="background:#e6f7ff; color:#1890ff; padding:3px 10px; border-radius:4px; font-weight:bold; font-size:12px; display:inline-block;"
          >
            {{ getHandRank(player.holeCards, communityCards) }}
          </span>
          <div
            v-if="player.bestHandCards && player.bestHandCards.length === 5"
            class="best-hand-cards"
            style="display:flex; gap:4px; justify-content:center; margin-top:6px; flex-wrap:wrap;"
          >
            <div
              v-for="(c, ci) in player.bestHandCards"
              :key="'best' + ci"
              :class="[getCardClass(c), 'best-hand-card', 'best-hand-card-enter']"
              :style="{
                width: '32px',
                height: '46px',
                fontSize: '11px',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                borderRadius: '4px',
                animationDelay: (ci * 0.07) + 's'
              }"
            >
              {{ getCardDisplay(c) }}
            </div>
          </div>
        </template>
        <template v-else-if="(stage === 'showdown' || stage === 'settled') && !player.fold">
          <span
            style="background:#f6ffed; color:#52c41a; padding:3px 10px; border-radius:4px; font-weight:bold; font-size:12px; display:inline-block;"
          >
            {{ getHandRank(player.holeCards, communityCards) }}
          </span>
          <div
            v-if="player.bestHandCards && player.bestHandCards.length === 5"
            class="best-hand-cards"
            style="display:flex; gap:4px; justify-content:center; margin-top:6px; flex-wrap:wrap;"
          >
            <div
              v-for="(c, ci) in player.bestHandCards"
              :key="'best' + ci"
              :class="[getCardClass(c), 'best-hand-card', 'best-hand-card-enter']"
              :style="{
                width: '32px',
                height: '46px',
                fontSize: '11px',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
                borderRadius: '4px',
                animationDelay: (ci * 0.07) + 's'
              }"
            >
              {{ getCardDisplay(c) }}
            </div>
          </div>
        </template>
      </div>

      <div
        style="font-weight:bold; font-size:12px; margin-top:4px;"
        :style="{ color: player.fold ? '#ff4d4f' : (actIndex === seatIndex ? '#faad14' : '#52c41a') }"
      >
        {{ player.fold ? '已弃牌' : (actIndex === seatIndex ? '思考中...' : '进行中') }}
      </div>
    </template>
  </div>
</template>

<script>
import { getCardClass, getCardDisplay } from '../utils/dpGameCardVisual'
import { getHandRank } from '../utils/dpGameHandRank'

export default {
  name: 'GamePlayerCard',
  props: {
    player: { type: Object, required: true },
    seatIndex: { type: Number, required: true },
    boxStyle: { type: Object, required: true },
    actIndex: { type: Number, required: true },
    stage: { type: String, required: true },
    communityCards: { type: Array, required: true },
    communityCardsFlipComplete: { type: Boolean, required: true },
    isOwner: { type: Boolean, required: true },
    ownerRevealAll: { type: Boolean, required: true },
    myNickname: { type: String, default: '' }
  },
  computed: {
    isMe() {
      return !!this.myNickname && this.player.nickname === this.myNickname
    }
  },
  methods: {
    getCardClass,
    getCardDisplay,
    getHandRank,
    onClick() {
      if (!this.player.leftThisHand) {
        this.$emit('card-click', this.player.nickname)
      }
    }
  }
}
</script>

<style src="../styles/dp-poker-cards.css"></style>
