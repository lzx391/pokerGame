import { DP_NPC_DECISION_TRACE_SKIP_EXPERIMENTAL_PASSWORD } from '@/constants/dpNpcDecisionTraceUi'
import { dpDeckPresetSessionPassword } from './dpDeckPresetUnlock'

/** 决策分析 API 用的实验密码；内测跳过时不传 session 密码。 */
export function dpNpcDecisionTraceAuthPassword(roomId) {
  if (DP_NPC_DECISION_TRACE_SKIP_EXPERIMENTAL_PASSWORD) return ''
  return dpDeckPresetSessionPassword(roomId)
}

/** 是否已满足打开决策分析 UI 的密码条件（与排牌解锁独立）。 */
export function isNpcDecisionTraceUnlocked(roomId) {
  if (DP_NPC_DECISION_TRACE_SKIP_EXPERIMENTAL_PASSWORD) return true
  return !!dpDeckPresetSessionPassword(roomId)
}
