package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@GraphQLDescription("The outcome of the fix of all the errors matching some filters")
public class GqlFixAllErrorsResult {

    private int fixed;
    private int failed;
    private int skipped;
    private int alreadyFixed;
    private final List<String> failedIds = new ArrayList<>();

    void addFixed() {
        fixed++;
    }

    void addFailed(String errorId) {
        failed++;
        failedIds.add(errorId);
    }

    void addSkipped() {
        skipped++;
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
    @GraphQLDescription("Number of errors skipped: their check provides no fix, or they are fixed with values typed by an administrator")
    public int getSkipped() {
        return skipped;
    }

    @GraphQLField
    @GraphQLDescription("Number of errors which were already fixed")
    public int getAlreadyFixed() {
        return alreadyFixed;
    }
}
