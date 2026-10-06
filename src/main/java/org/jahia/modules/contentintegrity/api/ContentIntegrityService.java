package org.jahia.modules.contentintegrity.api;

import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.modules.contentintegrity.services.exceptions.ConcurrentExecutionException;

import javax.jcr.RepositoryException;
import java.util.List;

public interface ContentIntegrityService {

    ContentIntegrityResults validateIntegrity(String path, String workspace) throws ConcurrentExecutionException;

    ContentIntegrityResults validateIntegrity(String path, List<String> excludedPaths, boolean skipMountPoints, String workspace, List<String> checksToExecute, ExternalLogger externalLogger) throws ConcurrentExecutionException;

    void fixError(ContentIntegrityError error);

    /**
     * Fixes the error with values provided by an administrator, when the check which has detected it supports it.
     *
     * @throws RepositoryException if the values are invalid, or if the fix fails, with a message which explains why
     */
    void fixError(ContentIntegrityError error, List<String> values) throws RepositoryException;

    /**
     * @return the description of the values to provide to fix the error, null if its fix doesn't take values
     */
    FixValuesDefinition getFixValuesDefinition(ContentIntegrityError error);

    ContentIntegrityCheck getContentIntegrityCheck(String id);

    void storeErrorsInCache(ContentIntegrityResults results);

    void removeErrorsFromCache(ContentIntegrityResults results);

    ContentIntegrityResults getLatestTestResults();

    ContentIntegrityResults getTestResults(String testDate);

    List<String> getTestIDs();

    List<String> printIntegrityChecksList(boolean simpleOutput);

    List<String> getContentIntegrityChecksIdentifiers(boolean activeOnly);

    boolean isScanRunning();

    void stopRunningScan();
}
