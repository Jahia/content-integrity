package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.io.IOUtils;
import org.apache.jackrabbit.core.data.DataStoreException;
import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.ContentIntegrityCheckConfigurationImpl;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Binary;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;

import java.io.IOException;
import java.io.InputStream;

import static org.jahia.modules.contentintegrity.services.impl.ContentIntegrityCheckConfigurationImpl.BOOLEAN_PARSER;

@Component(service = ContentIntegrityCheck.class, immediate = true)
public class BinaryPropertiesSanityCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.IsConfigurable {

    private static final Logger logger = LoggerFactory.getLogger(BinaryPropertiesSanityCheck.class);
    private static final String DOWNLOAD_STREAM = "download-stream";
    private static final String ACCEPT_ZERO_BYTE_BINARIES = "accept-zero-byte-binaries";

    private final ContentIntegrityCheckConfiguration configurations;

    public BinaryPropertiesSanityCheck() {
        configurations = new ContentIntegrityCheckConfigurationImpl();
        getConfigurations().declareDefaultParameter(DOWNLOAD_STREAM, Boolean.FALSE, BOOLEAN_PARSER, "If true, each binary property is validated by reading its value as a stream (time consuming operation). Otherwise, only the length of the binary is read");
        getConfigurations().declareDefaultParameter(ACCEPT_ZERO_BYTE_BINARIES, Boolean.TRUE, BOOLEAN_PARSER, "If true, the binary properties with a valid zero byte length value will not be reported as errors. Otherwise, the binary is considered as valid only if its length is greater than zero");
    }

    @Override
    public ContentIntegrityCheckConfiguration getConfigurations() {
        return configurations;
    }

    private boolean downloadStream() {
        return (boolean) configurations.getParameter(DOWNLOAD_STREAM);
    }

    private boolean acceptZeroByteBinaries() {
        return (boolean) configurations.getParameter(ACCEPT_ZERO_BYTE_BINARIES);
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        final ContentIntegrityErrorList errors = createEmptyErrorsList();
        try {
            final PropertyIterator properties = node.getProperties();

            Property property;
            boolean isValid;
            final boolean acceptZeroByteBinaries = acceptZeroByteBinaries();
            final boolean downloadStream = downloadStream();
            while (properties.hasNext()) {
                property = properties.nextProperty();
                if (property.getType() != PropertyType.BINARY) continue;
                final Binary binary = property.getBinary();
                final long size = binary.getSize();
                if (downloadStream) {
                    try {
                        if (!acceptZeroByteBinaries && size == 0) {
                            isValid = false;
                        } else {
                            final InputStream stream = binary.getStream();
                            int length = 0;
                            int readLength;
                            do {
                                readLength = IOUtils.read(stream, new byte[1024]);
                                length += readLength;
                            } while (readLength > 0);
                            isValid = length == size;
                        }
                    } catch (DataStoreException | IOException e) {
                        isValid = false;
                    }
                } else {
                    isValid = acceptZeroByteBinaries ?
                            size >= 0 :
                            size > 0;
                }
                if (!isValid) {
                    final String locale = node.isNodeType(Constants.JAHIANT_TRANSLATION) ?
                            getTranslationNodeLocale(node) : null;
                    final ContentIntegrityError error = createError(node, locale, "Invalid binary property")
                            .addExtraInfo("property-name", property.getName())
                            .addExtraInfo("property-path", property.getPath(), true);
                    if (size == 0) {
                        error.setExtraMsg("Warning: the binary length is zero byte. This can be a false positive if an empty file has been uploaded");
                    }
                    errors.addError(error);
                }
            }
        } catch (RepositoryException e) {
            logger.error("Impossible to check the node " + node.getPath(), e);
        }
        return errors;
    }
}
