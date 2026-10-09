package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.Utils;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.ContentIntegrityCheckConfigurationImpl;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.modules.contentintegrity.services.util.RepairUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.osgi.service.component.annotations.Component;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.nodetype.PropertyDefinition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.jahia.modules.contentintegrity.services.impl.Constants.CALCULATION_ERROR;
import static org.jahia.modules.contentintegrity.services.impl.Constants.MIX_VERSIONABLE;
import static org.jahia.modules.contentintegrity.services.impl.ContentIntegrityCheckConfigurationImpl.BOOLEAN_PARSER;

@Component(service = ContentIntegrityCheck.class, immediate = true)
public class ReferencesSanityCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.IsConfigurable, ContentIntegrityCheck.SupportsIntegrityErrorFix {

    private static final String VALIDATE_REFS = "validate-refs";
    private static final String VALIDATE_BACK_REFS = "validate-back-refs";
    private static final String VALIDATE_VERSION_HISTORY = "validate-version-history";
    public static final ContentIntegrityErrorType INVALID_BACK_REF = createErrorType("INVALID_BACK_REF", "Missing referencing node");
    public static final ContentIntegrityErrorType BROKEN_REF = createErrorType("BROKEN_REF", "Broken reference");
    public static final ContentIntegrityErrorType BROKEN_REF_TO_VN = createErrorType("BROKEN_REF_TO_VN", "Broken reference to a virtual node");

    // A reference to a virtual node, and an invalid back reference, are not fixed
    private static final Collection<ContentIntegrityErrorType> FIXABLE_ERRORS = Arrays.asList(BROKEN_REF);

    private final ContentIntegrityCheckConfiguration configurations;

    public ReferencesSanityCheck() {
        configurations = new ContentIntegrityCheckConfigurationImpl();
        configurations.declareDefaultParameter(VALIDATE_REFS, Boolean.TRUE, BOOLEAN_PARSER, "Check the references sanity");
        configurations.declareDefaultParameter(VALIDATE_BACK_REFS, Boolean.FALSE, BOOLEAN_PARSER, "Check the back references sanity");
        configurations.declareDefaultParameter(VALIDATE_VERSION_HISTORY, Boolean.FALSE, BOOLEAN_PARSER, "Check the version history");
    }

    @Override
    public ContentIntegrityCheckConfiguration getConfigurations() {
        return configurations;
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        return Utils.mergeErrorLists(
                checkBackReferences(node),
                checkReferences(node)
        );
    }

    private ContentIntegrityErrorList checkReferences(JCRNodeWrapper node) {
        if (!((Boolean) getConfigurations().getParameter(VALIDATE_REFS))) return null;

        final PropertyIterator properties = JCRUtils.runJcrCallBack(node, Node::getProperties);
        if (properties == null) return null;

        ContentIntegrityErrorList errors = null;

        while (properties.hasNext()) {
            final Property property = properties.nextProperty();
            final PropertyDefinition definition;
            try {
                definition = property.getDefinition();
                property.getType();
            } catch (RepositoryException e) {
                if (errors == null) errors = createEmptyErrorsList();
                errors.addError(createFrameworkError(node, String.format("Skipping %s as its definition is inconsistent", JCRUtils.runJcrCallBack(property, Item::getPath, CALCULATION_ERROR)), e));
                continue;
            }
            if (!((Boolean) getConfigurations().getParameter(VALIDATE_VERSION_HISTORY)) && StringUtils.equals(definition.getDeclaringNodeType().getName(), MIX_VERSIONABLE)) {
                continue;
            }

            final Boolean isMultiple = JCRUtils.runJcrCallBack(property, Property::isMultiple);
            if (isMultiple == null) continue;

            final ContentIntegrityErrorList propErrors;
            if (isMultiple) {
                final Value[] values = JCRUtils.runJcrCallBack(property, Property::getValues);
                if (values == null) continue;
                propErrors = Utils.mergeErrorLists(Arrays.stream(values)
                        .map(v -> checkPropertyValue(v, node, property))
                        .filter(Objects::nonNull)
                        .toArray(ContentIntegrityErrorList[]::new));
            } else {
                final Value value = JCRUtils.runJcrCallBack(property, Property::getValue);
                if (value == null) continue;
                propErrors = checkPropertyValue(value, node, property);
            }
            errors = Utils.mergeErrorLists(errors, propErrors);
        }
        return errors;
    }

