package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.apache.commons.lang.StringUtils;

import java.util.Collection;
import java.util.Optional;

@GraphQLName("ContentIntegrityMutation")
@GraphQLDescription("The operations of Content Integrity which change the server")
public class GqlIntegrityMutation {

    private final GqlIntegrityService service;

    public GqlIntegrityMutation(GqlIntegrityService service) {
        this.service = service;
    }

    @GraphQLField
    @GraphQLName("integrityScan")
    @GraphQLDescription("Starts a scan, or stops the one of an execution")
    public GqlIntegrityScanMutation getIntegrityScan(@GraphQLName("id") @GraphQLDescription("ID of the execution, to stop its scan") String executionID) {
        return new GqlIntegrityScanMutation(service.getIntegrityScan(executionID));
    }

    @GraphQLField
    @GraphQLName("integrityCheckById")
    @GraphQLDescription("Configures the check specified by its ID")
    public GqlIntegrityCheckMutation getIntegrityCheckById(@GraphQLName("id") @GraphQLDescription("ID of the check") String id) {
        return Optional.ofNullable(service.getIntegrityCheckById(id))
                .map(GqlIntegrityCheckMutation::new)
                .orElse(null);
    }

    @GraphQLField
    @GraphQLName("scanResultsDetails")
    @GraphQLDescription("Fixes the errors of the stored results of a scan. The results are given by their identifier: the latest ones are " +
            "never chosen by default, unlike the query of the same name")
    public GqlScanResultsMutation getScanResultsDetails(@GraphQLName("id") @GraphQLNonNull String id,
                                                        @GraphQLName("filters") Collection<String> filters) {
        // A blank identifier passes the non-null check: it designates no results, rather than the latest ones
        if (StringUtils.isBlank(id)) return null;
        return Optional.ofNullable(service.getScanResultsDetails(id, filters))
                .map(GqlScanResultsMutation::new)
                .orElse(null);
    }

    @GraphQLField
    @GraphQLDescription("Deletes the stored results of a scan: its errors, its log, and its CSV and XLSX reports. Returns false if no results " +
            "have this identifier. The results of a running scan are not deleted. Requires the permission adminContentIntegrityDelete, and " +
            "the access to the reports, which only the server administrators read")
    public boolean deleteScanResults(@GraphQLName("id") @GraphQLNonNull String id) {
        return service.deleteScanResults(id);
    }

    @GraphQLField
    @GraphQLDescription("Deletes the stored results of all the scans, with their errors, their logs and their CSV and XLSX reports, and returns " +
            "their number. The results of a running scan are not deleted. Requires the permission adminContentIntegrityDelete, and the access " +
            "to the reports, which only the server administrators read")
    public int deleteAllScanResults() {
        return service.deleteAllScanResults();
    }
}
