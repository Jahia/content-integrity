package org.jahia.modules.verifyintegrity.services;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.Arrays;

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

    public static ContentIntegrityError createError(javax.jcr.Node node, String locale, String message, ContentIntegrityCheck integrityCheck) throws RepositoryException {
        logger.error(message);
        return new ContentIntegrityError(node.getPath(), node.getIdentifier(), node.getPrimaryNodeType().getName(),
                Arrays.toString(node.getMixinNodeTypes()), node.getSession().getWorkspace().getName(),
                locale == null ? "-" : locale, message, integrityCheck.getClass().getSimpleName(), integrityCheck.getId());
    }

    public JSONObject toJSON() {
        try {
            return (new JSONObject()).put("errorType", errorType).put("workspace", workspace).put("path", path)
                    .put("uuid", uuid).put("nt", getFullNodetype()).put("locale", locale)
                    .put("message", constraintMessage).put("fixed", fixed);
        } catch (JSONException e) {
            logger.error("", e);  //TODO: review me, I'm generated
        }
        return null;
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
        return primaryType; // TODO add the mixins
    }
}
