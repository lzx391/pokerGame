<template>
  <div class="dp-top-bar">
    <!-- 第一行：房间/阶段 + 底池跟注 + 设置（原三行合并为两行里的首行） -->
    <div class="dp-top-bar__row dp-top-bar__row--primary">
      <div class="dp-top-bar__primary-text">
        <span class="dp-top-bar__title">
          房间: {{ roomId }} | 阶段: <span class="dp-top-bar__accent">{{ stageLabel }}</span>
        </span>
        <span class="dp-top-bar__meta-sep" aria-hidden="true">·</span>
        <span class="dp-top-bar__sub">
          底池 <span class="dp-top-bar__pot">{{ pot }}</span>
          <span class="dp-top-bar__meta-sep dp-top-bar__meta-sep--thin" aria-hidden="true">|</span>
          跟注 <span class="dp-top-bar__bet">{{ currentBetToCall }}</span>
        </span>
      </div>
      <div ref="settingsRoot" class="dp-top-bar__settings-wrap">
        <button
            type="button"
            class="dp-btn dp-top-bar__btn dp-top-bar__btn--ghost dp-top-bar__settings-btn"
            :aria-expanded="settingsOpen ? 'true' : 'false'"
            aria-haspopup="true"
            aria-controls="dp-top-bar-settings-panel"
            @click.stop="toggleSettings"
        >
          设置
        </button>
        <div
            v-show="settingsOpen"
            id="dp-top-bar-settings-panel"
            class="dp-top-bar__settings-panel dp-game-theme-row"
            role="region"
            aria-label="对局显示设置"
            @click.stop
        >
          <span class="dp-game-theme-row__label">界面主题</span>
          <select
              class="dp-game-theme-select"
              aria-label="选择对局界面主题"
              :value="gameUiTheme"
              @change="onThemeChange($event.target.value)"
          >
            <option v-for="t in themeOptions" :key="t.id" :value="t.id">{{ t.label }}</option>
          </select>
          <label class="dp-game-eco-label">
            <input
                type="checkbox"
                :checked="ecoMode"
                aria-label="节能模式：减少动画与模糊效果"
                @change="$emit('update:ecoMode', $event.target.checked)"
            >
            节能模式
          </label>
        </div>
      </div>
    </div>
    <!-- 第二行：操作按钮 -->
    <div class="dp-top-bar__row dp-top-bar__row--actions">
      <div class="dp-top-bar__actions">
        <button
            type="button"
            class="dp-btn dp-top-bar__btn dp-top-bar__btn--ghost"
            :aria-pressed="isFullscreen ? 'true' : 'false'"
            @click="$emit('toggle-fullscreen')"
        >
          {{ isFullscreen ? '退出全屏' : '全屏' }}
        </button>
        <button type="button" class="dp-btn dp-btn--primary dp-top-bar__btn" @click="$emit('show-hand-rank')">
          牌型说明
        </button>
        <button
            v-if="spectatorCount > 0"
            type="button"
            class="dp-btn dp-btn--cyan dp-top-bar__btn"
            @click="$emit('show-spectators')"
        >
          观众席（{{ spectatorCount }}）
        </button>
        <button
            v-if="showSpectatorPrepare"
            type="button"
            class="dp-btn dp-btn--success dp-top-bar__btn"
            :disabled="nextHandReady"
            @click="$emit('ready-next-hand')"
        >
          {{ nextHandReady ? '已报名下一局' : '下一局加入对局' }}
        </button>
        <button type="button" class="dp-btn dp-btn--danger dp-top-bar__btn" @click="$emit('exit')">
          退出对局
        </button>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'GameTopBar',
  props: {
    roomId: { type: String, required: true },
    stageLabel: { type: String, required: true },
    pot: { type: Number, required: true },
    currentBetToCall: { type: Number, required: true },
    spectatorCount: { type: Number, default: 0 },
    isFullscreen: { type: Boolean, default: false },
    /** 纯观众或本手已退：在顶栏内展示报名下一局 */
    showSpectatorPrepare: { type: Boolean, default: false },
    nextHandReady: { type: Boolean, default: false },
    /** 与 game.vue 的 data-dp-game-theme 同步 */
    gameUiTheme: { type: String, required: true },
    ecoMode: { type: Boolean, required: true },
    themeOptions: {
      type: Array,
      default: function () {
        return []
      }
    }
  },
  data: function () {
    return {
      settingsOpen: false
    }
  },
  mounted: function () {
    this._closeSettingsOnOutside = function (e) {
      var root = this.$refs.settingsRoot
      if (!root || root.contains(e.target)) return
      this.settingsOpen = false
    }.bind(this)
    document.addEventListener('click', this._closeSettingsOnOutside)
  },
  beforeDestroy: function () {
    document.removeEventListener('click', this._closeSettingsOnOutside)
  },
  methods: {
    toggleSettings: function () {
      this.settingsOpen = !this.settingsOpen
    },
    onThemeChange: function (id) {
      this.$emit('update:gameUiTheme', id)
    }
  }
}
</script>
