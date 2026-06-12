<template>
  <div class="dp-custom-npc-console">
    <p class="dp-custom-npc-console__intro">
      CUSTOM × <strong>{{ pendingCount }}</strong> · shared profile
    </p>
    <ul class="dp-console-menu" role="menu" aria-label="自定义 NPC 六维参数">
      <li
        v-for="(field, i) in fields"
        :key="field.key"
        role="menuitem"
        :aria-selected="i === selectedIndex ? 'true' : 'false'"
        class="dp-console-menu__row"
        :class="rowClass(i)"
        @click="onRowTap(i)"
      >
        <div class="dp-console-menu__left">
          <span class="dp-console-menu__cursor" aria-hidden="true">&gt;</span>
          <span class="dp-console-menu__label">{{ field.hint.toUpperCase() }}</span>
        </div>
        <div class="dp-console-menu__right">
          <span class="dp-console-menu__adj">
            <button
              type="button"
              class="dp-console-menu__chev"
              :aria-label="field.hint + ' 减少'"
              :disabled="disabled || draft[field.key] <= 0"
              @click.stop="onAdjust(field.key, -1)"
            >
              &lt;
            </button>
            <button
              type="button"
              class="dp-console-menu__value-btn"
              :aria-label="field.hint + ' 当前值'"
              :disabled="disabled"
              @click.stop="onAdjust(field.key, 1)"
            >
              {{ formatValue(draft[field.key]) }}
            </button>
            <button
              type="button"
              class="dp-console-menu__chev"
              :aria-label="field.hint + ' 增加'"
              :disabled="disabled || draft[field.key] >= 1"
              @click.stop="onAdjust(field.key, 1)"
            >
              &gt;
            </button>
          </span>
        </div>
      </li>
    </ul>
    <p class="dp-console-footer" aria-hidden="true">
      <span class="dp-console-footer__touch">tap row · tap &lt;&gt; · OK below</span>
    </p>
  </div>
</template>

<script>
import '@/styles/dp-create-room-console.css'
import { shouldSkipRetroEnterEffects } from '@/utils/dpRetroEnterGameHandoff'
import {
  CUSTOM_NPC_STYLE_FIELDS,
  clampNpcStyle01,
  cloneNpcStyleProfile
} from '@/constants/npcStylePresets'

var STEP = 0.05

export default {
  name: 'DpCustomNpcConsole',
  props: {
    profile: {
      type: Object,
      default: function () {
        return null
      }
    },
    pendingCount: { type: Number, default: 1 },
    disabled: { type: Boolean, default: false }
  },
  data: function () {
    return {
      fields: CUSTOM_NPC_STYLE_FIELDS,
      selectedIndex: 0,
      draft: cloneNpcStyleProfile(this.profile)
    }
  },
  computed: {
    useRowBlink: function () {
      return !shouldSkipRetroEnterEffects()
    }
  },
  watch: {
    profile: {
      deep: true,
      handler: function (val) {
        this.draft = cloneNpcStyleProfile(val)
      }
    },
    draft: {
      deep: true,
      handler: function (val) {
        this.$emit('update:profile', cloneNpcStyleProfile(val))
      }
    }
  },
  methods: {
    formatValue: function (v) {
      return clampNpcStyle01(v).toFixed(2)
    },
    rowClass: function (i) {
      return {
        'dp-console-menu__row--selected': i === this.selectedIndex,
        'dp-console-menu__row--blink': i === this.selectedIndex && this.useRowBlink
      }
    },
    onRowTap: function (i) {
      if (this.disabled) return
      this.selectedIndex = i
    },
    onAdjust: function (key, delta) {
      if (this.disabled) return
      var cur = clampNpcStyle01(this.draft[key])
      var next = clampNpcStyle01(cur + delta * STEP)
      this.$set(this.draft, key, next)
    },
    getProfile: function () {
      var out = cloneNpcStyleProfile(this.draft)
      var keys = ['vpip', 'pfr', 'cbetFreq', 'bluffFreq', 'callStation', 'foldToPressure']
      for (var i = 0; i < keys.length; i++) {
        out[keys[i]] = clampNpcStyle01(out[keys[i]])
      }
      return out
    }
  }
}
</script>

<style scoped>
.dp-custom-npc-console {
  position: relative;
  z-index: 1;
}

.dp-custom-npc-console__intro {
  margin: 0 0 8px;
  padding: 0 4px;
  font-family: 'Courier New', ui-monospace, monospace;
  font-size: 10px;
  letter-spacing: 0.06em;
  text-align: center;
  color: rgba(74, 246, 38, 0.55);
  text-transform: uppercase;
}

.dp-custom-npc-console .dp-console-footer {
  display: block;
}

.dp-custom-npc-console .dp-console-footer__kb {
  display: none;
}

.dp-custom-npc-console .dp-console-footer__touch {
  display: inline;
}
</style>
