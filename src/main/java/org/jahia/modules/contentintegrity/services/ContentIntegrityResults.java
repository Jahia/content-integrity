package org.jahia.modules.contentintegrity.services;

import org.apache.commons.lang.time.FastDateFormat;
import org.jahia.utils.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ContentIntegrityResults {

    private static final Logger logger = LoggerFactory.getLogger(ContentIntegrityResults.class);

    private final Long testDate;
    private final String formattedTestDate;
    private final Long testDuration;
    private final String formattedTestDuration;
    private final String workspace;
    private final List<ContentIntegrityError> errors;
    private String executionID;


    public ContentIntegrityResults(Long testDate, Long testDuration, String workspace, List<ContentIntegrityError> errors) {
        this.testDate = testDate;
        formattedTestDate = FastDateFormat.getInstance("yyyy_MM_dd-HH_mm_ss_SSS").format(testDate);
        this.testDuration = testDuration;
        this.formattedTestDuration = DateUtils.formatDurationWords(testDuration);
        this.workspace = workspace;
        this.errors = errors;
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
        return errors;
    }

    public String getWorkspace() {
        return workspace;
    }

    public String getExecutionID() {
        return executionID;
    }

    public ContentIntegrityResults setExecutionID(String executionID) {
        this.executionID = executionID;
        return this;
    }
}
