<template>
  <div class="dp-play-flow">
    <p class="dp-play-flow__lead">
      你和朋友们扮演<strong>猫咪</strong>，用两张手牌配合桌面公共牌组牌型，通过每轮行动争夺桌心<strong>小鱼干池</strong>里的奖励。
    </p>

    <div class="dp-play-flow__section">
      <strong class="dp-play-flow__h">目标</strong>
      <p class="dp-play-flow__p">
        比牌型强弱；能留到最后并胜出的猫咪，分到本局池子里的小鱼干。牌型强弱可在对局里点顶栏「玩法说明」，在「牌型说明」中对照。
      </p>
    </div>

    <div class="dp-play-flow__section">
      <strong class="dp-play-flow__h">一局怎么进行</strong>
      <ul class="dp-play-flow__list dp-play-flow__list--tight">
        <li><strong>发牌猫</strong>：只标记「从哪一侧开始发牌」，随牌局进行轮转</li>
        <li>
          <strong>小猫（SC）/ 大猫（BC）</strong>：发牌猫顺时针方向最近的两个玩家变成慷慨的小猫大猫开局各自动拿出一点小鱼干，形成前置鱼干池，避免大家一直观望赢不到小鱼干。
        </li>
        <li>桌面公共牌会按阶段一张张翻开；轮到你时，可选<strong>跟投</strong>、<strong>加投</strong>、<strong>盖牌</strong>放弃，或<strong>全投</strong>光手上小鱼干，每轮有资格继续游戏的玩家必须投放相同的鱼干数。</li>
        <li>若只剩一人未盖牌，直接收下池子；否则进入<strong>结算阶段</strong>比牌型分池。结束后大家准备妥当再开下一局。</li>
      </ul>
    </div>

    <div class="dp-play-flow__section">
      <strong class="dp-play-flow__h">界面常用词</strong>
      <ul class="dp-play-flow__list dp-play-flow__list--tight">
        <li>顶栏<strong>阶段</strong>顺序：<strong>翻前圈</strong> → <strong>翻后圈</strong> → <strong>半决赛</strong> → <strong>决赛圈</strong> → <strong>结算阶段</strong>（比牌与等待下一局都属于结算阶段）。</li>
        <li><strong>小猫鱼干</strong>＝大猫鱼干的一半；<strong>大猫鱼干</strong>＝每轮主动投放的最低鱼干数；<strong>小鱼干池</strong>＝本局大家投入堆在中间、等待分配的总量。</li>
        <li><strong>需对齐</strong>＝为了游戏公平，只有投放相同鱼干数的玩家才有资格赢取小鱼干池。</li>
        <li>座位上 <strong>发</strong>＝发牌猫；<strong>SC / BC</strong>＝小猫 / 大猫位置。</li>
      </ul>
    </div>

    <p class="dp-play-flow__note">
      <strong>说明：</strong>小鱼干仅本玩法内计分，仅供娱乐，不能兑换现金或实物。
    </p>
  </div>
</template>

<script>
export default {
  name: 'GamePlayFlowContent'
}
</script>

<style scoped>
.dp-play-flow {
  text-align: left;
  font-size: 14px;
  line-height: 1.65;
  color: var(--dp-text-primary, #303133);
}
.dp-play-flow__lead {
  margin: 0 0 14px;
}
.dp-play-flow__section {
  margin-bottom: 14px;
}
.dp-play-flow__h {
  display: block;
  margin-bottom: 6px;
  font-size: 13px;
  color: var(--dp-text-primary, #303133);
}
.dp-play-flow__p {
  margin: 0;
  font-size: 14px;
  line-height: 1.65;
}
.dp-play-flow__list {
  margin: 0;
  padding-left: 1.15em;
}
.dp-play-flow__list li {
  margin-bottom: 6px;
}
.dp-play-flow__list--tight li:last-child {
  margin-bottom: 0;
}
.dp-play-flow__note {
  margin: 0;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.55;
  color: var(--dp-text-secondary, #606266);
  background: var(--dp-subpanel-bg, rgba(0, 0, 0, 0.04));
  border-radius: 8px;
  border: 1px solid var(--dp-subpanel-border, rgba(0, 0, 0, 0.06));
}
</style>
