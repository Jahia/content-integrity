package org.jahia.modules.verifyintegrity.services;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.jahia.exceptions.JahiaException;
import org.jahia.exceptions.JahiaInitializationException;
import org.jahia.services.cache.ehcache.EhCacheProvider;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.utils.DateUtils;
import org.slf4j.Logger;

import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.RepositoryException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class ContentIntegrityService {

    private static Logger logger = org.slf4j.LoggerFactory.getLogger(ContentIntegrityService.class);
    private static ContentIntegrityService instance = new ContentIntegrityService();

    private List<ContentIntegrityCheck> integrityChecks = new ArrayList<>();
    private Cache errorsCache;
    private EhCacheProvider ehCacheProvider;
    private String errorsCacheName = "ContentIntegrityService-errors";
    private long errorsCacheTti = 24L * 3600L; // 1 day;

    public static ContentIntegrityService getInstance() {
        return instance;
    }

    private ContentIntegrityService() {
    }

    public void start() throws JahiaInitializationException {
        if (errorsCache == null) {
            errorsCache = ehCacheProvider.getCacheManager().getCache(errorsCacheName);
            if (errorsCache == null) {
                ehCacheProvider.getCacheManager().addCache(errorsCacheName);
                errorsCache = ehCacheProvider.getCacheManager().getCache(errorsCacheName);
                errorsCache.getCacheConfiguration().setTimeToIdleSeconds(errorsCacheTti);
            }
        }
    }

    public void stop() throws JahiaException {
        if (errorsCache != null) errorsCache.flush();
    }

    public void setEhCacheProvider(EhCacheProvider ehCacheProvider) {
        this.ehCacheProvider = ehCacheProvider;
    }

    public void setErrorsCacheName(String errorsCacheName) {
        this.errorsCacheName = errorsCacheName;
    }

    public void setErrorsCacheTti(long errorsCacheTti) {
        this.errorsCacheTti = errorsCacheTti;
    }

    public void registerIntegrityCheck(ContentIntegrityCheck integrityCheck) {
        integrityCheck.setId(System.currentTimeMillis());
        integrityChecks.add(integrityCheck);
        Collections.sort(integrityChecks);
        logger.info(String.format("Registered %s in the contentIntegrity service ", integrityCheck));
    }

    public void unregisterIntegrityCheck(ContentIntegrityCheck integrityCheck) {
        integrityChecks.remove(integrityCheck);
        logger.info(String.format("Unregistered %s in the contentIntegrity service ", integrityCheck));
    }

    public List<ContentIntegrityError> validateIntegrity(String path, String workspace) {
        return validateIntegrity(path, workspace, false);
    }

    public List<ContentIntegrityError> validateIntegrity(String path, String workspace, boolean fixErrors) {   // TODO maybe need to prevent concurrent executions
        final JCRSessionWrapper session;
        try {
            session = JCRSessionFactory.getInstance().getCurrentSystemSession(workspace, null, null);
        } catch (RepositoryException e) {
            logger.error(String.format("Impossible to get the session for workspace %s", workspace), e);
            return null;
        }
        final JCRNodeWrapper node;
        final List<ContentIntegrityError> errors = new ArrayList<>();
        try {
            node = session.getNode(path);
            logger.info(String.format("Starting to check the integrity under %s in the workspace %s", path, workspace));
            final long start = System.currentTimeMillis();
            validateIntegrity(node, errors, fixErrors);
            logger.info(String.format("Integrity checked under %s in the workspace %s in %s", path, workspace, DateUtils.formatDurationWords(System.currentTimeMillis() - start)));
            storeErrorsInCache(errors, start);
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        return errors;
    }

    private void validateIntegrity(Node node, List<ContentIntegrityError> errors, boolean fixErrors) {
        // TODO add a mechanism to stop
        for (ContentIntegrityCheck integrityCheck : integrityChecks)
            if (integrityCheck.areConditionsMatched(node)) {
                if (logger.isDebugEnabled())
                    logger.debug(String.format("Running %s on %s", integrityCheck.getClass().getName(), node));
                final ContentIntegrityError error = integrityCheck.checkIntegrityBeforeChildren(node);
                handleError(error, node, fixErrors, integrityCheck, errors);
            } else if (logger.isDebugEnabled())
                logger.debug(String.format("Skipping %s on %s", integrityCheck.getClass().getName(), node));
        try {
            for (NodeIterator it = node.getNodes(); it.hasNext(); ) {
                final Node child = (Node) it.next();
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
        for (ContentIntegrityCheck integrityCheck : integrityChecks)
            if (integrityCheck.areConditionsMatched(node)) {
                final ContentIntegrityError error = integrityCheck.checkIntegrityAfterChildren(node);
                handleError(error, node, fixErrors, integrityCheck, errors);
            }
    }

    private void handleError(ContentIntegrityError error, Node node, boolean executeFix, ContentIntegrityCheck integrityCheck, List<ContentIntegrityError> errors) {
        if (error == null) return;
        if (executeFix && integrityCheck instanceof ContentIntegrityCheck.SupportsIntegrityErrorFix)
            try {
                error.setFixed(((ContentIntegrityCheck.SupportsIntegrityErrorFix) integrityCheck).fixError(node));
            } catch (RepositoryException e) {
                logger.error("An error occurred while fixing a content integrity error", e);
            }
        errors.add(error);
    }

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
                final boolean fixed = ((ContentIntegrityCheck.SupportsIntegrityErrorFix) integrityCheck).fixError(node);
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

    private ContentIntegrityCheck getContentIntegrityCheck(long id) {
        // TODO: a double storage of the integrity checks in a map where the keys are the IDs should fasten this method
        for (ContentIntegrityCheck integrityCheck : integrityChecks) {
            if (integrityCheck.getId() == id) return integrityCheck;
        }
        return null;
    }

    private void storeErrorsInCache(List<ContentIntegrityError> errors, long testDate) {
        final Element element = new Element(FastDateFormat.getInstance("yyyy_MM_dd-HH_mm_ss_SSS").format(testDate), errors);
        errorsCache.put(element);
    }

    public List<ContentIntegrityError> getLatestTestResults() {
        return getTestResults(null);
    }

    public List<ContentIntegrityError> getTestResults(String testDate) {
        final List<String> keys = errorsCache.getKeys();
        if (CollectionUtils.isEmpty(keys)) return null;
        if (StringUtils.isBlank(testDate)) {
            final TreeSet<String> testDates = new TreeSet<>(keys);
            return (List<ContentIntegrityError>) errorsCache.get(testDates.last()).getObjectValue();
        }
        return (List<ContentIntegrityError>) errorsCache.get(testDate).getObjectValue();
    }

    public List<String> getTestResultsDates()  {
        return errorsCache.getKeys();
    }

    public void printIntegrityChecksList() {
        logger.info("Integrity checks:");
        for (ContentIntegrityCheck integrityCheck : integrityChecks)
            logger.info(String.format("   %s", integrityCheck));

    }

    /**
     * What's the best strategy?
     * - iterating over the checks and for each, iterating over the tree
     * - iterating over the tree and for each node, iterating over the checks
     */
}