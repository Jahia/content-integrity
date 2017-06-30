package org.jahia.modules.verifyintegrity.services;

import org.jahia.exceptions.JahiaException;
import org.jahia.exceptions.JahiaInitializationException;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.utils.DateUtils;
import org.slf4j.Logger;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContentIntegrityService {

    private static Logger logger = org.slf4j.LoggerFactory.getLogger(ContentIntegrityService.class);
    private static ContentIntegrityService instance = new ContentIntegrityService();

    private List<ContentIntegrityCheck> integrityChecks = new ArrayList<>();

    public static ContentIntegrityService getInstance() {
        return instance;
    }

    private ContentIntegrityService() {
    }

    public void start() throws JahiaInitializationException {
        //TODO: review me, I'm generated
    }

    public void stop() throws JahiaException {
        //TODO: review me, I'm generated
    }

    public void registerIntegrityCheck(ContentIntegrityCheck integrityCheck) {
        integrityChecks.add(integrityCheck);
        Collections.sort(integrityChecks);
        logger.info(String.format("Registered %s in the contentIntegrity service ", integrityCheck));
    }

    public void unregisterIntegrityCheck(ContentIntegrityCheck integrityCheck) {
        integrityChecks.remove(integrityCheck);
        logger.info(String.format("Unregistered %s in the contentIntegrity service ", integrityCheck));
    }

    public List<ContentIntegrityError> validateIntegrity(String path, String workspace) {   // TODO maybe need to prevent concurrent executions
        final JCRSessionWrapper session;
        try {
            session = JCRSessionFactory.getInstance().getCurrentSystemSession(workspace, null, null);
        } catch (RepositoryException e) {
            logger.error(String.format("Impossible to get the session for workspace %s", workspace), e);
            return null;
        }
        final JCRNodeWrapper node;
        final List<ContentIntegrityError> errors = new ArrayList<>();
        try {
            node = session.getNode(path);
            logger.info(String.format("Starting to check the integrity under %s in the workspace %s", path, workspace));
            final long start = System.currentTimeMillis();
            validateIntegrity(node, errors);
            logger.info(String.format("Integrity checked under %s in the workspace %s in %s", path, workspace, DateUtils.formatDurationWords(System.currentTimeMillis() - start)));
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        return errors;
    }

    private void validateIntegrity(Node node, List<ContentIntegrityError> errors) {
        // TODO add a mechanism to stop
        for (ContentIntegrityCheck integrityCheck : integrityChecks)
            if (integrityCheck.areConditionsMatched(node)) {
                if (logger.isDebugEnabled())
                    logger.debug(String.format("Running %s on %s", integrityCheck.getClass().getName(), node));
                final ContentIntegrityError error = integrityCheck.checkIntegrityBeforeChildren(node);
                if (error != null) errors.add(error);
            } else if (logger.isDebugEnabled())
                logger.debug(String.format("Skipping %s on %s", integrityCheck.getClass().getName(), node));
        try {
            for (NodeIterator it = node.getNodes(); it.hasNext(); ) {
                final Node child = (Node) it.next();
                validateIntegrity(child, errors);
            }
        } catch (RepositoryException e) {
            String ws = "unknown";
            try {
                ws = node.getSession().getWorkspace().getName();
            } catch (RepositoryException e1) {
                logger.error("", e1);
            }
            logger.error(String.format("An error occured while iterating over the children of the node %s in the workspace %s",
                    node, ws), e);
        }
        for (ContentIntegrityCheck integrityCheck : integrityChecks)
            if (integrityCheck.areConditionsMatched(node)) {
                final ContentIntegrityError error = integrityCheck.checkIntegrityAfterChildren(node);
                if (error != null) errors.add(error);
            }
    }

    public void printIntegrityChecksList() {
        logger.info("Integrity checks:");
        for (ContentIntegrityCheck integrityCheck : integrityChecks)
            logger.info(String.format("   %s", integrityCheck));

    }

    /**
     * What's the best strategy?
     * - iterating over the checks and for each, iterating over the tree
     * - iterating over the tree and for each node, iterating over the checks
     */
}
