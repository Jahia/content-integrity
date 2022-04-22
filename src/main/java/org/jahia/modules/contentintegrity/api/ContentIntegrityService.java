package org.jahia.modules.contentintegrity.api;

import org.jahia.modules.contentintegrity.services.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;

import java.util.List;

public interface ContentIntegrityService {

    ContentIntegrityResults validateIntegrity(String path, String workspace);

    ContentIntegrityResults validateIntegrity(String path, List<String> excludedPaths, String workspace, List<String> checksToExecute);

    void fixError(ContentIntegrityError error);

    ContentIntegrityCheck getContentIntegrityCheck(String id);

    ContentIntegrityResults getLatestTestResults();

    ContentIntegrityResults getTestResults(String testDate);

    List<String> getTestIDs();

    List<String> printIntegrityChecksList(boolean simpleOutput);

    List<String> getContentIntegrityChecksIdentifiers(boolean activeOnly);
}
