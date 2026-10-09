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

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.JAHIAMIX_MARKED_FOR_DELETION
})
public class MarkForDeletionCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.SupportsIntegrityErrorFix {

    public static final ContentIntegrityErrorType NO_ROOT_DELETION = createErrorType("NO_ROOT_DELETION", "The node is flagged as deleted, but the root of the deletion can't be found", true);
    public static final ContentIntegrityErrorType DELETION_MARK_IN_LIVE = createErrorType("DELETION_MARK_IN_LIVE", "The node is flagged as deleted in the live workspace", true);
    public static final ContentIntegrityErrorType DELETION_MARK_UNDER_USERS = createErrorType("DELETION_MARK_UNDER_USERS", "A node under /users is flagged as deleted", true);
    private static final String USERS_SUBTREE_PATH_PREFIX = "/users/";

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        // A deletion is marked in the default workspace and published as a removal: the mark never exists in live
        if (JCRUtils.isInLiveWorkspace(node)) {
            return createSingleError(createError(node, DELETION_MARK_IN_LIVE));
        }
        // The users are auto-published: a deletion mark is never expected under /users
        if (StringUtils.startsWith(node.getPath(), USERS_SUBTREE_PATH_PREFIX)) {
            return createSingleError(createError(node, DELETION_MARK_UNDER_USERS));
        }
        if (JCRUtils.runJcrSupplierCallBack(() -> node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION_ROOT), false)) {
            return null;
        }

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
            return createSingleError(createFrameworkError(node, e));
        }
        return null;
    }

    /*
     * From the jcr-scripts fixes: the node is unmarked for deletion. Marking it again first rebuilds a consistent
     * deletion state, which the unmarking then removes.
     */
    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException {
        final ContentIntegrityErrorType errorType = error.getErrorType();
        if (!errorType.equals(NO_ROOT_DELETION) && !errorType.equals(DELETION_MARK_IN_LIVE) && !errorType.equals(DELETION_MARK_UNDER_USERS)) return false;
        RepairUtils.runWithListenersDisabled(() -> {
            RepairUtils.markThenUnmarkForDeletion(node, getName());
            node.saveSession();
        });
        return true;
    }
}
