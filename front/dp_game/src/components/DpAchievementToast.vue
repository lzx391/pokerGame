<template>
  <div
    class="dp-ach-toast"
    role="status"
    :aria-label="ariaLabel"
  >
    <div class="dp-ach-toast__icon" aria-hidden="true">
      <span class="dp-ach-toast__icon-inner">★</span>
    </div>
    <div class="dp-ach-toast__body">
      <div class="dp-ach-toast__label">成就解锁！</div>
      <div class="dp-ach-toast__title">{{ title }}</div>
      <div v-if="description" class="dp-ach-toast__desc">{{ description }}</div>
    </div>
  </div>
</template>

<script>
export default {
  name: 'DpAchievementToast',
  props: {
    title: { type: String, default: '' },
    description: { type: String, default: '' }
  },
  computed: {
    ariaLabel() {
      var parts = ['成就解锁']
      if (this.title) parts.push(this.title)
      if (this.description) parts.push(this.description)
      return parts.join('：')
    }
  }
}
</script>

<style scoped>
.dp-ach-toast {
  display: flex;
  align-items: stretch;
  gap: 10px;
  width: min(340px, calc(100vw - 32px));
  padding: 10px 12px;
  box-sizing: border-box;
  background: rgba(12, 12, 12, 0.86);
  border: 2px solid #1a1a1a;
  outline: 1px solid rgba(255, 255, 255, 0.12);
  outline-offset: -3px;
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.08),
    0 6px 18px rgba(0, 0, 0, 0.45);
  text-align: left;
  pointer-events: none;
  font-family: 'Segoe UI', 'Microsoft YaHei', sans-serif;
}

.dp-ach-toast__icon {
  flex-shrink: 0;
  width: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.dp-ach-toast__icon-inner {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 2px solid #3a3a3a;
  background: linear-gradient(180deg, rgba(80, 80, 80, 0.5), rgba(20, 20, 20, 0.9));
  color: #ffff55;
  font-size: 18px;
  line-height: 1;
  text-shadow: 1px 1px 0 #000;
}

.dp-ach-toast__body {
  min-width: 0;
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
}

.dp-ach-toast__label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: #55ff55;
  text-shadow: 1px 1px 0 rgba(0, 0, 0, 0.85);
}

.dp-ach-toast__title {
  font-size: 15px;
  font-weight: 700;
  line-height: 1.25;
  color: #ffff55;
  text-shadow: 1px 1px 0 rgba(0, 0, 0, 0.9);
  word-break: break-word;
}

.dp-ach-toast__desc {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.35;
  color: #b0b0b0;
  text-shadow: 1px 1px 0 rgba(0, 0, 0, 0.75);
  word-break: break-word;
}
</style>
