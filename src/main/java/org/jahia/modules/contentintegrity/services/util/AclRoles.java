package org.jahia.modules.contentintegrity.services.util;

import org.apache.commons.lang.StringUtils;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;

import javax.jcr.RepositoryException;
import javax.jcr.query.Query;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The roles of an access control entry (ACE), which an administrator chooses when the fix of an error sets them. Setting a
 * role on an ACE grants, or denies, its permissions to the principal of the ACE: only the roles which can be given at the
 * place of the ACE are offered, as the role groups of Jahia allow them:
 * - on content, the roles of the groups edit-role and live-role;
 * - on a site node, the roles of the group site-role too;
 * - out of the sites, for example on the root node, the roles of every group.
 * The hidden roles are not offered: they are set by the platform.
 */
public final class AclRoles {

    public static final String ROLES_PROPERTY = "j:roles";
    private static final String ACE_TYPE = "jnt:ace";
    private static final String SITE_TYPE = "jnt:virtualsite";
    private static final Collection<String> CONTENT_ROLE_GROUPS = Arrays.asList("edit-role", "live-role");
    private static final String SITE_ROLE_GROUP = "site-role";
    private static final String SITES_PATH_PREFIX = "/sites/";

    private AclRoles() {
    }

    /**
     * @return true if the property holds the roles of an ACE
     */
    public static boolean isAceRoles(JCRNodeWrapper node, String propertyName) throws RepositoryException {
        return ROLES_PROPERTY.equals(propertyName) && node.isNodeType(ACE_TYPE);
    }

    /**
     * @return the names of the roles which can be given on the ACE, with their labels, "Title (name)", sorted by label
     */
    public static Map<String, String> getGrantableRoles(JCRNodeWrapper ace) throws RepositoryException {
        final JCRNodeWrapper securedNode = getSecuredNode(ace);
        final boolean onContent = StringUtils.startsWith(securedNode.getPath(), SITES_PATH_PREFIX);
        final boolean onSite = onContent && securedNode.isNodeType(SITE_TYPE);

        // The roles are defined in the default workspace, with their titles in English
        final JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", Locale.ENGLISH, null);
        final Query query = session.getWorkspace().getQueryManager().createQuery("select * from [jnt:role]", Query.JCR_SQL2);
        final Map<String, String> rolesByLabel = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final JCRNodeIteratorWrapper roles = (JCRNodeIteratorWrapper) query.execute().getNodes();
        while (roles.hasNext()) {
            final JCRNodeWrapper role = (JCRNodeWrapper) roles.nextNode();
            if (role.hasProperty("j:hidden") && role.getProperty("j:hidden").getBoolean()) continue;
            final String group = role.hasProperty("j:roleGroup") ? role.getProperty("j:roleGroup").getString() : null;
            final boolean grantable = !onContent || CONTENT_ROLE_GROUPS.contains(group) || (onSite && SITE_ROLE_GROUP.equals(group));
            if (!grantable) continue;
            final String name = role.getName();
            final String title = role.getDisplayableName();
            rolesByLabel.put(StringUtils.isBlank(title) || StringUtils.equals(title, name) ? name : String.format("%s (%s)", title, name), name);
        }
        final Map<String, String> grantable = new LinkedHashMap<>();
        rolesByLabel.forEach((label, name) -> grantable.put(name, label));
        return grantable;
    }

    /**
     * @return what the roles chosen for the ACE do: grant or deny their permissions, to whom, and where
     */
    public static String describe(JCRNodeWrapper ace) throws RepositoryException {
        final String principal = ace.hasProperty("j:principal") ? ace.getProperty("j:principal").getString() : null;
        final boolean deny = ace.hasProperty("j:aceType") && StringUtils.equals(ace.getProperty("j:aceType").getString(), "DENY");
        return String.format("This access control entry of %s on %s has no role. Choose the roles to %s %s on this node and its sub-nodes.",
                describePrincipal(principal), getSecuredNode(ace).getPath(), deny ? "deny to" : "grant to", describePrincipal(principal));
    }

    // The node whose ACL holds the ACE: the ACE is a child of the j:acl node of the secured node
    private static JCRNodeWrapper getSecuredNode(JCRNodeWrapper ace) throws RepositoryException {
        return ace.getParent().getParent();
    }

    private static String describePrincipal(String principal) {
        if (StringUtils.startsWith(principal, "u:")) return "the user " + principal.substring(2);
        if (StringUtils.startsWith(principal, "g:")) return "the group " + principal.substring(2);
        return StringUtils.isBlank(principal) ? "its principal" : principal;
    }
}
