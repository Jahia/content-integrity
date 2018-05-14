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
}
