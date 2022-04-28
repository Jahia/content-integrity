package org.jahia.modules.contentintegrity.graphql;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.contentintegrity.graphql.model.GqlIntegrityService;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GraphQLTypeExtension(DXGraphQLProvider.Query.class)
public class QueryExtensions {

    private static final Logger logger = LoggerFactory.getLogger(QueryExtensions.class);

    @GraphQLField
    @GraphQLName("integrity")
    public static GqlIntegrityService getService() {
        return new GqlIntegrityService();
    }
}
