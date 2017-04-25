package org.jahia.modules.verifyintegrity.services;


import org.jahia.api.Constants;
import org.jahia.exceptions.JahiaException;
import org.jahia.exceptions.JahiaInitializationException;
import org.jahia.services.JahiaService;
import org.jahia.services.content.JCRNodeWrapper;
import org.slf4j.Logger;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContentIntegrityService extends JahiaService {
    private static Logger logger = org.slf4j.LoggerFactory.getLogger(ContentIntegrityService.class);

    private List<ContentIntegrityCheck> integrityChecks = new ArrayList<>();

    @Override
    public void start() throws JahiaInitializationException {
        //TODO: review me, I'm generated
    }

    @Override
    public void stop() throws JahiaException {
        //TODO: review me, I'm generated
    }

    public void registerIntegrityCheck(ContentIntegrityCheck integrityCheck) {
        integrityChecks.add(integrityCheck);
        Collections.sort(integrityChecks);
        logger.info(String.format("Registered %s in the contentIntegrity service "));
    }

    public void unregisterIntegrityCheck(ContentIntegrityCheck integrityCheck) {
        integrityChecks.remove(integrityCheck);
        logger.info(String.format("Unregistered %s in the contentIntegrity service "));
    }

    public void validateIntegrity(JCRNodeWrapper rootNode) {
        for (ContentIntegrityCheck integrityCheck : integrityChecks) validateIntegrity(rootNode, integrityCheck);

    }

    public void validateIntegrity(JCRNodeWrapper rootNode, ContentIntegrityCheck integrityCheck) {
        integrityCheck.checkIntegrity(rootNode);
        try {
            for (JCRNodeWrapper child : rootNode.getNodes()) {
                if (child.isNodeType(Constants.JAHIANT_CONTENT)) validateIntegrity(child, integrityCheck);
            }
        } catch (RepositoryException e) {
            logger.error(String.format("An error occured while running %s on the node %s", integrityCheck, rootNode.getPath()), e);
        }

    }
}
