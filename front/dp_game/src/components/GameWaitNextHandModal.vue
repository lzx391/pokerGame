<template>
  <div v-if="visible" class="hand-rank-modal-mask" @click="$emit('close')">
    <div
        class="hand-rank-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="dp-wait-next-hand-modal-title"
        @click.stop
    >
      <div class="dp-game-dialog__head">
        <span id="dp-wait-next-hand-modal-title" class="dp-game-dialog__title">等待玩家名单</span>
        <button
            type="button"
            class="dp-game-dialog__close"
            aria-label="关闭"
            @click="$emit('close')"
        >
          ×
        </button>
      </div>
      <div class="dp-game-dialog__body">
        <p class="wait-next-hand-modal__hint">
          已报名「下一局加入对局」的玩家；有空位时按下列顺序依次上桌。
        </p>
        <p v-if="!waitNextHand || waitNextHand.length === 0" class="spectator-modal__empty">
          当前没有等待上桌的玩家。
        </p>
        <ul v-else class="spectator-modal__list">
          <li
              v-for="(name, idx) in waitNextHand"
              :key="name + '-' + idx"
              class="spectator-modal__item wait-next-hand-modal__item"
          >
            <span class="wait-next-hand-modal__ord" aria-hidden="true">{{ idx + 1 }}</span>
            <span class="wait-next-hand-modal__name">{{ displayNickname(name) }}</span>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script>
import { dpDisplayNickname } from '../utils/dpDisplayNickname'

export default {
  name: 'GameWaitNextHandModal',
  props: {
    visible: { type: Boolean, default: false },
    waitNextHand: { type: Array, default: function () { return [] } }
  },
  methods: {
    displayNickname: dpDisplayNickname
  }
}
</script>

<style src="../styles/dp-game-modals.css"></style>
