package org.jahia.modules.contentintegrity.api;

import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResultsSummary;
import org.jahia.modules.contentintegrity.services.exceptions.ConcurrentExecutionException;

import javax.jcr.RepositoryException;
import java.util.List;

public interface ContentIntegrityService {

    /**
     * Scans the subtree of a node in a workspace, and stores its results in the JCR, from the start of the scan.
     */
    ContentIntegrityResults validateIntegrity(String path, String workspace) throws ConcurrentExecutionException;

    /**
     * Scans the subtree of a node in a workspace, and stores its results in the JCR, from the start of the scan.
     */
    ContentIntegrityResults validateIntegrity(String path, List<String> excludedPaths, boolean skipMountPoints, String workspace, List<String> checksToExecute, ExternalLogger externalLogger) throws ConcurrentExecutionException;

    /**
     * Scans the subtree of a node in a workspace.
     *
     * @param persistResults true to store the results in the JCR, false when the caller stores them itself, for example
     *                       once it has merged the results of several workspaces
     */
    ContentIntegrityResults validateIntegrity(String path, List<String> excludedPaths, boolean skipMountPoints, String workspace, List<String> checksToExecute, ExternalLogger externalLogger, boolean persistResults) throws ConcurrentExecutionException;

    void fixError(ContentIntegrityError error);

    /**
     * Fixes the error with values provided by an administrator, when the check which has detected it supports it.
     *
     * @throws RepositoryException if the values are invalid, or if the fix fails, with a message which explains why
     */
    void fixError(ContentIntegrityError error, List<String> values) throws RepositoryException;

    /**
     * @return true if the check which has detected the error provides a fix for it, and its node still exists and is not virtual
     */
    boolean isFixable(ContentIntegrityError error);

    /**
     * @return the description of the values to provide to fix the error, null if it is not fixable or if its fix doesn't take
     * values. Reads the node of the error
     */
    FixValuesDefinition getFixValuesDefinition(ContentIntegrityError error);

    ContentIntegrityCheck getContentIntegrityCheck(String id);

    /**
     * Stores the results in the JCR: creates or updates their report, with their status and the log of the scan.
     *
     * @param withErrors true once the scan is over, to store its errors
     * @return true if the results have been stored
     */
    boolean saveResults(ContentIntegrityResults results, List<String> executionLog, boolean withErrors);

    /**
     * Stores the errors of the results fixed since they were stored.
     */
    void saveFixedErrors(ContentIntegrityResults results);

    /**
     * @return the stored results, from the oldest scan to the latest, without their errors
     */
    List<ContentIntegrityResultsSummary> getResultsSummaries();

    /**
     * @return the latest stored results of a scan which is over, or null
     */
    ContentIntegrityResults getLatestTestResults();

    /**
     * @return the stored results with this identifier, or null. Those of a scan which runs, or which failed, have no errors
     */
    ContentIntegrityResults getTestResults(String testID);

    /**
     * @return the log of the scan of the stored results with this identifier, as stored in the JCR, or null
     */
    List<String> getExecutionLog(String testID);

    /**
     * @return the identifiers of the stored results, from the oldest scan to the latest
     */
    List<String> getTestIDs();

    List<String> printIntegrityChecksList(boolean simpleOutput);

    List<String> getContentIntegrityChecksIdentifiers(boolean activeOnly);

    boolean isScanRunning();

    void stopRunningScan();
}
