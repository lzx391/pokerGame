package com.example.mgdemoplus.achievement.impl;

import com.example.mgdemoplus.achievement.entity.DpAchievement;
import com.example.mgdemoplus.achievement.mapper.DpAchievementMapper;
import com.example.mgdemoplus.achievement.mapper.DpUserAchievementMapper;
import com.example.mgdemoplus.achievement.notify.AchievementNotifyPublisher;
import com.example.mgdemoplus.achievement.vo.DpAchievementWallItemVO;
import com.example.mgdemoplus.common.entity.DpUser;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DpAchievementServiceImplTest {

    private static final int USER_ID = 42;
    private static final int ACHIEVEMENT_ID = 7;
    private static final Long HAND_HISTORY_ID = 9001L;

    @Mock
    private DpAchievementMapper dpAchievementMapper;
    @Mock
    private DpUserAchievementMapper dpUserAchievementMapper;
    @Mock
    private DpUserMapper dpUserMapper;
    @Mock
    private AchievementNotifyPublisher achievementNotifyPublisher;

    @InjectMocks
    private DpAchievementServiceImpl service;

    private DpAchievement achievementDef;

    @BeforeEach
    void setUp() {
        achievementDef = new DpAchievement();
        achievementDef.setId(ACHIEVEMENT_ID);
        achievementDef.setCode("table_clear");
        achievementDef.setTitle("牌桌消消乐");
        achievementDef.setDescription("desc");
    }

    @Test
    @DisplayName("首次解锁写入 hand_history_id")
    void unlockIfAbsent_writesHandHistoryIdOnFirstUnlock() {
        when(dpAchievementMapper.selectByCode("table_clear")).thenReturn(achievementDef);
        when(dpUserAchievementMapper.selectUnlockedFlag(USER_ID, ACHIEVEMENT_ID)).thenReturn(null);

        service.unlockIfAbsent(USER_ID, "table_clear", HAND_HISTORY_ID);

        ArgumentCaptor<LocalDateTime> unlockedAtCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(dpUserAchievementMapper).tryUnlock(
                eq(USER_ID), eq(ACHIEVEMENT_ID), eq(HAND_HISTORY_ID), unlockedAtCaptor.capture());
        assertThat(unlockedAtCaptor.getValue()).isNotNull();
        verify(achievementNotifyPublisher).notifyUnlocked(USER_ID, achievementDef);
    }

    @Test
    @DisplayName("已解锁时幂等跳过，不覆盖 hand_history_id")
    void unlockIfAbsent_skipsWhenAlreadyUnlocked() {
        when(dpAchievementMapper.selectByCode("table_clear")).thenReturn(achievementDef);
        when(dpUserAchievementMapper.selectUnlockedFlag(USER_ID, ACHIEVEMENT_ID)).thenReturn(1);

        service.unlockIfAbsent(USER_ID, "table_clear", 9999L);

        verify(dpUserAchievementMapper, never()).tryUnlock(anyInt(), anyInt(), any(), any());
        verify(achievementNotifyPublisher, never()).notifyUnlocked(anyInt(), any());
    }

    @Test
    @DisplayName("成就墙已解锁且有 id 时返回 handHistoryId")
    void buildWallForUser_returnsHandHistoryIdWhenUnlocked() {
        DpUser user = new DpUser();
        user.setId(USER_ID);
        when(dpUserMapper.selectById(USER_ID)).thenReturn(user);

        DpAchievementWallItemVO unlocked = new DpAchievementWallItemVO();
        unlocked.setCode("table_clear");
        unlocked.setUnlocked(true);
        unlocked.setHandHistoryId(HAND_HISTORY_ID);

        DpAchievementWallItemVO locked = new DpAchievementWallItemVO();
        locked.setCode("quad_nightmare");
        locked.setUnlocked(false);
        locked.setHandHistoryId(null);

        when(dpAchievementMapper.selectWallForUser(USER_ID)).thenReturn(List.of(unlocked, locked));

        List<DpAchievementWallItemVO> wall = service.buildWallForUser(USER_ID);

        assertThat(wall).hasSize(2);
        assertThat(wall.get(0).getHandHistoryId()).isEqualTo(HAND_HISTORY_ID);
        assertThat(wall.get(1).getHandHistoryId()).isNull();
    }
}
