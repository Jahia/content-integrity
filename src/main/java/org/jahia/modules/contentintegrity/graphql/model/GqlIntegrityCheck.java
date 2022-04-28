package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.apache.commons.collections4.CollectionUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.stream.Collectors;

@GraphQLName("IntegrityCheck")
@GraphQLDescription("Registered Integrity Check")
public class GqlIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(GqlIntegrityCheck.class);

    private final ContentIntegrityCheck integrityCheck;

    public GqlIntegrityCheck(ContentIntegrityCheck integrityCheck) {
        this.integrityCheck = integrityCheck;
    }

    @GraphQLField
    @GraphQLDescription("Identifier of the Integrity Check")
    public String getId() {
        return integrityCheck.getId();
    }

    @GraphQLField
    public boolean isEnabled() {
        return integrityCheck.isEnabled();
    }

    @GraphQLField
    public String getPriority() {
        return Float.toString(integrityCheck.getPriority());
    }

    @GraphQLField
    public boolean isConfigurable() {
        return integrityCheck instanceof ContentIntegrityCheck.IsConfigurable;
    }

    @GraphQLField
    public Collection<GqlIntegrityCheckConfiguration> getConfigurations() {
        if (!isConfigurable()) return CollectionUtils.emptyCollection();
        final ContentIntegrityCheck.IsConfigurable configurableCheck = (ContentIntegrityCheck.IsConfigurable) integrityCheck;
        final ContentIntegrityCheckConfiguration configurations = configurableCheck.getConfigurations();
        return configurations.getConfigurationNames().stream()
                .map(n -> new GqlIntegrityCheckConfiguration(n,
                        configurations.getDescription(n),
                        configurations.getParameterDefaultValue(n),
                        configurations.getParameter(n)))
                .collect(Collectors.toSet());
    }
}
