<template>
  <span
    :class="['dp-user-avatar', 'dp-user-avatar--' + size]"
    :title="title || undefined"
    role="img"
    :aria-label="ariaLabel"
  >
    <span
      class="dp-user-avatar__initial"
      :class="{ 'dp-user-avatar__initial--behind': showImage }"
      aria-hidden="true"
    >{{ initial }}</span>
    <img
      v-if="showImage"
      :key="imgKey"
      :src="displaySrc"
      class="dp-user-avatar__img"
      :class="{ 'dp-user-avatar__img--loaded': imageLoaded }"
      alt=""
      decoding="async"
      :loading="imgLoadingAttr"
      @load="onImgLoad"
      @error="onImgError"
    >
  </span>
</template>

<script>
import {
  avatarDisplayWebPath,
  avatarFileSrc,
  avatarInitialFromNickname,
  avatarThumbWebPath
} from '@/utils/dpAvatarUrl'

export default {
  name: 'DpUserAvatar',
  props: {
    avatarUrl: { type: String, default: '' },
    nickname: { type: String, default: '' },
    size: {
      type: String,
      default: 'md',
      validator: function (v) {
        return ['sm', 'md', 'lg'].indexOf(v) !== -1
      }
    },
    cacheBust: { type: [Number, String], default: '' },
    title: { type: String, default: '' },
    /** 传给 <img loading>；好友抽屉首屏可 eager 或依赖预取 */
    imgLoading: { type: String, default: '' }
  },
  data: function () {
    return {
      useFullFallback: false,
      imageLoaded: false,
      imageFailed: false
    }
  },
  computed: {
    resolvedWebPath: function () {
      if (!this.avatarUrl || this.imageFailed) return ''
      if (this.useFullFallback || this.size === 'lg') {
        return this.avatarUrl
      }
      return avatarDisplayWebPath(this.avatarUrl, this.size)
    },
    displaySrc: function () {
      if (!this.resolvedWebPath) return ''
      return avatarFileSrc(this.resolvedWebPath, this.cacheBust, {
        variant: this.useFullFallback || this.size === 'lg' ? 'full' : 'thumb'
      })
    },
    showImage: function () {
      return !!this.displaySrc && !this.imageFailed
    },
    imgKey: function () {
      return this.displaySrc + '|' + String(this.useFullFallback)
    },
    initial: function () {
      return avatarInitialFromNickname(this.nickname)
    },
    ariaLabel: function () {
      var n = (this.nickname || '').trim()
      return n ? n + ' 的头像' : '用户头像'
    },
    imgLoadingAttr: function () {
      var v = (this.imgLoading || '').trim()
      if (v === 'lazy' || v === 'eager') return v
      return undefined
    }
  },
  watch: {
    avatarUrl: 'resetImageState',
    cacheBust: 'resetImageState',
    size: 'resetImageState'
  },
  methods: {
    resetImageState: function () {
      this.useFullFallback = false
      this.imageLoaded = false
      this.imageFailed = false
    },
    onImgLoad: function () {
      this.imageLoaded = true
    },
    onImgError: function () {
      var canThumb =
        !this.useFullFallback &&
        this.size !== 'lg' &&
        !!avatarThumbWebPath(this.avatarUrl)
      if (canThumb) {
        this.useFullFallback = true
        this.imageLoaded = false
        return
      }
      this.imageFailed = true
      this.imageLoaded = false
    }
  }
}
</script>

<style scoped>
.dp-user-avatar {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: 6px;
  overflow: hidden;
  background: color-mix(in srgb, var(--dp-accent, #1890ff) 18%, var(--dp-subpanel-bg, #f0f0f0));
  border: 1px solid var(--dp-subpanel-border, #e0e0e0);
  color: var(--dp-accent, #1890ff);
  font-weight: 700;
  line-height: 1;
  user-select: none;
}
.dp-user-avatar--sm {
  width: 32px;
  height: 32px;
  font-size: 14px;
}
.dp-user-avatar--md {
  width: 40px;
  height: 40px;
  font-size: 16px;
}
.dp-user-avatar--lg {
  width: 72px;
  height: 72px;
  font-size: 28px;
}
.dp-user-avatar__initial {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 0;
}
.dp-user-avatar__initial--behind {
  opacity: 0;
  pointer-events: none;
}
.dp-user-avatar__img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  z-index: 1;
  opacity: 0;
  transition: opacity 0.12s ease;
}
.dp-user-avatar__img--loaded {
  opacity: 1;
}
</style>
