package org.jahia.modules.verifyintegrity.services.checks;

import org.jahia.modules.verifyintegrity.services.AbstractContentIntegrityCheck;
import org.jahia.modules.verifyintegrity.services.ContentIntegrityError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.Node;
import javax.jcr.RepositoryException;

public class MarkForDeletionCheck extends AbstractContentIntegrityCheck {

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
                    break;
                }
                if (!parent.isNodeType("jmix:markedForDeletion")) {
                    isConsistent = false;
                    break;
                }
                if (parent.isNodeType("jmix:markedForDeletionRoot")) break;
            }
            if (!isConsistent) {
                return ContentIntegrityError.createError(node, null, "The node is flagged as deleted, but the root of the deletion can't be found", this);
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
