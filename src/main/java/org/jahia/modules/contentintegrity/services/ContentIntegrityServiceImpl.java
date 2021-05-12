package org.jahia.modules.contentintegrity.services;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.bin.Jahia;
import org.jahia.exceptions.JahiaException;
import org.jahia.exceptions.JahiaInitializationException;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityService;
import org.jahia.modules.contentintegrity.services.util.ProgressMonitor;
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.cache.ehcache.EhCacheProvider;
import org.jahia.services.content.JCRNodeIteratorWrapper;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

@Component(name = "org.jahia.modules.contentintegrity.service", service = ContentIntegrityService.class, property = {
        Constants.SERVICE_PID + "=org.jahia.modules.contentintegrity.service",
        Constants.SERVICE_DESCRIPTION + "=Content integrity service",
        Constants.SERVICE_VENDOR + "=" + Jahia.VENDOR_NAME}, immediate = true)
public class ContentIntegrityServiceImpl implements ContentIntegrityService {

    private static Logger logger = org.slf4j.LoggerFactory.getLogger(ContentIntegrityServiceImpl.class);

    private List<ContentIntegrityCheck> integrityChecks = new ArrayList<>();
    private Cache errorsCache;
    private EhCacheProvider ehCacheProvider;
    private String errorsCacheName = "ContentIntegrityService-errors";
    private long errorsCacheTti = 24L * 3600L; // 1 day;
    private long nbNodestoScanCalculationDuration = 0L;
    private long ownTime = 0L;
    private long ownTimeIntervalStart = 0L;
    private long integrityChecksIdGenerator = 0;
    private long nbNodesToScan = 0;

    @Activate
    public void start() throws JahiaInitializationException {
        if (ehCacheProvider == null)
            ehCacheProvider = (EhCacheProvider) SpringContextSingleton.getBean("bigEhCacheProvider");
        if (errorsCache == null) {
            errorsCache = ehCacheProvider.getCacheManager().getCache(errorsCacheName);
            if (errorsCache == null) {
                ehCacheProvider.getCacheManager().addCache(errorsCacheName);
                errorsCache = ehCacheProvider.getCacheManager().getCache(errorsCacheName);
                errorsCache.getCacheConfiguration().setTimeToIdleSeconds(errorsCacheTti);
            }
        }

        logger.info("Content integrity service started");
    }

    @Deactivate
    public void stop() throws JahiaException {
        if (errorsCache != null) errorsCache.flush();

        logger.info("Content integrity service stopped");
    }

