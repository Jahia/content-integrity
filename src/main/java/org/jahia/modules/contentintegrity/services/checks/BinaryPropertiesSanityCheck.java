package org.jahia.modules.contentintegrity.services.checks;

import org.apache.jackrabbit.core.data.DataStoreException;
import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;

@Component(service = ContentIntegrityCheck.class, immediate = true)
public class BinaryPropertiesSanityCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(BinaryPropertiesSanityCheck.class);

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        final ContentIntegrityErrorList errors = createEmptyErrorsList();
        try {
            final PropertyIterator properties = node.getProperties();

            while (properties.hasNext()) {
                final Property property = properties.nextProperty();
                if (property.getType() != PropertyType.BINARY) continue;
                try {
                    property.getBinary().getStream();
                } catch (DataStoreException dse) {
                    final String locale = node.isNodeType(Constants.JAHIANT_TRANSLATION) ?
                            getTranslationNodeLocale(node) : null;
                    errors.addError(createError(node, locale, "Invalid binary property")
                            .addExtraInfo("property-name", property.getName())
                            .addExtraInfo("property-path", property.getPath()));
                }
            }
        } catch (RepositoryException e) {
            logger.error("Impossible to check the node " + node.getPath(), e);
        }
        return errors;
    }
}
