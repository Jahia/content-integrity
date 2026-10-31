package org.jahia.modules.contentintegrity.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.contentintegrity.graphql.model.GqlIntegrityMutation;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;

/*
 * The operations which change the server are mutations: the fields of the queries only read.
 */
@GraphQLTypeExtension(DXGraphQLProvider.Mutation.class)
public class MutationExtensions {

    @GraphQLField
    @GraphQLName("contentIntegrity")
    @GraphQLDescription("Starts and stops the scans, fixes the errors, deletes the stored results and configures the checks. " +
            "Requires the permission adminContentIntegrity")
    public static GqlIntegrityMutation getService() {
        return new GqlIntegrityMutation(QueryExtensions.getService());
    }
}
