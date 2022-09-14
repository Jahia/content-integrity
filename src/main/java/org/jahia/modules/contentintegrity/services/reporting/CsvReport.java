package org.jahia.modules.contentintegrity.services.reporting;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.modules.contentintegrity.services.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

public class CsvReport extends Report {

    private static final Logger logger = LoggerFactory.getLogger(CsvReport.class);

    private static final List<String> DEFAULT_CSV_HEADER_ITEMS = Arrays.asList("Check ID", "Fixed", "Error type", "Workspace", "Node identifier", "Node path", "Site", "Node primary type", "Node mixins", "Locale", "Error message", "Extra information", "Specific extra information");

    @Override
    public void write(OutputStream out, ContentIntegrityResults results, boolean withColumnHeaders, boolean excludeFixedErrors) throws IOException {
        IOUtils.writeLines(toCSVFileContent(results, withColumnHeaders, excludeFixedErrors), null, out, StandardCharsets.UTF_8);
    }

    @Override
    public String getFileExtension() {
        return "csv";
    }

    @Override
    public String getFileContentType() {
        return "text/csv";
    }

    private List<String> toCSVFileContent(ContentIntegrityResults results, boolean withColumnHeaders, boolean excludeFixedErrors) {
        final List<String> lines = getReportContent(results, excludeFixedErrors);
        if (withColumnHeaders) {
            String header = getColumns();
            if (StringUtils.isBlank(header)) {
                final StringBuilder sb = new StringBuilder();
                for (String item : DEFAULT_CSV_HEADER_ITEMS) {
                    Utils.appendToCSVLine(sb, item);
                }
                header = sb.toString();
            }
            lines.add(0, header);
        }

        return lines;
    }
}
