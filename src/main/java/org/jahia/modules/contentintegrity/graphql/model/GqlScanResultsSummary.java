package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;

import java.time.Instant;
import java.util.Optional;

// The stored results of a scan, without their errors
public class GqlScanResultsSummary {

    private final ContentIntegrityResults results;

    public GqlScanResultsSummary(ContentIntegrityResults results) {
        this.results = results;
    }

    @GraphQLField
    @GraphQLName("id")
    public String getID() {
        return results.getID();
    }

    @GraphQLField
    @GraphQLDescription("Date at which the scan was started, in the ISO-8601 format")
    public String getStartDate() {
        return Instant.ofEpochMilli(results.getTestDate()).toString();
    }

    @GraphQLField
    @GraphQLDescription("The scanned workspace, or all-workspaces when the scan covered both")
    public String getWorkspace() {
        return results.getWorkspace();
    }

    @GraphQLField
    @GraphQLDescription("finished, or interrupted when the scan was stopped before its end: the errors are then those found until then")
    public String getStatus() {
        return results.isInterrupted() ? "interrupted" : "finished";
    }

    @GraphQLField
    @GraphQLDescription("The number of errors found by the scan, including those fixed since")
    public int getErrorCount() {
        return results.getErrors().size();
    }

    @GraphQLField
    @GraphQLDescription("The number of errors which block an XML import of the scanned content, including those fixed since")
    public long getImportErrorCount() {
        return results.getErrors().stream()
                .filter(e -> Optional.ofNullable(e.getErrorType()).map(ContentIntegrityErrorType::isBlockingImport).orElse(Boolean.FALSE))
                .count();
    }
}
