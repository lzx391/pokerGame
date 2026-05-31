<template>
  <div
    class="dp-game-root"
    :data-dp-game-theme="effectiveThemeForCss"
  >
    <div class="dp-lobby-inner home-inner">
      <home-profile-modal
        :visible.sync="profileVisible"
        @saved="onProfileSaved"
        @avatar-updated="onAvatarUpdated"
      />

      <game-play-guide-modal
        :visible="playGuideVisible"
        :active-tab="playGuideTab"
        :items="handRankReference"
        :first-run="playGuideFirstRun"
        @close="onPlayGuideClose"
        @tab-change="playGuideTab = $event"
        @confirm="onPlayGuideConfirm"
      />

      <!-- ====== 顶部品牌横幅 ====== -->
      <header class="home-hero">
        <div class="home-hero__brand">
          <div class="home-hero__logo">
            <span class="home-hero__logo-paw" aria-hidden="true"></span>
            <h1 class="home-hero__title">猫咪牌局</h1>
          </div>
          <p class="home-hero__subtitle">约上好友，来一局优雅的猫咪扑克</p>
        </div>
        <div class="home-hero__user">
          <dp-user-avatar
            v-if="user && user.nickname"
            class="home-hero__avatar"
            :avatar-url="user.avatarUrl"
            :nickname="user.nickname"
            :cache-bust="userAvatarCacheBust || avatarCacheBustFromUpdatedAt(user.avatarUpdatedAt)"
            size="sm"
            :title="user.nickname"
          />
          <div class="home-hero__user-text">
            <span v-if="user && user.nickname" class="home-hero__username">{{ user.nickname }}</span>
            <span class="home-hero__user-label">在线</span>
          </div>
        </div>
      </header>

      <!-- ====== 主题与工具栏 ====== -->
      <div class="home-toolbar">
        <div class="dp-game-theme-row">
          <span class="dp-game-theme-row__label">界面主题</span>
          <dp-theme-picker
            :game-ui-theme="gameUiTheme"
            :theme-options="gameThemeOptions"
            @input-theme="onLobbyThemeChange($event)"
          />
          <dp-fluidity-toggle label-class="home-fluidity-toggle" />
        </div>
        <div class="home-toolbar__actions">
          <button type="button" class="home-tool-btn" @click="openPlayGuide(false)">玩法说明</button>
          <button type="button" class="home-tool-btn" @click="goButtonGuide">新手一分钟</button>
          <button type="button" class="home-tool-btn" @click="profileVisible = true">个人资料</button>
          <button type="button" class="home-tool-btn home-tool-btn--danger" @click="logout">退出</button>
        </div>
      </div>

      <!-- ====== 快捷入口卡片网格 ====== -->
      <section class="home-quick">
        <h3 class="home-section-title">
          快捷入口
        </h3>
        <div class="home-quick__grid">
          <!-- 快速匹配 - 主推操作 -->
          <button
            type="button"
            class="home-quick-card home-quick-card--primary"
            :disabled="quickMatchLoading && !quickMatchPolling"
            @click="onQuickMatchButtonClick"
          >
            <span class="home-quick-card__icon-wrap home-quick-card__icon-wrap--match">
              <i class="el-icon-s-flag"></i>
            </span>
            <span class="home-quick-card__label">{{ quickMatchPolling ? '取消匹配' : quickMatchLoading ? '匹配中…' : '快速匹配' }}</span>
            <span class="home-quick-card__desc">{{ quickMatchPolling ? '正在寻找对手…' : '即刻加入对局' }}</span>
          </button>

          <!-- 创建房间 -->
          <button type="button" class="home-quick-card home-quick-card--accent" @click="goCreateRoom">
            <span class="home-quick-card__icon-wrap home-quick-card__icon-wrap--create">
              <i class="el-icon-s-home"></i>
            </span>
            <span class="home-quick-card__label">创建房间</span>
            <span class="home-quick-card__desc">自定义规则开桌</span>
          </button>

          <!-- 历史对局 -->
          <button type="button" class="home-quick-card" @click="goHandHistory">
            <span class="home-quick-card__icon-wrap">
              <i class="el-icon-document"></i>
            </span>
            <span class="home-quick-card__label">历史对局</span>
            <span class="home-quick-card__desc">回顾精彩牌局</span>
          </button>

          <!-- 排行榜 -->
          <button type="button" class="home-quick-card" @click="goLeaderboard">
            <span class="home-quick-card__icon-wrap">
              <i class="el-icon-s-data"></i>
            </span>
            <span class="home-quick-card__label">排行榜</span>
            <span class="home-quick-card__desc">猫王争霸</span>
          </button>

          <!-- 曲库上传 -->
          <button type="button" class="home-quick-card" @click="goMusicUpload">
            <span class="home-quick-card__icon-wrap">
              <i class="el-icon-upload"></i>
            </span>
            <span class="home-quick-card__label">曲库上传</span>
            <span class="home-quick-card__desc">管理背景音乐</span>
          </button>

          <!-- 下载中心 -->
          <button type="button" class="home-quick-card" @click="goDownloadCenter">
            <span class="home-quick-card__icon-wrap">
              <i class="el-icon-download"></i>
            </span>
            <span class="home-quick-card__label">下载中心</span>
            <span class="home-quick-card__desc">获取客户端</span>
          </button>

          <!-- 邮箱 -->
          <el-badge
            :value="unreadCount"
            :hidden="!unreadCount"
            :max="99"
            class="home-quick-card__badge"
          >
            <button
              type="button"
              class="home-quick-card"
              aria-label="打开邮箱"
              title="邮箱（好友申请 / 进房邀请）"
              @click="openMailbox"
            >
              <span class="home-quick-card__icon-wrap">
                <i class="el-icon-message"></i>
              </span>
              <span class="home-quick-card__label">邮箱</span>
              <span class="home-quick-card__desc">申请与邀请</span>
            </button>
          </el-badge>

          <!-- 好友 -->
          <el-badge
            :value="friendChatUnreadTotal"
            :hidden="!friendChatUnreadTotal"
            :max="99"
            class="home-quick-card__badge"
          >
            <button
              type="button"
              class="home-quick-card"
              aria-label="好友列表"
              title="好友列表"
              @click="openFriendsDrawer"
            >
              <span class="home-quick-card__icon-wrap">
                <i class="el-icon-user"></i>
              </span>
              <span class="home-quick-card__label">好友</span>
              <span class="home-quick-card__desc">私信与跟随</span>
            </button>
          </el-badge>
        </div>
      </section>

      <!-- ====== 房间列表区 ====== -->
      <section class="home-rooms">
        <div class="home-rooms__head">
          <h3 class="home-section-title">
            房间列表
          </h3>
          <div class="home-rooms__head-right">
            <span v-if="useFilterQuery" class="home-rooms__mode-badge">条件筛选</span>
            <span v-else class="home-rooms__mode-badge home-rooms__mode-badge--dim">默认列表</span>
            <button
              type="button"
              class="home-rooms__refresh-btn"
              :disabled="roomsLoading"
              title="立即刷新"
              @click="refreshRoomList"
            >
              <i class="el-icon-refresh" :class="{ 'home-rooms__refresh-ico--spin': roomsLoading }"></i>
              刷新
            </button>
          </div>
        </div>

        <!-- 可折叠筛选栏 -->
        <div class="home-filters" :class="{ 'home-filters--open': filterOpen }">
          <button type="button" class="home-filters__toggle" @click="filterOpen = !filterOpen">
            <i :class="filterOpen ? 'el-icon-arrow-up' : 'el-icon-arrow-down'" class="home-filters__toggle-icon"></i>
            筛选条件
            <span v-if="filtersActiveFromForm()" class="home-filters__active-dot" title="有激活的筛选条件"></span>
          </button>
          <div v-if="filterOpen" class="home-filters__body">
            <div class="home-filters__row">
              <label class="home-filters__item">
                <span class="home-filters__label">房间号</span>
                <input
                  v-model.trim="filters.roomId"
                  type="text"
                  class="home-filters__input"
                  placeholder="精确匹配"
                  maxlength="32"
                  @keyup.enter="applyFilters"
                />
              </label>
              <label class="home-filters__item home-filters__item--num">
                <span class="home-filters__label">大猫鱼干 ≥</span>
                <input
                  v-model="filters.minBigBlind"
                  type="number"
                  min="0"
                  class="home-filters__input"
                  placeholder="可选"
                />
              </label>
              <label class="home-filters__item home-filters__item--num">
                <span class="home-filters__label">大猫鱼干 ≤</span>
                <input
                  v-model="filters.maxBigBlind"
                  type="number"
                  min="0"
                  class="home-filters__input"
                  placeholder="可选"
                />
              </label>
              <label class="home-filters__item home-filters__item--num">
                <span class="home-filters__label">人数 ≥</span>
                <input
                  v-model="filters.minPlayers"
                  type="number"
                  min="0"
                  class="home-filters__input"
                  placeholder="可选"
                />
              </label>
              <label class="home-filters__item home-filters__item--num">
                <span class="home-filters__label">人数 ≤</span>
                <input
                  v-model="filters.maxPlayers"
                  type="number"
                  min="0"
                  class="home-filters__input"
                  placeholder="可选"
                />
              </label>
              <label class="home-filters__item home-filters__item--select">
                <span class="home-filters__label">类型</span>
                <select v-model="filters.password" class="home-filters__select">
                  <option value="any">全部</option>
                  <option value="locked">仅密码房</option>
                  <option value="open">仅公开</option>
                </select>
              </label>
            </div>
            <div class="home-filters__actions">
              <button type="button" class="home-filters__btn home-filters__btn--search" @click="applyFilters">搜索</button>
              <button type="button" class="home-filters__btn home-filters__btn--reset" @click="resetFilters">重置</button>
            </div>
          </div>
        </div>

        <!-- 房间列表内容 -->
        <p v-if="roomsLoading" class="home-rooms__hint">
          <span class="home-rooms__spinner"></span> 加载中…
        </p>
        <p v-else-if="roomsError" class="home-rooms__hint home-rooms__hint--error">{{ roomsError }}</p>
        <p v-else-if="!roomDtos.length" class="home-rooms__empty">
          <i class="el-icon-s-home home-rooms__empty-icon"></i>
          <span>还没有房间哦，快去「创建房间」开一桌吧！</span>
        </p>
        <div v-else class="home-rooms__grid">
          <div
            v-for="roomDto in roomDtos"
            :key="roomDto.roomId"
            class="room-card"
            :class="{ 'room-card--locked': roomDto.passwordProtected }"
          >
            <div class="room-card__felt">
              <div class="room-card__header">
                <span class="room-card__room-id">#{{ roomDto.roomId }}</span>
                <i v-if="roomDto.passwordProtected" class="el-icon-lock room-card__lock" title="需要密码"></i>
              </div>
              <div class="room-card__body">
                <div class="room-card__players">
                  <span class="room-card__player-count">{{ roomDto.playerSize }}</span>
                  <span class="room-card__player-sep">/</span>
                  <span class="room-card__player-max">{{ roomDto.maxSeatCount != null ? roomDto.maxSeatCount : 9 }}</span>
                  <span class="room-card__player-label">人</span>
                </div>
                <div v-if="roomDto.smallBlindChips != null && roomDto.bigBlindChips != null" class="room-card__blinds">
                  <span class="room-card__blind-chip room-card__blind-chip--small">{{ roomDto.smallBlindChips }}</span>
                  <span class="room-card__blind-sep">/</span>
                  <span class="room-card__blind-chip room-card__blind-chip--big">{{ roomDto.bigBlindChips }}</span>
                  <span v-if="roomDto.startingStackBb" class="room-card__bb-label">{{ roomDto.startingStackBb }}×</span>
                </div>
              </div>
              <div class="room-card__footer">
                <div class="room-card__seats">
                  <span
                    v-for="i in (roomDto.maxSeatCount || 9)"
                    :key="'s' + i"
                    class="room-card__seat"
                    :class="{ 'is-filled': i <= roomDto.playerSize }"
                  ></span>
                </div>
                <button type="button" class="room-card__join" @click="joinRoom(roomDto)">加入</button>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>

    <!-- ====== 以下弹层保持原样 ====== -->
    <el-drawer
      title="好友列表"
      :visible.sync="friendsDrawerVisible"
      direction="rtl"
      append-to-body
      custom-class="home-friends-drawer"
      size="380px"
      @open="onFriendsDrawerOpen"
    >
      <div class="dp-social-sheet dp-social-sheet--drawer">
        <div class="home-friends-drawer__toolbar">
          <el-input
            v-model="friendsSearchInput"
            clearable
            size="small"
            placeholder="搜索好友昵称或 ID"
            prefix-icon="el-icon-search"
            class="home-friends-drawer__search"
            @input="onFriendsSearchInput"
            @clear="onFriendsSearchClear"
          />
          <el-button
            type="primary"
            size="small"
            plain
            class="home-friends-drawer__add-btn"
            @click="openAddFriendDialog"
          >
            添加好友
          </el-button>
        </div>
        <p v-if="friendsLoading" class="dp-social-sheet__hint">加载中…</p>
        <p v-else-if="friendsTotal === 0" class="dp-social-sheet__hint">
          {{ friendsQuery ? '没有匹配的好友' : '暂无好友' }}
        </p>
        <template v-else>
        <ul class="dp-social-list" role="list" aria-label="好友">
          <li
            v-for="f in friends"
            :key="'friend-' + f.userId"
            :class="['dp-social-list__item', friendPresenceRowClass(f)]"
          >
            <div
              class="dp-social-list__row dp-social-list__row--profile"
              role="button"
              tabindex="0"
              title="查看资料"
              @click="openPlayerSocialProfileFromFriend(f)"
              @keyup.enter="openPlayerSocialProfileFromFriend(f)"
            >
              <dp-user-avatar
                :avatar-url="f.avatarUrl"
                :nickname="friendPrimaryName(f)"
                :cache-bust="avatarCacheBustFromUpdatedAt(f.avatarUpdatedAt)"
                size="sm"
              />
              <div class="dp-social-list__text">
              <div class="dp-social-list__primary dp-social-list__primary--dm">
                <span>{{ friendPrimaryName(f) }}</span>
                <span
                  v-if="friendUnreadFor(f.userId)"
                  class="dp-social-list__dm-dot"
                  title="有未读私信"
                  aria-label="有未读私信"
                />
              </div>
              <div
                v-if="friendPresenceLine(f)"
                class="dp-social-list__presence"
              >{{ friendPresenceLine(f) }}</div>
              <button
                v-if="f.userId != null && f.userId !== ''"
                type="button"
                class="dp-social-list__idline"
                title="点击复制用户 ID"
                @click.stop="copySocialId(f.userId)"
              >
                ID {{ f.userId }}
              </button>
              </div>
            </div>
            <div class="dp-social-list__actions">
              <el-badge
                :value="friendUnreadFor(f.userId)"
                :hidden="!friendUnreadFor(f.userId)"
                :max="99"
                class="home-friend-chat-badge"
              >
                <el-button
                  type="primary"
                  plain
                  size="mini"
                  @click="openFriendChat(f)"
                >
                  对话
                </el-button>
              </el-badge>
              <el-button
                v-if="friendShowFollow(f)"
                type="success"
                plain
                size="mini"
                :loading="friendFollowBusy(f.userId)"
                :disabled="friendFollowOtherBusy(f.userId)"
                @click="onFollowFriend(f)"
              >
                跟随
              </el-button>
              <el-button
                type="danger"
                plain
                size="mini"
                :loading="friendRemoveBusy(f.userId)"
                :disabled="friendFollowAnyBusy()"
                @click="onRemoveFriend(f)"
              >
                删除
              </el-button>
            </div>
          </li>
        </ul>
        <el-pagination
          v-if="friendsTotal > friendsPageSize"
          class="home-friends-drawer__pager"
          layout="prev, pager, next"
          :total="friendsTotal"
          :page-size="friendsPageSize"
          :current-page="friendsPage"
          @current-change="onFriendsPageChange"
        />
        </template>
      </div>
    </el-drawer>

    <el-dialog
      title="添加好友"
      :visible.sync="addFriendDialogVisible"
      width="400px"
      append-to-body
      custom-class="home-add-friend-dialog"
      @open="onAddFriendDialogOpen"
      @closed="onAddFriendDialogClosed"
    >
      <div class="home-add-friend">
        <div class="home-add-friend__search-row">
          <el-input
            v-model="addFriendLookupInput"
            clearable
            placeholder="输入用户 ID 或完整昵称"
            @keyup.enter.native="onAddFriendLookup"
          />
          <el-button
            type="primary"
            :loading="addFriendLookupLoading"
            @click="onAddFriendLookup"
          >
            搜索
          </el-button>
        </div>
        <p v-if="addFriendLookupError" class="home-add-friend__error">{{ addFriendLookupError }}</p>
        <div
          v-if="addFriendLookupUser"
          class="home-add-friend__card home-add-friend__card--profile"
          role="button"
          tabindex="0"
          title="查看资料"
          @click="openPlayerSocialProfileFromLookup"
          @keyup.enter="openPlayerSocialProfileFromLookup"
        >
          <dp-user-avatar
            :avatar-url="addFriendLookupUser.avatarUrl"
            :nickname="addFriendLookupDisplayName"
            :cache-bust="avatarCacheBustFromUpdatedAt(addFriendLookupUser.avatarUpdatedAt)"
            size="sm"
          />
          <div class="home-add-friend__card-text">
            <div class="home-add-friend__card-name">{{ addFriendLookupDisplayName }}</div>
            <button
              type="button"
              class="dp-social-list__idline"
              title="点击复制用户 ID"
              @click.stop="copySocialId(addFriendLookupUser.userId)"
            >
              ID {{ addFriendLookupUser.userId }}
            </button>
          </div>
        </div>
        <div
          v-if="addFriendLookupUser && addFriendStatusHint"
          class="home-add-friend__status"
        >
          {{ addFriendStatusHint }}
        </div>
        <el-button
          v-if="addFriendLookupUser && addFriendCanSend"
          type="primary"
          class="home-add-friend__send"
          :loading="addFriendSendBusy"
          @click="onAddFriendSendRequest"
        >
          发送好友申请
        </el-button>
      </div>
    </el-dialog>

    <el-dialog
      title="消息"
      :visible.sync="mailboxVisible"
      width="560px"
      append-to-body
      custom-class="home-mailbox-dialog"
      @open="onMailboxDialogOpen"
      @close="onMailboxDialogClose"
    >
      <div class="dp-social-sheet dp-social-sheet--dialog">
        <p v-if="mailboxLoading" class="dp-social-sheet__hint">加载中…</p>
        <template v-else>
          <section class="home-mailbox__sec" aria-label="好友申请">
            <h4 class="home-mailbox__subtitle">好友申请</h4>
            <p v-if="!friendRequests.length" class="home-mailbox__empty">暂无待处理申请</p>
            <div
              v-for="row in friendRequests"
              :key="'fr-' + row.id"
              class="dp-social-list__item"
            >
              <div class="dp-social-list__text">
                <div class="dp-social-list__primary">{{ friendRequestPrimaryName(row) }}</div>
                <div class="dp-social-list__secondary">希望加你为好友 · {{ row.createdAt || '—' }}</div>
                <button
                  v-if="row.fromUserId != null"
                  type="button"
                  class="dp-social-list__idline"
                  title="点击复制申请者 ID"
                  @click="copySocialId(row.fromUserId)"
                >
                  ID {{ row.fromUserId }}
                </button>
              </div>
              <div class="dp-social-list__actions">
                <el-button
                  type="primary"
                  size="mini"
                  :loading="mailboxActionBusy(row.id, 'friend')"
                  @click="onAcceptFriend(row.id)"
                >同意</el-button>
                <el-button
                  size="mini"
                  :loading="mailboxActionBusy(row.id, 'friend')"
                  @click="onRejectFriend(row.id)"
                >拒绝</el-button>
              </div>
            </div>
          </section>
          <section class="home-mailbox__sec" aria-label="进房邀请">
            <h4 class="home-mailbox__subtitle">进房邀请</h4>
            <p v-if="!roomInvites.length" class="home-mailbox__empty">暂无有效邀请</p>
            <div
              v-for="row in roomInvites"
              :key="'riv-' + row.id"
              class="dp-social-list__item"
            >
              <div class="dp-social-list__text">
                <div class="dp-social-list__primary">{{ roomInvitePrimaryName(row) }}</div>
                <div class="dp-social-list__secondary">
                  房间 {{ row.roomId }} · 剩余 {{ inviteRemainingLabel(row) }}
                </div>
                <button
                  v-if="row.inviterUserId != null"
                  type="button"
                  class="dp-social-list__idline"
                  title="点击复制房主 ID"
                  @click="copySocialId(row.inviterUserId)"
                >
                  邀请人 ID {{ row.inviterUserId }}
                </button>
              </div>
              <div class="dp-social-list__actions">
                <el-button
                  type="primary"
                  size="mini"
                  :loading="mailboxActionBusy(row.id, 'room')"
                  @click="onAcceptRoomInvite(row)"
                >同意并进房</el-button>
                <el-button
                  size="mini"
                  :loading="mailboxActionBusy(row.id, 'room')"
                  @click="onRejectRoomInvite(row.id)"
                >拒绝</el-button>
              </div>
            </div>
          </section>
        </template>
      </div>
    </el-dialog>

    <friend-chat-dialog
      :visible.sync="friendChatVisible"
      :peer-user-id="friendChatPeerId"
      :peer-display-name="friendChatPeerName"
      :peer-avatar-url="friendChatPeerAvatar"
      :peer-avatar-updated-at="friendChatPeerAvatarUpdatedAt"
      :peer-unread-count="friendChatPeerUnread"
      @closed="onFriendChatClosed"
    />

    <game-player-social-sheet
      v-if="playerSocialOpen && playerSocialTarget"
      :visible="true"
      :target="playerSocialTarget"
      @close="closePlayerSocialSheet"
      @view-hand-history-with-opponent="openOpponentHandHistoryFromSocial"
    />

    <game-hand-history-modal
      :visible="opponentHandHistoryOpen"
      list-mode="withOpponent"
      :other-user-id="opponentHandHistoryUserId"
      :opponent-display-name="opponentHandHistoryDisplayName"
      :game-ui-theme="gameUiTheme"
      @close="closeOpponentHandHistoryModal"
    />
  </div>
