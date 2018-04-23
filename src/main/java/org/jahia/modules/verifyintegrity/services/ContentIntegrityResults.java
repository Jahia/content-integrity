package org.jahia.modules.verifyintegrity.services;

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
    private final List<ContentIntegrityError> errors;


    public ContentIntegrityResults(Long testDate, Long testDuration, List<ContentIntegrityError> errors) {
        this.testDate = testDate;
        formattedTestDate = FastDateFormat.getInstance("yyyy_MM_dd-HH_mm_ss_SSS").format(testDate);
        this.testDuration = testDuration;
        this.formattedTestDuration = DateUtils.formatDurationWords(testDuration);
        this.errors = errors;
    }

    public Long getTestDate() {
        return testDate;
    }

    public String getFormattedTestDate() {
        return formattedTestDate;
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
}
