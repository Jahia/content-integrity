package org.jahia.modules.contentintegrity.services.checks;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
import org.jahia.modules.contentintegrity.api.ContentIntegrityError;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorList;
import org.jahia.modules.contentintegrity.api.ContentIntegrityErrorType;
import org.jahia.modules.contentintegrity.services.impl.AbstractContentIntegrityCheck;
import org.jahia.modules.contentintegrity.services.impl.Constants;
import org.jahia.modules.contentintegrity.services.util.RepairUtils;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.workflow.WorkflowService;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.lock.LockException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.jahia.modules.contentintegrity.services.impl.Constants.JCR_LOCKISDEEP;
import static org.jahia.modules.contentintegrity.services.impl.Constants.JCR_LOCKOWNER;
import static org.jahia.modules.contentintegrity.services.impl.Constants.J_LOCKTOKEN;
import static org.jahia.modules.contentintegrity.services.impl.Constants.J_LOCK_TYPES;

@Component(service = ContentIntegrityCheck.class, immediate = true, property = {
        ContentIntegrityCheck.ExecutionCondition.APPLY_IF_HAS_PROP + "=" + J_LOCK_TYPES + "," + J_LOCKTOKEN + "," + JCR_LOCKISDEEP + "," + JCR_LOCKOWNER
})
public class LockSanityCheck extends AbstractContentIntegrityCheck implements ContentIntegrityCheck.SupportsIntegrityErrorFix {

    private static final Logger logger = LoggerFactory.getLogger(LockSanityCheck.class);

    public static final ContentIntegrityErrorType INCONSISTENT_LOCK = createErrorType("INCONSISTENT_LOCK", "Missing properties on a locked node");
    public static final ContentIntegrityErrorType DELETION_LOCK_ON_I18N = createErrorType("DELETION_LOCK_ON_I18N", "Deletion lock remaining on a translation node");
    public static final ContentIntegrityErrorType LOCK_OF_UNKNOWN_PROCESS = createErrorType("LOCK_OF_UNKNOWN_PROCESS", "Lock of a workflow which does not exist anymore");
    public static final ContentIntegrityErrorType LOCK_PROPERTIES_IN_LIVE = createErrorType("LOCK_PROPERTIES_IN_LIVE", "Lock properties on a node of the live workspace which is not locked");

    /*
     * The workflows lock their nodes with the owner " publication-process-<id> " (the publication workflows, type
     * validation) or " process-<id> " (the lock step of a workflow definition, any type), <id> being the identifier of
     * their process: the lock type value is "<owner>:<type>".
     */
    private static final Pattern WORKFLOW_LOCK = Pattern.compile("^ ((?:publication-)?process-(\\d+)) :(.+)$");
    private static final String WORKFLOW_PROVIDER = "jBPM";
    private static final String LOCK_TYPE = "lock-type";
    private static final String PROCESS_ID = "process-id";

    private final HashSet<String> lockRelatedProperties = new HashSet<>();
    // Whether a process exists, by identifier, for the duration of a scan
    private final Map<String, Boolean> existingProcesses = new HashMap<>();

    @Override
    protected void activateInternal(ComponentContext context) {
        lockRelatedProperties.clear();
        lockRelatedProperties.add(J_LOCK_TYPES);
        lockRelatedProperties.add(J_LOCKTOKEN);
        lockRelatedProperties.add(JCR_LOCKISDEEP);
        lockRelatedProperties.add(JCR_LOCKOWNER);
    }

    @Override
    protected void initializeIntegrityTestInternal(JCRNodeWrapper scanRootNode, Collection<String> excludedPaths) {
        existingProcesses.clear();
    }

    @Override
    public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
        final ContentIntegrityErrorList errors = createEmptyErrorsList();

        final boolean isLive;
        try {
            isLive = StringUtils.equals(node.getSession().getWorkspace().getName(), Constants.LIVE_WORKSPACE);
            /*
             * The first publication of a node clones it to live while a workflow locks it: its lock type and token are
             * copied, but not the JCR lock, and they are never removed from live
             */
            if (isLive && !node.hasProperty(JCR_LOCKOWNER)) {
                checkLockPropertiesInLive(node, errors);
                return errors;
            }
        } catch (RepositoryException e) {
            errors.addError(createFrameworkError(node, e));
            return errors;
        }

