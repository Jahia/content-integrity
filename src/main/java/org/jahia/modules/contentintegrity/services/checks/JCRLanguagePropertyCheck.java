package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.modules.contentintegrity.services.util.RepairUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

import static org.jahia.modules.contentintegrity.services.impl.Constants.JCR_LANGUAGE;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.JAHIANT_TRANSLATION
})
public class JCRLanguagePropertyCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.SupportsIntegrityErrorFix {

    private static final Logger logger = LoggerFactory.getLogger(JCRLanguagePropertyCheck.class);

    public static final ContentIntegrityErrorType MISSING_JCR_LANGUAGE_PROP = createErrorType("MISSING_JCR_LANGUAGE_PROP", String.format("The %s property is missing", JCR_LANGUAGE), true);
    public static final ContentIntegrityErrorType INCONSISTENT_JCR_LANGUAGE_PROP = createErrorType("INCONSISTENT_JCR_LANGUAGE_PROP", String.format("The value of the property %s is inconsistent with the node name", JCR_LANGUAGE), true);

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        try {
            if (!node.hasProperty(JCR_LANGUAGE)) {
                return createSingleError(createError(node, JCRUtils.getTranslationNodeLocaleFromNodeName(node), MISSING_JCR_LANGUAGE_PROP)
                        .addExtraInfo("parent-node-type", node.getParent().getPrimaryNodeTypeName()));
            }
            final String langPropValue = node.getProperty(JCR_LANGUAGE).getString();
            if (!node.getName().equals(Constants.TRANSLATION_NODE_PREFIX.concat(langPropValue))) {
                return createSingleError(createError(node, JCRUtils.getTranslationNodeLocaleFromNodeName(node), INCONSISTENT_JCR_LANGUAGE_PROP)
                        .addExtraInfo("parent-node-type", node.getParent().getPrimaryNodeTypeName())
                        .addExtraInfo("jcr-language-prop-value", langPropValue));
            }
        } catch (RepositoryException e) {
            return createSingleError(createFrameworkError(node, e));
        }
        return null;
    }

    /*
     * From the jcr-scripts fix: jcr:language gets the language of the translation node, read from its name.
     */
    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException {
        if (!error.getErrorType().equals(MISSING_JCR_LANGUAGE_PROP) && !error.getErrorType().equals(INCONSISTENT_JCR_LANGUAGE_PROP)) return false;
        final JCRNodeWrapper translation = RepairUtils.getErrorTarget(node, error);
        final String language = JCRUtils.getTranslationNodeLocaleFromNodeName(translation);
        if (StringUtils.isBlank(language)) return false;
        RepairUtils.runWithListenersDisabled(() -> {
            translation.setProperty(Constants.JCR_LANGUAGE, language);
            translation.saveSession();
        });
        return true;
    }
}
