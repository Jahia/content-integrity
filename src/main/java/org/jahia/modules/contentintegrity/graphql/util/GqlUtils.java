package org.jahia.modules.contentintegrity.graphql.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * TODO: this is a fork from org.jahia.modules.graphql.provider.dxm.util.GqlUtils
 * because its package is not exported in Jahia 7
 */
public class GqlUtils {

    private static final Logger logger = LoggerFactory.getLogger(GqlUtils.class);

    /**
     * A supplier that always supplies for Boolean.FALSE.
     *
     * Should be used to supply for FALSE default value to GraphQL boolean parameters via @GraphQLDefaultValue
     */
    public static class SupplierFalse implements Supplier<Object> {

        @Override
        public Boolean get() {
            return Boolean.FALSE;
        }
    }

    /**
     * A supplier that always supplies for Boolean.TRUE.
     *
     * Should be used to supply for TRUE default value to GraphQL boolean parameters via @GraphQLDefaultValue
     */
    public static class SupplierTrue implements Supplier<Object> {

        @Override
        public Boolean get() {
            return Boolean.TRUE;
        }
    }

    private GqlUtils() {
    }
}
