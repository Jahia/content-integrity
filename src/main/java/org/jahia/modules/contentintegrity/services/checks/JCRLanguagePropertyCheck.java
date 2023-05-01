package org.jahia.modules.contentintegrity.services.checks;

import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

import static org.jahia.modules.contentintegrity.services.impl.Constants.JCR_LANGUAGE;

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
                return createSingleError(createError(node, JCRUtils.getTranslationNodeLocaleFromNodeName(node), msg));
            }
            final String langPropValue = node.getProperty(JCR_LANGUAGE).getString();
            if (!node.getName().equals(Constants.TRANSLATION_NODE_PREFIX.concat(langPropValue))) {
                final String msg = String.format("The value of the property %s is inconsistent with the node name", JCR_LANGUAGE);
                return createSingleError(createError(node, JCRUtils.getTranslationNodeLocaleFromNodeName(node), msg)
                        .addExtraInfo("jcr-language-prop-value", langPropValue));
            }
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        return null;
    }
}
