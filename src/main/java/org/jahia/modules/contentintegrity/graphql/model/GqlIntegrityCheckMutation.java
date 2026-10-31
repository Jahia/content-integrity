package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;

@GraphQLName("IntegrityCheckMutation")
public class GqlIntegrityCheckMutation {

    private final GqlIntegrityCheck check;

    public GqlIntegrityCheckMutation(GqlIntegrityCheck check) {
        this.check = check;
    }

    @GraphQLField
    @GraphQLName("configure")
    @GraphQLDescription("Sets a parameter of the configuration of the check. Returns false if the value is refused, or while a scan runs")
    public boolean setConfiguration(@GraphQLName("name") @GraphQLNonNull String name,
                                    @GraphQLName("value") @GraphQLNonNull String value) {
        return check.setConfiguration(name, value);
    }

    @GraphQLField
    @GraphQLDescription("Resets a parameter of the configuration of the check to its default value. Returns false while a scan runs")
    public boolean resetConfiguration(@GraphQLName("name") @GraphQLNonNull String name) {
        return check.resetConfiguration(name);
    }

    @GraphQLField
    @GraphQLDescription("Resets all the parameters of the configuration of the check to their default values. Returns false while a scan runs")
    public boolean resetAllConfigurations() {
        return check.resetAllConfigurations();
    }
}
