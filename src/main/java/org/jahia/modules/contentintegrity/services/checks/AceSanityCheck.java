package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.jahia.api.Constants.EDIT_WORKSPACE;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.JAHIANT_ACE
})
public class AceSanityCheck extends AbstractContentIntegrityCheck implements
        ContentIntegrityCheck.SupportsIntegrityErrorFix {

    private static final Logger logger = LoggerFactory.getLogger(AceSanityCheck.class);
    private static final String J_PRINCIPAL = "j:principal";

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        try {
            if (node.isNodeType("jnt:externalAce")) {
                return checkExternalAce(node);
            }
            return checkRegularAce(node);
        } catch (RepositoryException e) {
            logger.error("", e);
        }

        return null;
    }

    private ContentIntegrityErrorList checkExternalAce(JCRNodeWrapper node) throws RepositoryException {
        final ContentIntegrityErrorList errors = createEmptyErrorsList();
        boolean hasPropSourceAce = true, hasPropRoles = true;
        if (!node.hasProperty("j:sourceAce")) {
            hasPropSourceAce = false;
            final ContentIntegrityError error = createError(node, "External ACE without source ACE");
            error.setExtraInfos(ErrorType.NO_SOURCE_ACE_PROP);
            errors.addError(error);
        }
        if (!node.hasProperty("j:roles")) {
            hasPropRoles = false;
            final ContentIntegrityError error = createError(node, "External ACE without property j:roles");
            error.setExtraInfos(ErrorType.NO_ROLES_PROP);
            errors.addError(error);
        }

        if (hasPropSourceAce) {
            for (JCRValueWrapper value : node.getProperty("j:sourceAce").getValues()) {
                JCRNodeWrapper srcAce;
                try {
                    srcAce = value.getNode();
                } catch (RepositoryException re) {
                    boolean isFalsePositive = false;
                    if (isInLiveWorkspace(node)) {
                        final JCRSessionWrapper defaultSession = JCRSessionFactory.getInstance().getCurrentSystemSession(EDIT_WORKSPACE, null, null);
                        if (nodeExists(value.getString(), defaultSession)) {
                            final JCRNodeWrapper srcAceDefault = defaultSession.getNodeByUUID(value.getString());
                            final JCRNodeWrapper srcContentDefault = srcAceDefault.getParent().getParent();
                            if (!node.getSession().nodeExists(srcContentDefault.getPath()))
                                isFalsePositive = true;
                        }
                    }
                    if (!isFalsePositive)
                        errors.addError(createErrorWithInfos(node, null, "Broken reference to source ACE", ErrorType.SOURCE_ACE_BROKEN_REF));
                    continue;
                }
                final JCRSiteNode site = node.getResolveSite();
                final JCRSiteNode aceSite = srcAce.getResolveSite();
                final String aceSiteKey = aceSite == null ? null : aceSite.getSiteKey();
                if (!StringUtils.equals(site == null ? null : site.getSiteKey(), aceSiteKey)) {
                    final Map<String, Object> infos = new HashMap<>(4);
                    infos.put("error-type", ErrorType.SOURCE_ACE_DIFFERENT_SITE);
                    infos.put("ace-uuid", srcAce.getIdentifier());
                    infos.put("ace-path", srcAce.getPath());
                    infos.put("ace-site", aceSiteKey);
                    errors.addError(createErrorWithInfos(node, null, "The external ACE and the source ACE are stored in different sites", infos));
                }
                if (hasPropRoles) {
                    if (!srcAce.hasProperty("j:roles")) {
                        errors.addError(createErrorWithInfos(node, null,
                                "The roles differ on the external and source ACE, since the j:roles property is missing on the source ACE",
                                ErrorType.ROLES_DIFFER_ON_SOURCE_ACE));
                    } else {
                        final List<String> externalAceRoles = getRoleNames(node);
                        final List<String> srcAceRoles = getRoleNames(srcAce);
                        final Collection externalAceOnlyRoles = CollectionUtils.subtract(externalAceRoles, srcAceRoles);
                        if (CollectionUtils.isNotEmpty(externalAceOnlyRoles)) {
                            errors.addError(createErrorWithInfos(node, null,
                                    String.format("Some roles are defined on the external ACE but not on the source ACE: [%s]", String.join(", ", externalAceOnlyRoles)),
                                    ErrorType.ROLES_DIFFER_ON_SOURCE_ACE));
                        }
                        final Collection srcAceOnlyRoles = CollectionUtils.subtract(srcAceRoles, externalAceRoles);
                        if (CollectionUtils.isNotEmpty(srcAceOnlyRoles)) {
                            errors.addError(createErrorWithInfos(node, null,
                                    String.format("Some roles are defined on the source ACE but not on the external ACE: [%s]", String.join(", ", srcAceOnlyRoles)),
                                    ErrorType.ROLES_DIFFER_ON_SOURCE_ACE));
                        }
                    }
                }
            }
        }

        return errors;
    }

    private List<String> getRoleNames(JCRNodeWrapper ace) throws RepositoryException {
        return Arrays.stream(ace.getProperty("j:roles").getValues()).map(jcrValueWrapper -> {
            try {
                return jcrValueWrapper.getString();
            } catch (RepositoryException e) {
                logger.error("", e);
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private ContentIntegrityErrorList checkRegularAce(JCRNodeWrapper node) throws RepositoryException {

        if (!node.hasProperty(J_PRINCIPAL)) {
            final ContentIntegrityError error = createError(node, "ACE without principal");
            error.setExtraInfos(ErrorType.NO_PRINCIPAL);
            return createSingleError(error);
        }

        final String principal = node.getProperty(J_PRINCIPAL).getString();
        final JCRSiteNode site = node.getResolveSite();
        final String siteKey = site == null ? null : site.getSiteKey();
        final JCRNodeWrapper principalNode = getPrincipal(siteKey, principal);
        if (principalNode == null) {
            final ContentIntegrityError error = createError(node, String.format("%s not found, but an ACE is defined for it", principal));
            error.setExtraInfos(ErrorType.INVALID_PRINCIPAL);
            return createSingleError(error);
        }
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

    @Override
    public boolean fixError(JCRNodeWrapper ace, ContentIntegrityError error) throws RepositoryException {
        if (!Constants.EDIT_WORKSPACE.equals(ace.getSession().getWorkspace().getName())) return false;

        final Object errorExtraInfos = error.getExtraInfos();
        if (!(errorExtraInfos instanceof ErrorType)) {
            logger.error("Unexpected error type: " + errorExtraInfos);
            return false;
        }
        final ErrorType errorType = (ErrorType) errorExtraInfos;
        final JCRNodeWrapper node = ace.getParent().getParent();
        switch (errorType) {
            case NO_PRINCIPAL:
                return false;
            case INVALID_PRINCIPAL:
                final String principal = ace.getPropertyAsString(J_PRINCIPAL);
                final JCRPropertyWrapper roles = ace.getProperty("j:roles");
                final Map<String, String> rolesRem = new HashMap<>();
                for (JCRValueWrapper r : roles.getValues()) {
                    rolesRem.put(r.getString(), "REMOVE");
                }
                if (node.changeRoles(principal, rolesRem)) {
                    node.saveSession();
                    return true;
                } else {
                    return false;
                }
        }

        return false;
    }

    private enum ErrorType {
        NO_PRINCIPAL, INVALID_PRINCIPAL, NO_SOURCE_ACE_PROP, SOURCE_ACE_BROKEN_REF, NO_ROLES_PROP,
        SOURCE_ACE_DIFFERENT_SITE, ROLES_DIFFER_ON_SOURCE_ACE
    }
}
