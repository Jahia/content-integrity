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

    @Override
    public void initializeIntegrityTestInternal() {
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
        ContentIntegrityErrorList errors = null;
        try {
            privGroup = jgms.lookupGroup(null, PRIVILEGED_GROUPNAME, site.getSession());
            if (privGroup == null) {
                // The 'privileged' group is defined at server level. If missing, this unique error will be logged for each site
                final ContentIntegrityError error = createError(site, String.format("The '%s' group doesn't exist", PRIVILEGED_GROUPNAME));
                errors = appendError(errors, error);
            }

            final JCRGroupNode sitePrivGroup = jgms.lookupGroup(site.getSiteKey(), SITE_PRIVILEGED_GROUPNAME, site.getSession());
            if (sitePrivGroup == null) {
                final ContentIntegrityError error = createError(site, String.format("The '%s' group doesn't exist in the site %s", SITE_PRIVILEGED_GROUPNAME, site.getDisplayableName()));
                errors = appendError(errors, error);
            }

            if (privGroup != null && sitePrivGroup != null && !privGroup.isMember(sitePrivGroup)) {
                final ContentIntegrityError error = createError(site, String.format("The '%s' group of the site '%s' is not member of the '%s' group",
                        SITE_PRIVILEGED_GROUPNAME, site.getDisplayableName(), PRIVILEGED_GROUPNAME));
                errors = appendError(errors, error);
            }
        } catch (RepositoryException e) {
            logger.error("", e);
        }

        return  errors;
    }
}
