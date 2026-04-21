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
      <p class="dp-cat-tutorial-dialog__lead">
        你和朋友们扮演<strong>猫咪</strong>，用两张手牌配合桌面公共牌组牌型，通过表态争夺桌心<strong>小鱼干池</strong>里的奖励。
      </p>

      <div class="dp-cat-tutorial-dialog__section">
        <strong class="dp-cat-tutorial-dialog__h">目标</strong>
        <p class="dp-cat-tutorial-dialog__p">
          比牌型强弱；能留到最后并胜出的猫咪，分到本局池子里的小鱼干。牌型强弱可在对局里点「牌型说明」对照。
        </p>
      </div>

      <div class="dp-cat-tutorial-dialog__section">
        <strong class="dp-cat-tutorial-dialog__h">一局怎么进行</strong>
        <ul class="dp-cat-tutorial-dialog__list dp-cat-tutorial-dialog__list--tight">
          <li><strong>发牌猫</strong>：只标记「从哪一侧开始发牌」，不占输赢。</li>
          <li><strong>小猫（SC）/ 大猫（BC）</strong>：开局各自动拿出一点小鱼干，形成前置鱼干池，避免大家一直观望赢不到小鱼干。</li>
          <li>桌面公共牌会按阶段一张张翻开；轮到你时，可选<strong>跟投</strong>、<strong>加投</strong>、<strong>盖牌</strong>放弃，或<strong>全投</strong>光手上小鱼干。</li>
          <li>若只剩一人未盖牌，直接收下池子；否则进入<strong>结算阶段</strong>比牌型分池。结束后大家准备妥当再开下一局。</li>
        </ul>
      </div>

      <div class="dp-cat-tutorial-dialog__section">
        <strong class="dp-cat-tutorial-dialog__h">界面常用词</strong>
        <ul class="dp-cat-tutorial-dialog__list dp-cat-tutorial-dialog__list--tight">
          <li>顶栏<strong>阶段</strong>顺序：<strong>翻前圈</strong> → <strong>翻后圈</strong> → <strong>半决赛</strong> → <strong>决赛圈</strong> → <strong>结算阶段</strong>（比牌与等待下一局都属于结算阶段）。</li>
          <li><strong>小猫鱼干</strong>＝大猫鱼干的一半；<strong>大猫鱼干</strong>＝每轮主动投放的最低鱼干数；<strong>小鱼干池</strong>＝本局大家投入堆在中间、等待分配的总量。</li>
          <li><strong>需对齐</strong>＝为了游戏公平，只有投放相同鱼干数的玩家才有资格赢取小鱼干池。</li>
          <li>座位上 <strong>发</strong>＝发牌猫；<strong>SC / BC</strong>＝小猫 / 大猫位置。</li>
        </ul>
      </div>

      <p class="dp-cat-tutorial-dialog__note">
        <strong>说明：</strong>小鱼干仅本玩法内计分，仅供娱乐，不能兑换现金或实物。
      </p>

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
export default {
  name: 'CatTutorialDialog',
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
  font-size: 14px;
  line-height: 1.65;
  color: var(--dp-text-primary, #303133);
}
.dp-cat-tutorial-dialog__lead {
  margin: 0 0 14px;
}
.dp-cat-tutorial-dialog__section {
  margin-bottom: 14px;
}
.dp-cat-tutorial-dialog__h {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--dp-text-primary, #303133);
}
.dp-cat-tutorial-dialog__p {
  margin: 0;
  font-size: 14px;
  line-height: 1.65;
}
.dp-cat-tutorial-dialog__list {
  margin: 0;
  padding-left: 1.15em;
}
.dp-cat-tutorial-dialog__list li {
  margin-bottom: 6px;
}
.dp-cat-tutorial-dialog__list--tight li:last-child {
  margin-bottom: 0;
}
.dp-cat-tutorial-dialog__note {
  margin: 0 0 14px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--dp-text-secondary, #606266);
  background: var(--dp-subpanel-bg, rgba(0, 0, 0, 0.04));
  border-radius: 8px;
  border: 1px solid var(--dp-subpanel-border, rgba(0, 0, 0, 0.06));
}
.dp-cat-tutorial-dialog__check {
  display: flex;
  align-items: flex-start;
  gap: 8px;
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
