package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

import static org.jahia.api.Constants.JCR_LANGUAGE;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.JAHIANT_TRANSLATION
})
public class JCRLanguagePropertyCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(JCRLanguagePropertyCheck.class);

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        if (logger.isDebugEnabled()) logger.debug(String.format("Checking %s", node));
        try {
            if (!node.hasProperty(JCR_LANGUAGE)) {
                final String msg = String.format("The %s property is missing", JCR_LANGUAGE);
                return createSingleError(createError(node, getTranslationNodeLocaleFromNodeName(node), msg));
            }
            if (!node.getName().equals("j:translation_".concat(node.getProperty(JCR_LANGUAGE).getString()))) {
                final String msg = String.format("The value of the property %s is inconsistent with the node name", JCR_LANGUAGE);
                return createSingleError(createError(node, getTranslationNodeLocaleFromNodeName(node), msg));
            }
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        return null;
    }
}
