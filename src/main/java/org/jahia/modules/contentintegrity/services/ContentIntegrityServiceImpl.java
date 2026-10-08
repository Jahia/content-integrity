package org.jahia.modules.contentintegrity.services;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.bin.Jahia;
import org.jahia.bin.filters.jcr.JcrSessionFilter;
import org.jahia.exceptions.JahiaException;
import org.jahia.exceptions.JahiaInitializationException;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityService;
import org.jahia.modules.contentintegrity.api.ExternalLogger;
import org.jahia.modules.contentintegrity.api.FixValuesDefinition;
import org.jahia.modules.contentintegrity.services.exceptions.ConcurrentExecutionException;
import org.jahia.modules.contentintegrity.services.exceptions.InterruptedScanException;
import org.jahia.modules.contentintegrity.services.impl.JCRUtils;
import org.jahia.modules.contentintegrity.services.util.ProgressMonitor;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.utils.DateUtils;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;

import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.jahia.modules.contentintegrity.services.impl.Constants.JCR_PATH_SEPARATOR;
import static org.jahia.modules.contentintegrity.services.impl.Constants.ROOT_NODE_PATH;
import static org.jahia.modules.contentintegrity.services.impl.Constants.TAB_LVL_1;
import static org.jahia.modules.contentintegrity.services.impl.Constants.TAB_LVL_2;

@Component(name = "org.jahia.modules.contentintegrity.service", service = ContentIntegrityService.class, property = {
        Constants.SERVICE_PID + "=org.jahia.modules.contentintegrity.service",
        Constants.SERVICE_DESCRIPTION + "=Content integrity service",
        Constants.SERVICE_VENDOR + "=" + Jahia.VENDOR_NAME}, immediate = true)
public class ContentIntegrityServiceImpl implements ContentIntegrityService {

    private static final Logger logger = org.slf4j.LoggerFactory.getLogger(ContentIntegrityServiceImpl.class);

    private static final long NODES_COUNT_LOG_INTERVAL = 10000L;
    private static final long SESSION_REFRESH_INTERVAL = 10000L;
    private static final String INTERRUPT_PROP_NAME = "modules.contentIntegrity.interrupt";

