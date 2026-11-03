package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLField;
import org.jahia.modules.contentintegrity.services.ContentIntegrityReport;

public class GqlScanReportFile {

    private final String name, location, uri, extension;

    public GqlScanReportFile(ContentIntegrityReport report) {
        name = report.getName();
        location = report.getLocation().toString();
        uri = report.getUri();
        extension = report.getExtension();
    }

    @GraphQLField
    public String getName() {
        return name;
    }

    @GraphQLField
    public String getLocation() {
        return location;
    }

    @GraphQLField
    public String getUri() {
        return uri;
    }

    @GraphQLField
    public String getExtension() {
        return extension;
    }
}
