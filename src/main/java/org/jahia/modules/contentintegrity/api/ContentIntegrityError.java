package org.jahia.modules.contentintegrity.api;

import org.json.JSONObject;

import java.util.Map;

public interface ContentIntegrityError {
    String getErrorID();

    String getPath();

    String getUuid();

    String getPrimaryType();

    String getMixins();

    String getLocale();

    String getWorkspace();

    String getSite();

    String getConstraintMessage();

    String getIntegrityCheckName();

    String getIntegrityCheckID();

    Map<String, Object> getAllExtraInfos();

    boolean isFixed();

    void setFixed(boolean fixed);

    JSONObject toJSON();

    String toCSV();

    Object getExtraInfo(String key);

    /**
     * Specifies an extra information which is useful to analyze and understand the error.
     *
     * Shortcut to addExtraInfo(key, value, false)
     *
     * @param key
     * @param value
     * @return
     */
    ContentIntegrityError addExtraInfo(String key, Object value);

    ContentIntegrityError addExtraInfo(String key, Object value, boolean isErrorSpecific);

    ContentIntegrityError setErrorType(Object type);

    Object getErrorType();

    ContentIntegrityError setExtraMsg(String msg);
}
