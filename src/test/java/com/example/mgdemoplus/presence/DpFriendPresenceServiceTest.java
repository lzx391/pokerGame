package com.example.mgdemoplus.presence;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DpFriendPresenceServiceTest {

    @Test
    void getEffectiveMany_reflectsMapAndDefaultsIdle() {
        DpFriendPresenceService svc = new DpFriendPresenceService();
        svc.markInGame(7, "test");
        Map<Integer, DpFriendPresenceState> m = svc.getEffectiveMany(List.of(7, 8));
        assertThat(m).containsEntry(7, DpFriendPresenceState.IN_GAME).containsEntry(8, DpFriendPresenceState.IDLE);
    }
}
