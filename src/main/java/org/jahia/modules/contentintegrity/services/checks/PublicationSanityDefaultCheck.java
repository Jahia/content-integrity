package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.jahia.modules.contentintegrity.services.impl.Constants.LIVE_WORKSPACE;
import static org.jahia.modules.contentintegrity.services.impl.Constants.MODULES_SUBTREE_PATH_PREFIX;
import static org.jahia.modules.contentintegrity.services.impl.Constants.PUBLISHED;
import static org.jahia.modules.contentintegrity.services.impl.Constants.ROOT_NODE_PATH;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.JAHIAMIX_LASTPUBLISHED,
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_WS + "=" + Constants.EDIT_WORKSPACE
})
public class PublicationSanityDefaultCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.SupportsIntegrityErrorFix {

    private static final Logger logger = LoggerFactory.getLogger(PublicationSanityDefaultCheck.class);
    private static final String DIFFERENT_PATH_ROOT = "different-path-root";
    private static final String EXTRA_MSG_DIFFERENT_PATH_POTENTIAL_FP = "Warning: this node is the root of the scan, but not the root of the JCR. So the error might be a false positive, if the node is under a node which has been moved, but this move operation has not been published yet. To clarify this, you need to analyze the parent nodes, or redo the scan from a higher level";
    public static final ContentIntegrityErrorType NO_LIVE_NODE = createErrorType("NO_LIVE_NODE", "The live node with the same uuid is missing");
    public static final ContentIntegrityErrorType DIFFERENT_PATH = createErrorType("DIFFERENT_PATH", "Found a published node, with no pending modifications, but the path in live is different", true);
    public static final ContentIntegrityErrorType DIFFERENT_PATH_POTENTIAL_FP = createErrorType("DIFFERENT_PATH_POTENTIAL_FP", "Found a published node, with no pending modifications, but the path in live is different", true);
    public static final ContentIntegrityErrorType PATH_CONFLICT = createErrorType("PATH_CONFLICT", "Live node with same path but different uuid", true);
    public static final ContentIntegrityErrorType DIFFERENT_PT = createErrorType("DIFFERENT_PT", "Live node with same uuid but different primary type", true);

    private final Map<String, Object> inheritedErrors = new HashMap<>();
    private String scanRoot = null;

    @Override
    protected void reset() {
        inheritedErrors.clear();
        scanRoot = null;
    }

    @Override
    protected void initializeIntegrityTestInternal(JCRNodeWrapper node, Collection<String> excludedPaths) {
        scanRoot = node.getPath();
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        ContentIntegrityErrorList errors = null;
        try {
            final JCRSessionWrapper liveSession = JCRUtils.getSystemSession(LIVE_WORKSPACE, true);

            JCRNodeWrapper samePathLiveNode = null;
            try {
                samePathLiveNode = liveSession.getNode(node.getPath());
            } catch (PathNotFoundException ignored) {}
            if (samePathLiveNode != null) {
                final String samePathLiveNodeIdentifier = samePathLiveNode.getIdentifier();
                if (!StringUtils.equals(node.getIdentifier(), samePathLiveNodeIdentifier)) {
                    final String editNodeWithLiveNodeUUID = JCRUtils.runJcrCallBack(samePathLiveNodeIdentifier, id -> node.getSession().getNodeByIdentifier(id).getPath(), null, false);
                    final ContentIntegrityError error = createError(node, PATH_CONFLICT)
                            .addExtraInfo("live-node-uuid", samePathLiveNodeIdentifier, true)
                            .addExtraInfo("live-node-primary-type", samePathLiveNode.getPrimaryNodeTypeName())
                            .addExtraInfo("conflicted-edit-node", StringUtils.defaultIfBlank(editNodeWithLiveNodeUUID, "none"), true);
                    errors = trackError(errors, error);
                }
            }

            final boolean flaggedPublished = node.hasProperty(PUBLISHED) && node.getProperty(PUBLISHED).getBoolean();
            if (flaggedPublished || node.isNodeType(Constants.JMIX_AUTO_PUBLISH) || StringUtils.startsWith(node.getPath(), MODULES_SUBTREE_PATH_PREFIX)) {
                final JCRNodeWrapper liveNode;
                try {
                    liveNode = liveSession.getNodeByIdentifier(node.getIdentifier());
                } catch (ItemNotFoundException infe) {
                    final String msg = String.format("Found a node %s, but no corresponding live node exists",
                            flaggedPublished? "flagged as published" : "auto-published");
                    final ContentIntegrityError error = createError(node, NO_LIVE_NODE, msg);
                    return trackError(errors, error);
                }

                final String liveNodePT = liveNode.getPrimaryNodeTypeName();
                if (!StringUtils.equals(node.getPrimaryNodeTypeName(), liveNodePT)) {
                    final ContentIntegrityError error = createError(node, DIFFERENT_PT)
                            .addExtraInfo("live-node-primary-type", liveNodePT);
                    errors = trackError(errors, error);
                }

                /*
                 comparison of the path between the default and live workspaces: if a node has a different path, then the test will not be done
                 on its subtree
                 */
                final String nodePath = node.getPath();
                if (!inheritedErrors.containsKey(DIFFERENT_PATH_ROOT) && !StringUtils.equals(nodePath, liveNode.getPath())) {
                    inheritedErrors.put(DIFFERENT_PATH_ROOT, nodePath);
                    // Here we check the pending modifications without considering the translation subnodes. Only a renaming of node can
                    // change its path, what should result in pending modifications on the node itself
                    if (!JCRUtils.hasPendingModifications(node)) {
                        final ContentIntegrityError error;
                        if (!StringUtils.equals(nodePath, ROOT_NODE_PATH) && StringUtils.equals(nodePath, scanRoot)) {
                            inheritedErrors.put(DIFFERENT_PATH_ROOT, nodePath);
                            error = createError(node, DIFFERENT_PATH_POTENTIAL_FP);
                            error.setExtraMsg(EXTRA_MSG_DIFFERENT_PATH_POTENTIAL_FP);
                        } else {
                            error = createError(node, DIFFERENT_PATH);
                        }
                        error.addExtraInfo("live-node-path", liveNode.getPath(), true);
                        errors = trackError(errors, error);
                    }
                }
            }
        } catch (RepositoryException e) {
            logger.error("", e);
        }

        return errors;

        /*
        When checking if there's a node with the same ID in live, we never check if it has jmix:origin:Ws=live
        because PublicationSanityLiveCheck detects the nodes with jmix:origin:Ws=live and raises an error when there's
        a node with the same ID in default.
         */
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityAfterChildren(JCRNodeWrapper node) {
        if (inheritedErrors.containsKey(DIFFERENT_PATH_ROOT) && StringUtils.equals(node.getPath(), (String) inheritedErrors.get(DIFFERENT_PATH_ROOT)))
            inheritedErrors.remove(DIFFERENT_PATH_ROOT);
        return super.checkIntegrityAfterChildren(node);
    }

    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError integrityError) throws RepositoryException {
        if (integrityError.getErrorType().equals(NO_LIVE_NODE)) {
            node.getProperty(PUBLISHED).remove();
            node.getSession().save();
            return true;
        }
        return false;
    }
}
