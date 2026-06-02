<template>
  <svg
      class="dp-retro-table-fx"
      viewBox="0 0 100 100"
      preserveAspectRatio="none"
      aria-hidden="true"
  >
    <path
        v-if="edgePathD"
        :d="edgePathD"
        class="dp-retro-table-fx__edge"
        :class="{ 'dp-retro-table-fx__edge--animate': animated }"
        pathLength="100"
    />
  </svg>
</template>

<script>
import { retroTableEdgePathD } from '../utils/dpRetroTableFxGeometry'

export default {
  name: 'DpRetroTableFx',
  props: {
    layout: { type: Object, default: null },
    animated: { type: Boolean, default: false }
  },
  computed: {
    edgePathD: function () {
      if (!this.layout || !this.layout.vertices) return ''
      return retroTableEdgePathD(this.layout.vertices)
    }
  }
}
</script>
