package org.jahia.modules.aclcleanup;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRObservationManager;
import org.jahia.services.content.JCRPublicationService;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.content.decorator.JCRUserNode;
import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.jahia.services.usermanager.JahiaUser;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyIterator;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.query.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.jahia.api.Constants.ACL;
import static org.jahia.api.Constants.EDIT_WORKSPACE;
import static org.jahia.api.Constants.LIVE_WORKSPACE;

public class AceSanityCheck {

    private static final Logger log = LoggerFactory.getLogger(AceSanityCheck.class);
    private static final String JNT_ACE = "jnt:ace";
    private static final String JNT_EXTERNAL_ACE = "jnt:externalAce";
    private static final String JNT_EXTERNAL_PERMISSIONS = "jnt:externalPermissions";
    private static final String J_PRINCIPAL = "j:principal";
    private static final String J_EXTERNAL_PERMISSIONS_NAME = "j:externalPermissionsName";
    private static final String J_ROLES = "j:roles";
    private static final String J_SOURCE_ACE = "j:sourceAce";
    private static final String J_PATH = "j:path";
    private static final String J_ACE_TYPE = "j:aceType";
    private static final String J_LAST_PUBLISHED = "j:lastPublished";
    private static final String J_PUBLISHED = "j:published";
    private static final String JCR_LAST_MODIFIED = "jcr:lastModified";
    private static final Pattern CURRENT_SITE_PATTERN = Pattern.compile("^currentSite");

    final ScriptLogger logger = new ScriptLogger(log);

    private final Map<String, Role> roles = new HashMap<>();

    private boolean fixErrors = false;
    private boolean checkExternalAce = false;
    private boolean checkRegularAce = false;
    private boolean checkUselessExternalAce = false;
    private boolean checkMissingExternalAce = false;
    private boolean checkPrincipalOnAce = false;

