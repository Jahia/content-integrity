package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.core.value.InternalValue;
import org.apache.jackrabbit.spi.commons.nodetype.constraint.ValueConstraint;
import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.jahia.modules.contentintegrity.services.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.ContentIntegrityCheckConfigurationImpl;
import org.jahia.services.content.JCRNodeWrapper;
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
import javax.jcr.ValueFormatException;
import javax.jcr.nodetype.ConstraintViolationException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.SKIP_ON_NT + "=" + Constants.JAHIANT_TRANSLATION
})
public class PropertyConstraintsSanityCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.IsConfigurable {

    private static final Logger logger = LoggerFactory.getLogger(PropertyConstraintsSanityCheck.class);

    private static final String CHECK_SITE_LANGS_ONLY_KEY = "site-langs-only";
    private static final boolean DEFAULT_CHECK_SITE_LANGS_ONLY_KEY = false;
    private static final String CHECK_PROPERTIES_WITHOUT_CONSTRAINT = "check-properties-without-constraint";
    private static final boolean DEFAULT_CHECK_PROPERTIES_WITHOUT_CONSTRAINT = false;

    private final ContentIntegrityCheckConfiguration configurations;

    public PropertyConstraintsSanityCheck() {
        configurations = new ContentIntegrityCheckConfigurationImpl();
        getConfigurations().declareDefaultParameter(CHECK_SITE_LANGS_ONLY_KEY, DEFAULT_CHECK_SITE_LANGS_ONLY_KEY, "If true, only the translation subnodes related to an active language are checked when the node is in a site");
        getConfigurations().declareDefaultParameter(CHECK_PROPERTIES_WITHOUT_CONSTRAINT, DEFAULT_CHECK_PROPERTIES_WITHOUT_CONSTRAINT, "If true, every property value will be check to validate if it is compliant with the current definition, if false only the properties with a declared constraint are checked");
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
        final boolean checkPropertiesWithoutConstraint = checkPropertiesWithoutConstraint();
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
            if (!checkPropertiesWithoutConstraint && !isMandatory && !hasConstraints) continue;

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

        if (!propertyNode.hasProperty(pName)) {
            if (isMandatory) {
                trackEmptyValue(pName, propertyDefinition, node, nodeType, locale, errors );
            }
            return;
        }

        final Property property = propertyNode.getProperty(pName);
        boolean isEmpty = true;
        if (propertyDefinition.isMultiple()) {
            int idx = 0;
            for (Value value : property.getValues()) {
                isEmpty &= checkValue(value, idx, pName, propertyDefinition, node, nodeType, locale, errors);
                idx++;
            }
        } else {
            isEmpty = checkValue(property.getValue(), -1, pName, propertyDefinition, node, nodeType, locale, errors);
        }

        if (isEmpty && isMandatory) {
            trackEmptyValue(pName, propertyDefinition, node, nodeType, locale, errors );
        }
    }

    /**
     *
     * @param value
     * @param valueIdx ignored if the property is single-valued
     * @param pName
     * @param propertyDefinition
     * @param node
     * @param nodeType
     * @param locale
     * @param errors
     *
     * @return true if the value is empty
     * @throws RepositoryException
     */
    private boolean checkValue(Value value, int valueIdx,
                               String pName, ExtendedPropertyDefinition propertyDefinition,
                               JCRNodeWrapper node, ExtendedNodeType nodeType, String locale,
                               ContentIntegrityErrorList errors) throws RepositoryException {
        final int propertyType = propertyDefinition.getRequiredType();
        final boolean hasConstraints = propertyDefinition.getValueConstraints() != null && propertyDefinition.getValueConstraints().length > 0;
        final boolean checkPropertiesWithoutConstraint = checkPropertiesWithoutConstraint();

        switch (propertyType) {
            case PropertyType.BINARY:
                try {
                    value.getBinary();
                } catch (RepositoryException re) {
                    return true;
                }
                break;
            default:
                if (value.getString().length() <= 0) return true;
        }

        if (hasConstraints || checkPropertiesWithoutConstraint) {
            final String valueStr = value.getType() == PropertyType.BINARY ? "<binary>" : value.getString();
            if (propertyType != PropertyType.UNDEFINED && value.getType() != propertyType) {
                trackInvalidValueType(pName, propertyDefinition, value.getType(), node, nodeType, locale, errors);
            } else if (!constraintIsValid(value, propertyDefinition)) {
                trackInvalidValueConstaint(pName, propertyDefinition,
                        valueStr, valueIdx, node, nodeType, locale, errors);
            }
        }

        return false;
    }

