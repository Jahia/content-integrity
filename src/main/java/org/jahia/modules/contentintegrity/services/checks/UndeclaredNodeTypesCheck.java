package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.data.templates.JahiaTemplatesPackage;
import org.jahia.data.templates.ModuleState;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.jahia.utils.Patterns;
import org.osgi.framework.Bundle;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.jahia.modules.contentintegrity.services.impl.Constants.JCR_MIXINTYPES;
import static org.jahia.modules.contentintegrity.services.impl.Constants.JCR_PRIMARYTYPE;

@Component(service = ContentIntegrityCheck.class, immediate = true)
public class UndeclaredNodeTypesCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(UndeclaredNodeTypesCheck.class);

    public static final ContentIntegrityErrorType UNDECLARED_NODE_TYPE = createErrorType("UNDECLARED_NODE_TYPE", "Undeclared type", true);
    public static final ContentIntegrityErrorType GHOST_NODE_TYPE = createErrorType("GHOST_NODE_TYPE", "Ghost type", true);

    private final Set<String> existingNodeTypes = new HashSet<>();
    private final Map<String, Boolean> missingNodeTypes = new HashMap<>();
    private final Set<String> existingMixins = new HashSet<>();
    private final Map<String, Boolean> missingMixins = new HashMap<>();

    @Override
    protected void reset() {
        existingNodeTypes.clear();
        existingMixins.clear();
        missingNodeTypes.clear();
        missingMixins.clear();
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        final ContentIntegrityErrorList errors = createEmptyErrorsList();
        final String pt = node.getPropertyAsString(JCR_PRIMARYTYPE);
        checkNodeType(node, errors, existingNodeTypes, missingNodeTypes, "primary type", false, pt);
        try {
            if (node.hasProperty(JCR_MIXINTYPES)) {
                final String[] types = Arrays.stream(node.getProperty(JCR_MIXINTYPES).getValues())
                        .map(getStringValue())
                        .filter(Objects::nonNull)
                        .toArray(String[]::new);
                checkNodeType(node, errors, existingMixins, missingMixins, "mixin type", true, types);
            }
        } catch (RepositoryException re) {
            logger.error("Impossible to read the mixins of the node " + node, re);
        }
        return errors;
    }

    private Function<JCRValueWrapper, String> getStringValue() {
        return jcrValueWrapper -> {
            try {
                return jcrValueWrapper.getString();
            } catch (RepositoryException e) {
                logger.error("", e);
                return null;
            }
        };
    }

    private void checkNodeType(JCRNodeWrapper node, ContentIntegrityErrorList errors,
                               Set<String> existingTypes, Map<String, Boolean> missingTypes,
                               String text, boolean isMixin,
                               String... types) {
        for (String type : types) {
            if (existingTypes.contains(type)) continue;

            if (!missingTypes.containsKey(type)) {
                final ExtendedNodeTypeWrapper nodeTypeWrapper = getNodeType(type);
                final ExtendedNodeType nodeType = nodeTypeWrapper.getWrappedNT();
                if (nodeType == null || nodeTypeWrapper.isGhostType()) {
                    missingTypes.put(type, nodeTypeWrapper.isGhostType());
                } else if (nodeType.isMixin() == isMixin) {
                    existingTypes.add(type);
                    continue;
                } else {
                    missingTypes.put(type, false);
                }
            }
            final ContentIntegrityErrorType errorType = missingTypes.get(type) ? GHOST_NODE_TYPE : UNDECLARED_NODE_TYPE;
            errors.addError(createError(node, errorType, String.format("Undeclared %s", text))
                    .addExtraInfo(text, type));
        }
    }

    private ExtendedNodeTypeWrapper getNodeType(String type) {
        try {
            final ExtendedNodeType nodeType = NodeTypeRegistry.getInstance().getNodeType(type);
            final JahiaTemplatesPackage templatePackage = nodeType.getTemplatePackage();

            // the node type is not declared by a module, let's assume that the node type registry is always consistent with the code definitions
            if (templatePackage == null) return new ExtendedNodeTypeWrapper(nodeType, false);

            // The node type is declared by a stopped module or is not declared by the started version of the module
            if (templatePackage.getState().getState() != ModuleState.State.STARTED)
                return new ExtendedNodeTypeWrapper(nodeType, true);

            final boolean isDeclared = Optional.ofNullable(templatePackage.getBundle())
                    .map(Bundle::getHeaders)
                    .map(d -> d.get("Provide-Capability"))
                    .map(capabilities -> StringUtils.substringBetween(capabilities, "com.jahia.services.content;nodetypes:List<String>=\"", "\""))
                    .map(Patterns.COMMA::splitAsStream)
                    .map(declaredTypes -> declaredTypes.anyMatch(type::equals))
                    .orElse(false);

            return new ExtendedNodeTypeWrapper(nodeType, !isDeclared);
        } catch (NoSuchNodeTypeException e) {
            return new ExtendedNodeTypeWrapper(null, false);
        }
    }

    static class ExtendedNodeTypeWrapper {
        private final ExtendedNodeType wrappedNT;
        private final boolean isGhostType;

        ExtendedNodeTypeWrapper(ExtendedNodeType wrappedNT, boolean isGhostType) {
            this.wrappedNT = wrappedNT;
            this.isGhostType = isGhostType;
        }

        public ExtendedNodeType getWrappedNT() {
            return wrappedNT;
        }

        public boolean isGhostType() {
            return isGhostType;
        }
    }
}
