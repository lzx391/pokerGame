/**
 * 新手一分钟 · 全界面对局 UI 引导步骤（大白话）
 * scope: topBar | table | footer | actionPanel | page
 * simulateOpenActionSheet：进入该步后自动模拟点击「行动」打开抽屉
 * openActionSheet：保持行动抽屉打开（行动区特写步）
 */

import { CAT_COPY } from './dpCatThemeCopy'

export var GUIDE_UI_STEPS = [
  {
    scope: 'topBar',
    ref: 'guideTopSettings',
    title: '设置',
    body: '换界面主题、开关节能模式（少动画更省电）。真对局里随时能改，只影响你自己屏幕上的样子。',
    openSettings: true
  },
  {
    scope: 'topBar',
    ref: 'guideTopStage',
    title: '阶段',
    body: '当前牌局进行到哪一段：翻前圈 → 翻后圈 → 半决赛 → 决赛圈 → 结算阶段。公共牌一张张翻开时，这里会跟着变。'
  },
  {
    scope: 'topBar',
    ref: 'guideTopPot',
    title: CAT_COPY.pot,
    body: '本局大家投进桌心的 ' + CAT_COPY.chips + ' 总量。赢家（或最后没盖牌的人）从这里分走奖励。'
  },
  {
    scope: 'topBar',
    ref: 'guideTopHeroEco',
    title: '你的小鱼干',
    body:
      '「持有」是你还能用的 ' +
      CAT_COPY.chips +
      '；「本轮」是你这一圈已投多少；若轮到你且前面有人 ' +
      CAT_COPY.actionRaise +
      '，会显示「还需补」要再投多少才能继续。'
  },
  {
    scope: 'topBar',
    ref: 'guideTopAlign',
    title: CAT_COPY.needMatch,
    body: '桌上当前最高投入。你要跟投或加投，总投入得对齐这个数（或更高），否则不能争 ' + CAT_COPY.pot + '。'
  },
  {
    scope: 'topBar',
    ref: 'guideTopFullscreen',
    title: '全屏',
    body: '铺满屏幕，牌桌和底栏按钮更大。进教程会和真对局一样自动尝试全屏；再点一次可退出。'
  },
  {
    scope: 'topBar',
    ref: 'guideTopPlayGuide',
    title: '玩法说明',
    body: '打开完整规则：一局怎么进行、牌型强弱对照表等。本「新手一分钟」只讲按钮；细则在这里查。'
  },
  {
    scope: 'topBar',
    ref: 'guideTopOwnerHub',
    title: '房主操作',
    body: '只有房主可见：加演示 NPC、踢人、移交房主、看穿底牌等。教程里按钮在，但不会真改房间。'
  },
  {
    scope: 'topBar',
    ref: 'guideTopInvite',
    title: '邀请好友',
    body: '给互为好友的玩家发进房邀请。对方同意后可直接进这间房（观战或上桌视规则而定）。'
  },
  {
    scope: 'topBar',
    ref: 'guideTopHandHistory',
    title: '历史对局',
    body: '查看本房或你与他人的牌谱记录，方便复盘。教程模式不打开真实列表。'
  },
  {
    scope: 'topBar',
    ref: 'guideTopMusic',
    title: '音乐盒',
    body: '开关房间背景音乐、选曲目。不影响牌局逻辑，只是氛围。'
  },
  {
    scope: 'topBar',
    ref: 'guideTopWaitList',
    title: '等待名单',
    body: '已报名「下一局上桌」的玩家名单。本局进行中先在旁观/等待，新一手开始时按空位入座。'
  },
  {
    scope: 'topBar',
    ref: 'guideTopSpectators',
    title: '观众席',
    body: '当前不在牌桌上、但在房里围观的人。观众可聊天，也可点「下一局加入对局」排队上桌。'
  },
  {
    scope: 'table',
    ref: 'guideTableWrap',
    title: '牌桌与座位',
    body:
      '椭圆桌：中间是公共牌与 ' +
      CAT_COPY.pot +
      '；周围是各只猫的手牌区。发、SC、BC 小标是 ' +
      CAT_COPY.dealer +
      ' 与 ' +
      CAT_COPY.blindPositionHint +
      '；轮到你行动时座位会高亮。',
    closeUp: true,
    spotlightPad: 10
  },
  {
    scope: 'footer',
    ref: 'guideMobileChatCluster',
    title: '局内聊天（底栏）',
    body: '左下角和真对局一样：点「聊天」可展开记录，下面输入想说的话。不会真发到服务器。',
    expandChat: true,
    closeUp: true,
    spotlightPad: 12
  },
  {
    scope: 'footer',
    ref: 'guideChatToggle',
    title: '展开 / 收起聊天',
    body: '点「聊天」展开历史；收起后只留最新一条预览，不占地方。',
    expandChat: true,
    closeUp: true,
    spotlightPad: 10
  },
  {
    scope: 'footer',
    ref: 'guideChatListWrap',
    title: '聊天记录',
    body: '展开后这里显示谁说了什么。演示里已有假消息；真对局会实时更新。',
    expandChat: true,
    closeUp: true,
    spotlightPad: 12
  },
  {
    scope: 'footer',
    ref: 'guideChatBar',
    title: '输入与发送',
    body: '输入文字后点「发送」或回车。教程里只是演示，不会连服务器。',
    expandChat: true,
    closeUp: true,
    spotlightPad: 10
  },
  {
    scope: 'footer',
    ref: 'guideMobileLeaveSeat',
    title: '主动离座',
    body:
      '本手打完前离开座位去观众席（本手算' +
      CAT_COPY.actionFold +
      '）。想再玩可报名「下一局加入对局」。',
    closeUp: true,
    spotlightPad: 10
  },
  {
    scope: 'footer',
    ref: 'guideMobileActionBtn',
    title: '打开行动面板',
    body: '轮到你时，先点底栏「行动（倒计时）」打开抽屉。教程马上会帮你模拟点一下，和真对局一样。',
    simulateOpenActionSheet: true,
    closeUp: true,
    spotlightPad: 10
  },
  {
    scope: 'actionPanel',
    ref: 'guideActionSheet',
    title: '行动抽屉（总览）',
    body: '打开后整块抽屉里：上面快捷加投和滑条，中间跟投/加投，下面全投和盖牌。下面几步会逐个特写。',
    openActionSheet: true,
    closeUp: true,
    spotlightPad: 14
  },
  {
    scope: 'actionPanel',
    ref: 'guidePotPresets',
    title: '快捷加投',
    body: '按 ' + CAT_COPY.pot + ' 比例（⅓池、½池…）或「最小加投」快速填好加投额。',
    openActionSheet: true,
    closeUp: true,
    spotlightPad: 12
  },
  {
    scope: 'actionPanel',
    ref: 'guideRaiseSlider',
    title: '本笔投入滑条',
    body: '拖动滑条微调这一笔投多少；右边数字会跟着变。',
    openActionSheet: true,
    closeUp: true,
    spotlightPad: 12
  },
  {
    scope: 'actionPanel',
    ref: 'guideCall',
    title: '跟投 / 观望',
    body:
      '有人' +
      CAT_COPY.actionRaise +
      '时跟上「还需补」才能继续；没人' +
      CAT_COPY.actionRaise +
      '时显示「' +
      CAT_COPY.actionCheck +
      '」= 过牌。',
    openActionSheet: true,
    closeUp: true,
    spotlightPad: 10
  },
  {
    scope: 'actionPanel',
    ref: 'guideRaise',
    title: '加投',
    body: '在跟齐之外再多投，逼别人跟更多 ' + CAT_COPY.chips + ' 进池。',
    openActionSheet: true,
    closeUp: true,
    spotlightPad: 10
  },
  {
    scope: 'actionPanel',
    ref: 'guideFold',
    title: '盖牌',
    body: '放弃本手，不再争 ' + CAT_COPY.pot + '。已投进去的不退。',
    openActionSheet: true,
    closeUp: true,
    spotlightPad: 10
  },
  {
    scope: 'actionPanel',
    ref: 'guideAllin',
    title: '全投',
    body: '把手牌区显示的持有 ' + CAT_COPY.chips + ' 一次全压。',
    openActionSheet: true,
    closeUp: true,
    spotlightPad: 10
  },
  {
    scope: 'actionPanel',
    ref: 'guideActionTimer',
    title: '行动倒计时',
    body: '圆环倒数约 30 秒；超时自动盖牌，避免拖局。',
    openActionSheet: true,
    closeUp: true,
    spotlightPad: 10
  },
  {
    scope: 'page',
    ref: 'guideNotMyTurn',
    title: '没轮到你时',
    body: '底栏没有「行动」按钮或抽屉打不开，只能看别人打。顶栏仍可看 ' + CAT_COPY.pot + ' 与 ' + CAT_COPY.needMatch + '。',
    notMyTurn: true,
    closeActionSheet: true,
    closeUp: true,
    spotlightPad: 12
  }
]

export var GUIDE_UI_COMPLETE_STEP = {
  complete: true,
  title: '界面按钮 tour 完成',
  body: '可以去大厅「创建房间」或「快速匹配」实战。牌型与完整流程点顶栏「玩法说明」。',
  note: '本页为单机演示，所有按钮不会连服务器。'
}
