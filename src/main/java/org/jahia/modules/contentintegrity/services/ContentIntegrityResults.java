package org.jahia.modules.contentintegrity.services;

import org.apache.commons.lang.time.FastDateFormat;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.utils.DateUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ContentIntegrityResults {

    /**
     * The status of the scan which produced the results. They are stored in the JCR from the start of the scan.
     */
    public enum Status {
        RUNNING, FINISHED,
        // Stopped before its end: the errors are those found until then
        INTERRUPTED,
        FAILED;

        public String getValue() {
            return name().toLowerCase(Locale.ENGLISH);
        }

        public static Status fromValue(String value) {
            for (Status status : values()) {
                if (status.getValue().equals(value)) return status;
            }
            return null;
        }
    }

    private final Long testDate;
    private final String formattedTestDate;
    private final Long testDuration;
    private final String formattedTestDuration;
    private final String workspace;
    private final List<ContentIntegrityError> errors;
    private final List<String> executionLog;
    private final List<ContentIntegrityReport> reports;
    private Status status = Status.FINISHED;

    public ContentIntegrityResults(Long testDate, Long testDuration, String workspace, List<ContentIntegrityError> errors, List<String> executionLog) {
        this.testDate = testDate;
        formattedTestDate = FastDateFormat.getInstance("yyyy_MM_dd-HH_mm_ss_SSS").format(testDate);
        this.testDuration = testDuration;
        this.formattedTestDuration = DateUtils.formatDurationWords(testDuration);
        this.workspace = workspace;
        this.errors = errors;
        this.executionLog = executionLog;
        reports = new ArrayList<>();
    }

    public Long getTestDate() {
        return testDate;
    }

    public String getID() {
        return String.format("%s_%s", workspace, formattedTestDate);
    }

    public Long getTestDuration() {
        return testDuration;
    }

    public String getFormattedTestDuration() {
        return formattedTestDuration;
    }

    public List<ContentIntegrityError> getErrors() {
        return Collections.unmodifiableList(errors);
    }

    public String getWorkspace() {
        return workspace;
    }

    public List<String> getExecutionLog() {
        return Collections.unmodifiableList(executionLog);
    }

    public Status getStatus() {
        return status;
    }

    public ContentIntegrityResults setStatus(Status status) {
        this.status = status;
        return this;
    }

    public boolean isInterrupted() {
        return status == Status.INTERRUPTED;
    }

    public ContentIntegrityResults setInterrupted(boolean interrupted) {
        return setStatus(interrupted ? Status.INTERRUPTED : Status.FINISHED);
    }

    public String getSignature(boolean excludeFixedErrors) {
        return String.format("%s-%s", getID(), excludeFixedErrors ? "remainingErrors" : "full");
    }

    public void addJcrReport(String name, String path, String extension) {
        addReport(name, path, extension, ContentIntegrityReport.LOCATION.JCR);
    }

    public void addFilesystemReport(String name, String path, String extension) {
        addReport(name, path, extension, ContentIntegrityReport.LOCATION.FILESYSTEM);
    }

    private void addReport(String name, String path, String extension, ContentIntegrityReport.LOCATION location) {
        final ContentIntegrityReport report = new ContentIntegrityReport(name, location, path, extension);
        reports.add(report);
    }

    public List<ContentIntegrityReport> getReports() {
        return Collections.unmodifiableList(reports);
    }
}
