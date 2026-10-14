package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@GraphQLDescription("The outcome of the fix of all the errors matching some filters")
public class GqlFixAllErrorsResult {

    /**
     * Why an error is skipped by the fix of all the errors
     */
    public enum SkipReason {
        NO_FIX, TAKES_VALUES, VIRTUAL_NODE, NODE_NOT_FOUND
    }

    private int fixed;
    private int failed;
    private int skipped;
    private int alreadyFixed;
    private final List<String> failedIds = new ArrayList<>();
    private final Map<SkipReason, Integer> skippedByReason = new LinkedHashMap<>();
    private final Map<List<String>, GqlFixFailure> failures = new LinkedHashMap<>();

    void addFixed() {
        fixed++;
    }

    void addFailed(ContentIntegrityError error) {
        failed++;
        failedIds.add(error.getErrorID());
        final String errorType = Optional.ofNullable(error.getErrorType()).map(ContentIntegrityErrorType::getKey).orElse(null);
        failures.computeIfAbsent(Arrays.asList(error.getIntegrityCheckName(), errorType, error.getFixFailureMessage()),
                key -> new GqlFixFailure(key.get(0), key.get(1), key.get(2))).count++;
    }

    void addSkipped(SkipReason reason) {
        skipped++;
        skippedByReason.merge(reason, 1, Integer::sum);
    }

    void addAlreadyFixed() {
        alreadyFixed++;
    }

    @GraphQLField
    @GraphQLDescription("Number of errors fixed")
    public int getFixed() {
        return fixed;
    }

    @GraphQLField
    @GraphQLDescription("Number of errors whose fix has failed")
    public int getFailed() {
        return failed;
    }

    @GraphQLField
    @GraphQLDescription("Identifiers of the errors whose fix has failed, in the order of the results")
    public List<String> getFailedIds() {
        return Collections.unmodifiableList(failedIds);
    }

    @GraphQLField
    @GraphQLDescription("The errors whose fix has failed, grouped by check, error type and reason")
    public List<GqlFixFailure> getFailures() {
        return new ArrayList<>(failures.values());
    }

    @GraphQLField
    @GraphQLDescription("Number of errors skipped: their check provides no fix, they are fixed with values typed by an administrator, " +
            "their node is virtual, or it does not exist anymore")
    public int getSkipped() {
        return skipped;
    }

    @GraphQLField
    @GraphQLDescription("The errors skipped, counted by reason: NO_FIX, TAKES_VALUES, VIRTUAL_NODE or NODE_NOT_FOUND")
    public List<GqlSkippedErrors> getSkippedReasons() {
        final List<GqlSkippedErrors> reasons = new ArrayList<>();
        skippedByReason.forEach((reason, count) -> reasons.add(new GqlSkippedErrors(reason.name(), count)));
        return reasons;
    }

    @GraphQLField
    @GraphQLDescription("Number of errors which were already fixed")
    public int getAlreadyFixed() {
        return alreadyFixed;
    }

    @GraphQLDescription("Errors skipped for the same reason")
    public static class GqlSkippedErrors {
        private final String reason;
        private final int count;

        GqlSkippedErrors(String reason, int count) {
            this.reason = reason;
            this.count = count;
        }

        @GraphQLField
        @GraphQLDescription("NO_FIX: the check provides no fix for the error. TAKES_VALUES: the error is fixed with values typed by an administrator. " +
                "VIRTUAL_NODE: the node is served by an external provider. NODE_NOT_FOUND: the node does not exist anymore, for example removed by another fix")
        public String getReason() {
            return reason;
        }

        @GraphQLField
        public int getCount() {
            return count;
        }
    }

    @GraphQLDescription("Errors of the same type whose fix has failed for the same reason")
    public static class GqlFixFailure {
        private final String checkName;
        private final String errorType;
        private final String message;
        private int count;

        GqlFixFailure(String checkName, String errorType, String message) {
            this.checkName = checkName;
            this.errorType = errorType;
            this.message = message;
        }

        @GraphQLField
        public String getCheckName() {
            return checkName;
        }

        @GraphQLField
        public String getErrorType() {
            return errorType;
        }

        @GraphQLField
        @GraphQLDescription("The message of the exception raised by the fix, null when the fix has run without fixing the error")
        public String getMessage() {
            return message;
        }

        @GraphQLField
        public int getCount() {
            return count;
        }
    }
}
