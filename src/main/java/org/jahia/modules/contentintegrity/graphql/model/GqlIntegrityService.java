package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.jahia.api.Constants;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityService;
import org.jahia.modules.contentintegrity.services.Utils;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component(service = GqlIntegrityService.class, immediate = true)
public class GqlIntegrityService {

    private static final Logger logger = LoggerFactory.getLogger(GqlIntegrityService.class);

    /*
    TODO: replace with a local field, annotated with @Reference, and delete this method
    Requires to compile with Jahia 8.1.1.0+ , otherwise it doesn't compile because of a bug with the BND plugin version used along with previous versions
     */
    private ContentIntegrityService getService() {
        return Utils.getContentIntegrityService();
    }

    @GraphQLField
    @GraphQLName("integrityChecks")
    @GraphQLDescription("Returns the integrity checks")
    public Collection<GqlIntegrityCheck> getIntegrityChecks() {
        final ContentIntegrityService service = getService();
        return service.getContentIntegrityChecksIdentifiers(false).stream()
                .map(service::getContentIntegrityCheck)
                .sorted((o1, o2) -> {
                    if (o1.getPriority() != o2.getPriority()) {
                        return Float.compare(o1.getPriority(), o2.getPriority());
                    }
                    return o1.getName().compareTo(o2.getName());
                })
                .map(GqlIntegrityCheck::new)
                .collect(Collectors.toList());
    }

    @GraphQLField
    @GraphQLName("integrityCheckById")
    @GraphQLDescription("Returns the check specified by its ID")
    public GqlIntegrityCheck getIntegrityCheckById(@GraphQLName("id") @GraphQLDescription("ID of the check") String id) {
        final ContentIntegrityCheck integrityCheck = Utils.getContentIntegrityService().getContentIntegrityCheck(id);
        return Optional.ofNullable(integrityCheck)
                .map(GqlIntegrityCheck::new)
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
        return new GqlIntegrityScan(executionID);
    }

    @GraphQLField
    public Collection<String> getScanResults() {
        return Utils.getContentIntegrityService().getTestIDs();
    }
}
