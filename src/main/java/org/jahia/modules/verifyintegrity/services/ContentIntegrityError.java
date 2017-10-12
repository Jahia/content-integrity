package org.jahia.modules.verifyintegrity.services;

import org.apache.commons.lang.StringUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import javax.jcr.nodetype.NodeType;

public class ContentIntegrityError {

    private static final Logger logger = LoggerFactory.getLogger(ContentIntegrityError.class);

    private String path;
    private String uuid;
    private String primaryType;
    private String mixins;
    private String locale;
    private String workspace;
    private String constraintMessage;
    private String errorType;
    private boolean fixed = false;
    private long integrityCheckID = -1L;
    private Object extraInfos;

    private ContentIntegrityError(String path, String uuid, String primaryType, String mixins, String workspace,
                                  String locale, String constraintMessage, String errorType, long integrityCheckID) {
        this.path = path;
        this.uuid = uuid;
        this.primaryType = primaryType;
        this.mixins = mixins;
        this.locale = locale;
        this.workspace = workspace;
        this.constraintMessage = constraintMessage;
        this.errorType = errorType;
        this.integrityCheckID = integrityCheckID;
    }

    public static ContentIntegrityError createError(javax.jcr.Node node, String locale, String message, AbstractContentIntegrityCheck integrityCheck) {
        logger.error(message);
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
                    integrityCheck.getCheckName(), integrityCheck.getId());
        } catch (RepositoryException e) {
            logger.error("", e);  //TODO: review me, I'm generated
        }

        return new ContentIntegrityError(null, null, null, null, null, locale == null ? "-" : locale, message, integrityCheck.getCheckName(), integrityCheck.getId());
    }

    public JSONObject toJSON() {
        try {
            return (new JSONObject()).put("errorType", errorType).put("workspace", workspace).put("path", path)
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

        sb.append(StringUtils.replace(String.valueOf(integrityCheckID), separator, escapedSeparator));
        sb.append(separator).append(StringUtils.replace(String.valueOf(fixed), separator, escapedSeparator));
        sb.append(separator).append(StringUtils.replace(errorType, separator, escapedSeparator));
        sb.append(separator).append(StringUtils.replace(workspace, separator, escapedSeparator));
        sb.append(separator).append(StringUtils.replace(uuid, separator, escapedSeparator));
        sb.append(separator).append(StringUtils.replace(path, separator, escapedSeparator));
        sb.append(separator).append(StringUtils.replace(primaryType, separator, escapedSeparator));
        sb.append(separator).append(StringUtils.replace(mixins, separator, escapedSeparator));
        sb.append(separator).append(StringUtils.replace(locale, separator, escapedSeparator));
        sb.append(separator).append(StringUtils.replace(constraintMessage, separator, escapedSeparator));
        sb.append(separator).append(StringUtils.replace(String.valueOf(extraInfos), separator, escapedSeparator));

        return sb.toString();
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

    public String getWorkspace() {
        return workspace;
    }

    public String getErrorType() {
        return errorType;
    }

    public long getIntegrityCheckID() {
        return integrityCheckID;
    }

    public Object getExtraInfos() {
        return extraInfos;
    }

    public void setExtraInfos(Object extraInfos) {
        this.extraInfos = extraInfos;
    }

    private String getFullNodetype() {
        if (StringUtils.isBlank(mixins))
            return primaryType;
        return String.format("%s (%s)", primaryType, mixins);
    }
}
