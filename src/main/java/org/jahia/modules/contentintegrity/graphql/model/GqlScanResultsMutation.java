package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;

import java.util.List;

@GraphQLName("ScanResultsMutation")
public class GqlScanResultsMutation {

    private final GqlScanResults results;

    public GqlScanResultsMutation(GqlScanResults results) {
        this.results = results;
    }

    @GraphQLField
    @GraphQLDescription("Fixes the error with the fix of the check which has detected it, and returns the error. Its field 'fixed' tells if the fix has succeeded. " +
            "Requires the permission adminContentIntegrityFix")
    public GqlScanResultsError fixError(@GraphQLName("id") @GraphQLNonNull String id,
                                        @GraphQLName("values") @GraphQLDescription("The values to fix the error with, when its field 'fixWithValues' is true") List<String> values) {
        return results.fixError(id, values);
    }

    @GraphQLField
    @GraphQLDescription("Fixes several errors together, such as the errors of a node, which can block each other: a node is saved only once all " +
            "its mandatory properties are set. Returns the errors, whose field 'fixed' tells if their fix has succeeded. " +
            "Requires the permission adminContentIntegrityFix")
    public List<GqlScanResultsError> fixErrors(@GraphQLName("ids") @GraphQLNonNull List<String> ids,
                                               @GraphQLName("values") @GraphQLDescription("The values of each error, in the order of the IDs: null for an error fixed without values") List<List<String>> values) {
        return results.fixErrors(ids, values);
    }

    @GraphQLField
    @GraphQLDescription("Fixes all the errors matching the filters of these results, each with the fix of the check which has detected it. " +
            "The errors whose check provides no fix, the ones fixed with values typed by an administrator, and the ones of a virtual node are skipped. " +
            "Requires the permission adminContentIntegrityFix")
    public GqlFixAllErrorsResult fixAllErrors() {
        return results.fixAllErrors();
    }
}
