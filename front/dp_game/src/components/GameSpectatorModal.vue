<template>
  <div v-if="visible" class="hand-rank-modal-mask" @click="$emit('close')">
    <div class="hand-rank-modal" @click.stop>
      <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:15px;">
        <span style="font-size:18px; font-weight:bold;">观众席名单</span>
        <button
          type="button"
          style="background:#d9d9d9; border:none; width:28px; height:28px; border-radius:4px; cursor:pointer; font-size:16px; line-height:1;"
          @click="$emit('close')"
        >×</button>
      </div>
      <div v-if="!spectators || spectators.length === 0" style="font-size:13px; color:#999;">
        当前没有观众。
      </div>
      <ul v-else style="list-style:none; padding:0; margin:0;">
        <li
          v-for="name in spectators"
          :key="name"
          style="padding:6px 0; border-bottom:1px solid #f0f0f0; font-size:14px; color:#333;"
        >
          {{ displayNickname(name) }}
        </li>
      </ul>
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
