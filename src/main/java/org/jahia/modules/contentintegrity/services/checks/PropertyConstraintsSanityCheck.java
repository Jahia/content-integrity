package org.jahia.modules.contentintegrity.services.checks;

import org.apache.jackrabbit.core.value.InternalValue;
import org.apache.jackrabbit.spi.commons.nodetype.constraint.ValueConstraint;
import org.apache.jackrabbit.spi.commons.value.QValueValue;
import org.jahia.ajax.gwt.helper.ContentDefinitionHelper;
import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.Property;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.nodetype.ConstraintViolationException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ENABLED + ":Boolean=true",
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.JAHIANT_CONTENT + "," + Constants.JAHIANT_PAGE
})
public class PropertyConstraintsSanityCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(PropertyConstraintsSanityCheck.class);

    private ContentDefinitionHelper contentDefinitionHelper;

    private List<String> propertiesToIgnore = Arrays.asList("jcr:predecessors", "j:nodename", "jcr:versionHistory",
            "jcr:baseVersion", "jcr:isCheckedOut", "jcr:uuid", "jcr:mergeFailed", "j:isHomePage", "j:templateNode",
            "j:tags", "j:newTag", "j:tagList", "jcr:activity", "jcr:configuration", "j:legacyRuleSettings",
            "j:processId", "jcr:description", "j:invalidLanguages", "j:workInProgress", "jcr:lockOwner",
            "jcr:configuration", "j:originWS", "j:published", "j:lastPublishedBy", "j:lastPublished",
            "jcr:lastModified", "jcr:LastModifiedBy", "jcr:lockIsDeep", "j:locktoken", "j:lockTypes", "jcr:created",
            "jcr:createdBy", "j:fullpath", "jcr:mixinTypes", "jcr:primaryType"
    );

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        try {
            final JCRSessionWrapper session = node.getSession();
            final List<String> nodeTypes;
            nodeTypes = node.getNodeTypes();
            for (String nt : nodeTypes) {
                final Collection<ExtendedPropertyDefinition> propDefs = NodeTypeRegistry.getInstance().getNodeType(nt).getPropertyDefinitionsAsMap().values();
                for (ExtendedPropertyDefinition propertyDefinition : propDefs) {
                    final String propertyName = propertyDefinition.getName();
                    Locale errorLocale = null;
                    if (propertyDefinition.isInternationalized()) {
                        errorLocale = session.getLocale();
                    }

                    if (propertiesToIgnore.contains(propertyName)) {
                        continue;
                    }

                    final Property prop;
                    try {
                        prop = node.getProperty(propertyName);
                    } catch (PathNotFoundException ex) {
                        if (logger.isDebugEnabled())
                            logger.debug(String.format("Property : %s not found on node %s so continuing to other properties without validating", propertyName, node.getPath()));
                        continue;
                    } catch (RepositoryException ex) {
                        logger.error(String.format("Error getting Property : %s on node %s continuing even though error is : %s", propertyName, node.getPath(), ex.getMessage()));
                        if (logger.isDebugEnabled()) logger.debug("", ex);
                        continue;
                    }

                    if (Constants.JCR_TITLE.equals(propertyName) && !hasMixTitle(node.getPrimaryNodeType())) {
                        return createSingleError(node, errorLocale, "This node has a jcr:title property but, the primary node type does not have mix:title as one of it's supertypes");
                    }

                    // Following checks for constraint not fulfiled
                    if (propertyDefinition.getValueConstraints().length != 0) {
                        if (node.hasProperty(propertyName)) {
                            if (!propertyDefinition.isMultiple()) {
                                final Value value = node.getProperty(propertyName).getValue();
                                InternalValue internalValue = null;
                                if (value.getType() != PropertyType.BINARY && !((value.getType() == PropertyType.PATH || value.getType() == PropertyType.NAME) && !(value instanceof QValueValue))) {
                                    internalValue = InternalValue.create(value, null, null);
                                }
                                if (internalValue != null) {
                                    final ContentIntegrityError integrityError = validateConstraints(node, propertyDefinition, new InternalValue[]{
                                            internalValue}, errorLocale);
                                    if (integrityError != null) return createSingleError(integrityError);
                                }
                            } else {
                                final Value[] values = node.getProperty(propertyName).getValues();
                                final List<InternalValue> list = new ArrayList<InternalValue>();
                                for (Value value : values) {
                                    if (value != null) {
                                        // perform type conversion as necessary and create InternalValue
                                        // from (converted) Value
                                        InternalValue internalValue = null;
                                        if (value.getType() != PropertyType.BINARY
                                                && !((value.getType() == PropertyType.PATH || value.getType() == PropertyType.NAME) && !(value instanceof QValueValue))) {
                                            internalValue = InternalValue.create(value, null, null);
                                        }
                                        list.add(internalValue);
                                    }
                                }
                                if (!list.isEmpty()) {
                                    final InternalValue[] internalValues = list.toArray(new InternalValue[0]);
                                    final ContentIntegrityError integrityError = validateConstraints(node, propertyDefinition, internalValues, errorLocale);
                                    if (integrityError != null) return createSingleError(integrityError);
                                }
                            }
                        }
                    }


                    // Following condition checks for values incoherent with its type (i.e. string instead of date)
                    if (propertyDefinition != null && propertyDefinition.getSelectorOptions().get("password") == null) {

                        // check that we're not dealing with a not-set property from the translation nodes,
                        // in which case it needs to be omitted
                        final Locale locale = session.getLocale();
                        if (Constants.nonI18nPropertiesCopiedToTranslationNodes.contains(propertyName) && node.hasI18N(locale,
                                false)) {
                            // get the translation node for the current locale
                            final Node i18N = node.getI18N(locale, false);
                            if (!i18N.hasProperty(propertyName)) {
                                // if the translation node doesn't have the property and it's part of the set of copied
                                // properties, then we shouldn't test it
                                continue;
                            }
                        }

                        final Value[] values;
                        if (!propertyDefinition.isMultiple()) {
                            final Value oneValue = prop.getValue();
                            values = new Value[]{oneValue};
                        } else {
                            values = prop.getValues();
                        }

                        for (Value val : values) {
                            try {
                                getContentDefinitionHelper().convertValue(val, propertyDefinition);
                            } catch (RepositoryException rex) {
                                return createSingleError(node, locale, String.format("The property %s has a value inconsistent with its type", propertyName));
                            }

                        }
                    }
                }
            }
        } catch (RepositoryException e) {
            logger.error("", e);  //TODO: review me, I'm generated
        }
        return null;
    }

    /**
     * Verify if the nodeType is inheriting mix:title mixin
     *
     * @param nodeType nodetype to verify
     * @return true if the nodetype is inheriting mix:title, false otherwise
     */
    private boolean hasMixTitle(ExtendedNodeType nodeType) {
        if (Constants.MIX_TITLE.equals(nodeType.getName())) {
            return true;
        }

        final ExtendedNodeType[] supertypes = nodeType.getSupertypes();
        for (ExtendedNodeType extendedNodeType : supertypes) {
            if (hasMixTitle(extendedNodeType)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Validate constraints integrity on a node, such as regex or range constraint
     *
     * @param node               node on which perform constraint validation check
     * @param propertyDefinition property definition of the node
     * @param values             internvalValue of the node
     * @param errorLocale        locale being used/checked
     * @return
     */
    private ContentIntegrityError validateConstraints(JCRNodeWrapper node, ExtendedPropertyDefinition propertyDefinition,
                                                      InternalValue[] values, Locale errorLocale) {
        try {
            // check multi-value flag
            if (!propertyDefinition.isMultiple() && values != null && values.length > 1) {
                if (logger.isDebugEnabled())
                    logger.debug(String.format("Property is not multi-valued : %s | Node : %s",
                            propertyDefinition.getName(), node.getPath()));
                throw new ConstraintViolationException("the property is not multi-valued");
            }

            final ValueConstraint[] constraints = propertyDefinition.getValueConstraintObjects();

            if (values != null && values.length > 0) {

                // check value constraints on every value
                for (InternalValue value : values) {
                    // constraints are OR-ed together
                    boolean satisfied = false;
                    ConstraintViolationException cve = null;
                    for (ValueConstraint constraint : constraints) {
                        try {
                            constraint.check(value);
                            satisfied = true;
                            break;
                        } catch (ConstraintViolationException e) {
                            cve = e;
                        }
                    }
                    if (!satisfied) {
                        // re-throw last exception we encountered
                        throw cve;
                    }
                }
            }
        } catch (ConstraintViolationException e) {
            return createError(node, errorLocale, e.getMessage());
        } catch (RepositoryException e) {
            logger.debug("Repository exception", e);
        }

        return null;
    }

    private ContentDefinitionHelper getContentDefinitionHelper() {
        if (contentDefinitionHelper != null) return contentDefinitionHelper;
        final Object bean = SpringContextSingleton.getBean("ContentDefinitionHelper");
        if (bean instanceof ContentDefinitionHelper) contentDefinitionHelper = (ContentDefinitionHelper) bean;
        return contentDefinitionHelper;
    }

}
