package org.jahia.modules.contentintegrity.services.reporting;

import org.apache.commons.lang.StringEscapeUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

import static org.hamcrest.CoreMatchers.is;

public class CsvReportTest {

    // ErrorCollector reports every failed assertion of a test method, instead of stopping at the first one.
    @Rule
    public final ErrorCollector collector = new ErrorCollector();

    @Test
    public void wrapsAndEscapesAnOrdinaryValue() {
        assertCell("\"plain text\"", "plain text");
        assertCell("\"he said \"\"hi\"\"\"", "he said \"hi\"");
        assertCell("\"trimmed\"", "  trimmed  ");
    }

    @Test
    public void writesAnEmptyCellForABlankValue() {
        assertCell("\"\"", null);
        assertCell("\"\"", "");
        assertCell("\"\"", "   ");
    }

    @Test
    public void writesAnEmptyCellWhenOnlyControlCharactersRemain() {
        // U+0001 is not blank for StringUtils, and trim() removes it, so the value is empty here.
        assertCell("\"\"", "\u0001");
    }

    @Test
    public void keepsAValueThatStartsWithAFormulaTriggerAsLiteralText() {
        assertCell("\"'=1+1\"", "=1+1");
        assertCell("\"'+1\"", "+1");
        assertCell("\"'-1\"", "-1");
        assertCell("\"'@SUM(A1)\"", "@SUM(A1)");
    }

    @Test
    public void testsTheFirstCharacterOfTheTrimmedValue() {
        // trim() removes every character below U+0020, so a leading space, TAB or CR never reaches the
        // trigger test. It reveals the trigger that follows it, and it neutralises TAB and CR themselves.
        assertCell("\"'=1+1\"", "  =1+1");
        assertCell("\"'=1+1\"", "\t=1+1");
        assertCell("\"'=1+1\"", "\r=1+1");
        assertCell("\"SUM(A1)\"", "\tSUM(A1)");
        assertCell("\"SUM(A1)\"", "\rSUM(A1)");
    }

    @Test
    public void writesAValueThatStartsWithADoubleQuoteWithoutTheLiteralTextPrefix() {
        // The cell starts with a double quote, which no spreadsheet application reads as a formula.
        assertCell("\"\"\"=1+1\"", "\"=1+1");
    }

    @Test
    public void leavesAValueThatCarriesATriggerAwayFromTheStartUnchanged() {
        assertCell("\"/sites/mysite/home/=1+1\"", "/sites/mysite/home/=1+1");
        assertCell("\"{property-name=j:linknode}\"", "{property-name=j:linknode}");
        assertCell("\"a-b-c\"", "a-b-c");
        assertCell("\"user@example.com\"", "user@example.com");
    }

    private void assertCell(String expected, String value) {
        collector.checkThat(StringEscapeUtils.escapeJava(value), CsvReport.escapeCsv(value), is(expected));
    }
}
