package org.jahia.modules.verifyintegrity.services.checks;

import org.jahia.modules.verifyintegrity.services.ContentIntegrityCheck;
import org.jahia.modules.verifyintegrity.services.ContentIntegrityError;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

import static org.jahia.api.Constants.LIVE_WORKSPACE;
import static org.jahia.api.Constants.PUBLISHED;

public class PublicationSanityDefaultCheck extends ContentIntegrityCheck implements ContentIntegrityCheck.SupportsIntegrityErrorFix {

    private static final Logger logger = LoggerFactory.getLogger(PublicationSanityDefaultCheck.class);


    private enum ErrorType {NO_LIVE_NODE}

    @Override
    public ContentIntegrityError checkIntegrityBeforeChildren(Node node) {
        try {
            final JCRSessionWrapper liveSession = JCRSessionFactory.getInstance().getCurrentSystemSession(LIVE_WORKSPACE, null, null);
            if (node.hasProperty(PUBLISHED) && node.getProperty(PUBLISHED).getBoolean()) {
                try {
                    liveSession.getNodeByIdentifier(node.getIdentifier());
                    return null;
                } catch (ItemNotFoundException infe) {
                    final String msg = "Found a node flagged as published, but no corresponding live node exists";
                    final ContentIntegrityError error = ContentIntegrityError.createError(node, null, msg, this);
                    error.setExtraInfos(ErrorType.NO_LIVE_NODE);
                    return error;
                }
            }
            return null;
        } catch (RepositoryException e) {
            logger.error("", e);
            return null;
        }
    }

    @Override
    public ContentIntegrityError checkIntegrityAfterChildren(Node node) {
        return null;
    }


    @Override
    public boolean fixError(Node node, Object errorExtraInfos) throws RepositoryException {
        if (errorExtraInfos == null || !(errorExtraInfos instanceof ErrorType)) {
            logger.error("Unexpected error type: " + errorExtraInfos);
            return false;
        }
        final ErrorType errorType = (ErrorType) errorExtraInfos;
        switch (errorType) {
            case NO_LIVE_NODE:
                node.getProperty(PUBLISHED).remove();
                node.getSession().save();
                return true;
        }
        return false;
    }
}
