/**
 * Wave 8: move remaining real files, rewrite imports, delete compat shells.
 * Run: node scripts/wave8-cleanup.js
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.join(__dirname, '..')
const SRC = path.join(ROOT, 'src')

/** Real files to move: oldPath (relative to src) -> newPath (relative to src) */
const MOVES = {
  // shared/components — Retro CRT cross-cutting UI
  'components/DpCrtBootSequence.vue': 'shared/components/DpCrtBootSequence.vue',
  'components/DpCrtEventPopup.vue': 'shared/components/DpCrtEventPopup.vue',
  'components/DpCrtFullscreenOverlay.vue': 'shared/components/DpCrtFullscreenOverlay.vue',
  'components/DpFluidityToggle.vue': 'shared/components/DpFluidityToggle.vue',
  'components/DpRetroAmbientOverlay.vue': 'shared/components/DpRetroAmbientOverlay.vue',
  'components/DpRetroConfirmDialog.vue': 'shared/components/DpRetroConfirmDialog.vue',
  'components/DpRetroGlitchMonster.vue': 'shared/components/DpRetroGlitchMonster.vue',
  'components/DpRetroPasswordGateShell.vue': 'shared/components/DpRetroPasswordGateShell.vue',
  'components/DpRetroStackLeaderTicker.vue': 'shared/components/DpRetroStackLeaderTicker.vue',
  'components/DpRetroTableFx.vue': 'shared/components/DpRetroTableFx.vue',
  'components/DpRetroTv.vue': 'shared/components/DpRetroTv.vue',
  'components/DpThemePicker.vue': 'shared/components/DpThemePicker.vue',
  // features/npc/components
  'components/CustomNpcStyleDialog.vue': 'features/npc/components/CustomNpcStyleDialog.vue',
  'components/DpCustomNpcConsole.vue': 'features/npc/components/DpCustomNpcConsole.vue',
  'components/DpOwnerNpcConsole.vue': 'features/npc/components/DpOwnerNpcConsole.vue',
  'components/GameNpcMoodSheet.vue': 'features/npc/components/GameNpcMoodSheet.vue',
  // features/download/components
  'components/DpTerminalCli.vue': 'features/download/components/DpTerminalCli.vue',
  // constants
  'constants/dpCatThemeCopy.js': 'shared/constants/dpCatThemeCopy.js',
  'constants/dpCustomNpcUi.js': 'features/npc/constants/dpCustomNpcUi.js',
  'constants/npcStylePresets.js': 'features/npc/constants/npcStylePresets.js',
  // shared/utils — cross-cutting
  'utils/dpRouteTransition.js': 'shared/utils/dpRouteTransition.js',
  'utils/dpRouteTransitionFlag.js': 'shared/utils/dpRouteTransitionFlag.js',
  'utils/dpOverlayPortal.js': 'shared/utils/dpOverlayPortal.js',
  'utils/dpModalZIndex.js': 'shared/utils/dpModalZIndex.js',
  'utils/dpFullscreenOverlayBridge.js': 'shared/utils/dpFullscreenOverlayBridge.js',
  'utils/dpDisplayNickname.js': 'shared/utils/dpDisplayNickname.js',
  'utils/dpNicknameFont.js': 'shared/utils/dpNicknameFont.js',
  'utils/dpBodyGameTheme.js': 'shared/utils/dpBodyGameTheme.js',
  'utils/dpBodyFluidity.js': 'shared/utils/dpBodyFluidity.js',
  'utils/dpPrefetchGameRoute.js': 'shared/utils/dpPrefetchGameRoute.js',
  'utils/dpRetroBootLines.js': 'shared/utils/dpRetroBootLines.js',
  'utils/dpRetroEnterGameHandoff.js': 'shared/utils/dpRetroEnterGameHandoff.js',
  'utils/dpRetroGlitchMonsterSprites.js': 'shared/utils/dpRetroGlitchMonsterSprites.js',
  'utils/dpRetroTableFxGeometry.js': 'shared/utils/dpRetroTableFxGeometry.js',
  // features/room/utils
  'utils/dpHandRankDisplay.js': 'features/room/utils/dpHandRankDisplay.js',
  'utils/dpTopBarLabels.js': 'features/room/utils/dpTopBarLabels.js',
  'utils/dpSeatEnterNickDiff.js': 'features/room/utils/dpSeatEnterNickDiff.js',
  'utils/dpDeckCards.js': 'features/room/utils/dpDeckCards.js',
  'utils/dpTableLayoutDevLog.js': 'features/room/utils/dpTableLayoutDevLog.js',
  'utils/dpSeatEnterDevLog.js': 'features/room/utils/dpSeatEnterDevLog.js',
  'utils/dpHandHologramDevLog.js': 'features/room/utils/dpHandHologramDevLog.js',
  'utils/dpOwnerTerminalDevLog.js': 'features/room/utils/dpOwnerTerminalDevLog.js',
  'utils/dpDeckPresetUnlock.js': 'features/room/utils/dpDeckPresetUnlock.js',
  'utils/dpSeatRayDevLog.js': 'features/room/utils/dpSeatRayDevLog.js',
  'utils/dpDebugBounds.js': 'features/room/utils/dpDebugBounds.js',
  'utils/dpRetroShowdownReveal.js': 'features/room/utils/dpRetroShowdownReveal.js',
  'utils/dpRetroDesktopFxDevLog.js': 'features/room/utils/dpRetroDesktopFxDevLog.js',
}

