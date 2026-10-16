package com.clarys.app.util;

import org.junit.Test;
import static org.junit.Assert.*;
public class CsvExporterTest {
    @Test public void formulasCannotExecuteWhenOpenedInSpreadsheet() {
        assertEquals("\"'=SUM(A1:A2)\"",CsvExporter.cell("=SUM(A1:A2)"));
        assertEquals("\"'\t+123\"",CsvExporter.cell("\t+123"));
    }
    @Test public void embeddedQuotesAreEscaped() {
        assertEquals("\"Prenda \"\"M\"\"\"",CsvExporter.cell("Prenda \"M\""));
    }
}
