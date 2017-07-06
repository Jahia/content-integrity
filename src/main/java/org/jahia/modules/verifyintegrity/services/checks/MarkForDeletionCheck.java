package org.jahia.modules.verifyintegrity.services.checks;

import org.jahia.modules.verifyintegrity.services.ContentIntegrityCheck;
import org.jahia.modules.verifyintegrity.services.ContentIntegrityError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

public class MarkForDeletionCheck extends ContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(MarkForDeletionCheck.class);

    @Override
    public ContentIntegrityError checkIntegrityBeforeChildren(Node node) {
        boolean isConsistent = true;
        Node parent = node;
        try {
            while (true) {
                try {
                    parent = parent.getParent();
                } catch (ItemNotFoundException e) {
                    isConsistent = false;
                }
                if (!parent.isNodeType("jmix:markedForDeletion")) {
                    isConsistent = false;
                    break;
                }
                if (parent.isNodeType("jmix:markedForDeletionRoot")) break;
            }
            if (!isConsistent) {
                return ContentIntegrityError.createError(node, null, "The node is flagged as deleted, but the root of the deletion can't be found", this.getClass().getSimpleName());
            }
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        return null;
    }

    @Override
    public ContentIntegrityError checkIntegrityAfterChildren(Node node) {
        return null;  //TODO: review me, I'm generated
    }
}
