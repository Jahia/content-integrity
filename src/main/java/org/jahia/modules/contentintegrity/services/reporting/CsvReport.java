package org.jahia.modules.contentintegrity.services.reporting;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.settings.SettingsBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class CsvReport extends Report {

    private static final Logger logger = LoggerFactory.getLogger(CsvReport.class);

    private static final String REPORT_COLUMN_NAMES_CONF = "modules.contentIntegrity.csv.header";
    private static final String CSV_SEPARATOR = ";";
    private static final String CSV_VALUE_WRAPPER = "\"";
    private static final String CSV_EMPTY_VALUE = CSV_VALUE_WRAPPER + CSV_VALUE_WRAPPER;
    private static final String ESCAPED_CSV_VALUE_WRAPPER = CSV_VALUE_WRAPPER + CSV_VALUE_WRAPPER;

    @Override
    public void write(OutputStream out, ContentIntegrityResults results, boolean excludeFixedErrors) throws IOException {
        IOUtils.writeLines(toCSVFileContent(results, excludeFixedErrors), null, out, StandardCharsets.UTF_8);
    }

    @Override
    public String getFileExtension() {
        return "csv";
    }

    @Override
    public String getFileContentType() {
        return "text/csv";
    }

    private List<String> toCSVFileContent(ContentIntegrityResults results, boolean excludeFixedErrors) {
        final List<String> lines = getReportContent(results, excludeFixedErrors);
        String header = SettingsBean.getInstance().getString(REPORT_COLUMN_NAMES_CONF, null);
        if (StringUtils.isBlank(header)) {
            header = toCSVLine(getColumns());
        }
        lines.add(0, header);

        return lines;
    }

    private List<String> getReportContent(ContentIntegrityResults results, boolean excludeFixedErrors) {
        return results.getErrors().stream()
                .filter(error -> !excludeFixedErrors || !error.isFixed())
                .map(CsvReport::toCSVLine)
                .collect(Collectors.toList());
    }

    private static String toCSVLine(ContentIntegrityError error) {
        return toCSVLine(toTextElementsList(error));
    }

    private static String toCSVLine(List<String> items) {
        return items.stream()
                .map(CsvReport::escapeCsv)
                .collect(Collectors.joining(CSV_SEPARATOR));
    }

    private static String escapeCsv(String text) {
        if (StringUtils.isBlank(text)) return CSV_EMPTY_VALUE;
        return String.format("%s%s%s",
                CSV_VALUE_WRAPPER,
                StringUtils.replace(text.trim(), CSV_VALUE_WRAPPER, ESCAPED_CSV_VALUE_WRAPPER),
                CSV_VALUE_WRAPPER);
    }
}