    private ContentIntegrityErrorList checkPropertyValue(Value value, JCRNodeWrapper checkedNode, Property property) {
        switch (value.getType()) {
            case PropertyType.REFERENCE:
            case PropertyType.WEAKREFERENCE:
                return JCRUtils.runJcrSupplierCallBack(() -> {
                    final String uuid = value.getString();
                    if (JCRUtils.nodeExists(uuid, checkedNode.getSession())) return null;
                    if (JCRUtils.isVirtualNodeIdentifier(uuid)) {
                        return createSingleError(createPropertyRelatedError(checkedNode, BROKEN_REF_TO_VN)
                                .addExtraInfo("property-name", JCRUtils.runJcrCallBack(property, Property::getName, CALCULATION_ERROR))
                                .addExtraInfo("missing-uuid", uuid, true));
                    }
                    return createSingleError(createPropertyRelatedError(checkedNode, BROKEN_REF)
                            .addExtraInfo("property-name", JCRUtils.runJcrCallBack(property, Property::getName, CALCULATION_ERROR))
                            .addExtraInfo("missing-uuid", uuid, true));
                });
            default:
                return null;
        }
    }

    private ContentIntegrityErrorList checkBackReferences(JCRNodeWrapper node) {
        if (!((Boolean) getConfigurations().getParameter(VALIDATE_BACK_REFS))) return null;

        final PropertyIterator weakReferences = JCRUtils.isExternalNode(node) ? null : JCRUtils.runJcrCallBack(node, Node::getWeakReferences);
        final PropertyIterator references = JCRUtils.isExternalNode(node) ? null : JCRUtils.runJcrCallBack(node, Node::getReferences);
        return Utils.mergeErrorLists(
                checkBackReferences(weakReferences, node),
                checkBackReferences(references, node)
        );
    }

    private ContentIntegrityErrorList checkBackReferences(PropertyIterator propertyIterator, JCRNodeWrapper checkedNode) {
        if (propertyIterator == null) return null;

        ContentIntegrityErrorList errors = null;
        while (propertyIterator.hasNext()) {
            final Property property = propertyIterator.nextProperty();
            final String referencingNodeID;
            try {
                referencingNodeID = property.getParent().getIdentifier();
                property.getSession().getNodeByIdentifier(referencingNodeID);
            } catch (RepositoryException e) {
                if (errors == null) errors = createEmptyErrorsList();
                errors.addError(createError(checkedNode, INVALID_BACK_REF)
                        .addExtraInfo("property-name", JCRUtils.runJcrCallBack(property, Item::getName, CALCULATION_ERROR))
                        .addExtraInfo("referencing-node-path", JCRUtils.runJcrSupplierCallBack(() -> property.getParent().getPath(), CALCULATION_ERROR), true));
            }
        }
        return errors;
    }

    @Override
    public boolean isFixable(ContentIntegrityError error) {
        return FIXABLE_ERRORS.contains(error.getErrorType());
    }

    /*
     * From the jcr-scripts fix: the broken reference is removed. On a multi-valued property, only the broken
     * value is removed, and the property is removed when no value remains. A broken reference to a virtual
     * node is not fixed, since the provider might be temporarily unavailable.
     */
    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException {
        if (!error.getErrorType().equals(BROKEN_REF)) return false;
        final String propertyName = (String) error.getExtraInfo("property-name");
        final String missingUuid = (String) error.getExtraInfo("missing-uuid");
        final JCRNodeWrapper target = RepairUtils.getErrorTarget(node, error);
        if (StringUtils.isBlank(propertyName)) return false;
        // A property already removed, for example by the fix of another error, is fixed
        if (!target.hasProperty(propertyName)) return true;

        RepairUtils.runWithListenersDisabled(() -> {
            final JCRPropertyWrapper property = target.getProperty(propertyName);
            if (property.isMultiple() && missingUuid != null) {
                final List<Value> kept = new ArrayList<>();
                for (JCRValueWrapper value : property.getValues()) {
                    if (!missingUuid.equals(value.getString())) kept.add(value);
                }
                if (!kept.isEmpty()) {
                    property.setValue(kept.toArray(new Value[0]));
                    target.saveSession();
                    return;
                }
            }
            property.remove();
            target.saveSession();
        });
        return true;
    }
}
