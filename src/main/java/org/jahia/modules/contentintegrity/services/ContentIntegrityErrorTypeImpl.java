package org.jahia.modules.contentintegrity.services;

import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;

public class ContentIntegrityErrorTypeImpl implements ContentIntegrityErrorType {

    private final String key;
    private final boolean isBlockingImport;
    private String defaultMessage;

    public ContentIntegrityErrorTypeImpl(String key) {
        this(key, false);
    }

    public ContentIntegrityErrorTypeImpl(String key, boolean isBlockingImport) {
        this.key = key;
        this.isBlockingImport = isBlockingImport;
        defaultMessage = null;
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public boolean isBlockingImport() {
        return isBlockingImport;
    }

    @Override
    public String getDefaultMessage() {
        return defaultMessage;
    }

    @Override
    public ContentIntegrityErrorType setDefaultMessage(String defaultMessage) {
        if (this.defaultMessage != null)
            throw new UnsupportedOperationException("Changing the default message afterwards is not permitted");
        if (defaultMessage == null) throw new IllegalArgumentException("Setting the default message to null is not permitted");
        this.defaultMessage = defaultMessage;
        return this;
    }

    @Override
    public String toString() {
        return getKey();
    }
}
