package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.Utils;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.ContentIntegrityCheckConfigurationImpl;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Item;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.nodetype.PropertyDefinition;
import java.util.Arrays;
import java.util.Objects;

import static org.jahia.modules.contentintegrity.services.impl.Constants.CALCULATION_ERROR;
import static org.jahia.modules.contentintegrity.services.impl.Constants.MIX_VERSIONABLE;
import static org.jahia.modules.contentintegrity.services.impl.ContentIntegrityCheckConfigurationImpl.BOOLEAN_PARSER;

@Component(service = ContentIntegrityCheck.class, immediate = true)
public class ReferencesSanityCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.IsConfigurable {

    private static final Logger logger = LoggerFactory.getLogger(ReferencesSanityCheck.class);
    private static final String VALIDATE_REFS = "validate-refs";
    private static final String VALIDATE_BACK_REFS = "validate-back-refs";
    private static final String VALIDATE_VERSION_HISTORY = "validate-version-history";

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

    private enum ErrorType {INVALID_BACK_REF, BROKEN_REF, BROKEN_REF_TO_VN}

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
                logger.error(String.format("Skipping %s as its definition is inconsistent", JCRUtils.runJcrCallBack(property, Item::getPath, CALCULATION_ERROR)), e);
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
                propErrors = Arrays.stream(values)
                        .map(v -> checkPropertyValue(v, node, property))
                        .filter(Objects::nonNull)
                        .reduce(Utils::mergeErrorLists)
                        .orElse(null);
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
                        return createSingleError(createPropertyRelatedError(checkedNode, "Broken reference to a virtual node")
                                .setErrorType(ErrorType.BROKEN_REF_TO_VN)
                                .addExtraInfo("property-name", JCRUtils.runJcrCallBack(property, Property::getName, CALCULATION_ERROR))
                                .addExtraInfo("missing-uuid", uuid, true));
                    }
                    return createSingleError(createPropertyRelatedError(checkedNode, "Broken reference")
                            .setErrorType(ErrorType.BROKEN_REF)
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
                errors.addError(createError(checkedNode, "Missing referencing node")
                        .setErrorType(ErrorType.INVALID_BACK_REF)
                        .addExtraInfo("property-name", JCRUtils.runJcrCallBack(property, Item::getName, CALCULATION_ERROR))
                        .addExtraInfo("referencing-node-path", JCRUtils.runJcrSupplierCallBack(() -> property.getParent().getPath(), CALCULATION_ERROR), true));
            }
        }
        return errors;
    }
}
