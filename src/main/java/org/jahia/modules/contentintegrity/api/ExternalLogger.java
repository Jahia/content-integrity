package org.jahia.modules.contentintegrity.api;

public interface ExternalLogger {

    void logLine(String message);

    default boolean includeSummary() {
        return false;
    }
}
