package org.jahia.modules.contentintegrity.services.checks;

import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.modules.contentintegrity.services.util.RepairUtils;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRGroupNode;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.sites.JahiaSitesService;
import org.jahia.services.usermanager.JahiaGroupManagerService;
import org.jahia.services.usermanager.JahiaUserManagerService;
import org.osgi.service.component.annotations.Component;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.query.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.jahia.modules.contentintegrity.services.impl.Constants.ACE_TYPE_GRANT;
import static org.jahia.modules.contentintegrity.services.impl.Constants.ACL;
import static org.jahia.modules.contentintegrity.services.impl.Constants.EDIT_WORKSPACE;
import static org.jahia.modules.contentintegrity.services.impl.Constants.JAHIANT_VIRTUALSITE;
import static org.jahia.modules.contentintegrity.services.impl.Constants.JNT_EXTERNAL_ACE;
import static org.jahia.modules.contentintegrity.services.impl.Constants.J_ACE_TYPE;
import static org.jahia.modules.contentintegrity.services.impl.Constants.J_PRINCIPAL;
import static org.jahia.modules.contentintegrity.services.impl.Constants.J_ROLES;
import static org.jahia.modules.contentintegrity.services.impl.Constants.LIVE_WORKSPACE;
import static org.jahia.services.usermanager.JahiaGroupManagerService.PRIVILEGED_GROUPNAME;
import static org.jahia.services.usermanager.JahiaGroupManagerService.SITE_PRIVILEGED_GROUPNAME;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_WS + "=" + EDIT_WORKSPACE,
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + JAHIANT_VIRTUALSITE,
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_SUBTREES + "=/sites"
})
public class SiteLevelSystemGroupsCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.SupportsIntegrityErrorFix {

    private static final String EXTRA_MSG_PRIVILEGED_GROUP_NOT_EXIST = String.format("The '%s' group is created at server installation time, at server level, and should never be deleted", PRIVILEGED_GROUPNAME);
    private static final String EXTRA_MSG_SITE_PRIVILEGED_GROUP_NOT_EXIST = String.format("The '%s' group is created at site creation time, at site level, and should never be deleted", SITE_PRIVILEGED_GROUPNAME);
    private static final String EXTRA_MSG_SITE_PRIVILEGED_NOT_MEMBER_PRIVILEGED_GROUP = String.format("The '%s' group of each site must be member of the server level group '%s", SITE_PRIVILEGED_GROUPNAME, PRIVILEGED_GROUPNAME);

    public static final ContentIntegrityErrorType GROUP_DOES_NOT_EXIST = createErrorType("GROUP_DOES_NOT_EXIST", "Missing system group");
    public static final ContentIntegrityErrorType MISSING_MEMBERSHIP = createErrorType("MISSING_MEMBERSHIP", "Missing member in a system group");

    private static final String PRIVILEGED_ROLE = "privileged";
    private static final String SYSTEM_SITE_KEY = "systemsite";
    // The folders of a site whose ACL grants the role privileged to its group site-privileged, as site.xml creates them
    private static final List<String> SITE_PRIVILEGED_FOLDERS = Arrays.asList("users", "groups");
    // The principals which the ACL listener never adds to the group site-privileged
    private static final List<String> PRIVILEGED_GROUPS = Arrays.asList("g:" + PRIVILEGED_GROUPNAME, "g:" + SITE_PRIVILEGED_GROUPNAME);

    private JahiaGroupManagerService jgms;
    private boolean missingRootPrivilegedGroupLogged = false;

    @Override
    protected void reset() {
        missingRootPrivilegedGroupLogged = false;
    }

