package org.jahia.modules.contentintegrity.services.checks;

import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.JAHIANT_ACE
})
public class AceSanityCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(AceSanityCheck.class);
    private static final String J_PRINCIPAL = "j:principal";

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        try {
            if (!node.hasProperty(J_PRINCIPAL)) {
                return createSingleError(node, "ACE without principal");
            } else {
                final String principal = node.getProperty(J_PRINCIPAL).getString();
                final JCRSiteNode site = node.getResolveSite();
                final String siteKey = site == null ? null : site.getSiteKey();
                final JCRNodeWrapper principalNode = getPrincipal(siteKey, principal);
                if (principalNode == null) {
                    return createSingleError(node, String.format("%s not found, but an ACE is defined for it", principal));
                }
            }
        } catch (RepositoryException e) {
            logger.error("", e);
        }

        return null;
    }

    private JCRNodeWrapper getPrincipal(String site, String principal) {
        JCRNodeWrapper p = null;
        final String principalName = principal.substring(2);
        if (principal.startsWith("u:")) {
            p = JahiaUserManagerService.getInstance().lookupUser(principalName, site);
        } else if (principal.startsWith("g:")) {
            final JahiaGroupManagerService groupService = JahiaGroupManagerService.getInstance();
            p = groupService.lookupGroup(site, principalName);
            if (p == null) {
                p = groupService.lookupGroup(null, principalName);
            }
        }
        return p;
    }
}
