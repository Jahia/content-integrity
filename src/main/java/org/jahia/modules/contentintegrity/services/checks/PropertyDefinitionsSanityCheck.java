package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.core.value.InternalValue;
import org.apache.jackrabbit.spi.commons.nodetype.constraint.ValueConstraint;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.Utils;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.jahia.modules.contentintegrity.services.impl.ContentIntegrityCheckConfigurationImpl;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.modules.external.ExternalNodeImpl;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.decorator.validation.AdvancedGroup;
import org.jahia.services.content.decorator.validation.AdvancedSkipOnImportGroup;
import org.jahia.services.content.decorator.validation.DefaultSkipOnImportGroup;
import org.jahia.services.content.decorator.validation.JCRNodeValidator;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.jahia.utils.LanguageCodeConverters;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import javax.jcr.nodetype.PropertyDefinition;
import javax.validation.ConstraintViolation;
import javax.validation.groups.Default;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.jahia.modules.contentintegrity.services.impl.Constants.JAHIANT_TRANSLATION;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.SKIP_ON_NT + "=" + JAHIANT_TRANSLATION
})
public class PropertyDefinitionsSanityCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.IsConfigurable {

    private static final Logger logger = LoggerFactory.getLogger(PropertyDefinitionsSanityCheck.class);

    private static final String CHECK_SITE_LANGS_ONLY_KEY = "site-langs-only";
    private static final String CHECK_NODE_VALIDATORS_KEY = "check-node-validators";
    private static final String BINARY_VALUE_STR = "<binary>";
    private static final String FAILED_TO_CALCULATE_VALUE_STR = "<calculation error>";
    private static final String NON_I18N_PROP_VALIDATOR_ERROR_XTRA_MSG = "Non internationalized properties are tested for each available language. In case of constraint violation, the related errors might be duplicated if the validation does not involve internationalized properties";

    private final ContentIntegrityCheckConfiguration configurations;

    private ExtendedNodeType jntTranslationNt;
    private final Map<String, Boolean> jntTranslationNtParents = new HashMap<>();
    private Map<String, Constructor<?>> validators;
    private LocalValidatorFactoryBean validatorFactoryBean;

    private enum ErrorType {
        EMPTY_MANDATORY_PROPERTY("Missing mandatory property"),
        INVALID_VALUE_TYPE("The value does not match the type declared in the property definition"),
        INVALID_MULTI_VALUE_STATUS("The single/multi value status differs between the value and the definition"),
        INVALID_VALUE_CONSTRAINT("The value does not match the constraint declared in the property definition"),
        INVALID_NODE_VALIDATION("A node constraint is not validated"),
        UNDECLARED_PROPERTY("Undeclared property");

        private final String desc;

        ErrorType(String desc) {
            this.desc = desc;
        }
    }

    public PropertyDefinitionsSanityCheck() {
        configurations = new ContentIntegrityCheckConfigurationImpl();
        getConfigurations().declareDefaultParameter(CHECK_SITE_LANGS_ONLY_KEY, Boolean.FALSE, ContentIntegrityCheckConfigurationImpl.BOOLEAN_PARSER, "If true, only the translation sub-nodes related to an active language are checked when the node is in a site");
        getConfigurations().declareDefaultParameter(CHECK_NODE_VALIDATORS_KEY, Boolean.TRUE, ContentIntegrityCheckConfigurationImpl.BOOLEAN_PARSER, "If false, the node validators are not checked (property constraints are checked no matter this configuration)");
    }

    @Override
    public ContentIntegrityCheckConfiguration getConfigurations() {
        return configurations;
    }

    private boolean checkSiteLangsOnly() {
        return (boolean) getConfigurations().getParameter(CHECK_SITE_LANGS_ONLY_KEY);
    }

    private boolean checkNodeValidators() {
        return (boolean) getConfigurations().getParameter(CHECK_NODE_VALIDATORS_KEY);
    }

    @Override
    protected void initializeIntegrityTestInternal(JCRNodeWrapper node, Collection<String> excludedPaths) {
        try {
            jntTranslationNt = NodeTypeRegistry.getInstance().getNodeType(JAHIANT_TRANSLATION);
        } catch (NoSuchNodeTypeException e) {
            logger.error(String.format("Impossible to load the definition of %s", JAHIANT_TRANSLATION), e);
            setScanDurationDisabled(true);
        }
        jntTranslationNtParents.clear();

        validators = JCRSessionFactory.getInstance().getDefaultProvider().getValidators();
        validatorFactoryBean = JCRSessionFactory.getInstance().getValidatorFactoryBean();
    }

