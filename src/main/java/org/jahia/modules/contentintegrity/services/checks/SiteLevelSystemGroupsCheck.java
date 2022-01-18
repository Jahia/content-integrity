package org.jahia.modules.contentintegrity.services.checks;

import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.registries.ServicesRegistry;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRGroupNode;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

import java.util.Collection;

import static org.jahia.api.Constants.EDIT_WORKSPACE;
import static org.jahia.api.Constants.JAHIANT_VIRTUALSITE;
import static org.jahia.services.usermanager.JahiaGroupManagerService.PRIVILEGED_GROUPNAME;
import static org.jahia.services.usermanager.JahiaGroupManagerService.SITE_PRIVILEGED_GROUPNAME;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_WS + "=" + EDIT_WORKSPACE,
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + JAHIANT_VIRTUALSITE,
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_SUBTREES + "=/sites"
})
public class SiteLevelSystemGroupsCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(SiteLevelSystemGroupsCheck.class);

    private JahiaGroupManagerService jgms;
    private boolean missingRootPrivilegedGroupLogged = false;

    @Override
    public void initializeIntegrityTestInternal(JCRNodeWrapper node, Collection<String> excludedPaths) {
        missingRootPrivilegedGroupLogged = false;
        if (jgms == null)
            jgms = ServicesRegistry.getInstance().getJahiaGroupManagerService();
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        if (!(node instanceof JCRSiteNode)) {
            logger.error(String.format("Unexpected non site node: %s", node.getPath()));
            return null;
        }
        final JCRSiteNode site = (JCRSiteNode) node;
        final JCRGroupNode privGroup;
        final ContentIntegrityErrorList errors = createEmptyErrorsList();
        try {
            privGroup = jgms.lookupGroup(null, PRIVILEGED_GROUPNAME, site.getSession());
            if (privGroup == null && !missingRootPrivilegedGroupLogged) {
                // The 'privileged' group is defined at server level. If missing, a unique error will be logged while scanning the first site
                final ContentIntegrityError error = createError(site.getSession().getRootNode(), String.format("The '%s' group does not exist", PRIVILEGED_GROUPNAME))
                        .setErrorType(ErrorType.GROUP_DOES_NOT_EXIST)
                        .addExtraInfo("group-name", PRIVILEGED_GROUPNAME)
                        .setExtraMsg(String.format("The '%s' group is created at server installation time, at server level, and should never be deleted", PRIVILEGED_GROUPNAME));
                errors.addError(error);
                missingRootPrivilegedGroupLogged = true;
            }

            final JCRGroupNode sitePrivGroup = jgms.lookupGroup(site.getSiteKey(), SITE_PRIVILEGED_GROUPNAME, site.getSession());
            if (sitePrivGroup == null) {
                final ContentIntegrityError error = createError(site, String.format("The '%s' group doesn't exist in the site", SITE_PRIVILEGED_GROUPNAME))
                        .setErrorType(ErrorType.GROUP_DOES_NOT_EXIST)
                        .addExtraInfo("group-name", SITE_PRIVILEGED_GROUPNAME)
                        .addExtraInfo("site-name", site.getDisplayableName())
                        .setExtraMsg(String.format("The '%s' group is created at site creation time, at site level, and should never be deleted", SITE_PRIVILEGED_GROUPNAME));
                errors.addError(error);
            }

            if (privGroup != null && sitePrivGroup != null && !privGroup.isMember(sitePrivGroup)) {
                final ContentIntegrityError error = createError(site, String.format("The '%s' group of a site is not member of the '%s' group",
                        SITE_PRIVILEGED_GROUPNAME, PRIVILEGED_GROUPNAME))
                        .setErrorType(ErrorType.MISSING_MEMBERSHIP)
                        .addExtraInfo("missing-member", SITE_PRIVILEGED_GROUPNAME)
                        .addExtraInfo("group-missing-a-member", PRIVILEGED_GROUPNAME)
                        .addExtraInfo("site-name", site.getDisplayableName())
                        .setExtraMsg(String.format("The '%s' group of each site must be member of the server level group '%s", SITE_PRIVILEGED_GROUPNAME, PRIVILEGED_GROUPNAME));
                errors.addError(error);
            }
        } catch (RepositoryException e) {
            logger.error("", e);
        }

        return  errors;
    }

    private enum ErrorType {
        GROUP_DOES_NOT_EXIST, MISSING_MEMBERSHIP
    }
}
