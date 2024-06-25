package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.jahia.modules.contentintegrity.services.impl.Constants.EDIT_WORKSPACE;
import static org.jahia.modules.contentintegrity.services.impl.Constants.EXTERNAL_PERMISSIONS_PATH;
import static org.jahia.modules.contentintegrity.services.impl.Constants.JCR_PATH_SEPARATOR;
import static org.jahia.modules.contentintegrity.services.impl.Constants.JNT_EXTERNAL_ACE;
import static org.jahia.modules.contentintegrity.services.impl.Constants.JNT_EXTERNAL_PERMISSIONS;
import static org.jahia.modules.contentintegrity.services.impl.Constants.J_ACE_TYPE;
import static org.jahia.modules.contentintegrity.services.impl.Constants.J_EXTERNAL_PERMISSIONS_NAME;
import static org.jahia.modules.contentintegrity.services.impl.Constants.J_PRINCIPAL;
import static org.jahia.modules.contentintegrity.services.impl.Constants.J_ROLES;
import static org.jahia.modules.contentintegrity.services.impl.Constants.J_SOURCE_ACE;
import static org.jahia.modules.contentintegrity.services.impl.Constants.SLASH;
import static org.jahia.modules.contentintegrity.services.impl.Constants.SPACE;
import static org.jahia.modules.contentintegrity.services.impl.Constants.UNDERSCORE;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.JAHIANT_ACE
})
public class AceSanityCheck extends AbstractContentIntegrityCheck implements
        ContentIntegrityCheck.SupportsIntegrityErrorFix {

    private static final Logger logger = LoggerFactory.getLogger(AceSanityCheck.class);
    private static final Pattern CURRENT_SITE_PATTERN = Pattern.compile("^currentSite");
    private static final String EXTRA_MSG_SOURCE_ACE_NOT_TYPE_GRANT = "External ACE are defined only for the ACE of type GRANT";
    private static final String EXTRA_MSG_SRC_ACE_WITHOUT_ROLES_PROP = "Impossible to check if the roles defined on the external ACE and the source ACE are consistent, since the property " + J_ROLES + " is missing on the source ACE";
    private static final String EXTRA_MSG_INVALID_PRINCIPAL = "If the principal exists, check if it is defined at site level, and if does, if this site differs from the current site. Warning: if the principal comes from an external source such as a LDAP, it might be just temporarily missing because of a connectivity issue";

    public static final ContentIntegrityErrorType NO_PRINCIPAL = createErrorType("NO_PRINCIPAL", "ACE without principal");
    public static final ContentIntegrityErrorType INVALID_PRINCIPAL = createErrorType("INVALID_PRINCIPAL", "ACE with an invalid principal");
    public static final ContentIntegrityErrorType NO_ACE_TYPE_PROP = createErrorType("NO_ACE_TYPE_PROP", "Missing property ".concat(J_ACE_TYPE));
    public static final ContentIntegrityErrorType INVALID_ACE_TYPE_PROP = createErrorType("INVALID_ACE_TYPE_PROP", "External ACE with an invalid ".concat(J_ACE_TYPE));
    public static final ContentIntegrityErrorType INVALID_NODENAME = createErrorType("INVALID_NODENAME", "ACE with an invalid nodename");
    public static final ContentIntegrityErrorType NO_SOURCE_ACE_PROP = createErrorType("NO_SOURCE_ACE_PROP", "External ACE without source ACE");
    public static final ContentIntegrityErrorType EMPTY_SOURCE_ACE_PROP = createErrorType("EMPTY_SOURCE_ACE_PROP", "External ACE without source ACE");
    public static final ContentIntegrityErrorType SOURCE_ACE_BROKEN_REF = createErrorType("SOURCE_ACE_BROKEN_REF", "Broken reference to source ACE");
    public static final ContentIntegrityErrorType INVALID_EXTERNAL_ACE_PATH = createErrorType("INVALID_EXTERNAL_ACE_PATH", "The external ACE has not the expected path");
    public static final ContentIntegrityErrorType SOURCE_ACE_NOT_TYPE_GRANT = createErrorType("SOURCE_ACE_NOT_TYPE_GRANT", "The source ACE is not of type GRANT");
    public static final ContentIntegrityErrorType INVALID_EXTERNAL_PERMISSIONS = createErrorType("INVALID_EXTERNAL_PERMISSIONS", "External ACE defined for external permissions which are not declared by the role");
    public static final ContentIntegrityErrorType MISSING_EXTERNAL_ACE = createErrorType("MISSING_EXTERNAL_ACE", "ACE with a missing external ACE");
    public static final ContentIntegrityErrorType ACE_NON_GRANT_WITH_EXTERNAL_ACE = createErrorType("ACE_NON_GRANT_WITH_EXTERNAL_ACE", "ACE of type different from GRANT with an external ACE");
    public static final ContentIntegrityErrorType NO_ROLES_PROP = createErrorType("NO_ROLES_PROP", "Missing property " + J_ROLES);
    public static final ContentIntegrityErrorType INVALID_ROLES_PROP = createErrorType("INVALID_ROLES_PROP", "Invalid " + J_ROLES + " property");
    public static final ContentIntegrityErrorType ROLES_DIFFER_ON_SOURCE_ACE = createErrorType("ROLES_DIFFER_ON_SOURCE_ACE", "Roles differ between the external ACE and the source ACE");
    public static final ContentIntegrityErrorType ROLE_DOESNT_EXIST = createErrorType("ROLE_DOESNT_EXIST", "Reference to a role which does not exist");

    private final Map<String, Role> roles = new HashMap<>();

    @Override
    public void initializeIntegrityTestInternal(JCRNodeWrapper node, Collection<String> excludedPaths) {
        final JCRSessionWrapper defaultSession = JCRUtils.getSystemSession(EDIT_WORKSPACE, false);
        try {
            processRole(defaultSession.getNode("/roles"), null, true);
        } catch (RepositoryException e) {
            logger.error("Error while loading the available roles", e);
            setScanDurationDisabled(true);
        }
    }

    private void processRole(JCRNodeWrapper roleNode, String parentRole, boolean isRootFolder) throws RepositoryException {
        String roleName = null;
        if (!isRootFolder) {
            final Role role = new Role(roleNode.getName(), roleNode.getIdentifier());
            if (parentRole != null) {
                roles.get(parentRole).getExternalPermissions().forEach(role::addExternalPermission);
            }
            for (JCRNodeWrapper extPerm : JCRContentUtils.getNodes(roleNode, JNT_EXTERNAL_PERMISSIONS)) {
                if (!extPerm.hasProperty(EXTERNAL_PERMISSIONS_PATH)) {
                    logger.error(String.format("Skipping the external permission %s since it is invalid (no %s property)", extPerm.getPath(), EXTERNAL_PERMISSIONS_PATH));
                    continue;
                }
                role.addExternalPermission(extPerm.getName(), extPerm.getPropertyAsString(EXTERNAL_PERMISSIONS_PATH));
            }
            roleName = role.getName();
            roles.put(roleName, role);
        }
        for (JCRNodeWrapper jcrNodeWrapper : JCRContentUtils.getChildrenOfType(roleNode, Constants.JAHIANT_ROLE)) {
            processRole(jcrNodeWrapper, roleName, false);
        }
    }

    @Override
    public ContentIntegrityErrorList finalizeIntegrityTestInternal(JCRNodeWrapper node, Collection<String> excludedPaths) {
        roles.clear();
        return null;
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

    private ContentIntegrityErrorList checkExternalAce(JCRNodeWrapper externalAceNode) throws RepositoryException {
        final ContentIntegrityErrorList errors = createEmptyErrorsList();
        errors.addAll(checkPrincipalOnAce(externalAceNode));
        errors.addAll(checkAceNodeName(externalAceNode, true));

        final String aceType;
        if (!externalAceNode.hasProperty(J_ACE_TYPE)) {
            errors.addError(createError(externalAceNode, NO_ACE_TYPE_PROP, "External ACE without property ".concat(J_ACE_TYPE)));
        } else if (!StringUtils.equals(Constants.ACE_TYPE_GRANT, aceType = externalAceNode.getPropertyAsString(J_ACE_TYPE))) {
            errors.addError(createError(externalAceNode, INVALID_ACE_TYPE_PROP)
                    .addExtraInfo("defined-ace-type", aceType));
        }

        boolean hasPropSourceAce = true, hasPropRoles = true;
        if (!externalAceNode.hasProperty(J_SOURCE_ACE)) {
            hasPropSourceAce = false;
            errors.addError(createError(externalAceNode, NO_SOURCE_ACE_PROP));
        }
        if (!externalAceNode.hasProperty(J_ROLES)) {
            hasPropRoles = false;
            errors.addError(createError(externalAceNode, NO_ROLES_PROP, "External ACE without property j:roles"));
        }

        if (hasPropSourceAce) {
            final JCRValueWrapper[] values = externalAceNode.getProperty(J_SOURCE_ACE).getValues();
            if (values.length == 0) {
                errors.addError(createError(externalAceNode, EMPTY_SOURCE_ACE_PROP));
            }
            for (JCRValueWrapper value : values) {
                JCRNodeWrapper srcAce = null;
                try {
                    srcAce = value.getNode();
                } catch (RepositoryException ignored) {
                }
                if (srcAce == null) {
                    boolean isFalsePositive = false;
                    if (JCRUtils.isInLiveWorkspace(externalAceNode)) {
                        final JCRSessionWrapper defaultSession = JCRUtils.getSystemSession(EDIT_WORKSPACE, true);
                        if (JCRUtils.nodeExists(value.getString(), defaultSession)) isFalsePositive = true;
                    }
                    if (!isFalsePositive)
                        errors.addError(createError(externalAceNode, SOURCE_ACE_BROKEN_REF));
                    continue;
                }

                final String srcAceType;
                final String srcAceIdentifier = srcAce.getIdentifier();
                if (srcAce.hasProperty(J_ACE_TYPE) && !StringUtils.equals(Constants.ACE_TYPE_GRANT, srcAceType = srcAce.getPropertyAsString(J_ACE_TYPE))) {
                    errors.addError(createError(externalAceNode, SOURCE_ACE_NOT_TYPE_GRANT)
                            .addExtraInfo("src-ace-uuid", srcAceIdentifier, true)
                            .addExtraInfo("src-ace-path", srcAce.getPath(), true)
                            .addExtraInfo("src-ace-type", srcAceType)
                            .setExtraMsg(EXTRA_MSG_SOURCE_ACE_NOT_TYPE_GRANT));
                }

                if (hasPropRoles) {
                    if (!srcAce.hasProperty(J_ROLES)) {
                        errors.addError(createError(externalAceNode, ROLES_DIFFER_ON_SOURCE_ACE, String.format("Missing %s property on the source ACE", J_ROLES))
                                .addExtraInfo("src-ace-uuid", srcAceIdentifier, true)
                                .addExtraInfo("src-ace-path", srcAce.getPath(), true)
                                .setExtraMsg(EXTRA_MSG_SRC_ACE_WITHOUT_ROLES_PROP));
                    } else {
                        final List<String> externalAceRoles = getRoleNames(externalAceNode);
                        if (CollectionUtils.isEmpty(externalAceRoles)) {
                            errors.addError(createError(externalAceNode, INVALID_ROLES_PROP, String.format("The property %s has no value", J_ROLES)));
                        } else if (externalAceRoles.size() > 1) {
                            errors.addError(createError(externalAceNode, INVALID_ROLES_PROP, String.format("Unexpected number of roles in the property %s", J_ROLES))
                                    .addExtraInfo(J_ROLES, externalAceRoles));
                        } else {
                            final List<String> srcAceRoles = getRoleNames(srcAce);
                            final String role = externalAceRoles.get(0);

                            final Map<String, String> roleExternalPermissions;
                            final String externalPermissionsName;
                            if (!roles.containsKey(role)) {
                                errors.addError(createError(externalAceNode, ROLE_DOESNT_EXIST, "External ACE defined for a role which does not exist")
                                        .addExtraInfo("role", role));
                            } else if (!(roleExternalPermissions = roles.get(role).getExternalPermissions())
                                    .containsKey(externalPermissionsName = externalAceNode.getPropertyAsString(J_EXTERNAL_PERMISSIONS_NAME))) {
                                errors.addError(createError(externalAceNode, INVALID_EXTERNAL_PERMISSIONS)
                                        .addExtraInfo("role", role)
                                        .addExtraInfo("external-permissions-name", externalPermissionsName)
                                        .addExtraInfo("expected-external-permissions-names", roleExternalPermissions.keySet()));
                            } else {
                                final String externalAcePathPattern = roleExternalPermissions.get(externalPermissionsName);
                                final StringBuilder expectedPath = new StringBuilder();
                                final Matcher matcher = CURRENT_SITE_PATTERN.matcher(externalAcePathPattern);
                                if (matcher.find()) {
                                    expectedPath.append(matcher.replaceFirst(srcAce.getResolveSite().getPath()));
                                } else {
                                    expectedPath.append(externalAcePathPattern);
                                }
                                if (expectedPath.charAt(expectedPath.length() - 1) != Constants.JCR_PATH_SEPARATOR_CHAR) {
                                    expectedPath.append(Constants.JCR_PATH_SEPARATOR_CHAR);
                                }
                                expectedPath.append(Constants.ACL).append(JCR_PATH_SEPARATOR).append(externalAceNode.getName());
                                if (!StringUtils.equals(expectedPath.toString(), externalAceNode.getPath())) {
                                    errors.addError(createError(externalAceNode, INVALID_EXTERNAL_ACE_PATH)
                                            .addExtraInfo("ace-uuid", srcAceIdentifier, true)
                                            .addExtraInfo("ace-path", srcAce.getPath(), true)
                                            .addExtraInfo("role", role)
                                            .addExtraInfo("external-permissions-name", externalPermissionsName)
                                            .addExtraInfo("external-permissions-path", externalAcePathPattern)
                                            .addExtraInfo("external-ace-expected-path", expectedPath, true));
                                }
                            }

                            if (!srcAceRoles.contains(role)) {
                                errors.addError(createError(externalAceNode, ROLES_DIFFER_ON_SOURCE_ACE, "External ACE defined for a role which is not defined on the source ACE")
                                        .addExtraInfo("role", role)
                                        .addExtraInfo("ace-uuid", srcAceIdentifier, true)
                                        .addExtraInfo("ace-path", srcAce.getPath(), true)
                                        .addExtraInfo("ace-roles", srcAceRoles));
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
        }).filter(Objects::nonNull).sorted().collect(Collectors.toList());
    }

    private ContentIntegrityErrorList checkRegularAce(JCRNodeWrapper aceNode) throws RepositoryException {
        final ContentIntegrityErrorList errors = createEmptyErrorsList();
        errors.addAll(checkPrincipalOnAce(aceNode));
        errors.addAll(checkAceNodeName(aceNode, false));

        final boolean isGrantAce;
        final String aceType;
        if (!aceNode.hasProperty(J_ACE_TYPE)) {
            isGrantAce = false;
            aceType = StringUtils.EMPTY;
            errors.addError(createError(aceNode, NO_ACE_TYPE_PROP, "ACE without property ".concat(J_ACE_TYPE)));
        } else {
            aceType = aceNode.getPropertyAsString(J_ACE_TYPE);
            isGrantAce = StringUtils.equals(aceType, Constants.ACE_TYPE_GRANT);
        }

        if (!aceNode.hasProperty(J_ROLES)) {
            errors.addError(createError(aceNode, NO_ROLES_PROP, "ACE without property ".concat(J_ROLES)));
        } else {
            for (JCRValueWrapper roleRef : aceNode.getProperty(J_ROLES).getValues()) {
                final String roleName = roleRef.getString();
                if (!roles.containsKey(roleName)) {
                    errors.addError(createError(aceNode, ROLE_DOESNT_EXIST, "ACE with a role that doesn't exist")
                            .addExtraInfo("role", roleName));
                } else if (isGrantAce) {
                    final Role role = roles.get(roleName);
                    for (String extPerm : role.getExternalPermissions().keySet()) {
                        final PropertyIterator references = aceNode.getWeakReferences();
                        boolean extAceFound = false;
                        while (!extAceFound && references.hasNext()) {
                            final Node extAce = references.nextProperty().getParent();
                            if (!extAce.isNodeType(JNT_EXTERNAL_ACE)) continue;
                            if (extAce.hasProperty(J_EXTERNAL_PERMISSIONS_NAME) &&
                                    StringUtils.equals(extPerm, extAce.getProperty(J_EXTERNAL_PERMISSIONS_NAME).getString()))
                                extAceFound = true;
                        }
                        if (!extAceFound)
                            errors.addError(createError(aceNode, MISSING_EXTERNAL_ACE)
                                    .addExtraInfo("role", roleName)
                                    .addExtraInfo("external-permissions", extPerm)
                                    .addExtraInfo("external-ace-scope", role.getExternalPermissions().get(extPerm)));
                    }
                }
            }
        }

        if (!isGrantAce) {
            final PropertyIterator references = aceNode.getWeakReferences();
            String roles = null;
            while (references.hasNext()) {
                final Node extAce = references.nextProperty().getParent();
                if (extAce.isNodeType(JNT_EXTERNAL_ACE)) {
                    if (roles == null) {
                        if (aceNode.hasProperty(J_ROLES)) {
                            roles = String.join(SPACE, getRoleNames(aceNode));
                        } else {
                            roles = StringUtils.EMPTY;
                        }
                    }
                    errors.addError(createError(aceNode, ACE_NON_GRANT_WITH_EXTERNAL_ACE)
                            .addExtraInfo("ace-type", aceType)
                            .addExtraInfo("ace-roles", roles)
                            .addExtraInfo("external-ace", extAce.getPath(), true));
                }
            }
        }

        return errors;
    }

    private ContentIntegrityErrorList checkPrincipalOnAce(JCRNodeWrapper node) throws RepositoryException {

        if (!node.hasProperty(J_PRINCIPAL)) {
            return createSingleError(createError(node, NO_PRINCIPAL));
        }

        final String principal = node.getProperty(J_PRINCIPAL).getString();
        final JCRSiteNode site = node.getResolveSite();
        final String siteKey = site == null ? null : site.getSiteKey();
        final JCRNodeWrapper principalNode = getPrincipal(siteKey, principal);
        if (principalNode == null) {
            return createSingleError(createError(node, INVALID_PRINCIPAL)
                    .addExtraInfo("invalid principal", principal)
                    .addExtraInfo("site", siteKey)
                    .setExtraMsg(EXTRA_MSG_INVALID_PRINCIPAL));
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

    private ContentIntegrityErrorList checkAceNodeName(JCRNodeWrapper ace, boolean isExternal) {
        final String expectedNodeName;

        if (isExternal) {
            expectedNodeName = new StringBuilder(Constants.EXTERNAL_ACE_NODENAME_PREFIX)
                    .append(ace.getPropertyAsString(J_ROLES))
                    .append(UNDERSCORE)
                    .append(ace.getPropertyAsString(J_EXTERNAL_PERMISSIONS_NAME))
                    .append(UNDERSCORE)
                    .append(JCRContentUtils.replaceColon(ace.getPropertyAsString(J_PRINCIPAL)).replaceAll(SLASH, UNDERSCORE))
                    .toString();
        } else {
            expectedNodeName = new StringBuilder(ace.getPropertyAsString(J_ACE_TYPE))
                    .append(UNDERSCORE)
                    .append(JCRContentUtils.replaceColon(ace.getPropertyAsString(J_PRINCIPAL)).replaceAll(SLASH, UNDERSCORE))
                    .toString();
        }

        if (StringUtils.equals(ace.getName(), expectedNodeName)) return null;
        return createSingleError(
                createError(ace, INVALID_NODENAME)
                        .addExtraInfo("expected-nodename", expectedNodeName, true));
    }

    @Override
    public boolean fixError(JCRNodeWrapper ace, ContentIntegrityError error) throws RepositoryException {
        if (!Constants.EDIT_WORKSPACE.equals(ace.getSession().getWorkspace().getName())) return false;

        final JCRNodeWrapper node = ace.getParent().getParent();
        ContentIntegrityErrorType errorType = error.getErrorType();
        if (errorType.equals(NO_PRINCIPAL)) {
            return false;
        } else if (errorType.equals(INVALID_PRINCIPAL)) {
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

    private static class Role {
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