    @Override
    public void initializeIntegrityTestInternal(JCRNodeWrapper node, Collection<String> excludedPaths) {
        if (jgms == null)
            jgms = BundleUtils.getOsgiService(JahiaGroupManagerService.class, null);
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        if (!(node instanceof JCRSiteNode)) {
            return createSingleError(createFrameworkError(node, String.format("Unexpected non site node: %s", node.getPath())));
        }
        final JCRSiteNode site = (JCRSiteNode) node;
        final JCRGroupNode privGroup;
        final ContentIntegrityErrorList errors = createEmptyErrorsList();
        try {
            privGroup = jgms.lookupGroup(null, PRIVILEGED_GROUPNAME, site.getSession());
            if (privGroup == null && !missingRootPrivilegedGroupLogged) {
                // The 'privileged' group is defined at server level. If missing, a unique error will be logged while scanning the first site
                final ContentIntegrityError error = createError(site.getSession().getRootNode(), GROUP_DOES_NOT_EXIST, String.format("The '%s' group does not exist", PRIVILEGED_GROUPNAME))
                        .addExtraInfo("group-name", PRIVILEGED_GROUPNAME)
                        .setExtraMsg(EXTRA_MSG_PRIVILEGED_GROUP_NOT_EXIST);
                errors.addError(error);
                missingRootPrivilegedGroupLogged = true;
            }

            final JCRGroupNode sitePrivGroup = jgms.lookupGroup(site.getSiteKey(), SITE_PRIVILEGED_GROUPNAME, site.getSession());
            if (sitePrivGroup == null) {
                final ContentIntegrityError error = createError(site, GROUP_DOES_NOT_EXIST, String.format("The '%s' group doesn't exist in the site", SITE_PRIVILEGED_GROUPNAME))
                        .addExtraInfo("group-name", SITE_PRIVILEGED_GROUPNAME)
                        .addExtraInfo("site-name", site.getDisplayableName(), true)
                        .setExtraMsg(EXTRA_MSG_SITE_PRIVILEGED_GROUP_NOT_EXIST);
                errors.addError(error);
            }

            if (privGroup != null && sitePrivGroup != null && !privGroup.isMember(sitePrivGroup)) {
                final ContentIntegrityError error = createError(site, MISSING_MEMBERSHIP, String.format("The '%s' group of a site is not member of the '%s' group",
                        SITE_PRIVILEGED_GROUPNAME, PRIVILEGED_GROUPNAME))
                        .addExtraInfo("missing-member", SITE_PRIVILEGED_GROUPNAME, true)
                        .addExtraInfo("group-missing-a-member", PRIVILEGED_GROUPNAME)
                        .addExtraInfo("site-name", site.getDisplayableName(), true)
                        .setExtraMsg(EXTRA_MSG_SITE_PRIVILEGED_NOT_MEMBER_PRIVILEGED_GROUP);
                errors.addError(error);
            }
        } catch (RepositoryException e) {
            errors.addError(createFrameworkError(site, e));
        }

        return errors;
    }

    /*
     * The system groups are restored as Jahia creates them with a site (JahiaSitesService.addSite):
     * - the server group privileged, hidden, whose members are the groups site-privileged of the sites;
     * - the group site-privileged of a site, member of privileged, granted the role privileged on the site and on its
     *   folders users and groups. Its members are the principals granted a role with a privileged access in the site,
     *   as the ACL listener adds them.
     * The ACL changed on a node already published is published.
     */
    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException {
        if (jgms == null) jgms = BundleUtils.getOsgiService(JahiaGroupManagerService.class, null);
        final JCRSessionWrapper session = node.getSession();
        if (GROUP_DOES_NOT_EXIST.equals(error.getErrorType())) {
            final Object groupName = error.getExtraInfos().get("group-name");
            if (PRIVILEGED_GROUPNAME.equals(groupName)) return restorePrivilegedGroup(session);
            if (SITE_PRIVILEGED_GROUPNAME.equals(groupName)) return restoreSitePrivilegedGroup(node.getResolveSite());
            return false;
        }
        if (MISSING_MEMBERSHIP.equals(error.getErrorType())) {
            final JCRSiteNode site = node.getResolveSite();
            if (site == null) return false;
            final JCRGroupNode privGroup = jgms.lookupGroup(null, PRIVILEGED_GROUPNAME, session);
            final JCRGroupNode sitePrivGroup = jgms.lookupGroup(site.getSiteKey(), SITE_PRIVILEGED_GROUPNAME, session);
            // A missing group is reported by GROUP_DOES_NOT_EXIST, whose fix restores the membership as well
            if (privGroup == null || sitePrivGroup == null) return false;
            if (!privGroup.isMember(sitePrivGroup)) {
                privGroup.addMember(sitePrivGroup);
                session.save();
            }
            return true;
        }
        return false;
    }

    private boolean restorePrivilegedGroup(JCRSessionWrapper session) throws RepositoryException {
        JCRGroupNode privGroup = jgms.lookupGroup(null, PRIVILEGED_GROUPNAME, session);
        if (privGroup == null) {
            privGroup = jgms.createGroup(null, PRIVILEGED_GROUPNAME, null, true, session);
            session.save();
        }
        for (JCRSiteNode site : BundleUtils.getOsgiService(JahiaSitesService.class, null).getSitesNodeList(session)) {
            final JCRGroupNode sitePrivGroup = jgms.lookupGroup(site.getSiteKey(), SITE_PRIVILEGED_GROUPNAME, session);
            if (sitePrivGroup != null && !privGroup.isMember(sitePrivGroup)) privGroup.addMember(sitePrivGroup);
        }
        session.save();
        return true;
    }

