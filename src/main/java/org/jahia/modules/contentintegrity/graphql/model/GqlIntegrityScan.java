package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLDefaultValue;
import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityService;
import org.jahia.modules.contentintegrity.api.ExternalLogger;
import org.jahia.modules.contentintegrity.graphql.util.GqlUtils;
import org.jahia.modules.contentintegrity.services.ContentIntegrityReport;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.modules.contentintegrity.services.Utils;
import org.jahia.modules.contentintegrity.services.exceptions.ConcurrentExecutionException;
import org.jahia.modules.contentintegrity.services.impl.Constants;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GqlIntegrityScan {

    private static final Logger logger = LoggerFactory.getLogger(GqlIntegrityScan.class);

    private static final Map<String, Status> executionStatus = new LinkedHashMap<>();
    private static final Map<String, List<String>> executionLog = new HashMap<>();
    private static final Map<String, List<ContentIntegrityReport>> executionReports = new HashMap<>();
    private static final Map<String, String> scanResults = new HashMap<>();
    private static final String PATH_DESC = "Path of the node from which to start the scan. If not defined, the root node is used";
    private static final int LOGS_LIMIT_CLIENT_SIDE_INTRO_SIZE = 100;
    private static final int LOGS_LIMIT_CLIENT_SIDE_END_SIZE = 500;
    private static final int LOGS_LIMIT_CLIENT_SIDE_TOTAL_SIZE = LOGS_LIMIT_CLIENT_SIDE_INTRO_SIZE + LOGS_LIMIT_CLIENT_SIDE_END_SIZE + 1;
    public static final String ABBREVIATED_LINE_SUFFIX = " [...]";
    public static final String NO_CHECK_SELECTED = "No check selected";
    public static final String NO_ERROR_FOUND = "No error found";

    private String id;

    /*
    TODO: replace with a local field, annotated with @Reference, and delete this method
    Requires to compile with Jahia 8.1.1.0+ , otherwise it doesn't compile because of a bug with the BND plugin version used along with previous versions
     */
    private ContentIntegrityService getService() {
        return Utils.getContentIntegrityService();
    }

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
        INTERRUPTED("interrupted"),
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
                          @GraphQLName("skipMountPoints") @GraphQLDefaultValue(GqlUtils.SupplierFalse.class) boolean skipMountPoints,
                          @GraphQLName("checksToRun") List<String> checksToRun,
                          @GraphQLName("uploadResults") boolean uploadResults) {
        id = generateExecutionID();
        executionStatus.put(id, Status.RUNNING);
        final List<String> output = new ArrayList<>();
        executionLog.put(id, output);
        final GqlExternalLogger console = new GqlExternalLogger() {
            @Override
            public void logLine(String e) {
                output.add(WordUtils.abbreviate(e, 200, 250, ABBREVIATED_LINE_SUFFIX));
            }
        };

        if (CollectionUtils.isEmpty(checksToRun)) {
            output.add(NO_CHECK_SELECTED);
            executionStatus.put(id, Status.FINISHED);
            return id;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

            final ContentIntegrityService service = getService();
            final List<String> checksToExecute = Utils.getChecksToExecute(service, checksToRun, null, console);
            final List<String> workspaces = workspace.getWorkspaces();
            try {
                final List<ContentIntegrityResults> results = new ArrayList<>(workspaces.size());
                for (String ws : workspaces) {
                    if (executionStatus.get(id) != Status.RUNNING) break;
                    final ContentIntegrityResults contentIntegrityResults = service.validateIntegrity(Optional.ofNullable(path).orElse(Constants.ROOT_NODE_PATH), excludedPaths, skipMountPoints, ws, checksToExecute, console);
                    if (contentIntegrityResults != null)
                        results.add(contentIntegrityResults.setExecutionID(id));
                }
                final ContentIntegrityResults mergedResults = Utils.mergeResults(results);
                if (mergedResults == null || CollectionUtils.isEmpty(mergedResults.getErrors())) {
                    console.logLine(NO_ERROR_FOUND);
                } else {
                    scanResults.put(id, mergedResults.getID());
                    final int nbErrors = mergedResults.getErrors().size();
                    final String details = workspaces.size() == 1 ?
                            StringUtils.EMPTY :
                            results.stream()
                                    .map(r -> r.getWorkspace() + " : " + r.getErrors().size())
                                    .collect(Collectors.joining(" , ", " [", "]"));

                    console.logLine(String.format("%d error%s found%s", nbErrors, nbErrors == 1 ? StringUtils.EMPTY : "s", details));

                    if (uploadResults && Utils.writeDumpInTheJCR(mergedResults, false, console)) {
                        executionReports.put(id, mergedResults.getReports());
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
        if (!executionLog.containsKey(id)) {
            return Collections.singletonList(Status.UNKNOWN.getDescription());
        }

        final List<String> logs = executionLog.get(id);
        final int size = logs.size();
        if (size < LOGS_LIMIT_CLIENT_SIDE_TOTAL_SIZE) return new ArrayList<>(logs);

        final Stream<String> limitMsg = Stream.of(StringUtils.EMPTY, String.format("Limit reached. Displaying the last %d lines", LOGS_LIMIT_CLIENT_SIDE_END_SIZE), StringUtils.EMPTY);
        final Stream<String> logsBeginning = Stream.concat(logs.stream().limit(LOGS_LIMIT_CLIENT_SIDE_INTRO_SIZE), limitMsg);
        return Stream.concat(logsBeginning, logs.stream().skip(size - LOGS_LIMIT_CLIENT_SIDE_END_SIZE)).collect(Collectors.toList());
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

    @GraphQLField
    @GraphQLName("reports")
    public List<GqlScanReportFile> getReports() {
        if (!executionReports.containsKey(id)) return null;
        return executionReports.get(id).stream()
                .map(GqlScanReportFile::new)
                .collect(Collectors.toList());
    }

    @GraphQLField
    @GraphQLName("resultsID")
    public String getResultsIdentifier() {
        return scanResults.get(id);
    }

    @GraphQLField
    public boolean stopRunningScan() {
        executionStatus.put(id, Status.INTERRUPTED);

        final ContentIntegrityService service = getService();

        if (!service.isScanRunning())
            return Boolean.FALSE;

        service.stopRunningScan();
        return Boolean.TRUE;
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
