package org.jahia.modules.contentintegrity.services.checks;

import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.id.NodeId;
import org.apache.jackrabbit.core.version.InternalVersionHistory;
import org.apache.jackrabbit.core.version.InternalVersionManager;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.Utils;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.jahia.modules.contentintegrity.services.impl.ContentIntegrityCheckConfigurationImpl;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.modules.external.ExtensionNode;
import org.jahia.modules.external.ExternalNodeImpl;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.history.NodeVersionHistoryHelper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import java.util.Collections;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.MIX_VERSIONABLE,
        ContentIntegrityCheck.ENABLED + "=false"
})
public class VersionHistoryCheck extends AbstractContentIntegrityCheck implements
        ContentIntegrityCheck.SupportsIntegrityErrorFix,
        ContentIntegrityCheck.IsConfigurable {

    private static final Logger logger = LoggerFactory.getLogger(VersionHistoryCheck.class);

    public static final ContentIntegrityErrorType TOO_MANY_VERSIONS = createErrorType("TOO_MANY_VERSIONS", "The node has too many versions");

    private static final String THRESHOLD_KEY = "threshold";
    private static final int defaultThreshold = 100;

    private final ContentIntegrityCheckConfiguration configurations;

    public VersionHistoryCheck() {
        configurations = new ContentIntegrityCheckConfigurationImpl();
        configurations.declareDefaultParameter(THRESHOLD_KEY, defaultThreshold, ContentIntegrityCheckConfigurationImpl.INTEGER_PARSER, "Number of versions for a single node beyond which an error is raised");
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        final Node realNode = node.getRealNode();
        if (realNode instanceof ExternalNodeImpl || realNode instanceof ExtensionNode) return null;

        final String identifier;
        try {
            identifier = node.getIdentifier();
        } catch (RepositoryException e) {
            return createSingleError(createFrameworkError(node, "Impossible to calculate the node identifier", e));
        }

        // skip the node if it can have been checked while scanning the default workspace
        if (JCRUtils.isInLiveWorkspace(node) && JCRUtils.nodeExists(identifier, JCRUtils.getSystemSession(Constants.EDIT_WORKSPACE, false)))
            return null;

        try {
            final int numVersions;
            try {
                numVersions = countVersions(node, identifier);
            } catch (ItemNotFoundException infe) {
                final ContentIntegrityErrorList errors = createSingleError(createFrameworkError(node, infe));
                if (logger.isDebugEnabled()) logger.debug("Impossible to load the version history for the node " + node.getPath(), infe);
                return errors;
            }
            final int threshold = getThreshold();
            if (numVersions > threshold)
                return createSingleError(createError(node, TOO_MANY_VERSIONS, String.format("The node has over %s versions", threshold))
                        .addExtraInfo("versions-count", numVersions, true)
                        .addExtraInfo("versions-count-range", Utils.getApproximateCount(numVersions, threshold))
                        .addExtraInfo("auto-publish", node.isNodeType(Constants.JMIX_AUTO_PUBLISH)));
        } catch (RepositoryException e) {
            return createSingleError(createFrameworkError(node, String.format("Error while checking the version history of the node %s", identifier), e));
        }

        return null;
    }

    private int countVersions(JCRNodeWrapper node, String identifier) throws RepositoryException {
        final JCRSessionWrapper session = node.getSession();
        final SessionImpl providerSession = (SessionImpl) session.getProviderSession(session.getNode(Constants.ROOT_NODE_PATH).getProvider());
        final InternalVersionManager vm = providerSession.getInternalVersionManager();
        final InternalVersionHistory history = vm.getVersionHistoryOfNode(NodeId.valueOf(identifier));
        return history.getNumVersions();
    }

    /*
     * The purge removes the versions which are not used anymore: the versions still used, for example by the live
     * node, are kept. The error is fixed only when the remaining versions are not over the threshold anymore.
     */
    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException {
        final String identifier = node.getIdentifier();
        NodeVersionHistoryHelper.purgeVersionHistoryForNodes(Collections.singleton(identifier));
        final int remaining = countVersions(node, identifier);
        if (remaining > getThreshold()) {
            logger.warn("{} versions of {} are still used after the purge, over the threshold {}", remaining, node.getPath(), getThreshold());
            return false;
        }
        return true;
    }

    private int getThreshold() {
        return (int) getConfigurations().getParameter(THRESHOLD_KEY);
    }

    @Override
    public ContentIntegrityCheckConfiguration getConfigurations() {
        return configurations;
    }
}

/*
Notes

It would be better to be able to configure if the nodes existing in each workspace have to be checked when scanning the default or the live.
Currently, they are checked when scanning the default.
 */
