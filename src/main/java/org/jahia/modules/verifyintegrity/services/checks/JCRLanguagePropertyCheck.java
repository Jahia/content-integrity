package org.jahia.modules.verifyintegrity.services.checks;

import org.jahia.modules.verifyintegrity.services.ContentIntegrityCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import static org.jahia.api.Constants.JCR_LANGUAGE;

public class JCRLanguagePropertyCheck extends ContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(JCRLanguagePropertyCheck.class);


    @Override
    public void checkIntegrityBeforeChildren(Node node) {
        if (logger.isDebugEnabled()) logger.debug(String.format("Checking %s", node));
        try {
            if (!node.hasProperty(JCR_LANGUAGE)) {
                logger.error(String.format("The %s property is missing on node %s", JCR_LANGUAGE, node));
                return;
            }
            if (!node.getName().equals("j:translation_" + node.getProperty(JCR_LANGUAGE).getString())) {
                logger.error(String.format("The value of the property %s is inconsistent with the node name on node %s", JCR_LANGUAGE, node));
            }
        } catch (RepositoryException e) {
            logger.error("", e);
        }
    }

    @Override
    public void checkIntegrityAfterChildren(Node node) {
    }
}
