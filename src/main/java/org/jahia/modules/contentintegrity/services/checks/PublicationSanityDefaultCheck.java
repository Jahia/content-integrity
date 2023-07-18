package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static org.jahia.modules.contentintegrity.services.impl.Constants.LIVE_WORKSPACE;
import static org.jahia.modules.contentintegrity.services.impl.Constants.MODULES_SUBTREE_PATH_PREFIX;
import static org.jahia.modules.contentintegrity.services.impl.Constants.PUBLISHED;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.JAHIAMIX_LASTPUBLISHED,
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_WS + "=" + Constants.EDIT_WORKSPACE
})
public class PublicationSanityDefaultCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.SupportsIntegrityErrorFix {

    private static final Logger logger = LoggerFactory.getLogger(PublicationSanityDefaultCheck.class);
    private static final String DIFFERENT_PATH_ROOT = "different-path-root";

    private enum ErrorType {NO_LIVE_NODE, DIFFERENT_PATH, DIFFERENT_PATH_POTENTIAL_FP}
    private final Map<String, Object> inheritedErrors = new HashMap<>();
    private String scanRoot = null;

    @Override
    protected void initializeIntegrityTestInternal(JCRNodeWrapper node, Collection<String> excludedPaths) {
        scanRoot = node.getPath();
    }

    @Override
    public ContentIntegrityErrorList finalizeIntegrityTestInternal(JCRNodeWrapper node, Collection<String> excludedPaths) {
        inheritedErrors.clear();
        scanRoot = null;
        return null;
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        try {
            final JCRSessionWrapper liveSession = JCRUtils.getSystemSession(LIVE_WORKSPACE, true);
            final boolean flaggedPublished = node.hasProperty(PUBLISHED) && node.getProperty(PUBLISHED).getBoolean();
            if (flaggedPublished || node.isNodeType(Constants.JMIX_AUTO_PUBLISH) || StringUtils.startsWith(node.getPath(), MODULES_SUBTREE_PATH_PREFIX)) {
                final JCRNodeWrapper liveNode;
                try {
                    liveNode = liveSession.getNodeByIdentifier(node.getIdentifier());
                } catch (ItemNotFoundException infe) {
                    final String msg = String.format("Found a node %s, but no corresponding live node exists",
                            flaggedPublished? "flagged as published" : "auto-published");
                    final ContentIntegrityError error = createError(node, msg)
                            .setErrorType(ErrorType.NO_LIVE_NODE);
                    return createSingleError(error);
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
                        final String msg = "Found a published node, with no pending modifications, but the path in live is different";
                        final ContentIntegrityError error = createError(node, msg)
                                .addExtraInfo("live-node-path", liveNode.getPath(), true);
                        if (!StringUtils.equals(nodePath, "/") && StringUtils.equals(nodePath, scanRoot)) {
                            inheritedErrors.put(DIFFERENT_PATH_ROOT, nodePath);
                            error.setErrorType(ErrorType.DIFFERENT_PATH_POTENTIAL_FP);
                            error.setExtraMsg("Warning: this node is the root of the scan, but not the root of the JCR. So the error might be a false positive, if the node is under a node which has been moved, but this move operation has not been published yet. To clarify this, you need to analyze the parent nodes, or redo the scan from a higher level");
                        } else {
                            error.setErrorType(ErrorType.DIFFERENT_PATH);
                        }
                        return createSingleError(error);
                    }
                }
            }
            return null;
        } catch (RepositoryException e) {
            logger.error("", e);
            return null;
        }
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
        final Object errorTypeObject = integrityError.getErrorType();
        if (!(errorTypeObject instanceof ErrorType)) {
            logger.error("Unexpected error type: " + errorTypeObject);
            return false;
        }
        final ErrorType errorType = (ErrorType) errorTypeObject;
        switch (errorType) {
            case NO_LIVE_NODE:
                node.getProperty(PUBLISHED).remove();
                node.getSession().save();
                return true;
        }
        return false;
    }
}
