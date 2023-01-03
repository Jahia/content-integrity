package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLField;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.modules.contentintegrity.services.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.stream.Collectors;

public class GqlScanResults {

    private static final Logger logger = LoggerFactory.getLogger(GqlScanResults.class);

    private final ContentIntegrityResults testResults;

    public GqlScanResults(String id) {
        testResults = Utils.getContentIntegrityService().getTestResults(id);
    }

    public boolean isValid() {
        return testResults != null;
    }

    @GraphQLField
    public String getReportFilePath() {
        return testResults.getMetadata("report-path");
    }

    @GraphQLField
    public String getReportFileName() {
        return testResults.getMetadata("report-filename");
    }

    @GraphQLField
    public Collection<GqlScanResultsError> getErrors() {
        return testResults.getErrors().stream()
                .map(GqlScanResultsError::new)
                .collect(Collectors.toList());
    }
}