    private boolean restoreSitePrivilegedGroup(JCRSiteNode site) throws RepositoryException {
        if (site == null) return false;
        final JCRSessionWrapper session = site.getSession();
        final String siteKey = site.getSiteKey();
        JCRGroupNode sitePrivGroup = jgms.lookupGroup(siteKey, SITE_PRIVILEGED_GROUPNAME, session);
        if (sitePrivGroup == null) {
            sitePrivGroup = jgms.createGroup(siteKey, SITE_PRIVILEGED_GROUPNAME, null, false, session);
            session.save();
        }
        final JCRGroupNode privGroup = jgms.lookupGroup(null, PRIVILEGED_GROUPNAME, session);
        if (privGroup != null && !privGroup.isMember(sitePrivGroup)) privGroup.addMember(sitePrivGroup);
        for (JCRNodeWrapper principal : getPrincipalsWithPrivilegedAccess(site)) {
            if (!sitePrivGroup.isMember(principal)) sitePrivGroup.addMember(principal);
        }

        final List<JCRNodeWrapper> securedNodes = new ArrayList<>();
        if (!SYSTEM_SITE_KEY.equals(siteKey)) {
            site.grantRoles("g:" + SITE_PRIVILEGED_GROUPNAME, Collections.singleton(PRIVILEGED_ROLE));
            site.denyRoles("g:" + PRIVILEGED_GROUPNAME, Collections.singleton(PRIVILEGED_ROLE));
            securedNodes.add(site);
        }
        for (String folder : SITE_PRIVILEGED_FOLDERS) {
            if (!site.hasNode(folder)) continue;
            final JCRNodeWrapper folderNode = site.getNode(folder);
            folderNode.grantRoles("g:" + SITE_PRIVILEGED_GROUPNAME, Collections.singleton(PRIVILEGED_ROLE));
            securedNodes.add(folderNode);
        }
        session.save();

        final JCRSessionWrapper liveSession = JCRUtils.getSystemSession(LIVE_WORKSPACE, false);
        final List<String> acls = new ArrayList<>();
        for (JCRNodeWrapper securedNode : securedNodes) {
            if (securedNode.hasNode(ACL) && JCRUtils.nodeExists(securedNode.getIdentifier(), liveSession)) {
                acls.add(securedNode.getNode(ACL).getIdentifier());
            }
        }
        if (!acls.isEmpty()) RepairUtils.publishAsRoot(acls, EDIT_WORKSPACE, LIVE_WORKSPACE);
        return true;
    }

    /*
     * The principals granted, in the site, a role with a privileged access: the ACL listener makes them members of the
     * group site-privileged when the role is granted
     */
    private Collection<JCRNodeWrapper> getPrincipalsWithPrivilegedAccess(JCRSiteNode site) throws RepositoryException {
        final JCRSessionWrapper session = site.getSession();
        final Set<String> privilegedRoles = new HashSet<>();
        final JCRNodeIteratorWrapper roles = (JCRNodeIteratorWrapper) session.getWorkspace().getQueryManager()
                .createQuery("select * from [jnt:role] where [j:privilegedAccess] = true", Query.JCR_SQL2).execute().getNodes();
        while (roles.hasNext()) privilegedRoles.add(((JCRNodeWrapper) roles.nextNode()).getName());

        final Set<String> principalKeys = new LinkedHashSet<>();
        final JCRNodeIteratorWrapper aces = (JCRNodeIteratorWrapper) session.getWorkspace().getQueryManager()
                .createQuery(String.format("select * from [jnt:ace] as ace where isdescendantnode(ace, ['%s'])", site.getPath()), Query.JCR_SQL2)
                .execute().getNodes();
        while (aces.hasNext()) {
            final JCRNodeWrapper ace = (JCRNodeWrapper) aces.nextNode();
            if (ace.isNodeType(JNT_EXTERNAL_ACE) || !ace.hasProperty(J_PRINCIPAL) || !ace.hasProperty(J_ROLES)
                    || !ACE_TYPE_GRANT.equals(ace.getPropertyAsString(J_ACE_TYPE))) continue;
            final String principal = ace.getProperty(J_PRINCIPAL).getString();
            if (PRIVILEGED_GROUPS.contains(principal)) continue;
            for (Value role : ace.getProperty(J_ROLES).getValues()) {
                if (privilegedRoles.contains(role.getString())) {
                    principalKeys.add(principal);
                    break;
                }
            }
        }

        final List<JCRNodeWrapper> principals = new ArrayList<>();
        for (String principalKey : principalKeys) {
            final String name = principalKey.substring(2);
            JCRNodeWrapper principal = null;
            if (principalKey.startsWith("u:")) {
                principal = BundleUtils.getOsgiService(JahiaUserManagerService.class, null).lookupUser(name, site.getSiteKey());
            } else if (principalKey.startsWith("g:")) {
                principal = jgms.lookupGroup(site.getSiteKey(), name, session);
                if (principal == null) principal = jgms.lookupGroup(null, name, session);
            }
            if (principal != null) principals.add(principal);
        }
        return principals;
    }
}
