package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;

import java.util.List;

// An event of the scan subscription: the state of the scan, and the log lines written since the previous event
public class GqlIntegrityScanProgress {

    private final String id, status, startDate, resultsID;
    private final List<String> logs;

    public GqlIntegrityScanProgress(String id, String status, String startDate, String resultsID, List<String> logs) {
        this.id = id;
        this.status = status;
        this.startDate = startDate;
        this.resultsID = resultsID;
        this.logs = logs;
    }

    @GraphQLField
    @GraphQLName("id")
    public String getID() {
        return id;
    }

    @GraphQLField
    @GraphQLDescription("running, finished, interrupted or failed")
    public String getStatus() {
        return status;
    }

    @GraphQLField
    @GraphQLDescription("Date at which the scan was started, in the ISO-8601 format")
    public String getStartDate() {
        return startDate;
    }

    @GraphQLField
    @GraphQLName("resultsID")
    @GraphQLDescription("The ID of the results of the scan, once it is over")
    public String getResultsIdentifier() {
        return resultsID;
    }

    @GraphQLField
    @GraphQLDescription("The log lines written since the previous event. The first event carries the lines written so far")
    public List<String> getLogs() {
        return logs;
    }
}
