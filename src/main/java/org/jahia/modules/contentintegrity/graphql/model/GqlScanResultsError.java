package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLField;
import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class GqlScanResultsError {

    private static final Logger logger = LoggerFactory.getLogger(GqlScanResultsError.class);

    private final ContentIntegrityError error;

    public GqlScanResultsError(ContentIntegrityError error) {
        this.error = error;
    }

    @GraphQLField
    public String getCheckName() {
        return error.getIntegrityCheckName();
    }

    @GraphQLField
    public boolean isFixed() {
        return error.isFixed();
    }

    @GraphQLField
    public String getErrorType() {
        return String.valueOf(Optional.ofNullable(error.getErrorType()).orElse(StringUtils.EMPTY));
    }

    @GraphQLField
    public String getWorkspace() {
        return error.getWorkspace();
    }

    @GraphQLField
    public String getNodeId() {
        return error.getUuid();
    }

    @GraphQLField
    public String getNodePath() {
        return error.getPath();
    }

    @GraphQLField
    public String getSite() {
        return error.getSite();
    }

    @GraphQLField
    public String getNodePrimaryType() {
        return error.getPrimaryType();
    }

    @GraphQLField
    public String getNodeMixins() {
        return error.getMixins();
    }

    @GraphQLField
    public String getLocale() {
        return error.getLocale();
    }

    @GraphQLField
    public String getMessage() {
        return error.getConstraintMessage();
    }
}
