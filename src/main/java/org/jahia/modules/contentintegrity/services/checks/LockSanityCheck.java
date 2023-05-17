package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.Constants;
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
public class LockSanityCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(LockSanityCheck.class);

    private enum ErrorType {INCONSISTENT_LOCK, DELETION_LOCK_ON_I18N}
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
                logger.error("", e);
            }
        }
        if (missingProps != null && !missingProps.isEmpty()) {
            final ContentIntegrityError error = createError(node, "Missing properties on a locked node")
                    .addExtraInfo("missing-properties", missingProps)
                    .setErrorType(ErrorType.INCONSISTENT_LOCK);
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
                errors.addError(createError(node, "Deletion lock remaining on a translation node")
                        .setErrorType(ErrorType.DELETION_LOCK_ON_I18N));
            }

        } catch (RepositoryException e) {
            logger.error(String.format("Error while checking the node %s", node.getPath()), e);
        }
    }
}
