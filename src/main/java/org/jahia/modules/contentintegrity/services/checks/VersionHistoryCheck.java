package org.jahia.modules.contentintegrity.services.checks;

import org.apache.jackrabbit.core.SessionImpl;
import org.apache.jackrabbit.core.id.NodeId;
import org.apache.jackrabbit.core.version.InternalVersionHistory;
import org.apache.jackrabbit.core.version.InternalVersionManager;
import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.jahia.modules.contentintegrity.services.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.ContentIntegrityCheckConfigurationImpl;
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
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.MIX_VERSIONABLE
})
public class VersionHistoryCheck extends AbstractContentIntegrityCheck implements
        ContentIntegrityCheck.SupportsIntegrityErrorFix,
        ContentIntegrityCheck.IsConfigurable {

    private static final Logger logger = LoggerFactory.getLogger(VersionHistoryCheck.class);

    private static final String THRESHOLD_KEY = "threshold";
    private static final int defaultThreshold = 100;

    private final ContentIntegrityCheckConfiguration configurations;

    public VersionHistoryCheck() {
        configurations = new ContentIntegrityCheckConfigurationImpl();
        configurations.declareDefaultParameter(THRESHOLD_KEY, defaultThreshold, "Number of versions for a single node beyond which an error is raised");
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        final Node realNode = node.getRealNode();
        if (realNode instanceof ExternalNodeImpl || realNode instanceof ExtensionNode) return null;
        
        try {
            final JCRSessionWrapper session = node.getSession();
            final SessionImpl providerSession = (SessionImpl) session.getProviderSession(session.getNode("/").getProvider());
            final InternalVersionManager vm = providerSession.getInternalVersionManager();
            final InternalVersionHistory history;
            try {
                history = vm.getVersionHistoryOfNode(NodeId.valueOf(node.getIdentifier()));
            } catch (ItemNotFoundException infe) {
                logger.error(infe.getMessage());
                if (logger.isDebugEnabled()) logger.debug("Impossible to load the version history for the node " + node.getPath(), infe);
                return null;
            }
            final int numVersions = history.getNumVersions();
            final int threshold = getThreshold();
            if (numVersions > threshold)
                return createSingleError(node, String.format("The node has over %s versions: %s", threshold, numVersions));
        } catch (RepositoryException e) {
            logger.error("", e);
        }

        return null;
    }

    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException {
        NodeVersionHistoryHelper.purgeVersionHistoryForNodes(Collections.singleton(node.getIdentifier()));
        return true;
    }

    private int getThreshold() {
        final Object o = getConfigurations().getParameter(THRESHOLD_KEY);
        if (o instanceof Integer) return (Integer) o;
        if (o instanceof String) {
            try {
                return Integer.parseInt((String) o);
            } catch (NumberFormatException nfe) {
                logger.error(String.format("Invalid threshold: %s", o));
            }
        }
        return defaultThreshold;
    }

    @Override
    public ContentIntegrityCheckConfiguration getConfigurations() {
        return configurations;
    }
}
