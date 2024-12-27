package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;
import java.util.Collection;

import static org.jahia.modules.contentintegrity.services.impl.Constants.JCR_FROZENNODE;
import static org.jahia.modules.contentintegrity.services.impl.Constants.JCR_FROZENUUID;
import static org.jahia.modules.contentintegrity.services.impl.Constants.JCR_VERSIONABLEUUID;
import static org.jahia.modules.contentintegrity.services.impl.Constants.NT_VERSION;

/*
@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_SUBTREES + "=" + "/jcr:system/jcr:versionStorage",
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.NT_VERSIONHISTORY,
        ContentIntegrityCheck.ENABLED + "=false"
})
 */
public class VersionSanityCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(VersionSanityCheck.class);

    public static final ContentIntegrityErrorType ORPHAN_IN_SUBTREE = createErrorType("ORPHAN_IN_SUBTREE", "Orphaned version histories found in the subtree");
    public static final ContentIntegrityErrorType ORPHANED_HISTORY = createErrorType("ORPHANED_HISTORY", "Orphaned version history");
    public static final ContentIntegrityErrorType HISTORY_WITHOUT_NODE_ID = createErrorType("HISTORY_WITHOUT_NODE_ID", "Version history without versioned node ID");

    private long versionHistoriesCount = 0L;
    private long orphanVersionHistoriesCount = 0L;
    private long versionNodesCount = 0L;
    private long orphanedVersionNodesCount = 0L;

    @Override
    protected void initializeIntegrityTestInternal(JCRNodeWrapper scanRootNode, Collection<String> excludedPaths) {
        versionHistoriesCount = 0L;
        orphanVersionHistoriesCount = 0L;
        versionNodesCount = 0L;
        orphanedVersionNodesCount = 0L;
    }

    @Override
    protected ContentIntegrityErrorList finalizeIntegrityTestInternal(JCRNodeWrapper scanRootNode, Collection<String> excludedPaths) {
        if (orphanVersionHistoriesCount > 0) {
            return createSingleError(createError(scanRootNode, ORPHAN_IN_SUBTREE)
                    .addExtraInfo("version-histories-count", versionHistoriesCount)
                    .addExtraInfo("orphaned-version-histories-count", orphanVersionHistoriesCount)
                    .addExtraInfo("orphaned-version-histories-ratio", String.format("%.2f%%", 100F * orphanVersionHistoriesCount / versionHistoriesCount))
                    .addExtraInfo("total-nb-version-nodes", versionNodesCount)
                    .addExtraInfo("total-nb-orphan-version-nodes", orphanedVersionNodesCount)
                    .addExtraInfo("total-nb-non-orphan-version-nodes", versionNodesCount - orphanedVersionNodesCount)
                    .addExtraInfo("orphaned-version-nodes-ratio", String.format("%.2f%%", 100F * orphanedVersionNodesCount / versionNodesCount))
                    .addExtraInfo("total-nb-deletable-version-nodes", versionNodesCount - versionHistoriesCount + orphanVersionHistoriesCount)
            );
        }
        return null;
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        versionHistoriesCount++;
        final int nbVersions = JCRContentUtils.getChildrenOfType(node, NT_VERSION).size();
        versionNodesCount += nbVersions;
        final String uuid = node.getPropertyAsString(JCR_VERSIONABLEUUID);
        if (StringUtils.isBlank(uuid)) {
            return createSingleError(createError(node, HISTORY_WITHOUT_NODE_ID));
        }

        if (JCRUtils.runJcrCallBack(node, VersionSanityCheck::isOrphanedHistory)) {
            orphanVersionHistoriesCount++;
            orphanedVersionNodesCount += nbVersions;
            return createSingleError(createError(node, ORPHANED_HISTORY).addExtraInfo("nb-version-nodes", nbVersions, true));
        }

        return null;
    }

    private static boolean isOrphanedHistory(JCRNodeWrapper versionHistory) throws RepositoryException {
        final JCRNodeIteratorWrapper it = versionHistory.getNodes();
        while (it.hasNext()) {
            final JCRNodeWrapper node = (JCRNodeWrapper) it.next();
            if (node.isNodeType(NT_VERSION) && node.hasNode(JCR_FROZENNODE)) {
                final JCRNodeWrapper frozen = node.getNode(JCR_FROZENNODE);
                if (frozen.hasProperty(JCR_FROZENUUID)) {
                    final String uuid = frozen.getPropertyAsString(JCR_FROZENUUID);
                    return !JCRUtils.nodeExists(uuid, JCRUtils.getSystemEditSession()) && !JCRUtils.nodeExists(uuid, JCRUtils.getSystemLiveSession());
                }
            }
        }
        return false;
    }
}