    @Reference(service = ContentIntegrityCheck.class, cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC, unbind = "unregisterIntegrityCheck")
    public void registerIntegrityCheck(ContentIntegrityCheck integrityCheck) {
        if (!integrityCheck.isValid()) {
            logger.info(String.format("Skipping registration on invalid integrity check %s", integrityCheck.toString()));
            return;
        }

        integrityCheck.setId(getNextIntegrityCheckID());
        integrityChecks.add(integrityCheck);
        Collections.sort(integrityChecks, new Comparator<ContentIntegrityCheck>() {
            @Override
            public int compare(ContentIntegrityCheck o1, ContentIntegrityCheck o2) {
                return (int) (o1.getPriority() - o2.getPriority());
            }
        });

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

    private synchronized long getNextIntegrityCheckID() {
        return ++integrityChecksIdGenerator;
    }

    @Override
    public ContentIntegrityResults validateIntegrity(String path, String workspace) {
        return validateIntegrity(path, null, workspace);
    }

    @Override
    public ContentIntegrityResults validateIntegrity(String path, List<String> excludedPaths, String workspace) {
        return validateIntegrity(path, excludedPaths, workspace, false);
    }

    private ContentIntegrityResults validateIntegrity(String path, List<String> excludedPaths, String workspace, boolean fixErrors) {   // TODO maybe need to prevent concurrent executions
        try {
            JCRSessionFactory.getInstance().closeAllSessions();
            final JCRSessionWrapper session;
            try {
                session = JCRSessionFactory.getInstance().getCurrentSystemSession(workspace, null, null);
            } catch (RepositoryException e) {
                logger.error(String.format("Impossible to get the session for workspace %s", workspace), e);
                return null;
            }
            try {
                final JCRNodeWrapper node = session.getNode(path);
                logger.info(String.format("Starting to check the integrity under %s in the workspace %s", path, workspace));
                final List<ContentIntegrityError> errors = new ArrayList<>();
                final long start = System.currentTimeMillis();
                resetCounters();
                final Set<String> trimmedExcludedPaths = new HashSet<>();
                if (CollectionUtils.isNotEmpty(excludedPaths)) {
                    for (String excludedPath : excludedPaths) {
                        trimmedExcludedPaths.add(("/".equals(excludedPath) || !excludedPath.endsWith("/")) ? excludedPath : excludedPath.substring(0, excludedPath.length() - 1));
                    }
                }
                calculateNbNodestoScan(node, trimmedExcludedPaths);
                ProgressMonitor.getInstance().init(nbNodesToScan, "Scan progress", logger);
                for (ContentIntegrityCheck integrityCheck : integrityChecks) {
                    integrityCheck.initializeIntegrityTest();
                }
                validateIntegrity(node, trimmedExcludedPaths, errors, fixErrors);
                for (ContentIntegrityCheck integrityCheck : integrityChecks) {
                    integrityCheck.finalizeIntegrityTest();
                }
                final long testDuration = System.currentTimeMillis() - start;
                logger.info(String.format("Integrity checked under %s in the workspace %s in %s", path, workspace, DateUtils.formatDurationWords(testDuration)));
                printChecksDuration();
                final ContentIntegrityResults results = new ContentIntegrityResults(start, testDuration, errors);
                storeErrorsInCache(results);
                return results;
            } catch (RepositoryException e) {
                logger.error("", e);
            }
        } finally {
            JCRSessionFactory.getInstance().closeAllSessions();
        }
        return null;
    }

    private void resetCounters() {
        for (ContentIntegrityCheck integrityCheck : integrityChecks) {
            integrityCheck.resetOwnTime();
        }
        nbNodestoScanCalculationDuration = 0L;
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

    private void printChecksDuration() {
        if (logger.isDebugEnabled())
            logger.debug(String.format("   Calculation of the size of the tree: %s", DateUtils.formatDurationWords(nbNodestoScanCalculationDuration)));
        logger.info(String.format("   Scan of the tree: %s", DateUtils.formatDurationWords(ownTime)));
        for (ContentIntegrityCheck integrityCheck : integrityChecks) {
            logger.info(String.format("   %s: %s", integrityCheck.getName(), DateUtils.formatDurationWords(integrityCheck.getOwnTime())));
        }
    }

    private void validateIntegrity(JCRNodeWrapper node, Set<String> excludedPaths, List<ContentIntegrityError> errors, boolean fixErrors) {
        // TODO add a mechanism to stop , prevent concurrent run
        try {
            beginComputingOwnTime();
            final String path = node.getPath();
            if (CollectionUtils.isNotEmpty(excludedPaths) && excludedPaths.contains(path)) {
                logger.info(String.format("Skipping node %s", path));
                return;
            }
        } finally {
            endComputingOwnTime();
        }
        checkNode(node, errors, fixErrors, true);
        try {
            final JCRNodeIteratorWrapper it = node.getNodes();
            boolean hasNext = it.hasNext();
            while (hasNext) {
                JCRNodeWrapper child;
                try {
                    beginComputingOwnTime();
                    child = (JCRNodeWrapper) it.next();
                    hasNext = it.hasNext(); // Not using a for loop so that it.hasNext() is part of the calculation of the duration of the scan
                    if ("/jcr:system".equals(child.getPath()))
                        continue; // If the test is started from /jcr:system or somewhere under, then it will not be skipped
                } finally {
                    endComputingOwnTime();
                }
                validateIntegrity(child, excludedPaths, errors, fixErrors);
            }
        } catch (RepositoryException e) {
            String ws = "unknown";
            try {
                ws = node.getSession().getWorkspace().getName();
            } catch (RepositoryException e1) {
                logger.error("", e1);
            }
            logger.error(String.format("An error occured while iterating over the children of the node %s in the workspace %s",
                    node, ws), e);
        }
        checkNode(node, errors, fixErrors, false);
        ProgressMonitor.getInstance().progress();
    }

    private void checkNode(JCRNodeWrapper node, List<ContentIntegrityError> errors, boolean fixErrors, boolean beforeChildren) {
        for (ContentIntegrityCheck integrityCheck : integrityChecks) {
            final long start = System.currentTimeMillis();
            if (integrityCheck.areConditionsMatched(node)) {
                if (logger.isDebugEnabled())
                    logger.debug(String.format("Running %s on %s %s its children", integrityCheck.getClass().getName(), node, beforeChildren ? "before" : "after"));
                try {
                    final ContentIntegrityErrorList checkResult = beforeChildren ?
                            integrityCheck.checkIntegrityBeforeChildren(node) :
                            integrityCheck.checkIntegrityAfterChildren(node);
                    handleResult(checkResult, node, fixErrors, integrityCheck, errors);
                } catch (Throwable t) {
                    logFatalError(node, t, integrityCheck);
                }
            } else if (logger.isDebugEnabled())
                logger.debug(String.format("Skipping %s on %s (%s its children) as conditions are not matched", integrityCheck.getClass().getName(), node, beforeChildren ? "before" : "after"));
            integrityCheck.trackOwnTime(System.currentTimeMillis() - start);
        }
    }

    private void calculateNbNodestoScan(JCRNodeWrapper node, Set<String> excludedPaths) {
        final long start = System.currentTimeMillis();
        try {
            nbNodesToScan = calculateNbNodestoScan(node, excludedPaths, 0);
            logger.info(String.format("%s nodes to scan", nbNodesToScan));
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        nbNodestoScanCalculationDuration = System.currentTimeMillis() - start;
    }

    private int calculateNbNodestoScan(JCRNodeWrapper node, Set<String> excludedPaths, int currentCount) throws RepositoryException {
        if (CollectionUtils.isNotEmpty(excludedPaths) && excludedPaths.contains(node.getPath())) {
            return currentCount;
        }
        int count = currentCount + 1;
        for (JCRNodeWrapper child : node.getNodes()) {
            if ("/jcr:system".equals(child.getPath()))
                continue; // If the test is started from /jcr:system or somewhere under, then it will not be skipped
            count = calculateNbNodestoScan(child, excludedPaths, count);
        }
        return count;
    }

    private void logFatalError(JCRNodeWrapper node, Throwable t, ContentIntegrityCheck integrityCheck) {
        String path = null;
        try {
            path = node.getPath();
        } finally {
            logger.error("Impossible to check the integrity of " + path, t);
            integrityCheck.trackFatalError();
        }
    }

    private void handleResult(ContentIntegrityErrorList checkResult, JCRNodeWrapper node, boolean executeFix, ContentIntegrityCheck integrityCheck, List<ContentIntegrityError> errors) {
        if (checkResult == null || !checkResult.hasErrors()) return;
        for (ContentIntegrityError integrityError : checkResult.getNestedErrors()) {
            if (executeFix && integrityCheck instanceof ContentIntegrityCheck.SupportsIntegrityErrorFix)
                try {
                    integrityError.setFixed(((ContentIntegrityCheck.SupportsIntegrityErrorFix) integrityCheck).fixError(node, integrityError));
                } catch (RepositoryException e) {
                    logger.error("An error occurred while fixing a content integrity error", e);
                }
            errors.add(integrityError);
        }
    }

    @Override
    public void fixError(ContentIntegrityError error) {
        fixErrors(Collections.singletonList(error));
    }

    public void fixErrors(List<ContentIntegrityError> errors) {
        final Map<String, JCRSessionWrapper> sessions = new HashMap<>();
        for (ContentIntegrityError error : errors) {
            final ContentIntegrityCheck integrityCheck = getContentIntegrityCheck(error.getIntegrityCheckID());
            if (integrityCheck == null) {
                logger.error("Impossible to load the integrity check which detected this error");
                continue;
            }
            if (!(integrityCheck instanceof ContentIntegrityCheck.SupportsIntegrityErrorFix)) continue;

            final String workspace = error.getWorkspace();
            JCRSessionWrapper session = sessions.get(workspace);
            if (session == null) {
                session = getSystemSession(workspace);
                if (session == null) continue;
                sessions.put(workspace, session);
            }
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

    private JCRSessionWrapper getSystemSession(String workspace) {
        try {
            return JCRSessionFactory.getInstance().getCurrentSystemSession(workspace, null, null);
        } catch (RepositoryException e) {
            logger.error(String.format("Impossible to get the session for workspace %s", workspace), e);
            return null;
        }
    }

    @Override
    public ContentIntegrityCheck getContentIntegrityCheck(long id) {
        // TODO: a double storage of the integrity checks in a map where the keys are the IDs should fasten this method
        for (ContentIntegrityCheck integrityCheck : integrityChecks) {
            if (integrityCheck.getId() == id) return integrityCheck;
        }
        return null;
    }

    private void storeErrorsInCache(ContentIntegrityResults results) {
        final Element element = new Element(results.getFormattedTestDate(), results);
        errorsCache.put(element);
    }

    @Override
    public ContentIntegrityResults getLatestTestResults() {
        return getTestResults(null);
    }

    @Override
    public ContentIntegrityResults getTestResults(String testDate) {
        final List<String> keys = errorsCache.getKeys();
        if (CollectionUtils.isEmpty(keys)) return null;
        if (StringUtils.isBlank(testDate)) {
            final TreeSet<String> testDates = new TreeSet<>(keys);
            return (ContentIntegrityResults) errorsCache.get(testDates.last()).getObjectValue();
        }
        return (ContentIntegrityResults) errorsCache.get(testDate).getObjectValue();
    }

    @Override
    public List<String> getTestResultsDates() {
        return errorsCache.getKeys();
    }

    @Override
    public List<String> printIntegrityChecksList(boolean simpleOutput) {
        final int nbChecks = integrityChecks.size();
        final List<String> lines = new ArrayList<>(nbChecks + 1);
        logAndAppend(String.format("Integrity checks (%d):", nbChecks), lines);
        for (ContentIntegrityCheck integrityCheck : integrityChecks)
            logAndAppend(String.format("   %s", simpleOutput ? integrityCheck : integrityCheck.toFullString()), lines);
        return lines;
    }

    private void logAndAppend(String line, List<String> lines) {
        logger.info(line);
        lines.add(line);
    }

    /**
     * What's the best strategy?
     * - iterating over the checks and for each, iterating over the tree
     * - iterating over the tree and for each node, iterating over the checks
     */
}
