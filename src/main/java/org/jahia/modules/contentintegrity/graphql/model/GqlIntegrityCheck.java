package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.apache.commons.collections4.CollectionUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.jahia.modules.contentintegrity.services.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.stream.Collectors;

@GraphQLName("IntegrityCheck")
@GraphQLDescription("Registered Integrity Check")
public class GqlIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(GqlIntegrityCheck.class);

    private final ContentIntegrityCheck integrityCheck;
    private final ContentIntegrityCheckConfiguration configurations;

    public GqlIntegrityCheck(ContentIntegrityCheck integrityCheck) {
        this.integrityCheck = integrityCheck;
        if (isConfigurable()) {
            final ContentIntegrityCheck.IsConfigurable configurableCheck = (ContentIntegrityCheck.IsConfigurable) integrityCheck;
            configurations = configurableCheck.getConfigurations();
        } else {
            configurations = null;
        }
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
        return configurations.getConfigurationNames().stream()
                .map(this::getConfiguration)
                .collect(Collectors.toSet());
    }

    @GraphQLField
    public GqlIntegrityCheckConfiguration getConfiguration(@GraphQLName("name") @GraphQLNonNull String name) {
        return new GqlIntegrityCheckConfiguration(name,
                configurations.getDescription(name),
                configurations.getParameterDefaultValue(name),
                configurations.getParameter(name)
                );
    }

    @GraphQLField
    @GraphQLName("configure")
    public boolean setConfiguration(@GraphQLName("name") @GraphQLNonNull String name,
                                 @GraphQLName("value") @GraphQLNonNull String value) {
        if (Utils.getContentIntegrityService().isScanRunning()) return false;

        try {
            configurations.setParameter(name, value);
            return true;
        } catch (IllegalArgumentException iae) {
            logger.error("", iae);
            return false;
        }
    }

    @GraphQLField
    @GraphQLName("resetConfiguration")
    public boolean resetConfiguration(@GraphQLName("name") @GraphQLNonNull String name) {
        if (Utils.getContentIntegrityService().isScanRunning()) return false;

        try {
            configurations.setParameter(name, null);
            return true;
        } catch (IllegalArgumentException iae) {
            logger.error("", iae);
            return false;
        }
    }

    @GraphQLField
    public boolean resetAllConfigurations() {
        if (Utils.getContentIntegrityService().isScanRunning()) return false;

        configurations.getConfigurationNames().forEach(name -> configurations.setParameter(name, null));
        return true;
    }
}
