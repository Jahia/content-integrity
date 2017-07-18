package org.jahia.modules.verifyintegrity.services.checks;

import org.jahia.api.Constants;
import org.jahia.modules.verifyintegrity.services.ContentIntegrityCheck;
import org.jahia.modules.verifyintegrity.services.ContentIntegrityError;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

public class PublicationSanityCheck extends ContentIntegrityCheck implements ContentIntegrityCheck.SupportsIntegrityErrorFix {

    private static final Logger logger = LoggerFactory.getLogger(PublicationSanityCheck.class);


    private enum ErrorType {DEFAULT, LIVE}

    @Override
    public ContentIntegrityError checkIntegrityBeforeChildren(Node node) {
        try {
            if (isInDefaultWorkspace(node)) {
                final JCRSessionWrapper liveSession = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.LIVE_WORKSPACE, null, null);
                return scanDefault(node, liveSession);
            } else {
                final JCRSessionWrapper defaultSession = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, null, null);
                return scanLive(node, defaultSession);
            }
        } catch (RepositoryException e) {
            logger.error("", e);
            return null;
        }
    }

    private ContentIntegrityError scanLive(javax.jcr.Node node, JCRSessionWrapper defaultSession) {
        try {
            if (node.hasProperty(Constants.ORIGIN_WORKSPACE) && Constants.LIVE_WORKSPACE.equals(node.getProperty(Constants.ORIGIN_WORKSPACE).getString())) {
                // UGC
                return null;
            }
            try {
                defaultSession.getNodeByIdentifier(node.getIdentifier());
            } catch (ItemNotFoundException infe) {
                final String msg = "Found not-UGC node which exists only in live";
                final ContentIntegrityError error = ContentIntegrityError.createError(node, null, msg, this);
                error.setExtraInfos(ErrorType.LIVE);
                return error;
            }
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        return null;
    }

    private ContentIntegrityError scanDefault(javax.jcr.Node node, JCRSessionWrapper liveSession) {
        try {
            if (node.hasProperty(Constants.PUBLISHED) && node.getProperty(Constants.PUBLISHED).getBoolean()) {
                try {
                    liveSession.getNodeByIdentifier(node.getIdentifier());
                } catch (ItemNotFoundException infe) {
                    final String msg = "Found a node flagged as published, but no corresponding live node exists";
                    final ContentIntegrityError error = ContentIntegrityError.createError(node, null, msg, this);
                    error.setExtraInfos(ErrorType.DEFAULT);
                    return error;
                }
            }
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        return null;
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
            case DEFAULT:
                node.getProperty(Constants.PUBLISHED).remove();
                node.getSession().save();
                return true;
            case LIVE:
                node.remove();
                node.getSession().save();
                return true;
        }
        return false;
    }
}
