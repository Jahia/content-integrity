package org.jahia.modules.contentintegrity.services.checks;

import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.ItemNotFoundException;
import javax.jcr.RepositoryException;

import static org.jahia.modules.contentintegrity.services.impl.Constants.EDIT_WORKSPACE;
import static org.jahia.modules.contentintegrity.services.impl.Constants.HOME_PAGE_FALLBACK_NAME;
import static org.jahia.modules.contentintegrity.services.impl.Constants.HOME_PAGE_FLAG;
import static org.jahia.modules.contentintegrity.services.impl.Constants.JAHIANT_PAGE;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.JAHIANT_VIRTUALSITE,
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_SUBTREES + "=" + "/sites"
})
public class HomePageDeclarationCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.SupportsIntegrityErrorFix {

    private static final Logger logger = LoggerFactory.getLogger(HomePageDeclarationCheck.class);

    private enum ErrorType {NO_HOME, MULTIPLE_HOMES, FALLBACK_ON_NAME, FALLBACK_ON_NAME_WRONG_TYPE}

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        try {
            int flaggedAsHomeCount = 0;
            final JCRNodeIteratorWrapper iterator = node.getNodes();
            while (iterator.hasNext()) {
                final JCRNodeWrapper child = (JCRNodeWrapper) iterator.nextNode();
                if (child.isNodeType(JAHIANT_PAGE) && child.hasProperty(HOME_PAGE_FLAG) && child.getProperty(HOME_PAGE_FLAG).getBoolean())
                    flaggedAsHomeCount++;
            }
            if (flaggedAsHomeCount != 1) {
                if (flaggedAsHomeCount > 1) {
                    return createSingleError(createError(node, "The site has several pages flagged as home")
                            .setErrorType(ErrorType.MULTIPLE_HOMES)
                            .addExtraInfo("nb-pages-flagged", flaggedAsHomeCount));
                } else if (node.hasNode(HOME_PAGE_FALLBACK_NAME)) {
                    final JCRNodeWrapper homeNode = node.getNode(HOME_PAGE_FALLBACK_NAME);
                    if (homeNode.isNodeType(JAHIANT_PAGE)) {
                        if (JCRUtils.isInLiveWorkspace(node))
                            return null; // Let's consider it is not an error, if fixed in default, then it will get fixed in live after publishing
                        final String msg = String.format("The site has no page flagged as home, but one is named '%s'", HOME_PAGE_FALLBACK_NAME);
                        return createSingleError(createError(node, msg)
                                .setErrorType(ErrorType.FALLBACK_ON_NAME));
                    } else {
                        final String msg = String.format("The site has no page flagged as home. It has a sub-node named '%s', but this node is not of type %s",
                                HOME_PAGE_FALLBACK_NAME, JAHIANT_PAGE);
                        return createSingleError(createError(node, msg)
                                .setErrorType(ErrorType.FALLBACK_ON_NAME_WRONG_TYPE)
                                .addExtraInfo("home-node-type", homeNode.getPrimaryNodeTypeName()));
                    }
                } else {
                    if (JCRUtils.isInLiveWorkspace(node))
                        return null; // Not an error, the home page has maybe not yet been published
                    final String msg = String.format("The site has no page flagged as home and no one is named '%s'", HOME_PAGE_FALLBACK_NAME);
                    return createSingleError(createError(node, msg)
                            .setErrorType(ErrorType.NO_HOME));
                }
            }
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        return null;
    }

    @Override
    public boolean fixError(JCRNodeWrapper site, ContentIntegrityError integrityError) throws RepositoryException {
        final Object errorTypeObject = integrityError.getErrorType();
        if (!(errorTypeObject instanceof ErrorType)) {
            logger.error("Unexpected error type: " + errorTypeObject);
            return false;
        }
        final ErrorType errorType = (ErrorType) errorTypeObject;
        final JCRNodeIteratorWrapper iterator;
        switch (errorType) {
            case NO_HOME:
                iterator = site.getNodes();
                while (iterator.hasNext()) {
                    final JCRNodeWrapper child = (JCRNodeWrapper) iterator.nextNode();
                    if (child.isNodeType(JAHIANT_PAGE)) {
                        child.setProperty(HOME_PAGE_FLAG, true);
                        child.getSession().save();
                        return true;
                    }
                }
                return false;
            case MULTIPLE_HOMES:
                if (JCRUtils.isInDefaultWorkspace(site)) {
                    JCRNodeWrapper home = null;
                    if (site.hasNode(HOME_PAGE_FALLBACK_NAME)) {
                        home = site.getNode(HOME_PAGE_FALLBACK_NAME);
                        if (!(home.hasProperty(HOME_PAGE_FLAG) && home.getProperty(HOME_PAGE_FLAG).getBoolean())) {
                            home = null; // means it was actually not this one
                        }
                    }
                    iterator = site.getNodes();
                    while (iterator.hasNext()) {
                        final JCRNodeWrapper child = (JCRNodeWrapper) iterator.nextNode();
                        if (child.isNodeType(JAHIANT_PAGE) && child.hasProperty(HOME_PAGE_FLAG) && child.getProperty(HOME_PAGE_FLAG).getBoolean()) {
                            if (home == null) home = child;
                            else
                                child.getProperty(HOME_PAGE_FLAG).remove();
                        }
                    }
                    return true;
                } else {
                    final JCRSessionWrapper session_default = JCRUtils.getSystemSession(EDIT_WORKSPACE, false);
                    JCRNodeWrapper home_default = null;
                    final JCRNodeWrapper site_default = session_default.getNode(site.getPath());
                    for (JCRNodeWrapper child : site_default.getNodes()) {
                        if (child.isNodeType(JAHIANT_PAGE) && child.hasProperty(HOME_PAGE_FLAG) && child.getProperty(HOME_PAGE_FLAG).getBoolean()) {
                            if (home_default == null) home_default = child;
                            else {
                                logger.error("Impossible to fix the error in live. It needs to be fixed in default first."); // multiple home in default
                                return false;
                            }
                        }
                    }
                    if (home_default == null) {
                        logger.error("Impossible to fix the error in live. It needs to be fixed in default first.");
                        return false; // no home in default
                    }
                    JCRNodeWrapper home = null;
                    try {
                        home = site.getSession().getNodeByIdentifier(home_default.getIdentifier());
                    } catch (ItemNotFoundException infe) {
                        // home not yet published
                    }
                    iterator = site.getNodes();
                    while (iterator.hasNext()) {
                        final JCRNodeWrapper child = (JCRNodeWrapper) iterator.nextNode();
                        if (child.isNodeType(JAHIANT_PAGE) && child.hasProperty(HOME_PAGE_FLAG) && child.getProperty(HOME_PAGE_FLAG).getBoolean()) {
                            if (home != null && home.getIdentifier().equals(child.getIdentifier())) continue;
                            child.getProperty(HOME_PAGE_FLAG).remove();
                        }
                    }
                    site.getSession().save();
                    return true;
                }
            case FALLBACK_ON_NAME:
                final JCRNodeWrapper home = site.getNode(HOME_PAGE_FALLBACK_NAME);
                home.setProperty(HOME_PAGE_FLAG, true);
                home.getSession().save();
                return true;
        }
        return false;
    }
}