    @Override
    protected ContentIntegrityErrorList finalizeIntegrityTestInternal(JCRNodeWrapper scanRootNode, Collection<String> excludedPaths) {
        jntTranslationNt = null;
        jntTranslationNtParents.clear();
        validators = null;
        validatorFactoryBean = null;

        return null;
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        final ContentIntegrityErrorList errors = createEmptyErrorsList();

        checkMandatoryProperties(node, errors);
        checkExistingProperties(node, errors);
        checkNodeValidators(node, errors);

        return errors;
    }

    private void checkNodeValidators(JCRNodeWrapper node, ContentIntegrityErrorList errors) {
        if (!checkNodeValidators()) return;
        if (MapUtils.isEmpty(validators)) return;
        final AtomicBoolean nodeHasBeenChecked = new AtomicBoolean(false);
        try {
            doOnTranslationNodes(node, new TranslationNodeProcessor() {
                @Override
                public void execute(Node translationNode, String locale) throws RepositoryException {
                    checkNodeValidators(node, locale, errors);
                    nodeHasBeenChecked.set(true);
                }
            });
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        if (!nodeHasBeenChecked.get()) JCRUtils.runJcrSupplierCallBack(() -> checkNodeValidators(node, null, errors));
    }

    private Void checkNodeValidators(JCRNodeWrapper node, String locale, ContentIntegrityErrorList errors) throws RepositoryException {
        final JCRNodeWrapper checkedNode = locale == null ? node :
                JCRUtils.runJcrSupplierCallBack(() -> JCRUtils.getSystemSession(node.getSession(), locale).getNode(node.getPath()), null, false);
        if (checkedNode == null) return null;
        
        validators.entrySet().stream()
                .filter(e -> JCRUtils.runJcrCallBack(e.getKey(), checkedNode::isNodeType, Boolean.FALSE))
                .map(Map.Entry::getValue)
                .map(c -> JCRUtils.runJcrSupplierCallBack(() -> this.createValidatorInstance(c, checkedNode)))
                .filter(validator -> validator instanceof JCRNodeValidator)
                .map(validator -> {
                    final Set<ConstraintViolation<JCRNodeValidator>> constraintViolations = validatorFactoryBean.validate((JCRNodeValidator) validator, Default.class, DefaultSkipOnImportGroup.class);
                    if (!constraintViolations.isEmpty()) return constraintViolations;
                    return validatorFactoryBean.validate((JCRNodeValidator) validator, AdvancedGroup.class, AdvancedSkipOnImportGroup.class);
                })
                .filter(CollectionUtils::isNotEmpty)
                .flatMap(Collection::stream)
                .forEach(constraintViolation -> trackNodeConstraintViolation(constraintViolation, checkedNode, locale, errors));

        return null;
    }

    private Object createValidatorInstance(Constructor<?> constructor, JCRNodeWrapper node) {
        try {
            return constructor.newInstance(node);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            logger.error("", e);
            return null;
        }
    }

    private void checkMandatoryProperties(JCRNodeWrapper node, ContentIntegrityErrorList errors) {
        final Map<String, String> checkedProperties = new HashMap<>();
        doOnSupertypes(node, new SupertypeProcessor() {
            @Override
            public void execute(JCRNodeWrapper node, ExtendedNodeType extendedNodeType) throws RepositoryException {
                checkMandatoryPropertiesForType(node, extendedNodeType, checkedProperties, errors);
            }
        });
    }

    private void checkMandatoryPropertiesForType(JCRNodeWrapper node, ExtendedNodeType nodeType, Map<String, String> checkedProperties, ContentIntegrityErrorList errors) throws RepositoryException {
        final String nodeTypeName = nodeType.getName();
        final ExtendedPropertyDefinition[] extendedPropertyDefinitions = nodeType.getPropertyDefinitions();
        for (ExtendedPropertyDefinition propertyDefinition : extendedPropertyDefinitions) {
            final String propertyDefinitionName = propertyDefinition.getName();
            if (StringUtils.equals(propertyDefinitionName, Constants.PROPERTY_DEFINITION_NAME_WILDCARD)) continue;
            if (checkedProperties.containsKey(propertyDefinitionName)) {
                if (logger.isDebugEnabled()) {
                    logger.debug(String.format("Already encountered the property %s when checking the type %s on the node %s",
                            propertyDefinitionName, checkedProperties.get(propertyDefinitionName), node.getIdentifier()));
                }
                continue;
            }
            checkedProperties.put(propertyDefinitionName, nodeTypeName);
            if (!propertyDefinition.isMandatory()) continue;

            if (propertyDefinition.isInternationalized()) {
                doOnTranslationNodes(node, new TranslationNodeProcessor() {
                    @Override
                    public void execute(Node translationNode, String locale) throws RepositoryException {
                        checkMandatoryProperty(node, translationNode, propertyDefinitionName, propertyDefinition, locale, errors);
                    }
                });
            } else {
                final Node realNode = getRealNode(node);
                if (realNode != null)
                    checkMandatoryProperty(node, realNode, propertyDefinitionName, propertyDefinition, null, errors);
            }
        }
    }

    private void checkMandatoryProperty(JCRNodeWrapper node, Node propertyNode, String pName,
                                        ExtendedPropertyDefinition propertyDefinition, String locale,
                                        ContentIntegrityErrorList errors) throws RepositoryException {
        if (StringUtils.equals(pName, Constants.PROPERTY_DEFINITION_NAME_WILDCARD)) return;
        if (!propertyDefinition.isMandatory()) return;
        if (node.getRealNode() instanceof ExternalNodeImpl) {
            final String declaringType = propertyDefinition.getDeclaringNodeType().getName();
            if (StringUtils.equals(declaringType, Constants.MIX_VERSIONABLE)) {
                return;
            }
            if (StringUtils.equals(declaringType, Constants.MIX_SIMPLEVERSIONABLE)) {
                return;
            }
        }

        if (!propertyNode.hasProperty(pName)) {
            trackMissingMandatoryValue(pName, propertyDefinition, node, locale, errors );
            return;
        }

        final Property property = propertyNode.getProperty(pName);
        if (isPropertyEmpty(property)) {
            trackMissingMandatoryValue(pName, propertyDefinition, node, locale, errors);
        }
    }

    private boolean isPropertyEmpty (Property property) throws RepositoryException {
        boolean isEmpty = true;
        if (property.isMultiple()) {
            for (Value value : property.getValues()) {
                isEmpty &= isValueEmpty(value);
            }
        } else {
            isEmpty = isValueEmpty(property.getValue());
        }
        return isEmpty;
    }

    private boolean isValueEmpty(Value value) throws RepositoryException {
        if (value == null) return true;
        if (value.getType() == PropertyType.BINARY) {
            try {
                value.getBinary();
                return false;
            } catch (RepositoryException re) {
                return true;
            }
        }
        return value.getString().length() <= 0;
    }

    private void checkExistingProperties(JCRNodeWrapper node, ContentIntegrityErrorList errors) {
        final Map<String, ExtendedPropertyDefinition> namedPropertyDefinitions = new HashMap<>();
        final Map<Integer, ExtendedPropertyDefinition> unstructuredPropertyDefinitions = new HashMap<>();
        loadPropertyDefinitions(node, namedPropertyDefinitions, unstructuredPropertyDefinitions);

        try {
            final Node realNode = getRealNode(node);
            if (realNode == null)
                return;

            checkExistingPropertiesInternal(realNode, null, node, namedPropertyDefinitions, unstructuredPropertyDefinitions, errors);
            doOnTranslationNodes(node, new TranslationNodeProcessor() {
                @Override
                public void execute(Node translationNode, String locale) throws RepositoryException {
                    loadPropertyDefinitions(node.getSession().getNodeByUUID(translationNode.getIdentifier()), namedPropertyDefinitions, null);
                    checkExistingPropertiesInternal(translationNode, locale, node, namedPropertyDefinitions, unstructuredPropertyDefinitions, errors);
                }
            });
        } catch (RepositoryException e) {
            logger.error("Error while checking the existing properties on the node " + node.getPath(), e);
        }
    }

    private void loadPropertyDefinitions(JCRNodeWrapper node,
                                         Map<String, ExtendedPropertyDefinition> namedPropertyDefinitions,
                                         Map<Integer, ExtendedPropertyDefinition> unstructuredPropertyDefinitions) {

        doOnSupertypes(node, true, new SupertypeProcessor() {
            @Override
            public void execute(JCRNodeWrapper node, ExtendedNodeType extendedNodeType) {
                namedPropertyDefinitions.putAll(extendedNodeType.getPropertyDefinitionsAsMap());
                if (unstructuredPropertyDefinitions == null) return;
                for (ExtendedPropertyDefinition propertyDefinition : extendedNodeType.getPropertyDefinitions()) {
                    if (StringUtils.equals(propertyDefinition.getName(), Constants.PROPERTY_DEFINITION_NAME_WILDCARD)) {
                        unstructuredPropertyDefinitions.put(getExtendedPropertyType(propertyDefinition), propertyDefinition);
                    }
                }
            }
        });
    }

    private void checkExistingPropertiesInternal(Node node, String locale, JCRNodeWrapper jahiaNode,
                                                 Map<String, ExtendedPropertyDefinition> namedPropertyDefinitions,
                                                 Map<Integer, ExtendedPropertyDefinition> unstructuredPropertyDefinitions,
                                                 ContentIntegrityErrorList errors) throws RepositoryException {
        if (node instanceof JCRNodeWrapper) throw new IllegalArgumentException("This method has to be executed on the real node");

        final boolean isI18n = StringUtils.isNotBlank(locale);
        final PropertyIterator properties = node.getProperties();

        while (properties.hasNext()) {
            final Property property = properties.nextProperty();
            final String pName = property.getName();

            boolean isUndeclared;
            PropertyDefinition propertyDefinition = null;
            if (node.isNodeType(Constants.JNT_EXTERNAL_PROVIDER_EXTENSION)) {
                final JCRPropertyWrapper p;
                try {
                    p = jahiaNode.getSession().getNode(node.getPath()).getProperty(pName);
                    /*
                    in case of an external node extension (e.g. the node written in Jackrabbit to complete the virtual node), the extension node inherits from
                    jmix:externalProviderExtension , but not the Jahia node. As a consequence, it is not possible to load the ExtendedPropertyDefinition.
                    if this case happens again with other types, a more generic solution would be
                        if (!jahiaNode.isNodeType(propertyDefinition.getDeclaringNodeType().getName())) continue;
                     */
                    if (StringUtils.equals(property.getDefinition().getDeclaringNodeType().getName(), Constants.JMIX_EXTERNAL_PROVIDER_EXTENSION))
                        continue;
                } catch (RepositoryException re) {
                    // The property exists only at extension node level, let's skip it
                    continue;
                }
                try {
                    propertyDefinition = p.getDefinition();
                    isUndeclared = false;
                } catch (RepositoryException re) {
                    isUndeclared = true;
                }
            } else {
                try {
                    propertyDefinition = property.getDefinition();
                } catch (RepositoryException e) {
                    // if the property is declared, but its value doesn't match the declared type, then property.getDefinition()
                    // raises an exception
                }
                /*
                in case of an external node extension (e.g. the node written in Jackrabbit to complete the virtual node), the extension node inherits from
                jmix:externalProviderExtension , but not the Jahia node. As a consequence, it is not possible to load the ExtendedPropertyDefinition.
                if this case happens again with other types, a more generic solution would be
                    if (!jahiaNode.isNodeType(propertyDefinition.getDeclaringNodeType().getName())) continue;
                 */
                if (propertyDefinition != null && StringUtils.equals(propertyDefinition.getDeclaringNodeType().getName(), Constants.JMIX_EXTERNAL_PROVIDER_EXTENSION))
                    continue;
                if (namedPropertyDefinitions.containsKey(pName)) {
                    if (isI18n && propertyDefinition != null) {
                        isUndeclared = !namedPropertyDefinitions.get(pName).isInternationalized() &&
                                !isTranslationTypeParent(propertyDefinition.getDeclaringNodeType().getName());
                    } else {
                        isUndeclared = namedPropertyDefinitions.get(pName).isInternationalized() != isI18n;
                    }
                } else if (propertyDefinition != null && StringUtils.equals(propertyDefinition.getName(), Constants.PROPERTY_DEFINITION_NAME_WILDCARD)) {
                    isUndeclared = unstructuredPropertyDefinitions.keySet().stream().noneMatch(k -> areExtendedPropertyTypesCompliant(getExtendedPropertyType(property, isI18n), k));
                } else if (propertyDefinition != null) {
                    isUndeclared = false;
                    logger.error(String.format("The property %s is declared at Jackrabbit level, but not at Jahia level", property.getPath()));
                } else {
                    isUndeclared = true;
                }
            }

            if (isUndeclared) {
                trackUndeclaredProperty(pName, jahiaNode, locale, errors);
                continue;
            }

            // from here, the property is declared
            final ExtendedPropertyDefinition epd = getExtendedPropertyDefinition(propertyDefinition, property, isI18n, namedPropertyDefinitions, unstructuredPropertyDefinitions);
            if (epd == null) {
                logger.error(String.format("Impossible to load the property definition for the property %s on the node %s", pName, jahiaNode.getPath()));
                continue;
            }

            if (isPropertyEmpty(property)) continue;

            final int propertyXType = getExtendedPropertyType(property, isI18n);
            final int definitionXType = getExtendedPropertyType(epd);
            if (propertyXType != definitionXType) {
                if (multiValuedStatusDiffer(propertyXType, definitionXType)) {
                    trackInvalidMultiValuedStatus(pName, epd, jahiaNode, locale, errors);
                }
                if (baseTypeDiffer(propertyXType, definitionXType)) {
                    trackInvalidValueType(pName, epd, getValueType(property), jahiaNode, locale, errors);
                }
                continue;
            }

            // from here, the property is declared, and matches the definition structure
            checkPropertyConstraints(property, locale, epd, jahiaNode, errors);
        }
    }

    private void checkPropertyConstraints(Property property, String locale, ExtendedPropertyDefinition epd, JCRNodeWrapper jahiaNode, ContentIntegrityErrorList errors) throws RepositoryException {
        final boolean hasConstraints = epd.getValueConstraints() != null && epd.getValueConstraints().length > 0;
        if (!hasConstraints) return;

        final String pName = property.getName();
        if (epd.isMultiple()) {
            int idx = 0;
            for (Value value : property.getValues()) {
                checkValue(value, idx, pName, epd, jahiaNode, locale, errors);
                idx++;
            }
        } else {
            checkValue(property.getValue(), -1, pName, epd, jahiaNode, locale, errors);
        }
    }

    private void checkValue(Value value, int valueIdx,
                               String pName, ExtendedPropertyDefinition epd,
                               JCRNodeWrapper jahiaNode, String locale,
                               ContentIntegrityErrorList errors) throws RepositoryException {
        if (isValueEmpty(value)) return;
        if (!constraintIsValid(value, epd)) {
            trackInvalidValueConstraint(pName, epd, getPrintableValue(value), valueIdx, jahiaNode, locale, epd.getValueConstraints(), errors);
        }
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
                return true;
            } catch (ConstraintViolationException ignored) {
            } catch (RepositoryException e) {
                logger.error("", e);
            }
        }

