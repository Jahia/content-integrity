package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
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

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        final String username = String.format("u:%s", node.getName());
        // guest is not owner of his node
        if (StringUtils.equals(username, "u:guest")) return null;
        final Map<String, List<String[]>> aclEntries = node.getAclEntries();
        if (aclEntries.containsKey(username)) {
            final String path = node.getPath();
            if (aclEntries.get(username).stream().filter(acl -> StringUtils.equals(acl[0], path) && StringUtils.equals(acl[1], "GRANT") && StringUtils.equals(acl[2], "owner")).count() != 1) {
                return createSingleError(createError(node, "The user is not owner of his account node"));
            }
        }

        return null;
    }
}
