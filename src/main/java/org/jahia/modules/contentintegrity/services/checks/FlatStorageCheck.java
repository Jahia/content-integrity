package org.jahia.modules.contentintegrity.services.checks;

import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.PRIORITY + ":Float=0" // For performances purpose, the result of getNodes() will be stored in the JR low level cache and will fasten any other check using it as well
})
public class FlatStorageCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(FlatStorageCheck.class);

    private int threshold = 500; // TODO make this somehow configurable

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(Node node) {
        try {
            if (node.getNodes().getSize() > threshold)
                return createSingleError(node, String.format("The node has over %s children", threshold));
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        return null;
    }
}
