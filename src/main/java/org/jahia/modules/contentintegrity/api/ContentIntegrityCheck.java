package org.jahia.modules.contentintegrity.api;

import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.RepositoryException;
import java.util.Collection;

public interface ContentIntegrityCheck {

    String PRIORITY = "ContentIntegrityCheck.priority";
    String ENABLED = "ContentIntegrityCheck.enabled";

    ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node);

    ContentIntegrityErrorList checkIntegrityAfterChildren(JCRNodeWrapper node);

    String getName();

    boolean isEnabled();

    void setEnabled(boolean enabled);

    String getId();

    void setId(String id);

    float getPriority();

    boolean areConditionsMatched(JCRNodeWrapper node);

    String toFullString();

    void resetOwnTime();

    long getOwnTime();

    void trackOwnTime(long time);

    void trackFatalError();

    void initializeIntegrityTest(JCRNodeWrapper node, Collection<String> excludedPaths);

    void finalizeIntegrityTest(JCRNodeWrapper node, Collection<String> excludedPaths);

    boolean isValid();

    interface SupportsIntegrityErrorFix {
        /**
         * Fix a single error identified during a previous scan.
         *
         * @param node the node on which the error has been identified.
         * @param error the error to fix
         * @return true if the error has been successfuly fixed, or if it is already fixed. false otherwise
         * @throws RepositoryException
         */
        boolean fixError(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException;
    }

    interface IsConfigurable {
        ContentIntegrityCheckConfiguration getConfigurations();
    }

    /*
       Execution conditions
    */
    public interface ExecutionCondition {
        boolean matches(JCRNodeWrapper node);

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
