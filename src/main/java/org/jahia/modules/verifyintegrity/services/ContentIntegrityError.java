package org.jahia.modules.verifyintegrity.services;

import org.apache.karaf.shell.support.table.Row;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.util.Arrays;
import java.util.List;

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

    public ContentIntegrityError(String path, String uuid, String primaryType, String mixins, String workspace, String locale, String constraintMessage, String errorType) {
        this.path = path;
        this.uuid = uuid;
        this.primaryType = primaryType;
        this.mixins = mixins;
        this.locale = locale;
        this.workspace = workspace;
        this.constraintMessage = constraintMessage;
        this.errorType = errorType;
    }

    public static ContentIntegrityError createError(javax.jcr.Node node, String locale, String message, String type) throws RepositoryException {
        logger.error(message);
        return new ContentIntegrityError(node.getPath(), node.getIdentifier(), node.getPrimaryNodeType().getName(),
                Arrays.toString(node.getMixinNodeTypes()), node.getSession().getWorkspace().getName(),
                locale == null ? "-" : locale, message, type);
    }

    public JSONObject toJSON() {
        try {
            return (new JSONObject()).put("errorType", errorType).put("workspace", workspace).put("path", path).put("uuid", uuid).put("nt", getFullNodetype()).put("locale", locale).put("message", constraintMessage);
        } catch (JSONException e) {
            logger.error("", e);  //TODO: review me, I'm generated
        }
        return null;
    }

    private String getFullNodetype() {
        return primaryType; // TODO add the mixins
    }
}
