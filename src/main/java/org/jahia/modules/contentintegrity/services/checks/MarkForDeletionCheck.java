package org.jahia.modules.contentintegrity.services.checks;

import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_WS + "=" + Constants.EDIT_WORKSPACE,
        ContentIntegrityCheck.ExecutionCondition.SKIP_ON_NT + "=" + Constants.JAHIAMIX_MARKED_FOR_DELETION_ROOT,
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.JAHIAMIX_MARKED_FOR_DELETION
})
public class MarkForDeletionCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(MarkForDeletionCheck.class);
    public static final ContentIntegrityErrorType NO_ROOT_DELETION = createErrorType("NO_ROOT_DELETION", "The node is flagged as deleted, but the root of the deletion can't be found", true);

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        boolean isConsistent = true;
        JCRNodeWrapper parent = node;
        try {
            while (true) {
                try {
                    parent = parent.getParent();
                } catch (ItemNotFoundException e) {
                    isConsistent = false;
                    break;
                }
                if (!parent.isNodeType("jmix:markedForDeletion")) {
                    isConsistent = false;
                    break;
                }
                if (parent.isNodeType("jmix:markedForDeletionRoot")) break;
            }
            if (!isConsistent) {
                return createSingleError(createError(node, NO_ROOT_DELETION));
            }
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        return null;
    }
}
