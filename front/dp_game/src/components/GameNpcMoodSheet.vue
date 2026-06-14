<template>
  <transition name="dp-overlay">
    <div
        v-if="visible && target"
        class="hand-rank-modal-mask dp-npc-mood-modal-mask"
        role="presentation"
        @click="$emit('close')"
    >
      <div
          class="hand-rank-modal dp-npc-mood-crt"
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
    </div>
  </transition>
</template>

<script>
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
    }
  }
}
</script>

<style src="../styles/dp-game-modals.css"></style>

<style scoped>
.dp-npc-mood-modal-mask {
  z-index: var(--dp-z-npc-mood, 9100);
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

.dp-npc-mood-crt__bezel {
  padding: 10px;
  border: 3px solid #2a2a2a;
  border-radius: 6px;
  background: linear-gradient(180deg, #3a3a3a 0%, #222 100%);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.08),
    0 8px 24px rgba(0, 0, 0, 0.55);
}

.dp-npc-mood-crt__screen {
  position: relative;
  overflow: hidden;
  padding: 28px 18px 24px;
  border: 2px solid rgba(0, 0, 0, 0.45);
  background: #0a0c0e;
  box-shadow: inset 0 0 32px rgba(0, 0, 0, 0.65);
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
    rgba(0, 0, 0, 0.18) 2px,
    rgba(0, 0, 0, 0.18) 4px
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
  color: var(--dp-text-primary, #e8e8e8);
  word-break: break-word;
  max-width: 100%;
}

.dp-npc-mood-crt__name {
  font-size: 11px;
}
</style>

<style>
body[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__bezel,
.dp-game-root[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__bezel {
  border-radius: 0;
  border: 3px solid #1a1a1a;
  background: linear-gradient(180deg, #2e2e2e 0%, #181818 100%);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.06),
    0 6px 0 rgba(0, 0, 0, 0.55);
}

body[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__screen,
.dp-game-root[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__screen {
  border-radius: 0;
  border: 2px solid var(--dp-terminal-border-active, rgba(74, 246, 38, 0.28));
  background: rgba(0, 0, 0, 0.55);
  box-shadow:
    inset 0 0 24px rgba(74, 246, 38, 0.04),
    inset 0 0 48px rgba(0, 0, 0, 0.75);
}

body[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__close,
.dp-game-root[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__close {
  border-radius: 0;
  border: 1px solid rgba(74, 246, 38, 0.28);
  background: var(--dp-terminal-bg, #0a0c0e);
  color: var(--dp-accent, #4af626);
}

body[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__close:hover,
.dp-game-root[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__close:hover {
  background: var(--dp-accent, #4af626);
  color: #080a0c;
  filter: none;
}

body[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__line,
.dp-game-root[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__line {
  color: var(--dp-accent, #4af626);
  text-shadow: var(--dp-terminal-glow-text, 0 0 6px rgba(74, 246, 38, 0.55));
}

body[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__scanlines,
.dp-game-root[data-dp-game-theme='retro8bit'] .dp-npc-mood-crt__scanlines {
  opacity: 0.42;
  background: repeating-linear-gradient(
    0deg,
    transparent 0,
    transparent 2px,
    rgba(74, 246, 38, 0.04) 2px,
    rgba(74, 246, 38, 0.04) 4px
  );
}

body[data-dp-game-theme='retro8bit'] .hand-rank-modal-mask.dp-npc-mood-modal-mask,
.dp-game-root[data-dp-game-theme='retro8bit'] .hand-rank-modal-mask.dp-npc-mood-modal-mask {
  background: rgba(0, 0, 0, 0.65);
}
</style>
