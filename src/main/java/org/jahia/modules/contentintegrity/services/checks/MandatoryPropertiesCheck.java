package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.ContentIntegrityCheckConfigurationImpl;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRValueFactoryImpl;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.utils.LanguageCodeConverters;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.SKIP_ON_NT + "=" + Constants.JAHIANT_TRANSLATION
})
public class MandatoryPropertiesCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.IsConfigurable {

    private static final Logger logger = LoggerFactory.getLogger(MandatoryPropertiesCheck.class);

    private static final String CHECK_SITE_LANGS_ONLY_KEY = "site-langs-only";
    private static final boolean DEFAULT_CHECK_SITE_LANGS_ONLY_KEY = false;

    private final ContentIntegrityCheckConfiguration configurations;

    public MandatoryPropertiesCheck() {
        configurations = new ContentIntegrityCheckConfigurationImpl();
        getConfigurations().declareDefaultParameter(CHECK_SITE_LANGS_ONLY_KEY, DEFAULT_CHECK_SITE_LANGS_ONLY_KEY, "If true, only the translation subnodes related to an active language are checked when the node is in a site");
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {

        final ExtendedNodeType primaryNodeType;
        final ExtendedNodeType[] mixinNodeTypes;
        try {
            primaryNodeType = node.getPrimaryNodeType();
            mixinNodeTypes = node.getMixinNodeTypes();
        } catch (RepositoryException e) {
            logger.error("Impossible to load the type of the node", e);
            return null;
        }

        final ContentIntegrityErrorList errors = createEmptyErrorsList();
        final Map<String, String> checkedProperties = new HashMap<>();
        String identifier = null;
        try {
            identifier = node.getIdentifier();
            checkNodeForType(node, primaryNodeType, checkedProperties, errors);
            for (ExtendedNodeType mixinNodeType : mixinNodeTypes) {
                checkNodeForType(node, mixinNodeType, checkedProperties, errors);
            }
        } catch (RepositoryException e) {
            logger.error("Error while checking the property constaints on the node " + identifier, e);
        }

        return errors;
    }

    private void checkNodeForType(JCRNodeWrapper node, ExtendedNodeType nodeType, Map<String, String> checkedProperties, ContentIntegrityErrorList errors) throws RepositoryException {
        final String nodeTypeName = nodeType.getName();
        final ExtendedPropertyDefinition[] extendedPropertyDefinitions = nodeType.getPropertyDefinitions();
        for (ExtendedPropertyDefinition propertyDefinition : extendedPropertyDefinitions) {
            final String propertyName = propertyDefinition.getName();
            if (checkedProperties.containsKey(propertyName)) {
                if (logger.isDebugEnabled()) {
                    logger.debug(String.format("Already encountered the property %s when checking the type %s on the node %s",
                            propertyName, checkedProperties.get(propertyName), node.getIdentifier()));
                }
                continue;
            }
            checkedProperties.put(propertyName, nodeTypeName);

            final boolean isMandatory = propertyDefinition.isMandatory();
            final boolean hasConstraints = propertyDefinition.getValueConstraints() != null && propertyDefinition.getValueConstraints().length > 0;
            if (!isMandatory && !hasConstraints) return;

            if (propertyDefinition.isInternationalized()) {
                if (checkSiteLangsOnly() && StringUtils.startsWith(node.getPath(), "/sites/")) {
                    final JCRSiteNode site = node.getResolveSite();
                    final List<Locale> locales = node.getSession().getWorkspace().getName().equals(Constants.EDIT_WORKSPACE) ?
                            site.getLanguagesAsLocales() : site.getActiveLiveLanguagesAsLocales();
                    for (Locale locale : locales) {
                        try {
                            final Node translationNode = node.getI18N(locale, false);
                            checkProperty(node, translationNode, propertyName, propertyDefinition, locale, nodeType, errors);
                        } catch (ItemNotFoundException ignored) {}
                    }
                } else {
                    final NodeIterator translationNodesIterator = node.getI18Ns();
                    while (translationNodesIterator.hasNext()) {
                        final Node translationNode = translationNodesIterator.nextNode();
                        final String locale = getTranslationNodeLocale(translationNode);
                        if (StringUtils.isBlank(locale)) {
                            logger.error(String.format("Skipping a translation node since its language is invalid: %s", translationNode.getIdentifier()));
                            continue;
                        }
                        checkProperty(node, translationNode, propertyName, propertyDefinition, locale, nodeType, errors);
                    }
                }
            } else {
                checkProperty(node, node.getRealNode(), propertyName, propertyDefinition, (String) null, nodeType, errors);
            }
        }
    }

    private void checkProperty(JCRNodeWrapper node, Node propertyNode, String pName, ExtendedPropertyDefinition propertyDefinition, Locale locale, ExtendedNodeType nodeType, ContentIntegrityErrorList errors) throws RepositoryException {
        final String localeString = locale == null ? null : LanguageCodeConverters.localeToLanguageTag(locale);
        checkProperty(node, propertyNode, pName, propertyDefinition, localeString, nodeType, errors);
    }

    private void checkProperty(JCRNodeWrapper node, Node propertyNode, String pName, ExtendedPropertyDefinition propertyDefinition, String locale, ExtendedNodeType nodeType, ContentIntegrityErrorList errors) throws RepositoryException {
        final boolean isMandatory = propertyDefinition.isMandatory();
        final boolean hasConstraints = propertyDefinition.getValueConstraints() != null && propertyDefinition.getValueConstraints().length > 0;

        if (!propertyNode.hasProperty(pName)) {
            if (isMandatory) {
                errors.addError(createError(node, locale, "Missing mandatory property")
                        .addExtraInfo("error-type", ErrorType.EMPTY_MANDATORY_PROPERTY)
                        .addExtraInfo("property-name", pName)
                        .addExtraInfo("declaring-type", nodeType.getName()));
            }
            return;
        }

        final Property property = propertyNode.getProperty(pName);
        final int propertyType = propertyDefinition.getRequiredType();
        if (propertyDefinition.isMultiple()) {
            boolean isEmpty = true;
            int idx = 0;
            for (Value value : property.getValues()) {
                boolean isCurrentEmpty = true;
                switch (propertyType) {
                    case PropertyType.BINARY:
                        try {
                            value.getBinary();
                            isCurrentEmpty = false;
                        } catch (RepositoryException ignored) {}
                        break;
                    default:
                        isCurrentEmpty = value.getString().length() <= 0;
                }
                isEmpty &= isCurrentEmpty;
                if (isCurrentEmpty && hasConstraints) {
                    final Value[] values = new Value[1];
                    values[0] = value;
                    if (!nodeType.canSetProperty(pName, values)) {
                        final String valueStr = propertyType == PropertyType.BINARY ? "<binary>" : value.getString();
                        errors.addError(createError(node, locale, "The value is not allowed by the constraint")
                                .addExtraInfo("error-type", ErrorType.CONSTRAINT_NOT_VALIDATED)
                                .addExtraInfo("property-name", pName)
                                .addExtraInfo("declaring-type", nodeType.getName())
                                .addExtraInfo("invalid-value", valueStr)
                                .addExtraInfo("value-index", idx));
                    }
                }
                idx++;
            }
            if (isEmpty && isMandatory) {
                errors.addError(createError(node, locale, "Empty mandatory property")
                        .addExtraInfo("error-type", ErrorType.EMPTY_MANDATORY_PROPERTY)
                        .addExtraInfo("property-name", pName)
                        .addExtraInfo("declaring-type", nodeType.getName()));
            }
        } else {
            boolean isEmpty = false;
            switch (propertyType) {
                case PropertyType.BINARY:
                    try {
                        property.getBinary();
                    } catch (RepositoryException re) {
                        isEmpty = true;
                    }
                    break;
                default:
                    isEmpty = property.getLength() <= 0;
            }
            if (isEmpty && isMandatory) {
                errors.addError(createError(node, locale, "Empty mandatory property")
                        .addExtraInfo("error-type", ErrorType.EMPTY_MANDATORY_PROPERTY)
                        .addExtraInfo("property-name", pName)
                        .addExtraInfo("declaring-type", nodeType.getName()));
            } else if (!isEmpty && hasConstraints) {
                final String valueStr = propertyType == PropertyType.BINARY ? "<binary>" : property.getString();
                if (!nodeType.canSetProperty(pName, property.getValue())) {
                    errors.addError(createError(node, locale, "The value is not allowed by the constraint")
                            .addExtraInfo("error-type", ErrorType.CONSTRAINT_NOT_VALIDATED)
                            .addExtraInfo("property-name", pName)
                            .addExtraInfo("declaring-type", nodeType.getName())
                            .addExtraInfo("invalid-value", valueStr));
                }
            }
        }
    }

    private boolean checkSiteLangsOnly() {
        final Object o = getConfigurations().getParameter(CHECK_SITE_LANGS_ONLY_KEY);
        if (o instanceof Boolean) return (boolean) o;
        return DEFAULT_CHECK_SITE_LANGS_ONLY_KEY;
    }

    @Override
    public ContentIntegrityCheckConfiguration getConfigurations() {
        return configurations;
    }

    private enum ErrorType {
        EMPTY_MANDATORY_PROPERTY, CONSTRAINT_NOT_VALIDATED
    }
}
