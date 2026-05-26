<template>
  <div class="dp-button-guide-overlay" aria-live="polite">
    <!-- 完成步：全屏卡片 -->
    <div
      v-if="isCompleteStep"
      class="dp-button-guide-overlay__complete"
      role="dialog"
      aria-labelledby="dp-guide-complete-title"
      @click="$emit('finish')"
    >
      <div class="dp-button-guide-overlay__complete-card" @click.stop>
        <h2 id="dp-guide-complete-title">{{ stepTitle }}</h2>
        <p>{{ stepBody }}</p>
        <p class="dp-button-guide-overlay__complete-note">{{ stepNote }}</p>
        <div class="dp-button-guide-overlay__actions" style="justify-content: center; margin-top: 16px;">
          <button type="button" class="dp-btn dp-btn--primary" @click="$emit('finish')">
            返回大厅
          </button>
        </div>
      </div>
    </div>

    <template v-else>
      <!-- 空白区域点击 → 下一步（四块遮罩围出镂空） -->
      <div
        v-for="(region, idx) in shadeRegions"
        :key="'shade-' + idx"
        class="dp-button-guide-overlay__shade"
        :style="regionStyle(region)"
        aria-hidden="true"
        @click="onBlankTap"
      />

      <div
        v-if="holeStyle"
        class="dp-button-guide-overlay__hole"
        :style="holeStyle"
        aria-hidden="true"
      />

      <div
        v-if="bubbleStyle"
        class="dp-button-guide-overlay__bubble-wrap"
        :style="bubbleStyle"
        role="dialog"
        :aria-label="stepTitle"
      >
        <div class="dp-button-guide-overlay__bubble" @click.stop>
          <div class="dp-button-guide-overlay__step-tag">
            第 {{ stepIndex + 1 }} / {{ totalSteps }} 步
          </div>
          <div class="dp-button-guide-overlay__title">{{ stepTitle }}</div>
          <p class="dp-button-guide-overlay__body">{{ stepBody }}</p>
          <p v-if="tapBlankToNext" class="dp-button-guide-overlay__tap-hint">
            点击屏幕空白处继续
          </p>
          <div class="dp-button-guide-overlay__actions">
            <button
              type="button"
              class="dp-btn dp-btn--ghost"
              @click="$emit('skip')"
            >
              跳过
            </button>
            <button
              v-if="stepIndex > 0"
              type="button"
              class="dp-btn dp-btn--ghost"
              @click="$emit('prev')"
            >
              上一步
            </button>
            <button
              type="button"
              class="dp-btn dp-btn--primary"
              @click="$emit('next')"
            >
              下一步
            </button>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script>
export default {
  name: 'ButtonGuideSpotlight',
  props: {
    stepIndex: { type: Number, required: true },
    totalSteps: { type: Number, required: true },
    stepTitle: { type: String, default: '' },
    stepBody: { type: String, default: '' },
    stepNote: { type: String, default: '' },
    isCompleteStep: { type: Boolean, default: false },
    isLastHighlightStep: { type: Boolean, default: false },
    spotlightRect: { type: Object, default: null },
    spotlightPad: { type: Number, default: 6 },
    tapBlankToNext: { type: Boolean, default: true }
  },
  computed: {
    holePad: function () {
      return Math.max(4, Number(this.spotlightPad) || 6)
    },
    holeStyle: function () {
      var r = this.spotlightRect
      if (!r || r.width <= 0 || r.height <= 0) return null
      var pad = this.holePad
      return {
        top: (r.top - pad) + 'px',
        left: (r.left - pad) + 'px',
        width: (r.width + pad * 2) + 'px',
        height: (r.height + pad * 2) + 'px'
      }
    },
    holeBox: function () {
      var r = this.spotlightRect
      if (!r || r.width <= 0 || r.height <= 0) return null
      var pad = this.holePad
      return {
        top: r.top - pad,
        left: r.left - pad,
        width: r.width + pad * 2,
        height: r.height + pad * 2
      }
    },
    shadeRegions: function () {
      var vw = typeof window !== 'undefined' ? window.innerWidth : 360
      var vh = typeof window !== 'undefined' ? window.innerHeight : 640
      var box = this.holeBox
      if (!box) {
        return [{ top: 0, left: 0, width: vw, height: vh }]
      }
      var t = box.top
      var l = box.left
      var w = box.width
      var h = box.height
      var regions = []
      if (t > 0) regions.push({ top: 0, left: 0, width: vw, height: t })
      if (h > 0 && l > 0) regions.push({ top: t, left: 0, width: l, height: h })
      var rightW = vw - l - w
      if (h > 0 && rightW > 0) regions.push({ top: t, left: l + w, width: rightW, height: h })
      var bottomTop = t + h
      var bottomH = vh - bottomTop
      if (bottomH > 0) regions.push({ top: bottomTop, left: 0, width: vw, height: bottomH })
      return regions.length ? regions : [{ top: 0, left: 0, width: vw, height: vh }]
    },
    bubbleStyle: function () {
      var r = this.spotlightRect
      var vw = typeof window !== 'undefined' ? window.innerWidth : 360
      var vh = typeof window !== 'undefined' ? window.innerHeight : 640
      var maxW = Math.min(360, vw * 0.92)
      var margin = 12
      var top
      var left
      if (r && r.width > 0) {
        left = Math.max(margin, Math.min(r.left, vw - maxW - margin))
        var below = r.top + r.height + margin
        if (below + 200 < vh) {
          top = below
        } else {
          top = Math.max(margin, r.top - 200 - margin)
        }
      } else {
        top = Math.max(margin, (vh - 200) / 2)
        left = Math.max(margin, (vw - maxW) / 2)
      }
      return {
        top: top + 'px',
        left: left + 'px',
        width: maxW + 'px'
      }
    }
  },
  methods: {
    regionStyle: function (region) {
      return {
        top: region.top + 'px',
        left: region.left + 'px',
        width: region.width + 'px',
        height: region.height + 'px'
      }
    },
    onBlankTap: function () {
      if (this.tapBlankToNext) this.$emit('next')
    }
  }
}
</script>
