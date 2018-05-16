package org.jahia.modules.verifyintegrity.services.checks;

import org.jahia.modules.verifyintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.verifyintegrity.services.ContentIntegrityError;
import org.jahia.modules.verifyintegrity.services.impl.AbstractContentIntegrityCheck;
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
    public ContentIntegrityError checkIntegrityBeforeChildren(Node node) {
        try {
            if (node.getNodes().getSize() > threshold)
                return ContentIntegrityError.createError(node, null, String.format("The node has over %s children", threshold), this);
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        return null;
    }
}