/** Map compat component name -> features path (for re-export shells) */
const COMPONENT_SHELL_MAP = {
  'ButtonGuideSpotlight.vue': '@features/room/components/ButtonGuideSpotlight.vue',
  'CatTutorialDialog.vue': '@features/room/components/CatTutorialDialog.vue',
  'CreateRoom.vue': '@features/lobby/pages/CreateRoomPage.vue',
  'DownloadAdminPasswordGate.vue': '@features/download/components/DownloadAdminPasswordGate.vue',
  'DownloadCenter.vue': '@features/download/pages/DownloadCenterPage.vue',
  'DpAchievementToast.vue': '@features/achievement/components/DpAchievementToast.vue',
  'DpAchievementToastHost.vue': '@features/achievement/components/DpAchievementToastHost.vue',
  'DpAchievementWallCrt.vue': '@features/achievement/components/DpAchievementWallCrt.vue',
  'DpAchievementWallModal.vue': '@features/achievement/components/DpAchievementWallModal.vue',
  'DpAuthStage.vue': '@features/user/components/DpAuthStage.vue',
  'DpCreateRoomConsole.vue': '@features/lobby/components/DpCreateRoomConsole.vue',
  'DpHandHistoryDetail.vue': '@features/history/components/DpHandHistoryDetail.vue',
  'DpHandHistoryViewer.vue': '@features/history/components/DpHandHistoryViewer.vue',
  'DpMailboxConsole.vue': '@features/social/components/DpMailboxConsole.vue',
  'DpMusicPlayer.vue': '@features/music/components/DpMusicPlayer.vue',
  'DpTablePotDisplay.vue': '@features/room/components/DpTablePotDisplay.vue',
  'DpUserAvatar.vue': '@features/user/components/DpUserAvatar.vue',
  'FriendChatDialog.vue': '@features/social/components/FriendChatDialog.vue',
  'GameActionPanel.vue': '@features/room/components/GameActionPanel.vue',
  'GameBottomSheet.vue': '@features/room/components/GameBottomSheet.vue',
  'GameButtonGuidePage.vue': '@features/room/pages/ButtonGuidePage.vue',
  'GameCommunityCards.vue': '@features/room/components/GameCommunityCards.vue',
  'GameDeckPresetDialog.vue': '@features/room/components/GameDeckPresetDialog.vue',
  'GameDeckPresetPasswordGate.vue': '@features/room/components/GameDeckPresetPasswordGate.vue',
  'GameDpFloatingModals.vue': '@features/room/components/GameDpFloatingModals.vue',
  'GameDpGameSheets.vue': '@features/room/components/GameDpGameSheets.vue',
  'GameFriendChatPanel.vue': '@features/social/components/GameFriendChatPanel.vue',
  'GameFriendChatPickerContent.vue': '@features/social/components/GameFriendChatPickerContent.vue',
  'GameFriendChatSheet.vue': '@features/social/components/GameFriendChatSheet.vue',
  'GameHandHistoryModal.vue': '@features/room/components/GameHandHistoryModal.vue',
  'GameHandRankModal.vue': '@features/room/components/GameHandRankModal.vue',
  'GameHeroDockFooter.vue': '@features/room/components/GameHeroDockFooter.vue',
  'GameHeroHandHologram.vue': '@features/room/components/GameHeroHandHologram.vue',
  'GameInviteFriendContent.vue': '@features/social/components/GameInviteFriendContent.vue',
  'GameInviteFriendPanel.vue': '@features/social/components/GameInviteFriendPanel.vue',
  'GameInviteFriendSheet.vue': '@features/social/components/GameInviteFriendSheet.vue',
  'GameMusicBoxModal.vue': '@features/room/components/GameMusicBoxModal.vue',
  'GameNpcDecisionTraceActionGrid.vue': '@features/npc/components/GameNpcDecisionTraceActionGrid.vue',
  'GameNpcDecisionTraceBody.vue': '@features/npc/components/GameNpcDecisionTraceBody.vue',
  'GameNpcDecisionTraceDock.vue': '@features/npc/components/GameNpcDecisionTraceDock.vue',
  'GameNpcDecisionTraceMatrixGrid.vue': '@features/npc/components/GameNpcDecisionTraceMatrixGrid.vue',
  'GameNpcDecisionTracePanel.vue': '@features/npc/components/GameNpcDecisionTracePanel.vue',
  'GameOwnerHubContent.vue': '@features/room/components/GameOwnerHubContent.vue',
  'GameOwnerHubPanel.vue': '@features/room/components/GameOwnerHubPanel.vue',
  'GameOwnerPanel.vue': '@features/room/components/GameOwnerPanel.vue',
  'GameOwnerToolModal.vue': '@features/room/components/GameOwnerToolModal.vue',
  'GameOwnerTouchPanel.vue': '@features/room/components/GameOwnerTouchPanel.vue',
  'GamePlayFlowContent.vue': '@features/room/components/GamePlayFlowContent.vue',
  'GamePlayGuideModal.vue': '@features/room/components/GamePlayGuideModal.vue',
  'GamePlayerCard.vue': '@features/room/components/GamePlayerCard.vue',
  'GamePlayerSocialSheet.vue': '@features/social/components/GamePlayerSocialSheet.vue',
  'GameRoomChatBar.vue': '@features/room/components/GameRoomChatBar.vue',
  'GameRoomChatPanel.vue': '@features/room/components/GameRoomChatPanel.vue',
  'GameRoundTable.vue': '@features/room/components/GameRoundTable.vue',
  'GameSettledPrepareBar.vue': '@features/room/components/GameSettledPrepareBar.vue',
  'GameSpectatorModal.vue': '@features/room/components/GameSpectatorModal.vue',
  'GameTableActionTimer.vue': '@features/room/components/GameTableActionTimer.vue',
  'GameTopBar.vue': '@features/room/components/GameTopBar.vue',
  'GameWaitNextHandModal.vue': '@features/room/components/GameWaitNextHandModal.vue',
  'HandHistory.vue': '@features/history/pages/HandHistoryPage.vue',
  'HandHistoryDetail.vue': '@features/history/pages/HandHistoryDetailPage.vue',
  'HomeProfileModal.vue': '@features/user/components/HomeProfileModal.vue',
  'LeaderboardPage.vue': '@features/leaderboard/pages/LeaderboardPage.vue',
  'LobbyRoomPasswordGate.vue': '@features/lobby/components/LobbyRoomPasswordGate.vue',
  'MusicUpload.vue': '@features/music/pages/MusicUploadPage.vue',
  'OAuthCallback.vue': '@features/user/pages/OAuthCallbackPage.vue',
  'QuickMatchPixelCritters.vue': '@features/quickmatch/components/QuickMatchPixelCritters.vue',
  'game.vue': '@features/room/pages/GamePage.vue',
  'home.vue': '@features/lobby/pages/LobbyPage.vue',
  'image_upload.vue': '@features/user/components/ImageUpload.vue',
  'login.vue': '@features/user/pages/LoginPage.vue',
  'register.vue': '@features/user/pages/RegisterPage.vue',
}

