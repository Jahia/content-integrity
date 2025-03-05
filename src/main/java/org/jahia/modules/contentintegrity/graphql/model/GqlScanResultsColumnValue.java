package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLField;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GqlScanResultsColumnValue implements Comparable<GqlScanResultsColumnValue> {

    private static final Logger logger = LoggerFactory.getLogger(GqlScanResultsColumnValue.class);

    private final String name;
    private final long count;

    public GqlScanResultsColumnValue(String name, long count) {
        this.name = name;
        this.count = count;
    }

    @GraphQLField
    public String getName() {
        return name;
    }

    @GraphQLField
    public long getCount() {
        return count;
    }

    @Override
    public int compareTo(@NotNull GqlScanResultsColumnValue o) {
        return getName().compareTo(o.getName());
    }
}
