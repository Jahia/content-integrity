package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.function.Function;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ENABLED + ":Boolean=false",
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.JAHIAMIX_NODENAMEINFO
})
public class NodeNameInfoSanityCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(NodeNameInfoSanityCheck.class);

    private enum ErrorType {MISSING_FULLPATH, INVALID_FULLPATH, MISSING_NODENAME, INVALID_NODENAME}

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        final ContentIntegrityErrorList errors = createEmptyErrorsList();

        try {
            validateStringProperty(node, Constants.FULLPATH, JCRNodeWrapper::getPath, errors, ErrorType.MISSING_FULLPATH, ErrorType.INVALID_FULLPATH);
            validateStringProperty(node, Constants.NODENAME, JCRNodeWrapper::getName, errors, ErrorType.MISSING_NODENAME, ErrorType.INVALID_NODENAME);
        } catch (RepositoryException e) {
            logger.error("", e);
        }

        return errors;
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
                    .addExtraInfo("property-value", value)
                    .addExtraInfo("expected-property-value", expectedValue));
        }
    }
}
