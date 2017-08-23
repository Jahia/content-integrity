package org.jahia.modules.verifyintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.verifyintegrity.services.AbstractContentIntegrityCheck;
import org.jahia.modules.verifyintegrity.services.ContentIntegrityError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import static org.jahia.api.Constants.JCR_LANGUAGE;

public class JCRLanguagePropertyCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(JCRLanguagePropertyCheck.class);

    @Override
    public ContentIntegrityError checkIntegrityBeforeChildren(Node node) {
        if (logger.isDebugEnabled()) logger.debug(String.format("Checking %s", node));
        try {
            if (!node.hasProperty(JCR_LANGUAGE)) {
                final String msg = String.format("The %s property is missing", JCR_LANGUAGE);
                return ContentIntegrityError.createError(node, StringUtils.substringAfterLast(node.getName(), "_"), msg, this);
            }
            if (!node.getName().equals("j:translation_" + node.getProperty(JCR_LANGUAGE).getString())) {
                final String msg = String.format("The value of the property %s is inconsistent with the node name", JCR_LANGUAGE);
                return ContentIntegrityError.createError(node, StringUtils.substringAfterLast(node.getName(), "_"), msg, this);
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
}
