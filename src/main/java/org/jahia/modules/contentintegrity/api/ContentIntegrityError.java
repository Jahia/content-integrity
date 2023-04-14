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

    Map<String, Object> getExtraInfos();

    Map<String, Object> getSpecificExtraInfos();

    Map<String, Object> getAllExtraInfos();

    boolean isFixed();

    void setFixed(boolean fixed);

    JSONObject toJSON();

    Object getExtraInfo(String key);

    /**
     * Specifies an extra information which is useful to analyze and understand the error.
     * <p>
     * Shortcut to {@link #addExtraInfo(String, Object, boolean) addExtraInfo(key, value, false)}
     *
     * @param key   the key
     * @param value the value
     * @return the current error
     */
    ContentIntegrityError addExtraInfo(String key, Object value);

    /**
     * Specifies an extra information which is useful to analyze and understand the error.
     * <p>
     * Errors flagged as specific or not will be separated in the generated reports, not specific infos being useful to aggregate similar errors where specific infos make each error unique.
     *
     * @param key             the key
     * @param value           the value
     * @param isErrorSpecific true if the info can't be used for aggregation, false otherwise
     * @return the current error
     */
    ContentIntegrityError addExtraInfo(String key, Object value, boolean isErrorSpecific);

    ContentIntegrityError setErrorType(Object type);

    Object getErrorType();

    /**
     * Defines a message to help understanding the error. This message is not embedded in the report.
     *
     * @param msg the text of the message
     * @return the current error
     */
    ContentIntegrityError setExtraMsg(String msg);
}
