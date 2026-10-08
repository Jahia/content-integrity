package org.jahia.modules.contentintegrity.graphql;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.contentintegrity.graphql.model.GqlIntegrityScan;
import org.jahia.modules.contentintegrity.graphql.model.GqlIntegrityScanProgress;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.reactivestreams.Publisher;

@GraphQLTypeExtension(DXGraphQLProvider.Subscription.class)
public class SubscriptionExtensions {

    @GraphQLField
    @GraphQLName("contentIntegrityScan")
    @GraphQLDescription("Follows a scan: its status and its new log lines, until it is over. Requires the permission adminContentIntegrity")
    public static Publisher<GqlIntegrityScanProgress> getScan(@GraphQLName("id") @GraphQLNonNull String executionID) throws IllegalAccessException {
        // The same check as the queries: on the WebSocket transport, the provider sets the user of the connection
        QueryExtensions.getService();
        return GqlIntegrityScan.follow(executionID);
    }
}
