package com.clarys.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ValidationUtilsTest {

    @Test
    public void strongPasswordRequiresEveryCharacterGroup() {
        assertTrue(ValidationUtils.isStrongPassword("Clarys2026*"));
        assertFalse(ValidationUtils.isStrongPassword("corta1*"));
        assertFalse(ValidationUtils.isStrongPassword("clarys2026*"));
        assertFalse(ValidationUtils.isStrongPassword("CLARYS2026*"));
        assertFalse(ValidationUtils.isStrongPassword("ClarysClave*"));
        assertFalse(ValidationUtils.isStrongPassword("Clarys2026"));
    }
}
