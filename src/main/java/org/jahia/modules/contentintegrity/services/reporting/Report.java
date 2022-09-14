package org.jahia.modules.contentintegrity.services.reporting;

import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.settings.SettingsBean;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Collectors;

public abstract class Report {

    public static final String REPORT_COLUMN_NAMES_CONF = "modules.contentIntegrity.csv.header";

    abstract public void write(OutputStream stream, ContentIntegrityResults results, boolean withColumnHeaders, boolean excludeFixedErrors) throws IOException;

    public final String getFileName(String signature) {
        return String.format("%s.%s", signature, getFileExtension());
    }

    abstract public String getFileExtension();

    abstract public String getFileContentType();

    protected final String getColumns() {
        return SettingsBean.getInstance().getString(REPORT_COLUMN_NAMES_CONF, null);
    }

    protected final List<String> getReportContent(ContentIntegrityResults results, boolean excludeFixedErrors) {
        return results.getErrors().stream()
                .filter(error -> !excludeFixedErrors || !error.isFixed())
                .map(ContentIntegrityError::toCSV)
                .collect(Collectors.toList());
    }

}
