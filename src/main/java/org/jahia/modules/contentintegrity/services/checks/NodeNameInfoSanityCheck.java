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
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.JAHIAMIX_NODENAMEINFO,
        ContentIntegrityCheck.ENABLED + "=false"
})
public class NodeNameInfoSanityCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.SupportsIntegrityErrorFix {

    private static final Logger logger = LoggerFactory.getLogger(NodeNameInfoSanityCheck.class);
    public static final ContentIntegrityErrorType INVALID_FULLPATH = createErrorType("INVALID_FULLPATH", "Unexpected property value");
    public static final ContentIntegrityErrorType MISSING_NODENAME = createErrorType("MISSING_NODENAME", "Missing property");
    public static final ContentIntegrityErrorType INVALID_NODENAME = createErrorType("INVALID_NODENAME", "Unexpected property value");

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        final ContentIntegrityErrorList errors = createEmptyErrorsList();

        try {
            validateFullPathProperty(node, errors);
            validateNodeNameProperty(node, errors);
        } catch (RepositoryException e) {
            errors.addError(createFrameworkError(node, e));
        }

        return errors;
    }

    /*
     * j:fullpath is deprecated and might be missing, but when it is defined it has to hold the path of the node,
     * in both workspaces (from the clean-recursive script).
     */
    private void validateFullPathProperty(JCRNodeWrapper node, ContentIntegrityErrorList errors) throws RepositoryException {
        if (!node.hasProperty(Constants.FULLPATH)) return;
        validateValue(node, Constants.FULLPATH, node.getPath(), INVALID_FULLPATH, errors);
    }

    private void validateNodeNameProperty(JCRNodeWrapper node, ContentIntegrityErrorList errors) throws RepositoryException {
        if (!node.hasProperty(Constants.NODENAME)) {
            errors.addError(createError(node, MISSING_NODENAME)
                    .addExtraInfo("property-name", Constants.NODENAME));
            return;
        }
        validateValue(node, Constants.NODENAME, node.getName(), INVALID_NODENAME, errors);
    }

    private void validateValue(JCRNodeWrapper node, String propertyName, String expectedValue,
                               ContentIntegrityErrorType errorType, ContentIntegrityErrorList errors) throws RepositoryException {
        final String value = node.getPropertyAsString(propertyName);
        if (!StringUtils.equals(value, expectedValue)) {
            errors.addError(createError(node, errorType)
                    .addExtraInfo("property-name", propertyName)
                    .addExtraInfo("property-value", value, true)
                    .addExtraInfo("expected-property-value", expectedValue, true));
        }
    }

    /*
     * From the jcr-scripts fixes: j:nodename gets the name of the node, and j:fullpath its path.
     */
    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException {
        final ContentIntegrityErrorType errorType = error.getErrorType();
        final String propertyName;
        final String value;
        if (errorType.equals(MISSING_NODENAME) || errorType.equals(INVALID_NODENAME)) {
            propertyName = Constants.NODENAME;
            value = node.getName();
        } else if (errorType.equals(INVALID_FULLPATH)) {
            propertyName = Constants.FULLPATH;
            value = node.getPath();
        } else {
            return false;
        }
        RepairUtils.runWithListenersDisabled(() -> {
            node.setProperty(propertyName, value);
            node.saveSession();
        });
        return true;
    }
}
