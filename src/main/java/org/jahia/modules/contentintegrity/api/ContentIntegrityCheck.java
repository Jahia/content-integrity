package org.jahia.modules.contentintegrity.api;

import org.jahia.modules.contentintegrity.services.ContentIntegrityError;
import org.jahia.modules.contentintegrity.services.ContentIntegrityErrorList;
import org.jahia.services.content.JCRNodeWrapper;

import javax.jcr.RepositoryException;

public interface ContentIntegrityCheck {

    String PRIORITY = "ContentIntegrityCheck.priority";
    String ENABLED = "ContentIntegrityCheck.enabled";

    ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node);

    ContentIntegrityErrorList checkIntegrityAfterChildren(JCRNodeWrapper node);

    String getName();

    void setEnabled(boolean enabled);

    long getId();

    void setId(long id);

    float getPriority();

    boolean areConditionsMatched(JCRNodeWrapper node);

    String toFullString();

    void resetOwnTime();

    long getOwnTime();

    void trackOwnTime(long time);

    void trackFatalError();

    void initializeIntegrityTest();

    void finalizeIntegrityTest();

    interface SupportsIntegrityErrorFix {
        boolean fixError(JCRNodeWrapper node, ContentIntegrityError error) throws RepositoryException;
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
    }
}
