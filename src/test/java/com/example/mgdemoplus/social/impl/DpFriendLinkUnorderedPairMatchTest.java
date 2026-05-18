package com.example.mgdemoplus.social.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回归：好友链路与「邀请进房」必须以无序用户对识别 dp_friend_link，禁止仅接受「低列=全局 min、高列=全局 max」一种存法。
 */
class DpFriendLinkUnorderedPairMatchTest {

    /** 与 {@link DpFriendSocialService} 中 countFriendLinksBetween 的 SQL 条件一致（两列可同时表示同一对）。 */
    static boolean linkRowMatchesPair(int userA, int userB, int colLow, int colHigh) {
        if (userA <= 0 || userB <= 0 || userA == userB) {
            return false;
        }
        int lo = Math.min(userA, userB);
        int hi = Math.max(userA, userB);
        return (colLow == lo && colHigh == hi) || (colLow == hi && colHigh == lo);
    }

    @Test
    void acceptsCanonicalAndInvertedColumnOrder() {
        assertTrue(linkRowMatchesPair(3, 7, 3, 7));
        assertTrue(linkRowMatchesPair(3, 7, 7, 3));
        assertTrue(linkRowMatchesPair(7, 3, 3, 7));
    }

    @Test
    void rejectsWrongPairOrInvalidUsers() {
        assertFalse(linkRowMatchesPair(3, 7, 3, 8));
        assertFalse(linkRowMatchesPair(3, 7, 4, 7));
        assertFalse(linkRowMatchesPair(0, 7, 0, 7));
        assertFalse(linkRowMatchesPair(5, 5, 5, 5));
    }

    @Test
    void pendingOnlyHasNoLinkRow() {
        assertFalse(linkRowMatchesPair(1, 2, 0, 0));
    }
}
