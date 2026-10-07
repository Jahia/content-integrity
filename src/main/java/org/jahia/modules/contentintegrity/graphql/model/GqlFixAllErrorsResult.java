package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;

@GraphQLDescription("The outcome of the fix of all the errors matching some filters")
public class GqlFixAllErrorsResult {

    private int fixed;
    private int failed;
    private int skipped;
    private int alreadyFixed;

    void addFixed() {
        fixed++;
    }

    void addFailed() {
        failed++;
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