</template>

<script>
import '@/styles/dp-game-themes.css'
import '@/styles/dp-lobby-shell.css'
import dpLobbyThemeMixin from '@/mixins/dpLobbyThemeMixin'
import { ensureDpUserIdInStorage } from '@/utils/dpEnsureUserId'
import { dpFriendPresenceRowClass, dpFriendPresenceStatusText, dpFriendPresenceBucket } from '@/utils/dpFriendPresence'
import { dpSocialDisplayNickname } from '@/utils/dpSocialDisplayName'
import { dpResultSuccess, dpResultData, dpResultMessage, dpAxiosErrorMessage } from '@/utils/dpApiResult'
import { dpSocialApi } from '@/api/api.dpSocial'
import { copySocialId as copySocialIdToClipboard } from '@/utils/dpCopySocialId'
import GamePlayGuideModal from '@/components/GamePlayGuideModal.vue'
import HomeProfileModal from '@/components/HomeProfileModal.vue'
import FriendChatDialog from '@/components/FriendChatDialog.vue'
import GamePlayerSocialSheet from '@/components/GamePlayerSocialSheet.vue'
import GameHandHistoryModal from '@/components/GameHandHistoryModal.vue'
import DpUserAvatar from '@/components/DpUserAvatar.vue'
import DpFluidityToggle from '@/components/DpFluidityToggle.vue'
import { buildSocialStreamUrl } from '@/utils/dpSocialStream'
import { mapGetters, mapState, mapActions } from 'vuex'
import {
  peekCatTutorialRequested,
  clearCatTutorialSessionFlag,
  isCatTutorialDismissedPermanently,
  setCatTutorialDismissedPermanently
} from '@/constants/dpCatThemeCopy'
import { exitLobbyQuickMatchSilently } from '@/utils/dpLobbyQuickMatchExit'
import { postQuickMatchCancel2 } from '@/utils/dpQuickMatchExit'
import { prefetchGameChunk, navigateToGame } from '@/utils/dpPrefetchGameRoute'
import { prefetchAvatarUrls } from '@/utils/dpAvatarPrefetch'
import { avatarCacheBustFromUpdatedAt } from '@/utils/dpAvatarUrl'

