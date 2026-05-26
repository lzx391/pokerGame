<template>
  <span
    :class="['dp-user-avatar', 'dp-user-avatar--' + size]"
    :title="title || undefined"
    role="img"
    :aria-label="ariaLabel"
  >
    <img
      v-if="imageSrc"
      :src="imageSrc"
      class="dp-user-avatar__img"
      alt=""
    >
    <span v-else class="dp-user-avatar__initial" aria-hidden="true">{{ initial }}</span>
  </span>
</template>

<script>
import { avatarFileSrc, avatarInitialFromNickname } from '@/utils/dpAvatarUrl'

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
    title: { type: String, default: '' }
  },
  computed: {
    imageSrc() {
      return avatarFileSrc(this.avatarUrl, this.cacheBust)
    },
    initial() {
      return avatarInitialFromNickname(this.nickname)
    },
    ariaLabel() {
      var n = (this.nickname || '').trim()
      return n ? n + ' 的头像' : '用户头像'
    }
  }
}
</script>

<style scoped>
.dp-user-avatar {
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
.dp-user-avatar__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.dp-user-avatar__initial {
  display: block;
}
</style>
