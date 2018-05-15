package org.jahia.modules.verifyintegrity.api;

import org.jahia.modules.verifyintegrity.services.ContentIntegrityError;
import org.jahia.modules.verifyintegrity.services.ContentIntegrityResults;

import java.util.List;

public interface ContentIntegrityService {

    ContentIntegrityResults validateIntegrity(String path, String workspace);

    void fixError(ContentIntegrityError error);

    ContentIntegrityResults getLatestTestResults();

    ContentIntegrityResults getTestResults(String testDate);

    List<String> getTestResultsDates();

    List<String> printIntegrityChecksList(boolean simpleOutput);
}
