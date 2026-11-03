package org.jahia.modules.contentintegrity.services;

import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;

import java.util.Objects;

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

    /**
     * Two error types are equal when they have the same key: the type of an error read from the stored results is
     * rebuilt from its key, and is equal to the constant which its check declares.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ContentIntegrityErrorType)) return false;
        return Objects.equals(key, ((ContentIntegrityErrorType) o).getKey());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(key);
    }

    @Override
    public String toString() {
        return getKey();
    }
}
