package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@GraphQLName("IntegrityCheckConfig")
@GraphQLDescription("Configuration related to an Integrity Check")
public class GqlIntegrityCheckConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(GqlIntegrityCheckConfiguration.class);

    private final String name;
    private final String description;
    private final Object defaultValue;
    private final Object value;
    private final String type;

    public GqlIntegrityCheckConfiguration(String name, String description, Object defaultValue, Object value) {
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.value = value;
        this.type = defaultValue.getClass().getSimpleName().toLowerCase();
    }

    @GraphQLField
    public String getName() {
        return name;
    }

    @GraphQLField
    public String getDescription() {
        return description;
    }

    @GraphQLField
    public String getDefaultValue() {
        return String.valueOf(defaultValue);
    }

    @GraphQLField
    public String getValue() {
        return String.valueOf(value);
    }

    @GraphQLField
    public String getType() {
        return type;
    }
}
