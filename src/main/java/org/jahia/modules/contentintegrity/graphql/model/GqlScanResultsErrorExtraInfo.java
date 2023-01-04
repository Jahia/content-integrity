package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GqlScanResultsErrorExtraInfo {

    private static final Logger logger = LoggerFactory.getLogger(GqlScanResultsErrorExtraInfo.class);

    private final String key;
    private final String label;
    private final Object value;

    public GqlScanResultsErrorExtraInfo(String key, String label, Object value) {
        this.key = key;
        this.label = label;
        this.value = value;
    }

    @GraphQLField
    public String getKey() {
        return key;
    }

    @GraphQLField
    public String getLabel() {
        return label;
    }

    @GraphQLField
    public String getValue() {
        return String.valueOf(value);
    }
}
