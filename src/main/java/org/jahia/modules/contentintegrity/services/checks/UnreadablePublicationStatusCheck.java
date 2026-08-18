package org.jahia.modules.contentintegrity.services.checks;

import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import static org.jahia.modules.contentintegrity.services.impl.Constants.JAHIA_MIX_I18N;
import static org.jahia.modules.contentintegrity.services.impl.Constants.TRANSLATION_NODE_PREFIX;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + JAHIA_MIX_I18N
})
public class UnreadablePublicationStatusCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(UnreadablePublicationStatusCheck.class);

    public static final ContentIntegrityErrorType UNREADABLE_PUBLICATION_STATUS =
            createErrorType("UNREADABLE_PUBLICATION_STATUS",
                    "Issue when reading workflow and delete status of node");

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        try {
            final ContentIntegrityErrorList errors = createEmptyErrorsList();
            final String nodeId = node.getIdentifier();
            final NodeIterator translationNodes = node.getNodes(TRANSLATION_NODE_PREFIX + "*");

            while (translationNodes.hasNext()) {
                final Node translationNode = translationNodes.nextNode();
                final String locale = JCRUtils.getTranslationNodeLocale(translationNode);
                if (locale == null) continue;

                final JCRSessionWrapper localizedSession = JCRUtils.getSystemSession(node.getSession(), locale);
                if (localizedSession == null) continue;

                try {
                    localizedSession.getNodeByIdentifier(nodeId);
                } catch (ItemNotFoundException e) {
                    errors.addError(createError(node, locale, UNREADABLE_PUBLICATION_STATUS)
                            .addExtraInfo("locale", locale, true));
                }
            }

            return errors;
        } catch (RepositoryException e) {
            return createSingleError(createFrameworkError(node, e));
        }
    }
}
