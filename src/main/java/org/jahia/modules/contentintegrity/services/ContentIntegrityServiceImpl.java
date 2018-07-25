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
import org.jahia.services.SpringContextSingleton;
import org.jahia.services.cache.ehcache.EhCacheProvider;
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

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private long ownTime = 0L;
    private long integrityChecksIdGenerator = 0;


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
        return validateIntegrity(path, workspace, false);
    }

    public ContentIntegrityResults validateIntegrity(String path, String workspace, boolean fixErrors) {   // TODO maybe need to prevent concurrent executions
        final JCRSessionWrapper session;
        try {
            session = JCRSessionFactory.getInstance().getCurrentSystemSession(workspace, null, null);
        } catch (RepositoryException e) {
            logger.error(String.format("Impossible to get the session for workspace %s", workspace), e);
            return null;
        }
        final JCRNodeWrapper node;
        try {
            node = session.getNode(path);
            logger.info(String.format("Starting to check the integrity under %s in the workspace %s", path, workspace));
            final List<ContentIntegrityError> errors = new ArrayList<>();
            final long start = System.currentTimeMillis();
            resetChecksDurationCounter();
            for (ContentIntegrityCheck integrityCheck : integrityChecks) {
                integrityCheck.initializeIntegrityTest();
            }
            validateIntegrity(node, errors, fixErrors);
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
        return null;
    }

    private void resetChecksDurationCounter() {
        for (ContentIntegrityCheck integrityCheck : integrityChecks) {
            integrityCheck.resetOwnTime();
        }
        ownTime = 0L;
    }

    private void printChecksDuration() {
        logger.info(String.format("   Scan of the tree: %s", DateUtils.formatDurationWords(ownTime)));
        for (ContentIntegrityCheck integrityCheck : integrityChecks) {
            logger.info(String.format("   %s: %s", integrityCheck.getName(), DateUtils.formatDurationWords(integrityCheck.getOwnTime())));
        }
    }

    private void validateIntegrity(Node node, List<ContentIntegrityError> errors, boolean fixErrors) {
        // TODO add a mechanism to stop , prevent concurrent run
        for (ContentIntegrityCheck integrityCheck : integrityChecks) {
            final long start = System.currentTimeMillis();
            if (integrityCheck.areConditionsMatched(node)) {
                if (logger.isDebugEnabled())
                    logger.debug(String.format("Running %s on %s", integrityCheck.getClass().getName(), node));
                try {
                    final ContentIntegrityErrorList checkResult = integrityCheck.checkIntegrityBeforeChildren(node);
                    handleResult(checkResult, node, fixErrors, integrityCheck, errors);
                } catch (Throwable t) {
                    logFatalError(node, t, integrityCheck);
                }
            } else if (logger.isDebugEnabled())
                logger.debug(String.format("Skipping %s on %s", integrityCheck.getClass().getName(), node));
            integrityCheck.trackOwnTime(System.currentTimeMillis() - start);
        }
        try {
            for (NodeIterator it = node.getNodes(); it.hasNext(); ) {
                final long start = System.currentTimeMillis();
                final Node child = (Node) it.next();
                if ("/jcr:system".equals(child.getPath()))
                    continue; // If the test is started from /jcr:system or somewhere under, then it will not be skipped
                ownTime += (System.currentTimeMillis() - start);
                validateIntegrity(child, errors, fixErrors);
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
        for (ContentIntegrityCheck integrityCheck : integrityChecks) {
            final long start = System.currentTimeMillis();
            if (integrityCheck.areConditionsMatched(node)) {
                try {
                    final ContentIntegrityErrorList checkResult = integrityCheck.checkIntegrityAfterChildren(node);
                    handleResult(checkResult, node, fixErrors, integrityCheck, errors);
                } catch (Throwable t) {
                    logFatalError(node, t, integrityCheck);
                }
            }
            integrityCheck.trackOwnTime(System.currentTimeMillis() - start);
        }
    }

    private void logFatalError(Node node, Throwable t, ContentIntegrityCheck integrityCheck) {
        String path = null;
        try {
            path = node.getPath();
        } catch (RepositoryException e) {
            logger.error("", e);
        } finally {
            logger.error("Impossible to check the integrity of " + path, t);
            integrityCheck.trackFatalError();
        }
    }

    private void handleResult(ContentIntegrityErrorList checkResult, Node node, boolean executeFix, ContentIntegrityCheck integrityCheck, List<ContentIntegrityError> errors) {
        if (checkResult == null) return;
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