export default {
  components: {
    GamePlayGuideModal,
    HomeProfileModal,
    FriendChatDialog,
    GamePlayerSocialSheet,
    GameHandHistoryModal,
    DpUserAvatar,
    DpFluidityToggle
  },
  mixins: [dpLobbyThemeMixin],
  data() {
    return {
      profileVisible: false,
      playGuideVisible: false,
      playGuideTab: 'flow',
      playGuideFirstRun: false,
      user: {},
      userAvatarCacheBust: '',
      roomDtos: [],
      roomsLoading: true,
      roomsError: '',
      filterOpen: false,
      filters: {
        roomId: '',
        minBigBlind: '',
        maxBigBlind: '',
        minPlayers: '',
        maxPlayers: '',
        password: 'any'
      },
      useFilterQuery: false,
      quickMatchLoading: false,
      quickMatchPolling: false,
      quickMatchWs: null,
      quickMatchWsSession: 0,
      quickMatchWsNoReconnect: false,
      quickMatchWsReconnectTimer: null,
      quickMatchWsReconnectAttempt: 0,
      friendsDrawerVisible: false,
      friendsSearchInput: '',
      friendsSearchDebounceTimer: null,
      addFriendDialogVisible: false,
      addFriendLookupInput: '',
      addFriendLookupUser: null,
      addFriendLookupAddStatus: '',
      addFriendLookupLoading: false,
      addFriendLookupError: '',
      addFriendSendBusy: false,
      mailboxVisible: false,
      mailboxTickSeconds: 0,
      mailboxTickTimer: null,
      socialEventSource: null,
      socialEsSession: 0,
      socialEsReconnectTimer: null,
      socialEsReconnectAttempt: 0,
      friendChatVisible: false,
      friendChatPeerId: null,
      friendChatPeerName: '',
      friendChatPeerAvatar: '',
      friendChatPeerAvatarUpdatedAt: null,
      friendChatPeerUnread: 0,
      friendFollowBusyUserId: null,
      playerSocialOpen: false,
      playerSocialTarget: null,
      opponentHandHistoryOpen: false,
      opponentHandHistoryUserId: null,
      opponentHandHistoryDisplayName: ''
    }
  },
  computed: {
    ...mapGetters('dpGame', ['handRankReference']),
    ...mapState('dpMailbox', [
      'unreadCount',
      'friendChatUnreadTotal',
      'friendRequests',
      'roomInvites',
      'friends',
      'friendsTotal',
      'friendsPage',
      'friendsPageSize',
      'friendsQuery',
      'mailboxLoading',
      'friendsLoading',
      'actionBusyId'
    ]),
    ...mapGetters('dpMailbox', ['friendUnreadForUser']),
    addFriendLookupDisplayName() {
      var u = this.addFriendLookupUser
      return dpSocialDisplayNickname(u && u.nickname, u && u.userId, '未知用户')
    },
    addFriendStatusHint() {
      var s = this.addFriendLookupAddStatus
      if (s === 'SELF') return '不能添加自己'
      if (s === 'ALREADY_FRIENDS') return '已是好友'
      if (s === 'PENDING_OUTBOUND') return '申请已发送'
      if (s === 'PENDING_INBOUND') return '对方已向您发来申请，请在大厅邮箱中处理'
      return ''
    },
    addFriendCanSend() {
      return this.addFriendLookupAddStatus === 'CAN_ADD'
    },
    pageSize() {
      return 20
    }
  },
  watch: {
    '$route.path': function (path) {
      if (path === '/home' && this.user && this.user.token) {
        this.connectSocialStream()
      } else {
        this.closeSocialStream()
      }
    },
    quickMatchPolling: function (val) {
      if (val) prefetchGameChunk()
    }
  },
  async created() {
    try {
      await ensureDpUserIdInStorage(this.$http)
      var raw = localStorage.getItem('userInfo')
      this.user = raw ? JSON.parse(raw) : {}
    } catch (e) {
      this.user = {}
    }
    this.getRooms()
    this.timer = setInterval(() => {
      this.getRooms()
    }, 10000)
    if (this.user && this.user.token) {
      this.bootstrapSocial()
      this.loadCurrentUserAvatar()
      prefetchGameChunk()
    }
  },
  mounted() {
    if (this.user && this.user.token) {
      prefetchGameChunk()
    }
    if (peekCatTutorialRequested()) {
      clearCatTutorialSessionFlag()
      if (!isCatTutorialDismissedPermanently()) {
        this.openPlayGuide(true)
      }
    }
  },
  beforeDestroy() {
    if (this.timer) {
      console.log('正在销毁定时器')
      clearInterval(this.timer)
      this.timer = null
    }
    if (this.friendsSearchDebounceTimer != null) {
      clearTimeout(this.friendsSearchDebounceTimer)
      this.friendsSearchDebounceTimer = null
    }
    this.closeSocialStream()
    this.stopMailboxTick()
    this.quickMatchPolling = false
    this.quickMatchLoading = false
    this.disconnectQuickMatchWs()
    postQuickMatchCancel2(this.$http, this.user)
  },
  methods: {
    avatarCacheBustFromUpdatedAt,
    ...mapActions('dpMailbox', [
      'fetchUnreadCount',
      'fetchFriendChatUnreadSummary',
      'fetchNotifySummary',
      'applyNotifyPayload',
      'applyFriendPresencePayload',
      'fetchMailbox',
      'fetchFriends',
      'removeFriend',
      'acceptFriend',
      'rejectFriend',
      'acceptRoomInvite',
      'rejectRoomInvite',
      'followFriendToTheirRoom'
    ]),
    friendRemoveBusy(userId) {
      var id = userId != null ? String(userId) : ''
      return !!id && this.actionBusyId === 'rm:' + id
    },
    mailboxActionBusy(rowId, kind) {
      var id = rowId != null ? String(rowId) : ''
      if (!this.actionBusyId || !id) return false
      return kind === 'friend'
        ? this.actionBusyId === 'f:' + id
        : this.actionBusyId === 'r:' + id
    },
    friendPrimaryName(f) {
      return dpSocialDisplayNickname(f && f.nickname, f && f.userId, '未知好友')
    },
    friendPresenceRowClass(f) {
      return dpFriendPresenceRowClass(f)
    },
    friendPresenceLine(f) {
      return dpFriendPresenceStatusText(f)
    },
    friendShowFollow(f) {
      return dpFriendPresenceBucket(f) === 'in_game'
    },
    friendFollowBusy(userId) {
      var uid = userId != null ? Number(userId) : 0
      return isFinite(uid) && uid > 0 && this.friendFollowBusyUserId === uid
    },
    friendFollowOtherBusy(userId) {
      if (this.friendFollowBusyUserId == null) return false
      var uid = userId != null ? Number(userId) : 0
      return isFinite(uid) && uid > 0 && this.friendFollowBusyUserId !== uid
    },
    friendFollowAnyBusy() {
      return this.friendFollowBusyUserId != null
    },
    friendRequestPrimaryName(row) {
      return dpSocialDisplayNickname(row && row.fromNickname, row && row.fromUserId, '未知用户')
    },
    roomInvitePrimaryName(row) {
      return dpSocialDisplayNickname(row && row.inviterNickname, row && row.inviterUserId, '未知用户')
    },
    copySocialId(raw) {
      var self = this
      copySocialIdToClipboard(raw, {
        onSuccess: function () {
          if (self.$message) self.$message.success('已复制 ID')
        }
      })
    },
    openPlayerSocialProfileFromFriend(f) {
      if (!f) return
      var nickname = f.nickname
      if (!nickname) nickname = this.friendPrimaryName(f)
      this.openPlayerSocialProfile({ nickname: nickname, userId: f.userId })
    },
    openPlayerSocialProfileFromLookup() {
      var u = this.addFriendLookupUser
      if (!u) return
      var nickname = u.nickname
      if (!nickname) nickname = this.addFriendLookupDisplayName
      this.openPlayerSocialProfile({ nickname: nickname, userId: u.userId })
    },
    /**
     * 大厅好友/搜索：打开与对局一致的玩家资料卡（GamePlayerSocialSheet）。
     * @param {{ nickname: string, userId?: number|string }} payload
     */
    async openPlayerSocialProfile(payload) {
      var nickname = payload && payload.nickname
      if (!nickname) return

      if (this.user && nickname === this.user.nickname) {
        this.profileVisible = true
        return
      }

      var uid = payload && payload.userId != null ? Number(payload.userId) : 0
      if (!uid || uid <= 0 || isNaN(uid)) {
        try {
          var res = await dpSocialApi(this.$http).lookupUser(String(nickname))
          if (dpResultSuccess(res.data)) {
            var user = (dpResultData(res.data) || {}).user
            var looked = user && user.userId != null ? Number(user.userId) : 0
            if (looked > 0 && !isNaN(looked)) uid = looked
          }
        } catch (e) {
          /* 静默 */
        }
      }

      if (!uid || uid <= 0 || isNaN(uid)) {
        this.$message.warning('无法获取该玩家的账号信息')
        return
      }

      this.playerSocialTarget = { nickname: nickname, userId: uid }
      this.playerSocialOpen = true
    },
    closePlayerSocialSheet() {
      this.playerSocialOpen = false
      this.playerSocialTarget = null
    },
    openOpponentHandHistoryFromSocial(payload) {
      if (!payload || payload.userId == null || payload.userId === '') return
      var uid = Number(payload.userId)
      if (!uid || uid <= 0 || isNaN(uid)) return
      this.closePlayerSocialSheet()
      this.opponentHandHistoryUserId = uid
      this.opponentHandHistoryDisplayName = payload.displayName || ''
      this.opponentHandHistoryOpen = true
    },
    closeOpponentHandHistoryModal() {
      this.opponentHandHistoryOpen = false
      this.opponentHandHistoryUserId = null
      this.opponentHandHistoryDisplayName = ''
    },
    inviteRemainingLabel(inv) {
      var base = inv && inv.remainingSeconds != null ? Number(inv.remainingSeconds) : NaN
      if (!isFinite(base)) return '—'
      var sec = Math.max(0, Math.floor(base) - this.mailboxTickSeconds)
      return sec <= 0 ? '已过期' : sec + ' 秒'
    },
    friendUnreadFor(userId) {
      return this.friendUnreadForUser(userId)
    },
    bootstrapSocial() {
      var http = this.$http
      var self = this
      this.fetchNotifySummary({ http }).catch(function () {
        return Promise.all([
          self.fetchUnreadCount({ http }),
          self.fetchFriendChatUnreadSummary({ http })
        ])
      })
      this.fetchFriends({ http }).catch(() => {})
      this.connectSocialStream()
    },
    closeSocialStream() {
      this.socialEsSession++
      if (this.socialEsReconnectTimer != null) {
        clearTimeout(this.socialEsReconnectTimer)
        this.socialEsReconnectTimer = null
      }
      var es = this.socialEventSource
      var notifyHandler = this._socialNotifyHandler
      var presenceHandler = this._socialFriendPresenceHandler
      this.socialEventSource = null
      this._socialNotifyHandler = null
      this._socialFriendPresenceHandler = null
      if (es) {
        es.onopen = null
        es.onerror = null
        es.onmessage = null
        if (notifyHandler) {
          try { es.removeEventListener('notify', notifyHandler) } catch (e) { /* ignore */ }
        }
        if (presenceHandler) {
          try { es.removeEventListener('friendPresence', presenceHandler) } catch (e) { /* ignore */ }
        }
        try { es.close() } catch (e) { /* ignore */ }
      }
    },
    scheduleSocialStreamReconnect() {
      if (this.socialEsReconnectTimer != null) return
      if (!this.user || !this.user.token) return
      if (this.$route.path !== '/home') return
      var self = this
      var attempt = this.socialEsReconnectAttempt
      var delay = Math.min(30000, 1000 * Math.pow(2, attempt))
      this.socialEsReconnectTimer = setTimeout(function () {
        self.socialEsReconnectTimer = null
        self.socialEsReconnectAttempt++
        self.connectSocialStream(true)
      }, delay)
    },
    connectSocialStream(isReconnect) {
      if (!this.user || !this.user.token) return
      if (this.$route.path !== '/home') return
      var url = buildSocialStreamUrl(this.user.token)
      if (!url) return
      this.closeSocialStream()
      var session = ++this.socialEsSession
      var self = this
      var es
      try { es = new EventSource(url) } catch (e) { this.scheduleSocialStreamReconnect(); return }
      this.socialEventSource = es
      this._socialNotifyHandler = function (ev) {
        if (self.socialEsSession !== session) return
        self.onSocialNotify(ev && ev.data)
      }
      this._socialFriendPresenceHandler = function (ev) {
        if (self.socialEsSession !== session) return
        self.onSocialFriendPresence(ev && ev.data)
      }
      var onNotify = this._socialNotifyHandler
      var onFriendPresence = this._socialFriendPresenceHandler
      es.addEventListener('notify', onNotify)
      es.addEventListener('friendPresence', onFriendPresence)
      es.onmessage = onNotify
      es.onopen = function () {
        if (self.socialEsSession !== session) return
        self.socialEsReconnectAttempt = 0
        if (isReconnect) {
          self.fetchNotifySummary({ http: self.$http }).catch(function () {})
        }
      }
      es.onerror = function () {
        if (self.socialEsSession !== session) return
        try { es.close() } catch (err) { /* ignore */ }
        if (self.socialEventSource === es) self.socialEventSource = null
        self.scheduleSocialStreamReconnect()
      }
    },
    onSocialNotify(raw) {
      if (raw) {
        try {
          var parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
          if (process.env.NODE_ENV !== 'production') {
            console.info('[social-sse] notify received', parsed)
          }
          this.applyNotifyPayload(parsed)
          return
        } catch (e) {
          if (process.env.NODE_ENV !== 'production') {
            console.warn('[social-sse] notify parse failed', raw, e)
          }
        }
      }
      this.fetchNotifySummary({ http: this.$http }).catch(() => {})
    },
    onSocialFriendPresence(raw) {
      if (!raw) return
      try {
        var parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
        if (process.env.NODE_ENV !== 'production') {
          console.info('[social-sse] friendPresence received', parsed)
        }
        this.applyFriendPresencePayload(parsed)
      } catch (e) {
        if (process.env.NODE_ENV !== 'production') {
          console.warn('[social-sse] friendPresence parse failed', raw, e)
        }
      }
    },
    openFriendChat(f) {
      var uid = f && f.userId != null ? Number(f.userId) : 0
      if (!isFinite(uid) || uid <= 0) return
      this.friendChatPeerId = uid
      this.friendChatPeerName = this.friendPrimaryName(f)
      this.friendChatPeerAvatar = (f && f.avatarUrl) || ''
      this.friendChatPeerAvatarUpdatedAt = f && f.avatarUpdatedAt != null ? f.avatarUpdatedAt : null
      this.friendChatPeerUnread = this.friendUnreadFor(uid)
      this.friendChatVisible = true
    },
    onFriendChatClosed() {
      this.friendChatPeerId = null
      this.friendChatPeerName = ''
      this.friendChatPeerAvatar = ''
      this.friendChatPeerAvatarUpdatedAt = null
      this.friendChatPeerUnread = 0
      this.fetchFriendChatUnreadSummary({ http: this.$http }).catch(() => {})
    },
    startMailboxTick() {
      var self = this
      this.stopMailboxTick()
      this.mailboxTickSeconds = 0
      this.mailboxTickTimer = setInterval(function () { self.mailboxTickSeconds++ }, 1000)
    },
    stopMailboxTick() {
      if (this.mailboxTickTimer != null) {
        clearInterval(this.mailboxTickTimer)
        this.mailboxTickTimer = null
      }
      this.mailboxTickSeconds = 0
    },
    async onMailboxDialogOpen() {
      this.mailboxTickSeconds = 0
      var http = this.$http
      await this.fetchMailbox({ http })
      await this.fetchUnreadCount({ http })
      this.startMailboxTick()
    },
    onMailboxDialogClose() {
      this.stopMailboxTick()
      this.fetchUnreadCount({ http: this.$http }).catch(() => {})
    },
    async openMailbox() {
      if (!this.user || !this.user.token) { alert('请先登录'); return }
      this.mailboxVisible = true
    },
    async openFriendsDrawer() {
      if (!this.user || !this.user.token) { alert('请先登录'); return }
      this.friendsDrawerVisible = true
    },
    async onFriendsDrawerOpen() {
      if (!this.user || !this.user.token) return
      this.friendsSearchInput = this.friendsQuery || ''
      var http = this.$http
      var r = await this.fetchFriends({ http, page: 1, pageSize: this.friendsPageSize, q: this.friendsQuery })
      if (r && r.ok === false && r.message) { alert(r.message) }
    },
    onFriendsSearchInput() {
      var self = this
      if (this.friendsSearchDebounceTimer != null) { clearTimeout(this.friendsSearchDebounceTimer) }
      this.friendsSearchDebounceTimer = setTimeout(function () {
        self.friendsSearchDebounceTimer = null
        self.reloadFriendsPageOne()
      }, 350)
    },
    onFriendsSearchClear() { this.friendsSearchInput = ''; this.reloadFriendsPageOne() },
    async reloadFriendsPageOne() {
      if (!this.user || !this.user.token) return
      var q = (this.friendsSearchInput || '').trim()
      var r = await this.fetchFriends({ http: this.$http, page: 1, pageSize: this.friendsPageSize, q: q })
      if (r && r.ok === false && r.message && this.$message) { this.$message.error(r.message) }
    },
    async onFriendsPageChange(page) {
      if (!this.user || !this.user.token) return
      var r = await this.fetchFriends({ http: this.$http, page: page, pageSize: this.friendsPageSize, q: (this.friendsSearchInput || '').trim() })
      if (r && r.ok === false && r.message && this.$message) { this.$message.error(r.message) }
    },
    openAddFriendDialog() {
      if (!this.user || !this.user.token) { alert('请先登录'); return }
      this.addFriendDialogVisible = true
    },
    onAddFriendDialogOpen() {
      this.addFriendLookupInput = ''
      this.addFriendLookupUser = null
      this.addFriendLookupAddStatus = ''
      this.addFriendLookupError = ''
      this.addFriendSendBusy = false
    },
    onAddFriendDialogClosed() { this.addFriendLookupLoading = false; this.addFriendSendBusy = false },
    async onAddFriendLookup() {
      var q = (this.addFriendLookupInput || '').trim()
      if (!q) { this.addFriendLookupError = '请输入用户 id 或昵称'; this.addFriendLookupUser = null; this.addFriendLookupAddStatus = ''; return }
      this.addFriendLookupLoading = true
      this.addFriendLookupError = ''
      this.addFriendLookupUser = null
      this.addFriendLookupAddStatus = ''
      try {
        var api = dpSocialApi(this.$http)
        var res = await api.lookupUser(q)
        var body = res.data
        if (!dpResultSuccess(body)) { this.addFriendLookupError = dpResultMessage(body) || '未找到用户'; return }
        var d = dpResultData(body) || {}
        this.addFriendLookupUser = d.user || null
        this.addFriendLookupAddStatus = d.addStatus != null ? String(d.addStatus) : ''
        if (!this.addFriendLookupUser) { this.addFriendLookupError = '未找到用户' }
      } catch (e) {
        this.addFriendLookupError = dpAxiosErrorMessage(e, '搜索失败')
      } finally { this.addFriendLookupLoading = false }
    },
    async onAddFriendSendRequest() {
      if (!this.addFriendCanSend || !this.addFriendLookupUser) return
      var uid = Number(this.addFriendLookupUser.userId)
      if (!uid || uid <= 0) return
      this.addFriendSendBusy = true
      try {
        var res = await this.$http.post('/dp/friends/requests', { toUserId: uid })
        if (dpResultSuccess(res.data)) { this.addFriendLookupAddStatus = 'PENDING_OUTBOUND'; this.$message.success((res.data.data && res.data.data.message) || '已发送'); return }
        var msg = dpResultMessage(res.data)
        if (msg.indexOf('对方已向您发来申请') !== -1) { this.addFriendLookupAddStatus = 'PENDING_INBOUND'; this.$message.info(msg + '，请在大厅邮箱中处理。'); return }
        if (msg.indexOf('已是好友') !== -1) {
          this.addFriendLookupAddStatus = 'ALREADY_FRIENDS'
          await this.fetchFriends({ http: this.$http, page: this.friendsPage, pageSize: this.friendsPageSize, q: (this.friendsSearchInput || '').trim() })
          this.$message.info(msg); return
        }
        if (msg.indexOf('申请已存在') !== -1) { this.addFriendLookupAddStatus = 'PENDING_OUTBOUND'; this.$message.success(msg); return }
        this.$message.error(msg)
      } catch (err) { this.$message.error(dpAxiosErrorMessage(err, '无法发送好友申请，请稍后重试')) }
      finally { this.addFriendSendBusy = false }
    },
    async onFollowFriend(f) {
      var uid = f && f.userId != null ? Number(f.userId) : 0
      if (!isFinite(uid) || uid <= 0) return
      if (this.friendFollowBusyUserId != null) return
      if (dpFriendPresenceBucket(f) !== 'in_game') return
      if (!this.user || !this.user.token) { alert('请先登录'); return }
      this.friendFollowBusyUserId = uid
      try {
        await this.exitQuickMatchBeforeRoomAction()
        const res = await this.followFriendToTheirRoom({ http: this.$http, friendUserId: uid })
        if (!res || !res.ok) return
        var rid = res.roomId != null && res.roomId !== '' ? String(res.roomId).trim() : ''
        if (!rid) { alert('未返回房间号'); return }
        this.friendsDrawerVisible = false
        await navigateToGame(this.$router, rid)
      } finally { this.friendFollowBusyUserId = null }
    },
    async onRemoveFriend(f) {
      var uid = f && f.userId != null ? Number(f.userId) : 0
      if (!isFinite(uid) || uid <= 0) return
      var label = this.friendPrimaryName(f)
      try {
        await this.$confirm('确定删除好友「' + label + '」？删除后须重新添加互为好友才可再发进房邀请。', '删除好友', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
      } catch (e) { return }
      var r = await this.removeFriend({ http: this.$http, friendUserId: uid })
      if (r && r.ok) { this.$message.success((r.message) || '已删除') }
      else { this.$message.error((r && r.message) || '删除失败') }
    },
    async onAcceptFriend(id) { await this.acceptFriend({ http: this.$http, id }) },
    async onRejectFriend(id) { await this.rejectFriend({ http: this.$http, id }) },
    async onAcceptRoomInvite(row) {
      const id = row && row.id
      const res = await this.acceptRoomInvite({ http: this.$http, id })
      if (!res || !res.ok) return
      var rid = res.roomId || (row && row.roomId) || ''
      if (!rid) { alert('未返回房间号'); return }
      this.mailboxVisible = false
      await navigateToGame(this.$router, rid)
    },
    async onRejectRoomInvite(id) { await this.rejectRoomInvite({ http: this.$http, id }) },
    openPlayGuide(firstRun) { this.playGuideFirstRun = !!firstRun; this.playGuideTab = 'flow'; this.playGuideVisible = true },
    onPlayGuideClose() { this.playGuideVisible = false; this.playGuideFirstRun = false },
    onPlayGuideConfirm(payload) {
      if (payload && payload.dontShowAgain) { setCatTutorialDismissedPermanently() }
      this.onPlayGuideClose()
    },
    async loadCurrentUserAvatar() {
      try {
        const res = await this.$http.get('/dpUser/profile')
        const body = res.data
        if (!dpResultSuccess(body)) return
        const profile = (dpResultData(body) || {}).profile || {}
        if (profile.avatarUrl) { this.$set(this.user, 'avatarUrl', profile.avatarUrl) }
        if (profile.avatarUpdatedAt != null) { this.$set(this.user, 'avatarUpdatedAt', profile.avatarUpdatedAt) }
        this.prefetchCurrentUserAvatars()
      } catch (e) { /* ignore */ }
    },
    prefetchCurrentUserAvatars() {
      if (!this.user || !this.user.avatarUrl) return
      var bust = this.userAvatarCacheBust || avatarCacheBustFromUpdatedAt(this.user.avatarUpdatedAt)
      prefetchAvatarUrls([{ avatarUrl: this.user.avatarUrl, cacheBust: bust }], { prefetchFull: true }).catch(function () {})
    },
    onAvatarUpdated(payload) {
      if (!payload) return
      if (payload.avatarUrl) { this.$set(this.user, 'avatarUrl', payload.avatarUrl) }
      if (payload.avatarUpdatedAt != null) { this.$set(this.user, 'avatarUpdatedAt', payload.avatarUpdatedAt) }
      if (payload.cacheBust) { this.userAvatarCacheBust = payload.cacheBust }
      this.prefetchCurrentUserAvatars()
      try {
        var raw = localStorage.getItem('userInfo')
        var stored = raw ? JSON.parse(raw) : {}
        if (payload.avatarUrl) stored.avatarUrl = payload.avatarUrl
        localStorage.setItem('userInfo', JSON.stringify(stored))
      } catch (e) { /* ignore */ }
    },
    onProfileSaved(payload) {
      if (!payload) return
      if (payload.nickname) { this.user.nickname = payload.nickname }
      if (payload.token) { this.user.token = payload.token }
      try {
        var raw = localStorage.getItem('userInfo')
        var stored = raw ? JSON.parse(raw) : {}
        if (payload.nickname) stored.nickname = payload.nickname
        if (payload.token) stored.token = payload.token
        if (payload.newPassword) stored.password = payload.newPassword
        else if (payload.passwordForStorage) stored.password = payload.passwordForStorage
        localStorage.setItem('userInfo', JSON.stringify(stored))
      } catch (e) { /* ignore */ }
    },
    logout() { this.closeSocialStream(); localStorage.removeItem('userInfo'); this.$router.push('/') },
    goHandHistory() { this.$router.push('/hand-history') },
    goLeaderboard() { this.$router.push('/leaderboard') },
    goButtonGuide() { this.$router.push({ name: 'GameButtonGuide' }) },
    goMusicUpload() { this.$router.push('/music-upload') },
    goDownloadCenter() { this.$router.push('/download-center') },
    async goCreateRoom() { await this.exitQuickMatchBeforeRoomAction(); this.$router.push('/create-room') },
    async exitQuickMatchBeforeRoomAction() {
      var self = this
      await exitLobbyQuickMatchSilently(this.$http, this.user, {
        resetQuickMatchUiFlags: function () { self.quickMatchPolling = false; self.quickMatchLoading = false },
        disconnectQuickMatchWs: function () { self.disconnectQuickMatchWs() }
      })
    },
    async onQuickMatchButtonClick() {
      if (this.quickMatchPolling) { await this.exitQuickMatchBeforeRoomAction(); return }
      await this.quickMatch()
    },
    quickMatchWsBaseUrl() {
      if (typeof window !== 'undefined' && window.dpElectron && window.dpElectron.serverUrl) {
        var url = window.dpElectron.serverUrl.replace(/\/+$/, '')
        return url.replace(/^https?:/, url.indexOf('https:') === 0 ? 'wss:' : 'ws:')
      }
      var secure = window.location.protocol === 'https:'
      return (secure ? 'wss:' : 'ws:') + '//' + window.location.host
    },
    disconnectQuickMatchWs() {
      this.quickMatchWsNoReconnect = true
      this.clearQuickMatchWsReconnectTimer()
      this.quickMatchWsReconnectAttempt = 0
      this.quickMatchWsSession++
      var w = this.quickMatchWs; this.quickMatchWs = null
      if (w) { w.onopen = null; w.onclose = null; w.onerror = null; w.onmessage = null; try { w.close() } catch (e) { /* ignore */ } }
    },
    clearQuickMatchWsReconnectTimer() {
      if (this.quickMatchWsReconnectTimer != null) { clearTimeout(this.quickMatchWsReconnectTimer); this.quickMatchWsReconnectTimer = null }
    },
    scheduleQuickMatchWsReconnect(sessionAtClose) {
      var self = this
      if (this.quickMatchWsNoReconnect) return
      if (!this.quickMatchPolling) return
      if (this.quickMatchWsSession !== sessionAtClose) return
      this.clearQuickMatchWsReconnectTimer()
      var exp = Math.min(5, this.quickMatchWsReconnectAttempt)
      var delay = Math.min(30000, 1000 * Math.pow(2, exp))
      var jitter = Math.floor(Math.random() * 400)
      this.quickMatchWsReconnectAttempt++
      this.quickMatchWsReconnectTimer = setTimeout(function () {
        self.quickMatchWsReconnectTimer = null
        if (self.quickMatchWsNoReconnect) return
        if (!self.quickMatchPolling) return
        if (self.quickMatchWsSession !== sessionAtClose) return
        var g = self.quickMatchWs
        if (g && (g.readyState === WebSocket.OPEN || g.readyState === WebSocket.CONNECTING)) return
        self.reconnectQuickMatchWsWhileWaiting(sessionAtClose)
      }, delay + jitter)
    },
    reconnectQuickMatchWsWhileWaiting(prevSession) {
      var self = this
      if (this.quickMatchWsNoReconnect || !this.quickMatchPolling) return
      if (this.quickMatchWsSession !== prevSession) return
      if (!this.user || !this.user.nickname || !this.user.token) return
      this.quickMatchWsSession++
      var sessionAtOpen = this.quickMatchWsSession
      if (this.quickMatchWs) {
        var old = this.quickMatchWs; this.quickMatchWs = null
        old.onopen = null; old.onclose = null; old.onerror = null; old.onmessage = null
        try { old.close() } catch (e) { /* ignore */ }
      }
      var path = process.env.NODE_ENV === 'development' ? '/dp-ws/dp-quick-match' : '/ws/dp-quick-match'
      var url = this.quickMatchWsBaseUrl() + path + '?nickname=' + encodeURIComponent(this.user.nickname) + '&token=' + encodeURIComponent(String(this.user.token))
      var ws
      try { ws = new WebSocket(url) } catch (e) { this.scheduleQuickMatchWsReconnect(sessionAtOpen); return }
      this.quickMatchWs = ws
      ws.onopen = function () { if (self.quickMatchWsSession !== sessionAtOpen || self.quickMatchWsNoReconnect) return; self.quickMatchWsReconnectAttempt = 0 }
      ws.onerror = function () {}
      ws.onclose = function () { if (self.quickMatchWsSession !== sessionAtOpen) return; if (self.quickMatchWs === ws) self.quickMatchWs = null; self.scheduleQuickMatchWsReconnect(sessionAtOpen) }
      ws.onmessage = function (ev) { self.handleQuickMatchWsMessage(ev, sessionAtOpen) }
    },
    connectQuickMatchWs() {
      var self = this
      return new Promise(function (resolve, reject) {
        if (!self.user || !self.user.nickname) { reject(new Error('no user')); return }
        var tok = self.user.token ? String(self.user.token) : ''
        if (!tok) { reject(new Error('no token')); return }
        self.disconnectQuickMatchWs()
        self.quickMatchWsNoReconnect = false
        var sessionAtOpen = ++self.quickMatchWsSession
        var path = process.env.NODE_ENV === 'development' ? '/dp-ws/dp-quick-match' : '/ws/dp-quick-match'
        var url = self.quickMatchWsBaseUrl() + path + '?nickname=' + encodeURIComponent(self.user.nickname) + '&token=' + encodeURIComponent(tok)
        var ws
        try { ws = new WebSocket(url) } catch (e) { reject(e); return }
        self.quickMatchWs = ws
        var settled = false
        ws.onopen = function () { if (self.quickMatchWsSession !== sessionAtOpen || self.quickMatchWsNoReconnect) return; if (settled) return; settled = true; self.quickMatchWsReconnectAttempt = 0; resolve() }
        ws.onerror = function () { if (self.quickMatchWsSession !== sessionAtOpen) return; if (!settled) { settled = true; reject(new Error('ws error')) } }
        ws.onclose = function () {
          if (self.quickMatchWsSession !== sessionAtOpen) return
          if (!settled) { settled = true; reject(new Error('ws closed before open')); return }
          if (self.quickMatchWs === ws) self.quickMatchWs = null
          self.scheduleQuickMatchWsReconnect(sessionAtOpen)
        }
        ws.onmessage = function (ev) { self.handleQuickMatchWsMessage(ev, sessionAtOpen) }
      })
    },
    handleQuickMatchWsMessage(ev, sessionAtOpen) {
      if (this.quickMatchWsSession !== sessionAtOpen) return
      try {
        var data = JSON.parse(ev.data)
        if (data._ws !== 'quickMatch') return
        if (data.state === 'MATCHED' && data.roomId) {
          this.quickMatchPolling = false; this.quickMatchLoading = false; this.disconnectQuickMatchWs()
          navigateToGame(this.$router, data.roomId); return
        }
        if (data.state === 'IDLE') {
          this.quickMatchPolling = false; this.quickMatchLoading = false; this.disconnectQuickMatchWs()
          alert(data.message || '已不在匹配队列')
        }
      } catch (e) { console.error('quickMatchWs message', e) }
    },
    async quickMatch() {
      if (!this.user || !this.user.nickname) { alert('请先登录后再快速匹配'); return }
      if (!this.user.token) { alert('登录已失效，请重新登录'); return }
      this.quickMatchLoading = true
      try { await this.connectQuickMatchWs() } catch (e) { console.error('quickMatch ws', e); alert('匹配通道连接失败，请稍后重试'); this.quickMatchLoading = false; return }
      try {
        const params = { nickname: this.user.nickname }
        if (this.user.userId != null && this.user.userId !== '') { params.userId = this.user.userId }
        const res = await this.$http.post('/dpRoom/quickMatch2', null, { params })
        const body = res.data
        if (!dpResultSuccess(body)) { this.disconnectQuickMatchWs(); alert(dpResultMessage(body)); return }
        const data = dpResultData(body) || {}
        if (data.roomId) { this.disconnectQuickMatchWs(); await navigateToGame(this.$router, data.roomId); return }
        if (data.queued && data.state === 'WAITING') { this.quickMatchPolling = true; prefetchGameChunk(); return }
        this.disconnectQuickMatchWs(); alert('匹配响应异常，请稍后重试')
      } catch (e) { console.error('quickMatch', e); this.disconnectQuickMatchWs(); alert('网络错误，请稍后重试') }
      finally { if (!this.quickMatchPolling) { this.quickMatchLoading = false } }
    },
    cancelQuickMatchRemote() { return postQuickMatchCancel2(this.$http, this.user) },
    filtersActiveFromForm() {
      if ((this.filters.roomId || '').length > 0) return true
      if (this.filters.password !== 'any') return true
      const n = (s) => (s === '' || s == null ? NaN : parseInt(String(s), 10))
      if (!isNaN(n(this.filters.minBigBlind))) return true
      if (!isNaN(n(this.filters.maxBigBlind))) return true
      if (!isNaN(n(this.filters.minPlayers))) return true
      if (!isNaN(n(this.filters.maxPlayers))) return true
      return false
    },
    buildFilterQueryParams() {
      const o = { page: 1, pageSize: this.pageSize }
      const rid = (this.filters.roomId || '').trim()
      if (rid) o.roomId = rid
      const n = (s) => (s === '' || s == null ? NaN : parseInt(String(s), 10))
      const minBB = n(this.filters.minBigBlind); if (!isNaN(minBB)) o.minBigBlindChips = minBB
      const maxBB = n(this.filters.maxBigBlind); if (!isNaN(maxBB)) o.maxBigBlindChips = maxBB
      const minP = n(this.filters.minPlayers); if (!isNaN(minP)) o.minPlayerCount = minP
      const maxP = n(this.filters.maxPlayers); if (!isNaN(maxP)) o.maxPlayerCount = maxP
      if (this.filters.password === 'locked') o.passwordProtected = true
      if (this.filters.password === 'open') o.passwordProtected = false
      return o
    },
    applyFilters() { this.useFilterQuery = this.filtersActiveFromForm(); this.getRooms({ showSpinner: true }) },
    resetFilters() {
      this.filters = { roomId: '', minBigBlind: '', maxBigBlind: '', minPlayers: '', maxPlayers: '', password: 'any' }
      this.useFilterQuery = false; this.getRooms({ showSpinner: true })
    },
    async getRooms(opts) {
      try {
        if (this.useFilterQuery && !this.filtersActiveFromForm()) { this.useFilterQuery = false }
        const useQuery = this.useFilterQuery && this.filtersActiveFromForm()
        const forceSpinner = !!(opts && opts.showSpinner)
        if (forceSpinner || !this.roomDtos.length) this.roomsLoading = true
        this.roomsError = ''
        const base = { page: 1, pageSize: this.pageSize }
        const url = useQuery ? '/dpRoom/publicRooms/query' : '/dpRoom/publicRooms'
        const params = useQuery ? this.buildFilterQueryParams() : base
        const res = await this.$http.get(url, { params })
        var list = res && res.data ? res.data.list : []
        this.roomDtos = Array.isArray(list) ? list : []
      } catch (e) { console.error('getRooms', e); this.roomsError = '房间列表加载失败，请确认后端已启动。'; this.roomDtos = [] }
      finally { this.roomsLoading = false }
    },
    refreshRoomList() { this.getRooms({ showSpinner: true }) },
    async joinRoom(roomDto) {
      await this.exitQuickMatchBeforeRoomAction()
      const roomId = typeof roomDto === 'string' ? roomDto : roomDto.roomId
      let roomPassword = ''
      if (roomDto && roomDto.passwordProtected) {
        roomPassword = window.prompt('请输入房间密码') || ''
        if (!roomPassword.trim()) { alert('需要输入密码才能加入'); return }
      }
      const params = { roomId, nickname: this.user.nickname }
      if (roomPassword) { params.roomPassword = roomPassword.trim() }
      if (this.user.userId != null && this.user.userId !== '') { params.userId = this.user.userId }
      const res = await this.$http.post('/dpRoom/joinRoom2', null, { params })
      const body = res.data
      if (!dpResultSuccess(body)) { alert(dpResultMessage(body)); return }
      await navigateToGame(this.$router, roomId)
    }
  }
}
</script>

<style scoped>
/* ============================================
   猫咪牌局 大厅 - 温暖扑克客厅
   ============================================ */

/* ---------- 基础间距 ---------- */
.home-inner {
  padding-bottom: clamp(20px, 4vw, 32px);
}

/* ---------- 品牌横幅 ---------- */
.home-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: clamp(16px, 3.5vw, 24px) clamp(16px, 3.5vw, 24px);
  margin-bottom: clamp(14px, 3vw, 20px);
  background: var(--dp-panel-bg);
  border: 1px solid var(--dp-panel-border);
  border-radius: clamp(12px, 2.4vw, 18px);
  box-shadow: var(--dp-panel-shadow);
  position: relative;
  overflow: hidden;
  flex-wrap: wrap;
}

