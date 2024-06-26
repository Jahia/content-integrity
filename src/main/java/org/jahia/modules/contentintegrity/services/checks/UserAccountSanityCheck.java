package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.jahia.services.content.JCRNodeWrapper;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.JAHIANT_USER
})
public class UserAccountSanityCheck extends AbstractContentIntegrityCheck {

    private static final Logger logger = LoggerFactory.getLogger(UserAccountSanityCheck.class);

    public static final ContentIntegrityErrorType NOT_OWNER = createErrorType("NOT_OWNER", "The user is not owner of his account node");

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        final String username = String.format("u:%s", node.getName());
        // guest is not owner of his node
        if (StringUtils.equals(username, Constants.GUEST_USER_KEY)) return null;
        final Map<String, List<String[]>> aclEntries = node.getAclEntries();
        if (aclEntries.containsKey(username)) {
            final String path = node.getPath();
            if (aclEntries.get(username).stream().filter(acl -> StringUtils.equals(acl[0], path) && StringUtils.equals(acl[1], Constants.ACE_TYPE_GRANT) && StringUtils.equals(acl[2], Constants.ROLE_OWNER)).count() != 1) {
                return createSingleError(createError(node, NOT_OWNER));
            }
        }

        return null;
    }
}