    public void initializeIntegrityTestInternal() {
        final JCRSessionWrapper defaultSession;
        try {
            defaultSession = getEditSession();
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

    public void finalizeIntegrityTestInternal() {
        roles.clear();
        fixErrors = false;
        checkExternalAce = false;
        checkRegularAce = false;
        checkUselessExternalAce = false;
        checkMissingExternalAce = false;
        checkPrincipalOnAce = false;
    }

    public List<String> checkIntegrityAfterChildren(JCRNodeWrapper node) {
        try {
            if (!node.isNodeType(JNT_ACE)) return null;
            if (node.isNodeType(JNT_EXTERNAL_ACE)) {
                checkExternalAce(node);
            } else {
                checkRegularAce(node);
            }
            return logger.getBuffer();
        } catch (RepositoryException e) {
            logger.error("", e);
        }

        return null;
    }

    private void checkExternalAce(JCRNodeWrapper node) throws RepositoryException {
        if (!checkExternalAce) return;

        // TODO tester external ACE qui pointe plusieurs fois vers la meme ACE

        final JCRSessionWrapper session = node.getSession();
        final String path = node.getPath();
        checkPrincipalOnAce(node);
        if (!session.nodeExists(path)) return; // the ACE has been deleted in checkPrincipalOnAce()

        if (!checkUselessExternalAce) return;

        final String aceType;
        if (!node.hasProperty(J_ACE_TYPE)) {
            createError(node, "External ACE without property ".concat(J_ACE_TYPE));
            if (fixErrors) {
                deleteExternalACE(node);
                return;
            }
        } else if (!StringUtils.equals("GRANT", aceType = node.getPropertyAsString(J_ACE_TYPE))) {
            createError(node, String.format("External ACE with an invalid value for %s : %s", J_ACE_TYPE, aceType));
            if (fixErrors) {
                deleteExternalACE(node);
                return;
            }
        }

        boolean hasPropSourceAce = true, hasPropRoles = true;
        if (!node.hasProperty(J_SOURCE_ACE)) {
            hasPropSourceAce = false;
            createError(node, "External ACE without source ACE");
            if (fixErrors) {
                deleteExternalACE(node);
                return;
            }
        }
        if (!node.hasProperty(J_ROLES)) {
            hasPropRoles = false;
            createError(node, "External ACE without property j:roles");
        }

        if (hasPropSourceAce) {
            final List<Value> updatedSourceAce = fixErrors ? new ArrayList<>() : null;
            boolean needUpdateSourceAce = false;
            final JCRValueWrapper[] values = node.getProperty(J_SOURCE_ACE).getValues();
            if (values.length == 0) {
                createError(node, "External ACE without source ACE");
                if (fixErrors) {
                    deleteExternalACE(node);
                    return;
                }
            }
            for (JCRValueWrapper value : values) {
                boolean valueIsValid = true;
                JCRNodeWrapper srcAce = null;
                try {
                    srcAce = value.getNode();
                } catch (RepositoryException ignored) { }
                if (srcAce == null) {
                    boolean isFalsePositive = false;
                    if (isInLiveWorkspace(node)) {
                        final JCRSessionWrapper defaultSession = getEditSession();
                        if (nodeExists(value.getString(), defaultSession)) isFalsePositive = true;
                    }
                    if (!isFalsePositive) {
                        valueIsValid = false;
                        createErrorWithInfos(node, null, "Broken reference to source ACE", ErrorType.SOURCE_ACE_BROKEN_REF);
                        if (fixErrors) {
                            logger.info("  > removing the ACE from the list of sources of the external ACE");
                        }
                    }
                } else {
                    final String srcAceType;
                    if (srcAce.hasProperty(J_ACE_TYPE) && !StringUtils.equals("GRANT", srcAceType = srcAce.getPropertyAsString(J_ACE_TYPE))) {
                        valueIsValid = false;
                        createError(node, String.format("Source ACE with an invalid type: %s", srcAceType));
                        if (fixErrors) {
                            logger.info("  > removing the ACE from the list of sources of the external ACE");
                        }
                    }
                    if (valueIsValid && hasPropRoles) {
                        if (!srcAce.hasProperty(J_ROLES)) {
                            createErrorWithInfos(node, null,
                                    String.format("The roles differ on the external and source ACE, since the %s property is missing on the source ACE", J_ROLES),
                                    ErrorType.ROLES_DIFFER_ON_SOURCE_ACE);
                        } else {
                            final List<String> externalAceRoles = getRoleNames(node);
                            if (CollectionUtils.isEmpty(externalAceRoles)) {
                                createErrorWithInfos(node, null, String.format("The property %s has no value", J_ROLES), ErrorType.INVALID_ROLES_PROP);
                            } else if (externalAceRoles.size() > 1) {
                                createErrorWithInfos(node, null, String.format("Unexpected number of roles in the property %s", J_ROLES), ErrorType.INVALID_ROLES_PROP);
                            } else {
                                final List<String> srcAceRoles = getRoleNames(srcAce);
                                final String role = externalAceRoles.get(0);
                                final String srcAceIdentifier = srcAce.getIdentifier();

                                if (roles.containsKey(role)) {
                                    final String externalPermissionsName = node.getPropertyAsString(J_EXTERNAL_PERMISSIONS_NAME);
                                    final String externalAcePathPattern = roles.get(role).getExternalPermissions().getOrDefault(externalPermissionsName, "");
                                    final StringBuilder expectedPath = new StringBuilder();
                                    final Matcher matcher = CURRENT_SITE_PATTERN.matcher(externalAcePathPattern);
                                    if (matcher.find()) {
                                        expectedPath.append(matcher.replaceFirst(srcAce.getResolveSite().getPath()));
                                    } else {
                                        expectedPath.append(externalAcePathPattern);
                                    }
                                    if (expectedPath.charAt(expectedPath.length() - 1) != '/') {
                                        expectedPath.append('/');
                                    }
                                    expectedPath.append("j:acl/").append(node.getName());
                                    if (!StringUtils.equals(expectedPath.toString(), node.getPath())) {
                                        valueIsValid = false;
                                        final Map<String, Object> infos = new HashMap<>(4);
                                        infos.put("error-type", ErrorType.INVALID_EXTERNAL_ACE_PATH);
                                        infos.put("ace-uuid", srcAceIdentifier);
                                        infos.put("ace-path", srcAce.getPath());
                                        infos.put("role", role);
                                        infos.put("external-permissions-name", externalPermissionsName);
                                        infos.put("external-permissions-path", externalAcePathPattern);
                                        infos.put("external-ace-expected-path", expectedPath);
                                        createErrorWithInfos(node, null, "The external ACE has not the expected path", infos);
                                        if (fixErrors) {
                                            logger.info("  > removing the ACE from the list of sources of the external ACE");
                                        }
                                    }
                                }

                                if (!srcAceRoles.contains(role)) {
                                    valueIsValid = false;
                                    final Map<String, Object> infos = new HashMap<>();
                                    infos.put("error-type", ErrorType.ROLES_DIFFER_ON_SOURCE_ACE);
                                    infos.put("ace-uuid", srcAceIdentifier);
                                    createErrorWithInfos(node, null,
                                            String.format("The external ACE is defined for the role %s, but the ACE (%s) has not this role", role, srcAceIdentifier),
                                            ErrorType.ROLES_DIFFER_ON_SOURCE_ACE);
                                    if (fixErrors) {
                                        logger.info("  > removing the ACE from the list of sources of the external ACE");
                                    }
                                }
                            }
                        }
                    }
                }
                if (valueIsValid) {
                    if (fixErrors) updatedSourceAce.add(value);

                } else needUpdateSourceAce = true;
            }
            //update j:sourceace
            if (fixErrors && needUpdateSourceAce) {
                if (CollectionUtils.isEmpty(updatedSourceAce)) {
                    deleteExternalACE(node);
                } else {
                    if (executeWithDisabledListeners(() -> {
                        node.setProperty(J_SOURCE_ACE, updatedSourceAce.toArray(new Value[0]));
                        node.saveSession();
                    }))
                        logger.info("  > updated the list of source ACE of the external ACE to remove the unrelevant sources");
                }
            }
        }

        return ;
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

    private void checkRegularAce(JCRNodeWrapper node) throws RepositoryException {
        if (!checkRegularAce) return;

        final JCRSessionWrapper session = node.getSession();
        final String path = node.getPath();
        checkPrincipalOnAce(node);
        if (!session.nodeExists(path)) return; // the ACE has been deleted in checkPrincipalOnAce()

        if (!node.hasProperty(J_ACE_TYPE)) {
            createError(node, "ACE without property ".concat(J_ACE_TYPE));
            if (fixErrors) {
                deleteACE(node);
                return;
            }
        }

        if (!node.hasProperty(J_ROLES)) {
            createErrorWithInfos(node, null, "ACE without property j:roles", ErrorType.NO_ROLES_PROP);
        } else {
            for (JCRValueWrapper roleRef : node.getProperty(J_ROLES).getValues()) {
                final String roleName = roleRef.getString();
                if (!roles.containsKey(roleName)) {
                    final Map<String, Object> infos = new HashMap<>(2);
                    infos.put("error-type", ErrorType.ROLE_DOESNT_EXIST);
                    infos.put("role", roleName);
                    createErrorWithInfos(node, null, "ACE with a role that doesn't exist", infos);
                } else if (checkMissingExternalAce && StringUtils.equals("GRANT", node.getPropertyAsString(J_ACE_TYPE))) {
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
                        if (!extAceFound) {
                            createErrorWithInfos(node, null,
                                    String.format("The ACE has a role (%s) which defines external permissions (%s) but no related %s exist",
                                            roleName, extPerm, JNT_EXTERNAL_ACE), null);
                            if (fixErrors) {
                                if (node.hasProperty(J_ACE_TYPE)) {
                                    node.setProperty(J_ACE_TYPE, node.getPropertyAsString(J_ACE_TYPE));
                                    node.saveSession();
                                    logger.info("  > triggered the AclListener on the node");
                                    if (isInDefaultWorkspace(node) && nodeExists(node.getIdentifier(), getLiveSession())) {
                                        publish(Collections.singletonList(node.getParent().getIdentifier()));
                                        logger.info("  > published the parent ACL");
                                    }
                                } else {
                                    logger.error(String.format("  /!\\ the error can't be fixed because the %s has no property %s", JNT_ACE, J_ACE_TYPE));
                                }
                            }
                        }
                    }
                }
            }
        }

        return;
    }

    private void checkPrincipalOnAce(JCRNodeWrapper node) throws RepositoryException {
        if (!checkPrincipalOnAce) return;

        if (!node.hasProperty(J_PRINCIPAL)) {
            createError(node, "ACE without principal");
            if (fixErrors) {
                deleteACE(node);
            }
            return;
        }

        final String principal = node.getProperty(J_PRINCIPAL).getString();
        final JCRSiteNode site = node.getResolveSite();
        final String siteKey = site == null ? null : site.getSiteKey();
        final JCRNodeWrapper principalNode = getPrincipal(siteKey, principal);
        if (principalNode == null) {
            createError(node, String.format("%s not found, but an ACE is defined for it", principal));
            if (fixErrors) {
                deleteACE(node);
            }
            return;
        }
        return;
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

    private boolean executeWithDisabledListeners(Callback callback) {
        JCRObservationManager.setAllEventListenersDisabled(true);
        try {
            callback.execute();
            return true;
        } catch (RepositoryException e) {
            logger.error("", e);
        } finally {
            JCRObservationManager.setAllEventListenersDisabled(false);
        }
        return false;
    }

    private void deleteACE(JCRNodeWrapper ace) {
        if (!fixErrors) return;

        try {
            if (!ace.isNodeType(JNT_ACE)) {
                logger.error("  /!\\ Impossible to delete the node as it is not an ACE: " + ace.getPath());
                return;
            }
            if (ace.isNodeType(JNT_EXTERNAL_ACE))
                deleteExternalACE(ace);
            else deleteRegularACE(ace);
        } catch (RepositoryException re) {
            logger.error("  /!\\ Impossible to delete the ACE", re);
        }
    }

    private void deleteRegularACE(JCRNodeWrapper ace) {
        if (!fixErrors) return;

        try {
            final JCRNodeWrapper node = ace.getParent().getParent();
            final boolean hasPendingModifications = hasPendingModifications(node);
            ace.remove();
            final String workspace = ace.getSession().getWorkspace().getName();
            logger.info(String.format("  > deleted the ACE in the %s workspace", workspace));
            //cleanAclIfNeeded(node);
            ace.saveSession();
            if (EDIT_WORKSPACE.equals(workspace)) {
                republishNode(node, ace, hasPendingModifications);
            }
        } catch (RepositoryException e) {
            logger.error("", e);
        }
    }

    private void republishNode(JCRNodeWrapper node, JCRNodeWrapper ace, boolean hasPendingModifications) throws RepositoryException {
        final boolean isAutoPublish = node.isNodeType("jmix:autoPublish");
        final boolean isVersionable = node.isNodeType("mix:versionable");

        if (!hasPendingModifications) {
            if (isVersionable && !isAutoPublish) {
                publish(Collections.singletonList(node.getIdentifier()));
                logger.info(String.format("  > the node \"%s\" has been republished", node.getName()));
            } else if (isAutoPublish) {
                logger.info(String.format("  > the node \"%s\" has been republished because it is flagged with jmix:autoPublish", node.getName()));
            } else {
                logger.warn(String.format("  > the node \"%s\" had no pending modifications before running the script, but it can't be republished", node.getName()));
            }
        } else if (isAutoPublish) {
            logger.info(String.format("  > the node \"%s\" had some pending modifications and has been republished because it is flagged with jmix:autoPublish", node.getName()));
        } else {
            // The node has pending modifications, and is not auto-published
            if (aclCanBePublished(node)) {
                final JCRNodeWrapper acl = ace.getParent();
                publish(Collections.singletonList(acl.getIdentifier()));
                logger.info(String.format("  > the node \"%s\" can't be republished as it had pending modifications before running the script. Only the acl node has been republished", node.getName()));
            } else {
                logger.warn(String.format("  > the node \"%s\" and its related acl can't be republished as it has never been published.", node.getName()));
            }
        }
    }

    private boolean hasPendingModifications(JCRNodeWrapper node) throws RepositoryException {
        if (!node.hasProperty(J_LAST_PUBLISHED)) return true;
        if (!node.hasProperty(J_PUBLISHED) || !node.getProperty(J_PUBLISHED).getBoolean()) return true;
        final java.util.Calendar lastModified = node.getProperty(JCR_LAST_MODIFIED).getDate();
        final java.util.Calendar lastPublished = node.getProperty(J_LAST_PUBLISHED).getDate();
        return lastModified.after(lastPublished);
    }

    private boolean aclCanBePublished(JCRNodeWrapper node) throws RepositoryException {
        if (!node.hasProperty(J_LAST_PUBLISHED)) return false;
        return node.hasProperty(J_PUBLISHED) && node.getProperty(J_PUBLISHED).getBoolean();
    }

    private void deleteExternalACE(JCRNodeWrapper externalACE) {     // TODO est ce que c'est vraiment exécuté uniquement sur external ACE, ou aussi ACE?
        if (!fixErrors) return;

        executeWithDisabledListeners(() -> {
            final String workspace = externalACE.getSession().getWorkspace().getName();
            if (EDIT_WORKSPACE.equals(workspace)) {
                final JCRSessionWrapper liveSession = getLiveSession();
                if (nodeExists(externalACE.getIdentifier(), liveSession)) {
                    final JCRNodeWrapper liveExternalACE = liveSession.getNodeByUUID(externalACE.getIdentifier());
                    liveExternalACE.remove();
                    logger.info("  > deleted the external ACE in the live workspace");
                    cleanAclIfNeeded(liveExternalACE.getParent().getParent());
                    liveSession.save();
                }
            }
            externalACE.remove();
            cleanAclIfNeeded(externalACE.getParent().getParent());
            externalACE.saveSession();
            logger.info(String.format("  > deleted the external ACE in the %s workspace", workspace));
        });
    }

    private boolean cleanAclIfNeeded(JCRNodeWrapper node) throws RepositoryException {
        if (!fixErrors) return false;

        if (!node.hasNode(ACL)) return false;
        final JCRNodeWrapper acl = node.getNode(ACL);
        if (acl.getNodes().hasNext()) return false;
        acl.remove();
        node.removeMixin("jmix:accessControlled");
        logger.info(String.format("  > deleted the ACL node in the %s workspace as it is now useless", node.getSession().getWorkspace().getName()));
        return true;
    }

    private void publish(List<String> uuids) throws RepositoryException {
        if (!fixErrors) return;

        final JahiaUser realUser = JCRSessionFactory.getInstance().getCurrentUser();
        if (!realUser.isRoot()) {
            final JCRUserNode rootUser = JahiaUserManagerService.getInstance().lookupRootUser();
            JCRSessionFactory.getInstance().setCurrentUser(rootUser.getJahiaUser());
        }

        try {
            JCRPublicationService.getInstance().publish(uuids,
                    Constants.EDIT_WORKSPACE, Constants.LIVE_WORKSPACE, false, null);
        } finally {
            if (!realUser.isRoot()) {
                JCRSessionFactory.getInstance().setCurrentUser(realUser);
            }
        }
    }

    private JCRSessionWrapper getSession(String workspace) throws RepositoryException {
        return JCRSessionFactory.getInstance().getCurrentSystemSession(workspace, null, null);
    }

    private JCRSessionWrapper getEditSession() throws RepositoryException {
        return getSession(EDIT_WORKSPACE);
    }

    private JCRSessionWrapper getLiveSession() throws RepositoryException {
        return getSession(LIVE_WORKSPACE);
    }

    private void createError(JCRNodeWrapper node, String msg) {
        logger.error(String.format("%s : %s", node.getPath(), msg));
    }

    private void createErrorWithInfos(JCRNodeWrapper node, Locale locale, String msg, Object infos) {
        createError(node, msg);
    }

    protected boolean isInDefaultWorkspace(JCRNodeWrapper node) {
        try {
            return Constants.EDIT_WORKSPACE.equals(node.getSession().getWorkspace().getName());
        } catch (RepositoryException e) {
            logger.error("", e);
            return false;
        }
    }

    protected boolean isInLiveWorkspace(JCRNodeWrapper node) {
        return !isInDefaultWorkspace(node);
    }

    protected boolean nodeExists(String uuid, JCRSessionWrapper session) {
        try {
            session.getNodeByUUID(uuid);
            return true;
        } catch (RepositoryException e) {
            return false;
        }
    }

    private String resolveSiteKey(JCRNodeWrapper node) {
        if (node == null) return null;
        final String path = node.getPath();
        if (!path.startsWith("/sites/")) return null;
        return StringUtils.split(path, '/')[1];
    }

    /*
    SETTERS
     */

    public void setFixErrors(boolean fixErrors) {
        this.fixErrors = fixErrors;
    }

    public void setCheckUselessExternalAce(boolean checkUselessExternalAce) {
        this.checkUselessExternalAce = checkUselessExternalAce;
    }

    public void setCheckMissingExternalAce(boolean checkMissingExternalAce) {
        this.checkMissingExternalAce = checkMissingExternalAce;
    }

    public void setCheckExternalAce(boolean checkExternalAce) {
        this.checkExternalAce = checkExternalAce;
    }

    public void setCheckRegularAce(boolean checkRegularAce) {
        this.checkRegularAce = checkRegularAce;
    }

    public void setCheckPrincipalOnAce(boolean checkPrincipalOnAce) {
        this.checkPrincipalOnAce = checkPrincipalOnAce;
    }

    /*
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
     */

    private enum ErrorType {
        NO_PRINCIPAL, INVALID_PRINCIPAL, NO_SOURCE_ACE_PROP, SOURCE_ACE_BROKEN_REF, NO_ROLES_PROP, INVALID_ROLES_PROP,
        INVALID_EXTERNAL_ACE_PATH, ROLES_DIFFER_ON_SOURCE_ACE, ROLE_DOESNT_EXIST
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

    private interface Callback {
        public void execute() throws RepositoryException;
    }
}
