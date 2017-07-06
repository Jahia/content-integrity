package org.jahia.modules.verifyintegrity.services.checks;

import org.jahia.modules.verifyintegrity.services.ContentIntegrityCheck;
import org.jahia.modules.verifyintegrity.services.ContentIntegrityError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;

import static org.jahia.api.Constants.JAHIANT_PAGE;

public class HomePageDeclaration extends ContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(HomePageDeclaration.class);
    private static final String HOME_PAGE_FLAG = "j:isHomePage";

    @Override
    public ContentIntegrityError checkIntegrityBeforeChildren(Node node) {
        return null;  //TODO: review me, I'm generated
    }

    @Override
    public ContentIntegrityError checkIntegrityAfterChildren(Node node) {
        try {
            int flaggedAsHomeCount = 0;
            final NodeIterator iterator = node.getNodes();
            while (iterator.hasNext()) {
                final Node child = iterator.nextNode();
                if (child.isNodeType(JAHIANT_PAGE) && node.hasProperty(HOME_PAGE_FLAG) && node.getProperty(HOME_PAGE_FLAG).getBoolean())
                    flaggedAsHomeCount++;
            }
            if (flaggedAsHomeCount != 1) {
                final String msg;
                if (flaggedAsHomeCount > 1)
                    msg = String.format("The site %s has several pages flagged as home", node.getName());
                else if (node.hasNode("home"))
                    msg = String.format("The site %s has no page flagged as home, but one is named 'home'", node.getName());
                else
                    msg = String.format("The site %s has no page flagged as home and no one is named 'home'", node.getName());
                return ContentIntegrityError.createError(node, null, msg, this.getClass().getSimpleName());
            }
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        return null;
    }
}
