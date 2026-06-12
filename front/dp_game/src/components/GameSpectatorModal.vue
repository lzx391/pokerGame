<template>
  <transition name="dp-overlay">
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
        <p v-if="!spectatorRows.length" class="spectator-modal__empty">
          当前没有观众。
        </p>
        <ul v-else class="spectator-modal__list">
          <li
              v-for="row in spectatorRows"
              :key="row.nickname"
              class="spectator-modal__item"
          >
            <button
                type="button"
                class="spectator-modal__profile-btn"
                :aria-label="'查看 ' + row.displayName + ' 的资料'"
                @click="onSpectatorProfileClick(row)"
            >
              <dp-user-avatar
                  :avatar-url="row.avatarUrl"
                  :nickname="row.displayName"
                  :cache-bust="avatarCacheBustFromUpdatedAt(row.avatarUpdatedAt)"
                  size="sm"
              />
              <span class="spectator-modal__name">{{ row.displayName }}</span>
            </button>
          </li>
        </ul>
      </div>
    </div>
  </div>
  </transition>
</template>

<script>
import { mapState } from 'vuex'
import DpUserAvatar from '@/components/DpUserAvatar.vue'
import { dpDisplayNickname, isDpBotNickname } from '../utils/dpDisplayNickname'
import { resolveRoomPersonMeta } from '../utils/dpRoomPlayerLookup'
import { avatarCacheBustFromUpdatedAt } from '@/utils/dpAvatarUrl'

export default {
  name: 'GameSpectatorModal',
  components: { DpUserAvatar },
  inject: ['dpGameView'],
  props: {
    visible: { type: Boolean, default: false },
    spectators: { type: Array, default: function () { return [] } },
    players: { type: Array, default: function () { return [] } }
  },
  computed: {
    ...mapState('dpMailbox', ['friends']),
    spectatorRows: function () {
      var specs = this.spectators || []
      var players = this.players || []
      var friends = this.friends || []
      var out = []
      for (var i = 0; i < specs.length; i++) {
        var name = specs[i]
        if (!name || typeof name !== 'string') continue
        var meta = resolveRoomPersonMeta({
          players: players,
          friends: friends,
          nickname: name
        })
        out.push({
          nickname: name,
          displayName: dpDisplayNickname(name),
          userId: meta.userId,
          avatarUrl: meta.avatarUrl || '',
          avatarUpdatedAt: meta.avatarUpdatedAt
        })
      }
      return out
    }
  },
  methods: {
    avatarCacheBustFromUpdatedAt,
    onSpectatorProfileClick: function (row) {
      if (!row || !row.nickname) return
      var vm = this.dpGameView
      if (!vm || typeof vm.openPlayerSocialProfile !== 'function') return
      if (isDpBotNickname(row.nickname)) {
        if (vm.$message) vm.$message.info('机器人不支持该功能')
        return
      }
      vm.openPlayerSocialProfile({
        nickname: row.nickname,
        userId: row.userId || undefined
      })
    }
  }
}
</script>

<style src="../styles/dp-game-modals.css"></style>
