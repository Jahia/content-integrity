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

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
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
    private static final String JNT_EXTERNAL_ACE = "jnt:externalAce";
    private static final String JNT_EXTERNAL_PERMISSIONS = "jnt:externalPermissions";
    private static final String J_PRINCIPAL = "j:principal";
    private static final String J_EXTERNAL_PERMISSIONS_NAME = "j:externalPermissionsName";
    private static final String J_ROLES = "j:roles";
    private static final String J_SOURCE_ACE = "j:sourceAce";
    private static final String J_PATH = "j:path";

    private final Map<String, Role> roles = new HashMap<>();

    @Override
    public void initializeIntegrityTestInternal() {
        final JCRSessionWrapper defaultSession;
        try {
            defaultSession = JCRSessionFactory.getInstance().getCurrentSystemSession(EDIT_WORKSPACE, null, null);
            final String statement = "SELECT * FROM [jnt:role] WHERE ISDESCENDANTNODE('/roles')";
            final Query query = defaultSession.getWorkspace().getQueryManager().createQuery(statement, Query.JCR_SQL2);

            for (final NodeIterator it = query.execute().getNodes(); it.hasNext(); ) {
                final JCRNodeWrapper roleNode = (JCRNodeWrapper) it.next();
                final Role role = new Role(roleNode.getName(), roleNode.getIdentifier());
                for (JCRNodeWrapper extPerm : roleNode.getNodes()) {
                    if (!extPerm.isNodeType(JNT_EXTERNAL_PERMISSIONS)) continue;
                    if (!extPerm.hasProperty(J_PATH)) {
                        logger.error(String.format("Skipping the extenal permission %s since it is invalid (no %s property)", extPerm.getPath(), J_PATH));
                        continue;
                    }
                    role.addExternalPermission(extPerm.getName(), extPerm.getPropertyAsString(J_PATH));
                }
                roles.put(role.getName(), role);
            }
        } catch (RepositoryException e) {
            logger.error("Error whole loading the available roles", e);
        }
    }

    @Override
    public void finalizeIntegrityTestInternal() {
        roles.clear();
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        try {
            if (node.isNodeType(JNT_EXTERNAL_ACE)) {
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
        errors.addAll(checkPrincipalOnAce(node));

        boolean hasPropSourceAce = true, hasPropRoles = true;
        if (!node.hasProperty(J_SOURCE_ACE)) {
            hasPropSourceAce = false;
            final ContentIntegrityError error = createError(node, "External ACE without source ACE");
            error.setExtraInfos(ErrorType.NO_SOURCE_ACE_PROP);
            errors.addError(error);
        }
        if (!node.hasProperty(J_ROLES)) {
            hasPropRoles = false;
            final ContentIntegrityError error = createError(node, "External ACE without property j:roles");
            error.setExtraInfos(ErrorType.NO_ROLES_PROP);
            errors.addError(error);
        }

        if (hasPropSourceAce) {
            final JCRValueWrapper[] values = node.getProperty(J_SOURCE_ACE).getValues();
            if (values.length == 0) {
                final ContentIntegrityError error = createError(node, "External ACE without source ACE");
                error.setExtraInfos(ErrorType.EMPTY_SOURCE_ACE_PROP);
                errors.addError(error);
            }
            for (JCRValueWrapper value : values) {
                JCRNodeWrapper srcAce = null;
                try {
                    srcAce = value.getNode();
                } catch (RepositoryException ignored) {
                }
                if (srcAce == null) {
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

                if (hasPropRoles) {
                    if (!srcAce.hasProperty(J_ROLES)) {
                        errors.addError(createErrorWithInfos(node, null,
                                String.format("The roles differ on the external and source ACE, since the %s property is missing on the source ACE", J_ROLES),
                                ErrorType.ROLES_DIFFER_ON_SOURCE_ACE));
                    } else {
                        final List<String> externalAceRoles = getRoleNames(node);
                        if (CollectionUtils.isEmpty(externalAceRoles)) {
                            errors.addError(createErrorWithInfos(node, null, String.format("The property %s has no value", J_ROLES), ErrorType.INVALID_ROLES_PROP));
                        } else if (externalAceRoles.size() > 1) {
                            errors.addError(createErrorWithInfos(node, null, String.format("Unexpected number of roles in the property %s", J_ROLES), ErrorType.INVALID_ROLES_PROP));
                        } else {
                            final List<String> srcAceRoles = getRoleNames(srcAce);
                            final String role = externalAceRoles.get(0);
                            final String srcAceIdentifier = srcAce.getIdentifier();

                            if (roles.containsKey(role) && roles.get(role).getExternalPermissions().getOrDefault(J_EXTERNAL_PERMISSIONS_NAME, "").equals("currentSite")) {
                                final String aceSiteKey = resolveSiteKey(node);
                                if (!StringUtils.equals(aceSiteKey, resolveSiteKey(srcAce))) {
                                    final Map<String, Object> infos = new HashMap<>(4);
                                    infos.put("error-type", ErrorType.SOURCE_ACE_DIFFERENT_SITE);
                                    infos.put("ace-uuid", srcAceIdentifier);
                                    infos.put("ace-path", srcAce.getPath());
                                    infos.put("ace-site", aceSiteKey);
                                    createErrorWithInfos(node, null, "The external ACE and the source ACE are stored in different sites", infos);
                                }
                            }
                            
                            if (!srcAceRoles.contains(role)) {
                                final Map<String, Object> infos = new HashMap<>();
                                infos.put("error-type", ErrorType.ROLES_DIFFER_ON_SOURCE_ACE);
                                infos.put("ace-uuid", srcAceIdentifier);
                                errors.addError(createErrorWithInfos(node, null,
                                        String.format("The external ACE is defined for the role %s, but the ace (%s) has not this role", role, srcAceIdentifier),
                                        ErrorType.ROLES_DIFFER_ON_SOURCE_ACE));
                            }
                        }
                    }
                }
            }
        }

        return errors;
    }

    private List<String> getRoleNames(JCRNodeWrapper ace) throws RepositoryException {
        return Arrays.stream(ace.getProperty(J_ROLES).getValues()).map(jcrValueWrapper -> {
            try {
                return jcrValueWrapper.getString();
            } catch (RepositoryException e) {
                logger.error("", e);
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    private ContentIntegrityErrorList checkRegularAce(JCRNodeWrapper node) throws RepositoryException {
        final ContentIntegrityErrorList errors = createEmptyErrorsList();
        errors.addAll(checkPrincipalOnAce(node));

        if (!node.hasProperty(J_ROLES)) {
            errors.addError(createErrorWithInfos(node, null, "ACE without property j:roles", ErrorType.NO_ROLES_PROP));
        } else {
            for (JCRValueWrapper roleRef : node.getProperty(J_ROLES).getValues()) {
                final String roleName = roleRef.getString();
                if (!roles.containsKey(roleName)) {
                    final Map<String, Object> infos = new HashMap<>(2);
                    infos.put("error-type", ErrorType.ROLE_DOESNT_EXIST);
                    infos.put("role", roleName);
                    errors.addError(createErrorWithInfos(node, null, "ACE with a role that doesn't exist", infos));
                } else {
                    final Role role = roles.get(roleName);
                    for (String extPerm : role.getExternalPermissions().keySet()) {
                        final PropertyIterator references = node.getWeakReferences();
                        boolean extAceFound = false;
                        while (!extAceFound && references.hasNext()) {
                            final Node extAce = references.nextProperty().getParent();
                            if (!extAce.isNodeType(JNT_EXTERNAL_ACE)) continue;
                            if (extAce.hasProperty(J_EXTERNAL_PERMISSIONS_NAME) &&
                                    StringUtils.equals(extPerm, extAce.getProperty(J_EXTERNAL_PERMISSIONS_NAME).getString()))
                                extAceFound = true;
                        }
                        if (!extAceFound)
                            errors.addError(createErrorWithInfos(node, null,
                                    String.format("The ACE has a role (%s) which defines external permissions (%s) but no related %s exist",
                                            roleName, extPerm, JNT_EXTERNAL_ACE)));
                    }
                }
            }
        }

        return errors;
    }

    private ContentIntegrityErrorList checkPrincipalOnAce(JCRNodeWrapper node) throws RepositoryException {

        if (!node.hasProperty(J_PRINCIPAL)) {
            return createSingleError(createErrorWithInfos(node, null, "ACE without principal", ErrorType.NO_PRINCIPAL));
        }

        final String principal = node.getProperty(J_PRINCIPAL).getString();
        final JCRSiteNode site = node.getResolveSite();
        final String siteKey = site == null ? null : site.getSiteKey();
        final JCRNodeWrapper principalNode = getPrincipal(siteKey, principal);
        if (principalNode == null) {
            return createSingleError(createErrorWithInfos(node, null,
                    String.format("%s not found, but an ACE is defined for it", principal), ErrorType.INVALID_PRINCIPAL));
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

    private String resolveSiteKey(JCRNodeWrapper node) {
        if (node == null) return null;
        final String path = node.getPath();
        if (!path.startsWith("/sites/")) return null;
        return StringUtils.split(path, '/')[1];
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
                final JCRPropertyWrapper roles = ace.getProperty(J_ROLES);
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
        NO_PRINCIPAL, INVALID_PRINCIPAL,
        NO_SOURCE_ACE_PROP, EMPTY_SOURCE_ACE_PROP, SOURCE_ACE_BROKEN_REF, SOURCE_ACE_DIFFERENT_SITE,
        NO_ROLES_PROP, INVALID_ROLES_PROP,
        ROLES_DIFFER_ON_SOURCE_ACE, ROLE_DOESNT_EXIST
    }

    private class Role {
        String name;
        String uuid;
        Map<String, String> externalPermissions = new HashMap<>();

        public Role(String name, String uuid) {
            this.name = name;
            this.uuid = uuid;
        }

        public String getName() {
            return name;
        }

        public String getUuid() {
            return uuid;
        }

        public Map<String, String> getExternalPermissions() {
            return externalPermissions;
        }

        public void addExternalPermission(String name, String path) {
            externalPermissions.put(name, path);
        }
    }
}
