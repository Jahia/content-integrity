package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;

import javax.jcr.RepositoryException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.JAHIANT_USER
})
public class UserAccountSanityCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.SupportsIntegrityErrorFix {

    public static final ContentIntegrityErrorType NOT_OWNER = createErrorType("NOT_OWNER", "The user is not owner of his account node");

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        final String username = String.format("u:%s", node.getName());
        // guest is not owner of his node
        if (StringUtils.equals(username, Constants.GUEST_USER_KEY)) return null;
        final Map<String, List<String[]>> aclEntries = node.getAclEntries();
        if (aclEntries.containsKey(username) && !isOwner(node, aclEntries)) {
            return createSingleError(createError(node, NOT_OWNER));
        }

        return null;
    }

    private boolean isOwner(JCRNodeWrapper node, Map<String, List<String[]>> aclEntries) {
        final String path = node.getPath();
        final List<String[]> entries = aclEntries.get(String.format("u:%s", node.getName()));
        return entries != null && entries.stream().filter(acl -> StringUtils.equals(acl[0], path) && StringUtils.equals(acl[1], Constants.ACE_TYPE_GRANT) && StringUtils.equals(acl[2], Constants.ROLE_OWNER)).count() == 1;
    }

    /*
     * The user is granted the role owner on its account node again, as when the account is created. Its other roles are
     * kept.
     */
    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException {
        if (!NOT_OWNER.equals(error.getErrorType())) return false;
        if (isOwner(node, node.getAclEntries())) return true;
        node.grantRoles(String.format("u:%s", node.getName()), Collections.singleton(Constants.ROLE_OWNER));
        node.saveSession();
        return true;
    }
}
