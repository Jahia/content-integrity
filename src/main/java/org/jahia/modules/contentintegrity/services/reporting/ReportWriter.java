package org.jahia.modules.contentintegrity.services.reporting;

public interface ReportWriter {

    boolean saveFile(String name, String extension, String contentType, byte[] data);
}
