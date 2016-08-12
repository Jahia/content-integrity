package org.jahia.modules.verifyintegrity.exceptions;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;

import java.util.Locale;

public class IntegrityViolationException {

	private static Logger logger = org.slf4j.LoggerFactory.getLogger(IntegrityViolationException.class);

	private String path;

	private String nodeType;

	private String propertyName;

	private String locale;

	private String constraintMessage;



	public String getPath() {
		return path;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public String getNodeType() {
		return nodeType;
	}

	public void setNodeType(String nodeType) {
		this.nodeType = nodeType;
	}

	public String getPropertyName() {
		return propertyName;
	}

	public void setPropertyName(String propertyName) {
		this.propertyName = propertyName;
	}

	public String getLocale() {
		return locale;
	}

	public void setLocale(String locale) {
		this.locale = locale;
	}

	public String getConstraintMessage() {
		return constraintMessage;
	}

	public void setConstraintMessage(String constraintMessage) {
		this.constraintMessage = constraintMessage;
	}

	public IntegrityViolationException(String path, String nodeType, String propertyName, Locale locale, String
			constraintMessage) {
		this.path = path;
		this.nodeType = nodeType;
		this.propertyName = propertyName;

		if (locale != null) {
			this.locale = locale.getDisplayLanguage();
		}

		this.constraintMessage = constraintMessage;
	}

	@Override
	public String toString() {
		try {
			JSONObject resultAsJSON = new JSONObject();
			resultAsJSON.put("path", path);
			resultAsJSON.put("nodeType", nodeType);
			resultAsJSON.put("propertyName", propertyName);
			resultAsJSON.put("locale", locale);
			resultAsJSON.put("constraintMessage", constraintMessage.replace("'", "\'"));

			return resultAsJSON.toString();
		} catch (JSONException ex) {
			logger.error("Unexpected JSONException : " + ex.getMessage());
			return null;
		}
	}
}
