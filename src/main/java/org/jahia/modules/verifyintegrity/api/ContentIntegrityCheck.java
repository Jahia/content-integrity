package org.jahia.modules.verifyintegrity.api;

import org.jahia.modules.verifyintegrity.services.ContentIntegrityError;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

public interface ContentIntegrityCheck {

    String PRIORITY = "ContentIntegrityCheck.priority";

    ContentIntegrityError checkIntegrityBeforeChildren(Node node);

    ContentIntegrityError checkIntegrityAfterChildren(Node node);

    long getId();

    void setId(long id);

    float getPriority();

    boolean areConditionsMatched(Node node);

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
    }
}
