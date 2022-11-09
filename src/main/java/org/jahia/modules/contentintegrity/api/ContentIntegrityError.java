package org.jahia.modules.contentintegrity.api;

import org.json.JSONObject;

import java.util.Map;

public interface ContentIntegrityError {
    String getPath();

    String getUuid();

    String getPrimaryType();

    String getMixins();

    String getLocale();

    String getWorkspace();

    String getConstraintMessage();

    String getIntegrityCheckName();

    String getIntegrityCheckID();

    Map<String, Object> getExtraInfos();

    boolean isFixed();

    void setFixed(boolean fixed);

    JSONObject toJSON();

    String toCSV();

    Object getExtraInfo(String key);

    ContentIntegrityError addExtraInfo(String key, Object value);

    ContentIntegrityError setErrorType(Object type);

    Object getErrorType();

    ContentIntegrityError setExtraMsg(String msg);
}