    private final List<ContentIntegrityCheck> integrityChecks = new ArrayList<>();
    // The results are stored in the JCR. The last ones read are kept in memory, as browsing them reads them page by page
    private static final int LOADED_RESULTS_MAX_SIZE = 3;
    private final Map<String, ContentIntegrityResults> loadedResults = Collections.synchronizedMap(new LinkedHashMap<String, ContentIntegrityResults>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ContentIntegrityResults> eldest) {
            return size() > LOADED_RESULTS_MAX_SIZE;
        }
    });
    private long nbNodesToScanCalculationDuration = 0L;
    private long ownTime = 0L;
    private long ownTimeIntervalStart = 0L;
    private long nbNodesToScan = 0;
    private final Semaphore semaphore = new Semaphore(1);

    @Activate
    public void start() throws JahiaInitializationException {
        Utils.restrictReportsFolderAccess();
        // A scan this server was running when it stopped will never end
        final int abandonedScans = ResultsStore.interruptAbandonedScans();
        if (abandonedScans > 0) logger.info("{} scan(s) running when the server stopped marked as interrupted", abandonedScans);
        logger.info("Content integrity service started ({})", Utils.getContentIntegrityVersion());
    }

    @Deactivate
    public void stop() throws JahiaException {
        loadedResults.clear();

        logger.info("Content integrity service stopped ({})", Utils.getContentIntegrityVersion());
    }

    @Reference(service = ContentIntegrityCheck.class, cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC, unbind = "unregisterIntegrityCheck")
    public void registerIntegrityCheck(ContentIntegrityCheck integrityCheck) {
        if (!integrityCheck.isValid()) {
            logger.info(String.format("Skipping registration on invalid integrity check %s", integrityCheck.toString()));
            return;
        }

        integrityCheck.setId(generateCheckID(integrityCheck));
        integrityChecks.add(integrityCheck);
        integrityChecks.sort((o1, o2) -> (int) (o1.getPriority() - o2.getPriority()));

        logger.info(String.format("Registered %s in the contentIntegrity service, number of checks: %s, service: %s, CL: %s", integrityCheck, integrityChecks.size(), this, this.getClass().getClassLoader()));
    }

    public void unregisterIntegrityCheck(ContentIntegrityCheck integrityCheck) {
        if (!integrityCheck.isValid()) {
            if (logger.isDebugEnabled()) {
                logger.debug(String.format("Skipping unregistration on invalid integrity check %s", integrityCheck.toString()));
            }
            return;
        }

        final boolean success = integrityChecks.remove(integrityCheck);// TODO: not sure that .equals() always work here, maybe we should itereate until we find one instance for which .getId().equals(integrityCheck.getId())
        if (success)
            logger.info(String.format("Unregistered %s in the contentIntegrity service, number of checks: %s", integrityCheck, integrityChecks.size()));
        else
            logger.error(String.format("Failed to unregister %s in the contentIntegrity service, number of checks: %s", integrityCheck, integrityChecks.size()));
    }

    private synchronized String generateCheckID(ContentIntegrityCheck integrityCheck) {
        return integrityCheck.getClass().getSimpleName();
    }

    @Override
    public ContentIntegrityResults validateIntegrity(String path, String workspace) throws ConcurrentExecutionException {
        return validateIntegrity(path, null, false, workspace, null, null);
    }

    @Override
    public ContentIntegrityResults validateIntegrity(String path, List<String> excludedPaths, boolean skipMountPoints, String workspace, List<String> checksToExecute, ExternalLogger externalLogger) throws ConcurrentExecutionException {
        return validateIntegrity(path, excludedPaths, skipMountPoints, workspace, checksToExecute, externalLogger, true);
    }

    @Override
    public ContentIntegrityResults validateIntegrity(String path, List<String> excludedPaths, boolean skipMountPoints, String workspace, List<String> checksToExecute, ExternalLogger externalLogger, boolean persistResults) throws ConcurrentExecutionException {
        return validateIntegrity(path, excludedPaths, skipMountPoints, workspace, checksToExecute, externalLogger, false, persistResults);
    }

    private ContentIntegrityResults validateIntegrity(String path, List<String> excludedPaths, boolean skipMountPoints, String workspace, List<String> checksToExecute, ExternalLogger callerLogger, boolean fixErrors, boolean persistResults) throws ConcurrentExecutionException {
        if (!semaphore.tryAcquire()) {
            throw new ConcurrentExecutionException();
        }

        ScanReportLogger reportLogger = null;
        ContentIntegrityResults finalResults = null;
        try {
            JcrSessionFilter.endRequest();
            final JCRSessionWrapper session = JCRUtils.getSystemSession(workspace);
            if (session == null) return null;

            final long start = System.currentTimeMillis();
            if (persistResults) {
                // The report of the scan is stored from its start, then updated with its log until its end
                reportLogger = new ScanReportLogger(callerLogger, new ContentIntegrityResults(start, 0L, workspace, new ArrayList<>(), new ArrayList<>())
                        .setStatus(ContentIntegrityResults.Status.RUNNING));
                reportLogger.flush();
            }
            final ExternalLogger externalLogger = reportLogger != null ? reportLogger : callerLogger;
            try {
                if (!session.nodeExists(path)) {
                    Utils.log(String.format("The node %s does not exist in the workspace %s", path, session.getWorkspace().getName()), logger, externalLogger);
                    return null;
                }
                final JCRNodeWrapper node = session.getNode(path);
                final String excludedPathsDesc = CollectionUtils.isNotEmpty(excludedPaths) ?
                        excludedPaths.stream().collect(Collectors.joining(" , ", " (excluded paths: ", " )")) : StringUtils.EMPTY;
                Utils.log(String.format("Starting to check the integrity under %s in the workspace %s%s with %s", path, workspace, excludedPathsDesc, Utils.getContentIntegrityVersion()), logger, externalLogger);
                final List<ContentIntegrityError> errors = new ArrayList<>();
                resetCounters();
                final Set<String> trimmedExcludedPaths = new HashSet<>();
                if (CollectionUtils.isNotEmpty(excludedPaths)) {
                    for (String excludedPath : excludedPaths) {
                        trimmedExcludedPaths.add((ROOT_NODE_PATH.equals(excludedPath) || !excludedPath.endsWith(JCR_PATH_SEPARATOR)) ? excludedPath : excludedPath.substring(0, excludedPath.length() - 1));
                    }
                }
                calculateNbNodesToScan(node, trimmedExcludedPaths, skipMountPoints, externalLogger);
                if (nbNodesToScan < 1) {
                    Utils.log("Interrupting the scan", Utils.LOG_LEVEL.WARN, logger, externalLogger);
                    return null;
                }
                ProgressMonitor.getInstance().init(nbNodesToScan, "Scan progress", logger, externalLogger);
                final List<ContentIntegrityCheck> activeChecks = new ArrayList<>();
                for (ContentIntegrityCheck integrityCheck : getActiveChecks(checksToExecute)) {
                    if (integrityCheck.areConditionsReachable(node, trimmedExcludedPaths)) {
                        integrityCheck.initializeIntegrityTest(node, trimmedExcludedPaths);
                        activeChecks.add(integrityCheck);
                    } else {
                        Utils.log(String.format("Skipping %s as its conditions can't be reached during this scan", integrityCheck.getName()), Utils.LOG_LEVEL.DEBUG, logger, externalLogger);
                    }
                }
                if (CollectionUtils.isEmpty(activeChecks)) {
                    Utils.log("No integrity check to run", Utils.LOG_LEVEL.WARN, logger, externalLogger);
                    return null;
                }
                validateIntegrity(node, trimmedExcludedPaths, skipMountPoints, activeChecks, errors, externalLogger, fixErrors);
                final boolean interrupted = System.getProperty(INTERRUPT_PROP_NAME) != null;
                if (interrupted) {
                    Utils.log("Scan interrupted before the end", Utils.LOG_LEVEL.WARN, logger, externalLogger);
                }
                for (ContentIntegrityCheck integrityCheck : activeChecks) {
                    final ContentIntegrityErrorList lastErrors = integrityCheck.finalizeIntegrityTest(node, trimmedExcludedPaths);
                    handleResult(lastErrors, null, false, integrityCheck, errors, externalLogger);
                }
                final long testDuration = System.currentTimeMillis() - start;
                final List<String> summary = new ArrayList<>();
                final ExternalLogger summaryLogger = summary::add;
                final String msg = String.format("Integrity checked under %s in the workspace %s in %s, %d nodes scanned, %d errors found", path, workspace, DateUtils.formatDurationWords(testDuration), ProgressMonitor.getInstance().getCounter(), errors.size());
                Utils.log(msg, logger, externalLogger, summaryLogger);
                final List<ExternalLogger> externalLoggers = new ArrayList<>();
                externalLoggers.add(summaryLogger);
                if (externalLogger != null && externalLogger.includeSummary()) externalLoggers.add(externalLogger);
                final ExternalLogger[] externalLoggersArray = externalLoggers.toArray(new ExternalLogger[0]);
                printChecksDuration(testDuration, activeChecks, externalLoggersArray);
                Utils.validateImportCompatibility(errors, logger, externalLoggersArray);
                Utils.detectLegacyErrorTypes(errors, logger, externalLoggersArray);
                finalResults = new ContentIntegrityResults(start, testDuration, workspace, errors, summary).setInterrupted(interrupted);
                if (reportLogger != null) saveResults(finalResults, reportLogger.getLines(), true);
                return finalResults;
            } catch (RepositoryException e) {
                Utils.log("", Utils.LOG_LEVEL.ERROR, logger, e, externalLogger);
            } catch (InterruptedScanException e) {
                Utils.log("Scan interrupted before the end", Utils.LOG_LEVEL.WARN, logger, externalLogger);
                // Stopped while the nodes to scan were counted, so before any node was checked: the results record the interrupted scan
                finalResults = new ContentIntegrityResults(start, System.currentTimeMillis() - start, workspace, new ArrayList<>(), new ArrayList<>()).setInterrupted(true);
                if (reportLogger != null) saveResults(finalResults, reportLogger.getLines(), true);
                return finalResults;
            }
        } finally {
            // A scan which ended without results failed: its report keeps the log which tells why
            if (reportLogger != null && finalResults == null) {
                reportLogger.getReport().setStatus(ContentIntegrityResults.Status.FAILED);
                reportLogger.flush();
            }
            JcrSessionFilter.endRequest();
            System.clearProperty(INTERRUPT_PROP_NAME);
            semaphore.release();
        }
        return null;
    }

    private void resetCounters() {
        for (ContentIntegrityCheck integrityCheck : integrityChecks) {
            integrityCheck.resetOwnTime();
        }
        nbNodesToScanCalculationDuration = 0L;
        ownTime = 0L;
        ownTimeIntervalStart = 0L;
        nbNodesToScan = 0L;
    }

    private void beginComputingOwnTime() {
        ownTimeIntervalStart = System.currentTimeMillis();
    }

    private void endComputingOwnTime() {
        if (ownTimeIntervalStart == 0L) {
            logger.error("Invalid call to endComputingOwnTime()");
            return;
        }
        ownTime += (System.currentTimeMillis() - ownTimeIntervalStart);
        ownTimeIntervalStart = 0L;
    }

    private void printChecksDuration(long totalDuration, List<ContentIntegrityCheck> activeChecks, ExternalLogger... externalLoggers) {
        final long totalChecksDuration = activeChecks.stream().map(ContentIntegrityCheck::getOwnTime).reduce(0L, Long::sum);
        Utils.log(String.format("%sCalculation of the size of the tree: %s", TAB_LVL_1, getDurationOutput(nbNodesToScanCalculationDuration, totalDuration)), logger, externalLoggers);
        Utils.log(String.format("%sScan of the tree: %s", TAB_LVL_1, getDurationOutput(ownTime, totalDuration)), logger, externalLoggers);
        final List<ContentIntegrityCheck> sortedChecks = activeChecks.stream().sorted((o1, o2) -> (int) (o2.getOwnTime() - o1.getOwnTime())).collect(Collectors.toList());
        final long durationRest = totalDuration - nbNodesToScanCalculationDuration - ownTime - totalChecksDuration;
        Utils.log(String.format("%sOther: %s", TAB_LVL_1, getDurationOutput(durationRest, totalDuration)), logger, externalLoggers);
        Utils.log(String.format("%sIntegrity checks: %s", TAB_LVL_1, getDurationOutput(totalChecksDuration, totalDuration)), logger, externalLoggers);
        for (ContentIntegrityCheck integrityCheck : sortedChecks) {
            Utils.log(String.format("%s%s: %s", TAB_LVL_2, integrityCheck.getName(), getDurationOutput(integrityCheck.getOwnTime(), totalChecksDuration)), logger, externalLoggers);
        }
    }

    private String getDurationOutput(long duration, long totalDuration) {
        return String.format("%s [%.0f%%]", DateUtils.formatDurationWords(duration), 100F * duration / totalDuration);
    }

    private void validateIntegrity(JCRNodeWrapper node, Set<String> excludedPaths, boolean skipMountPoints, List<ContentIntegrityCheck> activeChecks, List<ContentIntegrityError> errors, ExternalLogger externalLogger, boolean fixErrors) {
        if (System.getProperty(INTERRUPT_PROP_NAME) != null) {
            return;
        }
        try {
            beginComputingOwnTime();
            final String path = node.getPath();
            if (isExcluded(path, excludedPaths)) {
                Utils.log(String.format("Skipping node %s", path), logger, externalLogger);
                return;
            }
        } finally {
            endComputingOwnTime();
        }
        checkNode(node, activeChecks, errors, fixErrors, true, externalLogger);
        try {
            boolean hasNext;
            final Iterator<JCRNodeWrapper> children;
            int childIdx = 0;
            try {
                beginComputingOwnTime();
                children = node.getNodes().iterator();
                hasNext = children.hasNext();
            } finally {
                endComputingOwnTime();
            }
            JCRNodeWrapper child;
            while (hasNext) {
                childIdx++;
                try {
                    beginComputingOwnTime();
                    try {
                        child = children.next(); // Not using a for loop so that it.next() is part of the calculation of the duration of the scan, and internal errors can be catched
                    } catch (Throwable t) {
                        Utils.log(String.format("Impossible to load the child node %d of %s , skipping it and its subtree", childIdx, node.getPath()),
                                Utils.LOG_LEVEL.ERROR, logger, t, externalLogger);
                        continue;
                    }
                    hasNext = children.hasNext(); // Not calculating in the while loop so that it.hasNext() is part of the calculation of the duration of the scan
                    if (isNodeIgnored(child, node, skipMountPoints, externalLogger))
                        continue;
                } finally {
                    endComputingOwnTime();
                }
                validateIntegrity(child, excludedPaths, skipMountPoints, activeChecks, errors, externalLogger, fixErrors);
            }
        } catch (Throwable e) {
            String ws = "unknown";
            try {
                ws = node.getSession().getWorkspace().getName();
            } catch (RepositoryException e1) {
                logger.error("", e1);
            }
            logger.error(String.format("An error occurred while iterating over the children of the node %s in the workspace %s",
                    node, ws), e);
        }
        checkNode(node, activeChecks, errors, fixErrors, false, externalLogger);
        try {
            beginComputingOwnTime();
            ProgressMonitor.getInstance().progress();
            if (ProgressMonitor.getInstance().getCounter() % SESSION_REFRESH_INTERVAL == 0) {
                try {
                    node.getSession().refresh(false);
                } catch (RepositoryException e) {
                    logger.error("", e);
                }
            }
        } finally {
            endComputingOwnTime();
        }
    }

    private void checkNode(JCRNodeWrapper node, List<ContentIntegrityCheck> activeChecks, List<ContentIntegrityError> errors, boolean fixErrors, boolean beforeChildren, ExternalLogger externalLogger) {
        for (ContentIntegrityCheck integrityCheck : activeChecks) {
            final long start = System.currentTimeMillis();
            if (integrityCheck.canRun() && integrityCheck.areConditionsMatched(node)) {
                if (logger.isDebugEnabled())
                    logger.debug(String.format("Running %s on %s %s its children", integrityCheck.getClass().getName(), node, beforeChildren ? "before" : "after"));
                try {
                    final ContentIntegrityErrorList checkResult = beforeChildren ?
                            integrityCheck.checkIntegrityBeforeChildren(node) :
                            integrityCheck.checkIntegrityAfterChildren(node);
                    handleResult(checkResult, node, fixErrors, integrityCheck, errors, externalLogger);
                } catch (Throwable t) {
                    logFatalError(node, t, integrityCheck, externalLogger);
                    try {
                        errors.add(ContentIntegrityErrorImpl.createFrameworkError(node, null, null, t, integrityCheck));
                    } catch (Throwable t2) {
                        Utils.log("Failed to track a framework error", Utils.LOG_LEVEL.ERROR, logger, t2, externalLogger);
                    }
                }
            } else if (logger.isDebugEnabled())
                logger.debug(String.format("Skipping %s on %s (%s its children) as conditions are not matched", integrityCheck.getClass().getName(), node, beforeChildren ? "before" : "after"));
            integrityCheck.trackOwnTime(System.currentTimeMillis() - start);
        }
    }

    private void calculateNbNodesToScan(JCRNodeWrapper node, Set<String> excludedPaths, boolean skipMountPoints, ExternalLogger externalLogger) throws InterruptedScanException {
        final long start = System.currentTimeMillis();
        try {
            nbNodesToScan = calculateNbNodesToScan(node, excludedPaths, skipMountPoints, 0L, externalLogger);
            Utils.log(String.format("%s nodes to scan", nbNodesToScan), logger, externalLogger);
        } catch (InterruptedScanException e) {
            throw e;
        } catch (Throwable e) {
            logger.error("", e);
        }
        nbNodesToScanCalculationDuration = System.currentTimeMillis() - start;
    }

    private long calculateNbNodesToScan(JCRNodeWrapper node, Set<String> excludedPaths, boolean skipMountPoints, long currentCount, ExternalLogger externalLogger) throws RepositoryException, InterruptedScanException {
        if (System.getProperty(INTERRUPT_PROP_NAME) != null) {
            throw new InterruptedScanException();
        }
        if (isExcluded(node.getPath(), excludedPaths)) {
            return currentCount;
        }

        long count = currentCount + 1L;
        if (count % NODES_COUNT_LOG_INTERVAL == 0)
            Utils.log(String.format("Counted %d nodes to scan so far", count), logger, externalLogger);

        if (count % SESSION_REFRESH_INTERVAL == 0)
            node.getSession().refresh(false);

        final Iterator<JCRNodeWrapper> children;
        int childIdx = 0;
        try {
            children = node.getNodes().iterator();
        } catch (Throwable t) {
            Utils.log(String.format("Impossible to load the child nodes of %s , skipping them in the calculation of the number of nodes to scan", node.getPath()),
                    Utils.LOG_LEVEL.ERROR, logger, t, externalLogger);
            return count;
        }
        JCRNodeWrapper child;
        while (children.hasNext()) {
            childIdx++;
            try {
                child = children.next();
            } catch (Throwable t) {
                Utils.log(String.format("Impossible to load the child %d node of %s , skipping it in the calculation of the number of nodes to scan", childIdx, node.getPath()),
                        Utils.LOG_LEVEL.ERROR, logger, t, externalLogger);
                continue;
            }
            if (isNodeIgnored(child, node, skipMountPoints, externalLogger))
                continue;
            count = calculateNbNodesToScan(child, excludedPaths, skipMountPoints, count, externalLogger);
        }
        return count;
    }

    private boolean isNodeIgnored(JCRNodeWrapper node, JCRNodeWrapper parent, boolean skipMountPoints, ExternalLogger externalLogger) {
        final String path = node.getPath();
        if ("/jcr:system".equals(path)) {
            logger.debug("Skipping {}", path);
            return true; // If the test is started from /jcr:system or somewhere under, then it will not be skipped (this method is not executed on the root node of the scan, it is used to filter the children when traversing the subtree)
        }
        final String parentPath = parent.getPath();
        final String parentPathPlusSlash = StringUtils.equals(parentPath, ROOT_NODE_PATH) ? parentPath : parentPath.concat(JCR_PATH_SEPARATOR);
        if (!StringUtils.equals(path, parentPathPlusSlash + node.getName())) {
            Utils.log(String.format("Ignoring a child node as it is not the child of its parent node, according to their respective paths: %s", path), Utils.LOG_LEVEL.ERROR, logger, externalLogger);
            return true;
        }
        if (skipMountPoints && JCRUtils.isExternalNode(node)) {
            logger.info("Skipping {}", path);
            return true;
        } else return false;
    }

    private boolean isExcluded(String scanNodePath, Set<String> excludedPaths) {
        return excludedPaths.stream().anyMatch(excludedPath -> StringUtils.startsWith(scanNodePath, excludedPath));
    }

    private void logFatalError(JCRNodeWrapper node, Throwable t, ContentIntegrityCheck integrityCheck, ExternalLogger externalLogger) {
        String path = null;
        try {
            path = node.getPath();
        } finally {
            Utils.log("Impossible to check the integrity of " + path, Utils.LOG_LEVEL.ERROR, logger, t, externalLogger);
            integrityCheck.trackFatalError();
        }
    }

    private void handleResult(ContentIntegrityErrorList checkResult, JCRNodeWrapper node, boolean executeFix, ContentIntegrityCheck integrityCheck, List<ContentIntegrityError> errors, ExternalLogger externalLogger) {
        if (checkResult == null || !checkResult.hasErrors()) return;
        for (ContentIntegrityError integrityError : checkResult.getNestedErrors()) {
            if (executeFix && integrityCheck instanceof ContentIntegrityCheck.SupportsIntegrityErrorFix)
                try {
                    integrityError.setFixed(((ContentIntegrityCheck.SupportsIntegrityErrorFix) integrityCheck).fixError(node, integrityError));
                } catch (RepositoryException e) {
                    logger.error("An error occurred while fixing a content integrity error", e);
                }
            errors.add(integrityError);
            if (errors.size() % 1000 == 0) {
                Utils.log(String.format("%d errors tracked so far", errors.size()), logger, externalLogger);
            }
        }
    }

    @Override
    public void fixError(ContentIntegrityError error) {
        fixErrors(Collections.singletonList(error));
    }

    public void fixErrors(List<ContentIntegrityError> errors) {
        for (ContentIntegrityError error : errors) {
            final ContentIntegrityCheck integrityCheck = getContentIntegrityCheck(error.getIntegrityCheckID());
            if (integrityCheck == null) {
                logger.error("Impossible to load the integrity check which detected this error");
                continue;
            }
            if (!(integrityCheck instanceof ContentIntegrityCheck.SupportsIntegrityErrorFix)) continue;
            if (Utils.isOnVirtualNode(error)) {
                logger.warn("The error {} is not fixed: its node is virtual", error.getErrorID());
                continue;
            }

            final String workspace = error.getWorkspace();
            final JCRSessionWrapper session = JCRUtils.getSystemSession(workspace);
            if (session == null) continue;

            try {
                final String uuid = error.getUuid();
                final JCRNodeWrapper node = session.getNodeByUUID(uuid);
                final boolean fixed = ((ContentIntegrityCheck.SupportsIntegrityErrorFix) integrityCheck).fixError(node, error);
                if (fixed) error.setFixed(true);
                else logger.error(String.format("Failed to fix the error %s", error.toJSON()));
            } catch (RepositoryException e) {
                logger.error(String.format("Failed to fix the error %s", error.toJSON()), e);
            }
        }
    }

    @Override
    public void fixError(ContentIntegrityError error, List<String> values) throws RepositoryException {
        final ContentIntegrityCheck integrityCheck = getContentIntegrityCheck(error.getIntegrityCheckID());
        if (!(integrityCheck instanceof ContentIntegrityCheck.SupportsIntegrityErrorFixWithValues)) {
            throw new RepositoryException("The check which has detected the error doesn't fix it with values");
        }
        final ContentIntegrityCheck.SupportsIntegrityErrorFixWithValues check = (ContentIntegrityCheck.SupportsIntegrityErrorFixWithValues) integrityCheck;
        if (!check.isFixWithValues(error)) {
            throw new RepositoryException("This error is not fixed with values");
        }
        if (Utils.isOnVirtualNode(error)) {
            throw new RepositoryException("The errors of a virtual node, served by an external provider, are not fixed");
        }
        final JCRNodeWrapper node = getErrorNode(error);
        if (check.fixError(node, error, values)) {
            error.setFixed(true);
        } else {
            logger.error(String.format("Failed to fix the error %s", error.toJSON()));
        }
    }

    @Override
    public FixValuesDefinition getFixValuesDefinition(ContentIntegrityError error) {
        final ContentIntegrityCheck integrityCheck = getContentIntegrityCheck(error.getIntegrityCheckID());
        if (!(integrityCheck instanceof ContentIntegrityCheck.SupportsIntegrityErrorFixWithValues)) return null;
        final ContentIntegrityCheck.SupportsIntegrityErrorFixWithValues check = (ContentIntegrityCheck.SupportsIntegrityErrorFixWithValues) integrityCheck;
        if (!check.isFixWithValues(error)) return null;
        try {
            return check.getFixValuesDefinition(getErrorNode(error), error);
        } catch (RepositoryException e) {
            logger.error(String.format("Impossible to describe the values to fix the error %s", error.toJSON()), e);
            return null;
        }
    }

    private JCRNodeWrapper getErrorNode(ContentIntegrityError error) throws RepositoryException {
        final JCRSessionWrapper session = JCRUtils.getSystemSession(error.getWorkspace());
        if (session == null) throw new RepositoryException("Impossible to open a session on the workspace " + error.getWorkspace());
        return session.getNodeByUUID(error.getUuid());
    }

    @Override
    public ContentIntegrityCheck getContentIntegrityCheck(String id) {
        // TODO: a double storage of the integrity checks in a map where the keys are the IDs should fasten this method

        if (StringUtils.isBlank(id)) return null;

        for (ContentIntegrityCheck integrityCheck : integrityChecks) {
            if (StringUtils.equals(integrityCheck.getId(), id)) return integrityCheck;
        }

        return null;
    }

    @Override
    public List<String> getContentIntegrityChecksIdentifiers(boolean activeOnly) {
        final Predicate<ContentIntegrityCheck> predicate = activeOnly ?
                ContentIntegrityCheck::isEnabled :
                c -> true;
        return integrityChecks.stream().filter(predicate).map(ContentIntegrityCheck::getId).collect(Collectors.toList());
    }

    private List<ContentIntegrityCheck> getActiveChecks(List<String> checksToExecute) {
        final Predicate<ContentIntegrityCheck> predicate = CollectionUtils.isEmpty(checksToExecute) ?
                ContentIntegrityCheck::isEnabled :
                c -> checksToExecute.contains(c.getId());
        return integrityChecks.stream().filter(predicate).collect(Collectors.toList());
    }

    @Override
    public boolean saveResults(ContentIntegrityResults results, List<String> executionLog, boolean withErrors) {
        final boolean saved = ResultsStore.save(results, executionLog, withErrors);
        if (withErrors) loadedResults.put(results.getID(), results);
        return saved;
    }

    @Override
    public void saveFixedErrors(ContentIntegrityResults results) {
        ResultsStore.saveFixedErrors(results);
    }

    @Override
    @Deprecated
    public void storeErrorsInCache(ContentIntegrityResults results) {
        saveFixedErrors(results);
    }

    @Override
    @Deprecated
    public void removeErrorsFromCache(ContentIntegrityResults results) {
        loadedResults.remove(results.getID());
    }

    @Override
    public List<ContentIntegrityResultsSummary> getResultsSummaries() {
        return ResultsStore.list();
    }

    @Override
    public ContentIntegrityResults getLatestTestResults() {
        return getTestResults(null);
    }

    @Override
    public ContentIntegrityResults getTestResults(String testID) {
        final String id;
        if (StringUtils.isNotBlank(testID)) {
            id = testID;
        } else {
            // The latest results with errors to read: those of a scan which is over
            final List<ContentIntegrityResultsSummary> summaries = getResultsSummaries();
            id = summaries.stream()
                    .filter(s -> s.getStatus() == ContentIntegrityResults.Status.FINISHED || s.getStatus() == ContentIntegrityResults.Status.INTERRUPTED)
                    .reduce((a, b) -> b)
                    .map(ContentIntegrityResultsSummary::getID)
                    .orElse(null);
            if (id == null) return null;
        }

        final ContentIntegrityResults loaded = loadedResults.get(id);
        if (loaded != null) {
            // Another server may have fixed some errors since they were read
            final Set<String> fixedErrors = ResultsStore.loadFixedErrors(id);
            loaded.getErrors().stream().filter(e -> !e.isFixed() && fixedErrors.contains(e.getErrorID())).forEach(e -> e.setFixed(true));
            return loaded;
        }
        final ContentIntegrityResults results = ResultsStore.load(id, this::getContentIntegrityCheck);
        // The results of a scan which runs change until its end
        if (results != null && results.getStatus() != ContentIntegrityResults.Status.RUNNING) loadedResults.put(id, results);
        return results;
    }

    @Override
    public List<String> getExecutionLog(String testID) {
        return ResultsStore.loadExecutionLog(testID);
    }

    @Override
    public List<String> getTestIDs() {
        return getResultsSummaries().stream().map(ContentIntegrityResultsSummary::getID).collect(Collectors.toList());
    }

    @Override
    public List<String> printIntegrityChecksList(boolean simpleOutput) {
        final int nbChecks = integrityChecks.size();
        final List<String> lines = new ArrayList<>(nbChecks + 1);
        logAndAppend(String.format("Integrity checks (%d):", nbChecks), lines);
        for (ContentIntegrityCheck integrityCheck : integrityChecks)
            logAndAppend(String.format("%s%s", TAB_LVL_1, simpleOutput ? integrityCheck : integrityCheck.toFullString()), lines);
        return lines;
    }

    private void logAndAppend(String line, List<String> lines) {
        logger.info(line);
        lines.add(line);
    }

    @Override
    public boolean isScanRunning() {
        return semaphore.availablePermits() == 0;
    }

    @Override
    public void stopRunningScan() {
        System.setProperty(INTERRUPT_PROP_NAME, "true");
    }

    /**
     * What's the best strategy?
     * - iterating over the checks and for each, iterating over the tree
     * - iterating over the tree and for each node, iterating over the checks
     */
}
