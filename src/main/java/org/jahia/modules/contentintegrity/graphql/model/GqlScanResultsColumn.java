package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.TreeSet;

public class GqlScanResultsColumn {

    private static final Logger logger = LoggerFactory.getLogger(GqlScanResultsColumn.class);

    private final String name;
    private final Set<String> values;

    public GqlScanResultsColumn(String name, Set<String> values) {
        this.name = name;
        this.values = new TreeSet<>(values);
    }

    @GraphQLField
    public String getName() {
        return name;
    }

    @GraphQLField
    public Set<String> getValues() {
        return values;
    }
}
