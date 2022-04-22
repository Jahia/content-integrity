package org.jahia.modules.contentintegrity.services;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.services.content.JCRNodeWrapper;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;
import java.util.LinkedHashMap;
import java.util.Map;

public class ContentIntegrityError {

    private static final Logger logger = LoggerFactory.getLogger(ContentIntegrityError.class);

    private final String path;
    private final String uuid;
    private final String primaryType;
    private final String mixins;
    private final String locale;
    private final String workspace;
    private final String constraintMessage;
    private final String integrityCheckName;
    private final String integrityCheckID;
    private boolean fixed = false;
    private Map<String,Object> extraInfos;

    private ContentIntegrityError(String path, String uuid, String primaryType, String mixins, String workspace,
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
            return new ContentIntegrityError(node.getPath(), node.getIdentifier(), node.getPrimaryNodeType().getName(),
                    mixins, node.getSession().getWorkspace().getName(), locale == null ? "-" : locale, message,
                    integrityCheck.getName(), integrityCheck.getId());
        } catch (RepositoryException e) {
            logger.error("", e);
        }

        return new ContentIntegrityError(null, null, null, null, null, locale == null ? "-" : locale, message, integrityCheck.getName(), integrityCheck.getId());
    }

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

    public String toCSV() {
        return toCSV(",", ";");
    }

    public String toCSV(String separator, String escapedSeparator) {
        final StringBuilder sb = new StringBuilder();

        appendToCSVLine(sb, String.valueOf(integrityCheckID), separator, escapedSeparator);
        appendToCSVLine(sb, String.valueOf(fixed), separator, escapedSeparator);
        appendToCSVLine(sb, integrityCheckName, separator, escapedSeparator);
        appendToCSVLine(sb, workspace, separator, escapedSeparator);
        appendToCSVLine(sb, uuid, separator, escapedSeparator);
        appendToCSVLine(sb, path, separator, escapedSeparator);
        appendToCSVLine(sb, primaryType, separator, escapedSeparator);
        appendToCSVLine(sb, mixins, separator, escapedSeparator);
        appendToCSVLine(sb, locale, separator, escapedSeparator);
        appendToCSVLine(sb, constraintMessage, separator, escapedSeparator);
        appendToCSVLine(sb, String.valueOf(extraInfos), separator, escapedSeparator);

        return sb.toString();
    }

    private void appendToCSVLine(StringBuilder sb, Object value, String separator, String escapedSeparator) {
        if (sb.length() > 0) sb.append(separator);
        if (value == null) return;
        sb.append(StringUtils.replace(String.valueOf(value), separator, escapedSeparator));
    }

    public boolean isFixed() {
        return fixed;
    }

    public void setFixed(boolean fixed) {
        this.fixed = fixed;
    }

    public String getPath() {
        return path;
    }

    public String getUuid() {
        return uuid;
    }

    public String getLocale() {
        return locale;
    }

    public String getPrimaryType() {
        return primaryType;
    }

    public String getMixins() {
        return mixins;
    }

    public String getConstraintMessage() {
        return constraintMessage;
    }

    public String getWorkspace() {
        return workspace;
    }

    public String getIntegrityCheckName() {
        return integrityCheckName;
    }

    public String getIntegrityCheckID() {
        return integrityCheckID;
    }

    public Map<String, Object> getExtraInfos() {
        if (MapUtils.isEmpty(extraInfos)) return MapUtils.EMPTY_MAP;
        return new LinkedHashMap<>(extraInfos);
    }

    public Object getExtraInfo(String key) {
        return extraInfos.get(key);
    }

    public ContentIntegrityError addExtraInfo(String key, Object value) {
        if (extraInfos == null) extraInfos = new LinkedHashMap<>();
        extraInfos.put(key, value);
        return this;
    }

    public ContentIntegrityError setErrorType(Object type) {
        return addExtraInfo("error-type", type);
    }

    public Object getErrorType() {
        return getExtraInfo("error-type");
    }

    public ContentIntegrityError setExtraMsg(String msg) {
        return addExtraInfo("extra-message", msg);
    }

    private String getFullNodetype() {
        if (StringUtils.isBlank(mixins))
            return primaryType;
        return String.format("%s (%s)", primaryType, mixins);
    }
}