    private boolean constraintIsValid(Value value, ExtendedPropertyDefinition propertyDefinition) {
        final ValueConstraint[] constraints = propertyDefinition.getValueConstraintObjects();
        if (constraints == null || constraints.length == 0) {
            // no constraints to check
            return true;
        }

        for (ValueConstraint constraint : constraints) {
            try {
                constraint.check(InternalValue.create(value, null, null));
                break;
            } catch (ConstraintViolationException e) {
               return false;
            } catch (RepositoryException e) {
                logger.error("", e);
            }
        }

        return true;
    }

    private void trackEmptyValue(String propertyName, ExtendedPropertyDefinition propertyDefinition,
                                 JCRNodeWrapper node, ExtendedNodeType nodeType, String locale,
                                 ContentIntegrityErrorList errors) {
        trackError(ErrorType.EMPTY_MANDATORY_PROPERTY, propertyName, propertyDefinition, null, -1, -1, node, nodeType, locale, errors);
    }

    private void trackInvalidValueConstaint(String propertyName, ExtendedPropertyDefinition propertyDefinition,
                                            String value, int valueIdx,
                                            JCRNodeWrapper node, ExtendedNodeType nodeType, String locale,
                                            ContentIntegrityErrorList errors) {
        trackError(ErrorType.INVALID_VALUE_CONSTRAINT, propertyName, propertyDefinition, value, valueIdx, -1, node, nodeType, locale, errors);
    }

    private void trackInvalidValueType(String propertyName, ExtendedPropertyDefinition propertyDefinition,
                                       int valueType,
                                       JCRNodeWrapper node, ExtendedNodeType nodeType, String locale,
                                       ContentIntegrityErrorList errors) {
        trackError(ErrorType.INVALID_VALUE_TYPE, propertyName, propertyDefinition, null, -1, valueType, node, nodeType, locale, errors);
    }

    private void trackError(ErrorType errorType,
                                   String propertyName, ExtendedPropertyDefinition propertyDefinition,
                                   String value, int valueIdx, int valueType,
                                   JCRNodeWrapper node, ExtendedNodeType nodeType, String locale,
                                   ContentIntegrityErrorList errors) {
        final ContentIntegrityError error = createError(node, locale, errorType.desc)
                .addExtraInfo("error-type", errorType)
                .addExtraInfo("property-name", propertyName)
                .addExtraInfo("declaring-type", nodeType.getName());
        if (value != null) {
            error.addExtraInfo("invalid-value", value);
            if (propertyDefinition.isMultiple()) error.addExtraInfo("value-index", valueIdx);
        }
        if (valueType >= 0) {
            error.addExtraInfo("value-type", PropertyType.nameFromValue(valueType));
            error.addExtraInfo("expected-value-type", PropertyType.nameFromValue(propertyDefinition.getRequiredType()));
        }
        errors.addError(error);
    }

    private boolean checkSiteLangsOnly() {
        final Object o = getConfigurations().getParameter(CHECK_SITE_LANGS_ONLY_KEY);
        if (o instanceof Boolean) return (boolean) o;
        if (o instanceof String) return Boolean.parseBoolean((String) o);
        return DEFAULT_CHECK_SITE_LANGS_ONLY_KEY;
    }

    private boolean checkPropertiesWithoutConstraint() {
        final Object o = getConfigurations().getParameter(CHECK_PROPERTIES_WITHOUT_CONSTRAINT);
        if (o instanceof Boolean) return (boolean) o;
        if (o instanceof String) return Boolean.parseBoolean((String) o);
        return DEFAULT_CHECK_PROPERTIES_WITHOUT_CONSTRAINT;
    }

    @Override
    public ContentIntegrityCheckConfiguration getConfigurations() {
        return configurations;
    }

    private enum ErrorType {
        EMPTY_MANDATORY_PROPERTY("Missing mandatory property"),
        INVALID_VALUE_TYPE("The value does not match the type declared in the property definition"),
        INVALID_VALUE_CONSTRAINT("The value does not match the constraint declared in the property definition");

        private final String desc;

        ErrorType(String desc) {
            this.desc = desc;
        }
    }
}
