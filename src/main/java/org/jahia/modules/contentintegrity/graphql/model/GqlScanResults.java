package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.modules.contentintegrity.services.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class GqlScanResults {

    private static final Logger logger = LoggerFactory.getLogger(GqlScanResults.class);

    private static final int MAX_PAGE_SIZE = 100;

    private final ContentIntegrityResults testResults;
    private final int errorCount, totalErrorCount;
    private final String reportFilePath, reportFileName;

    public GqlScanResults(String id, Collection<String> filters) {
        final ContentIntegrityResults all = Utils.getContentIntegrityService().getTestResults(id);
        if (all == null) {
            testResults = null;
            errorCount = 0;
            totalErrorCount = 0;
            reportFileName = null;
            reportFilePath = null;

            return;
        }

        if (CollectionUtils.isEmpty(filters)) {
            testResults = all;
        } else {
            final Map<String, String> filtersMap = filters.stream().collect(Collectors.toMap(f -> StringUtils.substringBefore(f, ";"), f -> StringUtils.substringAfter(f, ";")));
            final List<ContentIntegrityError> filteredErrors = all.getErrors().stream()
                    .filter(error ->
                            filtersMap.entrySet().stream().allMatch(filter -> StringUtils.equals(getColumnValue(error, filter.getKey()), filter.getValue()))
                    )
                    .collect(Collectors.toList());
            testResults = new ContentIntegrityResults(all.getTestDate(), all.getTestDuration(), all.getWorkspace(), filteredErrors, all.getExecutionLog());
        }
        errorCount = testResults.getErrors().size();
        totalErrorCount = all.getErrors().size();
        reportFileName = all.getMetadata("report-path");
        reportFilePath = all.getMetadata("report-filename");
    }

    public boolean isValid() {
        return testResults != null;
    }

    @GraphQLField
    public String getReportFilePath() {
        return reportFilePath;
    }

    @GraphQLField
    public String getReportFileName() {
        return reportFileName;
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
        return errorCount;
    }

    @GraphQLField
    public int getTotalErrorCount() {
        return totalErrorCount;
    }

    @GraphQLField
    public GqlScanResultsError getErrorById(@GraphQLName("id") String id) {
        return testResults.getErrors().stream()
                .filter(e -> StringUtils.equals(e.getErrorID(), id))
                .map(GqlScanResultsError::new)
                .findFirst().orElse(null);
    }

    @GraphQLField
    public Collection<GqlScanResultsColumn> getPossibleValues(@GraphQLName("names") Collection<String> cols) {
        final Map<String, Set<String>> values = cols.stream().collect(Collectors.toMap(name -> name, name -> new HashSet<>()));
        testResults.getErrors().forEach(error -> cols.forEach(col -> values.get(col).add(Optional.ofNullable(getColumnValue(error, col)).orElse(StringUtils.EMPTY))));

        return values.entrySet().stream()
                .map(e -> new GqlScanResultsColumn(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private String getColumnValue(ContentIntegrityError error, String column) {
        switch (column) {
            case "checkName":
                return error.getIntegrityCheckName();
            case "errorType":
                return String.valueOf(Optional.ofNullable(error.getErrorType()).orElse(StringUtils.EMPTY));
            case "workspace":
                return error.getWorkspace();
            case "site":
                return error.getSite();
            case "nodePrimaryType":
                return error.getPrimaryType();
            case "locale":
                return error.getLocale();
            case "message":
                return error.getConstraintMessage();
            default:
                return StringUtils.EMPTY;
        }
    }
}