/* 装饰性背景纹理 */
.home-hero::before {
  content: '';
  position: absolute;
  top: -30%;
  right: -10%;
  width: 200px;
  height: 200px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--dp-accent) 0%, transparent 70%);
  opacity: 0.06;
  pointer-events: none;
}

.home-hero::after {
  content: '';
  position: absolute;
  bottom: -20%;
  left: 5%;
  width: 120px;
  height: 120px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--dp-warning, #faad14) 0%, transparent 70%);
  opacity: 0.08;
  pointer-events: none;
}

.home-hero__brand {
  position: relative;
  z-index: 1;
}

.home-hero__logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

/* CSS 猫爪图标：一个大圆 + 四个小趾 */
.home-hero__logo-paw {
  position: relative;
  width: clamp(28px, 5vw, 36px);
  height: clamp(28px, 5vw, 36px);
  flex-shrink: 0;
  animation: home-paw-float 3s ease-in-out infinite;
}

.home-hero__logo-paw::before {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 55%;
  height: 55%;
  border-radius: 50% 50% 45% 45%;
  background: var(--dp-text-primary);
}

.home-hero__logo-paw::after {
  content: '';
  position: absolute;
  top: 4%;
  left: 4%;
  width: 35%;
  height: 38%;
  border-radius: 50% 50% 40% 40%;
  background: var(--dp-text-primary);
  box-shadow:
    18px -3px 0 -2px var(--dp-text-primary),
    36px 5px 0 -3px var(--dp-text-primary),
    48px 20px 0 -4px var(--dp-text-primary);
}

