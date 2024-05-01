package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.jahia.modules.contentintegrity.services.impl.ContentIntegrityCheckConfigurationImpl;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.sites.JahiaSitesService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.jahia.modules.contentintegrity.services.impl.Constants.CALCULATION_ERROR;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ENABLED + "=false"
})
public class StaticInternalLinksCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.IsConfigurable {

    private static final Logger logger = LoggerFactory.getLogger(StaticInternalLinksCheck.class);

    private static final int TEXT_EXTRACT_MAX_LENGTH = 200;
    public static final int TEXT_EXTRACT_READABILITY_ZONE_LENGTH = 20;
    public static final String IGNORE_LOCALHOST = "ignore-localhost";

    private final Set<String> domains = new HashSet<>();
    private final Map<String, Collection<String>> ignoredProperties = new HashMap<>();

    private final ContentIntegrityCheckConfiguration configurations;

    public StaticInternalLinksCheck() {
        configurations = new ContentIntegrityCheckConfigurationImpl();
        configurations.declareDefaultParameter(IGNORE_LOCALHOST, Boolean.TRUE, ContentIntegrityCheckConfigurationImpl.BOOLEAN_PARSER, "Ignore localhost when scanning the domains of the sites");
    }

    @Override
    protected void activateInternal(ComponentContext context) {
        ignoredProperties.put(Constants.JAHIANT_VIRTUALSITE, new HashSet<>());
        ignoredProperties.get(Constants.JAHIANT_VIRTUALSITE).add("j:serverName");
        ignoredProperties.get(Constants.JAHIANT_VIRTUALSITE).add("j:serverNameAliases");
    }

    @Override
    protected void initializeIntegrityTestInternal(JCRNodeWrapper scanRootNode, Collection<String> excludedPaths) {
        domains.clear();
        try {
            final JCRSessionWrapper systemSession = JCRUtils.getSystemSession(Constants.LIVE_WORKSPACE, false);
            JahiaSitesService.getInstance().getSitesNodeList(systemSession).stream()
                    .map(JCRSiteNode::getAllServerNames)
                    .flatMap(Collection::stream)
                    .filter(domain -> !(ignoreLocalhost() && "localhost".equalsIgnoreCase(domain)))
                    .forEach(domains::add);
        } catch (RepositoryException e) {
            logger.error("", e);
        }
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        final PropertyIterator properties;
        try {
            properties = node.getProperties();
        } catch (RepositoryException e) {
            logger.error("", e);
            return null;
        }
        final ContentIntegrityErrorList errors = createEmptyErrorsList();
        while (properties.hasNext()) {
            final Property property = properties.nextProperty();
            try {
                if (property.isMultiple()) {
                    Arrays.stream(property.getValues())
                            .forEach(value -> checkValue(value, errors, node, property));
                } else {
                    checkValue(property.getValue(), errors, node, property);
                }
            } catch (RepositoryException e) {
                logger.error("", e);
            }
        }
        return errors;
    }

    private void checkValue(Value value, ContentIntegrityErrorList errors, JCRNodeWrapper node, Property property) {
        if (value.getType() != PropertyType.STRING) return;
        final String propertyName;
        String tmpPropName = null;
        try {
            tmpPropName = property.getName();
            // For performance purpose, we just check the exact PT, without considering the inheritance or the mixins
            // TODO : for the moment, it would not work with ignored i18n properties
            final String pt = node.getPrimaryNodeTypeName();
            if (ignoredProperties.containsKey(pt) && ignoredProperties.get(pt).contains(tmpPropName))
                return;
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        propertyName = StringUtils.defaultString(tmpPropName, CALCULATION_ERROR);
        try {
            final String text = value.getString();
            domains.forEach(domain -> {
                if (StringUtils.contains(text, domain)) {
                    errors.addError(createPropertyRelatedError(node, "Hardcoded site domain in a String value")
                            .addExtraInfo("property-name", propertyName)
                            .addExtraInfo("property-value", getTextExtract(text, domain), true)
                            .addExtraInfo("domain", domain));
                }
            });
        } catch (RepositoryException e) {
            logger.error("", e);
        }
    }

    private String getTextExtract(String text, String domain) {
        if (text.length() <= TEXT_EXTRACT_MAX_LENGTH) return text;
        final int offset = StringUtils.indexOf(text, domain) - TEXT_EXTRACT_READABILITY_ZONE_LENGTH;
        return StringUtils.abbreviate(text, offset, TEXT_EXTRACT_MAX_LENGTH);
    }

    @Override
    public ContentIntegrityCheckConfiguration getConfigurations() {
        return configurations;
    }

    private boolean ignoreLocalhost() {
        return (boolean) getConfigurations().getParameter(IGNORE_LOCALHOST);
    }
}
