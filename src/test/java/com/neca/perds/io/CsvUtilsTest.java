package com.neca.perds.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CsvUtilsTest {
    @Test
    void splitLine_splitsUnquotedFields() {
        assertEquals(java.util.List.of("a", "b", "c"), CsvUtils.splitLine("a,b,c"));
    }

    @Test
    void splitLine_keepsEmptyFields() {
        assertEquals(java.util.List.of("a", "", "c", ""), CsvUtils.splitLine("a,,c,"));
    }

    @Test
    void splitLine_supportsQuotedFieldsWithCommas() {
        assertEquals(java.util.List.of("a,b", "c"), CsvUtils.splitLine("\"a,b\",c"));
    }

    @Test
    void splitLine_supportsEscapedQuotesInsideQuotedField() {
        assertEquals(java.util.List.of("a\"b", "c"), CsvUtils.splitLine("\"a\"\"b\",c"));
    }

    @Test
    void splitLine_throwsOnUnclosedQuote() {
        assertThrows(IllegalArgumentException.class, () -> CsvUtils.splitLine("\"a,b"));
    }
}