@keyframes home-paw-float {
  0%, 100% { transform: translateY(0) rotate(0deg); }
  25% { transform: translateY(-3px) rotate(-3deg); }
  75% { transform: translateY(-2px) rotate(3deg); }
}

.home-hero__title {
  margin: 0;
  font-size: clamp(1.3rem, 4.5vw, 1.7rem);
  font-weight: 700;
  color: var(--dp-text-primary);
  letter-spacing: 0.02em;
  line-height: 1.2;
}

.home-hero__subtitle {
  margin: 6px 0 0 0;
  font-size: clamp(12px, 2.4vw, 14px);
  color: var(--dp-text-muted);
  line-height: 1.4;
}

.home-hero__user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: var(--dp-subpanel-bg);
  border-radius: 12px;
  border: 1px solid var(--dp-subpanel-border);
  position: relative;
  z-index: 1;
  flex-shrink: 0;
}

.home-hero__avatar {
  flex-shrink: 0;
}

.home-hero__user-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.home-hero__username {
  font-size: 14px;
  font-weight: 600;
  color: var(--dp-text-primary);
}

.home-hero__user-label {
  font-size: 11px;
  color: var(--dp-success);
  display: flex;
  align-items: center;
  gap: 4px;
}

.home-hero__user-label::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--dp-success);
}

