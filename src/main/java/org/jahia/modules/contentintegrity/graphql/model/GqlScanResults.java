package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.modules.contentintegrity.services.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.stream.Collectors;

public class GqlScanResults {

    private static final Logger logger = LoggerFactory.getLogger(GqlScanResults.class);

    private static final int MAX_PAGE_SIZE = 100;

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
    public Collection<GqlScanResultsError> getErrors(@GraphQLName("offset") int offset, @GraphQLName("pageSize") int pageSize) {
        if (offset < 0 || offset >= getErrorCount() || pageSize < 1) return CollectionUtils.emptyCollection();

        return testResults.getErrors().stream()
                .skip(offset)
                .limit(Math.min(pageSize, MAX_PAGE_SIZE))
                .map(GqlScanResultsError::new)
                .collect(Collectors.toList());
    }

    @GraphQLField
    public int getErrorCount() {
        return testResults.getErrors().size();
    }

    @GraphQLField
    public GqlScanResultsError getErrorById(@GraphQLName("id") String id) {
        return testResults.getErrors().stream()
                .filter(e -> StringUtils.equals(e.getErrorID(), id))
                .map(GqlScanResultsError::new)
                .findFirst().orElse(null);
    }
}
