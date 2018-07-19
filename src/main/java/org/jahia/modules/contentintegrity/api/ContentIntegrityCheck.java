package org.jahia.modules.contentintegrity.api;

import org.jahia.modules.contentintegrity.services.ContentIntegrityError;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

public interface ContentIntegrityCheck {

    String PRIORITY = "ContentIntegrityCheck.priority";
    String ENABLED = "ContentIntegrityCheck.enabled";

    ContentIntegrityError checkIntegrityBeforeChildren(Node node);

    ContentIntegrityError checkIntegrityAfterChildren(Node node);

    String getName();

    long getId();

    void setId(long id);

    float getPriority();

    boolean areConditionsMatched(Node node);

    String toFullString();

    void resetOwnTime();

    long getOwnTime();

    void trackOwnTime(long time);

    void trackFatalError();

    void initializeIntegrityTest();

    void finalizeIntegrityTest();

    interface SupportsIntegrityErrorFix {
        boolean fixError(Node node, Object errorExtraInfos) throws RepositoryException;
    }

    /*
       Execution conditions
    */
    public interface ExecutionCondition {
        boolean matches(Node node);

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
    }
}
