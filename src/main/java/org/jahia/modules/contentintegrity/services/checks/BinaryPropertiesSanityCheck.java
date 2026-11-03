package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.core.data.DataStoreException;
import org.jahia.modules.contentintegrity.api.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.api.JCRUtils;
import org.jahia.modules.contentintegrity.services.ContentIntegrityCheckConfigurationImpl;
import org.jahia.modules.contentintegrity.services.util.RepairUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import java.io.IOException;
import java.io.InputStream;

import static org.jahia.modules.contentintegrity.api.Constants.JCR_CONTENT;
import static org.jahia.modules.contentintegrity.services.ContentIntegrityCheckConfigurationImpl.BOOLEAN_PARSER;

@Component(service = ContentIntegrityCheck.class, immediate = true)
public class BinaryPropertiesSanityCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.IsConfigurable, ContentIntegrityCheck.SupportsIntegrityErrorFix {

    private static final String DOWNLOAD_STREAM = "download-stream";
    private static final String ACCEPT_ZERO_BYTE_BINARIES = "accept-zero-byte-binaries";
    private static final String EXTRA_MSG_ZERO_LENGTH_BINARY = "Warning: the binary length is zero byte. This can be a false positive if an empty file has been uploaded";
    private static final String NT_FILE = "nt:file";
    private static final ContentIntegrityErrorType INVALID_BINARY = createErrorType("INVALID_BINARY", "Invalid binary property");

    private final ContentIntegrityCheckConfiguration configurations;

    public BinaryPropertiesSanityCheck() {
        configurations = new ContentIntegrityCheckConfigurationImpl();
        getConfigurations().declareDefaultParameter(DOWNLOAD_STREAM, Boolean.FALSE, BOOLEAN_PARSER, "If true, each binary property is validated by reading its value as a stream (time consuming operation). Otherwise, only the length of the binary is read");
        getConfigurations().declareDefaultParameter(ACCEPT_ZERO_BYTE_BINARIES, Boolean.FALSE, BOOLEAN_PARSER, "If true, the binary properties with a valid zero byte length value will not be reported as errors. Otherwise, the binary is considered as valid only if its length is greater than zero");
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
            final boolean acceptZeroByteBinaries = acceptZeroByteBinaries();
            final boolean downloadStream = downloadStream();
            while (properties.hasNext()) {
                property = properties.nextProperty();
                if (property.getType() != PropertyType.BINARY) continue;
                final long size = property.getBinary().getSize();
                if (!isValid(property, acceptZeroByteBinaries, downloadStream)) {
                    final String locale = node.isNodeType(Constants.JAHIANT_TRANSLATION) ?
                            JCRUtils.getTranslationNodeLocale(node) : null;
                    final ContentIntegrityError error = createError(node, locale, INVALID_BINARY)
                            .addExtraInfo("property-name", property.getName())
                            .addExtraInfo("property-path", property.getPath(), true);
                    if (size == 0) {
                        error.setExtraMsg(EXTRA_MSG_ZERO_LENGTH_BINARY);
                    }
                    errors.addError(error);
                }
            }
        } catch (RepositoryException e) {
            errors.addError(createFrameworkError(node, "Impossible to check the node " + node.getPath(), e));
        }
        return errors;
    }

    private static boolean isValid(Property property, boolean acceptZeroByteBinaries, boolean downloadStream) throws RepositoryException {
        final Binary binary = property.getBinary();
        final long size = binary.getSize();
        if (!downloadStream) {
            return acceptZeroByteBinaries ? size >= 0 : size > 0;
        }
        if (!acceptZeroByteBinaries && size == 0) return false;
        InputStream stream = null;
        try {
            stream = binary.getStream();
            int length = 0;
            int readLength;
            do {
                readLength = IOUtils.read(stream, new byte[1024]);
                length += readLength;
            } while (readLength > 0);
            return length == size;
        } catch (DataStoreException | IOException e) {
            return false;
        } finally {
            IOUtils.closeQuietly(stream);
        }
    }

    /*
     * From the jcr-scripts fix: a broken binary can't be restored. The file holding it is removed when the property
     * is held by the jcr:content node of a file; otherwise only the property is removed, so that a translation node
     * keeps the other languages, and a node keeps its subtree. A mandatory property can't be removed alone: its node is
     * removed when it has no sub-node, and the error is not fixed otherwise.
     * The file is the parent of the node of the error, never a node read from property-path: that extra info is cut
     * to 100 characters, so a long path would resolve to an ancestor folder of the file, which would be removed.
     * The parent is removed only when it is an nt:file: a page or a site accepts a child of any type and name, so an
     * editor can create a jcr:content under a page they may not delete.
     * The error is checked again first, reading the stream whatever download-stream is: the node may have changed
     * since the scan, and a binary which is now valid is left as it is.
     */
    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException {
        if (!error.getErrorType().equals(INVALID_BINARY)) return false;
        final String propertyName = (String) error.getExtraInfo("property-name");
        if (StringUtils.isBlank(propertyName)) return false;
        final Node realNode = node.getRealNode();
        if (!realNode.hasProperty(propertyName)) return false;
        final Property property = realNode.getProperty(propertyName);
        if (property.getType() != PropertyType.BINARY) return false;
        if (isValid(property, acceptZeroByteBinaries(), true)) return true;

        if (isContentOfFile(node)) {
            final JCRNodeWrapper file = node.getParent();
            RepairUtils.runWithListenersDisabled(() -> RepairUtils.removeNodeRaw(file));
        } else if (!property.getDefinition().isMandatory()) {
            RepairUtils.runWithListenersDisabled(() -> RepairUtils.removePropertyRaw(node, propertyName));
        } else if (!realNode.hasNodes()) {
            // The repository refuses to save a node without its mandatory property: the node alone is removed
            RepairUtils.runWithListenersDisabled(() -> RepairUtils.removeNodeRaw(node));
        } else {
            return false;
        }
        return true;
    }

    private static boolean isContentOfFile(JCRNodeWrapper node) throws RepositoryException {
        if (!StringUtils.equals(node.getName(), JCR_CONTENT)) return false;
        final Node parent = node.getRealNode().getParent();
        return parent.isNodeType(NT_FILE);
    }
}
