package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityService;
import org.jahia.modules.contentintegrity.services.Utils;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.osgi.service.component.annotations.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.osgi.service.component.annotations.Reference;

@Component(service = GqlIntegrityService.class, immediate = true)
public class GqlIntegrityService {

    @Reference
    private ContentIntegrityService service;

    @GraphQLField
    @GraphQLName("integrityChecks")
    @GraphQLDescription("Returns the integrity checks")
    public Collection<GqlIntegrityCheck> getIntegrityChecks() {
        return service.getContentIntegrityChecksIdentifiers(false).stream()
                .map(service::getContentIntegrityCheck)
                .sorted((o1, o2) -> {
                    if (o1.getPriority() != o2.getPriority()) {
                        return Float.compare(o1.getPriority(), o2.getPriority());
                    }
                    return o1.getName().compareTo(o2.getName());
                })
                .map(check -> new GqlIntegrityCheck(service, check))
                .collect(Collectors.toList());
    }

    @GraphQLField
    @GraphQLName("canFixErrors")
    @GraphQLDescription("True if the current user may fix the errors, with the permission adminContentIntegrityFix")
    public boolean canFixErrors() {
        return Utils.canFixErrors();
    }

    @GraphQLField
    @GraphQLName("integrityCheckById")
    @GraphQLDescription("Returns the check specified by its ID")
    public GqlIntegrityCheck getIntegrityCheckById(@GraphQLName("id") @GraphQLDescription("ID of the check") String id) {
        final ContentIntegrityCheck integrityCheck = service.getContentIntegrityCheck(id);
        return Optional.ofNullable(integrityCheck)
                .map(check -> new GqlIntegrityCheck(service, check))
                .orElse(null);
    }

    @GraphQLName("WorkspaceToScan")
    public enum Workspace {
        EDIT(Constants.EDIT_WORKSPACE),

        LIVE(Constants.LIVE_WORKSPACE),

        BOTH(EDIT, LIVE);

        private final List<String> workspaces;

        Workspace(String workspace) {
            this.workspaces = Collections.singletonList(workspace);
        }

        Workspace(Workspace... wrappedWorkspaces) {
            workspaces = Arrays.stream(wrappedWorkspaces)
                    .flatMap(w -> w.getWorkspaces().stream())
                    .collect(Collectors.toList());
        }

        public List<String> getWorkspaces() {
            return workspaces;
        }
    }

    @GraphQLField
    @GraphQLName("integrityScan")
    public GqlIntegrityScan getIntegrityScan(@GraphQLName("id") String executionID) {
        return new GqlIntegrityScan(service, executionID);
    }

    @GraphQLField
    @GraphQLDescription("The identifiers of the stored results, from the oldest scan to the latest")
    public Collection<String> getScanResults() {
        return service.getTestIDs();
    }

    @GraphQLField
    @GraphQLDescription("The stored results, from the oldest scan to the latest, with their date, their status and their number of errors. " +
            "They are read from the JCR, without their errors")
    public Collection<GqlScanResultsSummary> getScanResultsSummaries() {
        return service.getResultsSummaries().stream()
                .map(GqlScanResultsSummary::new)
                .collect(Collectors.toList());
    }

    @GraphQLField
    @GraphQLDescription("The log of the scan of the stored results, whatever their status, or null if no results have this identifier. " +
            "The log of a running scan is the one of its last update, at most a few seconds old")
    public List<String> getScanResultsLogs(@GraphQLName("id") @GraphQLNonNull String id) {
        return service.getExecutionLog(id);
    }

    @GraphQLField
    public GqlScanResults getScanResultsDetails(@GraphQLName("id") String id, @GraphQLName("filters") Collection<String> filters) {
        final GqlScanResults results = new GqlScanResults(service, id, filters);
        return results.isValid() ? results : null;
    }
}
