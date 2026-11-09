package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLField;

public class GqlScanResultsColumnValue implements Comparable<GqlScanResultsColumnValue> {

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
