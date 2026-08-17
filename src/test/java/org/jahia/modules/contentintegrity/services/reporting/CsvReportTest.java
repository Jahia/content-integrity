package org.jahia.modules.contentintegrity.services.reporting;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CsvReportTest {

    @Test
    public void wrapsAndEscapesAnOrdinaryValue() {
        assertEquals("\"plain text\"", CsvReport.escapeCsv("plain text"));
        assertEquals("\"he said \"\"hi\"\"\"", CsvReport.escapeCsv("he said \"hi\""));
        assertEquals("\"trimmed\"", CsvReport.escapeCsv("  trimmed  "));
    }

    @Test
    public void writesAnEmptyCellForABlankValue() {
        assertEquals("\"\"", CsvReport.escapeCsv(null));
        assertEquals("\"\"", CsvReport.escapeCsv(""));
        assertEquals("\"\"", CsvReport.escapeCsv("   "));
    }

    @Test
    public void writesAnEmptyCellWhenOnlyControlCharactersRemain() {
        // U+0001 is not blank for StringUtils, and trim() removes it, so the value is empty here.
        assertEquals("\"\"", CsvReport.escapeCsv("\u0001"));
    }

    @Test
    public void keepsAValueThatStartsWithAFormulaTriggerAsLiteralText() {
        assertEquals("\"'=1+1\"", CsvReport.escapeCsv("=1+1"));
        assertEquals("\"'+1\"", CsvReport.escapeCsv("+1"));
        assertEquals("\"'-1\"", CsvReport.escapeCsv("-1"));
        assertEquals("\"'@SUM(A1)\"", CsvReport.escapeCsv("@SUM(A1)"));
        // The leading whitespace is trimmed first, so the trigger below is the first character.
        assertEquals("\"'=1+1\"", CsvReport.escapeCsv("  =1+1"));
    }

    @Test
    public void leavesAValueThatCarriesATriggerAwayFromTheStartUnchanged() {
        assertEquals("\"/sites/mysite/home/=1+1\"", CsvReport.escapeCsv("/sites/mysite/home/=1+1"));
        assertEquals("\"{property-name=j:linknode}\"", CsvReport.escapeCsv("{property-name=j:linknode}"));
        assertEquals("\"a-b-c\"", CsvReport.escapeCsv("a-b-c"));
        assertEquals("\"user@example.com\"", CsvReport.escapeCsv("user@example.com"));
    }
}
