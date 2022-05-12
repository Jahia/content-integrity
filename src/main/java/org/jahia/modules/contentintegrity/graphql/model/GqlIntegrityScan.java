package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;

public class GqlIntegrityScan {

    private static final Logger logger = LoggerFactory.getLogger(GqlIntegrityScan.class);

    private static final Map<String, Status> executionStatus = new LinkedHashMap<>();
    private static final Map<String, List<String>> executionLog = new HashMap<>();
    private static final String PATH_DESC = "Path of the node from which to start the scan. If not defined, the root node is used";

    private String id;

    public GqlIntegrityScan(String executionID) {
        if (StringUtils.isNotBlank(executionID)) {
            id = executionID;
        } else if (MapUtils.isEmpty(executionStatus)) {
            id = null;
        } else {
            id = executionStatus.entrySet().stream().filter(e -> e.getValue() == Status.RUNNING).map(Map.Entry::getKey).reduce((a, b) -> b).orElse(null);
            if (id == null)
                id = executionStatus.keySet().stream().reduce((a, b) -> b).orElse(null);
        }
    }

    private enum Status {
        RUNNING("running"),
        FINISHED("finished"),
        FAILED("failed"),
        UNKNOWN("Unknown execution ID"),
        NONE("No scan currently running");

        private final String description;

        Status(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @GraphQLField
    public String getScan(@GraphQLName("workspace") @GraphQLNonNull GqlIntegrityService.Workspace workspace,
                          @GraphQLName("startNode") @GraphQLDescription(PATH_DESC) String path,
                          @GraphQLName("excludedPaths") List<String> excludedPaths,
                          @GraphQLName("checksToRun") List<String> checksWhiteList,
                          @GraphQLName("uploadResults") boolean uploadResults) {
        id = generateExecutionID();
        executionStatus.put(id, Status.RUNNING);
        final List<String> output = new ArrayList<>();
        executionLog.put(id, output);
        final GqlExternalLogger console = output::add;

        Executors.newSingleThreadExecutor().execute(() -> {
            final ContentIntegrityService service = Utils.getContentIntegrityService();
            final List<String> checksToExecute = Utils.getChecksToExecute(service, checksWhiteList, null, console);
            final List<String> workspaces = workspace.getWorkspaces();
            try {
                final List<ContentIntegrityResults> results = new ArrayList<>(workspaces.size());
                for (String ws : workspaces) {
                    final ContentIntegrityResults contentIntegrityResults = service.validateIntegrity(Optional.ofNullable(path).orElse("/"), excludedPaths, ws, checksToExecute, console);
                    if (contentIntegrityResults != null)
                        results.add(contentIntegrityResults.setExecutionID(id));
                }
                if (uploadResults) {
                    final ContentIntegrityResults mergedResults = Utils.mergeResults(results);
                    if (mergedResults == null || CollectionUtils.isEmpty(mergedResults.getErrors())) {
                        console.logLine("No error found");
                    } else {
                        final int nbErrors = mergedResults.getErrors().size();
                        console.logLine(String.format("%d error%s found", nbErrors, nbErrors == 1 ? StringUtils.EMPTY : "s"));
                        Utils.writeDumpInTheJCR(mergedResults, false, false, console);
                    }
                }
                executionStatus.put(id, Status.FINISHED);
            } catch (ConcurrentExecutionException cee) {
                logger.error("", cee);
                output.add(cee.getMessage());
                executionStatus.put(id, Status.FAILED);
            }

        });
        return id;
    }

    @GraphQLField
    @GraphQLName("logs")
    public List<String> getExecutionLogs() {
        return Optional.ofNullable(executionLog.get(id)).orElse(Collections.singletonList(Status.UNKNOWN.getDescription()));
    }

    @GraphQLField
    @GraphQLName("status")
    public String getExecutionStatus() {
        return Optional.ofNullable(executionStatus.get(id)).orElse(Status.UNKNOWN).getDescription();
    }

    @GraphQLField
    @GraphQLName("id")
    public String getID() {
        return id;
    }

    private String generateExecutionID() {
        return UUID.randomUUID().toString();
    }

    private abstract interface GqlExternalLogger extends ExternalLogger {
        @Override
        default boolean includeSummary() {
            return true;
        }
    }
}
