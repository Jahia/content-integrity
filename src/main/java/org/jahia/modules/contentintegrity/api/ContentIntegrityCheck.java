package org.jahia.modules.contentintegrity.api;

import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.RepositoryException;
import java.util.Collection;
import java.util.List;

public interface ContentIntegrityCheck {

    String PRIORITY = "ContentIntegrityCheck.priority:Float";
    String ENABLED = "ContentIntegrityCheck.enabled:Boolean";

    ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node);

    ContentIntegrityErrorList checkIntegrityAfterChildren(JCRNodeWrapper node);

    String getName();

    boolean isEnabled();

    boolean canRun();

    void setEnabled(boolean enabled);

    String getId();

    void setId(String id);

    float getPriority();

    boolean areConditionsMatched(JCRNodeWrapper node);

    boolean areConditionsReachable(JCRNodeWrapper scanRootNode, Collection<String> excludedPaths);

    String toFullString();

    void resetOwnTime();

    long getOwnTime();

    void trackOwnTime(long time);

    void trackFatalError();

    void initializeIntegrityTest(JCRNodeWrapper scanRootNode, Collection<String> excludedPaths);

    ContentIntegrityErrorList finalizeIntegrityTest(JCRNodeWrapper scanRootNode, Collection<String> excludedPaths);

    boolean isValid();

    interface SupportsIntegrityErrorFix {
        /**
         * @param error an error detected by this check
         * @return true if this check provides a fix for this error, for example for its type. Only reads the error, so it is
         * cheap to call on every error of a scan. True by default: a check which fixes only some of its errors overrides it
         */
        default boolean isFixable(ContentIntegrityError error) {
            return true;
        }

        /**
         * Fix a single error identified during a previous scan. Only called on an error which is fixable.
         *
         * @param node the node on which the error has been identified.
         * @param error the error to fix
         * @return true if the error has been successfuly fixed, or if it is already fixed. false otherwise
         * @throws RepositoryException
         */
        boolean fixError(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException;
    }

    /**
     * A check whose fix of some errors takes values provided by an administrator, for example the value of a missing mandatory property.
     */
    interface SupportsIntegrityErrorFixWithValues extends SupportsIntegrityErrorFix {
        /**
         * Tells if the fix of a fixable error takes values: an error takes values if this method returns a description of them.
         *
         * @param node  the node on which the error has been identified
         * @param error the error to fix, which is fixable
         * @return the description of the values to provide, null if the fix of this error doesn't take values
         * @throws RepositoryException
         */
        FixValuesDefinition getFixValuesDefinition(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException;

        /**
         * Fix a single error with the provided values.
         *
         * @param node   the node on which the error has been identified
         * @param error  the error to fix
         * @param values the values, as strings
         * @return true if the error has been successfully fixed. false otherwise
         * @throws RepositoryException if the values are invalid, with a message which explains why
         */
        boolean fixError(JCRNodeWrapper node, ContentIntegrityError error, List<String> values) throws RepositoryException;

        /**
         * Fixes several errors of the same node with their values. A node is saved only once all its mandatory properties
         * are set: a check whose errors can block each other, such as two missing mandatory properties, writes all the values
         * then saves the node once. By default, the errors are fixed one by one.
         *
         * @param node   the node on which the errors have been identified
         * @param errors the errors to fix, which take values
         * @param values the values of each error, in the order of the errors
         * @return true if all the errors have been successfully fixed. false otherwise
         * @throws RepositoryException if the values are invalid, with a message which explains why
         */
        default boolean fixErrors(JCRNodeWrapper node, List<ContentIntegrityError> errors, List<List<String>> values) throws RepositoryException {
            boolean fixed = true;
            for (int i = 0; i < errors.size(); i++) {
                if (!fixError(node, errors.get(i), values.get(i))) fixed = false;
            }
            return fixed;
        }
    }

    interface IsConfigurable {
        ContentIntegrityCheckConfiguration getConfigurations();
    }

    /*
       Execution conditions
    */
    public interface ExecutionCondition {
        boolean matches(JCRNodeWrapper node);

        /**
         * Validates if the condition can be matched on at least 1 node during the scan.
         * A positive value means that it can, a negative value means that it can't, zero means that this can't be determined.
         * @param scanRootNode
         * @param excludedPaths
         * @return
         */
        default int isReachableCondition(JCRNodeWrapper scanRootNode, Collection<String> excludedPaths) { return 0; }

        String APPLY = "apply";
        String SKIP = "skip";

        String ON_NT = "OnNodeTypes";
        String APPLY_ON_NT = APPLY + ON_NT;
        String SKIP_ON_NT = SKIP + ON_NT;

        String ON_SUBTREES = "OnSubTrees";
        String APPLY_ON_SUBTREES = APPLY + ON_SUBTREES;
        String SKIP_ON_SUBTREES = SKIP + ON_SUBTREES;

        String ON_WS = "OnWorkspace";
        String APPLY_ON_WS = APPLY + ON_WS;
        String SKIP_ON_WS = SKIP + ON_WS;

        String IF_HAS_PROP = "IfHasProperties";
        String APPLY_IF_HAS_PROP = APPLY + IF_HAS_PROP;
        String SKIP_IF_HAS_PROP = SKIP + IF_HAS_PROP;

        String ON_EXTERNAL_NODES = "OnExternalNodes:Boolean";
        String APPLY_ON_EXTERNAL_NODES = APPLY + ON_EXTERNAL_NODES;
        String SKIP_ON_EXTERNAL_NODES = SKIP + ON_EXTERNAL_NODES;
    }

    public interface ValidityCondition {
        String APPLY = "apply";
        String SKIP = "skip";

        String ON_VERSION_GREATER_THAN = "OnVersionGT";
        String OR_EQUAL = "E";
        String APPLY_ON_VERSION_GT = APPLY + ON_VERSION_GREATER_THAN;
        String APPLY_ON_VERSION_GTE = APPLY + ON_VERSION_GREATER_THAN + OR_EQUAL;
    }
}
