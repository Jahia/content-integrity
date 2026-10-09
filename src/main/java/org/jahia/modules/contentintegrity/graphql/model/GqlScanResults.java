package org.jahia.modules.contentintegrity.graphql.model;

import graphql.ErrorType;
import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.api.ContentIntegrityService;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.modules.contentintegrity.services.Utils;
import org.jahia.modules.graphql.provider.dxm.BaseGqlClientException;
import org.jahia.modules.graphql.provider.dxm.node.GqlJcrWrongInputException;

import javax.jcr.RepositoryException;
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

    private static final int MAX_PAGE_SIZE = 100;

    private final List<ContentIntegrityError> filteredErrors;
    private final List<ContentIntegrityError> allErrors;
    private final int errorCount, totalErrorCount;
    private final Collection<String> currentFilters;
    private final List<GqlScanReportFile> reports;
    private final ContentIntegrityResults results;

    public GqlScanResults(String id, Collection<String> filters) {
        final ContentIntegrityResults all = Utils.getContentIntegrityService().getTestResults(id);
        results = all;
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
    @GraphQLDescription("Fixes the error with the fix of the check which has detected it, and returns the error. Its field 'fixed' tells if the fix has succeeded. " +
            "Requires the permission adminContentIntegrityFix")
    public GqlScanResultsError fixError(@GraphQLName("id") @GraphQLNonNull String id,
                                        @GraphQLName("values") @GraphQLDescription("The values to fix the error with, when its field 'fixWithValues' is true") List<String> values) {
        checkFixPermission();
        final ContentIntegrityError error = allErrors.stream()
                .filter(e -> StringUtils.equals(e.getErrorID(), id))
                .findFirst().orElse(null);
        if (error == null) return null;
        if (!error.isFixed()) {
            final ContentIntegrityService service = Utils.getContentIntegrityService();
            if (values == null) {
                service.fixError(error);
            } else {
                try {
                    service.fixError(error, values);
                } catch (RepositoryException e) {
                    throw new GqlJcrWrongInputException(e.getMessage());
                }
            }
            // The fixed status is stored with the results, in the JCR
            if (error.isFixed()) service.saveFixedErrors(results);
        }
        return new GqlScanResultsError(error);
    }

    @GraphQLField
    @GraphQLDescription("Fixes all the errors matching the filters of these results, each with the fix of the check which has detected it. " +
            "The errors whose check provides no fix, the ones fixed with values typed by an administrator, and the ones of a virtual node are skipped. " +
            "Requires the permission adminContentIntegrityFix")
    public GqlFixAllErrorsResult fixAllErrors() {
        checkFixPermission();
        final GqlFixAllErrorsResult result = new GqlFixAllErrorsResult();
        if (filteredErrors == null) return result;

        final ContentIntegrityService service = Utils.getContentIntegrityService();
        for (ContentIntegrityError error : filteredErrors) {
            if (error.isFixed()) {
                result.addAlreadyFixed();
                continue;
            }
            // An error whose fix takes values is fixed with values chosen by an administrator
            if (!service.isFixable(error) || service.getFixValuesDefinition(error) != null) {
                result.addSkipped();
                continue;
            }
            // A fix can make another error of the list unfixable, for example when it removes its node: it is then counted as failed
            service.fixError(error);
            if (error.isFixed()) result.addFixed();
            else result.addFailed(error.getErrorID());
        }
        // The fixed status is stored with the results, in the JCR
        if (result.getFixed() > 0) service.saveFixedErrors(results);
        return result;
    }

    private static void checkFixPermission() {
        if (!Utils.canFixErrors()) {
            throw new BaseGqlClientException("The current user is not allowed to fix the errors: the permission " + Utils.FIX_PERMISSION + " is required", ErrorType.DataFetchingException);
        }
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
