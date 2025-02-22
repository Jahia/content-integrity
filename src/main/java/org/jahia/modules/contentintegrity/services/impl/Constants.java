package org.jahia.modules.contentintegrity.services.impl;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Constants extends org.jahia.api.Constants {

    private static final Logger logger = LoggerFactory.getLogger(Constants.class);

    public static final String CALCULATION_ERROR = "<calculation error>";
    public static final String SPACE = " ";
    public static final String UNDERSCORE = "_";
    public static final String SLASH = "/";
    public static final String TAB_LVL_2 = StringUtils.repeat(SPACE, 6);
    public static final String TAB_LVL_1 = StringUtils.repeat(SPACE, 3);

    public static final char JCR_PATH_SEPARATOR_CHAR = SLASH.charAt(0);
    public static final String JCR_PATH_SEPARATOR = SLASH;
    public static final String ROOT_NODE_PATH = JCR_PATH_SEPARATOR;
    public static final String MODULES_TREE_PATH = "/modules";
    public static final String MODULES_SUBTREE_PATH_PREFIX = MODULES_TREE_PATH + JCR_PATH_SEPARATOR;
    public static final String NODE_UNDER_MODULES_PATH_PREFIX = MODULES_TREE_PATH + JCR_PATH_SEPARATOR;
    public static final String SITES_TREE_PATH = "/sites";
    public static final String NODE_UNDER_SITE_PATH_PREFIX = SITES_TREE_PATH + JCR_PATH_SEPARATOR;

    public static final String JAHIA_MIX_I18N = "jmix:i18n";
    public static final String TRANSLATION_NODE_PREFIX = "j:translation_";
    public static final String PROPERTY_DEFINITION_NAME_WILDCARD = "*";
    public static final String PROPERTY_DEFINITION_NAME_JCR_PREFIX = "jcr:";
    public static final String JMIX_AUTO_PUBLISH = "jmix:autoPublish";
    public static final String JMIX_LIVE_PROPERTIES = "jmix:liveProperties";
    public static final String J_LIVE_PROPERTIES = "j:liveProperties";
    public static final String JMIX_DELETED_CHILDREN = "jmix:deletedChildren";
    public static final String J_DELETED_CHILDREN = "j:deletedChildren";

    public static final String JNT_EXTERNAL_ACE = "jnt:externalAce";
    public static final String JNT_EXTERNAL_PERMISSIONS = "jnt:externalPermissions";
    public static final String J_PRINCIPAL = "j:principal";
    public static final String J_EXTERNAL_PERMISSIONS_NAME = "j:externalPermissionsName";
    public static final String J_ROLES = "j:roles";
    public static final String J_SOURCE_ACE = "j:sourceAce";
    public static final String EXTERNAL_PERMISSIONS_PATH = "j:path";
    public static final String EXTERNAL_ACE_NODENAME_PREFIX = "REF";
    public static final String J_ACE_TYPE = "j:aceType";
    public static final String ACE_TYPE_GRANT = "GRANT";
    public static final String GUEST_USER_KEY = "u:guest";
    public static final String ROLE_OWNER = "owner";
    public static final String J_PRIVILEGED_ACCESS = "j:privilegedAccess";

    public static final String HOME_PAGE_FLAG = "j:isHomePage";
    public static final String HOME_PAGE_FALLBACK_NAME = "home";

    public static final String J_LOCK_TYPES = "j:lockTypes";
    public static final String J_LOCKTOKEN = "j:locktoken";
    public static final String JCR_LOCK_OWNER = "jcr:lockOwner";
    public static final String JCR_LOCK_IS_DEEP = "jcr:lockIsDeep";
    public static final String LOCK_TYPE_DELETION = " deletion :deletion";

    public static final String JNT_EXTERNAL_PROVIDER_EXTENSION = "jnt:externalProviderExtension";
    public static final String JMIX_EXTERNAL_PROVIDER_EXTENSION = "jmix:externalProviderExtension";

    public static final String JAHIANT_REFERENCEINFIELD_FIELDNAME = "j:fieldName";
    public static final String JAHIANT_REFERENCEINFIELD_LANG_SEP = UNDERSCORE;
    public static final String JAHIAMIX_NOLIVE = "jmix:nolive";

    public static final String MANIFEST_HEADER_CONTENT_INTEGRITY_VERSION = "Content-Integrity-Version";
}
