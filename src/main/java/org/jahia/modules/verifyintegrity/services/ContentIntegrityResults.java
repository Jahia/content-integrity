package org.jahia.modules.verifyintegrity.services;

import org.apache.commons.lang.time.FastDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ContentIntegrityResults {

    private static final Logger logger = LoggerFactory.getLogger(ContentIntegrityResults.class);

    private final Long testDate;
    private final String formattedTestDate;
    private final List<ContentIntegrityError> errors;


    public ContentIntegrityResults(Long testDate, List<ContentIntegrityError> errors) {
        this.testDate = testDate;
        formattedTestDate = FastDateFormat.getInstance("yyyy_MM_dd-HH_mm_ss_SSS").format(testDate);
        this.errors = errors;
    }

    public Long getTestDate() {
        return testDate;
    }

    public String getFormattedTestDate() {
        return formattedTestDate;
    }

    public List<ContentIntegrityError> getErrors() {
        return errors;
    }
}
