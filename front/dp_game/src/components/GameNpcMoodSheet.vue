<template>
  <transition name="dp-overlay">
    <div
        v-if="visible && target"
        class="hand-rank-modal-mask dp-npc-mood-modal-mask"
        role="presentation"
        @click="$emit('close')"
    >
      <!-- retro8bit: CRT 中央弹窗 -->
      <div
          v-if="isRetroTheme"
          class="hand-rank-modal dp-npc-mood-crt"
          :class="crtMoodClass"
          role="dialog"
          aria-modal="true"
          :aria-label="ariaLabel"
          @click.stop
      >
        <button
            type="button"
            class="dp-npc-mood-crt__close"
            aria-label="关闭"
            @click="$emit('close')"
        >
          ×
        </button>

        <div class="dp-npc-mood-crt__bezel">
          <div class="dp-npc-mood-crt__screen">
            <div class="dp-npc-mood-crt__scanlines" aria-hidden="true"></div>
            <div class="dp-npc-mood-crt__content">
              <div class="dp-npc-mood-crt__line dp-npc-mood-crt__name">{{ displayName }}</div>

              <template v-if="target.isLlm">
                <div class="dp-npc-mood-crt__line">LLM NO MOOD</div>
              </template>

              <template v-else>
                <div class="dp-npc-mood-crt__line">{{ moodTierLabel }}</div>
                <div class="dp-npc-mood-crt__line">{{ moodValueText }}</div>
              </template>
            </div>
          </div>
        </div>
      </div>

      <!-- 其它主题：常规居中弹窗 -->
      <div
          v-else
          class="hand-rank-modal dp-npc-mood-standard"
          :class="standardMoodClass"
          role="dialog"
          aria-modal="true"
          :aria-label="ariaLabel"
          @click.stop
      >
        <div class="dp-game-dialog__head">
          <span class="dp-game-dialog__title">{{ displayName }}</span>
          <button
              type="button"
              class="dp-game-dialog__close"
              aria-label="关闭"
              @click="$emit('close')"
          >
            ×
          </button>
        </div>
        <div class="dp-game-dialog__body dp-npc-mood-standard__body">
          <template v-if="target.isLlm">
            <p class="dp-npc-mood-standard__line dp-npc-mood-standard__line--muted">LLM NO MOOD</p>
          </template>
          <template v-else>
            <p class="dp-npc-mood-standard__tier">{{ moodTierLabel }}</p>
            <p class="dp-npc-mood-standard__value">{{ moodValueText }}</p>
          </template>
        </div>
      </div>
    </div>
  </transition>
</template>

<script>
import { mapState } from 'vuex'
import { dpDisplayNickname } from '../utils/dpDisplayNickname'

var MOOD_TIER_LABELS = {
  HIGH: 'happy',
  NEUTRAL: 'calm',
  LOW: 'sad'
}

export default {
  name: 'GameNpcMoodSheet',
  props: {
    visible: { type: Boolean, default: false },
    target: {
      type: Object,
      default: null
    }
  },
  computed: {
    ...mapState('dpGame', ['gameUiTheme']),
    isRetroTheme: function () {
      return this.gameUiTheme === 'retro8bit'
    },
    displayName: function () {
      return dpDisplayNickname(this.target || {})
    },
    moodClamped: function () {
      var raw = this.target && this.target.mood != null ? Number(this.target.mood) : 0
      if (isNaN(raw)) return 0
      if (raw > 1) return 1
      if (raw < -1) return -1
      return raw
    },
    moodStateKey: function () {
      var state = this.target && this.target.moodState
      if (state && MOOD_TIER_LABELS[state]) return state
      return 'NEUTRAL'
    },
    moodTierLabel: function () {
      return MOOD_TIER_LABELS[this.moodStateKey]
    },
    moodValueText: function () {
      var v = this.moodClamped
      var sign = v > 0 ? '+' : ''
      return sign + v.toFixed(2)
    },
    ariaLabel: function () {
      if (this.target && this.target.isLlm) {
        return this.displayName + ' LLM NO MOOD'
      }
      return this.displayName + ' mood ' + this.moodTierLabel + ' ' + this.moodValueText
    },
    crtMoodClass: function () {
      if (this.target && this.target.isLlm) return 'crt--neutral'
      if (this.moodStateKey === 'HIGH') return 'crt--happy'
      if (this.moodStateKey === 'LOW') return 'crt--sad'
      return 'crt--calm'
    },
    standardMoodClass: function () {
      if (this.target && this.target.isLlm) return 'mood--neutral'
      if (this.moodStateKey === 'HIGH') return 'mood--happy'
      if (this.moodStateKey === 'LOW') return 'mood--sad'
      return 'mood--calm'
    }
  }
}
</script>

