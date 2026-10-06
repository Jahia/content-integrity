package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import org.jahia.modules.contentintegrity.api.FixValuesDefinition;

import java.util.List;

@GraphQLDescription("The values to provide to fix an error")
public class GqlFixValuesDefinition {

    private final FixValuesDefinition definition;

    public GqlFixValuesDefinition(FixValuesDefinition definition) {
        this.definition = definition;
    }

    @GraphQLField
    @GraphQLDescription("The name of the value, for example a property name")
    public String getName() {
        return definition.getName();
    }

    @GraphQLField
    @GraphQLDescription("The JCR type of the values, for example String, Long, Boolean or Date")
    public String getType() {
        return definition.getType();
    }

    @GraphQLField
    @GraphQLDescription("True if several values can be provided")
    public boolean isMultiple() {
        return definition.isMultiple();
    }

    @GraphQLField
    @GraphQLDescription("The only accepted values, empty if any value matching the constraints is accepted")
    public List<String> getChoices() {
        return definition.getChoices();
    }

    @GraphQLField
    @GraphQLDescription("The constraints the values have to match")
    public List<String> getConstraints() {
        return definition.getConstraints();
    }

    @GraphQLField
    @GraphQLDescription("The values suggested by default")
    public List<String> getDefaultValues() {
        return definition.getDefaultValues();
    }
}
