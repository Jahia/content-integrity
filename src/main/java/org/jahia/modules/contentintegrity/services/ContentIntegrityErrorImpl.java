package org.jahia.modules.contentintegrity.services;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.services.content.JCRNodeWrapper;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import static org.jahia.modules.contentintegrity.services.Utils.getSiteKey;

public class ContentIntegrityErrorImpl implements ContentIntegrityError {

    private static final Logger logger = LoggerFactory.getLogger(ContentIntegrityErrorImpl.class);

    private static final String EXTRA_MESSAGE_KEY = "extra-message";
    private static final String MODULE_PREFIX = "module ";
    private static final String NO_SITE = "<no site> ";
    private static final int EXTRA_INFO_STRING_VALUE_MAX_LENGTH = 100;

    private final String id;
    private final String path;
    private final String site;
    private final String uuid;
    private final String primaryType;
    private final String mixins;
    private final String locale;
    private final String workspace;
    private final String constraintMessage;
    private final String integrityCheckName;
    private final String integrityCheckID;
    private ContentIntegrityErrorType errorType = null;
    private List<String> extraInfosKeys;
    private Map<String, Object> extraInfos;
    private Map<String, Object> specificExtraInfos;
    private boolean fixed = false;

    private ContentIntegrityErrorImpl(String path, String uuid, String primaryType, String mixins, String workspace,
                                      String locale, ContentIntegrityErrorType errorType, String constraintMessage,
                                      String integrityCheckName, String integrityCheckID) {
        id = UUID.randomUUID().toString();
        this.path = path;
        site = Optional.ofNullable(getSiteKey(path, true, MODULE_PREFIX::concat)).orElse(NO_SITE);
        this.uuid = uuid;
        this.primaryType = primaryType;
        this.mixins = mixins;
        this.locale = locale;
        this.workspace = workspace;
        this.errorType = errorType;
        this.constraintMessage = constraintMessage;
        this.integrityCheckName = integrityCheckName;
        this.integrityCheckID = integrityCheckID;
    }

    public static ContentIntegrityError createError(JCRNodeWrapper node, String locale, ContentIntegrityErrorType errorType, String message, ContentIntegrityCheck integrityCheck) {
        final String errorMessage = StringUtils.defaultIfBlank(message, errorType.getDefaultMessage());
        try {
            final NodeType[] mixinNodeTypes = node.getMixinNodeTypes();
            final String mixins;
            if (mixinNodeTypes == null || mixinNodeTypes.length == 0) mixins = null;
            else {
                final StringBuilder mixinsBuilder = new StringBuilder(mixinNodeTypes[0].getName());
                for (int i = 1; i < mixinNodeTypes.length; i++)
                    mixinsBuilder.append(",").append(mixinNodeTypes[i].getName());
                mixins = mixinsBuilder.toString();
            }
            return new ContentIntegrityErrorImpl(node.getPath(), node.getIdentifier(), node.getPrimaryNodeType().getName(),
                    mixins, node.getSession().getWorkspace().getName(), locale, errorType, errorMessage,
                    integrityCheck.getName(), integrityCheck.getId());
        } catch (RepositoryException e) {
            logger.error("", e);
        }

        return new ContentIntegrityErrorImpl(null, null, null, null, null, locale, errorType, errorMessage, integrityCheck.getName(), integrityCheck.getId());
    }

    @Override
    public JSONObject toJSON() {
        try {
            return (new JSONObject()).put("errorType", integrityCheckName).put("workspace", workspace).put("path", path)
                    .put("uuid", uuid).put("nt", getFullNodetype()).put("locale", Optional.ofNullable(locale).orElse(StringUtils.EMPTY))
                    .put("message", constraintMessage).put("fixed", fixed);
        } catch (JSONException e) {
            logger.error("", e);
        }
        return null;
    }

    @Override
    public String getErrorID() {
        return id;
    }

    @Override
    public boolean isFixed() {
        return fixed;
    }

    @Override
    public void setFixed(boolean fixed) {
        this.fixed = fixed;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    @Override
    public String getLocale() {
        return locale;
    }

    @Override
    public String getPrimaryType() {
        return primaryType;
    }

    @Override
    public String getMixins() {
        return mixins;
    }

    @Override
    public String getConstraintMessage() {
        return constraintMessage;
    }

    @Override
    public String getWorkspace() {
        return workspace;
    }

    @Override
    public String getSite() {
        return site;
    }

    @Override
    public String getIntegrityCheckName() {
        return integrityCheckName;
    }

    @Override
    public String getIntegrityCheckID() {
        return integrityCheckID;
    }

    @Override
    public Map<String, Object> getExtraInfos() {
        return Collections.unmodifiableMap(Optional.ofNullable(extraInfos).orElse(Collections.emptyMap()));
    }

    @Override
    public Map<String, Object> getSpecificExtraInfos() {
        return Collections.unmodifiableMap(Optional.ofNullable(specificExtraInfos).orElse(Collections.emptyMap()));
    }

    @Override
    public Map<String, Object> getAllExtraInfos() {
        if (CollectionUtils.isEmpty(extraInfosKeys)) return Collections.emptyMap();

        final LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (String key : extraInfosKeys) {
            map.put(key, getExtraInfo(key));
        }

        return map;
    }

    @Override
    public Object getExtraInfo(String key) {
        if (extraInfosKeys == null || !extraInfosKeys.contains(key)) return null;

        return extraInfos != null && extraInfos.containsKey(key) ? extraInfos.get(key) : specificExtraInfos.get(key);
    }

    @Override
    public ContentIntegrityError addExtraInfo(String key, Object value) {
        return addExtraInfo(key, value, false);
    }

    @Override
    public ContentIntegrityError addExtraInfo(String key, Object value, boolean isErrorSpecific) {
        return addExtraInfo(key, value, isErrorSpecific, false);
    }

    private ContentIntegrityError addExtraInfo(String key, Object value, boolean isErrorSpecific, boolean bypassLengthLimit) {
        if (extraInfosKeys == null) extraInfosKeys = new ArrayList<>();
        else if (extraInfosKeys.contains(key))
            throw new IllegalArgumentException(String.format("Key already defined: %s", key));

        extraInfosKeys.add(key);
        final Object storedValue = value instanceof String && !bypassLengthLimit ? StringUtils.left((String) value, EXTRA_INFO_STRING_VALUE_MAX_LENGTH) : value;
        if (isErrorSpecific) {
            if (specificExtraInfos == null) specificExtraInfos = new TreeMap<>();
            specificExtraInfos.put(key, storedValue);
        } else {
            if (extraInfos == null) extraInfos = new TreeMap<>();
            extraInfos.put(key, storedValue);
        }
        return this;
    }

    @Override
    public ContentIntegrityError setErrorType(Object type) {
        if (!(errorType instanceof ContentIntegrityErrorTypeImplLegacy)) throw new UnsupportedOperationException("The error type can't be changed afterwards");
        ((ContentIntegrityErrorTypeImplLegacy)getErrorType()).setKeyLegacy(type.toString());
        return this;
    }

    @Override
    public ContentIntegrityErrorType getErrorType() {
        return errorType;
    }

    @Override
    public ContentIntegrityError setExtraMsg(String msg) {
        return addExtraInfo(EXTRA_MESSAGE_KEY, msg, false, true);
    }

    private String getFullNodetype() {
        if (StringUtils.isBlank(mixins))
            return primaryType;
        return String.format("%s (%s)", primaryType, mixins);
    }
}