<style src="../styles/dp-game-modals.css"></style>

<style scoped>
.dp-npc-mood-modal-mask {
  z-index: var(--dp-z-npc-mood, 9100);
}

.dp-npc-mood-standard {
  max-width: min(92vw, 340px);
}

.dp-npc-mood-standard__body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding-top: 8px;
  padding-bottom: 24px;
  text-align: center;
}

.dp-npc-mood-standard__tier {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: lowercase;
  color: var(--dp-text-primary);
}

.dp-npc-mood-standard__value {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--dp-text-secondary);
}

.dp-npc-mood-standard__line {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--dp-text-primary);
}

.dp-npc-mood-standard__line--muted {
  color: var(--dp-text-muted);
}

.dp-npc-mood-standard.mood--happy .dp-npc-mood-standard__tier {
  color: var(--dp-danger, #e85c5c);
}

.dp-npc-mood-standard.mood--calm .dp-npc-mood-standard__tier {
  color: var(--dp-success, #5cb86a);
}

.dp-npc-mood-standard.mood--sad .dp-npc-mood-standard__tier {
  color: var(--dp-accent, #5c9ee8);
}

.dp-npc-mood-crt {
  position: relative;
  max-width: min(92vw, 340px);
  width: 100%;
  padding: 0;
  border-radius: 0;
  background: transparent;
  border: none;
  box-shadow: none;
  overflow: visible;
}

.dp-npc-mood-crt__close {
  position: absolute;
  top: -6px;
  right: -6px;
  z-index: 3;
  width: 32px;
  height: 32px;
  padding: 0;
  border: 2px solid var(--dp-subpanel-border, rgba(255, 255, 255, 0.2));
  border-radius: 0;
  background: var(--dp-subpanel-bg, #12151a);
  color: var(--dp-text-primary, #fff);
  font-family: var(--dp-font-pixel, monospace);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
}

.dp-npc-mood-crt__close:hover {
  filter: brightness(1.12);
}

.dp-npc-mood-crt.crt--happy {
  --dp-mood-crt-tint: #e85c5c;
  --dp-mood-crt-tint-rgb: 232, 92, 92;
  --dp-mood-crt-glow: 0 0 8px rgba(232, 92, 92, 0.45);
}

.dp-npc-mood-crt.crt--calm {
  --dp-mood-crt-tint: #5cb86a;
  --dp-mood-crt-tint-rgb: 92, 184, 106;
  --dp-mood-crt-glow: 0 0 8px rgba(92, 184, 106, 0.45);
}

.dp-npc-mood-crt.crt--sad {
  --dp-mood-crt-tint: #5c9ee8;
  --dp-mood-crt-tint-rgb: 92, 158, 232;
  --dp-mood-crt-glow: 0 0 8px rgba(92, 158, 232, 0.45);
}

.dp-npc-mood-crt.crt--neutral {
  --dp-mood-crt-tint: #a8adb8;
  --dp-mood-crt-tint-rgb: 168, 173, 184;
  --dp-mood-crt-glow: none;
}

.dp-npc-mood-crt__bezel {
  padding: 10px;
  border: 3px solid #2a2a2a;
  border-radius: 6px;
  background: linear-gradient(180deg, #3a3a3a 0%, #222 100%);
  box-shadow:
    inset 0 1px 0 rgba(var(--dp-mood-crt-tint-rgb, 255, 255, 255), 0.08),
    0 8px 24px rgba(0, 0, 0, 0.55);
}

.dp-npc-mood-crt__screen {
  position: relative;
  overflow: hidden;
  padding: 28px 18px 24px;
  border: 2px solid rgba(var(--dp-mood-crt-tint-rgb, 0, 0, 0), 0.35);
  background: #0a0c0e;
  box-shadow:
    inset 0 0 32px rgba(0, 0, 0, 0.65),
    inset 0 0 20px rgba(var(--dp-mood-crt-tint-rgb, 0, 0, 0), 0.06);
}

.dp-npc-mood-crt__scanlines {
  position: absolute;
  inset: 0;
  z-index: 0;
  pointer-events: none;
  opacity: 0.28;
  background: repeating-linear-gradient(
    0deg,
    transparent 0,
    transparent 2px,
    rgba(var(--dp-mood-crt-tint-rgb, 0, 0, 0), 0.14) 2px,
    rgba(var(--dp-mood-crt-tint-rgb, 0, 0, 0), 0.14) 4px
  );
}

.dp-npc-mood-crt__content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  text-align: center;
}

.dp-npc-mood-crt__line {
  font-family: var(--dp-font-pixel, 'Press Start 2P', monospace);
  font-size: 10px;
  line-height: 1.65;
  letter-spacing: 0.04em;
  color: var(--dp-mood-crt-tint, var(--dp-text-primary, #e8e8e8));
  text-shadow: var(--dp-mood-crt-glow, none);
  word-break: break-word;
  max-width: 100%;
}

.dp-npc-mood-crt__name {
  font-size: 11px;
}
</style>

<style>
body[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt.crt--happy,
.dp-game-root[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt.crt--happy {
  --dp-mood-crt-tint: #ff5555;
  --dp-mood-crt-tint-rgb: 255, 85, 85;
  --dp-mood-crt-glow: 0 0 6px rgba(255, 85, 85, 0.55);
}

body[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt.crt--calm,
.dp-game-root[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt.crt--calm {
  --dp-mood-crt-tint: #4af626;
  --dp-mood-crt-tint-rgb: 74, 246, 38;
  --dp-mood-crt-glow: 0 0 6px rgba(74, 246, 38, 0.55);
}

body[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt.crt--sad,
.dp-game-root[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt.crt--sad {
  --dp-mood-crt-tint: #55aaff;
  --dp-mood-crt-tint-rgb: 85, 170, 255;
  --dp-mood-crt-glow: 0 0 6px rgba(85, 170, 255, 0.55);
}

body[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt.crt--neutral,
.dp-game-root[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt.crt--neutral {
  --dp-mood-crt-tint: #9aa0a6;
  --dp-mood-crt-tint-rgb: 154, 160, 166;
  --dp-mood-crt-glow: 0 0 4px rgba(154, 160, 166, 0.35);
}

body[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__bezel,
.dp-game-root[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__bezel {
  border-radius: 0;
  border: 3px solid #1a1a1a;
  background: linear-gradient(180deg, #2e2e2e 0%, #181818 100%);
  box-shadow:
    inset 0 1px 0 rgba(var(--dp-mood-crt-tint-rgb), 0.06),
    0 6px 0 rgba(0, 0, 0, 0.55);
}

body[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__screen,
.dp-game-root[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__screen {
  border-radius: 0;
  border: 2px solid rgba(var(--dp-mood-crt-tint-rgb), 0.28);
  background: rgba(0, 0, 0, 0.55);
  box-shadow:
    inset 0 0 24px rgba(var(--dp-mood-crt-tint-rgb), 0.04),
    inset 0 0 48px rgba(0, 0, 0, 0.75);
}

body[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__close,
.dp-game-root[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__close {
  border-radius: 0;
  border: 1px solid rgba(var(--dp-mood-crt-tint-rgb), 0.28);
  background: var(--dp-terminal-bg, #0a0c0e);
  color: var(--dp-mood-crt-tint);
}

body[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__close:hover,
.dp-game-root[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__close:hover {
  background: var(--dp-mood-crt-tint);
  color: #080a0c;
  filter: none;
}

body[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__line,
.dp-game-root[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__line {
  color: var(--dp-mood-crt-tint);
  text-shadow: var(--dp-mood-crt-glow);
}

body[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__scanlines,
.dp-game-root[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__scanlines {
  opacity: 0.42;
  background: repeating-linear-gradient(
    0deg,
    transparent 0,
    transparent 2px,
    rgba(var(--dp-mood-crt-tint-rgb), 0.04) 2px,
    rgba(var(--dp-mood-crt-tint-rgb), 0.04) 4px
  );
}

body[data-dp-game-theme='retro8bit'] .hand-rank-modal-mask.dp-npc-mood-modal-mask,
.dp-game-root[data-dp-game-theme='retro8bit'] .hand-rank-modal-mask.dp-npc-mood-modal-mask {
  background: rgba(0, 0, 0, 0.65);
}
</style>
