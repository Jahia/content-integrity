package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import org.jahia.modules.contentintegrity.api.ContentIntegrityService;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.modules.contentintegrity.services.Utils;
import org.jahia.modules.contentintegrity.services.exceptions.ConcurrentExecutionException;
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

    @GraphQLField
    public String getScan(@GraphQLName("workspace") GqlIntegrityService.Workspace workspace,
                          @GraphQLName("startNode") String path) {
        final String executionID = getExecutionID();
        final List<String> output = new ArrayList<>();
        executionLog.put(executionID, output);

        Executors.newSingleThreadExecutor().execute(() -> {
            final List<String> workspaces = workspace.getWorkspaces();
            final ContentIntegrityService service = Utils.getContentIntegrityService();
            try {
                final List<ContentIntegrityResults> results = new ArrayList<>(workspaces.size());
                for (String ws : workspaces) {
                    results.add(service.validateIntegrity(path, null, ws, null, output::add).setExecutionID(executionID));
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
    public List<String> getExecutionStatus(@GraphQLName("id") String executionID) {
        return Optional.ofNullable(executionLog.get(executionID)).orElse(Collections.singletonList("Unknown execution ID"));
    }

    private String getExecutionID() {
        return UUID.randomUUID().toString();
    }
}
