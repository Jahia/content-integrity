package org.jahia.modules.contentintegrity.api;

import org.jahia.modules.contentintegrity.services.ContentIntegrityError;

import java.util.List;

public interface ContentIntegrityErrorList {

    ContentIntegrityErrorList addError(ContentIntegrityError error);

    ContentIntegrityErrorList addAll(ContentIntegrityErrorList otherList);

    boolean hasErrors();

    List<ContentIntegrityError> getNestedErrors();
}