/* ---------- 工具栏 ---------- */
.home-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: clamp(14px, 3vw, 20px);
  flex-wrap: wrap;
}

.home-toolbar__actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.home-tool-btn {
  padding: 6px 12px;
  font-size: 12px;
  font-family: var(--dp-font-ui);
  border: 1px solid var(--dp-input-border);
  border-radius: 7px;
  background: var(--dp-panel-bg);
  color: var(--dp-text-secondary);
  cursor: pointer;
  transition: all 0.18s ease;
  white-space: nowrap;
}

.home-tool-btn:hover {
  border-color: var(--dp-accent);
  color: var(--dp-accent);
  background: var(--dp-subpanel-bg);
}

.home-tool-btn--danger {
  color: var(--dp-danger);
}

.home-tool-btn--danger:hover {
  border-color: var(--dp-danger);
  background: rgba(255, 77, 79, 0.06);
}

/* ---------- 区块标题 ---------- */
.home-section-title {
  margin: 0 0 clamp(10px, 2.4vw, 14px);
  font-size: clamp(0.95rem, 2.6vw, 1.05rem);
  font-weight: 600;
  color: var(--dp-text-primary);
  display: flex;
  align-items: center;
  gap: 8px;
}


/* ---------- 快捷入口卡片网格 ---------- */
.home-quick {
  background: var(--dp-panel-bg);
  border: 1px solid var(--dp-panel-border);
  border-radius: clamp(12px, 2.4vw, 16px);
  box-shadow: var(--dp-panel-shadow);
  padding: clamp(14px, 3vw, 20px) clamp(14px, 3vw, 22px);
  margin-bottom: clamp(14px, 3vw, 20px);
}

