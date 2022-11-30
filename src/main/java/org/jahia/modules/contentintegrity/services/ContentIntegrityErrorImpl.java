package org.jahia.modules.contentintegrity.services;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.services.content.JCRNodeWrapper;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.jahia.modules.contentintegrity.services.Utils.appendToCSVLine;

public class ContentIntegrityErrorImpl implements ContentIntegrityError {

    private static final Logger logger = LoggerFactory.getLogger(ContentIntegrityErrorImpl.class);

    private final String path;
    private final String uuid;
    private final String primaryType;
    private final String mixins;
    private final String locale;
    private final String workspace;
    private final String constraintMessage;
    private final String integrityCheckName;
    private final String integrityCheckID;
    private List<String> extraInfosKeys;
    private Map<String, Object> extraInfos;
    private Map<String, Object> specificExtraInfos;
    private boolean fixed = false;

    private ContentIntegrityErrorImpl(String path, String uuid, String primaryType, String mixins, String workspace,
                                      String locale, String constraintMessage, String integrityCheckName, String integrityCheckID) {
        this.path = path;
        this.uuid = uuid;
        this.primaryType = primaryType;
        this.mixins = mixins;
        this.locale = locale;
        this.workspace = workspace;
        this.constraintMessage = constraintMessage;
        this.integrityCheckName = integrityCheckName;
        this.integrityCheckID = integrityCheckID;
    }

    public static ContentIntegrityError createError(JCRNodeWrapper node, String locale, String message, ContentIntegrityCheck integrityCheck) {
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
                    mixins, node.getSession().getWorkspace().getName(), locale == null ? "-" : locale, message,
                    integrityCheck.getName(), integrityCheck.getId());
        } catch (RepositoryException e) {
            logger.error("", e);
        }

        return new ContentIntegrityErrorImpl(null, null, null, null, null, locale == null ? "-" : locale, message, integrityCheck.getName(), integrityCheck.getId());
    }

    @Override
    public JSONObject toJSON() {
        try {
            return (new JSONObject()).put("errorType", integrityCheckName).put("workspace", workspace).put("path", path)
                    .put("uuid", uuid).put("nt", getFullNodetype()).put("locale", locale)
                    .put("message", constraintMessage).put("fixed", fixed);
        } catch (JSONException e) {
            logger.error("", e);
        }
        return null;
    }

    @Override
    public String toCSV() {
        final StringBuilder sb = new StringBuilder();

        appendToCSVLine(sb, String.valueOf(integrityCheckID));
        appendToCSVLine(sb, String.valueOf(fixed));
        appendToCSVLine(sb, integrityCheckName);
        appendToCSVLine(sb, workspace);
        appendToCSVLine(sb, uuid);
        appendToCSVLine(sb, path);
        appendToCSVLine(sb, primaryType);
        appendToCSVLine(sb, mixins);
        appendToCSVLine(sb, locale);
        appendToCSVLine(sb, constraintMessage);
        appendToCSVLine(sb, Objects.toString(extraInfos, StringUtils.EMPTY));
        appendToCSVLine(sb, Objects.toString(specificExtraInfos, StringUtils.EMPTY));

        return sb.toString();
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
    public String getIntegrityCheckName() {
        return integrityCheckName;
    }

    @Override
    public String getIntegrityCheckID() {
        return integrityCheckID;
    }

    @Override
    public Map<String, Object> getAllExtraInfos() {
        if (CollectionUtils.isEmpty(extraInfosKeys)) return MapUtils.EMPTY_MAP;

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
        if (extraInfosKeys == null) extraInfosKeys = new ArrayList<>();
        else if (extraInfosKeys.contains(key))
            throw new IllegalArgumentException(String.format("Key already defined: %s", key));

        extraInfosKeys.add(key);
        if (isErrorSpecific) {
            if (specificExtraInfos == null) specificExtraInfos = new HashMap<>();
            specificExtraInfos.put(key, value);
        } else {
            if (extraInfos == null) extraInfos = new HashMap<>();
            extraInfos.put(key, value);
        }
        return this;
    }

    @Override
    public ContentIntegrityError setErrorType(Object type) {
        return addExtraInfo("error-type", type);
    }

    @Override
    public Object getErrorType() {
        return getExtraInfo("error-type");
    }

    @Override
    public ContentIntegrityError setExtraMsg(String msg) {
        return addExtraInfo("extra-message", msg);
    }

    private String getFullNodetype() {
        if (StringUtils.isBlank(mixins))
            return primaryType;
        return String.format("%s (%s)", primaryType, mixins);
    }
}
