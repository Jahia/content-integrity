package org.jahia.modules.contentintegrity.services.util;

import org.apache.commons.lang.StringUtils;
import org.apache.jackrabbit.util.Text;
import org.jahia.modules.contentintegrity.api.FixValuesDefinition;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.query.Query;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The principal of an access control entry (ACE), which an administrator chooses when the fix of an error sets it: the
 * user or the group the ACE grants, or denies, its roles to. The users and the groups offered are the ones the ACE can
 * name at its place: the users and the groups of the server, and on a site, the ones of the site. The name of an ACE
 * tells its principal, GRANT_u_john for the user john: when that principal exists, it is suggested.
 * The ACE is saved with the listeners enabled, so that the ACL listener updates what depends on it.
 */
public final class AclPrincipals {

    public static final String PRINCIPAL_PROPERTY = "j:principal";
    private static final String ACE_TYPE = "jnt:ace";
    private static final String SITES_PATH_PREFIX = "/sites/";
    private static final String USER_PREFIX = "u:";
    private static final String GROUP_PREFIX = "g:";
    private static final String USERS_GROUP = "Users";
    private static final String GROUPS_GROUP = "Groups";
    private static final Pattern ACE_NAME = Pattern.compile("^(?:GRANT|DENY)_([ug])_(.+)$");

    private AclPrincipals() {
    }

    /**
     * @return true if the property holds the principal of an ACE
     */
    public static boolean isAcePrincipal(JCRNodeWrapper node, String propertyName) throws RepositoryException {
        return PRINCIPAL_PROPERTY.equals(propertyName) && node.isNodeType(ACE_TYPE);
    }

    /**
     * @return the description of the principal to choose for the ACE: the users and the groups it can name, the one its
     * name suggests, and what the principal gets
     */
    public static FixValuesDefinition getFixValuesDefinition(JCRNodeWrapper ace) throws RepositoryException {
        final Map<String, String> principals = getPrincipals(ace);
        final String suggested = getSuggestedPrincipal(ace);
        final boolean suggests = suggested != null && principals.containsKey(suggested);
        final List<String> choices = new ArrayList<>(principals.keySet());
        // The users, then the groups, displayed in two groups of the dropdown
        final List<String> groups = new ArrayList<>(choices.size());
        for (String principal : choices) groups.add(principal.startsWith(USER_PREFIX) ? USERS_GROUP : GROUPS_GROUP);
        return new FixValuesDefinition(PRINCIPAL_PROPERTY, PropertyType.nameFromValue(PropertyType.STRING), false, choices,
                Collections.emptyList(), suggests ? Collections.singletonList(suggested) : Collections.emptyList(),
                new ArrayList<>(principals.values()), groups, describe(ace, suggests ? describePrincipal(suggested, principals.get(suggested)) : null));
    }

    /**
     * Sets the principal of the ACE, which has to be one of the users and the groups it can name.
     *
     * @throws RepositoryException if the value is not one of them, with a message which tells why
     */
    public static void setPrincipal(JCRNodeWrapper ace, List<String> values) throws RepositoryException {
        if (values == null || values.size() != 1) throw new ValueFormatException("Choose one user or group");
        final String principal = values.get(0).trim();
        if (!getPrincipals(ace).containsKey(principal)) {
            throw new ConstraintViolationException(String.format("%s is not a user or a group which this access control entry can name", principal));
        }
        try {
            ace.setProperty(PRINCIPAL_PROPERTY, principal);
            ace.saveSession();
        } catch (RepositoryException e) {
            // The system session is shared: a rejected value must not stay pending in it
            ace.getSession().refresh(false);
            throw e;
        }
    }

    /**
     * @return the users then the groups the ACE can name, as u:name or g:name, with their labels, each sorted by label: the
     * name, followed by its site for a user or a group of a site
     */
    public static Map<String, String> getPrincipals(JCRNodeWrapper ace) throws RepositoryException {
        final String siteKey = getSiteKey(ace);
        // The users and the groups are stored in the default workspace
        final JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentSystemSession("default", null, null);
        final Map<String, String> users = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final Map<String, String> groups = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        collect(session, "jnt:user", "/users", USER_PREFIX, null, users);
        collect(session, "jnt:group", "/groups", GROUP_PREFIX, null, groups);
        if (siteKey != null) {
            collect(session, "jnt:user", SITES_PATH_PREFIX + siteKey + "/users", USER_PREFIX, siteKey, users);
            collect(session, "jnt:group", SITES_PATH_PREFIX + siteKey + "/groups", GROUP_PREFIX, siteKey, groups);
        }
        final Map<String, String> principals = new LinkedHashMap<>();
        users.forEach((label, principal) -> principals.put(principal, label));
        groups.forEach((label, principal) -> principals.put(principal, label));
        return principals;
    }

    private static void collect(JCRSessionWrapper session, String type, String root, String prefix, String siteKey,
                                Map<String, String> principalsByLabel) throws RepositoryException {
        if (!session.nodeExists(root)) return;
        final Query query = session.getWorkspace().getQueryManager().createQuery(
                String.format("select * from [%s] as p where isdescendantnode(p, '%s')", type, root), Query.JCR_SQL2);
        final JCRNodeIteratorWrapper nodes = (JCRNodeIteratorWrapper) query.execute().getNodes();
        while (nodes.hasNext()) {
            final String name = ((JCRNodeWrapper) nodes.nextNode()).getName();
            final String label = siteKey == null ? name : String.format("%s (site %s)", name, siteKey);
            principalsByLabel.put(label, prefix + name);
        }
    }



    /**
     * @return the principal the name of the ACE tells, GRANT_u_john for u:john, or null
     */
    public static String getSuggestedPrincipal(JCRNodeWrapper ace) {
        final Matcher matcher = ACE_NAME.matcher(ace.getName());
        if (!matcher.matches()) return null;
        return matcher.group(1) + ":" + Text.unescapeIllegalJcrChars(matcher.group(2));
    }

    private static String describePrincipal(String principal, String label) {
        return (principal.startsWith(USER_PREFIX) ? "user " : "group ") + label;
    }

    private static String describe(JCRNodeWrapper ace, String suggestedPrincipal) throws RepositoryException {
        final boolean deny = ace.hasProperty("j:aceType") && StringUtils.equals(ace.getProperty("j:aceType").getString(), "DENY");
        final List<String> roles = new ArrayList<>();
        if (ace.hasProperty(AclRoles.ROLES_PROPERTY)) {
            for (Value role : ace.getProperty(AclRoles.ROLES_PROPERTY).getValues()) roles.add(role.getString());
        }
        final String securedPath = ace.getParent().getParent().getPath();
        return String.format("This access control entry on %s has no principal. Choose the user or the group which %s %s on this node and its sub-nodes.%s",
                securedPath, deny ? "is denied" : "gets",
                roles.isEmpty() ? "its roles" : "the roles " + String.join(", ", roles),
                suggestedPrincipal == null ? "" : String.format(" Its name suggests the %s.", suggestedPrincipal));
    }

    private static String getSiteKey(JCRNodeWrapper ace) {
        final String path = ace.getPath();
        if (!path.startsWith(SITES_PATH_PREFIX)) return null;
        return StringUtils.substringBefore(path.substring(SITES_PATH_PREFIX.length()), "/");
    }
}