.home-quick__grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: clamp(8px, 2vw, 12px);
}

@media (max-width: 640px) {
  .home-quick__grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

/* 快捷入口卡片 */
.home-quick-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: clamp(12px, 2.5vw, 16px) clamp(8px, 2vw, 12px);
  border: 1px solid var(--dp-subpanel-border);
  border-radius: clamp(10px, 2vw, 14px);
  background: var(--dp-subpanel-bg);
  cursor: pointer;
  transition: all 0.22s cubic-bezier(0.25, 0.46, 0.45, 0.94);
  font-family: var(--dp-font-ui);
  text-align: center;
  position: relative;
  overflow: hidden;
}

.home-quick-card::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: inherit;
  background: var(--dp-accent);
  opacity: 0;
  transition: opacity 0.22s ease;
  pointer-events: none;
}

.home-quick-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0,0,0,0.08);
  border-color: var(--dp-accent);
}

.home-quick-card:hover::after {
  opacity: 0.03;
}

.home-quick-card:active {
  transform: translateY(0);
  transition: all 0.08s ease;
}

.home-quick-card:disabled {
  opacity: 0.45;
  cursor: not-allowed;
  transform: none;
}

.home-quick-card:disabled:hover {
  border-color: var(--dp-subpanel-border);
  box-shadow: none;
}

/* 主推：快速匹配 */
.home-quick-card--primary {
  background: linear-gradient(135deg, var(--dp-success) 0%, color-mix(in srgb, var(--dp-success) 80%, #000) 100%);
  border-color: transparent;
  color: #fff;
}

.home-quick-card--primary .home-quick-card__label,
.home-quick-card--primary .home-quick-card__desc {
  color: #fff;
}

.home-quick-card--primary .home-quick-card__icon-wrap {
  background: rgba(255,255,255,0.2);
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}

/* 次推：创建房间 */
.home-quick-card--accent {
  background: var(--dp-accent);
  border-color: transparent;
  color: #fff;
}

.home-quick-card--accent .home-quick-card__label,
.home-quick-card--accent .home-quick-card__desc {
  color: #fff;
}

.home-quick-card--accent .home-quick-card__icon-wrap {
  background: rgba(255,255,255,0.2);
  box-shadow: 0 2px 8px rgba(0,0,0,0.12);
}

.home-quick-card__icon-wrap {
  width: clamp(36px, 7vw, 44px);
  height: clamp(36px, 7vw, 44px);
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--dp-panel-bg);
  flex-shrink: 0;
  transition: transform 0.22s ease;
}

.home-quick-card:hover .home-quick-card__icon-wrap {
  transform: scale(1.08);
}

.home-quick-card__icon-wrap .el-icon-s-flag,
.home-quick-card__icon-wrap .el-icon-s-home,
.home-quick-card__icon-wrap .el-icon-document,
.home-quick-card__icon-wrap .el-icon-s-data,
.home-quick-card__icon-wrap .el-icon-upload,
.home-quick-card__icon-wrap .el-icon-download,
.home-quick-card__icon-wrap .el-icon-message,
.home-quick-card__icon-wrap .el-icon-user {
  font-size: clamp(18px, 3.5vw, 22px);
  line-height: 1;
  color: var(--dp-text-secondary);
}

/* 主推/次推卡片图标始终白色 */
.home-quick-card--primary .home-quick-card__icon-wrap i,
.home-quick-card--accent .home-quick-card__icon-wrap i {
  color: #fff;
}

.home-quick-card__label {
  font-size: clamp(12px, 2.4vw, 14px);
  font-weight: 600;
  color: var(--dp-text-primary);
  line-height: 1.3;
}

.home-quick-card__desc {
  font-size: clamp(10px, 2vw, 11px);
  color: var(--dp-text-muted);
  line-height: 1.3;
}

/* Badge 包裹 */
.home-quick-card__badge {
  display: block;
}

.home-quick-card__badge .home-quick-card {
  width: 100%;
}

.home-quick-card__badge >>> .el-badge__content {
  border: none;
}

/* ---------- 房间列表区 ---------- */
.home-rooms {
  background: var(--dp-panel-bg);
  border: 1px solid var(--dp-panel-border);
  border-radius: clamp(12px, 2.4vw, 16px);
  box-shadow: var(--dp-panel-shadow);
  padding: clamp(14px, 3vw, 20px) clamp(14px, 3vw, 22px);
}

.home-rooms__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}

