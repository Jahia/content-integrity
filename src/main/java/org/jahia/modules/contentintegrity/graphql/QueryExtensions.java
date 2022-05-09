package org.jahia.modules.contentintegrity.graphql;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLTypeExtension;
import org.jahia.modules.contentintegrity.graphql.model.GqlIntegrityService;
import org.jahia.modules.graphql.provider.dxm.DXGraphQLProvider;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

@GraphQLTypeExtension(DXGraphQLProvider.Query.class)
public class QueryExtensions {

    private static final Logger logger = LoggerFactory.getLogger(QueryExtensions.class);

    @GraphQLField
    @GraphQLName("contentIntegrity")
    public static GqlIntegrityService getService() throws IllegalAccessException {
        try {
            if (JCRSessionFactory.getInstance().getCurrentUserSession().getNode("/").hasPermission("adminContentIntegrity"))
                return BundleUtils.getOsgiService(GqlIntegrityService.class, null);
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        throw new IllegalAccessException("The current user is not allowed to access the API");
    }
}
