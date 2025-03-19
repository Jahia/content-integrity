package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.modules.contentintegrity.services.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GqlScanResults {

    private static final Logger logger = LoggerFactory.getLogger(GqlScanResults.class);

    private static final int MAX_PAGE_SIZE = 100;

    private final List<ContentIntegrityError> filteredErrors;
    private final List<ContentIntegrityError> allErrors;
    private final int errorCount, totalErrorCount;
    private final Collection<String> currentFilters;
    private final List<GqlScanReportFile> reports;

    public GqlScanResults(String id, Collection<String> filters) {
        final ContentIntegrityResults all = Utils.getContentIntegrityService().getTestResults(id);
        if (all == null) {
            allErrors = null;
            filteredErrors = null;
            errorCount = 0;
            totalErrorCount = 0;
            currentFilters = null;
            reports = new ArrayList<>();

            return;
        }

        currentFilters = filters;
        allErrors = all.getErrors();
        filteredErrors = getFilteredErrors(null);
        errorCount = filteredErrors.size();
        totalErrorCount = all.getErrors().size();
        reports = all.getReports().stream()
                .map(GqlScanReportFile::new)
                .collect(Collectors.toList());
    }

    private List<ContentIntegrityError> getFilteredErrors(Collection<String> ignoredFilters) {
        if (CollectionUtils.isEmpty(currentFilters)) return allErrors;

        final Map<String, String> filtersMap = currentFilters.stream()
                .map(f -> StringUtils.split(f, ";", 2))
                .filter(f -> f.length == 2)
                .filter(f -> ignoredFilters == null || !ignoredFilters.contains(f[0]))
                .collect(Collectors.toMap(f -> f[0], f -> f[1]));

        return allErrors.stream()
                .filter(error ->
                        filtersMap.entrySet().stream().allMatch(filter -> columnValueMatches(error, filter.getKey(), filter.getValue()))
                )
                .collect(Collectors.toList());
    }

    public boolean isValid() {
        return filteredErrors != null;
    }

    @GraphQLField
    public List<GqlScanReportFile> getReports() {
        return reports;
    }

    @GraphQLField
    public Collection<GqlScanResultsError> getErrors(@GraphQLName("offset") int offset, @GraphQLName("pageSize") int pageSize) {
        if (offset < 0 || offset >= getErrorCount() || pageSize < 1) return CollectionUtils.emptyCollection();

        return filteredErrors.stream()
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
        return filteredErrors.stream()
                .filter(e -> StringUtils.equals(e.getErrorID(), id))
                .map(GqlScanResultsError::new)
                .findFirst().orElse(null);
    }

    @GraphQLField
    public Collection<GqlScanResultsColumn> getPossibleValues(@GraphQLName("names") Collection<String> cols, @GraphQLName("withErrorsOnly") boolean withErrorsOnly) {
        final Map<String, Map<String, Long>> columnsData = cols.stream()
                .collect(Collectors.toMap(Function.identity(),
                        name -> {
                            final List<ContentIntegrityError> errorsWithOtherFilters = getFilteredErrors(Collections.singleton(name));
                            final Map<String, Long> result = errorsWithOtherFilters.stream()
                                    .map(error -> getColumnValue(error, name))
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

                            if (withErrorsOnly) return result;

                            allErrors.stream()
                                    .map(error -> getColumnValue(error, name))
                                    .filter(Objects::nonNull)
                                    .distinct()
                                    .filter(val -> !result.containsKey(val))
                                    .forEach(val -> result.put(val, 0L));
                            return result;
                        }
                ));

        return columnsData.entrySet().stream()
                .map(column -> new GqlScanResultsColumn(column.getKey(), column.getValue()))
                .collect(Collectors.toList());
    }

    private String getColumnValue(ContentIntegrityError error, String column) {
        switch (column) {
            case "checkName":
                return error.getIntegrityCheckName();
            case "errorType":
                return Optional.ofNullable(error.getErrorType()).map(ContentIntegrityErrorType::getKey).orElse(StringUtils.EMPTY);
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
            case "importError":
                return Optional.ofNullable(error.getErrorType()).map(ContentIntegrityErrorType::isBlockingImport).orElse(Boolean.FALSE).toString();
            default:
                return StringUtils.EMPTY;
        }
    }

    private boolean columnValueMatches(ContentIntegrityError error, String column, String value) {
        return StringUtils.equals(getColumnValue(error, column), value);
    }
}