        return false;
    }

    private int getValueType(Property property) throws RepositoryException {
        if (property.isMultiple()) {
            final Set<Integer> types = Arrays.stream(property.getValues())
                    .map(Value::getType)
                    .map(type -> type == PropertyType.REFERENCE ? PropertyType.WEAKREFERENCE : type)
                    .collect(Collectors.toSet());
            if (types.size() == 1) return types.iterator().next();
        } else {
            if (property.getValue() != null) {
                final int type = property.getValue().getType();
                return type == PropertyType.REFERENCE ? PropertyType.WEAKREFERENCE : type;
            }
        }
        return PropertyType.UNDEFINED;
    }

    private int getExtendedPropertyType(Property property, boolean isI18n) {
        boolean isMultiple = false;
        try {
            isMultiple = property.isMultiple();
            return getExtendedPropertyType(getValueType(property), isI18n, isMultiple);
        } catch (RepositoryException e) {
            logger.error("", e);
            return getExtendedPropertyType(0, isI18n, isMultiple);
        }
    }

    private int getExtendedPropertyType(ExtendedPropertyDefinition epd) {
        return getExtendedPropertyType(epd.getRequiredType(), epd.isInternationalized(), epd.isMultiple());
    }

    private int getExtendedPropertyType(int type, boolean isI18n, boolean isMultiple) {
        int xType = type;
        if (isI18n) xType += 100;
        if (isMultiple) xType += 1000;
        return xType;
    }

    private boolean baseTypeDiffer(int propertyXType, int definitionXType) {
        final int definitionBaseType = definitionXType % 100;
        final int propertyBaseType = propertyXType % 100;
        if (propertyBaseType == PropertyType.WEAKREFERENCE && definitionBaseType == PropertyType.REFERENCE) return false;
        return definitionBaseType != PropertyType.UNDEFINED && propertyBaseType != definitionBaseType;
    }

    private boolean i18nStatusDiffer(int propertyXType, int definitionXType) {
        return Math.floor(propertyXType / 100d) != Math.floor(definitionXType / 100d);
    }

    private boolean multiValuedStatusDiffer(int propertyXType, int definitionXType) {
        return Math.floor(propertyXType / 1000d) != Math.floor(definitionXType / 1000d);
    }

    private boolean areExtendedPropertyTypesCompliant(int propertyXType, int definitionXType) {
        if (i18nStatusDiffer(propertyXType, definitionXType) || multiValuedStatusDiffer(propertyXType, definitionXType))
            return false;
        return !baseTypeDiffer(propertyXType, definitionXType);
    }

    private ExtendedPropertyDefinition getExtendedPropertyDefinition(PropertyDefinition propertyDefinition,
                                                                     Property property, boolean isI18n,
                                                                     Map<String, ExtendedPropertyDefinition> namedPropertyDefinitions,
                                                                     Map<Integer, ExtendedPropertyDefinition> unstructuredPropertyDefinitions) throws RepositoryException {
        if (propertyDefinition == null) return namedPropertyDefinitions.get(property.getName());

        final String propertyDefinitionName = propertyDefinition.getName();
        if (StringUtils.equals(propertyDefinitionName, Constants.PROPERTY_DEFINITION_NAME_WILDCARD)) {
            if (isI18n && StringUtils.equals(propertyDefinition.getDeclaringNodeType().getName(), JAHIANT_TRANSLATION)) {
                final ExtendedPropertyDefinition epd = namedPropertyDefinitions.get(property.getName());
                if (epd != null && epd.isInternationalized()) return epd;
            }
            final List<Integer> compliantDefinitions = unstructuredPropertyDefinitions.keySet().stream()
                    .filter(k -> areExtendedPropertyTypesCompliant(getExtendedPropertyType(property, isI18n), k))
                    .collect(Collectors.toList());
            if (CollectionUtils.isEmpty(compliantDefinitions)) return null;
            if (compliantDefinitions.size() == 1) return unstructuredPropertyDefinitions.get(compliantDefinitions.get(0));
            logger.error("Several unstructured definitions are available for the property " + property.getPath());
            return null;
        } else {
            return namedPropertyDefinitions.get(propertyDefinitionName);
        }
    }

    private Node getRealNode(Node node) {
        if (node instanceof JCRNodeWrapper) {
            if (((JCRNodeWrapper) node).getRealNode() instanceof ExternalNodeImpl) {
                try {
                    return ((ExternalNodeImpl) ((JCRNodeWrapper) node).getRealNode()).getExtensionNode(false);
                } catch (RepositoryException e) {
                    logger.error("", e);
                    return null;
                }
            } else {
                return ((JCRNodeWrapper) node).getRealNode();
            }
        } else {
            return node;
        }
    }

    private boolean isTranslationTypeParent(String type) {
        if (!jntTranslationNtParents.containsKey(type))
            jntTranslationNtParents.put(type, jntTranslationNt.isNodeType(type));
        return jntTranslationNtParents.get(type);
    }

    private void trackMissingMandatoryValue(String propertyName, ExtendedPropertyDefinition propertyDefinition,
                                            JCRNodeWrapper node, String locale,
                                            ContentIntegrityErrorList errors) {
        trackError(ErrorType.EMPTY_MANDATORY_PROPERTY, propertyName, propertyDefinition, null, -1, -1, node, locale, null, errors);
    }

    private void trackInvalidValueConstraint(String propertyName, ExtendedPropertyDefinition propertyDefinition,
                                             String value, int valueIdx,
                                             JCRNodeWrapper node, String locale,
                                             String[] valueConstraints, ContentIntegrityErrorList errors) {
        final HashMap<String, Object> customExtraInfos = new HashMap<>();
        customExtraInfos.put("constraints", Arrays.toString(valueConstraints));
        trackError(ErrorType.INVALID_VALUE_CONSTRAINT, propertyName, propertyDefinition, value, valueIdx, -1, node, locale, customExtraInfos, errors);
    }

    private void trackInvalidValueType(String propertyName, ExtendedPropertyDefinition propertyDefinition,
                                       int valueType,
                                       JCRNodeWrapper node, String locale,
                                       ContentIntegrityErrorList errors) {
        trackError(ErrorType.INVALID_VALUE_TYPE, propertyName, propertyDefinition, null, -1, valueType, node, locale, null, errors);
    }

    private void trackInvalidMultiValuedStatus(String propertyName, ExtendedPropertyDefinition epd,
                                               JCRNodeWrapper node, String locale,
                                               ContentIntegrityErrorList errors) {
        trackError(ErrorType.INVALID_MULTI_VALUE_STATUS, propertyName, epd, null, -1, -1, node, locale, null, errors);
    }

    private void trackUndeclaredProperty(String propertyName,
                                         JCRNodeWrapper node, String locale,
                                         ContentIntegrityErrorList errors) {
        trackError(ErrorType.UNDECLARED_PROPERTY, propertyName, null, null, -1, -1, node, locale, null, errors);
    }

    private void trackNodeConstraintViolation(ConstraintViolation<JCRNodeValidator> constraintViolation,
                                              JCRNodeWrapper node,
                                              String locale,
                                              ContentIntegrityErrorList errors) {
        final HashMap<String, Object> customExtraInfos = new HashMap<>();
        String propertyName;
        final String propertyValue;
        try {
            final Method propertyNameGetter = constraintViolation.getConstraintDescriptor().getAnnotation().annotationType()
                    .getMethod("propertyName");
            propertyName = (String) propertyNameGetter.invoke(constraintViolation.getConstraintDescriptor().getAnnotation());
        } catch (Exception e) {
            propertyName = constraintViolation.getPropertyPath().toString();
        }

        ExtendedPropertyDefinition propertyDefinition;
        final String errorLocale;
        if (StringUtils.isNotBlank(propertyName)) {
            final String finalPropertyName = propertyName;
            propertyValue = JCRUtils.runJcrSupplierCallBack(() -> getPrintableValue(node.getProperty(finalPropertyName).getValue()));
            try {
                propertyDefinition = node.getApplicablePropertyDefinition(propertyName);
                if (propertyDefinition == null) {
                    propertyDefinition = node.getApplicablePropertyDefinition(propertyName.replaceFirst("_", ":"));
                }
            } catch (RepositoryException e) {
                logger.error("", e);
                propertyDefinition = null;
            }
            errorLocale = propertyDefinition != null && propertyDefinition.isInternationalized() ? locale : null;
        } else {
            propertyDefinition = null;
            errorLocale = null;
            propertyValue = null;
        }

        customExtraInfos.put("validator-message", constraintViolation.getMessage());
        customExtraInfos.put("language-use-for-validation", locale);
        final String extraMessage = errorLocale == null ? NON_I18N_PROP_VALIDATOR_ERROR_XTRA_MSG : null;
        trackError(ErrorType.INVALID_NODE_VALIDATION, propertyName, propertyDefinition, propertyValue, -1, -1, node, errorLocale, customExtraInfos, extraMessage, errors);
    }

    private void trackError(ErrorType errorType,
                            String propertyName, ExtendedPropertyDefinition propertyDefinition,
                            String value, int valueIdx, int valueType,
                            JCRNodeWrapper node, String locale,
                            Map<String, Object> customExtraInfos, ContentIntegrityErrorList errors) {
        trackError(errorType, propertyName, propertyDefinition, value, valueIdx, valueType, node, locale, customExtraInfos, null, errors);
    }

    private void trackError(ErrorType errorType,
                            String propertyName, ExtendedPropertyDefinition propertyDefinition,
                            String value, int valueIdx, int valueType,
                            JCRNodeWrapper node, String locale,
                            Map<String, Object> customExtraInfos, String extraMessage, ContentIntegrityErrorList errors) {
        final ContentIntegrityError error = createError(node, locale, errorType.desc)
                .setErrorType(errorType)
                .addExtraInfo("property-name", propertyName);
        if (propertyDefinition != null) {
            error.addExtraInfo("declaring-type", propertyDefinition.getDeclaringNodeType().getName());
        }
        if (value != null) {
            error.addExtraInfo("invalid-value", value, true);
            if (valueIdx >= 0 && propertyDefinition != null && propertyDefinition.isMultiple())
                error.addExtraInfo("value-index", valueIdx, true);
        }
        if (valueType >= 0) {
            error.addExtraInfo("value-type", PropertyType.nameFromValue(valueType));
            if (propertyDefinition != null)
                error.addExtraInfo("expected-value-type", PropertyType.nameFromValue(propertyDefinition.getRequiredType()));
        }
        if (customExtraInfos != null) {
            customExtraInfos.forEach(error::addExtraInfo);
        }
        if (StringUtils.isNotBlank(extraMessage)) error.setExtraMsg(extraMessage);

        errors.addError(error);
    }

    private void doOnTranslationNodes(JCRNodeWrapper node, TranslationNodeProcessor translationNodeProcessor) throws RepositoryException {
        if (checkSiteLangsOnly() && Utils.getSiteKey(node.getPath()) != null) {
            final JCRSiteNode site = node.getResolveSite();
            final List<Locale> locales = node.getSession().getWorkspace().getName().equals(Constants.EDIT_WORKSPACE) ?
                    site.getLanguagesAsLocales() : site.getActiveLiveLanguagesAsLocales();
            for (Locale locale : locales) {
                final Node translationNode = JCRUtils.getI18N(node, locale);
                if (translationNode == null) continue;
                final Node realTranslationNode = getRealNode(translationNode);
                if (realTranslationNode != null)
                    translationNodeProcessor.execute(realTranslationNode, locale.toString());
            }
        } else {
            final NodeIterator translationNodesIterator = node.getI18Ns();
            while (translationNodesIterator.hasNext()) {
                final Node translationNode = translationNodesIterator.nextNode();
                final Node realTranslationNode = getRealNode(translationNode);
                if (realTranslationNode == null)
                    continue;
                final String locale = JCRUtils.getTranslationNodeLocale(translationNode);
                if (StringUtils.isBlank(locale)) {
                    logger.error(String.format("Skipping a translation node since its language is invalid: %s", translationNode.getIdentifier()));
                    continue;
                }
                translationNodeProcessor.execute(realTranslationNode, locale);
            }
        }
    }

    private abstract static class TranslationNodeProcessor {

        public abstract void execute(Node translationNode, String locale) throws RepositoryException;
    }

    private void doOnSupertypes(JCRNodeWrapper node, SupertypeProcessor supertypeProcessor) {
        doOnSupertypes(node, false, supertypeProcessor);
    }

    private void doOnSupertypes(JCRNodeWrapper node, boolean reverseOrder, SupertypeProcessor supertypeProcessor) {
        final ExtendedNodeType primaryNodeType;
        final ExtendedNodeType[] mixinNodeTypes;
        try {
            primaryNodeType = node.getPrimaryNodeType();
            mixinNodeTypes = node.getMixinNodeTypes();
        } catch (RepositoryException e) {
            logger.error("Impossible to load the types of the node", e);
            return;
        }

        final List<ExtendedNodeType> superTypes = new ArrayList<>(mixinNodeTypes.length + 1);
        superTypes.add(primaryNodeType);
        superTypes.addAll(Arrays.asList(mixinNodeTypes));
        if (reverseOrder) Collections.reverse(superTypes);

        String identifier = null;
        try {
            identifier = node.getIdentifier();
        } catch (RepositoryException e) {
            logger.error("", e);
        }

        try {
            for (ExtendedNodeType superType : superTypes) {
                supertypeProcessor.execute(node, superType);
            }
        } catch (RepositoryException e) {
            logger.error("Error while checking the node " + identifier, e);
        }
    }

    private abstract static class SupertypeProcessor {

        public abstract void execute(JCRNodeWrapper node, ExtendedNodeType extendedNodeType) throws RepositoryException;
    }

    private String getPrintableValue(Value value) {
        try {
            return value.getType() == PropertyType.BINARY ? BINARY_VALUE_STR : value.getString();
        } catch (RepositoryException e) {
            logger.error("", e);
            return FAILED_TO_CALCULATE_VALUE_STR;
        }
    }
}
