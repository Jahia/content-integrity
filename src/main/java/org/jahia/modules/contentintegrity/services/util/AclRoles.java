package org.jahia.modules.contentintegrity.services.util;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.FixValuesDefinition;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;

import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFactory;
import javax.jcr.ValueFormatException;
import javax.jcr.nodetype.ConstraintViolationException;
import javax.jcr.query.Query;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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
 * The ACE is saved with the listeners enabled, so that the ACL listener updates what depends on it, such as the external
 * ACE of a role.
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
     * @return the description of the roles to choose for the ACE: the roles which can be given at its place, and what they do
     */
    public static FixValuesDefinition getFixValuesDefinition(JCRNodeWrapper ace) throws RepositoryException {
        final Map<String, String> roles = getGrantableRoles(ace);
        return new FixValuesDefinition(ROLES_PROPERTY, PropertyType.nameFromValue(PropertyType.NAME), true, new ArrayList<>(roles.keySet()),
                Collections.emptyList(), Collections.emptyList(), new ArrayList<>(roles.values()), describe(ace));
    }

    /**
     * Sets the roles of the ACE, which have to be roles which can be given at its place.
     *
     * @throws RepositoryException if a value is not one of them, with a message which tells why
     */
    public static void setRoles(JCRNodeWrapper ace, List<String> values) throws RepositoryException {
        final List<String> roles = new ArrayList<>();
        if (values != null) {
            values.stream().filter(StringUtils::isNotBlank).map(String::trim).distinct().forEach(roles::add);
        }
        if (roles.isEmpty()) throw new ValueFormatException("Choose at least one role");
        final Map<String, String> grantable = getGrantableRoles(ace);
        for (String role : roles) {
            if (!grantable.containsKey(role)) {
                throw new ConstraintViolationException(String.format("The role %s can't be given on this access control entry", role));
            }
        }
        final ValueFactory valueFactory = ace.getSession().getValueFactory();
        final Value[] jcrValues = new Value[roles.size()];
        for (int i = 0; i < jcrValues.length; i++) jcrValues[i] = valueFactory.createValue(roles.get(i), PropertyType.NAME);
        try {
            ace.setProperty(ROLES_PROPERTY, jcrValues);
            ace.saveSession();
        } catch (RepositoryException e) {
            // The system session is shared: a rejected value must not stay pending in it
            ace.getSession().refresh(false);
            throw e;
        }
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
