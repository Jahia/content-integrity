package org.jahia.modules.contentintegrity.services.checks;

import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheckConfiguration;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.ContentIntegrityCheckConfigurationImpl;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.PRIORITY + ":Float=0" // For performances purpose, the result of getNodes() will be stored in the JR low level cache and will fasten any other check using it as well
})
public class FlatStorageCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.IsConfigurable {

    private static final Logger logger = LoggerFactory.getLogger(FlatStorageCheck.class);

    private static final String THRESHOLD_KEY = "threshold";
    private static final int defaultThreshold = 500;

    private final ContentIntegrityCheckConfiguration configurations;

    public FlatStorageCheck() {
        configurations = new ContentIntegrityCheckConfigurationImpl();
        getConfigurations().declareDefaultParameter(THRESHOLD_KEY, defaultThreshold, "Number of children nodes beyond which an error is raised");
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        try {
            final long size = node.getNodes().getSize();
            final int threshold = getThreshold();
            if (size > threshold)
                return createSingleError(node, String.format("The node has over %s children: %s", threshold, size));
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        return null;
    }

    private int getThreshold() {
        final Object o = getConfigurations().getParameter(THRESHOLD_KEY);
        if (o instanceof Integer) return (Integer) o;
        if (o instanceof String) {
            try {
                return Integer.parseInt((String) o);
            } catch (NumberFormatException nfe) {
                logger.error(String.format("Invalid threshold: %s", o));
            }
        }
        return defaultThreshold;
    }

    @Override
    public ContentIntegrityCheckConfiguration getConfigurations() {
        return configurations;
    }
}