.home-rooms__head-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.home-rooms__mode-badge {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 5px;
  background: var(--dp-subpanel-bg);
  color: var(--dp-accent);
  border: 1px solid var(--dp-subpanel-border);
  white-space: nowrap;
}

.home-rooms__mode-badge--dim {
  color: var(--dp-text-muted);
}

.home-rooms__refresh-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  font-size: 12px;
  font-family: var(--dp-font-ui);
  border: 1px solid var(--dp-input-border);
  border-radius: 7px;
  background: var(--dp-panel-bg);
  color: var(--dp-text-secondary);
  cursor: pointer;
  transition: all 0.18s ease;
  white-space: nowrap;
}

.home-rooms__refresh-btn:hover:not(:disabled) {
  border-color: var(--dp-accent);
  color: var(--dp-accent);
}

.home-rooms__refresh-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.home-rooms__refresh-ico,
.home-rooms__refresh-btn .el-icon-refresh {
  font-size: 14px;
  line-height: 1;
  color: inherit;
}

.home-rooms__refresh-ico--spin {
  animation: home-rooms-spin 0.8s linear infinite;
}

@keyframes home-rooms-spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ---------- 可折叠筛选栏 ---------- */
.home-filters {
  margin-bottom: 14px;
  border: 1px solid var(--dp-subpanel-border);
  border-radius: 10px;
  background: var(--dp-subpanel-bg);
  overflow: hidden;
  transition: all 0.25s ease;
}

.home-filters__toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 10px 14px;
  font-size: 13px;
  font-family: var(--dp-font-ui);
  border: none;
  background: none;
  color: var(--dp-text-secondary);
  cursor: pointer;
  transition: color 0.18s ease;
}

.home-filters__toggle:hover {
  color: var(--dp-text-primary);
}

.home-filters__toggle-icon {
  font-size: 12px;
  color: var(--dp-text-muted);
  transition: transform 0.25s ease;
}

.home-filters__active-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--dp-accent);
  flex-shrink: 0;
  animation: home-dot-pulse 2s ease-in-out infinite;
}

@keyframes home-dot-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.home-filters__body {
  padding: 0 14px 14px;
  animation: home-filter-slide 0.25s ease;
}

@keyframes home-filter-slide {
  from { opacity: 0; max-height: 0; }
  to { opacity: 1; max-height: 300px; }
}

.home-filters__row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px 12px;
  align-items: flex-end;
}

.home-filters__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.home-filters__item--num {
  max-width: 100px;
}

.home-filters__item--select {
  min-width: 100px;
}

.home-filters__label {
  font-size: 11px;
  color: var(--dp-text-muted);
  white-space: nowrap;
}

.home-filters__input,
.home-filters__select {
  padding: 7px 10px;
  font-size: 13px;
  font-family: var(--dp-font-ui);
  border: 1px solid var(--dp-input-border);
  border-radius: 7px;
  background: var(--dp-input-bg);
  color: var(--dp-text-primary);
  min-width: 0;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.home-filters__input:focus,
.home-filters__select:focus {
  outline: none;
  border-color: var(--dp-accent);
  box-shadow: 0 0 0 3px rgba(24, 144, 255, 0.1);
}

.home-filters__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
}

.home-filters__btn {
  padding: 7px 18px;
  font-size: 13px;
  font-family: var(--dp-font-ui);
  border: none;
  border-radius: 7px;
  cursor: pointer;
  transition: all 0.18s ease;
}

.home-filters__btn--search {
  background: var(--dp-btn-primary-bg);
  color: var(--dp-btn-primary-fg);
}

.home-filters__btn--search:hover {
  filter: brightness(1.08);
}

.home-filters__btn--reset {
  background: transparent;
  color: var(--dp-text-muted);
  border: 1px solid var(--dp-input-border);
}

.home-filters__btn--reset:hover {
  border-color: var(--dp-text-muted);
  color: var(--dp-text-primary);
}

/* ---------- 加载/空态/错误 ---------- */
.home-rooms__hint {
  margin: 16px 0;
  padding: 12px;
  text-align: center;
  color: var(--dp-text-muted);
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.home-rooms__hint--error {
  color: var(--dp-danger);
}

.home-rooms__spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--dp-subpanel-border);
  border-top-color: var(--dp-accent);
  border-radius: 50%;
  animation: home-rooms-spin 0.7s linear infinite;
  display: inline-block;
}

.home-rooms__empty {
  margin: 24px 0;
  padding: 32px 16px;
  text-align: center;
  color: var(--dp-text-muted);
  font-size: 14px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.home-rooms__empty-icon {
  font-size: 40px;
  opacity: 0.35;
  color: var(--dp-text-muted);
}

/* ---------- 房间卡片网格 ---------- */
.home-rooms__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 240px), 1fr));
  gap: clamp(10px, 2vw, 14px);
  margin-top: 10px;
}

/* ---------- 单张房间卡 ---------- */
.room-card {
  border-radius: clamp(10px, 2vw, 14px);
  overflow: hidden;
  cursor: pointer;
  transition: all 0.25s cubic-bezier(0.25, 0.46, 0.45, 0.94);
  position: relative;
}

.room-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.1);
}

.room-card:hover .room-card__join {
  opacity: 1;
  transform: translateY(0);
}

.room-card__felt {
  padding: clamp(12px, 2.5vw, 16px);
  /* 模拟台呢质感 */
  background:
    linear-gradient(160deg, rgba(255,255,255,0.05) 0%, transparent 70%),
    var(--dp-subpanel-bg);
  border: 1px solid var(--dp-subpanel-border);
  border-radius: inherit;
  position: relative;
  overflow: hidden;
}

/* 台呢纹理装饰 */
.room-card__felt::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background:
    repeating-linear-gradient(
      45deg,
      transparent 0 3px,
      rgba(128,128,128,0.02) 3px 4px
    );
  pointer-events: none;
  border-radius: inherit;
}

/* 密码房边框 */
.room-card--locked .room-card__felt {
  border-color: var(--dp-warning, #faad14);
}

.room-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  position: relative;
  z-index: 1;
}

.room-card__room-id {
  font-size: clamp(13px, 2.6vw, 15px);
  font-weight: 700;
  color: var(--dp-text-primary);
  font-family: 'Courier New', monospace;
  letter-spacing: 0.03em;
}

.room-card__lock {
  font-size: 14px;
  line-height: 1;
  color: var(--dp-warning, #faad14);
}

.room-card__body {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  position: relative;
  z-index: 1;
}

.room-card__players {
  display: flex;
  align-items: baseline;
  gap: 2px;
}

.room-card__player-count {
  font-size: clamp(22px, 4.5vw, 26px);
  font-weight: 700;
  color: var(--dp-text-primary);
  line-height: 1;
}

.room-card__player-sep {
  font-size: 14px;
  color: var(--dp-text-muted);
  margin: 0 1px;
}

.room-card__player-max {
  font-size: clamp(15px, 3vw, 18px);
  color: var(--dp-text-secondary);
  line-height: 1;
}

.room-card__player-label {
  font-size: 12px;
  color: var(--dp-text-muted);
  margin-left: 2px;
}

.room-card__blinds {
  display: flex;
  align-items: center;
  gap: 4px;
}

.room-card__blind-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 28px;
  height: 28px;
  padding: 0 6px;
  border-radius: 14px;
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
}

.room-card__blind-chip--small {
  background: var(--dp-subpanel-bg);
  color: var(--dp-text-secondary);
  border: 1.5px solid var(--dp-text-muted);
}

.room-card__blind-chip--big {
  background: var(--dp-accent);
  color: #fff;
  border: 1.5px solid transparent;
  box-shadow: 0 2px 6px rgba(0,0,0,0.12);
}

.room-card__blind-sep {
  font-size: 12px;
  color: var(--dp-text-muted);
}

.room-card__bb-label {
  font-size: 11px;
  color: var(--dp-text-muted);
  margin-left: 2px;
  font-weight: 500;
}

.room-card__footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  position: relative;
  z-index: 1;
}

/* 座位指示器 */
.room-card__seats {
  display: flex;
  gap: 3px;
}

.room-card__seat {
  width: clamp(6px, 1.5vw, 8px);
  height: clamp(6px, 1.5vw, 8px);
  border-radius: 50%;
  background: var(--dp-subpanel-border);
  transition: background 0.3s ease;
}

.room-card__seat.is-filled {
  background: var(--dp-success);
  box-shadow: 0 0 4px rgba(82, 196, 26, 0.4);
}

/* 加入按钮 */
.room-card__join {
  padding: 6px 16px;
  font-size: 13px;
  font-family: var(--dp-font-ui);
  font-weight: 600;
  border: none;
  border-radius: 7px;
  background: var(--dp-btn-primary-bg);
  color: var(--dp-btn-primary-fg);
  cursor: pointer;
  transition: all 0.22s ease;
  opacity: 0.75;
  transform: translateY(1px);
}

.room-card:hover .room-card__join {
  opacity: 1;
  transform: translateY(0);
}

.room-card__join:hover {
  filter: brightness(1.1);
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}

.room-card__join:active {
  transform: scale(0.96);
  transition: all 0.06s ease;
}
</style>

<style>
/* append-to-body 弹层样式保持不变 */
.home-mailbox__sec {
  margin-bottom: clamp(14px, 3.6vw, 20px);
}
.home-mailbox__sec:last-child {
  margin-bottom: 0;
}
.home-mailbox__subtitle {
  margin: 0 0 10px;
  font-size: clamp(14px, 3.6vw, 16px);
  font-weight: 600;
  color: var(--dp-text-primary);
}
.home-mailbox__empty {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--dp-text-muted);
}
.home-add-friend__multi-hint {
  margin: 10px 0 0;
  font-size: 13px;
  color: var(--dp-text-muted);
  line-height: 1.45;
}
.home-add-friend__item {
  margin-bottom: 8px;
}
.home-add-friend__item:last-child {
  margin-bottom: 0;
}
</style>
