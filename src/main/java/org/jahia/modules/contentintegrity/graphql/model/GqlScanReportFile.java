package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLField;
import org.jahia.modules.contentintegrity.services.ContentIntegrityReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GqlScanReportFile {

    private static final Logger logger = LoggerFactory.getLogger(GqlScanReportFile.class);

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
