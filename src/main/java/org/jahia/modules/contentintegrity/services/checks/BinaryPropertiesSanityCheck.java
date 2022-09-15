package org.jahia.modules.contentintegrity.services.checks;

import org.apache.jackrabbit.core.data.DataStoreException;
import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.ContentIntegrityCheckConfigurationImpl;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;

import static org.jahia.modules.contentintegrity.services.impl.ContentIntegrityCheckConfigurationImpl.BOOLEAN_PARSER;

@Component(service = ContentIntegrityCheck.class, immediate = true)
public class BinaryPropertiesSanityCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.IsConfigurable {

    private static final Logger logger = LoggerFactory.getLogger(BinaryPropertiesSanityCheck.class);
    private static final String DOWNLOAD_STREAM = "download-stream";

    private final ContentIntegrityCheckConfiguration configurations;

    public BinaryPropertiesSanityCheck() {
        configurations = new ContentIntegrityCheckConfigurationImpl();
        getConfigurations().declareDefaultParameter(DOWNLOAD_STREAM, Boolean.FALSE, BOOLEAN_PARSER, "If true, each binary property is validated by reading its value as a stream (time consuming operation). Otherwise, only the length of the binary is read");
    }

    @Override
    public ContentIntegrityCheckConfiguration getConfigurations() {
        return configurations;
    }

    private boolean downloadStream() {
        return (boolean) configurations.getParameter(DOWNLOAD_STREAM);
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        final ContentIntegrityErrorList errors = createEmptyErrorsList();
        try {
            final PropertyIterator properties = node.getProperties();

            Property property;
            boolean isValid;
            while (properties.hasNext()) {
                property = properties.nextProperty();
                if (property.getType() != PropertyType.BINARY) continue;
                if (downloadStream()) {
                    isValid = true;
                    try {
                        property.getBinary().getStream();
                    } catch (DataStoreException dse) {
                        isValid = false;
                    }
                } else {
                    isValid = property.getBinary().getSize() >= 0;
                }
                if (!isValid) {
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
