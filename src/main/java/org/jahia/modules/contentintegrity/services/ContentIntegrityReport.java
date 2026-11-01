package org.jahia.modules.contentintegrity.services;

public class ContentIntegrityReport {

    public enum LOCATION {JCR, FILESYSTEM}

    private final LOCATION location;
    private final String name, uri, extension;

    public ContentIntegrityReport(String name, LOCATION location, String uri, String extension) {
        this.name = name;
        this.location = location;
        this.uri = uri;
        this.extension = extension;
    }

    public LOCATION getLocation() {
        return location;
    }

    public String getName() {
        return name;
    }

    public String getUri() {
        return uri;
    }

    public String getExtension() {
        return extension;
    }
}
