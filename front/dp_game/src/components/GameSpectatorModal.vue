<template>
  <div v-if="visible" class="hand-rank-modal-mask" @click="$emit('close')">
    <div class="hand-rank-modal" role="dialog" aria-modal="true" aria-labelledby="dp-spectator-modal-title" @click.stop>
      <div class="dp-game-dialog__head">
        <span id="dp-spectator-modal-title" class="dp-game-dialog__title">观众席名单</span>
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
        <p v-if="!spectators || spectators.length === 0" class="spectator-modal__empty">
          当前没有观众。
        </p>
        <ul v-else class="spectator-modal__list">
          <li
              v-for="name in spectators"
              :key="name"
              class="spectator-modal__item"
          >
            {{ displayNickname(name) }}
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>

<script>
import { dpDisplayNickname } from '../utils/dpDisplayNickname'

export default {
  name: 'GameSpectatorModal',
  props: {
    visible: { type: Boolean, default: false },
    spectators: { type: Array, default: function () { return [] } }
  },
  methods: {
    displayNickname: dpDisplayNickname
  }
}
</script>

<style src="../styles/dp-game-modals.css"></style>
