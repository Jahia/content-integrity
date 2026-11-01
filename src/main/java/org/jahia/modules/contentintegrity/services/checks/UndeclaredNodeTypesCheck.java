package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.data.templates.JahiaTemplatesPackage;
import org.jahia.data.templates.ModuleState;
import org.jahia.modules.contentintegrity.api.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.ContentIntegrityCheckConfigurationImpl;
import org.jahia.modules.contentintegrity.services.util.RepairUtils;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.jahia.services.content.nodetypes.NodeTypeRegistry;
import org.jahia.utils.Patterns;
import org.osgi.framework.Bundle;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NoSuchNodeTypeException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jahia.modules.contentintegrity.api.Constants.JCR_MIXINTYPES;
import static org.jahia.modules.contentintegrity.api.Constants.JCR_PRIMARYTYPE;

@Component(service = ContentIntegrityCheck.class, immediate = true)
public class UndeclaredNodeTypesCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.SupportsIntegrityErrorFix, ContentIntegrityCheck.IsConfigurable {

    private static final Logger logger = LoggerFactory.getLogger(UndeclaredNodeTypesCheck.class);

    public static final ContentIntegrityErrorType UNDECLARED_NODE_TYPE = createErrorType("UNDECLARED_NODE_TYPE", "Undeclared type", true);
    public static final ContentIntegrityErrorType GHOST_NODE_TYPE = createErrorType("GHOST_NODE_TYPE", "Ghost type", true);

    // The systemId of the node types declared by Jahia core, such as system-jahia
    private static final String CORE_SYSTEM_ID_PREFIX = "system-";

    private static final String PRIMARY_TYPE_INFO = "primary type";
    private static final String MIXIN_TYPE_INFO = "mixin type";

    private static final String TYPES_TO_FIX_KEY = "types-to-fix";
    private static final String TYPES_TO_FIX_DEFAULT_VAL = "";

    private final ContentIntegrityCheckConfiguration configurations;

    private final Set<String> existingNodeTypes = new HashSet<>();
    private final Map<String, Boolean> missingNodeTypes = new HashMap<>();
    private final Set<String> existingMixins = new HashSet<>();
    private final Map<String, Boolean> missingMixins = new HashMap<>();
    // The last node whose deletion impact has been measured: the scan reaches its descendants next
    private String measuredSubtree;

    public UndeclaredNodeTypesCheck() {
        configurations = new ContentIntegrityCheckConfigurationImpl();
        configurations.declareDefaultParameter(TYPES_TO_FIX_KEY, TYPES_TO_FIX_DEFAULT_VAL, null, "Types whose errors the fix acts on: it deletes the nodes of such a primary type, and removes such a mixin from its nodes (comma separated, spaces allowed). The errors of the other types are not fixed");
    }

    @Override
    public ContentIntegrityCheckConfiguration getConfigurations() {
        return configurations;
    }

    @Override
    protected void reset() {
        existingNodeTypes.clear();
        existingMixins.clear();
        missingNodeTypes.clear();
        missingMixins.clear();
        measuredSubtree = null;
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        final ContentIntegrityErrorList errors = createEmptyErrorsList();
        final String pt = node.getPropertyAsString(JCR_PRIMARYTYPE);
        checkNodeType(node, errors, existingNodeTypes, missingNodeTypes, PRIMARY_TYPE_INFO, false, pt);
        try {
            if (node.hasProperty(JCR_MIXINTYPES)) {
                final String[] types = Arrays.stream(node.getProperty(JCR_MIXINTYPES).getValues())
                        .map(getStringValue(errors, node))
                        .filter(Objects::nonNull)
                        .toArray(String[]::new);
                checkNodeType(node, errors, existingMixins, missingMixins, MIXIN_TYPE_INFO, true, types);
            }
        } catch (RepositoryException re) {
            errors.addError(createFrameworkError(node, "Impossible to read the mixins of the node " + node, re));
        }
        return errors;
    }

