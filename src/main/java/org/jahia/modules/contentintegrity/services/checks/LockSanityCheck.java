package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.HashSet;

import static org.jahia.api.Constants.JCR_LOCKISDEEP;
import static org.jahia.api.Constants.JCR_LOCKOWNER;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_WS + "=" + Constants.EDIT_WORKSPACE,
        ContentIntegrityCheck.ExecutionCondition.APPLY_IF_HAS_PROP + "=j:lockTypes,j:locktoken," + JCR_LOCKISDEEP + "," + JCR_LOCKOWNER
})
public class LockSanityCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(LockSanityCheck.class);

    private enum ErrorType {INCONSISTENT_LOCK, DELETION_LOCK_ON_I18N}
    private final HashSet<String> lockRelatedProperties = new HashSet<>();

    @Override
    protected void activate(ComponentContext context) {
        super.activate(context);
        init();
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

            for (JCRValueWrapper value : node.getProperty("j:lockTypes").getValues()) {
                if (!StringUtils.equals(value.getString(), " deletion :deletion")) continue;
                if (!node.getParent().isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION)) {
                    errors.addError(createError(node, "Deletion lock remaining on a translation node")
                            .setErrorType(ErrorType.DELETION_LOCK_ON_I18N));
                }
            }

        } catch (RepositoryException e) {
            logger.error(String.format("Error while checking the node %s", node.getPath()), e);
        }
    }

    private void init() {
        lockRelatedProperties.clear();
        lockRelatedProperties.add("j:lockTypes");
        lockRelatedProperties.add("j:locktoken");
        lockRelatedProperties.add(JCR_LOCKISDEEP);
        lockRelatedProperties.add(JCR_LOCKOWNER);
    }
}
