package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.ContentIntegrityCheckConfigurationImpl;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.function.Function;

import static org.jahia.modules.contentintegrity.services.impl.ContentIntegrityCheckConfigurationImpl.BOOLEAN_PARSER;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.JAHIAMIX_NODENAMEINFO
})
public class NodeNameInfoSanityCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.IsConfigurable {

    private static final Logger logger = LoggerFactory.getLogger(NodeNameInfoSanityCheck.class);
    private static final String CHECK_FULLPATH = "check-fullpath";

    private final ContentIntegrityCheckConfiguration configurations;

    public NodeNameInfoSanityCheck() {
        configurations = new ContentIntegrityCheckConfigurationImpl();
        configurations.declareDefaultParameter(CHECK_FULLPATH, Boolean.FALSE, BOOLEAN_PARSER, String.format("If true, the property %s is checked. This property is deprecated, but might be used in your own code. You should enable its check only in this case, and plan a refactoring of your code", Constants.FULLPATH));
    }

    private enum ErrorType {MISSING_FULLPATH, UNEXPECTED_FULLPATH, INVALID_FULLPATH, MISSING_NODENAME, INVALID_NODENAME;}

    @Override
    public ContentIntegrityCheckConfiguration getConfigurations() {
        return configurations;
    }

    private boolean checkFullPath() {
        return (boolean) getConfigurations().getParameter(CHECK_FULLPATH);
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        final ContentIntegrityErrorList errors = createEmptyErrorsList();

        try {
            validateFullPathProperty(node, errors);
            validateStringProperty(node, Constants.NODENAME, JCRNodeWrapper::getName, errors, ErrorType.MISSING_NODENAME, ErrorType.INVALID_NODENAME);
        } catch (RepositoryException e) {
            logger.error("", e);
        }

        return errors;
    }

    private void validateFullPathProperty(JCRNodeWrapper node, ContentIntegrityErrorList errors) throws RepositoryException {
        if (!checkFullPath()) return;

        if (JCRUtils.isInDefaultWorkspace(node)) {
            if (JCRUtils.isNeverPublished(node)) {
                ensureMissingFullpathProperty(node, errors);
            } else if (!JCRUtils.hasPendingModifications(node)) {
                validateConsistentFullpathProperty(node, errors);
            } else {
                // In this case, we expect the property to hold the path of the node in live, since it was the path the last time the node was published
                validateStringProperty(node, Constants.FULLPATH, defaultNode -> {
                    try {
                        return JCRUtils.getSystemSession(Constants.LIVE_WORKSPACE).getNodeByUUID(defaultNode.getIdentifier()).getPath();
                    } catch (RepositoryException e) {
                        logger.error("", e);
                        return null; // TODO : this should never happen, but if it does, the resulting behavior in uncontrolled
                    }
                }, errors, ErrorType.MISSING_FULLPATH, ErrorType.INVALID_FULLPATH);
            }
        } else {
            if (JCRUtils.isUGCNode(node)) {
                ensureMissingFullpathProperty(node, errors);
            } else {
                validateConsistentFullpathProperty(node, errors);
            }
        }
    }

    private void ensureMissingFullpathProperty(JCRNodeWrapper node, ContentIntegrityErrorList errors) throws RepositoryException {
        // TODO : the message should be different for a never published node in default and a UGC node in live
        if (node.hasProperty(Constants.FULLPATH)) {
            errors.addError(createError(node, String.format("Unexpected %s property", Constants.FULLPATH))
                    .setErrorType(ErrorType.UNEXPECTED_FULLPATH)
                    .setExtraMsg("The property is expected to be missing on never published nodes in the default workspace and on UGC nodes in the live workspace"));
        }
    }

    private void validateConsistentFullpathProperty (JCRNodeWrapper node, ContentIntegrityErrorList errors) throws RepositoryException {
        validateStringProperty(node, Constants.FULLPATH, JCRNodeWrapper::getPath, errors, ErrorType.MISSING_FULLPATH, ErrorType.INVALID_FULLPATH);
    }

    private void validateStringProperty(JCRNodeWrapper node, String propertyName,
                                        Function<JCRNodeWrapper, String> expectedValueGenerator, ContentIntegrityErrorList errors,
                                        ErrorType missingPropertyErrorType, ErrorType invalidPropertyValueErrorType) throws RepositoryException {
        if (!node.hasProperty(propertyName)) {
            errors.addError(createError(node, "Missing property")
                    .setErrorType(missingPropertyErrorType)
                    .addExtraInfo("property-name", propertyName));
            return;
        }
        final String value = node.getPropertyAsString(propertyName);
        final String expectedValue = expectedValueGenerator.apply(node);
        if (!StringUtils.equals(value, expectedValue)) {
            errors.addError(createError(node, "Unexpected property value")
                    .setErrorType(invalidPropertyValueErrorType)
                    .addExtraInfo("property-name", propertyName)
                    .addExtraInfo("property-value", value, true)
                    .addExtraInfo("expected-property-value", expectedValue, true));
        }
    }
}
