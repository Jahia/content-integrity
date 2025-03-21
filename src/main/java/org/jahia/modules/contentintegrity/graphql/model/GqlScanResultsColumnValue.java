package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLField;
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
    public int compareTo(GqlScanResultsColumnValue o) {
        if (count <= 0 && o.count > 0) return 1;
        if (o.count <= 0 && count > 0) return -1;
        return getName().compareTo(o.getName());
    }
}
