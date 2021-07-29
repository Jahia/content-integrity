package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.nodetype.PropertyDefinition;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ENABLED + ":Boolean=false"
})
public class UndeclaredPropertiesSanityCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(UndeclaredPropertiesSanityCheck.class);

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {

        final ContentIntegrityErrorList errors = createEmptyErrorsList();
        try {
            final boolean isI18n = node.isNodeType(Constants.JAHIANT_TRANSLATION);
            final PropertyIterator properties = node.getRealNode().getProperties();
            while (properties.hasNext()) {
                final Property property = properties.nextProperty();
                boolean isInvalid;
                try {
                    final PropertyDefinition propertyDefinition = property.getDefinition();
                    // if the node is not a translation node, and if the previous line has not thrown an exception, then the property is valid
                    isInvalid = isI18n && StringUtils.equals(property.getName(), "*") &&
                            StringUtils.equals(propertyDefinition.getDeclaringNodeType().getName(), Constants.JAHIANT_TRANSLATION);
                } catch (RepositoryException e) {
                    isInvalid = true;
                }
                if (isInvalid) {
                    final ContentIntegrityError error = createError(node, "Undeclared property").
                            addExtraInfo("property-name", property.getName()).
                            addExtraInfo("internationalized", isI18n);
                    if (isI18n)
                        error.addExtraInfo("language", getTranslationNodeLocale(node));
                    errors.addError(error);
                }
            }

            return errors;
        } catch (RepositoryException e) {
            logger.error("", e);
            return null;
        }
    }
}
