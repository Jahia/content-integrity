package org.jahia.modules.contentintegrity.services.impl;

public interface ExternalLogger {

    void logLine(String message);

    default boolean includeSummary() {
        return false;
    }
}
