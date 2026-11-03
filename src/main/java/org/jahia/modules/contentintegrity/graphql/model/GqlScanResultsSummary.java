package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResultsSummary;

import java.time.Instant;

// The stored results of a scan, without their errors
public class GqlScanResultsSummary {

    private final ContentIntegrityResultsSummary summary;

    public GqlScanResultsSummary(ContentIntegrityResultsSummary summary) {
        this.summary = summary;
    }

    @GraphQLField
    @GraphQLName("id")
    public String getID() {
        return summary.getID();
    }

    @GraphQLField
    @GraphQLDescription("Date at which the scan was started, in the ISO-8601 format")
    public String getStartDate() {
        return Instant.ofEpochMilli(summary.getTestDate()).toString();
    }

    @GraphQLField
    @GraphQLDescription("The scanned workspace, or all-workspaces when the scan covered both")
    public String getWorkspace() {
        return summary.getWorkspace();
    }

    @GraphQLField
    @GraphQLDescription("running, finished, interrupted when the scan was stopped before its end (its errors are then those found until then), " +
            "or failed")
    public String getStatus() {
        return summary.getStatus().getValue();
    }

    @GraphQLField
    @GraphQLDescription("The number of errors found by the scan, including those fixed since. 0 until the scan is over")
    public long getErrorCount() {
        return summary.getErrorCount();
    }

    @GraphQLField
    @GraphQLDescription("The number of errors which block an XML import of the scanned content, including those fixed since")
    public long getImportErrorCount() {
        return summary.getImportErrorCount();
    }
}
