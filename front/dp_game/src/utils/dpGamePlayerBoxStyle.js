/**
 * 玩家卡片容器样式（圆桌 / 底栏手牌区共用），与 theme CSS 变量配套。
 * @param {object} p 玩家对象
 * @param {number} seatIndex players 数组下标
 * @param {{ actIndex: number, stage: string, isOwner: boolean, selectedWinners: string[], myNickname: string }} o
 */
export function dpGamePlayerBoxStyle(p, seatIndex, o) {
  var actIndex = o.actIndex
  var stage = o.stage
  var isOwner = o.isOwner
  var selectedWinners = o.selectedWinners || []
  var myNickname = o.myNickname || ''

  var s = {
    background: 'var(--dp-player-card-bg)',
    padding: '10px',
    borderRadius: '10px',
    border: '2px solid transparent',
    transition: 'all 0.2s'
  }

  if (p.leftThisHand) {
    s.background = 'var(--dp-player-card-offline-bg)'
    s.borderColor = 'var(--dp-player-card-offline-border)'
    s.opacity = '0.85'
    return s
  }

  if (actIndex === seatIndex) {
    s.borderColor = 'var(--dp-player-card-turn-border)'
    s.background = 'var(--dp-player-card-turn-bg)'
  }

  if (myNickname && p.nickname === myNickname) {
    s.borderColor = 'var(--dp-player-border-me)'
  }

  if (p.fold) {
    s.opacity = '0.5'
  }

  if (selectedWinners.indexOf(p.nickname) !== -1) {
    s.borderColor = 'var(--dp-player-card-winner-border)'
    s.borderWidth = '3px'
    s.background = 'var(--dp-player-card-winner-bg)'
    s.opacity = '1'
  } else if (stage === 'showdown' && isOwner) {
    s.cursor = 'pointer'
    s.borderStyle = 'dashed'
    s.borderColor = 'var(--dp-player-showdown-border)'
  }

  return s
}
