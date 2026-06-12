<template>
  <svg
      class="dp-retro-table-fx"
      :class="{ 'dp-retro-table-fx--alert': edgeAlert }"
      viewBox="0 0 100 100"
      preserveAspectRatio="none"
      aria-hidden="true"
  >
    <path
        v-if="borderInnerD"
        :d="borderInnerD"
        class="dp-retro-table-fx__border-inner"
    />
    <path
        v-if="borderOuterD"
        :d="borderOuterD"
        class="dp-retro-table-fx__border-outer"
    />
    <rect
        v-for="(corner, idx) in cornerPixels"
        :key="'corner-' + idx"
        :x="corner.x"
        :y="corner.y"
        :width="corner.size"
        :height="corner.size"
        class="dp-retro-table-fx__corner"
    />
    <path
        v-if="edgePathD && animated"
        :d="edgePathD"
        class="dp-retro-table-fx__edge"
        :class="{ 'dp-retro-table-fx__edge--animate': animated }"
        pathLength="100"
    />
  </svg>
</template>

<script>
import {
  retroTableBorderPaths,
  retroTableEdgePathD
} from '../utils/dpRetroTableFxGeometry'

var CORNER_PIXEL_SIZE = 0.75

export default {
  name: 'DpRetroTableFx',
  props: {
    layout: { type: Object, default: null },
    animated: { type: Boolean, default: false },
    /** Synced to glitch monster burst (felt glitch + monster hold through recovery). */
    edgeAlert: { type: Boolean, default: false }
  },
  computed: {
    borderPaths: function () {
      return retroTableBorderPaths(this.layout)
    },
    borderOuterD: function () {
      return this.borderPaths.outer
    },
    borderInnerD: function () {
      return this.borderPaths.inner
    },
    edgePathD: function () {
      if (!this.layout || !this.layout.vertices) return ''
      return retroTableEdgePathD(this.layout.vertices)
    },
    cornerPixels: function () {
      if (!this.layout || !this.layout.vertices) return []
      var half = CORNER_PIXEL_SIZE / 2
      return this.layout.vertices.map(function (v) {
        return {
          x: (v.x - half).toFixed(2),
          y: (v.y - half).toFixed(2),
          size: CORNER_PIXEL_SIZE
        }
      })
    }
  }
}
</script>
