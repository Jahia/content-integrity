package org.jahia.modules.contentintegrity.api;

public interface ContentIntegrityErrorType {

    String getKey();

    boolean isBlockingImport();

    String getDefaultMessage();

    ContentIntegrityErrorType setDefaultMessage(String defaultMessage);
}