        HashSet<String> missingProps = null;
        for (String property : lockRelatedProperties) {
            try {
                if (!node.hasProperty(property)) {
                    if (missingProps == null) missingProps = new HashSet<>();
                    missingProps.add(property);
                }
            } catch (RepositoryException e) {
                errors.addError(createFrameworkError(node, e));
            }
        }
        if (missingProps != null && !missingProps.isEmpty()) {
            final ContentIntegrityError error = createError(node, INCONSISTENT_LOCK)
                    .addExtraInfo("missing-properties", missingProps);
            errors.addError(error);
        }

        checkPartialMarkedForDeletionLock(node, errors);
        if (!isLive) checkLocksOfUnknownProcesses(node, errors);

        return errors;
    }

    private void checkLockPropertiesInLive(JCRNodeWrapper node, ContentIntegrityErrorList errors) throws RepositoryException {
        final List<String> properties = new ArrayList<>();
        for (String property : new String[]{J_LOCK_TYPES, J_LOCKTOKEN}) {
            if (node.hasProperty(property)) properties.add(property);
        }
        if (properties.isEmpty()) return;
        errors.addError(createError(node, LOCK_PROPERTIES_IN_LIVE)
                .addExtraInfo("properties", properties)
                .addExtraInfo("lock-types", getLockTypes(node)));
    }

    private void checkPartialMarkedForDeletionLock(JCRNodeWrapper node, ContentIntegrityErrorList errors) {
        try {
            if (!node.isNodeType(Constants.JAHIANT_TRANSLATION)) return;
            if (!node.hasProperty(J_LOCK_TYPES)) return;

            boolean isLockedForDeletion = false;
            for (JCRValueWrapper value : node.getProperty(J_LOCK_TYPES).getValues()) {
                if (StringUtils.equals(value.getString(), Constants.LOCK_TYPE_DELETION)) {
                    isLockedForDeletion = true;
                    break;
                }
            }
            if (!isLockedForDeletion) return;

            if (!node.getParent().isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION)) {
                errors.addError(createError(node, DELETION_LOCK_ON_I18N));
            }

        } catch (RepositoryException e) {
            errors.addError(createFrameworkError(node, String.format("Error while checking the node %s", node.getPath()), e));
        }
    }

    /*
     * A workflow releases its locks when it ends. A process removed without ending, for example aborted from the jBPM
     * tables, leaves its locks: the node stays locked, and nothing tells it any longer that a workflow holds it.
     */
    private void checkLocksOfUnknownProcesses(JCRNodeWrapper node, ContentIntegrityErrorList errors) {
        try {
            for (String lockType : getLockTypes(node)) {
                final Matcher matcher = WORKFLOW_LOCK.matcher(lockType);
                if (!matcher.matches() || processExists(matcher.group(2))) continue;
                errors.addError(createError(node, LOCK_OF_UNKNOWN_PROCESS)
                        .addExtraInfo(LOCK_TYPE, lockType)
                        .addExtraInfo(PROCESS_ID, matcher.group(2)));
            }
        } catch (RepositoryException e) {
            errors.addError(createFrameworkError(node, String.format("Error while checking the locks of the node %s", node.getPath()), e));
        }
    }

    private boolean processExists(String processId) {
        return existingProcesses.computeIfAbsent(processId, id -> {
            final WorkflowService workflowService = BundleUtils.getOsgiService(WorkflowService.class, null);
            // Without the workflow service, a process can't be told unknown
            if (workflowService == null) return Boolean.TRUE;
            try {
                return workflowService.getWorkflow(WORKFLOW_PROVIDER, id, Locale.ENGLISH) != null;
            } catch (RuntimeException e) {
                logger.warn("Impossible to read the workflow process {}", id, e);
                return Boolean.TRUE;
            }
        });
    }

    private static List<String> getLockTypes(JCRNodeWrapper node) throws RepositoryException {
        final List<String> lockTypes = new ArrayList<>();
        if (!node.hasProperty(J_LOCK_TYPES)) return lockTypes;
        for (JCRValueWrapper value : node.getProperty(J_LOCK_TYPES).getValues()) {
            lockTypes.add(value.getString());
        }
        return lockTypes;
    }

    private static final String[] LOCK_PROPERTIES = {JCR_LOCKISDEEP, JCR_LOCKOWNER, J_LOCK_TYPES, J_LOCKTOKEN,
            "j:deletionMessage", "j:deletionDate", "j:deletionUser"};

    /*
     * From the jcr-scripts fixes: the lock is set and released, so that the platform cleans what it can, then the
     * remaining lock and deletion properties are removed. A deletion lock left on a translation node is cleaned
     * the same way, after marking and unmarking the node for deletion.
     */
    @Override
    public boolean fixError(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException {
        if (error.getErrorType().equals(INCONSISTENT_LOCK)) {
            RepairUtils.runWithListenersDisabled(() -> {
                if (node.isNodeType(Constants.JAHIAMIX_MARKED_FOR_DELETION_ROOT)) node.unmarkForDeletion();
                try {
                    node.lock(true, false);
                } catch (RepositoryException e) {
                    logger.debug("Impossible to lock {}", node.getPath(), e);
                }
                try {
                    node.unlock();
                } catch (RepositoryException e) {
                    logger.debug("Impossible to unlock {}", node.getPath(), e);
                }
                removeLockProperties(node);
            });
            return true;
        }
        if (error.getErrorType().equals(DELETION_LOCK_ON_I18N)) {
            RepairUtils.runWithListenersDisabled(() -> {
                RepairUtils.markThenUnmarkForDeletion(node, getName());
                removeLockProperties(node);
            });
            return true;
        }
        if (error.getErrorType().equals(LOCK_OF_UNKNOWN_PROCESS)) {
            return releaseLockOfUnknownProcess(node, (String) error.getExtraInfo(LOCK_TYPE));
        }
        if (error.getErrorType().equals(LOCK_PROPERTIES_IN_LIVE)) {
            // A node locked since the scan holds a real lock, which this fix doesn't release
            if (node.hasProperty(JCR_LOCKOWNER)) return false;
            RepairUtils.runWithListenersDisabled(() -> {
                RepairUtils.removePropertyRaw(node, J_LOCK_TYPES);
                RepairUtils.removePropertyRaw(node, J_LOCKTOKEN);
            });
            return !node.hasProperty(J_LOCK_TYPES) && !node.hasProperty(J_LOCKTOKEN);
        }
        return false;
    }

    /*
     * Releases the lock as its workflow does when it ends (JCRPublicationService.unlockForPublication): the lock type of
     * the process is removed, and the node is unlocked once no other lock type remains.
     */
    private boolean releaseLockOfUnknownProcess(JCRNodeWrapper node, String lockType) throws RepositoryException {
        final Matcher matcher = lockType == null ? null : WORKFLOW_LOCK.matcher(lockType);
        if (matcher == null || !matcher.matches()) return false;
        if (!getLockTypes(node).contains(lockType)) return true;
        try {
            node.unlock(matcher.group(3), " " + matcher.group(1) + " ");
        } catch (LockException e) {
            logger.debug("Impossible to unlock {}", node.getPath(), e);
        }
        // Read on the JCR node: the wrapper still tells the properties it had before the unlock
        final Node realNode = node.getRealNode();
        if (!realNode.hasProperty(J_LOCK_TYPES)) return true;
        for (Value value : realNode.getProperty(J_LOCK_TYPES).getValues()) {
            if (StringUtils.equals(value.getString(), lockType)) return false;
        }
        return true;
    }

    private void removeLockProperties(JCRNodeWrapper node) throws RepositoryException {
        for (String property : LOCK_PROPERTIES) {
            if (node.hasProperty(property)) node.getProperty(property).remove();
        }
        node.saveSession();
    }
}
