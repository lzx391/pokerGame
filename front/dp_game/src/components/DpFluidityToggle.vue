<template>
  <label class="dp-game-eco-label" :class="labelClass">
    <input
      type="checkbox"
      :checked="ecoMode"
      aria-label="节能模式：减少动画与模糊效果"
      @change="onChange"
    >
    节能模式
  </label>
</template>

<script>
import { mapState } from 'vuex'

export default {
  name: 'DpFluidityToggle',
  props: {
    /** 附加 class，如大厅顶栏 `home-fluidity-toggle` */
    labelClass: { type: String, default: '' }
  },
  computed: {
    ...mapState('dpGame', ['ecoMode'])
  },
  methods: {
    onChange: function (e) {
      var on = !!(e && e.target && e.target.checked)
      this.$store.commit('dpGame/SET_ECO_MODE', on)
    }
  }
}
</script>
