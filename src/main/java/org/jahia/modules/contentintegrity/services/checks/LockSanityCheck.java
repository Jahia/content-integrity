package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.jahia.modules.contentintegrity.services.util.RepairUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.HashSet;

import static org.jahia.modules.contentintegrity.services.impl.Constants.JCR_LOCKISDEEP;
import static org.jahia.modules.contentintegrity.services.impl.Constants.JCR_LOCKOWNER;
import static org.jahia.modules.contentintegrity.services.impl.Constants.J_LOCKTOKEN;
import static org.jahia.modules.contentintegrity.services.impl.Constants.J_LOCK_TYPES;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_WS + "=" + Constants.EDIT_WORKSPACE,
        ContentIntegrityCheck.ExecutionCondition.APPLY_IF_HAS_PROP + "=" + J_LOCK_TYPES + "," + J_LOCKTOKEN + "," + JCR_LOCKISDEEP + "," + JCR_LOCKOWNER
})
public class LockSanityCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.SupportsIntegrityErrorFix {

    private static final Logger logger = LoggerFactory.getLogger(LockSanityCheck.class);

    public static final ContentIntegrityErrorType INCONSISTENT_LOCK = createErrorType("INCONSISTENT_LOCK", "Missing properties on a locked node");
    public static final ContentIntegrityErrorType DELETION_LOCK_ON_I18N = createErrorType("DELETION_LOCK_ON_I18N", "Deletion lock remaining on a translation node");

    private final HashSet<String> lockRelatedProperties = new HashSet<>();

    @Override
    protected void activateInternal(ComponentContext context) {
        lockRelatedProperties.clear();
        lockRelatedProperties.add(J_LOCK_TYPES);
        lockRelatedProperties.add(J_LOCKTOKEN);
        lockRelatedProperties.add(JCR_LOCKISDEEP);
        lockRelatedProperties.add(JCR_LOCKOWNER);
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        final ContentIntegrityErrorList errors = createEmptyErrorsList();

        HashSet<String> missingProps = null;
        for (String property : lockRelatedProperties) {
            try {
                if (!node.hasProperty(property)) {
                    if (missingProps == null) missingProps = new HashSet<>();
                    missingProps.add(property);
                }
            } catch (RepositoryException e) {
                errors.addError(createFrameworkError(node, e));
            }
        }
        if (missingProps != null && !missingProps.isEmpty()) {
            final ContentIntegrityError error = createError(node, INCONSISTENT_LOCK)
                    .addExtraInfo("missing-properties", missingProps);
            errors.addError(error);
        }

        checkPartialMarkedForDeletionLock(node, errors);

        return errors;
    }

    private void checkPartialMarkedForDeletionLock(JCRNodeWrapper node, ContentIntegrityErrorList errors) {
        try {
            if (!node.isNodeType(Constants.JAHIANT_TRANSLATION)) return;
            if (!node.hasProperty(J_LOCK_TYPES)) return;

            boolean isLockedForDeletion = false;
            for (JCRValueWrapper value : node.getProperty(J_LOCK_TYPES).getValues()) {
                if (StringUtils.equals(value.getString(), Constants.LOCK_TYPE_DELETION)) {
                    isLockedForDeletion = true;
                    break;
                }
            }
            if (!isLockedForDeletion) return;

            if (!node.getParent().isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION)) {
                errors.addError(createError(node, DELETION_LOCK_ON_I18N));
            }

        } catch (RepositoryException e) {
            errors.addError(createFrameworkError(node, String.format("Error while checking the node %s", node.getPath()), e));
        }
    }

    private static final String[] LOCK_PROPERTIES = {JCR_LOCKISDEEP, JCR_LOCKOWNER, J_LOCK_TYPES, J_LOCKTOKEN,
            "j:deletionMessage", "j:deletionDate", "j:deletionUser"};

    /*
     * From the jcr-scripts fixes: the lock is set and released, so that the platform cleans what it can, then the
     * remaining lock and deletion properties are removed. A deletion lock left on a translation node is cleaned
     * the same way, after marking and unmarking the node for deletion.
     */
    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException {
        if (error.getErrorType().equals(INCONSISTENT_LOCK)) {
            RepairUtils.runWithListenersDisabled(() -> {
                if (node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION_ROOT)) node.unmarkForDeletion();
                try {
                    node.lock(true, false);
                } catch (RepositoryException e) {
                    logger.debug("Impossible to lock {}", node.getPath(), e);
                }
                try {
                    node.unlock();
                } catch (RepositoryException e) {
                    logger.debug("Impossible to unlock {}", node.getPath(), e);
                }
                removeLockProperties(node);
            });
            return true;
        }
        if (error.getErrorType().equals(DELETION_LOCK_ON_I18N)) {
            RepairUtils.runWithListenersDisabled(() -> {
                RepairUtils.markThenUnmarkForDeletion(node, getName());
                removeLockProperties(node);
            });
            return true;
        }
        return false;
    }

    private void removeLockProperties(JCRNodeWrapper node) throws RepositoryException {
        for (String property : LOCK_PROPERTIES) {
            if (node.hasProperty(property)) node.getProperty(property).remove();
        }
        node.saveSession();
    }
}