    private Function<JCRValueWrapper, String> getStringValue(ContentIntegrityErrorList errors, JCRNodeWrapper checkedNode) {
        return jcrValueWrapper -> {
            try {
                return jcrValueWrapper.getString();
            } catch (RepositoryException e) {
                errors.addError(createFrameworkError(checkedNode, e));
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
            final ContentIntegrityError error = createError(node, errorType, String.format("Undeclared %s", text))
                    .addExtraInfo(text, type);
            if (!isMixin) addDeletionImpact(node, error);
            errors.addError(error);
        }
    }

    /*
     * What the fix of a primary type deletes, read before an administrator names the type in the configuration: the
     * descendants of the node, and the references from outside its subtree to the node or to one of its descendants. The
     * real node is read, since the Jahia API can't load a node whose type is undeclared. A node in the subtree of an
     * error already measured gets no counts: the ones of its ancestor include it, and the fix of the ancestor deletes it.
     */
    private void addDeletionImpact(JCRNodeWrapper node, ContentIntegrityError error) {
        try {
            final Node realNode = node.getRealNode();
            final String path = realNode.getPath();
            if (measuredSubtree != null && path.startsWith(measuredSubtree + "/")) return;
            measuredSubtree = path;
            final DeletionImpact impact = new DeletionImpact();
            measureDeletionImpact(realNode, path, impact);
            error.addExtraInfo("descendants-count", impact.descendants, true)
                    .addExtraInfo("references-count", impact.references, true);
        } catch (RepositoryException e) {
            logger.debug("Impossible to measure what the fix of {} deletes", node.getPath(), e);
        }
    }

    private static void measureDeletionImpact(Node node, String rootPath, DeletionImpact impact) throws RepositoryException {
        impact.references += countExternalReferences(node.getReferences(), rootPath) + countExternalReferences(node.getWeakReferences(), rootPath);
        final NodeIterator children = node.getNodes();
        while (children.hasNext()) {
            impact.descendants++;
            measureDeletionImpact(children.nextNode(), rootPath, impact);
        }
    }

    private static long countExternalReferences(PropertyIterator references, String rootPath) throws RepositoryException {
        long count = 0;
        while (references.hasNext()) {
            final String referencingPath = references.nextProperty().getParent().getPath();
            if (!referencingPath.equals(rootPath) && !referencingPath.startsWith(rootPath + "/")) count++;
        }
        return count;
    }

    private static final class DeletionImpact {
        private long descendants;
        private long references;
    }

    private ExtendedNodeTypeWrapper getNodeType(String type) {
        try {
            final ExtendedNodeType nodeType = BundleUtils.getOsgiService(NodeTypeRegistry.class, null).getNodeType(type);
            final JahiaTemplatesPackage templatePackage = nodeType.getTemplatePackage();

            // No template package: the node type is either declared by Jahia core, then the node type registry is assumed to be
            // consistent with the code definitions, or bound to a module with no started version, not installed anymore or stopped
            if (templatePackage == null) return new ExtendedNodeTypeWrapper(nodeType, !nodeType.getSystemId().startsWith(CORE_SYSTEM_ID_PREFIX));

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

    /*
     * A ghost type is often the type of a module which is only stopped, or which has been moved to another module: its
     * nodes must be kept. So only the types named by the administrator in the configuration are fixed.
     */
    @Override
    public boolean isFixable(ContentIntegrityError error) {
        if (!error.getErrorType().equals(UNDECLARED_NODE_TYPE) && !error.getErrorType().equals(GHOST_NODE_TYPE)) return false;
        final String type = getTypeOfError(error);
        return type != null && getTypesToFix().contains(type);
    }

    private Set<String> getTypesToFix() {
        return Patterns.COMMA.splitAsStream((String) getConfigurations().getParameter(TYPES_TO_FIX_KEY))
                .map(StringUtils::trimToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private static String getTypeOfError(ContentIntegrityError error) {
        final Object mixin = error.getExtraInfo(MIXIN_TYPE_INFO);
        return (String) (mixin != null ? mixin : error.getExtraInfo(PRIMARY_TYPE_INFO));
    }

    /*
     * From the jcr-scripts fixes: an undeclared mixin is removed from the node. A node can't change its primary
     * type, so a node whose primary type is undeclared is removed.
     */
    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException {
        if (!isFixable(error)) return false;

        final String type = getTypeOfError(error);
        final boolean isMixin = error.getExtraInfo(MIXIN_TYPE_INFO) != null;
        // The type has been declared again since the scan, for example by a module started again: the error is gone
        if (isDeclared(type, isMixin)) {
            logger.info("The type {} is declared again, so {} is not changed", type, node.getPath());
            return true;
        }
        if (isMixin) {
            // A mixin already removed, for example by the fix of another error, is fixed
            RepairUtils.runWithListenersDisabled(() -> RepairUtils.removeMixinRaw(node, type));
        } else {
            RepairUtils.runWithListenersDisabled(() -> RepairUtils.removeNodeRaw(node));
        }
        return true;
    }

    private boolean isDeclared(String type, boolean isMixin) {
        final ExtendedNodeTypeWrapper nodeType = getNodeType(type);
        return nodeType.getWrappedNT() != null && !nodeType.isGhostType() && nodeType.getWrappedNT().isMixin() == isMixin;
    }
}