/** Import path rewrites: old @/ prefix -> new @features/@shared prefix */
const IMPORT_REWRITES = [
  // main.js relative paths
  ["from './utils/dpBodyGameTheme'", "from '@shared/utils/dpBodyGameTheme'"],
  ["from './utils/dpBodyFluidity'", "from '@shared/utils/dpBodyFluidity'"],
  ["from './utils/dpRouteTransitionFlag'", "from '@shared/utils/dpRouteTransitionFlag'"],
  ["from './components/DpThemePicker.vue'", "from '@shared/components/DpThemePicker.vue'"],
  // relative imports from old components/ root
  ["from '../utils/dpDisplayNickname'", "from '@shared/utils/dpDisplayNickname'"],
  ["from '../utils/dpRetroGlitchMonsterSprites'", "from '@shared/utils/dpRetroGlitchMonsterSprites'"],
  ["from '../utils/dpRetroDesktopFxDevLog'", "from '@features/room/utils/dpRetroDesktopFxDevLog'"],
  ["from '../utils/dpRetroTableFxGeometry'", "from '@shared/utils/dpRetroTableFxGeometry'"],
  ["from '../constants/npcStylePresets'", "from '@features/npc/constants/npcStylePresets'"],
  ["from '../constants/dpCustomNpcUi'", "from '@features/npc/constants/dpCustomNpcUi'"],
  // moved real components (@/components/X)
  ...Object.entries(MOVES)
    .filter(([k]) => k.endsWith('.vue'))
    .map(([oldP, newP]) => {
      const oldName = path.basename(oldP)
      const alias = newP.startsWith('shared/') ? '@shared/' : '@features/'
      const target = alias + newP.replace(/^(shared|features)\//, '')
      return ['@/components/' + oldName.replace('.vue', ''), target.replace('.vue', '')]
    }),
  // compat component shells (@/components/X)
  ...Object.entries(COMPONENT_SHELL_MAP).map(([name, target]) => [
    '@/components/' + name.replace('.vue', ''),
    target.replace('.vue', ''),
  ]),
  // constants
  ['@/constants/dpCatThemeCopy', '@shared/constants/dpCatThemeCopy'],
  ['@/constants/dpCustomNpcUi', '@features/npc/constants/dpCustomNpcUi'],
  ['@/constants/npcStylePresets', '@features/npc/constants/npcStylePresets'],
  ['@/constants/dpGameDealTiming', '@features/room/constants/dpGameDealTiming'],
  ['@/constants/dpGameHandRankReference', '@features/room/constants/dpGameHandRankReference'],
  ['@/constants/dpNpcDecisionTraceUi', '@features/npc/constants/dpNpcDecisionTraceUi'],
  ['@/constants/dpGameThemes', '@features/room/constants/dpGameThemes'],
  ['@/constants/guideUiSteps', '@features/room/constants/guideUiSteps'],
  // api
  ['@/api/api.dpRoom', '@features/room/api/roomApi'],
  ['@/api/api.dpSocial', '@features/social/api/socialApi'],
  ['@/api/api.dpLeaderboard', '@features/leaderboard/api/leaderboardApi'],
  // mixins
  ['@/mixins/dpGameActionCountdownMixin', '@features/room/mixins/dpGameActionCountdownMixin'],
  ['@/mixins/dpGameLayoutTierMixin', '@features/room/mixins/dpGameLayoutTierMixin'],
  ['@/mixins/dpGameTableFitMixin', '@features/room/mixins/dpGameTableFitMixin'],
  ['@/mixins/dpGameFullscreenMixin', '@features/room/mixins/dpGameFullscreenMixin'],
  ['@/mixins/dpLobbyThemeMixin', '@features/lobby/mixins/dpLobbyThemeMixin'],
  ['@/mixins/dpProfileGrayGlitchMixin', '@features/user/mixins/dpProfileGrayGlitchMixin'],
  // store
  ['@/store/modules/dpGame', '@features/room/store/dpGame'],
  ['@/store/modules/dpAchievement', '@features/achievement/store/dpAchievement'],
  ['@/store/modules/dpMailbox', '@features/social/store/dpMailbox'],
  // shared utils
  ['@/utils/dpApiResult', '@shared/utils/dpApiResult'],
  ['@/utils/dpRouteTransition', '@shared/utils/dpRouteTransition'],
  ['@/utils/dpRouteTransitionFlag', '@shared/utils/dpRouteTransitionFlag'],
  ['@/utils/dpOverlayPortal', '@shared/utils/dpOverlayPortal'],
  ['@/utils/dpModalZIndex', '@shared/utils/dpModalZIndex'],
  ['@/utils/dpFullscreenOverlayBridge', '@shared/utils/dpFullscreenOverlayBridge'],
  ['@/utils/dpDisplayNickname', '@shared/utils/dpDisplayNickname'],
  ['@/utils/dpNicknameFont', '@shared/utils/dpNicknameFont'],
  ['@/utils/dpBodyGameTheme', '@shared/utils/dpBodyGameTheme'],
  ['@/utils/dpBodyFluidity', '@shared/utils/dpBodyFluidity'],
  ['@/utils/dpPrefetchGameRoute', '@shared/utils/dpPrefetchGameRoute'],
  ['@/utils/dpRetroBootLines', '@shared/utils/dpRetroBootLines'],
  ['@/utils/dpRetroEnterGameHandoff', '@shared/utils/dpRetroEnterGameHandoff'],
  ['@/utils/dpRetroGlitchMonsterSprites', '@shared/utils/dpRetroGlitchMonsterSprites'],
  ['@/utils/dpRetroTableFxGeometry', '@shared/utils/dpRetroTableFxGeometry'],
  // user utils
  ['@/utils/dpAuthEnterLobby', '@features/user/utils/dpAuthEnterLobby'],
  ['@/utils/dpEnsureUserId', '@features/user/utils/dpEnsureUserId'],
  ['@/utils/dpAvatarUrl', '@features/user/utils/dpAvatarUrl'],
  ['@/utils/dpAvatarPrefetch', '@features/user/utils/dpAvatarPrefetch'],
  // lobby utils
  ['@/utils/dpLobbyEnterGame', '@features/lobby/utils/dpLobbyEnterGame'],
  ['@/utils/dpLobbyQuickMatchExit', '@features/lobby/utils/dpLobbyQuickMatchExit'],
  ['@/utils/dpCreateRoomEnterGame', '@features/lobby/utils/dpCreateRoomEnterGame'],
  ['@/utils/dpCreateRoomSubmit', '@features/lobby/utils/dpCreateRoomSubmit'],
  // room utils
  ['@/utils/dpHandRankDisplay', '@features/room/utils/dpHandRankDisplay'],
  ['@/utils/dpTopBarLabels', '@features/room/utils/dpTopBarLabels'],
  ['@/utils/dpSeatEnterNickDiff', '@features/room/utils/dpSeatEnterNickDiff'],
  ['@/utils/dpDeckCards', '@features/room/utils/dpDeckCards'],
  ['@/utils/dpTableLayoutDevLog', '@features/room/utils/dpTableLayoutDevLog'],
  ['@/utils/dpSeatEnterDevLog', '@features/room/utils/dpSeatEnterDevLog'],
  ['@/utils/dpHandHologramDevLog', '@features/room/utils/dpHandHologramDevLog'],
  ['@/utils/dpOwnerTerminalDevLog', '@features/room/utils/dpOwnerTerminalDevLog'],
  ['@/utils/dpDeckPresetUnlock', '@features/room/utils/dpDeckPresetUnlock'],
  ['@/utils/dpSeatRayDevLog', '@features/room/utils/dpSeatRayDevLog'],
  ['@/utils/dpDebugBounds', '@features/room/utils/dpDebugBounds'],
  ['@/utils/dpRetroShowdownReveal', '@features/room/utils/dpRetroShowdownReveal'],
  ['@/utils/dpRetroDesktopFxDevLog', '@features/room/utils/dpRetroDesktopFxDevLog'],
  ['@/utils/dpGameCardVisual', '@features/room/utils/dpGameCardVisual'],
  ['@/utils/dpGameHandRank', '@features/room/utils/dpGameHandRank'],
  ['@/utils/dpGameRoundTableLayout', '@features/room/utils/dpGameRoundTableLayout'],
  ['@/utils/dpGameRoomFingerprint', '@features/room/utils/dpGameRoomFingerprint'],
  ['@/utils/dpGamePlayerBoxStyle', '@features/room/utils/dpGamePlayerBoxStyle'],
  ['@/utils/dpGameEcoMode', '@features/room/utils/dpGameEcoMode'],
  ['@/utils/dpGameDealerAnchor', '@features/room/utils/dpGameDealerAnchor'],
  ['@/utils/dpGameMusicUrl', '@features/room/utils/dpGameMusicUrl'],
  ['@/utils/dpGameTheme', '@features/room/utils/dpGameTheme'],
  ['@/utils/dpRoomNetMultiplier', '@features/room/utils/dpRoomNetMultiplier'],
  ['@/utils/dpRoomPlayerLookup', '@features/room/utils/dpRoomPlayerLookup'],
  ['@/utils/guideMockTableData', '@features/room/utils/guideMockTableData'],
  // social utils
  ['@/utils/dpSocialStream', '@features/social/utils/dpSocialStream'],
  ['@/utils/dpSocialStreamClient', '@features/social/utils/dpSocialStreamClient'],
  ['@/utils/dpSocialDisplayName', '@features/social/utils/dpSocialDisplayName'],
  ['@/utils/dpCopySocialId', '@features/social/utils/dpCopySocialId'],
  ['@/utils/dpFriendPresence', '@features/social/utils/dpFriendPresence'],
  ['@/utils/dpFriendsInviteEligible', '@features/social/utils/dpFriendsInviteEligible'],
  ['@/utils/dpInviteFriendsDevLog', '@features/social/utils/dpInviteFriendsDevLog'],
  // npc utils
  ['@/utils/dpNpcDecisionTrace', '@features/npc/utils/dpNpcDecisionTrace'],
  ['@/utils/dpNpcDecisionTraceAuth', '@features/npc/utils/dpNpcDecisionTraceAuth'],
  ['@/utils/dpNpcDecisionTraceLabels', '@features/npc/utils/dpNpcDecisionTraceLabels'],
  ['@/utils/dpNpcDecisionTraceMatrix', '@features/npc/utils/dpNpcDecisionTraceMatrix'],
  // achievement utils
  ['@/utils/dpAchievementFormat', '@features/achievement/utils/dpAchievementFormat'],
  // download utils
  ['@/utils/dpDownloadAdminUnlock', '@features/download/utils/dpDownloadAdminUnlock'],
  ['@/utils/dpDownloadFileUrl', '@features/download/utils/dpDownloadFileUrl'],
  // quickmatch utils
  ['@/utils/dpQuickMatchExit', '@features/quickmatch/utils/dpQuickMatchExit'],
  // presence utils
  ['@/utils/dpSiteHeartbeat', '@features/presence/utils/dpSiteHeartbeat'],
]

function isReexport(content) {
  if (/^\/\*\* @deprecated/.test(content.trim()) && /from ['"]@/.test(content)) return true
  if (/^export \{ default \} from/.test(content.trim())) return true
  const stripped = content
    .split(/\r?\n/)
    .filter((l) => {
      const t = l.trim()
      return t && !t.startsWith('//') && !t.startsWith('/**') && !t.startsWith('*') && t !== '*/'
    })
  if (stripped.length <= 3 && /from ['"]@/.test(content) && /export/.test(content)) return true
  return false
}

function ensureDir(filePath) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true })
}

function walk(dir, acc = []) {
  if (!fs.existsSync(dir)) return acc
  for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
    const fp = path.join(dir, ent.name)
    if (ent.isDirectory()) walk(fp, acc)
    else if (/\.(vue|js)$/.test(ent.name)) acc.push(fp)
  }
  return acc
}

function rewriteImports(content) {
  let out = content
  // Longest match first to avoid partial replacements
  const sorted = [...IMPORT_REWRITES].sort((a, b) => b[0].length - a[0].length)
  for (const [from, to] of sorted) {
    out = out.split(from).join(to)
  }
  return out
}

function main() {
  let moved = 0
  let skipped = 0

  for (const [oldRel, newRel] of Object.entries(MOVES)) {
    const oldAbs = path.join(SRC, oldRel)
    const newAbs = path.join(SRC, newRel)
    if (!fs.existsSync(oldAbs)) {
      if (fs.existsSync(newAbs)) {
        skipped++
        continue
      }
      console.warn('MISSING source:', oldRel)
      continue
    }
    if (isReexport(fs.readFileSync(oldAbs, 'utf8'))) {
      console.warn('SKIP re-export source:', oldRel)
      continue
    }
    ensureDir(newAbs)
    let content = fs.readFileSync(oldAbs, 'utf8')
    content = rewriteImports(content)
    fs.writeFileSync(newAbs, content, 'utf8')
    moved++
    console.log('MOVED', oldRel, '->', newRel)
  }

  // Rewrite all source files
  const allFiles = walk(SRC).filter(
    (f) =>
      !f.includes(path.join('node_modules')) &&
      !f.includes(path.join('scripts', 'wave8'))
  )
  let rewritten = 0
  for (const fp of allFiles) {
    const orig = fs.readFileSync(fp, 'utf8')
    const next = rewriteImports(orig)
    if (next !== orig) {
      fs.writeFileSync(fp, next, 'utf8')
      rewritten++
    }
  }
  console.log('Rewritten files:', rewritten)

  // Delete compat shells in old dirs
  const OLD_DIRS = [
    'components',
    'utils',
    'constants',
    'mixins',
    'api',
    'store/modules',
  ]
  let deleted = 0
  for (const dir of OLD_DIRS) {
    const full = path.join(SRC, dir)
    if (!fs.existsSync(full)) continue
    for (const ent of fs.readdirSync(full)) {
      const fp = path.join(full, ent)
      if (!fs.statSync(fp).isFile()) continue
      if (!/\.(vue|js)$/.test(ent)) continue
      const content = fs.readFileSync(fp, 'utf8')
      const shouldDelete =
        isReexport(content) ||
        Object.keys(MOVES).includes(dir + '/' + ent) ||
        (dir === 'api' && ent.startsWith('api.'))
      if (shouldDelete) {
        fs.unlinkSync(fp)
        deleted++
        console.log('DELETED', dir + '/' + ent)
      }
    }
  }

  console.log('\nSummary: moved', moved, 'skipped(existing)', skipped, 'deleted shells', deleted)
}

main()
