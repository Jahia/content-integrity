package org.jahia.modules.contentintegrity.services;

/**
 * Stored results without their errors: what is known about them from the properties of their report node.
 */
public class ContentIntegrityResultsSummary {

    private final String id;
    private final long testDate;
    private final String workspace;
    private final ContentIntegrityResults.Status status;
    private final long errorCount;
    private final long importErrorCount;

    public ContentIntegrityResultsSummary(String id, long testDate, String workspace, ContentIntegrityResults.Status status, long errorCount, long importErrorCount) {
        this.id = id;
        this.testDate = testDate;
        this.workspace = workspace;
        this.status = status;
        this.errorCount = errorCount;
        this.importErrorCount = importErrorCount;
    }

    public String getID() {
        return id;
    }

    public long getTestDate() {
        return testDate;
    }

    public String getWorkspace() {
        return workspace;
    }

    public ContentIntegrityResults.Status getStatus() {
        return status;
    }

    public long getErrorCount() {
        return errorCount;
    }

    // The errors which block an XML import of the scanned content
    public long getImportErrorCount() {
        return importErrorCount;
    }
}
