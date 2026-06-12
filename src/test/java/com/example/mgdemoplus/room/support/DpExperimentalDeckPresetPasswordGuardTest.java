package com.example.mgdemoplus.room.support;

import com.example.mgdemoplus.utils.ResultUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DpExperimentalDeckPresetPasswordGuardTest {

    @Test
    void gate_rejectsWhenNotConfigured() {
        DpExperimentalDeckPresetPasswordGuard guard = new DpExperimentalDeckPresetPasswordGuard("");
        assertFalse(guard.isEnabled());
        ResultUtil result = guard.gate("any");
        assertNotNull(result);
        assertFalse(result.getSuccess());
    }

    @Test
    void gate_acceptsMatchingPassword() {
        DpExperimentalDeckPresetPasswordGuard guard = new DpExperimentalDeckPresetPasswordGuard("secret-pass");
        assertTrue(guard.isEnabled());
        assertNull(guard.gate("secret-pass"));
    }

    @Test
    void gate_rejectsWrongOrMissingPassword() {
        DpExperimentalDeckPresetPasswordGuard guard = new DpExperimentalDeckPresetPasswordGuard("secret-pass");
        assertNotNull(guard.gate("wrong"));
        assertNotNull(guard.gate(null));
        assertNotNull(guard.gate("  "));
    }

    @Test
    void verify_trimsSubmittedPassword() {
        DpExperimentalDeckPresetPasswordGuard guard = new DpExperimentalDeckPresetPasswordGuard("secret-pass");
        assertTrue(guard.verify("  secret-pass  "));
        assertFalse(guard.verify("secret-passx"));
    }
}
