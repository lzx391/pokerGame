<template>
  <el-dialog
    :title="title"
    :visible.sync="innerVisible"
    width="92%"
    custom-class="dp-cat-tutorial-dialog"
    append-to-body
    :close-on-click-modal="false"
    @close="onClose"
  >
    <div class="dp-cat-tutorial-dialog__body">
      <game-play-flow-content />
      <label class="dp-cat-tutorial-dialog__check">
        <input v-model="dontShowAgain" type="checkbox" />
        不再自动弹出（仍可在「玩法说明」里查看）
      </label>
    </div>
    <span slot="footer" class="dialog-footer">
      <el-button type="primary" @click="confirm">我知道了</el-button>
    </span>
  </el-dialog>
</template>

<script>
import GamePlayFlowContent from './GamePlayFlowContent.vue'

export default {
  name: 'CatTutorialDialog',
  components: { GamePlayFlowContent },
  props: {
    visible: { type: Boolean, default: false },
    title: { type: String, default: '欢迎来到猫咪牌局' }
  },
  data() {
    return {
      innerVisible: false,
      dontShowAgain: false
    }
  },
  watch: {
    visible: {
      immediate: true,
      handler(v) {
        this.innerVisible = v
        if (v) this.dontShowAgain = false
      }
    },
    innerVisible(v) {
      this.$emit('update:visible', v)
    }
  },
  methods: {
    confirm() {
      this.$emit('confirm', { dontShowAgain: this.dontShowAgain })
      this.innerVisible = false
    },
    onClose() {
      this.$emit('update:visible', false)
    }
  }
}
</script>

<style scoped>
.dp-cat-tutorial-dialog__body {
  text-align: left;
}
.dp-cat-tutorial-dialog__check {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-top: 12px;
  font-size: 13px;
  color: var(--dp-text-secondary, #606266);
  cursor: pointer;
}
.dp-cat-tutorial-dialog__check input {
  margin-top: 3px;
}
</style>

<style>
.dp-cat-tutorial-dialog {
  max-width: 520px;
}
</style>
