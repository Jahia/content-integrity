package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.jahia.modules.contentintegrity.api.ContentIntegrityService;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.modules.contentintegrity.services.Utils;
import org.jahia.modules.contentintegrity.services.exceptions.ConcurrentExecutionException;
import org.jahia.modules.contentintegrity.services.impl.ExternalLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

public class GqlIntegrityScan {

    private static final Logger logger = LoggerFactory.getLogger(GqlIntegrityScan.class);

    private static final Map<String, List<String>> executionLog = new HashMap<>();
    protected static final String PATH_DESC = "Path of the node from which to start the scan. If not defined, the root node is used";

    @GraphQLField
    public String getScan(@GraphQLName("workspace") @GraphQLNonNull GqlIntegrityService.Workspace workspace,
                          @GraphQLName("startNode") @GraphQLDescription(PATH_DESC) String path,
                          @GraphQLName("excludedPaths") List<String> excludedPaths,
                          @GraphQLName("checksToRun") List<String> checksWhiteList) {
        final String executionID = getExecutionID();
        final List<String> output = new ArrayList<>();
        executionLog.put(executionID, output);
        final ExternalLogger console = output::add;

        Executors.newSingleThreadExecutor().execute(() -> {
            final ContentIntegrityService service = Utils.getContentIntegrityService();
            final List<String> checksToExecute = Utils.getChecksToExecute(service, checksWhiteList, null, console);
            final List<String> workspaces = workspace.getWorkspaces();
            try {
                final List<ContentIntegrityResults> results = new ArrayList<>(workspaces.size());
                for (String ws : workspaces) {
                    results.add(service.validateIntegrity(Optional.ofNullable(path).orElse("/"), excludedPaths, ws, checksToExecute, console)
                            .setExecutionID(executionID));
                }
            } catch (ConcurrentExecutionException cee) {
                logger.error("", cee);
                output.add(cee.getMessage());
            }

        });
        return executionID;
    }

    @GraphQLField
    @GraphQLName("execution")
    public List<String> getExecutionStatus(@GraphQLName("id") @GraphQLNonNull String executionID) {
        return Optional.ofNullable(executionLog.get(executionID)).orElse(Collections.singletonList("Unknown execution ID"));
    }

    private String getExecutionID() {
        return UUID.randomUUID().toString();
    }
}
