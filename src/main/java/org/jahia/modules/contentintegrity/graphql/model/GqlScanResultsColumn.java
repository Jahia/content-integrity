package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLField;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class GqlScanResultsColumn {

    private final String name;
    private final Set<GqlScanResultsColumnValue> values;

    public GqlScanResultsColumn(String name, Set<GqlScanResultsColumnValue> values) {
        this.name = name;
        this.values = new TreeSet<>(values);
    }

    public GqlScanResultsColumn(String name, Map<String, Long> values) {
        this.name = name;
        this.values = values.entrySet().stream()
                .map(v -> new GqlScanResultsColumnValue(v.getKey(), v.getValue()))
                .collect(Collectors.toCollection(TreeSet::new));
    }

    @GraphQLField
    public String getName() {
        return name;
    }

    @GraphQLField
    public Set<GqlScanResultsColumnValue> getValues() {
        return values;
    }
}
