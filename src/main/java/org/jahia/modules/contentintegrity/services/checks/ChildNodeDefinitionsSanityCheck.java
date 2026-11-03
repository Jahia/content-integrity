package org.jahia.modules.contentintegrity.services.checks;

import org.jahia.modules.contentintegrity.api.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.util.RepairUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.nodetypes.ExtendedNodeType;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.nodetype.NodeType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.SKIP_ON_NT + "=rep:root"
})
public class ChildNodeDefinitionsSanityCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.SupportsIntegrityErrorFix {

    private static final Logger logger = LoggerFactory.getLogger(ChildNodeDefinitionsSanityCheck.class);
    private static final String ACL_NODE_NAME = "j:acl";

    public static final ContentIntegrityErrorType NOT_ALLOWED_BY_PARENT_DEF = createErrorType("NOT_ALLOWED_BY_PARENT_DEF",
            "The node is not allowed as a child node of its current parent node", true);

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        try {
            final JCRNodeWrapper parent = node.getParent();
            /*
            https://github.com/Jahia/jahia/blob/JAHIA_8_1_2_0/core/src/main/java/org/jahia/services/content/nodetypes/ExtendedNodeType.java#L1103
            if the node definition declares at least one i18n property, then jmix:i18n is added to the list of supertypes,
            so that the node can accept translation sub nodes, no matter the definition.
            If not, let's continue with the regular test, in case such subnode would be anyway allowed, by another mean that jmix:i18n
             */
            if (node.isNodeType(Constants.JAHIANT_TRANSLATION) && parent.isNodeType(Constants.JAHIA_MIX_I18N)) return null;

            final ContentIntegrityErrorList errors = createEmptyErrorsList();
            if (!isAllowed(node, parent, errors))
                return errors.addError(createError(node, NOT_ALLOWED_BY_PARENT_DEF).addExtraInfo("parent-node-type", parent.getPrimaryNodeTypeName()));
        } catch (RepositoryException re) {
            return createSingleError(createFrameworkError(node, "Impossible to validate " + node.getPath(), re));
        }

        return null;
    }

    private boolean isAllowed(JCRNodeWrapper node, JCRNodeWrapper parent, ContentIntegrityErrorList errors) throws RepositoryException {
        final List<String> types = new ArrayList<>();
        types.add(node.getPrimaryNodeTypeName());
        types.addAll(Arrays.stream(node.getMixinNodeTypes())
                .map(ExtendedNodeType::getName)
                .collect(Collectors.toList()));
        final String name = node.getName();
        return types.stream().anyMatch(type -> isChildAllowed(parent, name, type, errors, node))
                || isAcceptedByRepository(parent, name, node.getRealNode().getPrimaryNodeType().getName());
    }

    /*
     * Jahia rejects a child whose name matches a named child definition of another type, where Jackrabbit falls back
     * to the residual definitions. Such a node was accepted by the repository, for example a page that an editor
     * renamed j:conditionalVisibility, and is not an error: its fix would delete a subtree the editor may not delete.
     */
    private boolean isAcceptedByRepository(JCRNodeWrapper parent, String name, String primaryType) {
        try {
            final Node realParent = parent.getRealNode();
            if (realParent.getPrimaryNodeType().canAddChildNode(name, primaryType)) return true;
            for (NodeType mixin : realParent.getMixinNodeTypes()) {
                if (mixin.canAddChildNode(name, primaryType)) return true;
            }
        } catch (RepositoryException e) {
            logger.debug("Impossible to check if the repository accepts {} under {}", name, parent.getPath(), e);
        }
        return false;
    }

    private boolean isChildAllowed(JCRNodeWrapper parent, String name, String type, ContentIntegrityErrorList errors, JCRNodeWrapper checkedNode) {
        try {
            parent.getApplicableChildNodeDefinition(name, type);
        } catch (ConstraintViolationException cve) {
            return false;
        } catch (RepositoryException e) {
            errors.addError(createFrameworkError(checkedNode, String.format("Error while checking if %s accepts a child of type %s named %s",
                    parent.getPath(), type, name), e));
        }

        /*
        If there's a RepositoryException of another type than ConstraintViolationException,
        let's return true, so that no ContentIntegrityError is created with an error type related to the current check
         */
        return true;
    }

    /*
     * From the jcr-scripts fix: a node that its parent definition doesn't allow is removed, at the Jackrabbit
     * level, since the regular API can't load its definition. The error is checked again first, since the node may
     * have been moved or renamed since the scan.
     * A node with sub-nodes is not removed: the raw removal would take its whole subtree, which can hold content
     * that the user who created or moved the node may neither read nor delete. Its translations and its ACL are part
     * of the node.
     */
    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException {
        if (!error.getErrorType().equals(NOT_ALLOWED_BY_PARENT_DEF)) return false;
        if (isAllowed(node, node.getParent(), createEmptyErrorsList())) return true;
        if (hasSubtree(node.getRealNode())) return false;
        RepairUtils.runWithListenersDisabled(() -> RepairUtils.removeNodeRaw(node));
        return true;
    }

    private static boolean hasSubtree(Node node) throws RepositoryException {
        final NodeIterator children = node.getNodes();
        while (children.hasNext()) {
            final String name = children.nextNode().getName();
            if (!name.startsWith(Constants.TRANSLATION_NODE_PREFIX) && !name.equals(ACL_NODE_NAME)) return true;
        }
        return false;
    }
}
