package com.clarys.app.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TextSanitizerTest {

    @Test
    public void convertsNullValuesToEmptyText() {
        assertEquals("", TextSanitizer.emptyIfNull(null));
        assertEquals("", TextSanitizer.emptyIfNull("null"));
        assertEquals("", TextSanitizer.emptyIfNull(" NULL "));
    }

    @Test
    public void keepsRealTextAndAppliesDefaults() {
        assertEquals("Notas del taller", TextSanitizer.emptyIfNull("Notas del taller"));
        assertEquals("Cliente", TextSanitizer.orDefault("null", "Cliente"));
    }
}
