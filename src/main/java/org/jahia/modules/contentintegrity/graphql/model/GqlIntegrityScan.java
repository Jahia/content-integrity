package org.jahia.modules.contentintegrity.graphql.model;

import graphql.annotations.annotationTypes.GraphQLDefaultValue;
import graphql.annotations.annotationTypes.GraphQLDescription;
import graphql.annotations.annotationTypes.GraphQLField;
import graphql.annotations.annotationTypes.GraphQLName;
import graphql.annotations.annotationTypes.GraphQLNonNull;
import io.reactivex.Flowable;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.WordUtils;
import org.jahia.bin.filters.jcr.JcrSessionFilter;
import org.jahia.modules.contentintegrity.api.ContentIntegrityService;
import org.jahia.modules.contentintegrity.api.ExternalLogger;
import org.jahia.modules.contentintegrity.services.ContentIntegrityReport;
import org.jahia.modules.contentintegrity.services.ContentIntegrityResults;
import org.jahia.modules.contentintegrity.services.ScanReportLogger;
import org.jahia.modules.contentintegrity.services.Utils;
import org.jahia.modules.contentintegrity.services.exceptions.ConcurrentExecutionException;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.jahia.modules.graphql.provider.dxm.util.GqlUtils;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.usermanager.JahiaUser;
import org.slf4j.Logger;
import org.reactivestreams.Publisher;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GqlIntegrityScan {

    private static final Logger logger = LoggerFactory.getLogger(GqlIntegrityScan.class);

    // Written by the thread of each scan, read by the queries and the subscriptions
    private static final Map<String, Status> executionStatus = Collections.synchronizedMap(new LinkedHashMap<>());
    private static final Map<String, List<String>> executionLog = new ConcurrentHashMap<>();
    private static final Map<String, List<ContentIntegrityReport>> executionReports = new ConcurrentHashMap<>();
    private static final Map<String, String> scanResults = new ConcurrentHashMap<>();
    private static final Map<String, Instant> executionStart = new ConcurrentHashMap<>();
    // The executions asked to stop: they stay RUNNING until their scan has actually ended
    private static final Set<String> stopRequests = ConcurrentHashMap.newKeySet();
    private static final String PATH_DESC = "Path of the node from which to start the scan. If not defined, the root node is used";
    private static final int LOGS_LIMIT_CLIENT_SIDE_INTRO_SIZE = 100;
    private static final int LOGS_LIMIT_CLIENT_SIDE_END_SIZE = 500;
    private static final int LOGS_LIMIT_CLIENT_SIDE_TOTAL_SIZE = LOGS_LIMIT_CLIENT_SIDE_INTRO_SIZE + LOGS_LIMIT_CLIENT_SIDE_END_SIZE + 1;
    public static final String ABBREVIATED_LINE_SUFFIX = " [...]";
    public static final String NO_CHECK_SELECTED = "No check selected";
    public static final String NO_ERROR_FOUND = "No error found";
    private static final long PROGRESS_INTERVAL_MS = 1000L;

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
            synchronized (executionStatus) {
                id = executionStatus.entrySet().stream().filter(e -> e.getValue() == Status.RUNNING).map(Map.Entry::getKey).reduce((a, b) -> b).orElse(null);
                if (id == null)
                    id = executionStatus.keySet().stream().reduce((a, b) -> b).orElse(null);
            }
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
                          @GraphQLName("skipMountPoints") @GraphQLDefaultValue(GqlUtils.SupplierFalse.class) Boolean skipMountPoints,
                          @GraphQLName("checksToRun") List<String> checksToRun,
                          @GraphQLName("uploadResults") @GraphQLDefaultValue(GqlUtils.SupplierFalse.class) Boolean uploadResults) {
        // Boxed: graphql-java 13 (Jahia 8.1) passes null for a declared variable left unset, instead of the default value
        final boolean skipMountPointsValue = Boolean.TRUE.equals(skipMountPoints);
        final boolean uploadResultsValue = Boolean.TRUE.equals(uploadResults);
        id = generateExecutionID();
        executionStart.put(id, Instant.now());
        executionStatus.put(id, Status.RUNNING);
        final List<String> output = Collections.synchronizedList(new ArrayList<>());
        executionLog.put(id, output);
        final GqlExternalLogger console = e -> output.add(WordUtils.abbreviate(e, 200, 250, ABBREVIATED_LINE_SUFFIX));

        if (CollectionUtils.isEmpty(checksToRun)) {
            output.add(NO_CHECK_SELECTED);
            executionStatus.put(id, Status.FINISHED);
            return id;
        }

        final ContentIntegrityService service = getService();
        // A single scan runs at a time: a scan started while another one runs fails, and has no report
        if (service.isScanRunning()) {
            output.add(new ConcurrentExecutionException().getMessage());
            executionStatus.put(id, Status.FAILED);
            return id;
        }

        final List<String> workspaces = workspace.getWorkspaces();
        final String scannedWorkspace = workspaces.size() == 1 ? workspaces.get(0) : Utils.ALL_WORKSPACES;
        final long testDate = executionStart.get(id).toEpochMilli();
        // The report of the scan is stored from its start, then updated with its log until its end
        final ScanReportLogger reportLogger = new ScanReportLogger(console, new ContentIntegrityResults(testDate, 0L, scannedWorkspace, new ArrayList<>(), new ArrayList<>())
                .setStatus(ContentIntegrityResults.Status.RUNNING).setExecutionID(id));
        reportLogger.flush();
        scanResults.put(id, reportLogger.getReport().getID());

        final JahiaUser currentUser = JCRSessionFactory.getInstance().getCurrentUser();
        Executors.newSingleThreadExecutor().execute(() -> {
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

            JCRSessionFactory.getInstance().setCurrentUser(currentUser);
            boolean isOver = false;
            try {
                final List<String> checksToExecute = Utils.getChecksToExecute(service, checksToRun, null, reportLogger);
                final List<ContentIntegrityResults> results = new ArrayList<>(workspaces.size());
                for (String ws : workspaces) {
                    if (stopRequests.contains(id)) break;
                    // The results of the workspaces are stored once merged
                    final ContentIntegrityResults contentIntegrityResults = service.validateIntegrity(Optional.ofNullable(path).orElse(Constants.ROOT_NODE_PATH),
                            excludedPaths, skipMountPointsValue, ws, checksToExecute, reportLogger, false);
                    if (contentIntegrityResults != null)
                        results.add(contentIntegrityResults.setExecutionID(id));
                }
                final boolean isStopped = stopRequests.contains(id);
                if (results.isEmpty() && !isStopped) {
                    // No workspace could be scanned: its log tells why
                    return;
                }

                final ContentIntegrityResults mergedResults = Utils.mergeResults(results, testDate, scannedWorkspace).setExecutionID(id);
                // Stopped between two workspaces, or before the first one, the scan has not covered them all
                if (isStopped) mergedResults.setInterrupted(true);
                final boolean interrupted = mergedResults.isInterrupted();
                if (CollectionUtils.isEmpty(mergedResults.getErrors())) {
                    // An interrupted scan has not checked all the content: finding no error proves nothing
                    if (!interrupted) reportLogger.logLine(NO_ERROR_FOUND);
                } else {
                    final int nbErrors = mergedResults.getErrors().size();
                    final String details = workspaces.size() == 1 ?
                            StringUtils.EMPTY :
                            results.stream()
                                    .map(r -> r.getWorkspace() + " : " + r.getErrors().size())
                                    .collect(Collectors.joining(" , ", " [", "]"));

                    reportLogger.logLine(String.format("%d error%s found%s", nbErrors, nbErrors == 1 ? StringUtils.EMPTY : "s", details));

                    if (uploadResultsValue && Utils.writeDumpInTheJCR(mergedResults, false, reportLogger)) {
                        executionReports.put(id, mergedResults.getReports());
                    }
                }
                // The results are stored before the status changes, so that a client which reads it finds them
                service.saveResults(mergedResults, reportLogger.getLines(), true);
                isOver = true;
                executionStatus.put(id, interrupted ? Status.INTERRUPTED : Status.FINISHED);
            } catch (ConcurrentExecutionException cee) {
                logger.error("", cee);
                reportLogger.logLine(cee.getMessage());
            } catch (RuntimeException e) {
                logger.error("The scan {} failed", id, e);
                reportLogger.logLine(String.format("The scan failed: %s", e.getMessage()));
            } finally {
                if (!isOver) {
                    reportLogger.getReport().setStatus(ContentIntegrityResults.Status.FAILED);
                    service.saveResults(reportLogger.getReport(), reportLogger.getLines(), false);
                    executionStatus.put(id, Status.FAILED);
                }
                stopRequests.remove(id);
                JcrSessionFilter.endRequest();
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

        return limitLogs(new ArrayList<>(executionLog.get(id)));
    }

    private static List<String> limitLogs(List<String> logs) {
        final int size = logs.size();
        if (size < LOGS_LIMIT_CLIENT_SIDE_TOTAL_SIZE) return logs;

        final Stream<String> limitMsg = Stream.of(StringUtils.EMPTY, String.format("Limit reached. Displaying the last %d lines", LOGS_LIMIT_CLIENT_SIDE_END_SIZE), StringUtils.EMPTY);
        final Stream<String> logsBeginning = Stream.concat(logs.stream().limit(LOGS_LIMIT_CLIENT_SIDE_INTRO_SIZE), limitMsg);
        return Stream.concat(logsBeginning, logs.stream().skip(size - LOGS_LIMIT_CLIENT_SIDE_END_SIZE)).collect(Collectors.toList());
    }

    // The lines written since the previous event: beyond the limit, only the last ones are sent
    private static List<String> limitNewLogs(List<String> lines) {
        final int size = lines.size();
        if (size <= LOGS_LIMIT_CLIENT_SIDE_END_SIZE) return lines;

        final List<String> limited = new ArrayList<>(LOGS_LIMIT_CLIENT_SIDE_END_SIZE + 1);
        limited.add(String.format("[%d lines skipped]", size - LOGS_LIMIT_CLIENT_SIDE_END_SIZE));
        limited.addAll(lines.subList(size - LOGS_LIMIT_CLIENT_SIDE_END_SIZE, size));
        return limited;
    }

    /**
     * Follows an execution: an event when it has written new log lines, at most every second, then a last one once it
     * is over. The first event carries the lines written so far, so that a client which subscribes to a running scan
     * receives all its logs, once each.
     */
    public static Publisher<GqlIntegrityScanProgress> follow(String executionID) {
        return Flowable.defer(() -> {
            // The number of lines already sent to this subscriber, -1 before its first event
            final int[] sentLines = {-1};
            return Flowable.interval(0L, PROGRESS_INTERVAL_MS, TimeUnit.MILLISECONDS)
                    .concatMap(tick -> {
                        final GqlIntegrityScanProgress progress = readProgress(executionID, sentLines);
                        return progress == null ? Flowable.<GqlIntegrityScanProgress>empty() : Flowable.just(progress);
                    })
                    .takeUntil(progress -> !Status.RUNNING.getDescription().equals(progress.getStatus()));
        });
    }

    private static GqlIntegrityScanProgress readProgress(String executionID, int[] sentLines) {
        // The status first: the scan writes its last lines before its final status, so none can be missed
        final Status status = Optional.ofNullable(executionStatus.get(executionID)).orElse(Status.UNKNOWN);
        final List<String> output = executionLog.get(executionID);
        final List<String> logs = output == null ? Collections.emptyList() : new ArrayList<>(output);
        final boolean isFirst = sentLines[0] < 0;
        final List<String> newLines = isFirst ? limitLogs(logs) : limitNewLogs(logs.subList(Math.min(sentLines[0], logs.size()), logs.size()));
        sentLines[0] = logs.size();
        if (!isFirst && newLines.isEmpty() && status == Status.RUNNING) return null;

        final String startDate = Optional.ofNullable(executionStart.get(executionID)).map(Instant::toString).orElse(null);
        return new GqlIntegrityScanProgress(executionID, status.getDescription(), startDate, scanResults.get(executionID), new ArrayList<>(newLines));
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
    @GraphQLName("startDate")
    @GraphQLDescription("Date at which the scan was started, in the ISO-8601 format")
    public String getStartDate() {
        return Optional.ofNullable(executionStart.get(id)).map(Instant::toString).orElse(null);
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
    @GraphQLDescription("The identifier of the results of the scan, which are stored from its start")
    public String getResultsIdentifier() {
        return scanResults.get(id);
    }

    @GraphQLField
    public boolean stopRunningScan() {
        // Only this execution can be stopped: the scan which runs may have been started by another one.
        // Its status becomes INTERRUPTED once its scan has ended, so that no other scan is started in between.
        if (executionStatus.get(id) != Status.RUNNING || !stopRequests.add(id)) {
            return Boolean.FALSE;
        }

        // Between two workspaces, no tree is being scanned: the scan stops before the next workspace
        final ContentIntegrityService service = getService();
        if (service.isScanRunning()) service.stopRunningScan();
        return Boolean.TRUE;
    }

    private String generateExecutionID() {
        return UUID.randomUUID().toString();
    }

    private interface GqlExternalLogger extends ExternalLogger {
        @Override
        default boolean includeSummary() {
            return true;
        }
    }
}
