package com.example.mgdemoplus.achievement.impl;

import com.example.mgdemoplus.achievement.DpAchievementService;
import com.example.mgdemoplus.achievement.entity.DpAchievement;
import com.example.mgdemoplus.achievement.mapper.DpAchievementMapper;
import com.example.mgdemoplus.achievement.mapper.DpUserAchievementMapper;
import com.example.mgdemoplus.achievement.notify.AchievementNotifyPublisher;
import com.example.mgdemoplus.common.mapper.DpUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DpAchievementServiceImplTest {

    private static final int USER_ID = 42;
    private static final String CODE = DpAchievementService.CODE_TWENTY_SEVEN_TERMINATOR;

    private DpAchievementMapper achievementMapper;
    private DpUserAchievementMapper userAchievementMapper;
    private DpUserMapper userMapper;
    private AchievementNotifyPublisher notifyPublisher;
    private DpAchievementServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        achievementMapper = mock(DpAchievementMapper.class);
        userAchievementMapper = mock(DpUserAchievementMapper.class);
        userMapper = mock(DpUserMapper.class);
        notifyPublisher = mock(AchievementNotifyPublisher.class);
        service = new DpAchievementServiceImpl();
        inject("dpAchievementMapper", achievementMapper);
        inject("dpUserAchievementMapper", userAchievementMapper);
        inject("dpUserMapper", userMapper);
        inject("achievementNotifyPublisher", notifyPublisher);
    }

    @Test
    @DisplayName("首次解锁时写入并发 SSE")
    void unlockIfAbsent_pushesSseOnFirstUnlock() {
        DpAchievement def = achievementDef();
        when(achievementMapper.selectByCode(CODE)).thenReturn(def);
        when(userAchievementMapper.selectUnlockedFlag(USER_ID, def.getId())).thenReturn(null);

        service.unlockIfAbsent(USER_ID, CODE);

        verify(userAchievementMapper).tryUnlock(USER_ID, def.getId());
        verify(notifyPublisher).notifyUnlocked(USER_ID, def);
    }

    @Test
    @DisplayName("已解锁时不重复推送")
    void unlockIfAbsent_skipsWhenAlreadyUnlocked() {
        DpAchievement def = achievementDef();
        when(achievementMapper.selectByCode(CODE)).thenReturn(def);
        when(userAchievementMapper.selectUnlockedFlag(USER_ID, def.getId())).thenReturn(1);

        service.unlockIfAbsent(USER_ID, CODE);

        verify(userAchievementMapper, never()).tryUnlock(USER_ID, def.getId());
        verify(notifyPublisher, never()).notifyUnlocked(eq(USER_ID), eq(def));
    }

    private DpAchievement achievementDef() {
        DpAchievement def = new DpAchievement();
        def.setId(7);
        def.setCode(CODE);
        def.setTitle("27终结者");
        def.setDescription("用杂色27赢下底池");
        return def;
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = DpAchievementServiceImpl.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }
}
