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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GqlScanResults {

    private static final Logger logger = LoggerFactory.getLogger(GqlScanResults.class);

    private static final int MAX_PAGE_SIZE = 100;

    private final List<ContentIntegrityError> errors;
    private final int errorCount, totalErrorCount;
    private final List<GqlScanReportFile> reports;

    public GqlScanResults(String id, Collection<String> filters) {
        final ContentIntegrityResults all = Utils.getContentIntegrityService().getTestResults(id);
        if (all == null) {
            errors = null;
            errorCount = 0;
            totalErrorCount = 0;
            reports = new ArrayList<>();

            return;
        }

        if (CollectionUtils.isEmpty(filters)) {
            errors = all.getErrors();
        } else {
            final Map<String, String> filtersMap = filters.stream()
                    .map(f -> StringUtils.split(f, ";", 2))
                    .filter(f -> f.length == 2)
                    .collect(Collectors.toMap(f -> f[0], f -> f[1]));
            errors = all.getErrors().stream()
                    .filter(error ->
                            filtersMap.entrySet().stream().allMatch(filter -> columnValueMatches(error, filter.getKey(), filter.getValue()))
                    )
                    .collect(Collectors.toList());
        }
        errorCount = errors.size();
        totalErrorCount = all.getErrors().size();
        reports = all.getReports().stream()
                .map(GqlScanReportFile::new)
                .collect(Collectors.toList());
    }

    public boolean isValid() {
        return errors != null;
    }

    @GraphQLField
    public List<GqlScanReportFile> getReports() {
        return reports;
    }

    @GraphQLField
    public Collection<GqlScanResultsError> getErrors(@GraphQLName("offset") int offset, @GraphQLName("pageSize") int pageSize) {
        if (offset < 0 || offset >= getErrorCount() || pageSize < 1) return CollectionUtils.emptyCollection();

        return errors.stream()
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
        return errors.stream()
                .filter(e -> StringUtils.equals(e.getErrorID(), id))
                .map(GqlScanResultsError::new)
                .findFirst().orElse(null);
    }

    @GraphQLField
    public Collection<GqlScanResultsColumn> getPossibleValues(@GraphQLName("names") Collection<String> cols, @GraphQLName("withErrorsOnly") boolean withErrorsOnly) {
        final Map<String, Map<String, Long>> columnsData = cols.stream()
                .collect(Collectors.toMap(Function.identity(),
                        name -> errors.stream()
                                .map(error -> Optional.ofNullable(getColumnValue(error, name)).orElse(StringUtils.EMPTY))
                                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
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
